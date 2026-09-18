package com.saad.capvid.audio;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decodes the audio track of a video and returns it as mono 16 kHz float PCM in
 * [-1, 1], which is exactly what whisper.cpp expects.
 *
 * <h3>What changed</h3>
 * The previous implementation accumulated <b>every decoded sample of the whole
 * file</b> in a growable {@code short[]} before converting anything. A 10 minute
 * 48 kHz stereo clip is about 115 MB of shorts, and a 30 minute clip is roughly
 * 350 MB - well past the Java heap on most phones, so long videos died with an
 * OutOfMemoryError part way through "Extracting audio...".
 *
 * <p>This version decodes in bounded chunks: each chunk is downmixed to mono and
 * resampled to 16 kHz immediately, appended to the result, and discarded. Peak
 * extra memory is one chunk ({@link #CHUNK_SECONDS} seconds), independent of how
 * long the video is. Only the final 16 kHz array scales with duration, at
 * 64 KB/second.
 *
 * <p>A duration guard ({@link #MAX_TRANSCRIBE_SECONDS}) refuses to transcribe
 * clips too long for the final array to fit comfortably, with a clear message,
 * rather than letting the process be killed by the low-memory killer with no
 * explanation. Chunked transcription (splitting the audio and offsetting
 * timestamps) is the proper fix for very long videos and is tracked as remaining
 * work - see AUDIT.md.
 */
public class AudioExtractor {

    /** Decode/resample chunk length. Bounds peak memory regardless of clip length. */
    private static final int CHUNK_SECONDS = 20;

    /** Clips longer than this are rejected up front instead of OOMing. */
    public static final int MAX_TRANSCRIBE_SECONDS = 20 * 60;

    /** Guards against a decoder that stops producing output without signalling EOS. */
    private static final int MAX_STALL_ITERATIONS = 2000;

    /** Thrown when the input has no usable audio, or is too long. */
    public static class AudioException extends Exception {
        public AudioException(String message) {
            super(message);
        }
    }

    public static float[] extractPcm16k(Context context, Uri videoUri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(context, videoUri, null);

            int audioTrackIndex = -1;
            MediaFormat audioFormat = null;
            long durationUs = -1;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    audioFormat = format;
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION);
                    }
                    break;
                }
            }

            if (audioTrackIndex == -1) {
                throw new AudioException("This video has no audio track to caption");
            }

            String mime = audioFormat.getString(MediaFormat.KEY_MIME);
            int sampleRate = audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 48000;
            int channelCount = audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? Math.max(1, audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) : 1;

            if (durationUs > 0 && durationUs / 1_000_000L > MAX_TRANSCRIBE_SECONDS) {
                throw new AudioException("Video is " + (durationUs / 1_000_000L / 60)
                        + " minutes long; the offline model handles up to "
                        + (MAX_TRANSCRIBE_SECONDS / 60) + " minutes at a time");
            }

            extractor.selectTrack(audioTrackIndex);

            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(audioFormat, null, null, 0);
            decoder.start();

            return decodeResample(extractor, decoder, sampleRate, channelCount);

        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (IllegalStateException ignored) {
                    // already in an error state
                }
                decoder.release();
            }
            try {
                extractor.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private static float[] decodeResample(MediaExtractor extractor, MediaCodec decoder,
                                          int sampleRate, int channelCount) {
        final int chunkFrames = Math.max(1, sampleRate * CHUNK_SECONDS);

        // One chunk of decoded PCM, as interleaved shorts.
        short[] chunk = new short[chunkFrames * channelCount];
        int chunkPos = 0;

        // Downmixed mono for the chunk.
        float[] mono = new float[chunkFrames];

        // Growable 16 kHz result.
        FloatList out = new FloatList();

        // Linear interpolation carries one sample across chunk boundaries so the
        // resampler does not glitch at each seam.
        float carry = 0f;
        boolean haveCarry = false;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;
        int stalls = 0;

        while (!outputDone) {
            if (!inputDone) {
                int inputIndex = decoder.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                    int sampleSize = inputBuffer == null ? -1 : extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        long pts = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0);
                        extractor.advance();
                    }
                    stalls = 0;
                }
            }

            int outputIndex = decoder.dequeueOutputBuffer(info, 10000);
            if (outputIndex >= 0) {
                stalls = 0;
                ByteBuffer outputBuffer = decoder.getOutputBuffer(outputIndex);
                if (outputBuffer != null) {
                    outputBuffer.order(ByteOrder.LITTLE_ENDIAN);

                    // Consume as many whole frames as the buffer holds, flushing a
                    // full chunk to the result whenever it fills up.
                    while (outputBuffer.remaining() >= 2 * channelCount) {
                        if (chunkPos + channelCount > chunk.length) {
                            int n = downmix(chunk, chunkPos, mono, channelCount);
                            appendResampled(out, mono, n, sampleRate, carry, haveCarry);
                            carry = mono[Math.max(0, n - 1)];
                            haveCarry = true;
                            chunkPos = 0;
                        }
                        for (int c = 0; c < channelCount; c++) chunk[chunkPos + c] = outputBuffer.getShort();
                        chunkPos += channelCount;
                    }
                }
                decoder.releaseOutputBuffer(outputIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (++stalls > MAX_STALL_ITERATIONS) {
                    throw new IllegalStateException("Audio decoder stopped responding");
                }
            }
        }

        // Flush the tail of the last partial chunk.
        if (chunkPos >= channelCount) {
            int n = downmix(chunk, chunkPos, mono, channelCount);
            appendResampled(out, mono, n, sampleRate, carry, haveCarry);
        }

        return out.toArray();
    }

    /** Interleaved shorts -&gt; mono float. @return number of frames written. */
    private static int downmix(short[] interleaved, int sampleCount, float[] monoOut, int channels) {
        int frames = sampleCount / channels;
        if (channels == 1) {
            for (int i = 0; i < frames; i++) monoOut[i] = interleaved[i] / 32768.0f;
            return frames;
        }
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            int base = i * channels;
            for (int c = 0; c < channels; c++) sum += interleaved[base + c];
            monoOut[i] = (sum / (float) channels) / 32768.0f;
        }
        return frames;
    }

    /**
     * Linear-interpolation resample of one chunk, appended to {@code out}.
     * {@code carry} is the last sample of the previous chunk so the seam between
     * chunks interpolates correctly instead of stepping.
     */
    private static void appendResampled(FloatList out, float[] mono, int frames,
                                        int fromRate, float carry, boolean haveCarry) {
        if (frames <= 0) return;
        if (fromRate == 16000) {
            out.addRange(mono, frames);
            return;
        }
        double ratio = fromRate / 16000.0;
        int outCount = (int) (frames / ratio);
        float prev = haveCarry ? carry : mono[0];
        for (int i = 0; i < outCount; i++) {
            double srcIndex = i * ratio;
            int idx0 = (int) srcIndex;
            float a = idx0 < frames ? mono[idx0] : prev;
            float b = (idx0 + 1) < frames ? mono[idx0 + 1] : a;
            float frac = (float) (srcIndex - idx0);
            out.add(a * (1f - frac) + b * frac);
        }
    }

    /** Minimal growable float buffer - avoids autoboxing a Float per sample. */
    private static final class FloatList {
        private float[] data = new float[1 << 16];
        private int size = 0;

        void add(float v) {
            if (size == data.length) grow();
            data[size++] = v;
        }

        void addRange(float[] src, int count) {
            while (size + count > data.length) grow();
            System.arraycopy(src, 0, data, size, count);
            size += count;
        }

        private void grow() {
            float[] grown = new float[(int) Math.min((long) data.length * 2, Integer.MAX_VALUE - 8)];
            System.arraycopy(data, 0, grown, 0, size);
            data = grown;
        }

        float[] toArray() {
            float[] exact = new float[size];
            System.arraycopy(data, 0, exact, 0, size);
            return exact;
        }
    }
}

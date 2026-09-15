package com.saad.capvid.audio;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioExtractor {

    /** Minimal growable primitive short buffer — avoids the per-sample
     *  autoboxing of List&lt;Short&gt;, which was slow enough on longer clips
     *  to make "Extracting audio..." look frozen. */
    private static final class ShortBuffer {
        short[] data = new short[1 << 16];
        int size = 0;

        void add(short v) {
            if (size == data.length) {
                short[] grown = new short[data.length * 2];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            data[size++] = v;
        }
    }

    public static float[] extractPcm16k(Context context, Uri videoUri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, videoUri, null);

        int audioTrackIndex = -1;
        MediaFormat audioFormat = null;

        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrackIndex = i;
                audioFormat = format;
                break;
            }
        }

        if (audioTrackIndex == -1) throw new IllegalStateException("No audio track found");

        extractor.selectTrack(audioTrackIndex);

        String mime = audioFormat.getString(MediaFormat.KEY_MIME);
        int sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
        decoder.configure(audioFormat, null, null, 0);
        decoder.start();

        ShortBuffer pcmSamples = new ShortBuffer();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;

        while (!outputDone) {
            if (!inputDone) {
                int inputIndex = decoder.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        long presentationTime = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, presentationTime, 0);
                        extractor.advance();
                    }
                }
            }

            int outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000);
            if (outputIndex >= 0) {
                ByteBuffer outputBuffer = decoder.getOutputBuffer(outputIndex);
                outputBuffer.order(ByteOrder.LITTLE_ENDIAN);
                while (outputBuffer.remaining() >= 2) pcmSamples.add(outputBuffer.getShort());
                decoder.releaseOutputBuffer(outputIndex, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
            }
        }

        decoder.stop();
        decoder.release();
        extractor.release();

        float[] monoFloat = toMonoFloat(pcmSamples, channelCount);
        if (sampleRate != 16000) monoFloat = resample(monoFloat, sampleRate, 16000);

        return monoFloat;
    }

    private static float[] toMonoFloat(ShortBuffer samples, int channelCount) {
        int frameCount = samples.size / channelCount;
        float[] mono = new float[frameCount];
        for (int i = 0; i < frameCount; i++) {
            int sum = 0;
            for (int c = 0; c < channelCount; c++) sum += samples.data[i * channelCount + c];
            mono[i] = (sum / (float) channelCount) / 32768.0f;
        }
        return mono;
    }

    private static float[] resample(float[] input, int fromRate, int toRate) {
        int outputLength = (int) ((long) input.length * toRate / fromRate);
        float[] output = new float[outputLength];
        double ratio = (double) fromRate / toRate;
        for (int i = 0; i < outputLength; i++) {
            double srcIndex = i * ratio;
            int idx0 = (int) srcIndex;
            int idx1 = Math.min(idx0 + 1, input.length - 1);
            float frac = (float) (srcIndex - idx0);
            output[i] = input[idx0] * (1 - frac) + input[idx1] * frac;
        }
        return output;
    }
}

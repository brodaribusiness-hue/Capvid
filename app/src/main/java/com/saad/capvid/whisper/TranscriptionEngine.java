package com.saad.capvid.whisper;

import android.util.Log;

import com.saad.capvid.model.CaptionWord;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a loaded whisper.cpp context and turns its token stream into
 * word-level {@link CaptionWord}s.
 *
 * <h3>Timestamps</h3>
 * whisper.cpp reports token times in <b>centiseconds</b> (verified against
 * whisper.h at the pinned v1.9.4: "Get the start/end time of the specified
 * token, in centiseconds"), so {@code t * 10L} is the correct conversion to
 * milliseconds. That conversion was already right; what was missing was any
 * handling of the degenerate cases whisper really does produce - tokens with no
 * timestamp data (t0 == t1 == 0), segments made entirely of special tokens, and
 * words whose text trims away to nothing. Those produced CaptionWords with
 * startMs == -1, which then broke caption ordering and could make the overlay
 * show nothing.
 */
public class TranscriptionEngine {

    private static final String TAG = "CapvidWhisper";

    /** whisper_full() return code used by native-lib.cpp to signal cancellation. */
    public static final int RESULT_CANCELLED = -2;

    private final WhisperBridge bridge;
    private long contextPtr;
    private volatile boolean cancelled;

    public TranscriptionEngine(WhisperBridge bridge) {
        this.bridge = bridge;
    }

    public boolean loadModel(String modelPath) {
        if (!WhisperBridge.isLibraryAvailable()) {
            Log.e(TAG, "loadModel: libcapvid_native.so is not loaded (" + WhisperBridge.getLoadError() + ")");
            return false;
        }
        contextPtr = bridge.initContext(modelPath);
        if (contextPtr == 0) {
            Log.e(TAG, "loadModel: native context creation failed for " + modelPath);
        }
        return contextPtr != 0;
    }

    public boolean isModelLoaded() {
        return contextPtr != 0;
    }

    public void setProgressListener(WhisperBridge.ProgressListener listener) {
        bridge.setProgressListener(listener);
    }

    /** Requests cancellation; whisper aborts at its next computation checkpoint. */
    public void cancel() {
        cancelled = true;
        if (WhisperBridge.isLibraryAvailable()) bridge.cancelTranscribe();
    }

    /** Thrown so callers can distinguish "cancelled" from "transcription failed". */
    public static class TranscriptionException extends Exception {
        public TranscriptionException(String message) {
            super(message);
        }
    }

    /**
     * @return word-level captions, possibly empty if the audio contained no speech
     * @throws TranscriptionException if whisper failed or the call was cancelled
     */
    public List<CaptionWord> transcribe(float[] audioData) throws TranscriptionException {
        if (!WhisperBridge.isLibraryAvailable()) {
            throw new TranscriptionException("Speech engine unavailable: " + WhisperBridge.getLoadError());
        }
        if (contextPtr == 0) throw new TranscriptionException("No model is loaded");
        if (audioData == null || audioData.length == 0) {
            throw new TranscriptionException("No audio to transcribe");
        }

        int result = bridge.fullTranscribe(contextPtr, audioData);
        if (result == RESULT_CANCELLED || cancelled) {
            throw new TranscriptionException("Transcription cancelled");
        }
        if (result != 0) {
            throw new TranscriptionException("Transcription failed (whisper error " + result + ")");
        }

        List<CaptionWord> words = new ArrayList<>();
        int eotId = bridge.getEotToken(contextPtr);
        int segmentCount = bridge.getTextSegmentCount(contextPtr);

        for (int s = 0; s < segmentCount; s++) {
            int tokenCount = bridge.getTokenCount(contextPtr, s);

            StringBuilder currentWord = new StringBuilder();
            long wordStart = -1;
            long wordEnd = -1;

            for (int t = 0; t < tokenCount; t++) {
                int tokenId = bridge.getTokenId(contextPtr, s, t);
                // Skip special/timestamp tokens: they carry no displayable text and
                // their timestamps are segment-level, not word-level.
                if (eotId != 0 && tokenId >= eotId) continue;

                String tokenText = bridge.getTokenText(contextPtr, s, t);
                if (tokenText == null || tokenText.isEmpty()) continue;

                long t0 = bridge.getTokenT0(contextPtr, s, t) * 10L;
                long t1 = bridge.getTokenT1(contextPtr, s, t) * 10L;

                // English BPE marks a word boundary with a leading space on the
                // first token of the word. Punctuation tokens arrive without one,
                // so they stay attached to the word they follow - which is what we
                // want on screen.
                boolean startsNewWord = tokenText.startsWith(" ") || currentWord.length() == 0;

                if (startsNewWord && currentWord.length() > 0) {
                    addWord(words, currentWord, wordStart, wordEnd);
                    currentWord = new StringBuilder();
                    wordStart = -1;
                    wordEnd = -1;
                }

                if (currentWord.length() == 0) {
                    wordStart = t0 > 0 ? t0 : Math.max(0L, wordEnd);
                }
                currentWord.append(tokenText);
                wordEnd = Math.max(wordEnd, t1);
            }

            if (currentWord.length() > 0) {
                addWord(words, currentWord, wordStart, wordEnd);
            }
        }

        Log.i(TAG, "transcribe: produced " + words.size() + " caption words");
        return words;
    }

    private static void addWord(List<CaptionWord> out, StringBuilder sb, long start, long end) {
        String text = sb.toString().trim();
        if (text.isEmpty()) return;
        long startMs = start >= 0 ? start : 0L;
        long endMs = Math.max(end, startMs + 1);   // a zero-length window is never visible
        out.add(new CaptionWord(text, startMs, endMs));
    }

    /** Always safe to call, including twice. */
    public void release() {
        if (contextPtr != 0 && WhisperBridge.isLibraryAvailable()) {
            bridge.freeContext(contextPtr);
            contextPtr = 0;
        }
    }
}

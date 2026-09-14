package com.saad.capvid.whisper;

import com.saad.capvid.model.CaptionWord;

import java.util.ArrayList;
import java.util.List;

public class TranscriptionEngine {

    private final WhisperBridge bridge;
    private long contextPtr;

    public TranscriptionEngine(WhisperBridge bridge) {
        this.bridge = bridge;
    }

    public boolean loadModel(String modelPath) {
        contextPtr = bridge.initContext(modelPath);
        return contextPtr != 0;
    }

    public void setProgressListener(WhisperBridge.ProgressListener listener) {
        bridge.setProgressListener(listener);
    }

    public List<CaptionWord> transcribe(float[] audioData) {
        List<CaptionWord> words = new ArrayList<>();

        int result = bridge.fullTranscribe(contextPtr, audioData);
        if (result != 0) return words;

        int eotId = bridge.getEotToken(contextPtr);
        int segmentCount = bridge.getTextSegmentCount(contextPtr);

        for (int s = 0; s < segmentCount; s++) {
            int tokenCount = bridge.getTokenCount(contextPtr, s);

            StringBuilder currentWord = new StringBuilder();
            long wordStart = -1;
            long wordEnd = -1;

            for (int t = 0; t < tokenCount; t++) {
                int tokenId = bridge.getTokenId(contextPtr, s, t);
                if (tokenId >= eotId) continue;

                String tokenText = bridge.getTokenText(contextPtr, s, t);
                long t0 = bridge.getTokenT0(contextPtr, s, t) * 10L;
                long t1 = bridge.getTokenT1(contextPtr, s, t) * 10L;

                boolean startsNewWord = tokenText.startsWith(" ") || currentWord.length() == 0;

                if (startsNewWord && currentWord.length() > 0) {
                    words.add(new CaptionWord(currentWord.toString().trim(), wordStart, wordEnd));
                    currentWord = new StringBuilder();
                }

                if (currentWord.length() == 0) wordStart = t0;

                currentWord.append(tokenText);
                wordEnd = t1;
            }

            if (currentWord.length() > 0) {
                words.add(new CaptionWord(currentWord.toString().trim(), wordStart, wordEnd));
            }
        }

        return words;
    }

    public void release() {
        if (contextPtr != 0) {
            bridge.freeContext(contextPtr);
            contextPtr = 0;
        }
    }
}

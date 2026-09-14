package com.saad.capvid.whisper;

public class WhisperBridge {

    static {
        System.loadLibrary("capvid_native");
    }

    public interface ProgressListener {
        void onProgress(int percent);
    }

    private ProgressListener progressListener;

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    // Called directly from native code (native-lib.cpp) during whisper_full()
    public void onNativeProgress(int percent) {
        if (progressListener != null) progressListener.onProgress(percent);
    }

    public native long initContext(String modelPath);
    public native void freeContext(long contextPtr);
    public native int fullTranscribe(long contextPtr, float[] audioData);
    public native int getTextSegmentCount(long contextPtr);
    public native int getTokenCount(long contextPtr, int segmentIndex);
    public native String getTokenText(long contextPtr, int segmentIndex, int tokenIndex);
    public native long getTokenT0(long contextPtr, int segmentIndex, int tokenIndex);
    public native long getTokenT1(long contextPtr, int segmentIndex, int tokenIndex);
    public native int getTokenId(long contextPtr, int segmentIndex, int tokenIndex);
    public native int getEotToken(long contextPtr);
}

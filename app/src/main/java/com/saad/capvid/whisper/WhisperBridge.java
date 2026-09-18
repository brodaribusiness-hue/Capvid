package com.saad.capvid.whisper;

import android.util.Log;

/**
 * JNI bridge to the whisper.cpp context held in native memory.
 *
 * <p>Every method that takes a {@code contextPtr} tolerates a 0 pointer (the
 * native side checks it), so a failed model load degrades to "no captions"
 * rather than a native crash.
 */
public class WhisperBridge {

    private static final String TAG = "CapvidWhisper";

    /** Set when libcapvid_native.so could not be loaded. */
    private static final boolean LIBRARY_AVAILABLE;

    /** Why it failed, for the user-facing error message. */
    private static String loadError = null;

    static {
        boolean ok;
        try {
            System.loadLibrary("capvid_native");
            ok = true;
        } catch (Throwable t) {
            // Deliberately caught: an unchecked throw from a static initialiser
            // would poison the class permanently with ExceptionInInitializerError,
            // so every later use would fail with a confusing error instead of the
            // real one.
            ok = false;
            loadError = t.getClass().getSimpleName() + ": " + t.getMessage();
            Log.e(TAG, "Failed to load libcapvid_native.so", t);
        }
        LIBRARY_AVAILABLE = ok;
    }

    public interface ProgressListener {
        void onProgress(int percent);
    }

    private volatile ProgressListener progressListener;

    public static boolean isLibraryAvailable() {
        return LIBRARY_AVAILABLE;
    }

    public static String getLoadError() {
        return loadError;
    }

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Called from native code (native-lib.cpp progress_trampoline) while
     * whisper_full() runs, on the transcription thread. Implementations must
     * marshal to the main thread themselves before touching the UI.
     */
    @SuppressWarnings("unused")
    public void onNativeProgress(int percent) {
        ProgressListener l = progressListener;
        if (l != null) {
            try {
                l.onProgress(percent);
            } catch (Throwable ignored) {
                // A broken listener must not abort the transcription.
            }
        }
    }

    // ---- native methods (implemented in app/src/main/cpp/native-lib.cpp) ----
    //
    // These keep their original names and signatures on purpose: the JNI symbol
    // name is derived from the declaring class and method name, so renaming them
    // silently breaks the link to native-lib.cpp with an UnsatisfiedLinkError at
    // first call. Callers must check isLibraryAvailable() first; every method is
    // also null-tolerant on the native side.

    /** @return 0 on success, -2 if cancelled, any other value on failure. */
    public native long initContext(String modelPath);

    public native void freeContext(long contextPtr);

    /** @return 0 on success, -2 if cancelled, any other negative value on failure. */
    public native int fullTranscribe(long contextPtr, float[] audioData);

    /** Asks an in-flight transcription to stop at the next ggml checkpoint. */
    public native void cancelTranscribe();

    public native int getTextSegmentCount(long contextPtr);

    public native int getTokenCount(long contextPtr, int segmentIndex);

    public native String getTokenText(long contextPtr, int segmentIndex, int tokenIndex);

    /** @return token start, in CENTISECONDS (whisper's native unit). */
    public native long getTokenT0(long contextPtr, int segmentIndex, int tokenIndex);

    /** @return token end, in CENTISECONDS (whisper's native unit). */
    public native long getTokenT1(long contextPtr, int segmentIndex, int tokenIndex);

    public native int getTokenId(long contextPtr, int segmentIndex, int tokenIndex);

    public native int getEotToken(long contextPtr);

}

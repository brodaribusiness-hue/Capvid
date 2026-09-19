package com.saad.capvid.whisper

/** Thin JNI boundary. Native returns a small JSON token stream so the rest of
 * the app remains idiomatic Kotlin and is independently unit-testable. */
object WhisperNative {
    init {
        System.loadLibrary("capvid-native")
    }

    fun interface ProgressCallback {
        fun onProgress(progress: Int)
    }

    external fun transcribe(
        modelPath: String,
        audioSamples: FloatArray,
        language: String,
        callback: ProgressCallback
    ): String
}

package com.saad.capvid.whisper

import android.content.Context
import com.saad.capvid.caption.WordGrouping
import com.saad.capvid.model.CaptionLanguage
import com.saad.capvid.model.CaptionWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TranscriptionEngine(private val context: Context) {
    data class Progress(val phase: String, val percent: Int)

    suspend fun transcribe(
        videoFile: File,
        language: CaptionLanguage,
        onProgress: (Progress) -> Unit
    ): List<CaptionWord> = withContext(Dispatchers.IO) {
        val models = ModelManager(context)
        val model = models.ensureInstalled { percent -> onProgress(Progress("Downloading multilingual model", percent)) }
        val audio = AudioExtractor.extractFloatPcm(videoFile, File(context.cacheDir, "audio"))
        if (audio.isEmpty()) throw IllegalStateException("The video has no speech audio track")
        onProgress(Progress("Transcribing on device", 0))
        val json = WhisperNative.transcribe(
            modelPath = model.absolutePath,
            audioSamples = audio,
            language = models.languageFor(language),
            callback = WhisperNative.ProgressCallback { progress ->
                onProgress(Progress("Transcribing on device", progress))
            }
        )
        onProgress(Progress("Transcription complete", 100))
        WordGrouping.fromNativeJson(json)
    }
}

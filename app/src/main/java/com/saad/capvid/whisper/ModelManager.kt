package com.saad.capvid.whisper

import android.content.Context
import com.saad.capvid.model.CaptionLanguage
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ModelManager(private val context: Context) {
    companion object {
        // Multilingual tiny model: supports English, Urdu and Roman Urdu input.
        const val MODEL_FILE_NAME = "ggml-tiny.bin"
        const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin?download=true"
    }

    private val modelFile: File by lazy {
        File(context.filesDir, "models/$MODEL_FILE_NAME").apply { parentFile?.mkdirs() }
    }

    fun isInstalled(): Boolean = modelFile.isFile && modelFile.length() > 1024L * 1024L

    fun file(): File = modelFile

    fun ensureInstalled(onProgress: (Int) -> Unit): File {
        if (isInstalled()) {
            onProgress(100)
            return modelFile
        }
        val temporary = File(modelFile.parentFile, "$MODEL_FILE_NAME.download")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException("Model download failed with HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            var copied = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0L) onProgress((copied * 100L / total).toInt().coerceIn(0, 100))
                        read = input.read(buffer)
                    }
                }
            }
            if (temporary.length() <= 1024L * 1024L) throw IOException("Downloaded model is incomplete")
            if (!temporary.renameTo(modelFile)) throw IOException("Unable to install downloaded model")
            onProgress(100)
            return modelFile
        } finally {
            connection.disconnect()
            if (temporary.exists() && !isInstalled()) temporary.delete()
        }
    }

    fun languageFor(language: CaptionLanguage): String = language.whisperCode
}

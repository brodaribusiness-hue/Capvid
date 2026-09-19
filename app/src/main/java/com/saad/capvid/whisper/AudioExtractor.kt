package com.saad.capvid.whisper

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioExtractor {
    fun extractFloatPcm(videoFile: File, workDirectory: File): FloatArray {
        require(videoFile.isFile) { "Video does not exist: ${videoFile.absolutePath}" }
        workDirectory.mkdirs()
        val raw = File(workDirectory, "audio-${System.nanoTime()}.f32le")
        val command = "-y -i ${quote(videoFile.absolutePath)} -vn -ac 1 -ar 16000 -c:a pcm_f32le ${quote(raw.absolutePath)}"
        val session = FFmpegKit.execute(command)
        if (!ReturnCode.isSuccess(session.returnCode) || !raw.isFile) {
            raw.delete()
            throw IllegalStateException("Could not extract a mono 16 kHz audio track")
        }
        return try {
            val bytes = raw.readBytes()
            val count = bytes.size / Float.SIZE_BYTES
            FloatArray(count) { index ->
                ByteBuffer.wrap(bytes, index * Float.SIZE_BYTES, Float.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN).float
            }
        } finally {
            raw.delete()
        }
    }

    private fun quote(path: String): String = "'${path.replace("'", "'\\''")}'"
}

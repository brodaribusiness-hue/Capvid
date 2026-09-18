package com.saad.capvid.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object GallerySaver {
    fun saveVideo(context: Context, source: File, displayName: String = "Capvid-${System.currentTimeMillis()}.mp4"): android.net.Uri {
        require(source.isFile) { "Exported video does not exist" }
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Capvid")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Unable to create gallery item")
            return try {
                resolver.openOutputStream(uri)?.use { output -> FileInputStream(source).use { input -> input.copyTo(output) } }
                    ?: throw IOException("Unable to open gallery output")
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }

        @Suppress("DEPRECATION")
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Capvid")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Unable to create Movies/Capvid")
        val target = File(directory, displayName)
        FileInputStream(source).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        val values = ContentValues().apply {
            @Suppress("DEPRECATION")
            put(MediaStore.Video.Media.DATA, target.absolutePath)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DISPLAY_NAME, target.name)
        }
        return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to index exported video")
    }
}

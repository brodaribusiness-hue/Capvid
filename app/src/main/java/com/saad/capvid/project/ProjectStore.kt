package com.saad.capvid.project

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class ProjectStore(private val context: Context) {
    private val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }

    fun save(project: com.saad.capvid.model.Project): com.saad.capvid.model.Project {
        val saved = project.copy(updatedAt = System.currentTimeMillis())
        val target = projectFile(saved.id)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temporary).use { it.write(ProjectJsonCodec.encode(saved).toByteArray(Charsets.UTF_8)) }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Could not atomically save project")
        }
        return saved
    }

    fun load(id: String): com.saad.capvid.model.Project? {
        val file = projectFile(id)
        if (!file.exists()) return null
        return runCatching { ProjectJsonCodec.decode(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun loadAll(): List<com.saad.capvid.model.Project> = projectsDir.listFiles { file ->
        file.extension == "capvid"
    }?.mapNotNull { file ->
        runCatching { ProjectJsonCodec.decode(file.readText(Charsets.UTF_8)) }.getOrNull()
    }?.sortedByDescending { it.updatedAt } ?: emptyList()

    fun delete(id: String) {
        projectFile(id).delete()
    }

    /** Copies a picked content URI into app-private storage so editing survives
     * gallery provider changes and remains available offline. */
    fun copyVideo(uri: Uri, displayName: String?): File {
        val extension = displayName?.substringAfterLast('.', "mp4")?.let { ".${it.lowercase()}" } ?: ".mp4"
        val target = File(mediaDir, "video-${System.currentTimeMillis()}$extension")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open selected video")
        input.use { source -> FileOutputStream(target).use { destination -> source.copyTo(destination) } }
        return target
    }

    fun importVideoFile(source: File): File {
        val target = File(mediaDir, "video-${System.currentTimeMillis()}.${source.extension.ifBlank { "mp4" }}")
        FileInputStream(source).use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        return target
    }

    private fun projectFile(id: String): File = File(projectsDir, "$id.capvid")
}

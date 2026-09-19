package com.saad.capvid.export

import android.content.Context
import android.media.MediaMetadataRetriever
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.saad.capvid.model.AspectRatio
import com.saad.capvid.model.Project
import com.saad.capvid.model.VideoSegment
import com.saad.capvid.model.VideoTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class VideoExporter(private val context: Context) {
    data class Metadata(
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val fps: Float,
        val hasAudio: Boolean = true
    )
    data class Result(val output: File, val metadata: Metadata)

    suspend fun export(
        project: Project,
        output: File,
        onProgress: (Int) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        val input = File(project.videoPath)
        require(input.isFile) { "Source video does not exist" }
        val metadata = metadata(input)
        val work = File(context.cacheDir, "exports/${project.id}-${System.nanoTime()}").apply { mkdirs() }
        val ass = File(work, "captions.ass")
        val fonts = File(work, "fonts").apply { mkdirs() }
        try {
            ass.writeText(AssSubtitleBuilder.build(project, metadata.width, metadata.height), Charsets.UTF_8)
            copyFontAssets(fonts)
            onProgress(5)
            output.parentFile?.mkdirs()
            val command = buildCommand(input, output, ass, fonts, project, metadata)
            suspendCoroutine<Unit> { continuation ->
                FFmpegKit.executeAsync(command) { session ->
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        onProgress(100)
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException("FFmpeg caption export failed: ${session.returnCode}")
                        )
                    }
                }
            }
            Result(output, metadata)
        } finally {
            work.deleteRecursively()
        }
    }

    private fun copyFontAssets(target: File) {
        context.assets.list("fonts")?.filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }?.forEach { name ->
            context.assets.open("fonts/$name").use { input ->
                File(target, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun metadata(file: File): Metadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
            var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) width = height.also { height = width }
            Metadata(
                width = width.coerceAtLeast(2),
                height = height.coerceAtLeast(2),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.coerceIn(1f, 120f) ?: 30f,
                hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                    ?.equals("yes", ignoreCase = true) ?: false
            )
        } finally {
            retriever.release()
        }
    }

    companion object {
        /** Exposed for unit tests and for a deterministic export preview. */
        fun buildCommand(
            input: File,
            output: File,
            ass: File,
            fonts: File,
            project: Project,
            metadata: Metadata
        ): String {
            val ranges = project.transform.sourceSegments(metadata.durationMs)
            val mergedRanges = mergeAdjacent(ranges)
            if (mergedRanges.size > 1) {
                return buildSegmentCommand(input, output, ass, fonts, project.transform, metadata, mergedRanges)
            }

            val range = mergedRanges.firstOrNull()
            val startMs = range?.startMs ?: project.transform.trimStartMs
            val endMs = range?.endMs ?: project.transform.effectiveEnd(metadata.durationMs)
            val duration = (endMs - startMs).coerceAtLeast(0L)
            val filter = mutableListOf<String>()
            transformFilter(project.transform, metadata)?.let(filter::add)
            filter += subtitleFilter(ass, fonts)
            val start = if (startMs > 0L) "-ss ${formatSeconds(startMs)} " else ""
            val length = if (duration > 0L && (startMs > 0L || endMs < metadata.durationMs)) "-t ${formatSeconds(duration)} " else ""
            return buildString {
                append("-y ")
                append(start)
                append("-i ${shellQuote(input.absolutePath)} ")
                append(length)
                append("-map 0:v:0 -map 0:a? ")
                append("-vf \"${filter.joinToString(",")}\" ")
                append("-c:v libx264 -preset medium -crf 18 -fps_mode passthrough ")
                // The ordinary trim path can retain the source audio bit-for-bit.
                append("-c:a copy -movflags +faststart ")
                append(shellQuote(output.absolutePath))
            }
        }

        private fun buildSegmentCommand(
            input: File,
            output: File,
            ass: File,
            fonts: File,
            transform: VideoTransform,
            metadata: Metadata,
            ranges: List<VideoSegment>
        ): String {
            val graph = StringBuilder()
            ranges.forEachIndexed { index, range ->
                graph.append("[0:v]trim=start=${formatSeconds(range.startMs)}:end=${formatSeconds(range.endMs)},setpts=PTS-STARTPTS[v$index];")
                if (metadata.hasAudio) {
                    graph.append("[0:a]atrim=start=${formatSeconds(range.startMs)}:end=${formatSeconds(range.endMs)},asetpts=PTS-STARTPTS[a$index];")
                }
            }
            ranges.indices.forEach { index ->
                graph.append("[v$index]")
                if (metadata.hasAudio) graph.append("[a$index]")
            }
            graph.append("concat=n=${ranges.size}:v=1:a=${if (metadata.hasAudio) 1 else 0}[joinedv")
            if (metadata.hasAudio) graph.append("][joineda]") else graph.append("]")
            graph.append(';')
            val visualFilter = transformFilter(transform, metadata)?.let { "$it," } ?: ""
            graph.append("[joinedv]${visualFilter}${subtitleFilter(ass, fonts)}[outv]")

            return buildString {
                append("-y -i ${shellQuote(input.absolutePath)} ")
                append("-filter_complex \"$graph\" ")
                append("-map \"[outv]\" ")
                if (metadata.hasAudio) append("-map \"[joineda]\" -c:a aac ") else append("-an ")
                append("-c:v libx264 -preset medium -crf 18 -fps_mode passthrough -movflags +faststart ")
                append(shellQuote(output.absolutePath))
            }
        }

        private fun mergeAdjacent(ranges: List<VideoSegment>): List<VideoSegment> = buildList {
            ranges.sortedBy { it.startMs }.forEach { range ->
                val previous = lastOrNull()
                if (previous != null && range.startMs <= previous.endMs) {
                    removeAt(lastIndex)
                    add(VideoSegment(previous.startMs, maxOf(previous.endMs, range.endMs)))
                } else add(range)
            }
        }

        private fun transformFilter(transform: com.saad.capvid.model.VideoTransform, metadata: Metadata): String? {
            val ratio = transform.aspectRatio
            val zoom = transform.zoom.coerceAtLeast(1f)
            if (ratio == AspectRatio.ORIGINAL && zoom <= 1.001f && transform.panX == 0f && transform.panY == 0f) return null
            val filters = mutableListOf<String>()
            if (ratio != AspectRatio.ORIGINAL) {
                val sourceRatio = metadata.width.toFloat() / metadata.height.toFloat()
                val desired = ratio.width.toFloat() / ratio.height.toFloat()
                val targetW: Int
                val targetH: Int
                if (sourceRatio > desired) {
                    targetH = metadata.height
                    targetW = ((targetH * desired).toInt() / 2) * 2
                } else {
                    targetW = metadata.width
                    targetH = ((targetW / desired).toInt() / 2) * 2
                }
                filters += "crop=${targetW.coerceAtLeast(2)}:${targetH.coerceAtLeast(2)}:(iw-ow)/2+${formatExpression(transform.panX)}:(ih-oh)/2+${formatExpression(transform.panY)}"
            }
            if (zoom > 1.001f) {
                filters += "scale=ceil(iw*${formatExpression(zoom)}/2)*2:ceil(ih*${formatExpression(zoom)}/2)*2"
                filters += "crop=iw/${formatExpression(zoom)}:ih/${formatExpression(zoom)}:(in_w-out_w)/2:(in_h-out_h)/2"
            }
            return filters.joinToString(",").ifBlank { null }
        }

        private fun subtitleFilter(ass: File, fonts: File): String =
            "subtitles=${filterPath(ass)}:fontsdir=${filterPath(fonts)}"

        private fun filterPath(file: File): String = "'${file.absolutePath.replace("'", "\\'")}'"
        private fun shellQuote(path: String): String = "'${path.replace("'", "'\\''")}'"
        private fun formatSeconds(ms: Long): String = "%.3f".format(java.util.Locale.US, ms / 1000.0)
        private fun formatExpression(value: Float): String = "%.3f".format(java.util.Locale.US, value)
    }
}

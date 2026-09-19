package com.saad.capvid.model

import java.util.UUID

/** Languages exposed by the UI. Roman Urdu is written with the multilingual
 * English decoder because Whisper has no separate Roman Urdu language token. */
enum class CaptionLanguage(val label: String, val whisperCode: String) {
    ENGLISH("English", "en"),
    ROMAN_URDU("Roman Urdu", "en"),
    URDU("Urdu", "ur");

    companion object {
        fun fromName(value: String?): CaptionLanguage = entries.firstOrNull {
            it.name == value || it.label == value
        } ?: ENGLISH
    }
}

enum class CaptionEffect {
    MINIMAL,
    KARAOKE,
    POP,
    GLOW,
    NEON,
    TYPEWRITER,
    BOUNCE,
    HIGHLIGHT_BOX,
    OUTLINE,
    SHADOW_3D,
    FLIP_3D,
    WAVE,
    SWING,
    WORD_BY_WORD,
    COMIC,
    GRADIENT,
    FOCUS,
    GLITCH
}

enum class BreakMode {
    PUNCTUATION,
    SINGLE_WORD,
    RANDOM,
    NATURAL
}

enum class AspectRatio(val label: String, val width: Int, val height: Int) {
    ORIGINAL("Original", 0, 0),
    PORTRAIT("9:16", 9, 16),
    SQUARE("1:1", 1, 1),
    LANDSCAPE("16:9", 16, 9),
    CLASSIC("4:3", 4, 3)
}

data class CaptionWord(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val highlighted: Boolean = false,
    val phraseId: String? = null
) {
    val safeEndMs: Long get() = maxOf(endMs, startMs + 40L)
}

data class BreakSettings(
    val mode: BreakMode = BreakMode.NATURAL,
    val linesPerPage: Int = 2,
    val wordsPerLine: Int = 6,
    val capitalization: String = "none"
)

data class CaptionStyle(
    val id: String,
    val name: String,
    val fontAsset: String,
    val fontFamily: String,
    val fontSizeSp: Float,
    val textColor: String,
    val activeColor: String,
    val activeBackground: String,
    val strokeColor: String,
    val shadowColor: String,
    val captionBackground: String,
    val strokeWidth: Float,
    val shadowDistance: Float,
    val cornerRadius: Float,
    val positionX: Float,
    val positionY: Float,
    val alignment: Int,
    val lineSpacing: Float,
    val wordSpacing: Float,
    val activeWordEnabled: Boolean,
    val activeBackgroundEnabled: Boolean,
    val strokeEnabled: Boolean,
    val shadowEnabled: Boolean,
    val captionBackgroundEnabled: Boolean,
    val effect: CaptionEffect,
    val breaks: BreakSettings = BreakSettings()
)

data class VideoSegment(
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

data class VideoTransform(
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val aspectRatio: AspectRatio = AspectRatio.ORIGINAL,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    /** Source-time ranges that remain after split/delete operations. */
    val segments: List<VideoSegment> = emptyList()
) {
    fun effectiveEnd(durationMs: Long): Long = if (trimEndMs <= 0L) durationMs else minOf(trimEndMs, durationMs)

    /** Returns the source ranges in playback order, clipped to the trim window. */
    fun sourceSegments(durationMs: Long): List<VideoSegment> {
        val safeDuration = durationMs.coerceAtLeast(0L)
        val lower = trimStartMs.coerceIn(0L, safeDuration)
        val upper = effectiveEnd(safeDuration).coerceIn(lower, safeDuration)
        val candidates = if (segments.isEmpty()) listOf(VideoSegment(lower, upper)) else segments
        return candidates.mapNotNull { segment ->
            val start = segment.startMs.coerceIn(lower, upper)
            val end = segment.endMs.coerceIn(start, upper)
            VideoSegment(start, end).takeIf { it.durationMs >= 40L }
        }.sortedBy { it.startMs }
    }

    /** Splits the clip containing the playhead without changing its source content. */
    fun splitAt(playheadMs: Long, durationMs: Long): VideoTransform {
        val ranges = sourceSegments(durationMs)
        val point = playheadMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        val split = ranges.flatMap { segment ->
            if (point > segment.startMs + 40L && point < segment.endMs - 40L) {
                listOf(VideoSegment(segment.startMs, point), VideoSegment(point, segment.endMs))
            } else listOf(segment)
        }
        return if (split.size == ranges.size) this
        else copy(trimStartMs = 0L, trimEndMs = 0L, segments = split)
    }

    /** Removes one split clip and collapses a single remaining range back to trim. */
    fun deleteSegment(index: Int, durationMs: Long): VideoTransform {
        val ranges = sourceSegments(durationMs)
        if (index !in ranges.indices) return this
        val remaining = ranges.filterIndexed { position, _ -> position != index }
        if (remaining.isEmpty()) return copy(trimStartMs = 0L, trimEndMs = 1L, segments = emptyList())
        if (remaining.size == 1) {
            val only = remaining.single()
            return copy(
                trimStartMs = only.startMs,
                trimEndMs = if (only.endMs >= durationMs) 0L else only.endMs,
                segments = emptyList()
            )
        }
        return copy(trimStartMs = 0L, trimEndMs = 0L, segments = remaining)
    }
}

data class Project(
    val id: String,
    val name: String,
    val videoPath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val durationMs: Long,
    val language: CaptionLanguage = CaptionLanguage.ENGLISH,
    val addCaptions: Boolean = true,
    val words: List<CaptionWord> = emptyList(),
    val style: CaptionStyle,
    val transform: VideoTransform = VideoTransform()
) {
    companion object {
        fun create(
            videoPath: String,
            name: String = videoPath.substringAfterLast('/').ifBlank { "Untitled video" },
            durationMs: Long = 0L,
            language: CaptionLanguage = CaptionLanguage.ENGLISH
        ): Project {
            val now = System.currentTimeMillis()
            return Project(
                id = UUID.randomUUID().toString(),
                name = name,
                videoPath = videoPath,
                createdAt = now,
                updatedAt = now,
                durationMs = durationMs,
                language = language,
                style = com.saad.capvid.caption.StyleCatalog.defaultStyle()
            )
        }
    }
}

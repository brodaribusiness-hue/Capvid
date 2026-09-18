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

data class VideoTransform(
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val aspectRatio: AspectRatio = AspectRatio.ORIGINAL,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f
) {
    fun effectiveEnd(durationMs: Long): Long = if (trimEndMs <= 0L) durationMs else minOf(trimEndMs, durationMs)
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

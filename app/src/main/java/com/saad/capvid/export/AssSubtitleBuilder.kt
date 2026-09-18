package com.saad.capvid.export

import com.saad.capvid.model.BreakMode
import com.saad.capvid.model.CaptionEffect
import com.saad.capvid.model.CaptionStyle
import com.saad.capvid.model.CaptionWord
import com.saad.capvid.model.Project
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Builds an ASS document from the same style/effect vocabulary used by the
 * Canvas preview. Every preset gets its own animation tags rather than relying
 * on one global subtitle style.
 */
object AssSubtitleBuilder {
    fun build(project: Project, width: Int = 1920, height: Int = 1080): String {
        val style = project.style
        val trimStart = project.transform.trimStartMs
        val trimEnd = project.transform.effectiveEnd(project.durationMs)
        val words = project.words.mapNotNull { word ->
            val start = word.startMs - trimStart
            val end = word.safeEndMs - trimStart
            val clippedEnd = if (trimEnd > 0L) minOf(end, trimEnd - trimStart) else end
            if (clippedEnd <= 0L || start >= (trimEnd - trimStart).coerceAtLeast(1L)) null
            else word.copy(startMs = maxOf(0L, start), endMs = maxOf(40L, clippedEnd))
        }.sortedBy { it.startMs }
        val events = buildEvents(words, style, width, height)
        return buildString {
            append(header(style, width, height))
            append("[Events]\n")
            append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
            events.forEach { append(it).append('\n') }
        }
    }

    private fun buildEvents(words: List<CaptionWord>, style: CaptionStyle, width: Int, height: Int): List<String> {
        if (words.isEmpty()) return emptyList()
        val pages = makePages(words, style)
        val result = mutableListOf<String>()
        pages.forEach { page ->
            val pageStart = page.minOf { it.startMs }
            val pageEnd = page.maxOf { it.safeEndMs }
            val pageText = pageText(page, style)
            page.forEachIndexed { index, word ->
                val nextStart = page.getOrNull(index + 1)?.startMs ?: pageEnd
                val eventEnd = maxOf(word.safeEndMs, nextStart).coerceAtMost(pageEnd)
                val text = eventText(page, word, pageText, style, width, height, pageStart)
                result += "Dialogue: 0,${assTime(word.startMs)},${assTime(maxOf(word.startMs + 40L, eventEnd))},Capvid,,0,0,0,,${text}"
            }
        }
        return result
    }

    private fun makePages(words: List<CaptionWord>, style: CaptionStyle): List<List<CaptionWord>> {
        val lineSize = style.breaks.wordsPerLine.coerceIn(1, 20)
        val pageSize = (lineSize * style.breaks.linesPerPage.coerceIn(1, 4)).coerceAtLeast(1)
        val chunks = mutableListOf<MutableList<CaptionWord>>()
        var current = mutableListOf<CaptionWord>()
        words.forEachIndexed { index, word ->
            val pause = index > 0 && word.startMs - words[index - 1].safeEndMs > 420L
            val punctuation = index > 0 && words[index - 1].text.lastOrNull()?.let { it in ".!?;:" } == true
            val single = style.breaks.mode == BreakMode.SINGLE_WORD
            val randomBreak = style.breaks.mode == BreakMode.RANDOM && current.size >= (2 + (index % 4))
            val naturalBreak = style.breaks.mode == BreakMode.PUNCTUATION && punctuation ||
                style.breaks.mode == BreakMode.NATURAL && pause
            if (current.isNotEmpty() && (current.size >= pageSize || single || randomBreak || naturalBreak)) {
                chunks += current
                current = mutableListOf()
            }
            current += word
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    private fun pageText(page: List<CaptionWord>, style: CaptionStyle): String {
        val lineSize = style.breaks.wordsPerLine.coerceIn(1, 20)
        return page.mapIndexed { index, word ->
            val formatted = capitalize(word.text, style.breaks.capitalization)
            val separator = when {
                index == 0 -> ""
                index % lineSize == 0 -> "\\N"
                else -> " "
            }
            separator + escape(formatted)
        }.joinToString("")
    }

    private fun eventText(
        page: List<CaptionWord>,
        active: CaptionWord,
        plainText: String,
        style: CaptionStyle,
        width: Int,
        height: Int,
        pageStart: Long
    ): String {
        val activeText = escape(capitalize(active.text, style.breaks.capitalization))
        val activeTag = activeTag(style, active)
        val highlighted = if (style.activeWordEnabled || active.highlighted) {
            plainText.replace(activeText, "$activeTag$activeText", ignoreCase = false)
        } else plainText
        val position = "\\an${style.alignment}\\pos(${(width * style.positionX).roundToInt()},${(height * style.positionY).roundToInt()})"
        return "{${position}${effectTags(style, active, pageStart)}}$highlighted"
    }

    private fun activeTag(style: CaptionStyle, word: CaptionWord): String {
        if (!style.activeWordEnabled && !word.highlighted) return ""
        val builder = StringBuilder("{\\1c${assColor(style.activeColor)}")
        if (style.activeBackgroundEnabled) {
            builder.append("\\bord${maxOf(2f, style.strokeWidth + 5f)}\\3c${assColor(style.activeBackground)}")
        }
        builder.append('}')
        return builder.toString()
    }

    private fun effectTags(style: CaptionStyle, word: CaptionWord, pageStart: Long): String {
        val duration = (word.safeEndMs - word.startMs).coerceAtLeast(40L).toInt()
        val half = (duration / 2).coerceAtLeast(60)
        val stroke = if (style.strokeEnabled) "\\bord${style.strokeWidth}" else "\\bord0"
        val shadow = if (style.shadowEnabled) "\\shad${style.shadowDistance}" else "\\shad0"
        val base = StringBuilder("\\fn${style.fontFamily}\\fs${style.fontSizeSp.roundToInt()}$stroke$shadow")
        if (style.strokeEnabled) base.append("\\3c${assColor(style.strokeColor)}")
        if (style.shadowEnabled) base.append("\\4c${assColor(style.shadowColor)}")
        when (style.effect) {
            CaptionEffect.MINIMAL -> Unit
            CaptionEffect.KARAOKE -> base.append("\\kf${(duration / 10).coerceAtLeast(1)}")
            CaptionEffect.POP -> base.append("\\fscx72\\fscy72\\t(0,${half},\\fscx100\\fscy100)")
            CaptionEffect.GLOW -> base.append("\\blur4\\t(0,${half},\\blur0)")
            CaptionEffect.NEON -> base.append("\\bord${maxOf(style.strokeWidth, 3f)}\\blur1\\t(0,${half},\\blur0)")
            CaptionEffect.TYPEWRITER -> base.append("\\alpha&HFF&\\t(0,80,\\alpha&H00&)")
            CaptionEffect.BOUNCE -> base.append("\\fscx82\\fscy82\\t(0,${half},\\fscx105\\fscy105)\\t(${half},${duration},\\fscx100\\fscy100)")
            CaptionEffect.HIGHLIGHT_BOX -> base.append("\\bord${maxOf(style.strokeWidth, 2f)}\\3c${assColor(style.activeBackground)}")
            CaptionEffect.OUTLINE -> base.append("\\bord${maxOf(style.strokeWidth, 4f)}")
            CaptionEffect.SHADOW_3D -> base.append("\\xshad${style.shadowDistance}\\yshad${style.shadowDistance}")
            CaptionEffect.FLIP_3D -> base.append("\\frx90\\t(0,${duration.coerceAtMost(220)},\\frx0)")
            CaptionEffect.WAVE -> base.append("\\frz-5\\t(0,${half},\\frz5)\\t(${half},${duration},\\frz-2)")
            CaptionEffect.SWING -> base.append("\\frz-12\\t(0,${half},\\frz12)\\t(${half},${duration},\\frz0)")
            CaptionEffect.WORD_BY_WORD -> base.append("\\alpha&H00&")
            CaptionEffect.COMIC -> base.append("\\fscx88\\fscy88\\frz-3\\t(0,${half},\\fscx105\\fscy105\\frz3)")
            CaptionEffect.GRADIENT -> base.append("\\1c${assColor(style.activeColor)}\\t(0,${duration},\\1c${assColor(style.textColor)})")
            CaptionEffect.FOCUS -> base.append("\\blur6\\t(0,${duration.coerceAtMost(260)},\\blur0)")
            CaptionEffect.GLITCH -> base.append("\\xshad3\\t(0,70,\\xshad-3)\\t(70,140,\\xshad3)")
        }
        return base.toString()
    }

    private fun header(style: CaptionStyle, width: Int, height: Int): String = buildString {
        append("[Script Info]\n")
        append("ScriptType: v4.00+\n")
        append("PlayResX: $width\nPlayResY: $height\n")
        append("ScaledBorderAndShadow: yes\nWrapStyle: 2\n\n")
        append("[V4+ Styles]\n")
        append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        val outline = if (style.strokeEnabled) style.strokeWidth else 0f
        val shadow = if (style.shadowEnabled) style.shadowDistance else 0f
        val boxAlpha = if (style.captionBackgroundEnabled) "00" else "FF"
        append("Style: Capvid,${style.fontFamily},${style.fontSizeSp.roundToInt()},${assColor(style.textColor)},${assColor(style.activeColor)},${assColor(style.strokeColor)},&H${boxAlpha}000000,0,0,0,0,100,100,${style.wordSpacing},0,1,$outline,$shadow,${style.alignment},35,35,35,1\n\n")
    }

    private fun capitalize(value: String, mode: String): String = when (mode.lowercase(Locale.ROOT)) {
        "upper" -> value.uppercase(Locale.ROOT)
        "lower" -> value.lowercase(Locale.ROOT)
        "title" -> value.split(Regex("\\s+")).joinToString(" ") { part ->
            if (part.isBlank()) part else part.replaceFirstChar { it.uppercase(Locale.ROOT) }
        }
        else -> value
    }

    private fun assTime(milliseconds: Long): String {
        val safe = milliseconds.coerceAtLeast(0L)
        val hours = safe / 3_600_000L
        val minutes = (safe % 3_600_000L) / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val centiseconds = (safe % 1_000L) / 10L
        return "%d:%02d:%02d.%02d".format(Locale.US, hours, minutes, seconds, centiseconds)
    }

    private fun assColor(hex: String): String {
        val clean = hex.removePrefix("#").padStart(6, '0').takeLast(6)
        val red = clean.substring(0, 2)
        val green = clean.substring(2, 4)
        val blue = clean.substring(4, 6)
        return "&H00$blue$green$red&"
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace("\n", "\\N")
}

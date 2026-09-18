package com.saad.capvid.caption

import com.saad.capvid.model.CaptionWord
import org.json.JSONObject
import java.util.UUID

/**
 * Converts whisper's token timestamps into editable word spans. Whisper often
 * emits a word as multiple BPE tokens; a leading space starts the next word,
 * while punctuation without a leading space stays attached to its word.
 */
object WordGrouping {
    data class Token(val text: String, val startMs: Long, val endMs: Long)

    fun fromNativeJson(json: String): List<CaptionWord> {
        val root = JSONObject(json)
        root.optString("error").takeIf { it.isNotBlank() }?.let { error ->
            throw IllegalStateException(error)
        }
        val tokensJson = root.optJSONArray("tokens") ?: return emptyList()
        val tokens = buildList {
            for (index in 0 until tokensJson.length()) {
                val token = tokensJson.optJSONObject(index) ?: continue
                val text = token.optString("text")
                if (text.isNotBlank()) {
                    add(Token(text, token.optLong("startMs"), token.optLong("endMs")))
                }
            }
        }
        return groupTokens(tokens)
    }

    fun groupTokens(tokens: List<Token>): List<CaptionWord> {
        val result = mutableListOf<CaptionWord>()
        val current = StringBuilder()
        var currentStart = 0L
        var currentEnd = 0L

        fun flush() {
            val normalized = normalize(current.toString())
            if (normalized.isNotEmpty()) {
                result += CaptionWord(
                    id = UUID.randomUUID().toString(),
                    text = normalized,
                    startMs = currentStart,
                    endMs = maxOf(currentEnd, currentStart + 40L)
                )
            }
            current.clear()
            currentStart = 0L
            currentEnd = 0L
        }

        tokens.forEach { token ->
            val raw = token.text.replace('\n', ' ').replace('\r', ' ')
            if (raw.isBlank()) {
                flush()
                return@forEach
            }
            val startsNewWord = raw.firstOrNull()?.isWhitespace() == true && current.isNotEmpty()
            if (startsNewWord) flush()
            if (current.isEmpty()) currentStart = maxOf(0L, token.startMs)
            current.append(raw.trim())
            currentEnd = maxOf(currentEnd, token.endMs)
        }
        flush()

        return result.mapIndexed { index, word ->
            // Whisper may occasionally return an inverted or zero-length token
            // around punctuation. Keep the timeline monotonic and draggable.
            val previousEnd = result.getOrNull(index - 1)?.endMs ?: 0L
            val correctedStart = maxOf(word.startMs, previousEnd)
            word.copy(
                startMs = correctedStart,
                endMs = maxOf(word.endMs, correctedStart + 40L)
            )
        }
    }

    fun wordsFromText(text: String, startMs: Long, endMs: Long): List<CaptionWord> {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val total = maxOf(1L, endMs - startMs)
        return words.mapIndexed { index, word ->
            val start = startMs + total * index / words.size
            val end = startMs + total * (index + 1) / words.size
            CaptionWord(text = word, startMs = start, endMs = maxOf(start + 40L, end))
        }
    }

    private fun normalize(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
        .replace(Regex("\\s+([,.!?;:%])"), "$1")
        .replace(Regex("([([{])\\s+"), "$1")
}

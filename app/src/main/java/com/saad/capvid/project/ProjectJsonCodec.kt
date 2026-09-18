package com.saad.capvid.project

import com.saad.capvid.caption.StyleCatalog
import com.saad.capvid.model.AspectRatio
import com.saad.capvid.model.BreakMode
import com.saad.capvid.model.BreakSettings
import com.saad.capvid.model.CaptionEffect
import com.saad.capvid.model.CaptionLanguage
import com.saad.capvid.model.CaptionStyle
import com.saad.capvid.model.CaptionWord
import com.saad.capvid.model.Project
import com.saad.capvid.model.VideoTransform
import org.json.JSONArray
import org.json.JSONObject

/** Stable, dependency-free JSON serialization for .capvid project files. */
object ProjectJsonCodec {
    const val FORMAT_VERSION = 1

    fun encode(project: Project): String = toJson(project).toString(2)

    fun decode(json: String): Project = fromJson(JSONObject(json))

    fun toJson(project: Project): JSONObject = JSONObject().apply {
        put("formatVersion", FORMAT_VERSION)
        put("id", project.id)
        put("name", project.name)
        put("videoPath", project.videoPath)
        put("createdAt", project.createdAt)
        put("updatedAt", project.updatedAt)
        put("durationMs", project.durationMs)
        put("language", project.language.name)
        put("addCaptions", project.addCaptions)
        put("words", JSONArray().also { array -> project.words.forEach { array.put(wordToJson(it)) } })
        put("style", styleToJson(project.style))
        put("transform", transformToJson(project.transform))
    }

    fun fromJson(root: JSONObject): Project {
        val styleObject = root.optJSONObject("style")
        val transformObject = root.optJSONObject("transform")
        val wordsObject = root.optJSONArray("words") ?: JSONArray()
        val words = buildList {
            for (index in 0 until wordsObject.length()) {
                wordsObject.optJSONObject(index)?.let { add(wordFromJson(it)) }
            }
        }
        return Project(
            id = root.optString("id").ifBlank { "legacy-project" },
            name = root.optString("name", "Untitled video"),
            videoPath = root.optString("videoPath"),
            createdAt = root.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = root.optLong("updatedAt", System.currentTimeMillis()),
            durationMs = root.optLong("durationMs", 0L),
            language = CaptionLanguage.fromName(root.optString("language")),
            addCaptions = root.optBoolean("addCaptions", true),
            words = words,
            style = styleObject?.let(::styleFromJson) ?: StyleCatalog.defaultStyle(),
            transform = transformObject?.let(::transformFromJson) ?: VideoTransform()
        )
    }

    private fun wordToJson(word: CaptionWord) = JSONObject().apply {
        put("id", word.id)
        put("text", word.text)
        put("startMs", word.startMs)
        put("endMs", word.endMs)
        put("highlighted", word.highlighted)
        word.phraseId?.let { put("phraseId", it) }
    }

    private fun wordFromJson(json: JSONObject) = CaptionWord(
        id = json.optString("id").ifBlank { "word-${json.optLong("startMs")}" },
        text = json.optString("text"),
        startMs = json.optLong("startMs"),
        endMs = json.optLong("endMs"),
        highlighted = json.optBoolean("highlighted", false),
        phraseId = json.optString("phraseId").takeIf { it.isNotBlank() }
    )

    private fun styleToJson(style: CaptionStyle) = JSONObject().apply {
        put("id", style.id)
        put("name", style.name)
        put("fontAsset", style.fontAsset)
        put("fontFamily", style.fontFamily)
        put("fontSizeSp", style.fontSizeSp.toDouble())
        put("textColor", style.textColor)
        put("activeColor", style.activeColor)
        put("activeBackground", style.activeBackground)
        put("strokeColor", style.strokeColor)
        put("shadowColor", style.shadowColor)
        put("captionBackground", style.captionBackground)
        put("strokeWidth", style.strokeWidth.toDouble())
        put("shadowDistance", style.shadowDistance.toDouble())
        put("cornerRadius", style.cornerRadius.toDouble())
        put("positionX", style.positionX.toDouble())
        put("positionY", style.positionY.toDouble())
        put("alignment", style.alignment)
        put("lineSpacing", style.lineSpacing.toDouble())
        put("wordSpacing", style.wordSpacing.toDouble())
        put("activeWordEnabled", style.activeWordEnabled)
        put("activeBackgroundEnabled", style.activeBackgroundEnabled)
        put("strokeEnabled", style.strokeEnabled)
        put("shadowEnabled", style.shadowEnabled)
        put("captionBackgroundEnabled", style.captionBackgroundEnabled)
        put("effect", style.effect.name)
        put("breaks", JSONObject().apply {
            put("mode", style.breaks.mode.name)
            put("linesPerPage", style.breaks.linesPerPage)
            put("wordsPerLine", style.breaks.wordsPerLine)
            put("capitalization", style.breaks.capitalization)
        })
    }

    private fun styleFromJson(json: JSONObject): CaptionStyle {
        val fallback = StyleCatalog.find(json.optString("id"))
        val breaksJson = json.optJSONObject("breaks")
        return fallback.copy(
            id = json.optString("id", fallback.id),
            name = json.optString("name", fallback.name),
            fontAsset = json.optString("fontAsset", fallback.fontAsset),
            fontFamily = json.optString("fontFamily", fallback.fontFamily),
            fontSizeSp = json.optDouble("fontSizeSp", fallback.fontSizeSp.toDouble()).toFloat(),
            textColor = json.optString("textColor", fallback.textColor),
            activeColor = json.optString("activeColor", fallback.activeColor),
            activeBackground = json.optString("activeBackground", fallback.activeBackground),
            strokeColor = json.optString("strokeColor", fallback.strokeColor),
            shadowColor = json.optString("shadowColor", fallback.shadowColor),
            captionBackground = json.optString("captionBackground", fallback.captionBackground),
            strokeWidth = json.optDouble("strokeWidth", fallback.strokeWidth.toDouble()).toFloat(),
            shadowDistance = json.optDouble("shadowDistance", fallback.shadowDistance.toDouble()).toFloat(),
            cornerRadius = json.optDouble("cornerRadius", fallback.cornerRadius.toDouble()).toFloat(),
            positionX = json.optDouble("positionX", fallback.positionX.toDouble()).toFloat(),
            positionY = json.optDouble("positionY", fallback.positionY.toDouble()).toFloat(),
            alignment = json.optInt("alignment", fallback.alignment),
            lineSpacing = json.optDouble("lineSpacing", fallback.lineSpacing.toDouble()).toFloat(),
            wordSpacing = json.optDouble("wordSpacing", fallback.wordSpacing.toDouble()).toFloat(),
            activeWordEnabled = json.optBoolean("activeWordEnabled", fallback.activeWordEnabled),
            activeBackgroundEnabled = json.optBoolean("activeBackgroundEnabled", fallback.activeBackgroundEnabled),
            strokeEnabled = json.optBoolean("strokeEnabled", fallback.strokeEnabled),
            shadowEnabled = json.optBoolean("shadowEnabled", fallback.shadowEnabled),
            captionBackgroundEnabled = json.optBoolean("captionBackgroundEnabled", fallback.captionBackgroundEnabled),
            effect = runCatching { CaptionEffect.valueOf(json.optString("effect")) }.getOrDefault(fallback.effect),
            breaks = BreakSettings(
                mode = runCatching { BreakMode.valueOf(breaksJson?.optString("mode") ?: fallback.breaks.mode.name) }.getOrDefault(fallback.breaks.mode),
                linesPerPage = (breaksJson?.optInt("linesPerPage", fallback.breaks.linesPerPage) ?: fallback.breaks.linesPerPage).coerceIn(1, 4),
                wordsPerLine = (breaksJson?.optInt("wordsPerLine", fallback.breaks.wordsPerLine) ?: fallback.breaks.wordsPerLine).coerceIn(1, 20),
                capitalization = breaksJson?.optString("capitalization", fallback.breaks.capitalization) ?: fallback.breaks.capitalization
            )
        )
    }

    private fun transformToJson(transform: VideoTransform) = JSONObject().apply {
        put("trimStartMs", transform.trimStartMs)
        put("trimEndMs", transform.trimEndMs)
        put("aspectRatio", transform.aspectRatio.name)
        put("zoom", transform.zoom.toDouble())
        put("panX", transform.panX.toDouble())
        put("panY", transform.panY.toDouble())
    }

    private fun transformFromJson(json: JSONObject) = VideoTransform(
        trimStartMs = json.optLong("trimStartMs", 0L),
        trimEndMs = json.optLong("trimEndMs", 0L),
        aspectRatio = runCatching { AspectRatio.valueOf(json.optString("aspectRatio")) }.getOrDefault(AspectRatio.ORIGINAL),
        zoom = json.optDouble("zoom", 1.0).toFloat().coerceAtLeast(1f),
        panX = json.optDouble("panX", 0.0).toFloat().coerceIn(-1f, 1f),
        panY = json.optDouble("panY", 0.0).toFloat().coerceIn(-1f, 1f)
    )
}

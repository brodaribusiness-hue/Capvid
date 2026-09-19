package com.saad.capvid.caption

import com.saad.capvid.model.BreakSettings
import com.saad.capvid.model.CaptionEffect
import com.saad.capvid.model.CaptionStyle

/** The single source of truth for preview and export style presets. */
object StyleCatalog {
    const val STYLE_COUNT = 18

    private fun style(
        id: String,
        name: String,
        fontAsset: String,
        fontFamily: String,
        size: Float,
        text: String,
        active: String,
        effect: CaptionEffect,
        stroke: String = "#000000",
        strokeWidth: Float = 0f,
        shadow: String = "#000000",
        shadowDistance: Float = 0f,
        background: String = "#000000",
        activeBackground: String = "#A78BFA",
        x: Float = 0.5f,
        y: Float = 0.78f,
        alignment: Int = 2,
        activeWord: Boolean = true,
        activeBox: Boolean = false,
        strokeOn: Boolean = false,
        shadowOn: Boolean = false,
        captionBox: Boolean = false
    ) = CaptionStyle(
        id = id,
        name = name,
        fontAsset = fontAsset,
        fontFamily = fontFamily,
        fontSizeSp = size,
        textColor = text,
        activeColor = active,
        activeBackground = activeBackground,
        strokeColor = stroke,
        shadowColor = shadow,
        captionBackground = background,
        strokeWidth = strokeWidth,
        shadowDistance = shadowDistance,
        cornerRadius = 10f,
        positionX = x,
        positionY = y,
        alignment = alignment,
        lineSpacing = 1.05f,
        wordSpacing = 0f,
        activeWordEnabled = activeWord,
        activeBackgroundEnabled = activeBox,
        strokeEnabled = strokeOn,
        shadowEnabled = shadowOn,
        captionBackgroundEnabled = captionBox,
        effect = effect,
        breaks = BreakSettings()
    )

    val all: List<CaptionStyle> = listOf(
        style("pulse", "Pulse", "Montserrat-ExtraBold.ttf", "Montserrat ExtraBold", 42f, "#FFFFFF", "#A78BFA", CaptionEffect.POP, stroke = "#191B21", strokeWidth = 2f, shadow = "#000000", shadowDistance = 4f, shadowOn = true),
        style("karaoke", "Karaoke", "Roboto-Bold.ttf", "Roboto", 40f, "#D7D2E8", "#FDE68A", CaptionEffect.KARAOKE, stroke = "#101114", strokeWidth = 2f, strokeOn = true),
        style("aurora", "Aurora Glow", "OpenSans-SemiBold.ttf", "Open Sans", 40f, "#FFFFFF", "#86EFAC", CaptionEffect.GLOW, shadow = "#86EFAC", shadowDistance = 6f, shadowOn = true),
        style("neon", "Neon", "Oswald-Bold.ttf", "Oswald", 44f, "#F0FDFA", "#67E8F9", CaptionEffect.NEON, stroke = "#22D3EE", strokeWidth = 3f, shadow = "#0891B2", shadowDistance = 8f, shadowOn = true, strokeOn = true),
        style("typewriter", "Typewriter", "RobotoMono-Regular.ttf", "Roboto Mono", 34f, "#F4F4F5", "#FACC15", CaptionEffect.TYPEWRITER, stroke = "#27272A", strokeWidth = 1f, strokeOn = true),
        style("bounce", "Bounce", "BebasNeue-Regular.ttf", "Bebas Neue", 48f, "#FFFFFF", "#FB7185", CaptionEffect.BOUNCE, stroke = "#18181B", strokeWidth = 3f, shadow = "#000000", shadowDistance = 5f, shadowOn = true, strokeOn = true),
        style("marker", "Marker", "Caveat-Bold.ttf", "Caveat", 48f, "#FEFCE8", "#FACC15", CaptionEffect.HIGHLIGHT_BOX, activeBox = true, activeBackground = "#854D0E", captionBox = true, background = "#27272A"),
        style("outline", "Outline", "Lato-Bold.ttf", "Lato", 42f, "#FFFFFF", "#FFFFFF", CaptionEffect.OUTLINE, stroke = "#000000", strokeWidth = 5f, strokeOn = true),
        style("depth", "3D Depth", "Montserrat-ExtraBold.ttf", "Montserrat ExtraBold", 44f, "#FFFFFF", "#FCA5A5", CaptionEffect.SHADOW_3D, stroke = "#7F1D1D", strokeWidth = 2f, shadow = "#7F1D1D", shadowDistance = 8f, shadowOn = true, strokeOn = true),
        style("flip", "Flip", "Oswald-Bold.ttf", "Oswald", 42f, "#FFFFFF", "#C4B5FD", CaptionEffect.FLIP_3D, stroke = "#312E81", strokeWidth = 2f, strokeOn = true),
        style("wave", "Wave", "Pacifico-Regular.ttf", "Pacifico", 38f, "#FFFFFF", "#F9A8D4", CaptionEffect.WAVE, shadow = "#701A75", shadowDistance = 3f, shadowOn = true),
        style("swing", "Swing", "Lobster-Regular.ttf", "Lobster", 40f, "#FFFFFF", "#FDBA74", CaptionEffect.SWING, stroke = "#431407", strokeWidth = 2f, strokeOn = true),
        style("word-pop", "Word Pop", "Merriweather-Bold.ttf", "Merriweather", 36f, "#E4E4E7", "#4ADE80", CaptionEffect.WORD_BY_WORD, stroke = "#18181B", strokeWidth = 2f, strokeOn = true),
        style("comic", "Comic", "AmaticSC-Bold.ttf", "Amatic SC", 52f, "#FEF3C7", "#FB923C", CaptionEffect.COMIC, stroke = "#431407", strokeWidth = 3f, shadow = "#000000", shadowDistance = 4f, shadowOn = true, strokeOn = true),
        style("gradient", "Gradient", "PlayfairDisplay-Bold.ttf", "Playfair Display", 38f, "#F5D0FE", "#F0ABFC", CaptionEffect.GRADIENT, stroke = "#581C87", strokeWidth = 2f, strokeOn = true),
        style("focus", "Focus", "CormorantGaramond-Bold.ttf", "Cormorant Garamond", 43f, "#D4D4D8", "#FFFFFF", CaptionEffect.FOCUS, shadow = "#000000", shadowDistance = 4f, shadowOn = true),
        style("glitch", "Glitch", "JetBrainsMono-Bold.ttf", "JetBrains Mono", 35f, "#E0F2FE", "#F0FDFA", CaptionEffect.GLITCH, stroke = "#0EA5E9", strokeWidth = 2f, shadow = "#F43F5E", shadowDistance = 3f, shadowOn = true, strokeOn = true),
        style("clean", "Clean", "NotoSansArabic-Bold.ttf", "Noto Sans Arabic", 39f, "#FFFFFF", "#A78BFA", CaptionEffect.MINIMAL, stroke = "#000000", strokeWidth = 1f, shadow = "#000000", shadowDistance = 3f, shadowOn = true, strokeOn = true)
    )

    fun defaultStyle(): CaptionStyle = all.first()

    fun find(id: String): CaptionStyle = all.firstOrNull { it.id == id } ?: defaultStyle()
}

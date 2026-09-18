package com.saad.capvid.font

import android.content.Context
import android.graphics.Typeface
import com.saad.capvid.caption.StyleCatalog
import com.saad.capvid.model.CaptionStyle

class FontManager(private val context: Context) {
    private val cache = mutableMapOf<String, Typeface>()

    fun typeface(style: CaptionStyle): Typeface = cache.getOrPut(style.fontAsset) {
        runCatching { Typeface.createFromAsset(context.assets, "fonts/${style.fontAsset}") }
            .getOrElse { Typeface.create(style.fontFamily, Typeface.BOLD) }
    }

    fun bundledFonts(): List<CaptionStyle> = StyleCatalog.all

    fun hasBundledFont(assetName: String): Boolean = runCatching {
        context.assets.open("fonts/$assetName").use { true }
    }.getOrDefault(false)
}

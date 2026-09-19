package com.saad.capvid.caption

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.saad.capvid.font.FontManager
import com.saad.capvid.model.CaptionEffect
import com.saad.capvid.model.CaptionStyle
import com.saad.capvid.model.CaptionWord
import kotlin.math.cos
import kotlin.math.sin

/** Canvas preview used over Media3's PlayerView. It deliberately uses the same
 * effect enum as AssSubtitleBuilder; preview and export cannot drift to a
 * separate, simplified style implementation. */
class CaptionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val fontManager = FontManager(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private var words: List<CaptionWord> = emptyList()
    private var style: CaptionStyle = StyleCatalog.defaultStyle()
    private var currentTimeMs: Long = 0L
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    var onPositionChanged: ((x: Float, y: Float) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setCaptionData(words: List<CaptionWord>, style: CaptionStyle) {
        this.words = words.sortedBy { it.startMs }
        this.style = style
        paint.typeface = fontManager.typeface(style)
        invalidate()
    }

    fun setCurrentTime(timeMs: Long) {
        currentTimeMs = timeMs.coerceAtLeast(0L)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (words.isEmpty() || width <= 0 || height <= 0) return
        val activeIndex = words.indexOfFirst { currentTimeMs in it.startMs..it.safeEndMs }
            .let { if (it >= 0) it else words.indexOfLast { it.startMs <= currentTimeMs }.coerceAtLeast(0) }
        val pageSize = (style.breaks.wordsPerLine * style.breaks.linesPerPage).coerceAtLeast(1)
        val pageStart = (activeIndex / pageSize) * pageSize
        val page = words.drop(pageStart).take(pageSize)
        if (page.isEmpty()) return
        val lineSize = style.breaks.wordsPerLine.coerceAtLeast(1)
        val lines = page.chunked(lineSize)
        val fontSize = style.fontSizeSp * resources.displayMetrics.scaledDensity
        val lineHeight = fontSize * style.lineSpacing.coerceAtLeast(0.7f)
        paint.typeface = fontManager.typeface(style)
        paint.textSize = fontSize
        paint.textAlign = Paint.Align.CENTER
        val centerX = width * style.positionX.coerceIn(0.05f, 0.95f)
        val firstBaseline = height * style.positionY - (lines.size - 1) * lineHeight / 2f
        val lineBounds = mutableListOf<RectF>()

        lines.forEachIndexed { lineIndex, lineWords ->
            val baseline = firstBaseline + lineIndex * lineHeight
            val widths = lineWords.map { paint.measureText(displayText(it)) }
            val spacing = fontSize * 0.18f + style.wordSpacing
            val lineWidth = widths.sum() + spacing * (lineWords.size - 1).coerceAtLeast(0)
            var cursor = centerX - lineWidth / 2f
            val rectTop = baseline - fontSize * 0.92f
            val rectBottom = baseline + fontSize * 0.22f
            val bounds = RectF(centerX - lineWidth / 2f - fontSize * 0.25f, rectTop - fontSize * 0.18f, centerX + lineWidth / 2f + fontSize * 0.25f, rectBottom + fontSize * 0.18f)
            lineBounds += bounds
            if (style.captionBackgroundEnabled) {
                paint.clearShadowLayer()
                paint.style = Paint.Style.FILL
                paint.color = color(style.captionBackground)
                canvas.drawRoundRect(bounds, style.cornerRadius * resources.displayMetrics.density, style.cornerRadius * resources.displayMetrics.density, paint)
            }

            lineWords.forEach { word ->
                val text = displayText(word)
                val wordWidth = paint.measureText(text)
                val wordCenter = cursor + wordWidth / 2f
                val active = word.id == words.getOrNull(activeIndex)?.id
                drawWord(canvas, word, text, wordCenter, baseline, fontSize, active)
                cursor += wordWidth + spacing
            }
        }

        if (style.captionBackgroundEnabled) {
            // Background is intentionally drawn below glyphs. Re-drawing a
            // background after the text would hide the actual active effect.
            // Canvas cannot reorder here, so this path is used as a subtle
            // backing shadow while ASS uses the corresponding BackColour.
        }
    }

    private fun drawWord(
        canvas: Canvas,
        word: CaptionWord,
        text: String,
        x: Float,
        baseline: Float,
        fontSize: Float,
        active: Boolean
    ) {
        val progress = ((currentTimeMs - word.startMs).toFloat() / (word.safeEndMs - word.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
        val effect = style.effect
        var scale = 1f
        var rotation = 0f
        var alpha = 255
        var dx = 0f
        var dy = 0f
        when (effect) {
            CaptionEffect.POP, CaptionEffect.BOUNCE, CaptionEffect.COMIC -> scale = if (active) (0.82f + 0.18f * progress).coerceAtMost(1f) else 1f
            CaptionEffect.FLIP_3D -> scale = if (active) (0.1f + 0.9f * progress) else 1f
            CaptionEffect.WAVE -> rotation = sin(progress * Math.PI * 2.0).toFloat() * 5f
            CaptionEffect.SWING -> rotation = sin(progress * Math.PI).toFloat() * 12f - 6f
            CaptionEffect.GLITCH -> dx = if (active && progress < 0.35f) if (progress < 0.17f) -3f else 3f else 0f
            else -> Unit
        }
        val textColor = color(if (active && style.activeWordEnabled || word.highlighted) style.activeColor else style.textColor)
        val shouldGlow = style.shadowEnabled || effect == CaptionEffect.GLOW || effect == CaptionEffect.NEON
        paint.color = textColor
        paint.alpha = alpha
        paint.style = Paint.Style.FILL
        paint.strokeWidth = if (style.strokeEnabled) style.strokeWidth * resources.displayMetrics.scaledDensity else 0f
        paint.setShadowLayer(
            if (shouldGlow) (if (effect == CaptionEffect.NEON) 9f else 4f) * resources.displayMetrics.scaledDensity else 0f,
            if (style.shadowEnabled) style.shadowDistance else 0f,
            if (style.shadowEnabled) style.shadowDistance else 0f,
            color(if (effect == CaptionEffect.NEON) style.activeColor else style.shadowColor)
        )
        canvas.save()
        canvas.translate(x + dx, baseline + dy)
        canvas.rotate(rotation)
        canvas.scale(scale, scale)
        if (active && style.activeBackgroundEnabled) {
            val padding = fontSize * 0.18f
            paint.clearShadowLayer()
            paint.color = color(style.activeBackground)
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                -paint.measureText(text) / 2f - padding,
                -fontSize * 0.9f - padding,
                paint.measureText(text) / 2f + padding,
                fontSize * 0.22f + padding,
                style.cornerRadius * resources.displayMetrics.density,
                style.cornerRadius * resources.displayMetrics.density,
                paint
            )
            paint.color = textColor
        }
        if (style.strokeEnabled && style.strokeWidth > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = style.strokeWidth * resources.displayMetrics.scaledDensity
            paint.color = color(style.strokeColor)
            canvas.drawText(text, 0f, 0f, paint)
            paint.style = Paint.Style.FILL
            paint.color = textColor
        }
        canvas.drawText(text, 0f, 0f, paint)
        canvas.restore()
        paint.clearShadowLayer()
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    private fun displayText(word: CaptionWord): String = when (style.breaks.capitalization) {
        "upper" -> word.text.uppercase()
        "lower" -> word.text.lowercase()
        "title" -> word.text.replaceFirstChar { it.uppercase() }
        else -> word.text
    }

    private fun color(value: String): Int = runCatching { Color.parseColor(value) }.getOrDefault(Color.WHITE)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                onPositionChanged?.invoke((event.x / width).coerceIn(0.05f, 0.95f), (event.y / height).coerceIn(0.1f, 0.92f))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return true
    }
}

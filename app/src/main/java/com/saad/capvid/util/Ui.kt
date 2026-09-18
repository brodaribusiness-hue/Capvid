package com.saad.capvid.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.saad.capvid.R
import kotlin.math.roundToInt

object Ui {
    fun dp(context: Context, value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    fun text(context: Context, value: String, sizeSp: Float = 14f, color: Int = ContextCompat.getColor(context, R.color.capvid_text)): TextView =
        TextView(context).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            includeFontPadding = false
        }

    fun button(context: Context, label: String, primary: Boolean = false): Button = Button(context).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        setTextColor(if (primary) Color.rgb(20, 18, 30) else ContextCompat.getColor(context, R.color.capvid_text))
        background = rounded(
            context,
            if (primary) ContextCompat.getColor(context, R.color.capvid_purple) else ContextCompat.getColor(context, R.color.capvid_surface_alt),
            14f
        )
        minHeight = dp(context, 44f)
        stateListAnimator = null
        setPadding(dp(context, 18f), 0, dp(context, 18f), 0)
    }

    fun rounded(context: Context, fill: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(context, radiusDp).toFloat()
        setColor(fill)
        stroke?.let { setStroke(dp(context, 1f), it) }
    }

    fun sectionTitle(context: Context, value: String): TextView = text(context, value, 12f, ContextCompat.getColor(context, R.color.capvid_muted)).apply {
        letterSpacing = 0.08f
        setPadding(0, dp(context, 16f), 0, dp(context, 8f))
    }

    fun horizontal(context: Context, spacingDp: Float = 8f): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        showDividers = LinearLayout.SHOW_DIVIDER_NONE
        val spacing = dp(context, spacingDp)
        setPadding(spacing, 0, spacing, 0)
    }

    fun vertical(context: Context, paddingDp: Float = 0f): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(context, paddingDp)
        setPadding(padding, padding, padding, padding)
    }

    fun weight(params: ViewGroup.LayoutParams, weight: Float = 1f): LinearLayout.LayoutParams =
        (params as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)).apply { this.weight = weight }

    fun TextView.margins(context: Context, left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f) {
        layoutParams = (layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)).apply {
            if (this is ViewGroup.MarginLayoutParams) setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom))
        }
    }

}

package com.saad.capvid.scale

import android.app.Dialog
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import androidx.core.content.ContextCompat
import com.saad.capvid.R
import com.saad.capvid.model.AspectRatio
import com.saad.capvid.model.VideoTransform
import com.saad.capvid.util.Ui

class ScaleDialog(
    context: Context,
    private val initial: VideoTransform,
    private val onApply: (VideoTransform) -> Unit
) : Dialog(context) {
    private val aspect = Spinner(context)
    private val zoom = SeekBar(context)
    private val zoomLabel = Ui.text(context, "Zoom 100%", 13f, ContextCompat.getColor(context, R.color.capvid_muted))
    private val panX = SeekBar(context)
    private val panY = SeekBar(context)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val body = Ui.vertical(context, 18f).apply { background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface), 22f) }
        body.addView(Ui.text(context, "Scale & crop", 21f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
        body.addView(Ui.text(context, "Crop to a platform ratio or make a precise pan and zoom adjustment.", 13f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, Ui.dp(context, 5f), 0, Ui.dp(context, 10f)) })
        aspect.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, AspectRatio.entries.map { it.label })
        aspect.setSelection(AspectRatio.entries.indexOf(initial.aspectRatio).coerceAtLeast(0))
        aspect.background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface_alt), 12f)
        body.addView(Ui.sectionTitle(context, "ASPECT RATIO"))
        body.addView(aspect, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 50f)))
        zoom.max = 100
        zoom.progress = ((initial.zoom - 1f) * 100f).toInt().coerceIn(0, 100)
        body.addView(zoomLabel.apply { setPadding(0, Ui.dp(context, 14f), 0, 0) })
        body.addView(zoom)
        body.addView(Ui.text(context, "Pan horizontal", 12f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, Ui.dp(context, 10f), 0, 0) })
        panX.max = 200
        panX.progress = ((initial.panX + 1f) * 100f).toInt().coerceIn(0, 200)
        body.addView(panX)
        body.addView(Ui.text(context, "Pan vertical", 12f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, Ui.dp(context, 10f), 0, 0) })
        panY.max = 200
        panY.progress = ((initial.panY + 1f) * 100f).toInt().coerceIn(0, 200)
        body.addView(panY)
        val buttons = Ui.horizontal(context, 8f)
        buttons.addView(Ui.button(context, "Cancel").apply { setOnClickListener { dismiss() } }, LinearLayout.LayoutParams(0, Ui.dp(context, 48f), 1f))
        buttons.addView(Ui.button(context, "Apply scale", true).apply { setOnClickListener { applyAndClose() } }, LinearLayout.LayoutParams(0, Ui.dp(context, 48f), 1f))
        body.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(context, 16f) })
        zoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { zoomLabel.text = "Zoom ${100 + progress}%" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        zoomLabel.text = "Zoom ${100 + zoom.progress}%"
        setContentView(body)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun applyAndClose() {
        val ratio = AspectRatio.entries.getOrElse(aspect.selectedItemPosition) { AspectRatio.ORIGINAL }
        onApply(initial.copy(
            aspectRatio = ratio,
            zoom = 1f + zoom.progress / 100f,
            panX = (panX.progress - 100) / 100f,
            panY = (panY.progress - 100) / 100f
        ))
        dismiss()
    }
}

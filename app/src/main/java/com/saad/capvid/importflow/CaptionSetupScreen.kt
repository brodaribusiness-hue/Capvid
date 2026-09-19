package com.saad.capvid.importflow

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.saad.capvid.R
import com.saad.capvid.model.CaptionLanguage
import com.saad.capvid.util.Ui
import java.io.File

class CaptionSetupScreen(
    context: Context,
    private val videoFile: File,
    private val callbacks: Callbacks
) : ScrollView(context) {
    interface Callbacks {
        fun onProceed(language: CaptionLanguage, addCaptions: Boolean)
        fun onCancel()
    }

    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val progressLabel = Ui.text(context, "Ready to transcribe offline", 13f, ContextCompat.getColor(context, R.color.capvid_muted))
    private val proceed = Ui.button(context, "Proceed", true)
    private val languageSpinner = Spinner(context)
    private val addCaptions = Switch(context).apply {
        text = "Generate word-level captions"
        textSize = 15f
        setTextColor(ContextCompat.getColor(context, R.color.capvid_text))
        isChecked = true
        buttonTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, R.color.capvid_purple))
    }

    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.capvid_bg))
        isFillViewport = true
        val body = Ui.vertical(context, 22f)
        val top = Ui.horizontal(context, 0f)
        top.addView(Ui.button(context, "‹  Back").apply { setOnClickListener { callbacks.onCancel() } })
        body.addView(top)
        body.addView(Ui.text(context, "Set up captions", 28f).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, Ui.dp(context, 24f), 0, Ui.dp(context, 6f))
        })
        body.addView(Ui.text(context, "${videoFile.name}\nThe original video is copied to Capvid's private storage.", 13f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setLineSpacing(2f, 1f) })
        body.addView(Ui.sectionTitle(context, "CAPTION LANGUAGE"))
        languageSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, CaptionLanguage.entries.map { it.label })
        languageSpinner.background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface_alt), 14f)
        languageSpinner.setPadding(Ui.dp(context, 12f), 0, Ui.dp(context, 12f), 0)
        body.addView(languageSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 52f)))
        body.addView(addCaptions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 56f)).apply { topMargin = Ui.dp(context, 10f) })
        val hint = Ui.vertical(context, 14f).apply {
            background = Ui.rounded(context, Color.argb(30, 167, 139, 250), 16f)
        }
        hint.addView(Ui.text(context, "On-device AI", 14f, ContextCompat.getColor(context, R.color.capvid_purple)).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
        hint.addView(Ui.text(context, "The first transcription downloads the multilingual tiny model once. After that, transcription works without a network connection. Your video and model stay on this device.", 13f, ContextCompat.getColor(context, R.color.capvid_muted)))
        body.addView(hint, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(context, 16f) })
        body.addView(Ui.sectionTitle(context, "PROGRESS"))
        progress.max = 100
        progress.progress = 0
        progress.progressTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, R.color.capvid_purple))
        body.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 8f)))
        body.addView(progressLabel.apply { setPadding(0, Ui.dp(context, 8f), 0, 0) })
        body.addView(proceed.apply {
            setOnClickListener {
                val language = CaptionLanguage.entries.getOrElse(languageSpinner.selectedItemPosition) { CaptionLanguage.ENGLISH }
                callbacks.onProceed(language, addCaptions.isChecked)
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 52f)).apply { topMargin = Ui.dp(context, 28f) })
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setProgress(phase: String, percent: Int) {
        progress.progress = percent.coerceIn(0, 100)
        progressLabel.text = "$phase  •  ${percent.coerceIn(0, 100)}%"
    }

    fun setBusy(busy: Boolean) {
        proceed.isEnabled = !busy
        proceed.text = if (busy) "Working…" else "Proceed"
        languageSpinner.isEnabled = !busy
        addCaptions.isEnabled = !busy
    }
}

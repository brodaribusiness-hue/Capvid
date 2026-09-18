package com.saad.capvid.trim

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import com.saad.capvid.R
import com.saad.capvid.model.VideoTransform
import com.saad.capvid.util.Ui

class TrimDialog(
    context: Context,
    private val durationMs: Long,
    private val initial: VideoTransform,
    private val onApply: (VideoTransform) -> Unit,
    private val playheadMs: Long = 0L
) : Dialog(context) {
    private val start = SeekBar(context)
    private val end = SeekBar(context)
    private val startLabel = Ui.text(context, "In  0:00", 13f, ContextCompat.getColor(context, R.color.capvid_muted))
    private val endLabel = Ui.text(context, "Out  0:00", 13f, ContextCompat.getColor(context, R.color.capvid_muted))

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val body = Ui.vertical(context, 18f).apply { background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface), 22f) }
        body.addView(Ui.text(context, "Trim video", 21f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
        body.addView(Ui.text(context, "Set the in and out points. Captions follow the trimmed timeline on export.", 13f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, Ui.dp(context, 5f), 0, Ui.dp(context, 10f)) })
        val maxSeconds = (durationMs / 1000L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        start.max = maxSeconds
        end.max = maxSeconds
        start.progress = (initial.trimStartMs / 1000L).toInt().coerceIn(0, maxSeconds)
        end.progress = ((if (initial.trimEndMs > 0L) initial.trimEndMs else durationMs) / 1000L).toInt().coerceIn(1, maxSeconds)
        body.addView(startLabel)
        body.addView(start)
        body.addView(endLabel.apply { setPadding(0, Ui.dp(context, 8f), 0, 0) })
        body.addView(end)
        body.addView(Ui.text(context, "PLAYHEAD ACTIONS", 11f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, Ui.dp(context, 14f), 0, Ui.dp(context, 5f)) })
        val playheadActions = Ui.horizontal(context, 6f)
        playheadActions.addView(Ui.button(context, "Keep before playhead").apply {
            setOnClickListener {
                val point = playheadMs.coerceIn(100L, durationMs)
                onApply(initial.copy(trimStartMs = 0L, trimEndMs = point))
                dismiss()
            }
        }, LinearLayout.LayoutParams(0, Ui.dp(context, 44f), 1f))
        playheadActions.addView(Ui.button(context, "Keep after playhead").apply {
            setOnClickListener {
                val point = playheadMs.coerceIn(0L, maxOf(0L, durationMs - 100L))
                onApply(initial.copy(trimStartMs = point, trimEndMs = 0L))
                dismiss()
            }
        }, LinearLayout.LayoutParams(0, Ui.dp(context, 44f), 1f))
        body.addView(playheadActions)
        val buttons = Ui.horizontal(context, 8f)
        buttons.addView(Ui.button(context, "Cancel").apply { setOnClickListener { dismiss() } }, LinearLayout.LayoutParams(0, Ui.dp(context, 48f), 1f))
        buttons.addView(Ui.button(context, "Apply trim", true).apply { setOnClickListener { applyAndClose(maxSeconds) } }, LinearLayout.LayoutParams(0, Ui.dp(context, 48f), 1f))
        body.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(context, 16f) })
        start.setOnSeekBarChangeListener(listener { updateLabels(maxSeconds) })
        end.setOnSeekBarChangeListener(listener { updateLabels(maxSeconds) })
        updateLabels(maxSeconds)
        setContentView(body)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun applyAndClose(maxSeconds: Int) {
        val inMs = start.progress * 1000L
        val outMs = maxOf(inMs + 100L, end.progress * 1000L).coerceAtMost(durationMs)
        onApply(initial.copy(trimStartMs = inMs, trimEndMs = if (outMs >= durationMs - 500L) 0L else outMs))
        dismiss()
    }

    private fun updateLabels(maxSeconds: Int) {
        val inMs = start.progress * 1000L
        val outMs = end.progress * 1000L
        startLabel.text = "In  ${format(inMs)}"
        endLabel.text = "Out ${format(outMs)}"
        if (outMs <= inMs) end.progress = (start.progress + 1).coerceAtMost(maxSeconds)
    }

    private fun listener(block: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = block()
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun format(ms: Long): String = "%d:%02d".format(ms / 60_000L, (ms / 1000L) % 60L)
}

package com.saad.capvid.editor

import android.app.Dialog
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.saad.capvid.R
import com.saad.capvid.caption.CaptionOverlayView
import com.saad.capvid.model.CaptionWord
import com.saad.capvid.scale.ScaleDialog
import com.saad.capvid.style.StylePanelDialog
import com.saad.capvid.timeline.TimelineView
import com.saad.capvid.trim.TrimDialog
import com.saad.capvid.util.Ui
import kotlinx.coroutines.launch
import java.io.File

class EditorScreen(
    context: android.content.Context,
    private val viewModel: EditorViewModel,
    private val callbacks: Callbacks
) : LinearLayout(context) {
    interface Callbacks {
        fun onExitEditor()
    }

    private val player = ExoPlayer.Builder(context).build()
    private val playerView = PlayerView(context).apply {
        this.player = this@EditorScreen.player
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShutterBackgroundColor(Color.rgb(15, 16, 20))
    }
    private val overlay = CaptionOverlayView(context)
    private val timeline = TimelineView(context)
    private val timeLabel = Ui.text(context, "0:00 / 0:00", 12f, ContextCompat.getColor(context, R.color.capvid_muted))
    private val playButton = Ui.button(context, "▶")
    private val undoButton = Ui.button(context, "↶")
    private val redoButton = Ui.button(context, "↷")
    private val handler = Handler(Looper.getMainLooper())
    private var boundOwner: LifecycleOwner? = null
    private var lastProjectSnapshot: com.saad.capvid.model.Project? = null
    private var lastTimelineWords: List<com.saad.capvid.model.CaptionWord>? = null
    private var currentProject: com.saad.capvid.model.Project? = null
    private var lastDurationMs = 0L
    private val ticker = object : Runnable {
        override fun run() {
            val position = player.currentPosition.coerceAtLeast(0L)
            overlay.setCurrentTime(position)
            timeline.setCurrentTime(position)
            timeLabel.text = "${format(position)} / ${format(lastDurationMs)}"
            handler.postDelayed(this, 100L)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.capvid_bg))
        buildTopBar()
        buildPreview()
        buildTransport()
        buildTimeline()
        buildFunctionTabs()
        overlay.onPositionChanged = { x, y ->
            currentProject?.let { viewModel.setStyle(it.style.copy(positionX = x, positionY = y)) }
        }
        timeline.onWordClick = { word -> player.seekTo(word.startMs); showWordEditor(word) }
        timeline.onAddClick = ::showAddCaption
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playButton.text = if (isPlaying) "Ⅱ" else "▶" }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) lastDurationMs = player.duration.coerceAtLeast(0L)
            }
        })
        handler.post(ticker)
    }

    fun bind(owner: LifecycleOwner) {
        if (boundOwner === owner) return
        boundOwner = owner
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.project.collect { project ->
                        if (project != null) updateProject(project)
                    }
                }
                launch {
                    viewModel.export.collect { export ->
                        if (!export.running && export.uri != null) Toast.makeText(context, "Export saved to your gallery", Toast.LENGTH_LONG).show()
                        if (!export.running && export.error != null) Toast.makeText(context, export.error, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun buildTopBar() {
        val bar = Ui.horizontal(context, 5f).apply { setPadding(Ui.dp(context, 10f), Ui.dp(context, 8f), Ui.dp(context, 10f), Ui.dp(context, 6f)) }
        bar.addView(Ui.button(context, "×").apply { contentDescription = "Exit editor"; setOnClickListener { callbacks.onExitEditor() } })
        bar.addView(Ui.text(context, "Editor", 18f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(Ui.dp(context, 8f), 0, 0, 0) }, LinearLayout.LayoutParams(0, Ui.dp(context, 48f), 1f))
        bar.addView(Ui.button(context, "?").apply { contentDescription = "Help"; setOnClickListener { showHelp() } })
        bar.addView(Ui.button(context, "Save").apply { contentDescription = "Save project"; setOnClickListener { viewModel.save(); Toast.makeText(context, "Project saved", Toast.LENGTH_SHORT).show() } })
        val quality = Spinner(context).apply {
            adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf("Original quality", "Preview quality"))
            contentDescription = "Quality selector"
        }
        bar.addView(quality, LinearLayout.LayoutParams(Ui.dp(context, 126f), Ui.dp(context, 48f)))
        bar.addView(Ui.button(context, "Export", true).apply {
            contentDescription = "Export video"
            setOnClickListener { viewModel.save(); viewModel.exportToGallery() }
        })
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 62f)))
    }

    private fun buildPreview() {
        val preview = android.widget.FrameLayout(context).apply { setBackgroundColor(Color.rgb(8, 9, 12)) }
        preview.addView(playerView, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        preview.addView(overlay, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun buildTransport() {
        val row = Ui.horizontal(context, 6f).apply { setPadding(Ui.dp(context, 10f), Ui.dp(context, 6f), Ui.dp(context, 10f), Ui.dp(context, 6f)) }
        playButton.contentDescription = "Play or pause"
        playButton.setOnClickListener { if (player.isPlaying) player.pause() else player.play() }
        row.addView(playButton)
        row.addView(timeLabel, LinearLayout.LayoutParams(0, Ui.dp(context, 44f), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        undoButton.contentDescription = "Undo"
        redoButton.contentDescription = "Redo"
        undoButton.setOnClickListener { viewModel.undo(); refreshHistoryButtons() }
        redoButton.setOnClickListener { viewModel.redo(); refreshHistoryButtons() }
        row.addView(undoButton)
        row.addView(redoButton)
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 56f)))
    }

    private fun buildTimeline() {
        addView(timeline, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 133f)))
    }

    private fun buildFunctionTabs() {
        val tabs = Ui.horizontal(context, 4f).apply { setPadding(Ui.dp(context, 8f), Ui.dp(context, 6f), Ui.dp(context, 8f), Ui.dp(context, 10f)) }
        tabs.addView(Ui.button(context, "Captions").apply { setOnClickListener { showCaptionTools() } }, LinearLayout.LayoutParams(0, Ui.dp(context, 46f), 1f))
        tabs.addView(Ui.button(context, "Style").apply { setOnClickListener { showStylePanel() } }, LinearLayout.LayoutParams(0, Ui.dp(context, 46f), 1f))
        tabs.addView(Ui.button(context, "Trim").apply { setOnClickListener { showTrim() } }, LinearLayout.LayoutParams(0, Ui.dp(context, 46f), 1f))
        tabs.addView(Ui.button(context, "Scale").apply { setOnClickListener { showScale() } }, LinearLayout.LayoutParams(0, Ui.dp(context, 46f), 1f))
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 64f)))
    }

    private fun updateProject(project: com.saad.capvid.model.Project) {
        currentProject = project
        overlay.setCaptionData(project.words, project.style)
        if (project.words != lastTimelineWords || project.videoPath != lastProjectSnapshot?.videoPath) {
            timeline.setData(project.words, player.currentPosition, project.videoPath)
            lastTimelineWords = project.words
            refreshHistoryButtons()
        }
        if (project != lastProjectSnapshot) {
            lastProjectSnapshot = project
            refreshHistoryButtons()
        }
        if (player.currentMediaItem == null || player.currentMediaItem?.localConfiguration?.uri?.path != project.videoPath) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(project.videoPath))))
            player.prepare()
            player.playWhenReady = false
        }
        lastDurationMs = project.durationMs.coerceAtLeast(player.duration.takeIf { it > 0 } ?: 0L)
    }

    private fun refreshHistoryButtons() {
        undoButton.isEnabled = viewModel.canUndo()
        redoButton.isEnabled = viewModel.canRedo()
        undoButton.alpha = if (undoButton.isEnabled) 1f else 0.45f
        redoButton.alpha = if (redoButton.isEnabled) 1f else 0.45f
    }

    private fun showCaptionTools() {
        val project = currentProject ?: return
        val dialog = Dialog(context)
        val body = Ui.vertical(context, 18f).apply { background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface), 22f) }
        body.addView(Ui.text(context, "Caption tools", 21f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
        body.addView(Ui.text(context, "Select a word in the timeline to edit its text and timing, or add a manual caption.", 13f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, 7, 0, 15) })
        body.addView(Ui.button(context, "Add manual caption", true).apply { setOnClickListener { dialog.dismiss(); showAddCaption() } })
        body.addView(Ui.button(context, "Edit selected word").apply {
            setOnClickListener {
                dialog.dismiss()
                project.words.firstOrNull { player.currentPosition in it.startMs..it.safeEndMs }?.let(::showWordEditor)
                    ?: Toast.makeText(context, "Tap a word chip first", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(context, 8f) })
        dialog.setContentView(body)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showWordEditor(word: CaptionWord) {
        val dialog = Dialog(context)
        val body = Ui.vertical(context, 18f).apply { background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface), 22f) }
        body.addView(Ui.text(context, "Edit word", 21f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
        val text = EditText(context).apply { setText(word.text); hint = "Caption text"; setSingleLine(true); setTextColor(ContextCompat.getColor(context, R.color.capvid_text)); background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface_alt), 10f); setPadding(14, 0, 14, 0) }
        val start = EditText(context).apply { setText(word.startMs.toString()); hint = "Start milliseconds"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(ContextCompat.getColor(context, R.color.capvid_text)); background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface_alt), 10f); setPadding(14, 0, 14, 0) }
        val end = EditText(context).apply { setText(word.endMs.toString()); hint = "End milliseconds"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(ContextCompat.getColor(context, R.color.capvid_text)); background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface_alt), 10f); setPadding(14, 0, 14, 0) }
        val highlight = android.widget.Switch(context).apply {
            this.text = "Highlight this word"
            isChecked = word.highlighted
            setTextColor(ContextCompat.getColor(context, R.color.capvid_text))
        }
        body.addView(text, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)))
        body.addView(start, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)).apply { topMargin = Ui.dp(context, 8f) })
        body.addView(end, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)).apply { topMargin = Ui.dp(context, 8f) })
        body.addView(highlight, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)).apply { topMargin = Ui.dp(context, 5f) })
        val buttons = Ui.horizontal(context, 8f)
        buttons.addView(Ui.button(context, "Delete").apply { setOnClickListener { viewModel.deleteWord(word.id); dialog.dismiss() } }, LinearLayout.LayoutParams(0, Ui.dp(context, 48f), 1f))
        buttons.addView(Ui.button(context, "Save", true).apply {
            setOnClickListener {
                viewModel.updateWordText(word.id, text.text.toString())
                viewModel.updateWordTiming(word.id, start.text.toString().toLongOrNull() ?: word.startMs, end.text.toString().toLongOrNull() ?: word.endMs)
                viewModel.setWordHighlighted(word.id, highlight.isChecked)
                dialog.dismiss()
            }
        }, LinearLayout.LayoutParams(0, Ui.dp(context, 48f), 1f))
        body.addView(buttons, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(context, 12f) })
        dialog.setContentView(body)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showAddCaption() {
        val dialog = Dialog(context)
        val body = Ui.vertical(context, 18f).apply { background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface), 22f) }
        body.addView(Ui.text(context, "Add caption", 21f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
        val text = EditText(context).apply { hint = "What should appear?"; setTextColor(ContextCompat.getColor(context, R.color.capvid_text)); background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface_alt), 10f); setPadding(14, 0, 14, 0) }
        val start = EditText(context).apply { hint = "Start ms"; setText(player.currentPosition.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(ContextCompat.getColor(context, R.color.capvid_text)); background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface_alt), 10f); setPadding(14, 0, 14, 0) }
        val end = EditText(context).apply { hint = "End ms"; setText((player.currentPosition + 1800L).toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(ContextCompat.getColor(context, R.color.capvid_text)); background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface_alt), 10f); setPadding(14, 0, 14, 0) }
        body.addView(text, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 52f)))
        body.addView(start, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)).apply { topMargin = Ui.dp(context, 8f) })
        body.addView(end, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)).apply { topMargin = Ui.dp(context, 8f) })
        body.addView(Ui.button(context, "Add caption", true).apply {
            setOnClickListener {
                viewModel.addManualCaption(text.text.toString(), start.text.toString().toLongOrNull() ?: player.currentPosition, end.text.toString().toLongOrNull() ?: player.currentPosition + 1800L)
                dialog.dismiss()
            }
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 50f)).apply { topMargin = Ui.dp(context, 12f) })
        dialog.setContentView(body)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showStylePanel() {
        currentProject?.let { project ->
            StylePanelDialog(context, project.style) { viewModel.setStyle(it) }.apply {
                window?.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), (resources.displayMetrics.heightPixels * 0.82f).toInt())
                show()
                window?.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), (resources.displayMetrics.heightPixels * 0.82f).toInt())
            }
        }
    }

    private fun showTrim() {
        currentProject?.let { project ->
            TrimDialog(
                context,
                project.durationMs,
                project.transform,
                { viewModel.setTransform(it) },
                player.currentPosition,
                { viewModel.splitAtPlayhead(player.currentPosition) },
                { viewModel.deleteClip(it) }
            ).apply {
                show(); window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), LayoutParams.WRAP_CONTENT)
            }
        }
    }

    private fun showScale() {
        currentProject?.let { project ->
            ScaleDialog(context, project.transform) { viewModel.setTransform(it) }.apply {
                show(); window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), LayoutParams.WRAP_CONTENT)
            }
        }
    }

    private fun showHelp() {
        val dialog = Dialog(context)
        val body = Ui.vertical(context, 18f).apply { background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface), 22f) }
        body.addView(Ui.text(context, "Capvid editor", 21f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
        body.addView(Ui.text(context, "Tap Play to preview word highlights. Tap a word chip to edit its text or timing. Drag the caption on the preview to reposition it. Style effects are rendered by libass during export, not flattened into plain text.", 14f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, 10, 0, 16) })
        body.addView(Ui.button(context, "Got it", true).apply { setOnClickListener { dialog.dismiss() } })
        dialog.setContentView(body)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun format(ms: Long): String = "%d:%02d".format(ms / 60_000L, (ms / 1000L) % 60L)

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        player.release()
        super.onDetachedFromWindow()
    }
}

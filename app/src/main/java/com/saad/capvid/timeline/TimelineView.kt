package com.saad.capvid.timeline

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.saad.capvid.R
import com.saad.capvid.model.CaptionWord
import com.saad.capvid.util.Ui
import java.io.File
import java.util.concurrent.Executors

class TimelineView(context: android.content.Context) : HorizontalScrollView(context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(context, 8f), Ui.dp(context, 8f), Ui.dp(context, 8f), Ui.dp(context, 8f)) }
    private val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val filmstrip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private var words: List<CaptionWord> = emptyList()
    private var currentTimeMs = 0L
    private var videoPath: String? = null
    var onWordClick: ((CaptionWord) -> Unit)? = null
    var onAddClick: (() -> Unit)? = null

    init {
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(ContextCompat.getColor(context, R.color.capvid_surface))
        content.addView(chips)
        content.addView(filmstrip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(context, 55f)).apply { topMargin = Ui.dp(context, 8f) })
        addView(content, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setData(words: List<CaptionWord>, currentTimeMs: Long, videoPath: String?) {
        this.words = words
        this.currentTimeMs = currentTimeMs
        this.videoPath = videoPath
        rebuild()
    }

    fun setCurrentTime(timeMs: Long) {
        currentTimeMs = timeMs
        chips.children().forEachIndexed { index, view ->
            if (index < words.size) styleChip(view as TextView, words[index])
        }
    }

    private fun rebuild() {
        chips.removeAllViews()
        filmstrip.removeAllViews()
        words.forEachIndexed { index, word ->
            val chip = Ui.text(context, word.text, 13f)
            chip.setPadding(Ui.dp(context, 12f), Ui.dp(context, 8f), Ui.dp(context, 12f), Ui.dp(context, 8f))
            chip.minWidth = Ui.dp(context, 52f)
            chip.gravity = Gravity.CENTER
            chip.isClickable = true
            chip.setOnClickListener { onWordClick?.invoke(word) }
            styleChip(chip, word)
            chips.addView(chip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(context, 38f)).apply { rightMargin = Ui.dp(context, 6f) })
            filmstrip.addView(thumbnailCell(word, index), LinearLayout.LayoutParams(Ui.dp(context, 88f), Ui.dp(context, 51f)).apply { rightMargin = Ui.dp(context, 6f) })
        }
        val add = Ui.text(context, "+", 24f, ContextCompat.getColor(context, R.color.capvid_purple)).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface_alt), 12f)
            setOnClickListener { onAddClick?.invoke() }
            contentDescription = "Add caption"
        }
        chips.addView(add, LinearLayout.LayoutParams(Ui.dp(context, 44f), Ui.dp(context, 38f)))
        loadThumbnails()
    }

    private fun styleChip(chip: TextView, word: CaptionWord) {
        val active = currentTimeMs in word.startMs..word.safeEndMs
        chip.background = Ui.rounded(context, if (active) ContextCompat.getColor(context, R.color.capvid_purple) else ContextCompat.getColor(context, R.color.capvid_surface_alt), 12f)
        chip.setTextColor(if (active) Color.rgb(28, 22, 45) else ContextCompat.getColor(context, R.color.capvid_text))
        chip.alpha = if (active) 1f else 0.78f
    }

    private fun thumbnailCell(word: CaptionWord, index: Int): FrameLayout = FrameLayout(context).apply {
        background = Ui.rounded(context, Color.rgb(35, 38, 48), 8f)
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; tag = "thumbnail-$index" }
        addView(image, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(Ui.text(context, formatTime(word.startMs), 10f, Color.WHITE).apply {
            setPadding(Ui.dp(context, 4f), Ui.dp(context, 3f), 0, 0)
            setBackgroundColor(Color.argb(120, 0, 0, 0))
        }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 22f), Gravity.BOTTOM))
    }

    private fun loadThumbnails() {
        val path = videoPath ?: return
        if (!File(path).isFile) return
        executor.execute {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                words.forEachIndexed { index, word ->
                    val bitmap: Bitmap? = retriever.getFrameAtTime(word.startMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    post {
                        val cell = filmstrip.getChildAt(index) as? FrameLayout ?: return@post
                        val image = cell.findViewWithTag<ImageView>("thumbnail-$index") ?: return@post
                        image.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Throwable) {
                // A provider may not expose random access; the timeline still
                // displays accurate time chips in that case.
            } finally {
                retriever.release()
            }
        }
    }

    private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60_000L, (ms / 1000L) % 60L)

    private fun LinearLayout.children(): List<android.view.View> = (0 until childCount).map { getChildAt(it) }

    override fun onDetachedFromWindow() {
        executor.shutdownNow()
        super.onDetachedFromWindow()
    }
}

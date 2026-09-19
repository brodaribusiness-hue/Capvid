package com.saad.capvid.home

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.saad.capvid.R
import com.saad.capvid.model.Project
import com.saad.capvid.project.ProjectStore
import com.saad.capvid.util.Ui

class HomeScreen(
    context: Context,
    private val callbacks: Callbacks
) : ScrollView(context) {
    interface Callbacks {
        fun onCaptionVideo()
        fun onRecordVideo()
        fun onOpenProject(project: Project)
    }

    private val content = Ui.vertical(context, 22f).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.capvid_bg))
    }

    init {
        isFillViewport = true
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        build()
    }

    fun refresh() {
        content.removeAllViews()
        build()
    }

    private fun build() {
        val header = Ui.horizontal(context, 0f).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 16f))
        }
        val brand = Ui.vertical(context, 0f)
        brand.addView(Ui.text(context, "CAPVID", 13f, ContextCompat.getColor(context, R.color.capvid_purple)))
        brand.addView(Ui.text(context, "Your story, word by word.", 25f, ContextCompat.getColor(context, R.color.capvid_text)).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, Ui.dp(context, 5f), 0, 0)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Ui.text(context, "OFFLINE", 11f, ContextCompat.getColor(context, R.color.capvid_green)).apply {
            background = Ui.rounded(context, Color.argb(35, 110, 231, 183), 20f)
            setPadding(Ui.dp(context, 11f), Ui.dp(context, 7f), Ui.dp(context, 11f), Ui.dp(context, 7f))
        })
        content.addView(header)

        val hero = Ui.vertical(context, 20f).apply {
            background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface), 22f)
        }
        hero.addView(Ui.text(context, "Caption videos\nwithout sending them anywhere.", 24f).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setLineSpacing(Ui.dp(context, 3f).toFloat(), 1f)
        })
        hero.addView(Ui.text(context, "Whisper transcription, expressive animated templates, and a real burn-in export — all on your device.", 14f, ContextCompat.getColor(context, R.color.capvid_muted)).apply {
            setPadding(0, Ui.dp(context, 10f), 0, Ui.dp(context, 16f))
        })
        val actions = Ui.horizontal(context, 10f)
        val create = Ui.button(context, "＋  Create", true).apply { setOnClickListener { showCreateMenu() } }
        val project = Ui.button(context, "Open project").apply { setOnClickListener { showProjectList() } }
        actions.addView(create, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(project, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        hero.addView(actions)
        content.addView(hero)

        content.addView(Ui.sectionTitle(context, "RECENT PROJECTS"))
        val recent = ProjectStore(context).loadAll().take(4)
        if (recent.isEmpty()) {
            content.addView(Ui.text(context, "Your saved projects will appear here.", 14f, ContextCompat.getColor(context, R.color.capvid_muted)).apply {
                setPadding(0, Ui.dp(context, 8f), 0, Ui.dp(context, 20f))
            })
        } else {
            recent.forEach { addProjectRow(it) }
        }
        content.addView(Ui.text(context, "Everything stays local  •  No account  •  No subscription", 12f, ContextCompat.getColor(context, R.color.capvid_muted)).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, Ui.dp(context, 30f), 0, Ui.dp(context, 16f))
        })
    }

    private fun addProjectRow(project: Project) {
        val row = Ui.horizontal(context, 14f).apply {
            background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface), 16f)
            isClickable = true
            setPadding(Ui.dp(context, 14f), Ui.dp(context, 14f), Ui.dp(context, 14f), Ui.dp(context, 14f))
            setOnClickListener { callbacks.onOpenProject(project) }
        }
        val icon = Ui.text(context, "▣", 24f, ContextCompat.getColor(context, R.color.capvid_purple))
        row.addView(icon, LinearLayout.LayoutParams(Ui.dp(context, 38f), Ui.dp(context, 38f)))
        val labels = Ui.vertical(context, 0f)
        labels.addView(Ui.text(context, project.name, 15f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
        labels.addView(Ui.text(context, "${project.words.size} words  •  ${formatDuration(project.durationMs)}", 12f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, Ui.dp(context, 4f), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Ui.text(context, "›", 25f, ContextCompat.getColor(context, R.color.capvid_muted)))
        content.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = Ui.dp(context, 8f) })
    }

    private fun showCreateMenu() {
        val dialog = android.app.Dialog(context)
        val body = Ui.vertical(context, 18f).apply {
            background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface), 22f)
        }
        body.addView(Ui.text(context, "Create a project", 21f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
        body.addView(Ui.text(context, "Choose how you want to start.", 13f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, 0, 0, Ui.dp(context, 10f)) })
        body.addView(Ui.button(context, "Caption a video", true).apply {
            setOnClickListener { dialog.dismiss(); callbacks.onCaptionVideo() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.addView(Ui.button(context, "Record a video").apply {
            setOnClickListener { dialog.dismiss(); callbacks.onRecordVideo() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(context, 8f) })
        dialog.setContentView(body)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt(), LayoutParams.WRAP_CONTENT)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt(), LayoutParams.WRAP_CONTENT)
    }

    private fun showProjectList() {
        val dialog = android.app.Dialog(context)
        val body = Ui.vertical(context, 18f).apply { background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface), 22f) }
        body.addView(Ui.text(context, "Saved projects", 21f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
        val projects = ProjectStore(context).loadAll()
        if (projects.isEmpty()) body.addView(Ui.text(context, "No projects saved yet.", 14f, ContextCompat.getColor(context, R.color.capvid_muted)))
        projects.forEach { project ->
            body.addView(Ui.button(context, project.name).apply {
                setOnClickListener { dialog.dismiss(); callbacks.onOpenProject(project) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(context, 6f) })
        }
        body.addView(Ui.button(context, "Close").apply { setOnClickListener { dialog.dismiss() } }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(context, 12f) })
        dialog.setContentView(body)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt(), LayoutParams.WRAP_CONTENT)
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000L
        return "%d:%02d".format(seconds / 60L, seconds % 60L)
    }
}

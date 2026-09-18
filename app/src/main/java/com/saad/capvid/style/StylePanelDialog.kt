package com.saad.capvid.style

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.saad.capvid.R
import com.saad.capvid.caption.StyleCatalog
import com.saad.capvid.font.FontManager
import com.saad.capvid.model.BreakMode
import com.saad.capvid.model.CaptionStyle
import com.saad.capvid.util.Ui

class StylePanelDialog(
    context: Context,
    current: CaptionStyle,
    private val onApply: (CaptionStyle) -> Unit
) : Dialog(context) {
    private var working = current
    private val body = Ui.vertical(context, 16f)
    private val tabContent = FrameLayout(context)
    private val colorFields = mutableMapOf<String, EditText>()
    private val toggles = mutableMapOf<String, Switch>()
    private var activeTab = 0
    private var fontAlignment = current.alignment

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        body.addView(Ui.text(context, "Caption style", 22f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
        body.addView(Ui.text(context, "Preview-ready styles are rendered again during export.", 13f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, 4, 0, 10) })
        val tabs = Ui.horizontal(context, 5f)
        listOf("Templates", "Color", "Fonts", "Breaks").forEachIndexed { index, label ->
            tabs.addView(Ui.button(context, label, index == activeTab).apply {
                setOnClickListener { if (activeTab == 1) captureColors(); activeTab = index; rebuildTabs(tabs) }
                tag = "tab-$index"
            }, LinearLayout.LayoutParams(0, Ui.dp(context, 42f), 1f))
        }
        body.addView(tabs)
        val scroll = ScrollView(context)
        scroll.addView(tabContent, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val footer = Ui.horizontal(context, 8f)
        footer.addView(Ui.button(context, "Cancel").apply { setOnClickListener { dismiss() } }, LinearLayout.LayoutParams(0, Ui.dp(context, 50f), 1f))
        footer.addView(Ui.button(context, "Apply", true).apply { setOnClickListener { applyAndClose() } }, LinearLayout.LayoutParams(0, Ui.dp(context, 50f), 1f))
        body.addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(context, 12f) })
        setContentView(body)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout((context.resources.displayMetrics.widthPixels * 0.94f).toInt(), (context.resources.displayMetrics.heightPixels * 0.84f).toInt())
        rebuildTabs(tabs)
    }

    private fun rebuildTabs(tabs: LinearLayout) {
        for (index in 0 until tabs.childCount) {
            val button = tabs.getChildAt(index) as? android.widget.Button ?: continue
            button.background = Ui.rounded(context, if (index == activeTab) ContextCompat.getColor(context, R.color.capvid_purple) else ContextCompat.getColor(context, R.color.capvid_surface_alt), 12f)
            button.setTextColor(if (index == activeTab) Color.rgb(28, 22, 45) else ContextCompat.getColor(context, R.color.capvid_text))
        }
        tabContent.removeAllViews()
        when (activeTab) {
            0 -> buildTemplates()
            1 -> buildColors()
            2 -> buildFonts()
            else -> buildBreaks()
        }
    }

    private fun buildTemplates() {
        val wrapper = Ui.vertical(context, 0f)
        wrapper.addView(Ui.text(context, "Choose a visual language", 14f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, 14, 0, 14) })
        val grid = GridLayout(context).apply {
            columnCount = 2
            useDefaultMargins = false
        }
        StyleCatalog.all.forEach { style ->
            val card = Ui.vertical(context, 11f).apply {
                background = Ui.rounded(context, if (style.id == working.id) Color.argb(45, 167, 139, 250) else ContextCompat.getColor(context, R.color.capvid_surface_alt), 14f, if (style.id == working.id) ContextCompat.getColor(context, R.color.capvid_purple) else null)
                isClickable = true
                setOnClickListener { working = style; buildTemplates() }
            }
            val sample = Ui.text(context, "Aa  ${style.name}", 18f, color(style.activeColor)).apply {
                typeface = FontManager(context).typeface(style)
            }
            card.addView(sample)
            card.addView(Ui.text(context, effectLabel(style), 11f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, 5, 0, 0) })
            grid.addView(card, GridLayout.LayoutParams().apply {
                width = 0
                height = Ui.dp(context, 84f)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(Ui.dp(context, 4f), Ui.dp(context, 4f), Ui.dp(context, 4f), Ui.dp(context, 4f))
            })
        }
        wrapper.addView(grid)
        wrapper.addView(Ui.button(context, "＋  Save current as custom template").apply {
            setOnClickListener {
                working = working.copy(id = "custom-${System.currentTimeMillis()}", name = "My ${working.name}")
                buildTemplates()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(context, 12f) })
        tabContent.addView(wrapper)
    }

    private fun buildColors() {
        colorFields.clear()
        toggles.clear()
        val wrapper = Ui.vertical(context, 0f)
        wrapper.addView(Ui.text(context, "Tune the layers used in preview and ASS burn-in.", 13f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, 14, 0, 14) })
        listOf(
            "textColor" to "Text color",
            "activeColor" to "Active word color",
            "activeBackground" to "Active word background",
            "strokeColor" to "Stroke color",
            "shadowColor" to "Shadow color",
            "captionBackground" to "Caption background"
        ).forEach { (key, label) ->
            val field = EditText(context).apply {
                setText(workingValue(key))
                hint = "#FFFFFF"
                textSize = 14f
                inputType = InputType.TYPE_CLASS_TEXT
                setTextColor(ContextCompat.getColor(context, R.color.capvid_text))
                setSingleLine(true)
                background = Ui.rounded(context, ContextCompat.getColor(context, R.color.capvid_surface_alt), 10f)
                setPadding(Ui.dp(context, 12f), 0, Ui.dp(context, 12f), 0)
            }
            colorFields[key] = field
            wrapper.addView(Ui.text(context, label, 12f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, 8, 0, 5) })
            wrapper.addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 46f)))
        }
        listOf(
            "activeWordEnabled" to "Enable active word",
            "activeBackgroundEnabled" to "Enable active background",
            "strokeEnabled" to "Enable stroke",
            "shadowEnabled" to "Enable shadow",
            "captionBackgroundEnabled" to "Enable caption background"
        ).forEach { (key, label) ->
            val toggle = Switch(context).apply {
                text = label
                textSize = 13f
                isChecked = toggleValue(key)
                setTextColor(ContextCompat.getColor(context, R.color.capvid_text))
            }
            toggles[key] = toggle
            wrapper.addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)))
        }
        tabContent.addView(wrapper)
    }

    private fun buildFonts() {
        val wrapper = Ui.vertical(context, 0f)
        wrapper.addView(Ui.text(context, "Display, sans, serif, script, calligraphy and mono fonts are bundled.", 13f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, 14, 0, 10) })
        val scroll = HorizontalScrollView(context)
        val row = Ui.horizontal(context, 8f)
        StyleCatalog.all.forEach { style ->
            row.addView(Ui.text(context, style.name, 15f, color(style.textColor)).apply {
                typeface = FontManager(context).typeface(style)
                gravity = android.view.Gravity.CENTER
                background = Ui.rounded(context, if (style.fontAsset == working.fontAsset) Color.argb(55, 167, 139, 250) else ContextCompat.getColor(context, R.color.capvid_surface_alt), 12f)
                setPadding(Ui.dp(context, 14f), Ui.dp(context, 13f), Ui.dp(context, 14f), Ui.dp(context, 13f))
                setOnClickListener { working = working.copy(fontAsset = style.fontAsset, fontFamily = style.fontFamily); buildFonts() }
            })
        }
        scroll.addView(row)
        wrapper.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 62f)))
        val alignment = Spinner(context).apply {
            adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf("Left", "Center", "Right"))
            setSelection(if (working.alignment == 1) 0 else if (working.alignment == 3) 2 else 1)
            setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    fontAlignment = if (position == 0) 1 else if (position == 2) 3 else 2
                    working = working.copy(alignment = fontAlignment)
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            })
        }
        wrapper.addView(Ui.sectionTitle(context, "ALIGNMENT"))
        wrapper.addView(alignment, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)))
        val sizeLabel = Ui.text(context, "Size ${working.fontSizeSp.toInt()}sp", 12f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, 14, 0, 4) }
        val size = android.widget.SeekBar(context).apply {
            max = 56
            progress = (working.fontSizeSp - 24f).toInt().coerceIn(0, 56)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    working = working.copy(fontSizeSp = (24 + progress).toFloat())
                    sizeLabel.text = "Size ${working.fontSizeSp.toInt()}sp"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            })
        }
        wrapper.addView(sizeLabel)
        wrapper.addView(size)
        tabContent.addView(wrapper)
    }

    private fun buildBreaks() {
        val wrapper = Ui.vertical(context, 0f)
        wrapper.addView(Ui.text(context, "Control how speech becomes readable pages.", 13f, ContextCompat.getColor(context, R.color.capvid_muted)).apply { setPadding(0, 14, 0, 10) })
        val modes = Spinner(context).apply {
            adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, BreakMode.entries.map { it.name.lowercase().replace('_', ' ') })
            setSelection(BreakMode.entries.indexOf(working.breaks.mode).coerceAtLeast(0))
            setOnItemSelectedListener(simpleSelection { position -> working = working.copy(breaks = working.breaks.copy(mode = BreakMode.entries.getOrElse(position) { BreakMode.NATURAL })) })
        }
        wrapper.addView(Ui.sectionTitle(context, "LINE BREAKS"))
        wrapper.addView(modes, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)))
        val lines = Spinner(context).apply {
            adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf("1 line per page", "2 lines per page", "3 lines per page", "4 lines per page"))
            setSelection(working.breaks.linesPerPage.coerceIn(1, 4) - 1)
            setOnItemSelectedListener(simpleSelection { position -> working = working.copy(breaks = working.breaks.copy(linesPerPage = position + 1)) })
        }
        wrapper.addView(Ui.sectionTitle(context, "PAGE BREAKS"))
        wrapper.addView(lines, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)))
        val capitalization = Spinner(context).apply {
            adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf("Original case", "UPPERCASE", "lowercase", "Title Case"))
            setSelection(listOf("none", "upper", "lower", "title").indexOf(working.breaks.capitalization).coerceAtLeast(0))
            setOnItemSelectedListener(simpleSelection { position -> working = working.copy(breaks = working.breaks.copy(capitalization = listOf("none", "upper", "lower", "title").getOrElse(position) { "none" })) })
        }
        wrapper.addView(Ui.sectionTitle(context, "CAPITALIZATION"))
        wrapper.addView(capitalization, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)))
        wrapper.addView(Ui.button(context, "Use these breaks").apply {
            setOnClickListener {
                working = working.copy(breaks = working.breaks.copy(
                    mode = BreakMode.entries.getOrElse(modes.selectedItemPosition) { BreakMode.NATURAL },
                    linesPerPage = lines.selectedItemPosition + 1,
                    capitalization = listOf("none", "upper", "lower", "title").getOrElse(capitalization.selectedItemPosition) { "none" }
                ))
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 48f)).apply { topMargin = Ui.dp(context, 14f) })
        tabContent.addView(wrapper)
    }

    private fun captureColors() {
        if (colorFields.isEmpty()) return
        working = working.copy(
            textColor = cleanColor(colorFields["textColor"]?.text?.toString(), working.textColor),
            activeColor = cleanColor(colorFields["activeColor"]?.text?.toString(), working.activeColor),
            activeBackground = cleanColor(colorFields["activeBackground"]?.text?.toString(), working.activeBackground),
            strokeColor = cleanColor(colorFields["strokeColor"]?.text?.toString(), working.strokeColor),
            shadowColor = cleanColor(colorFields["shadowColor"]?.text?.toString(), working.shadowColor),
            captionBackground = cleanColor(colorFields["captionBackground"]?.text?.toString(), working.captionBackground),
            activeWordEnabled = toggles["activeWordEnabled"]?.isChecked ?: working.activeWordEnabled,
            activeBackgroundEnabled = toggles["activeBackgroundEnabled"]?.isChecked ?: working.activeBackgroundEnabled,
            strokeEnabled = toggles["strokeEnabled"]?.isChecked ?: working.strokeEnabled,
            shadowEnabled = toggles["shadowEnabled"]?.isChecked ?: working.shadowEnabled,
            captionBackgroundEnabled = toggles["captionBackgroundEnabled"]?.isChecked ?: working.captionBackgroundEnabled
        )
    }

    private fun applyAndClose() {
        if (activeTab == 1) captureColors()
        if (activeTab == 2) working = working.copy(alignment = fontAlignment)
        onApply(working)
        dismiss()
    }

    private fun workingValue(key: String): String = when (key) {
        "textColor" -> working.textColor
        "activeColor" -> working.activeColor
        "activeBackground" -> working.activeBackground
        "strokeColor" -> working.strokeColor
        "shadowColor" -> working.shadowColor
        else -> working.captionBackground
    }

    private fun toggleValue(key: String): Boolean = when (key) {
        "activeWordEnabled" -> working.activeWordEnabled
        "activeBackgroundEnabled" -> working.activeBackgroundEnabled
        "strokeEnabled" -> working.strokeEnabled
        "shadowEnabled" -> working.shadowEnabled
        else -> working.captionBackgroundEnabled
    }

    private fun cleanColor(value: String?, fallback: String): String = value?.trim()?.takeIf { Regex("#[0-9A-Fa-f]{6}").matches(it) } ?: fallback
    private fun simpleSelection(block: (Int) -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = block(position)
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }
    private fun color(value: String): Int = runCatching { Color.parseColor(value) }.getOrDefault(Color.WHITE)
    private fun effectLabel(style: CaptionStyle): String = style.effect.name.lowercase().replace('_', ' ')
}

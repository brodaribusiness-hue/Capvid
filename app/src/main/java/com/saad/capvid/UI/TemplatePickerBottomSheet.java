package com.saad.capvid.ui.template;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.saad.capvid.R;
import com.saad.capvid.font.FontManager;
import com.saad.capvid.style.CaptionStyleCatalog;
import com.saad.capvid.style.CaptionStyleCategory;
import com.saad.capvid.style.CaptionStyleDefinition;
import com.saad.capvid.style.CaptionStyleOptions;
import com.saad.capvid.style.CustomTemplateManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Full-screen style editor: Template / Color / Font / Breaks tabs at top,
 * one panel visible at a time, "save as custom template" checkbox, and
 * Cancel / Apply at the bottom.
 *
 * Usage from PreviewActivity:
 *   TemplatePickerBottomSheet picker = TemplatePickerBottomSheet.newInstance(currentStyleId);
 *   picker.setInitialOptions(currentOptions); // reflects whatever is live now
 *   picker.setOnApply((def, options) -> { ... });
 *   picker.show(getSupportFragmentManager(), "template_picker");
 */
public class TemplatePickerBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_SELECTED_ID = "selected_id";

    private static final int[] SWATCH_PALETTE = {
            0xFFFFFFFF, 0xFF000000, 0xFFFFD400, 0xFFFF3B30, 0xFF2979FF,
            0xFF7B2FF7, 0xFF8BC34A, 0xFF00E5FF, 0xFFE91E63, 0xFFFF8F00
    };

    public interface OnApplyListener {
        void onApply(CaptionStyleDefinition def, CaptionStyleOptions options);
    }

    private OnApplyListener onApplyListener;
    private CaptionStyleDefinition pendingSelection;
    private TemplateCardAdapter cardAdapter;
    private CustomTemplateManager customTemplateManager;
    private CaptionStyleOptions options = new CaptionStyleOptions();

    // top tabs
    private TextView tabTemplate, tabColor, tabFont, tabBreaks;
    private View templatesPanel, colorPanel, fontsPanel, breaksPanel;

    // color panel
    private SwitchCompat switchActiveWordColor, switchActiveWordBg, switchStroke, switchShadow, switchCaptionBg;
    private View swatchActiveWordColor, swatchActiveWordBg, swatchStroke, swatchShadow, swatchCaptionBg;
    private SeekBar seekActiveWordBgRadius;
    private TextView chipShadowDown, chipShadowRight;

    // fonts panel
    private RecyclerView fontList;
    private TextView chipAlignLeft, chipAlignCenter, chipAlignRight;
    private SeekBar seekLineSpacing, seekWordSpacing;
    private TextView chipCapNone, chipCapUpper, chipCapLower, chipCapTitle;

    // breaks panel
    private TextView chipBreakPunctuation, chipBreakSingleWord, chipBreakRandom;
    private TextView chipPage1, chipPage2, chipPage3, chipPage4;

    private CheckBox checkSaveAsCustom;

    public static TemplatePickerBottomSheet newInstance(String currentlySelectedStyleId) {
        TemplatePickerBottomSheet f = new TemplatePickerBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_SELECTED_ID, currentlySelectedStyleId);
        f.setArguments(args);
        return f;
    }

    public void setOnApply(OnApplyListener listener) {
        this.onApplyListener = listener;
    }

    /** Pass the caller's current live options so the sheet opens in sync with what's on screen. */
    public void setInitialOptions(CaptionStyleOptions initial) {
        this.options = initial != null ? initial.copy() : new CaptionStyleOptions();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_template_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String selectedId = getArguments() != null ? getArguments().getString(ARG_SELECTED_ID) : null;
        customTemplateManager = new CustomTemplateManager(requireContext());
        pendingSelection = CaptionStyleCatalog.byId(selectedId);

        bindTabs(view);
        bindTemplatesPanel(view, selectedId);
        bindColorPanel(view);
        bindFontsPanel(view);
        bindBreaksPanel(view);

        checkSaveAsCustom = view.findViewById(R.id.checkSaveAsCustom);

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btnApply).setOnClickListener(v -> onApplyClicked());

        showPanel(tabTemplate);
    }

    // ================= TOP TABS =================

    private void bindTabs(View view) {
        tabTemplate = view.findViewById(R.id.tabTemplate);
        tabColor = view.findViewById(R.id.tabColor);
        tabFont = view.findViewById(R.id.tabFont);
        tabBreaks = view.findViewById(R.id.tabBreaks);

        templatesPanel = view.findViewById(R.id.templatesPanel);
        colorPanel = view.findViewById(R.id.colorPanel);
        fontsPanel = view.findViewById(R.id.fontsPanel);
        breaksPanel = view.findViewById(R.id.breaksPanel);

        tabTemplate.setOnClickListener(v -> showPanel(tabTemplate));
        tabColor.setOnClickListener(v -> showPanel(tabColor));
        tabFont.setOnClickListener(v -> showPanel(tabFont));
        tabBreaks.setOnClickListener(v -> showPanel(tabBreaks));
    }

    private void showPanel(TextView selectedTab) {
        templatesPanel.setVisibility(selectedTab == tabTemplate ? View.VISIBLE : View.GONE);
        colorPanel.setVisibility(selectedTab == tabColor ? View.VISIBLE : View.GONE);
        fontsPanel.setVisibility(selectedTab == tabFont ? View.VISIBLE : View.GONE);
        breaksPanel.setVisibility(selectedTab == tabBreaks ? View.VISIBLE : View.GONE);

        for (TextView t : new TextView[]{tabTemplate, tabColor, tabFont, tabBreaks}) {
            t.setTextColor(t == selectedTab ? 0xFFFFFFFF : 0xFF7A7A80);
        }
    }

    // ================= TEMPLATES PANEL =================

    private void bindTemplatesPanel(View view, String selectedId) {
        RecyclerView categoryTabs = view.findViewById(R.id.categoryTabs);
        RecyclerView templateList = view.findViewById(R.id.templateList);

        categoryTabs.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        templateList.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<CaptionStyleCategory> categories = Arrays.asList(CaptionStyleCategory.values());

        cardAdapter = new TemplateCardAdapter(
                new ArrayList<>(stylesFor(CaptionStyleCategory.FEATURED)),
                selectedId,
                def -> pendingSelection = def
        );
        templateList.setAdapter(cardAdapter);

        CategoryTabAdapter tabAdapter = new CategoryTabAdapter(categories, category ->
                cardAdapter.updateItems(stylesFor(category)));
        categoryTabs.setAdapter(tabAdapter);
    }

    private List<CaptionStyleDefinition> stylesFor(CaptionStyleCategory category) {
        if (category == CaptionStyleCategory.CUSTOM) {
            return customTemplateManager.loadAll();
        }
        return CaptionStyleCatalog.byCategory(category);
    }

    // ================= COLOR PANEL =================

    private void bindColorPanel(View root) {
        View p = colorPanel;
        switchActiveWordColor = p.findViewById(R.id.switchActiveWordColor);
        switchActiveWordBg = p.findViewById(R.id.switchActiveWordBg);
        switchStroke = p.findViewById(R.id.switchStroke);
        switchShadow = p.findViewById(R.id.switchShadow);
        switchCaptionBg = p.findViewById(R.id.switchCaptionBg);

        swatchActiveWordColor = p.findViewById(R.id.swatchActiveWordColor);
        swatchActiveWordBg = p.findViewById(R.id.swatchActiveWordBg);
        swatchStroke = p.findViewById(R.id.swatchStroke);
        swatchShadow = p.findViewById(R.id.swatchShadow);
        swatchCaptionBg = p.findViewById(R.id.swatchCaptionBg);

        seekActiveWordBgRadius = p.findViewById(R.id.seekActiveWordBgRadius);
        chipShadowDown = p.findViewById(R.id.chipShadowDown);
        chipShadowRight = p.findViewById(R.id.chipShadowRight);

        switchActiveWordColor.setChecked(options.activeWordColorOn);
        switchActiveWordBg.setChecked(options.activeWordBgOn);
        switchStroke.setChecked(options.strokeOn);
        switchShadow.setChecked(options.shadowOn);
        switchCaptionBg.setChecked(options.captionBgOn);

        swatchActiveWordColor.setBackgroundColor(options.activeWordColor);
        swatchActiveWordBg.setBackgroundColor(options.activeWordBgColor);
        swatchStroke.setBackgroundColor(options.strokeColor);
        swatchShadow.setBackgroundColor(options.shadowColor);
        swatchCaptionBg.setBackgroundColor(options.captionBgColor);

        switchActiveWordColor.setOnCheckedChangeListener((b, checked) -> options.activeWordColorOn = checked);
        switchActiveWordBg.setOnCheckedChangeListener((b, checked) -> options.activeWordBgOn = checked);
        switchStroke.setOnCheckedChangeListener((b, checked) -> options.strokeOn = checked);
        switchShadow.setOnCheckedChangeListener((b, checked) -> options.shadowOn = checked);
        switchCaptionBg.setOnCheckedChangeListener((b, checked) -> options.captionBgOn = checked);

        swatchActiveWordColor.setOnClickListener(v -> options.activeWordColor = cyclePalette(swatchActiveWordColor, options.activeWordColor));
        swatchActiveWordBg.setOnClickListener(v -> options.activeWordBgColor = cyclePalette(swatchActiveWordBg, options.activeWordBgColor));
        swatchStroke.setOnClickListener(v -> options.strokeColor = cyclePalette(swatchStroke, options.strokeColor));
        swatchShadow.setOnClickListener(v -> options.shadowColor = cyclePalette(swatchShadow, options.shadowColor));
        swatchCaptionBg.setOnClickListener(v -> options.captionBgColor = cyclePalette(swatchCaptionBg, options.captionBgColor));

        seekActiveWordBgRadius.setProgress((int) options.activeWordBgCornerRadiusPx);
        seekActiveWordBgRadius.setOnSeekBarChangeListener(simpleSeek(v -> options.activeWordBgCornerRadiusPx = v));

        selectShadowDirection(options.shadowDirection);
        chipShadowDown.setOnClickListener(v -> selectShadowDirection(CaptionStyleOptions.ShadowDirection.DOWN));
        chipShadowRight.setOnClickListener(v -> selectShadowDirection(CaptionStyleOptions.ShadowDirection.RIGHT));
    }

    private int cyclePalette(View swatch, int currentColor) {
        int idx = 0;
        for (int i = 0; i < SWATCH_PALETTE.length; i++) {
            if (SWATCH_PALETTE[i] == currentColor) { idx = i; break; }
        }
        int next = SWATCH_PALETTE[(idx + 1) % SWATCH_PALETTE.length];
        swatch.setBackgroundColor(next);
        return next;
    }

    private void selectShadowDirection(CaptionStyleOptions.ShadowDirection dir) {
        options.shadowDirection = dir;
        chipShadowDown.setSelected(dir == CaptionStyleOptions.ShadowDirection.DOWN);
        chipShadowRight.setSelected(dir == CaptionStyleOptions.ShadowDirection.RIGHT);
        chipShadowDown.setTextColor(chipShadowDown.isSelected() ? 0xFFFFFFFF : 0xFFB0B0B5);
        chipShadowRight.setTextColor(chipShadowRight.isSelected() ? 0xFFFFFFFF : 0xFFB0B0B5);
    }

    // ================= FONTS PANEL =================

    private void bindFontsPanel(View root) {
        View p = fontsPanel;
        fontList = p.findViewById(R.id.fontList);
        chipAlignLeft = p.findViewById(R.id.chipAlignLeft);
        chipAlignCenter = p.findViewById(R.id.chipAlignCenter);
        chipAlignRight = p.findViewById(R.id.chipAlignRight);
        seekLineSpacing = p.findViewById(R.id.seekLineSpacing);
        seekWordSpacing = p.findViewById(R.id.seekWordSpacing);
        chipCapNone = p.findViewById(R.id.chipCapNone);
        chipCapUpper = p.findViewById(R.id.chipCapUpper);
        chipCapLower = p.findViewById(R.id.chipCapLower);
        chipCapTitle = p.findViewById(R.id.chipCapTitle);

        fontList.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        String initialFont = options.fontAssetOverride != null ? options.fontAssetOverride : FontManager.FONT_FILES[0];
        FontListAdapter fontAdapter = new FontListAdapter(Arrays.asList(FontManager.FONT_FILES), initialFont,
                asset -> options.fontAssetOverride = asset);
        fontList.setAdapter(fontAdapter);

        selectAlignment(options.alignment);
        chipAlignLeft.setOnClickListener(v -> selectAlignment(CaptionStyleOptions.Alignment.LEFT));
        chipAlignCenter.setOnClickListener(v -> selectAlignment(CaptionStyleOptions.Alignment.CENTER));
        chipAlignRight.setOnClickListener(v -> selectAlignment(CaptionStyleOptions.Alignment.RIGHT));

        seekLineSpacing.setProgress((int) options.lineSpacingPx);
        seekLineSpacing.setOnSeekBarChangeListener(simpleSeek(v -> options.lineSpacingPx = v));

        seekWordSpacing.setProgress((int) options.wordSpacingPx);
        seekWordSpacing.setOnSeekBarChangeListener(simpleSeek(v -> options.wordSpacingPx = v));

        selectCapitalization(options.capitalization);
        chipCapNone.setOnClickListener(v -> selectCapitalization(CaptionStyleOptions.Capitalization.NONE));
        chipCapUpper.setOnClickListener(v -> selectCapitalization(CaptionStyleOptions.Capitalization.UPPERCASE));
        chipCapLower.setOnClickListener(v -> selectCapitalization(CaptionStyleOptions.Capitalization.LOWERCASE));
        chipCapTitle.setOnClickListener(v -> selectCapitalization(CaptionStyleOptions.Capitalization.TITLECASE));
    }

    private void selectAlignment(CaptionStyleOptions.Alignment a) {
        options.alignment = a;
        setChipSelected(chipAlignLeft, a == CaptionStyleOptions.Alignment.LEFT);
        setChipSelected(chipAlignCenter, a == CaptionStyleOptions.Alignment.CENTER);
        setChipSelected(chipAlignRight, a == CaptionStyleOptions.Alignment.RIGHT);
    }

    private void selectCapitalization(CaptionStyleOptions.Capitalization c) {
        options.capitalization = c;
        setChipSelected(chipCapNone, c == CaptionStyleOptions.Capitalization.NONE);
        setChipSelected(chipCapUpper, c == CaptionStyleOptions.Capitalization.UPPERCASE);
        setChipSelected(chipCapLower, c == CaptionStyleOptions.Capitalization.LOWERCASE);
        setChipSelected(chipCapTitle, c == CaptionStyleOptions.Capitalization.TITLECASE);
    }

    // ================= BREAKS PANEL =================

    private void bindBreaksPanel(View root) {
        View p = breaksPanel;
        chipBreakPunctuation = p.findViewById(R.id.chipBreakPunctuation);
        chipBreakSingleWord = p.findViewById(R.id.chipBreakSingleWord);
        chipBreakRandom = p.findViewById(R.id.chipBreakRandom);
        chipPage1 = p.findViewById(R.id.chipPage1);
        chipPage2 = p.findViewById(R.id.chipPage2);
        chipPage3 = p.findViewById(R.id.chipPage3);
        chipPage4 = p.findViewById(R.id.chipPage4);

        selectLineBreakMode(options.lineBreakMode);
        chipBreakPunctuation.setOnClickListener(v -> selectLineBreakMode(CaptionStyleOptions.LineBreakMode.PUNCTUATION));
        chipBreakSingleWord.setOnClickListener(v -> selectLineBreakMode(CaptionStyleOptions.LineBreakMode.SINGLE_WORD));
        chipBreakRandom.setOnClickListener(v -> selectLineBreakMode(CaptionStyleOptions.LineBreakMode.RANDOM));

        selectPageBreaks(options.pageBreakLines);
        chipPage1.setOnClickListener(v -> selectPageBreaks(1));
        chipPage2.setOnClickListener(v -> selectPageBreaks(2));
        chipPage3.setOnClickListener(v -> selectPageBreaks(3));
        chipPage4.setOnClickListener(v -> selectPageBreaks(4));
    }

    private void selectLineBreakMode(CaptionStyleOptions.LineBreakMode mode) {
        options.lineBreakMode = mode;
        setChipSelected(chipBreakPunctuation, mode == CaptionStyleOptions.LineBreakMode.PUNCTUATION);
        setChipSelected(chipBreakSingleWord, mode == CaptionStyleOptions.LineBreakMode.SINGLE_WORD);
        setChipSelected(chipBreakRandom, mode == CaptionStyleOptions.LineBreakMode.RANDOM);
    }

    private void selectPageBreaks(int lines) {
        options.pageBreakLines = lines;
        setChipSelected(chipPage1, lines == 1);
        setChipSelected(chipPage2, lines == 2);
        setChipSelected(chipPage3, lines == 3);
        setChipSelected(chipPage4, lines == 4);
    }

    // ================= SHARED HELPERS =================

    private void setChipSelected(TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setTextColor(selected ? 0xFFFFFFFF : 0xFFB0B0B5);
    }

    private interface OnValue { void set(float v); }

    private SeekBar.OnSeekBarChangeListener simpleSeek(OnValue onValue) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { onValue.set(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private void onApplyClicked() {
        if (pendingSelection == null || onApplyListener == null) {
            dismiss();
            return;
        }

        if (checkSaveAsCustom != null && checkSaveAsCustom.isChecked()) {
            String customId = "CUSTOM_" + System.currentTimeMillis();
            CaptionStyleDefinition custom = new CaptionStyleDefinition(
                    customId,
                    pendingSelection.displayName + " (Custom)",
                    java.util.Collections.singletonList(CaptionStyleCategory.CUSTOM),
                    pendingSelection.swatchColors,
                    options.fontAssetOverride != null ? options.fontAssetOverride : pendingSelection.fontAsset,
                    false,
                    pendingSelection.treatment,
                    pendingSelection.isModern
            );
            customTemplateManager.save(custom);
        }

        onApplyListener.onApply(pendingSelection, options);
        dismiss();
    }
}

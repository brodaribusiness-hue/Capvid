package com.saad.capvid.style;

import android.graphics.Color;

/**
 * User-editable overrides applied ON TOP of whichever CaptionStyleDefinition
 * (template) is currently selected. Populated by the Color / Fonts / Breaks
 * tabs in TemplatePickerBottomSheet and consumed by CaptionOverlayView.
 *
 * One instance lives for the lifetime of the editor session (held by
 * PreviewActivity) and is mutated by the picker's Apply button.
 */
public class CaptionStyleOptions {

    public enum ShadowDirection { DOWN, RIGHT }

    public enum Alignment { LEFT, CENTER, RIGHT }

    public enum Capitalization { NONE, UPPERCASE, LOWERCASE, TITLECASE }

    public enum LineBreakMode { PUNCTUATION, SINGLE_WORD, RANDOM }

    // ---- Color tab ----
    public boolean activeWordColorOn = true;
    public int activeWordColor = Color.parseColor("#FFD400");

    public boolean activeWordBgOn = false;
    public int activeWordBgColor = Color.parseColor("#000000");
    public float activeWordBgCornerRadiusPx = 8f;

    public boolean strokeOn = false;
    public int strokeColor = Color.BLACK;
    public float strokeWidthPx = 4f;

    public boolean shadowOn = false;
    public int shadowColor = Color.BLACK;
    public ShadowDirection shadowDirection = ShadowDirection.DOWN;
    public float shadowOffsetPx = 6f;

    public boolean captionBgOn = false;
    public int captionBgColor = Color.argb(160, 0, 0, 0);

    // ---- Fonts tab ----
    /** null = use the selected template's own fontAsset */
    public String fontAssetOverride = null;
    public Alignment alignment = Alignment.CENTER;
    public float lineSpacingPx = 12f;
    public float wordSpacingPx = 22f;
    public Capitalization capitalization = Capitalization.NONE;

    // ---- Breaks tab ----
    public LineBreakMode lineBreakMode = LineBreakMode.PUNCTUATION;
    /** how many lines are visible on screen at once: 1-4 */
    public int pageBreakLines = 1;

    public String applyCapitalization(String text) {
        if (text == null) return "";
        switch (capitalization) {
            case UPPERCASE: return text.toUpperCase();
            case LOWERCASE: return text.toLowerCase();
            case TITLECASE:
                if (text.isEmpty()) return text;
                return Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase();
            case NONE:
            default: return text;
        }
    }

    public CaptionStyleOptions copy() {
        CaptionStyleOptions c = new CaptionStyleOptions();
        c.activeWordColorOn = activeWordColorOn;
        c.activeWordColor = activeWordColor;
        c.activeWordBgOn = activeWordBgOn;
        c.activeWordBgColor = activeWordBgColor;
        c.activeWordBgCornerRadiusPx = activeWordBgCornerRadiusPx;
        c.strokeOn = strokeOn;
        c.strokeColor = strokeColor;
        c.strokeWidthPx = strokeWidthPx;
        c.shadowOn = shadowOn;
        c.shadowColor = shadowColor;
        c.shadowDirection = shadowDirection;
        c.shadowOffsetPx = shadowOffsetPx;
        c.captionBgOn = captionBgOn;
        c.captionBgColor = captionBgColor;
        c.fontAssetOverride = fontAssetOverride;
        c.alignment = alignment;
        c.lineSpacingPx = lineSpacingPx;
        c.wordSpacingPx = wordSpacingPx;
        c.capitalization = capitalization;
        c.lineBreakMode = lineBreakMode;
        c.pageBreakLines = pageBreakLines;
        return c;
    }
}

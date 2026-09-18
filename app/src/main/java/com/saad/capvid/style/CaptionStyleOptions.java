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

    /**
     * Typography. These three used to live on the preview screen as a size
     * spinner and two checkboxes, next to a style dropdown that duplicated the
     * template picker. They belong to the look of the caption, so they moved
     * here and the picker now owns the whole thing.
     *
     * <p>The persisted source of truth is still VideoProject.textSizeSp /
     * .bold / .italic; PreviewActivity copies them in before opening the picker
     * and back out on Apply, so the two can never disagree.
     */
    public float textSizeSp = 12f;
    public boolean bold = false;
    public boolean italic = false;
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

    // ------------------------------------------------------------------
    // Persistence. Without this the whole Color/Fonts/Breaks panel reset to
    // defaults every time a project was reopened, so a user's styling work was
    // silently thrown away as soon as they left the editor.
    // ------------------------------------------------------------------

    public org.json.JSONObject toJson() throws org.json.JSONException {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("activeWordColorOn", activeWordColorOn);
        o.put("activeWordColor", activeWordColor);
        o.put("activeWordBgOn", activeWordBgOn);
        o.put("activeWordBgColor", activeWordBgColor);
        o.put("activeWordBgCornerRadiusPx", (double) activeWordBgCornerRadiusPx);
        o.put("strokeOn", strokeOn);
        o.put("strokeColor", strokeColor);
        o.put("strokeWidthPx", (double) strokeWidthPx);
        o.put("shadowOn", shadowOn);
        o.put("shadowColor", shadowColor);
        o.put("shadowDirection", shadowDirection.name());
        o.put("shadowOffsetPx", (double) shadowOffsetPx);
        o.put("captionBgOn", captionBgOn);
        o.put("captionBgColor", captionBgColor);
        o.put("fontAssetOverride", fontAssetOverride == null ? org.json.JSONObject.NULL : fontAssetOverride);
        o.put("alignment", alignment.name());
        o.put("lineSpacingPx", (double) lineSpacingPx);
        o.put("wordSpacingPx", (double) wordSpacingPx);
        o.put("capitalization", capitalization.name());
        o.put("lineBreakMode", lineBreakMode.name());
        o.put("pageBreakLines", pageBreakLines);
        o.put("textSizeSp", (double) textSizeSp);
        o.put("bold", bold);
        o.put("italic", italic);
        return o;
    }

    /**
     * @return the stored options, or a default instance when {@code o} is null or
     *         unreadable - a corrupted project must not crash the editor.
     */
    public static CaptionStyleOptions fromJson(org.json.JSONObject o) {
        CaptionStyleOptions c = new CaptionStyleOptions();
        if (o == null) return c;
        c.activeWordColorOn = o.optBoolean("activeWordColorOn", c.activeWordColorOn);
        c.activeWordColor = o.optInt("activeWordColor", c.activeWordColor);
        c.activeWordBgOn = o.optBoolean("activeWordBgOn", c.activeWordBgOn);
        c.activeWordBgColor = o.optInt("activeWordBgColor", c.activeWordBgColor);
        c.activeWordBgCornerRadiusPx = (float) o.optDouble("activeWordBgCornerRadiusPx", c.activeWordBgCornerRadiusPx);
        c.strokeOn = o.optBoolean("strokeOn", c.strokeOn);
        c.strokeColor = o.optInt("strokeColor", c.strokeColor);
        c.strokeWidthPx = (float) o.optDouble("strokeWidthPx", c.strokeWidthPx);
        c.shadowOn = o.optBoolean("shadowOn", c.shadowOn);
        c.shadowColor = o.optInt("shadowColor", c.shadowColor);
        c.shadowDirection = parseEnum(ShadowDirection.class, o.optString("shadowDirection", null), c.shadowDirection);
        c.shadowOffsetPx = (float) o.optDouble("shadowOffsetPx", c.shadowOffsetPx);
        c.captionBgOn = o.optBoolean("captionBgOn", c.captionBgOn);
        c.captionBgColor = o.optInt("captionBgColor", c.captionBgColor);
        c.fontAssetOverride = o.isNull("fontAssetOverride") ? null : o.optString("fontAssetOverride", null);
        c.alignment = parseEnum(Alignment.class, o.optString("alignment", null), c.alignment);
        c.lineSpacingPx = (float) o.optDouble("lineSpacingPx", c.lineSpacingPx);
        c.wordSpacingPx = (float) o.optDouble("wordSpacingPx", c.wordSpacingPx);
        c.capitalization = parseEnum(Capitalization.class, o.optString("capitalization", null), c.capitalization);
        c.lineBreakMode = parseEnum(LineBreakMode.class, o.optString("lineBreakMode", null), c.lineBreakMode);
        c.pageBreakLines = o.optInt("pageBreakLines", c.pageBreakLines);
        c.textSizeSp = (float) o.optDouble("textSizeSp", c.textSizeSp);
        c.bold = o.optBoolean("bold", c.bold);
        c.italic = o.optBoolean("italic", c.italic);
        return c;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
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
        c.textSizeSp = textSizeSp;
        c.bold = bold;
        c.italic = italic;
        return c;
    }
}

package com.saad.capvid.style;

import java.util.List;

/**
 * Metadata for one caption style, used by the template picker UI
 * (card preview, swatches, category filtering) and by MiniStylePreviewView.
 *
 * "id" MUST match the value name in your existing caption-style enum used by
 * CaptionOverlayView / AssSubtitleBuilder / StyleFontMap, e.g. "BOUNCE_POP".
 * If your enum uses different names, just change the ids below to match —
 * nothing else in this file needs to change.
 */
public class CaptionStyleDefinition {

    public enum PreviewTreatment {
        PLAIN,          // flat color text
        BOX_SOLID,      // solid rounded background box behind text
        BOX_TRANSLUCENT,// frosted/glass translucent box
        OUTLINE_GLOW,   // neon outline + glow
        GRADIENT_FILL,  // gradient-filled text
        SPLIT_HALF,     // first half plain, second half boxed/gradient
        ROTATED_MARKER, // rotated highlighter-style box
        CHROME,         // metallic gradient with dark outline
        COMIC_OUTLINE,  // thick black outline, bold curvy look
        SERIF_ITALIC    // cinematic small italic serif caption
    }

    public final String id;
    public final String displayName;
    public final List<CaptionStyleCategory> categories;
    public final int[] swatchColors;   // exactly 4 ARGB ints, shown as the 4 small squares
    public final String fontAsset;     // filename under assets/fonts/
    public final boolean isLite;       // shows the "LITE" badge
    public final PreviewTreatment treatment;
    public final boolean isModern;     // true for the 10 new advanced templates

    public CaptionStyleDefinition(String id, String displayName, List<CaptionStyleCategory> categories,
                                   int[] swatchColors, String fontAsset, boolean isLite,
                                   PreviewTreatment treatment, boolean isModern) {
        this.id = id;
        this.displayName = displayName;
        this.categories = categories;
        this.swatchColors = swatchColors;
        this.fontAsset = fontAsset;
        this.isLite = isLite;
        this.treatment = treatment;
        this.isModern = isModern;
    }
}

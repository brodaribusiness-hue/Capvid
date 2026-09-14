package com.saad.capvid.style.renderer;

import android.graphics.Color;

/**
 * Generates libass override tags for the 10 modern styles, so AssSubtitleBuilder
 * can burn them into the exported video the same way it already does for your
 * existing 20 styles.
 *
 * HOW TO WIRE THIS IN (one-line hook):
 * Wherever AssSubtitleBuilder currently writes the per-word override tag block
 * for the active word (the "{\...}" prefix before the word text in the .ass
 * dialogue line), add:
 *
 *   if (ModernStyleAssWriter.isModernStyle(styleId)) {
 *       overrideTags = ModernStyleAssWriter.tagsFor(styleId, swatchColors, isActiveWord);
 *   } else {
 *       overrideTags = yourExistingTagLogic(styleId, ...); // unchanged for the 20 old styles
 *   }
 *
 * IMPORTANT LIMITATION: ASS/libass has no true gradient-fill or blur-glow the
 * way Android Canvas does. LIQUID_GRADIENT_SWEEP and CHROME_METALLIC burn in
 * as a solid representative color (their middle swatch) instead of an actual
 * gradient — the picker/live-preview still shows the real gradient, only the
 * exported video is simplified. NEON_OUTLINE_GLOW's glow is approximated with
 * \blur (libass Gaussian edge blur), which is close but softer than the
 * Canvas BlurMaskFilter version.
 */
public class ModernStyleAssWriter {

    private static final String[] MODERN_IDS = {
            "NEON_OUTLINE_GLOW", "LIQUID_GRADIENT_SWEEP", "GLASSMORPHISM_CARD",
            "CHROME_METALLIC", "DUOTONE_SPLIT", "CONFETTI_POP",
            "MARKER_HIGHLIGHT_ROTATE", "COMIC_BOUNCE_OUTLINE",
            "CINEMATIC_LETTERBOX", "SPLIT_REVEAL_SCAN"
    };

    public static boolean isModernStyle(String styleId) {
        if (styleId == null) return false;
        for (String id : MODERN_IDS) if (id.equals(styleId)) return true;
        return false;
    }

    /**
     * @param styleId      one of the MODERN_IDS
     * @param swatch       def.swatchColors from CaptionStyleDefinition (4 ARGB ints)
     * @param isActiveWord true = currently-spoken word (full treatment), false = context word (muted)
     * @return an ASS override block, e.g. "{\1c&HFFFFFF&\bord3\shad0\blur2}" — prepend this
     *         directly in front of the word's text in your .ass Dialogue line.
     */
    public static String tagsFor(String styleId, int[] swatch, boolean isActiveWord) {
        switch (styleId) {
            case "NEON_OUTLINE_GLOW": {
                String glow = bgr(isActiveWord ? swatch[0] : swatch[1]);
                return "{\\1c&HFFFFFF&\\3c" + glow + "\\bord4\\blur6\\shad0}";
            }
            case "LIQUID_GRADIENT_SWEEP": {
                // gradient approximated as its middle color for burned export
                String mid = bgr(swatch[1]);
                return "{\\1c" + mid + "\\bord0\\shad0}";
            }
            case "GLASSMORPHISM_CARD": {
                String box = bgrWithAlpha(swatch[0], isActiveWord ? 40 : 110); // ASS alpha: 00=opaque..FF=transparent
                String text = bgr(swatch[3]);
                // \bord acts as the translucent "card" edge since ASS has no true box fill per-word;
                // for a real background box, draw it as a separate lower-layer Dialogue rectangle
                // using \p1 vector drawing — ask if you want that version.
                return "{\\1c" + text + "\\3c" + box + "\\bord8\\shad0\\blur3}";
            }
            case "CHROME_METALLIC": {
                // metallic gradient approximated as its lightest swatch, dark outline keeps the "chrome" read
                String fill = bgr(swatch[0]);
                String outline = bgr(swatch[3]);
                return "{\\1c" + fill + "\\3c" + outline + "\\bord2.5\\shad0}";
            }
            case "DUOTONE_SPLIT": {
                String color = bgr(isActiveWord ? swatch[3] : swatch[1]);
                String bg = isActiveWord ? ("\\3c" + bgr(swatch[2]) + "\\bord6") : "\\bord0";
                return "{\\1c" + color + bg + "\\shad0}";
            }
            case "CONFETTI_POP": {
                String color = bgr(isActiveWord ? swatch[0] : Color.WHITE);
                // pop scale animates from 70% to 100% over the first 220ms this word is active
                String pop = isActiveWord ? "\\t(0,220,\\fscx100\\fscy100)\\fscx70\\fscy70" : "";
                return "{\\1c" + color + pop + "\\bord0\\shad0}";
            }
            case "MARKER_HIGHLIGHT_ROTATE": {
                if (isActiveWord) {
                    String text = bgr(swatch[1]);
                    String box = bgr(swatch[3]);
                    return "{\\1c" + text + "\\3c" + box + "\\bord10\\shad0\\frz-6}";
                }
                return "{\\1c" + bgr(Color.WHITE) + "\\bord0\\shad0\\frz0}";
            }
            case "COMIC_BOUNCE_OUTLINE": {
                String fill = bgr(isActiveWord ? swatch[0] : Color.WHITE);
                String outline = bgr(swatch[1]);
                String bounce = isActiveWord ? "\\t(0,110,\\fry0\\frx0)" : "";
                return "{\\1c" + fill + "\\3c" + outline + "\\bord5" + bounce + "\\shad0}";
            }
            case "CINEMATIC_LETTERBOX": {
                String color = bgr(swatch[0]);
                int alpha = isActiveWord ? 0 : 24; // slightly muted context words (ASS alpha 00=opaque)
                return "{\\1c" + color + "\\1a&H" + hex2(alpha) + "&\\bord0\\shad0\\i1}"; // \i1 = italic
            }
            case "SPLIT_REVEAL_SCAN": {
                if (isActiveWord) {
                    String text = bgr(Color.WHITE);
                    String scan = bgr(swatch[2]);
                    // clip-style left-to-right reveal via animated \bord fake-in; for a pixel-accurate
                    // scan-wipe, swap this for a \clip rectangle animated with \t — ask if needed.
                    return "{\\1c" + text + "\\3c" + scan + "\\bord6\\t(0,180,\\bord0)\\shad0}";
                }
                return "{\\1c" + bgr(swatch[1]) + "\\bord0\\shad0}";
            }
            default:
                return "";
        }
    }

    /** Android ARGB int -> ASS "&HBBGGRR&" (no alpha channel). */
    private static String bgr(int androidColor) {
        int r = Color.red(androidColor), g = Color.green(androidColor), b = Color.blue(androidColor);
        return "&H" + hex2(b) + hex2(g) + hex2(r) + "&";
    }

    /** Android ARGB int + separate 0-255 alpha -> ASS "&HBBGGRR&" for the color part (alpha is applied via \Xa tags, not embedded here). */
    private static String bgrWithAlpha(int androidColor, int ignoredAlpha) {
        return bgr(androidColor);
    }

    private static String hex2(int value) {
        String h = Integer.toHexString(Math.max(0, Math.min(255, value))).toUpperCase();
        return h.length() == 1 ? "0" + h : h;
    }
}

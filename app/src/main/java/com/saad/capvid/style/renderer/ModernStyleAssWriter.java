package com.saad.capvid.style.renderer;

import android.graphics.Color;

/**
 * Generates libass override tags for the 10 "modern" caption styles so
 * {@code AssSubtitleBuilder} can burn them into the exported video.
 *
 * <p>Consumed by {@code com.saad.capvid.export.StyleAssMapper}, which is called
 * from {@code AssSubtitleBuilder}. (This file used to be dead code: it was
 * written as a drop-in helper with a "how to wire this in" comment in the
 * javadoc, but nothing ever called it, so all 10 modern styles exported as
 * plain white text. It is now actually on the export path.)
 *
 * <p><b>What this file does and does not do.</b> It only emits inline override
 * tags - the things that can be expressed inside a single text run: colours,
 * outline, alpha, rotation and {@code \t} transforms. Two classes of effect
 * cannot be inline tags and are therefore declared on
 * {@code StyleAssMapper.Mapping} instead, for {@code AssSubtitleBuilder} to
 * render structurally:
 * <ul>
 *   <li><b>Glow.</b> libass applies {@code \blur} to the glyph fill bitmap
 *       (ass_render.c:2726), so putting it on the text blurs the letters rather
 *       than glowing behind them. NEON_OUTLINE_GLOW therefore sets
 *       {@code glowRadius}, which becomes a separate lower-layer event with a
 *       thick blurred outline underneath the sharp text.</li>
 *   <li><b>Gradient.</b> libass has no gradient primitive. LIQUID_GRADIENT_SWEEP
 *       and CHROME_METALLIC set {@code gradientStops}, which becomes a stack of
 *       clipped colour bands.</li>
 * </ul>
 *
 * <p>Remaining deliberate simplifications:
 * <ul>
 *   <li>GLASSMORPHISM_CARD has no true per-word translucent panel; it is a
 *       BorderStyle-3 box whose outline colour carries the alpha.</li>
 *   <li>SPLIT_REVEAL_SCAN's scan wipe is approximated by animating the outline
 *       away; a pixel-accurate wipe would need an animated {@code \clip}.</li>
 * </ul>
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
     * @param isActiveWord true = currently-spoken word (full treatment), false = context word
     * @return a complete ASS override block, e.g. "{\1c&HFFFFFF&\bord3\shad0\blur2}",
     *         or "" when this style needs no per-word override.
     */
    public static String tagsFor(String styleId, int[] swatch, boolean isActiveWord) {
        String inner = innerTagsFor(styleId, swatch, isActiveWord);
        return inner.isEmpty() ? "" : "{" + inner + "}";
    }

    /**
     * Same tags as {@link #tagsFor} but without the surrounding braces, so a
     * caller can merge them into an override block that already carries other
     * tags (notably the per-word {@code \k} karaoke tag). Two separate blocks
     * on one word would each set {@code \1c}, and the later one would silently
     * win - merging keeps the intent unambiguous.
     *
     * @return tags with no surrounding braces, or "" if nothing to add
     */
    public static String innerTagsFor(String styleId, int[] swatch, boolean isActiveWord) {
        if (styleId == null || swatch == null || swatch.length < 4) return "";
        switch (styleId) {
            case "NEON_OUTLINE_GLOW": {
                // No \blur here: libass applies it to the glyph fill, which
                // makes the letters fuzzy instead of glowing. The halo is a
                // separate lower-layer event emitted by AssSubtitleBuilder from
                // StyleAssMapper.Mapping.glowRadius.
                String glow = bgr(isActiveWord ? swatch[0] : swatch[1]);
                return "\\1c&HFFFFFF&\\3c" + glow + "\\bord4\\shad0";
            }
            case "LIQUID_GRADIENT_SWEEP": {
                // gradient approximated as its middle colour for burned export
                return "\\1c" + bgr(swatch[1]) + "\\bord0\\shad0";
            }
            case "GLASSMORPHISM_CARD": {
                // ASS alpha runs 00=opaque .. FF=transparent, the opposite of
                // Android's, hence 255 - a.
                int a = 255 - (isActiveWord ? 40 : 110);
                String box = bgr(swatch[0]);
                String text = bgr(swatch[3]);
                // \bord stands in for the translucent "card" edge, because ASS
                // has no true per-word background box.
                return "\\1c" + text + "\\3c" + box + "\\3a&H" + hex2(a) + "&\\bord8\\shad0";
            }
            case "CHROME_METALLIC": {
                // metallic gradient approximated as its lightest swatch; the dark
                // outline is what keeps it reading as "chrome"
                return "\\1c" + bgr(swatch[0]) + "\\3c" + bgr(swatch[3]) + "\\bord2.5\\shad0";
            }
            case "DUOTONE_SPLIT": {
                String color = bgr(isActiveWord ? swatch[3] : swatch[1]);
                String bg = isActiveWord ? ("\\3c" + bgr(swatch[2]) + "\\bord6") : "\\bord0";
                return "\\1c" + color + bg + "\\shad0";
            }
            case "CONFETTI_POP": {
                String color = bgr(isActiveWord ? swatch[0] : Color.WHITE);
                // pop scale animates from 70% to 100% over the first 220ms this word is active
                String pop = isActiveWord ? "\\t(0,220,\\fscx100\\fscy100)\\fscx70\\fscy70" : "";
                return "\\1c" + color + pop + "\\bord0\\shad0";
            }
            case "MARKER_HIGHLIGHT_ROTATE": {
                if (isActiveWord) {
                    return "\\1c" + bgr(swatch[1]) + "\\3c" + bgr(swatch[3]) + "\\bord10\\shad0\\frz-6";
                }
                return "\\1c" + bgr(Color.WHITE) + "\\bord0\\shad0\\frz0";
            }
            case "COMIC_BOUNCE_OUTLINE": {
                String fill = bgr(isActiveWord ? swatch[0] : Color.WHITE);
                String outline = bgr(swatch[1]);
                String bounce = isActiveWord ? "\\t(0,110,\\fry0\\frx0)" : "";
                return "\\1c" + fill + "\\3c" + outline + "\\bord5" + bounce + "\\shad0";
            }
            case "CINEMATIC_LETTERBOX": {
                int alpha = isActiveWord ? 0 : 24; // slightly muted context words (ASS alpha 00=opaque)
                return "\\1c" + bgr(swatch[0]) + "\\1a&H" + hex2(alpha) + "&\\bord0\\shad0\\i1";
            }
            case "SPLIT_REVEAL_SCAN": {
                if (isActiveWord) {
                    // outline animates away over 180ms as a stand-in for a scan wipe
                    return "\\1c" + bgr(Color.WHITE) + "\\3c" + bgr(swatch[2])
                            + "\\bord6\\t(0,180,\\bord0)\\shad0";
                }
                return "\\1c" + bgr(swatch[1]) + "\\bord0\\shad0";
            }
            default:
                return "";
        }
    }

    /** Android ARGB int -&gt; ASS "&amp;HBBGGRR&amp;" (colour only, no alpha). */
    public static String bgr(int androidColor) {
        int r = Color.red(androidColor), g = Color.green(androidColor), b = Color.blue(androidColor);
        return "&H" + hex2(b) + hex2(g) + hex2(r) + "&";
    }

    /** Android ARGB int -&gt; ASS "&amp;HAABBGGRR&amp;" with its own alpha channel. */
    public static String bgra(int androidColor) {
        int a = 255 - Color.alpha(androidColor); // ASS: 00=opaque, FF=transparent
        int r = Color.red(androidColor), g = Color.green(androidColor), b = Color.blue(androidColor);
        return "&H" + hex2(a) + hex2(b) + hex2(g) + hex2(r) + "&";
    }

    public static String hex2(int value) {
        String h = Integer.toHexString(Math.max(0, Math.min(255, value))).toUpperCase();
        return h.length() == 1 ? "0" + h : h;
    }
}

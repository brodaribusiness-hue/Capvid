package com.saad.capvid.export;

import android.graphics.Color;

import com.saad.capvid.style.CaptionStyleCatalog;
import com.saad.capvid.style.CaptionStyleDefinition;
import com.saad.capvid.style.CaptionStyleOptions;
import com.saad.capvid.style.renderer.ModernStyleAssWriter;

/**
 * Translates a caption style (a {@link CaptionStyleDefinition} from the catalog)
 * plus the user's {@link CaptionStyleOptions} overrides into the libass override
 * tags {@link AssSubtitleBuilder} writes into the .ass file.
 *
 * <p>Every one of the 60 catalog styles is covered: the 10 "modern" ids are
 * handled by {@link ModernStyleAssWriter} (which already had bespoke tag sets),
 * and the remaining 50 are derived from their
 * {@link CaptionStyleDefinition.PreviewTreatment} so a newly added catalog entry
 * exports correctly without touching this file.
 *
 * <h3>Fidelity</h3>
 * Geometry, timing, colours, outline, shadow, blur, rotation, italic and
 * per-word highlight are all reproduced. Canvas-only effects cannot be: true
 * gradient fills become a solid representative colour, BlurMaskFilter glows
 * become libass {@code \blur}, and Android {@code Camera} 3D transforms
 * (CUBE_ROTATE_3D, TILT_PERSPECTIVE_3D, ROTATE_IN_3D_FLIP) have no libass
 * equivalent at all and export as their flat treatment. Those cases are listed
 * explicitly in {@link #exportNotes(String)} so nobody mistakes the export for
 * a pixel-perfect copy of the preview.
 */
public final class StyleAssMapper {

    /** Result of mapping a style: everything the .ass writer needs. */
    public static final class Mapping {
        /** Colour of the word currently being spoken. */
        public int primaryColor = Color.WHITE;
        /** Colour of the surrounding context words. */
        public int secondaryColor = Color.argb(190, 255, 255, 255);
        /** Outline / box colour. */
        public int outlineColor = Color.BLACK;
        /** Shadow colour. */
        public int shadowColor = Color.argb(128, 0, 0, 0);
        public float outlineWidth = 2f;
        public float shadowDepth = 0f;
        /** ASS BorderStyle: 1 = outline+shadow, 3 = opaque box behind text. */
        public int borderStyle = 1;
        public boolean italic = false;
        public boolean bold = false;
        /** Per-word extra tags for the ACTIVE word (no braces). */
        public String activeWordTags = "";
        /** Per-word extra tags for CONTEXT words (no braces). */
        public String contextWordTags = "";
        /** Multiplier applied to the base font size (e.g. 0.85 for small-caps looks). */
        public float sizeScale = 1f;

        /**
         * Radius of a soft halo drawn BEHIND the text, in preview pixels, or 0
         * for none. Rendered as a second, lower-layer event with a thick
         * outline and \blur, which is how a glow has to be done in ASS:
         * putting \blur on the text event blurs the glyphs themselves. Verified
         * against libass ass_render.c:2726, where ass_synth_blur() is applied
         * to the fill bitmap.
         */
        public float glowRadius = 0f;
        /** Colour of that halo. */
        public int glowColor = Color.WHITE;

        /**
         * Two or more ARGB colours for a gradient fill, first = top (or left),
         * last = bottom (or right); null for a flat fill. libass has no gradient
         * primitive, so AssSubtitleBuilder renders this as a stack of clipped
         * colour bands, each carrying the same karaoke timings.
         */
        public int[] gradientStops = null;
        /** True = top-to-bottom gradient, false = left-to-right. */
        public boolean gradientVertical = true;
    }

    private StyleAssMapper() {
    }

    /**
     * @param styleId a CaptionStyleType name
     * @param options the user's overrides; may be null
     */
    public static Mapping map(String styleId, CaptionStyleOptions options) {
        Mapping m = new Mapping();
        CaptionStyleDefinition def = CaptionStyleCatalog.byId(styleId);
        CaptionStyleOptions o = options != null ? options : new CaptionStyleOptions();

        int[] sw = def != null && def.swatchColors != null && def.swatchColors.length >= 4
                ? def.swatchColors
                : new int[]{Color.WHITE, Color.WHITE, Color.WHITE, Color.BLACK};

        CaptionStyleDefinition.PreviewTreatment t = def != null
                ? def.treatment : CaptionStyleDefinition.PreviewTreatment.PLAIN;

        // ---- base look from the treatment --------------------------------
        switch (t) {
            case BOX_SOLID:
                m.borderStyle = 3;
                m.outlineColor = sw[0];
                m.primaryColor = sw[1];
                m.secondaryColor = withAlpha(sw[1], 190);
                m.outlineWidth = 6f;
                break;

            case BOX_TRANSLUCENT:
                // BorderStyle 3 with Shadow 0 draws the box in OutlineColour
                // (libass ass_render.c:2737 only moves the box onto the shadow
                // layer, drawn in BackColour, when a shadow offset is set), so
                // the translucency has to live in the outline colour's alpha.
                m.borderStyle = 3;
                m.outlineColor = withAlpha(sw[0], 110);
                m.primaryColor = sw[1];
                m.secondaryColor = withAlpha(sw[1], 190);
                m.outlineWidth = 8f;
                break;

            case OUTLINE_GLOW:
                m.outlineColor = sw[3];
                m.outlineWidth = 3f;
                m.primaryColor = sw[0];
                m.secondaryColor = withAlpha(sw[0], 190);
                // Halo behind the glyphs, not blur on them.
                m.glowRadius = 6f;
                m.glowColor = sw[0];
                break;

            case GRADIENT_FILL:
                m.primaryColor = sw[1];
                m.secondaryColor = withAlpha(sw[1], 190);
                m.outlineColor = sw[3];
                m.outlineWidth = 2f;
                m.gradientStops = new int[]{sw[0], sw[1], sw[2]};
                break;

            case SPLIT_HALF:
                m.primaryColor = sw[1];
                m.secondaryColor = withAlpha(sw[1], 190);
                m.outlineColor = sw[2];
                m.outlineWidth = 4f;
                break;

            case ROTATED_MARKER:
                m.borderStyle = 3;
                m.outlineColor = sw[3];
                m.primaryColor = sw[1];
                m.secondaryColor = withAlpha(Color.WHITE, 190);
                m.outlineWidth = 10f;
                m.activeWordTags = "\\frz-6";
                m.contextWordTags = "\\frz0";
                break;

            case CHROME:
                m.primaryColor = sw[0];
                m.secondaryColor = withAlpha(sw[0], 190);
                m.outlineColor = sw[3];
                m.outlineWidth = 2.5f;
                // Light -> mid -> dark reads as brushed metal.
                m.gradientStops = new int[]{sw[2], sw[0], sw[3]};
                break;

            case COMIC_OUTLINE:
                m.primaryColor = sw[0];
                m.secondaryColor = withAlpha(Color.WHITE, 190);
                m.outlineColor = sw[1];
                m.outlineWidth = 5f;
                break;

            case SERIF_ITALIC:
                m.italic = true;
                m.sizeScale = 0.9f;
                m.primaryColor = sw[0];
                m.secondaryColor = withAlpha(sw[0], 190);
                m.outlineWidth = 1.5f;
                break;

            case PLAIN:
            default:
                m.primaryColor = sw[0];
                m.secondaryColor = withAlpha(sw[0], 190);
                m.outlineColor = sw[3];
                m.outlineWidth = 2f;
                break;
        }

        // ---- bespoke overrides for the 10 modern ids ---------------------
        if (ModernStyleAssWriter.isModernStyle(styleId)) {
            String active = ModernStyleAssWriter.innerTagsFor(styleId, sw, true);
            String context = ModernStyleAssWriter.innerTagsFor(styleId, sw, false);
            if (!active.isEmpty()) m.activeWordTags = join(m.activeWordTags, active);
            if (!context.isEmpty()) m.contextWordTags = join(m.contextWordTags, context);

            // The effects that cannot be expressed as inline tags are declared
            // here instead, so the writer does not fake them with \blur on the
            // text (which blurs the glyphs rather than glowing behind them).
            if ("NEON_OUTLINE_GLOW".equals(styleId)) {
                m.glowRadius = 7f;
                m.glowColor = sw[0];
            } else if ("LIQUID_GRADIENT_SWEEP".equals(styleId)) {
                m.gradientStops = new int[]{sw[0], sw[1], sw[2]};
            } else if ("CHROME_METALLIC".equals(styleId)) {
                m.gradientStops = new int[]{sw[2], sw[0], sw[3]};
            } else if ("GLASSMORPHISM_CARD".equals(styleId)) {
                m.borderStyle = 3;
                m.outlineColor = withAlpha(sw[0], 110);
                m.outlineWidth = 8f;
            }
        }

        // ---- entrance / emphasis animation -------------------------------
        String anim = animationTagsFor(styleId);
        if (!anim.isEmpty()) m.activeWordTags = join(m.activeWordTags, anim);

        // ---- user overrides win over the template ------------------------
        if (o.activeWordColorOn) m.primaryColor = o.activeWordColor;

        if (o.strokeOn) {
            m.outlineColor = o.strokeColor;
            // The picker's stroke width is in device px at preview scale; the
            // caller scales outlineWidth into PlayRes units.
            m.outlineWidth = Math.max(m.outlineWidth, o.strokeWidthPx);
            if (m.borderStyle == 1) m.borderStyle = 1;
        }

        if (o.shadowOn) {
            m.shadowColor = o.shadowColor;
            m.shadowDepth = Math.max(m.shadowDepth, o.shadowOffsetPx);
        }

        if (o.captionBgOn) {
            m.borderStyle = 3;
            m.outlineColor = o.captionBgColor;
        }

        return m;
    }

    /**
     * Human-readable list of the effects in {@code styleId} that the exported
     * video cannot reproduce exactly. Shown to the user before export so the
     * app never implies the burn-in is a pixel-perfect copy of the preview.
     *
     * @return "" when the style exports faithfully
     */
    /**
     * Entrance and emphasis animation, as ASS {@code \t} transforms.
     *
     * <p>Only tags that libass actually interpolates are used. {@code \t}
     * re-parses its argument tags with an interpolation factor
     * ({@code ass_parse.c:670-725}), and a tag honours that factor only if it
     * mixes its new value with the current one by {@code pwr}. Reading the
     * parser, those are: {@code \blur}, {@code \1c}-{@code \4c},
     * {@code \alpha} and {@code \1a}-{@code \4a}, {@code \frx \fry \frz},
     * {@code \fax \fay}, {@code \fscx \fscy \fs \fsp}, {@code \bord
     * \xbord \ybord}, {@code \shad \xshad \yshad} and {@code \clip /
     * \iclip}. {@code \pos} and {@code \move} do NOT interpolate, so nothing
     * here tries to animate position through {@code \t}.
     *
     * <p>Times are relative to the start of the event, which is what
     * {@code \t(t1,t2,...)} expects.
     *
     * @return tags with no surrounding braces, or "" if this style is static
     */
    static String animationTagsFor(String styleId) {
        if (styleId == null) return "";
        switch (styleId) {
            case "BLUR_TO_FOCUS":
                // Start defocused and sharpen over 300ms. \blur interpolates.
                return "\\blur10\\t(0,300,\\blur0)";
            case "GLITCH_FLICKER":
                // Alpha stutter. Chained \t tags each take over from the last.
                return "\\t(0,60,\\1a&H90&)"
                        + "\\t(60,120,\\1a&H00&)"
                        + "\\t(120,170,\\1a&H60&)"
                        + "\\t(170,240,\\1a&H00&)";
            case "SHAKE_WIGGLE_EMPHASIS":
                // Rotate back and forth, settling flat.
                return "\\frz-4"
                        + "\\t(0,90,\\frz4)"
                        + "\\t(90,180,\\frz-2)"
                        + "\\t(180,270,\\frz0)";
            case "TILT_PERSPECTIVE_3D":
                // A static forward tilt: ASS has perspective projection, so a
                // fixed \frx reads as the caption leaning away from the camera.
                return "\\frx12";
            case "ROTATE_IN_3D_FLIP":
                // Flip in from edge-on. \fry interpolates.
                return "\\fry90\\t(0,350,\\fry0)";
            case "CUBE_ROTATE_3D":
                // A continuous Y rotation is the closest single-transform
                // equivalent; a real cube needs six faces and depth sorting.
                return "\\fry-18\\t(0,600,\\fry18)";
            default:
                return "";
        }
    }

    /**
     * A one-line, honest description of how this style is burned in, shown in
     * the UI while exporting so nobody mistakes the result for a pixel-perfect
     * copy of the preview. Empty means "no known difference".
     *
     * <p>The per-style switch comes first because it is more specific than the
     * per-treatment one: RAINBOW_CYCLE and GLITCH_FLICKER carry a treatment that
     * already has a note, and their own note is the accurate one.
     */
    public static String exportNotes(String styleId) {
        CaptionStyleDefinition def = CaptionStyleCatalog.byId(styleId);
        if (def == null) return "";

        if (styleId != null) {
            switch (styleId) {
                case "BLUR_TO_FOCUS":
                    return "Blur-in animated with a \\blur \\t ramp.";
                case "GLITCH_FLICKER":
                case "SHAKE_WIGGLE_EMPHASIS":
                    return "Animated with chained \\t transforms.";
                case "TILT_PERSPECTIVE_3D":
                    return "Burned in as a static \\frx tilt.";
                case "ROTATE_IN_3D_FLIP":
                case "CUBE_ROTATE_3D":
                    return "Animated with a \\fry transform; not a true 3D solid.";
                case "DEPTH_STACK_3D":
                    return "Stacked depth burned in flat.";
                case "RAINBOW_CYCLE":
                    return "Colour cycle burned in as static gradient bands; "
                            + "animating \\1c would cancel the karaoke highlight.";
                case "WAVY_BASELINE":
                    return "Per-word baseline wave burned in flat - it needs "
                            + "per-word positioning, which one line event cannot do.";
                default:
                    break;
            }
        }

        switch (def.treatment) {
            case GRADIENT_FILL:
                return "Gradient burned in as " + BAND_NOTE + " clipped colour bands.";
            case CHROME:
                return "Metallic gradient burned in as " + BAND_NOTE + " clipped colour bands.";
            default:
                return "";
        }
    }

    /** Kept in step with {@code AssSubtitleBuilder.GRADIENT_BANDS}. */
    private static final String BAND_NOTE = "10";

    private static int withAlpha(int rgb, int alpha) {
        return Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b;
        if (b == null || b.isEmpty()) return a;
        return a + b;
    }
}

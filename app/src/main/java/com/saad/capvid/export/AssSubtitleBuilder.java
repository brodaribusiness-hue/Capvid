package com.saad.capvid.export;

import com.saad.capvid.caption.CaptionLayout;
import com.saad.capvid.model.CaptionWord;
import com.saad.capvid.style.CaptionStyleOptions;

import java.util.List;
import java.util.Locale;

/**
 * Builds the .ass subtitle file that FFmpeg's {@code ass} filter burns into the
 * exported video.
 *
 * <h3>Keeping the burn-in and the preview identical</h3>
 * <ul>
 *   <li><b>Line composition and timing</b> come from {@link CaptionLayout}, the
 *       same class {@code CaptionOverlayView} draws from, so which words share a
 *       line and when each line is on screen cannot diverge.</li>
 *   <li><b>Coordinate space.</b> {@code posXFraction}/{@code posYFraction} are
 *       fractions of the <i>video frame</i> in both places. The preview converts
 *       them to view pixels through the letterboxed video rect
 *       ({@code CaptionFrameGeometry}); here they are multiplied straight into
 *       the video's pixel size. Previously the preview used fractions of the
 *       <i>screen</i> while this class used fractions of the <i>video</i>, and
 *       since {@code VideoView} letterboxes the picture inside a
 *       {@code match_parent} view the two were different places - on a portrait
 *       phone playing a landscape clip the caption sat in the black bar on
 *       screen but inside the picture in the export.</li>
 *   <li><b>Font size</b> is the preview's pixel text size divided by the
 *       displayed-video-to-video scale ({@code previewTextSizePx * videoHeight /
 *       previewVideoDisplayHeightPx}), i.e. the caption keeps the same fraction
 *       of the picture height it had on screen. The old code divided by the
 *       whole overlay height, which made the burn-in several times too small
 *       whenever the video was letterboxed.</li>
 *   <li><b>Vertical anchor</b> converts the preview's baseline anchor to
 *       libass's centre-of-line anchor via {@code previewBaselineToCenterPx}.</li>
 *   <li><b>Horizontal anchor</b> maps the preview's LEFT/CENTER/RIGHT rules
 *       (0.06W / posX*W / 0.94W, all fractions of the video) onto
 *       {@code \an4} / {@code \an5} / {@code \an6}.</li>
 *   <li><b>Word highlighting</b> uses native ASS karaoke ({@code \k}) with each
 *       word's slot lasting until the next word begins - the same rule
 *       {@code CaptionLayout.activeWordIndex} uses on screen.</li>
 * </ul>
 *
 * <h3>Effects that need more than one event</h3>
 * Two of the template treatments cannot be expressed as inline tags on a single
 * text run, so they are emitted structurally:
 * <ul>
 *   <li><b>Glow</b> ({@code Mapping.glowRadius}) becomes an extra event emitted
 *       immediately BEFORE the text, carrying a thick, blurred,
 *       fully-transparent-fill outline. libass orders images by Layer and then
 *       by ReadOrder ({@code ass_render.c:3100-3112}), so an earlier event on
 *       the same layer composites underneath a later one - no separate layer
 *       number is needed. This is the only way to get a halo in libass:
 *       {@code \blur} on the text event is applied to the glyph fill bitmap
 *       ({@code ass_render.c:2726}), which furs the letters instead of glowing
 *       behind them.</li>
 *   <li><b>Gradient</b> ({@code Mapping.gradientStops}) becomes
 *       {@value #GRADIENT_BANDS} events, each clipped to a horizontal band of
 *       the line box and carrying an interpolated colour. {\code \clip} takes
 *       absolute script coordinates (libass converts it with
 *       {@code x2scr_pos_scaled}, it is not offset by {@code \pos}), and every
 *       band repeats the same karaoke timings so the word highlight still
 *       advances inside each band.</li>
 * </ul>
 *
 * <h3>Timeline</h3>
 * Event times are written on the ORIGINAL, untrimmed timeline. That is required,
 * not a bug: {@code VideoExporter} trims with {@code -ss}/{@code -to} as
 * <i>output</i> options, which preserves the decoder's original PTS, so the ass
 * filter sees untrimmed timestamps. Offsetting the times here would double-apply
 * the trim and desynchronise the captions.
 */
public final class AssSubtitleBuilder {

    /** Colour bands a gradient fill is split into. */
    static final int GRADIENT_BANDS = 10;

    /** Everything the builder needs, so the call site cannot forget a metric. */
    public static final class Request {
        /** Transcript words, in time order. */
        public List<CaptionWord> words;

        /** Display width/height of the video AFTER rotation metadata is applied. */
        public int videoWidth = 1920;
        public int videoHeight = 1080;

        /** CaptionStyleType name, used to look the style up in the catalog. */
        public String styleId = "MINIMAL_FADE";

        /** User overrides; may be null (defaults are used). */
        public CaptionStyleOptions options;

        /** Family name to write into the .ass Style - must be the font's real
         *  name-table family, see {@code StyleFontMap.familyNameFor}. */
        public String assFontFamilyName = "All Genders v4";

        public boolean bold;
        public boolean italic;

        /**
         * True when the selected font file is itself a bold (or black) face.
         * Needed because libass picks a face by family + weight, not by
         * filename: {@code RobotoMono-Bold.ttf} and {@code RobotoMono-Regular.ttf}
         * both report the family "Roboto Mono", so without the bold flag
         * fontconfig is free to hand back the Regular face and the export comes
         * out in a lighter weight than the preview, which loads the file
         * directly.
         */
        public boolean fontAssetIsBold;
        /** True when the selected font file is an italic/oblique face. */
        public boolean fontAssetIsItalic;

        /** Caption anchor as fractions of the video frame, matching the preview. */
        public float posXFraction = 0.5f;
        public float posYFraction = 0.85f;

        /** Preview metrics, in device pixels, from CaptionOverlayView. */
        public float previewTextSizePx = 40f;
        /**
         * Height, in preview view pixels, of the rect the video actually
         * occupies - {@code CaptionOverlayView.getVideoRect().height}, NOT the
         * overlay's own height. Using the overlay height here was what made the
         * burned-in caption the wrong size.
         */
        public float previewVideoDisplayHeightPx = 1000f;
        public float previewBaselineToCenterPx = -14f;
        public float previewLineHeightPx = 52f;
    }

    private AssSubtitleBuilder() {
    }

    public static String build(Request r) {
        CaptionStyleOptions options = r.options != null ? r.options : new CaptionStyleOptions();
        StyleAssMapper.Mapping m = StyleAssMapper.map(r.styleId, options);

        int W = Math.max(2, r.videoWidth);
        int H = Math.max(2, r.videoHeight);

        // View pixels per video pixel. Both the font size and the baseline
        // offset are measured in preview view pixels, so dividing by this
        // converts them into video pixels.
        float displayH = r.previewVideoDisplayHeightPx > 1f ? r.previewVideoDisplayHeightPx : 1f;
        float scale = H / displayH;

        float fontSize = Math.max(6f, r.previewTextSizePx * scale * m.sizeScale);
        float outline = m.outlineWidth * scale;
        float shadowX = m.shadowX * scale;
        float shadowY = m.shadowY * scale;
        float glow = m.glowRadius * scale;
        float lineHeight = Math.max(1f, r.previewLineHeightPx * scale);

        // Preview anchors the caption on the text BASELINE; libass \an5 anchors
        // on the centre of the line box. Shift by the baseline->centre distance.
        float baseY = r.posYFraction * H + r.previewBaselineToCenterPx * scale;

        CaptionStyleOptions.Alignment align = options.alignment != null
                ? options.alignment : CaptionStyleOptions.Alignment.CENTER;
        int an;
        float baseX;
        switch (align) {
            case LEFT:
                an = 4;                 // middle-left
                baseX = W * 0.06f;      // same 6% inset the preview uses
                break;
            case RIGHT:
                an = 6;                 // middle-right
                baseX = W * 0.94f;      // same 94% right edge the preview uses
                break;
            case CENTER:
            default:
                an = 5;                 // middle-centre
                baseX = r.posXFraction * W;
                break;
        }

        List<CaptionLayout.Line> lines = CaptionLayout.group(
                r.words, options.lineBreakMode, CaptionLayout.DEFAULT_WORDS_PER_LINE);

        StringBuilder sb = new StringBuilder(8192);

        sb.append("[Script Info]\n");
        sb.append("; Generated by Capvid. Times are on the original (untrimmed) timeline.\n");
        sb.append("ScriptType: v4.00+\n");
        sb.append("PlayResX: ").append(W).append('\n');
        sb.append("PlayResY: ").append(H).append('\n');
        // 2 = no automatic word wrapping: we place each line ourselves.
        sb.append("WrapStyle: 2\n");
        sb.append("ScaledBorderAndShadow: yes\n");
        sb.append("Collisions: Normal\n");
        sb.append('\n');

        sb.append("[V4+ Styles]\n");
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, ")
                .append("OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ")
                .append("ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, ")
                .append("Alignment, MarginL, MarginR, MarginV, Encoding\n");
        sb.append(String.format(Locale.US,
                "Style: Capvid,%s,%.2f,%s,%s,%s,%s,%d,%d,0,0,100,100,0,0,%d,%.2f,%.2f,%d,20,20,20,1\n",
                escapeStyleName(r.assFontFamilyName),
                fontSize,
                bgra(m.primaryColor),
                bgra(m.secondaryColor),
                bgra(m.outlineColor),
                bgra(m.shadowColor),
                (m.bold || r.bold || r.fontAssetIsBold) ? -1 : 0,
                (m.italic || r.italic || r.fontAssetIsItalic) ? -1 : 0,
                m.borderStyle,
                outline,
                // Deliberately 0: the Style has one Shadow field and libass
                // applies it to both axes, so the directional offset is emitted
                // per event as \xshad / \yshad instead.
                0f,
                an));
        sb.append('\n');

        sb.append("[Events]\n");
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        if (lines.isEmpty()) {
            sb.append('\n');
            return sb.toString();
        }

        int pages = Math.max(1, Math.min(4, options.pageBreakLines));

        for (int active = 0; active < lines.size(); active++) {
            CaptionLayout.Line activeLine = lines.get(active);
            long startMs = activeLine.activeFromMs;
            long endMs = activeLine.activeToMs;
            if (endMs <= startMs) continue;

            // Same windowing as the preview: centre the active line, clamp to range.
            int windowStart = active - (pages - 1) / 2;
            windowStart = Math.max(0, Math.min(windowStart, Math.max(0, lines.size() - pages)));
            int windowEnd = Math.min(lines.size(), windowStart + pages);

            for (int li = windowStart; li < windowEnd; li++) {
                boolean isActiveLine = (li == active);
                float y = baseY + (li - active) * lineHeight;
                CaptionLayout.Line line = lines.get(li);
                String text = isActiveLine
                        ? karaokeText(line, options)
                        : staticText(line, options, m);
                String perLineTags = (isActiveLine ? m.activeWordTags : m.contextWordTags)
                        + shadowTags(shadowX, shadowY)
                        + wordSpacingTag(line, options, scale);

                // Halo first. Everything stays on Layer 0 and relies on read
                // order: libass sorts by (Layer, ReadOrder), so the halo, which
                // is written first, composites underneath the sharp text.
                if (glow > 0.01f) {
                    appendDialogue(sb, 0, startMs, endMs, an, baseX, y,
                            glowTags(m, glow, outline), plainText(lines.get(li), options));
                }

                if (m.gradientStops != null && m.gradientStops.length >= 2) {
                    appendGradientBands(sb, startMs, endMs, an, baseX, y,
                            fontSize, H, m, perLineTags, text);
                } else {
                    appendDialogue(sb, 0, startMs, endMs, an, baseX, y, perLineTags, text);
                }
            }
        }

        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Event emission
    // ------------------------------------------------------------------

    private static void appendDialogue(StringBuilder sb, int layer, long startMs, long endMs,
                                       int an, float x, float y, String extraTags, String text) {
        sb.append("Dialogue: ").append(layer).append(',')
                .append(formatTime(startMs)).append(',')
                .append(formatTime(endMs))
                .append(",Capvid,,0,0,0,,{\\an").append(an)
                .append(String.format(Locale.US, "\\pos(%.1f,%.1f)", x, y));
        if (extraTags != null && !extraTags.isEmpty()) sb.append(extraTags);
        sb.append('}').append(text).append('\n');
    }

    /** Directional shadow offset. Empty when there is no shadow. */
    private static String shadowTags(float x, float y) {
        if (Math.abs(x) < 0.01f && Math.abs(y) < 0.01f) return "";
        return String.format(Locale.US, "\\xshad%.2f\\yshad%.2f", x, y);
    }

    /**
     * The preview separates words by {@code options.wordSpacingPx}; ASS has no
     * per-word gap, only {@code \fsp}, which adds to EVERY glyph's advance.
     * Spreading the total extra width across the line's glyphs keeps the line's
     * overall width - and therefore the caption's footprint in the frame - the
     * same as the preview, at the cost of slightly even letter tracking.
     */
    private static String wordSpacingTag(CaptionLayout.Line line, CaptionStyleOptions options,
                                         float scale) {
        float gapPx = options.wordSpacingPx * scale;
        if (gapPx < 0.01f || line.words.size() < 2) return "";
        int glyphs = 0;
        for (CaptionWord w : line.words) glyphs += Math.max(1, trim(w.text).length());
        glyphs += line.words.size() - 1;          // the separator spaces
        if (glyphs <= 0) return "";
        float fsp = ((line.words.size() - 1) * gapPx) / glyphs;
        return String.format(Locale.US, "\\fsp%.2f", fsp);
    }

    /**
     * The halo: a thick outline in the glow colour, blurred, with the glyph fill
     * made fully transparent so only the soft edge shows.
     */
    private static String glowTags(StyleAssMapper.Mapping m, float glow, float outline) {
        return "\\1a&HFF&"
                + "\\3c" + bgr(m.glowColor)
                + "\\3a&H00&"
                + String.format(Locale.US, "\\bord%.2f", glow + outline)
                + String.format(Locale.US, "\\blur%.2f", glow)
                + "\\shad0";
    }

    /**
     * A gradient fill, as {@value #GRADIENT_BANDS} horizontally clipped bands.
     *
     * <p>The bands span the line box generously ({@code y +/- fontSize}) because
     * anything outside the glyphs is invisible anyway, and because the exact
     * ascent/descent of the resolved face is not known here. Each band repeats
     * the full karaoke text, so the per-word highlight still advances within
     * every band; only the "sung" colour changes from band to band.
     */
    private static void appendGradientBands(StringBuilder sb, long startMs, long endMs,
                                            int an, float x, float y, float fontSize, int frameH,
                                            StyleAssMapper.Mapping m, String extraTags, String text) {
        int[] stops = m.gradientStops;
        float top = y - fontSize;
        float bandH = (2f * fontSize) / GRADIENT_BANDS;
        for (int b = 0; b < GRADIENT_BANDS; b++) {
            float y0 = top + b * bandH;
            float y1 = y0 + bandH + 0.5f;   // +0.5 so neighbouring bands cannot seam
            int colour = sampleGradient(stops, (b + 0.5f) / GRADIENT_BANDS);
            String clip = String.format(Locale.US, "\\clip(0,%.1f,%d,%.1f)",
                    Math.max(0f, y0), frameH, Math.min((float) frameH, y1));
            String tags = clip + "\\1c" + bgr(colour) + "\\1a&H00&";
            appendDialogue(sb, 0, startMs, endMs, an, x, y,
                    extraTags == null || extraTags.isEmpty() ? tags : tags + extraTags, text);
        }
    }

    /**
     * Linear interpolation across equally spaced stops.
     *
     * @param t 0 at the first stop, 1 at the last
     */
    static int sampleGradient(int[] stops, float t) {
        if (stops == null || stops.length == 0) return 0xFFFFFFFF;
        if (stops.length == 1) return stops[0];
        float clamped = Math.max(0f, Math.min(1f, t));
        float scaled = clamped * (stops.length - 1);
        int i = (int) Math.floor(scaled);
        if (i >= stops.length - 1) return stops[stops.length - 1];
        float f = scaled - i;
        int a = stops[i];
        int b = stops[i + 1];
        int alpha = mix((a >> 24) & 0xFF, (b >> 24) & 0xFF, f);
        int red = mix((a >> 16) & 0xFF, (b >> 16) & 0xFF, f);
        int green = mix((a >> 8) & 0xFF, (b >> 8) & 0xFF, f);
        int blue = mix(a & 0xFF, b & 0xFF, f);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int mix(int from, int to, float f) {
        return Math.max(0, Math.min(255, Math.round(from + (to - from) * f)));
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    /**
     * The active line: each word carries a {@code \k} karaoke slot lasting until
     * the NEXT word begins, which is the same rule the preview uses to decide
     * which word is highlighted. libass paints a word in SecondaryColour to the
     * right of the karaoke break and PrimaryColour to the left of it
     * ({@code ass_render.c:387-406}), so the highlight advances word by word.
     */
    private static String karaokeText(CaptionLayout.Line line, CaptionStyleOptions options) {
        StringBuilder sb = new StringBuilder();
        List<CaptionWord> ws = line.words;
        for (int i = 0; i < ws.size(); i++) {
            long from = ws.get(i).startMs;
            long to = (i < ws.size() - 1) ? ws.get(i + 1).startMs : line.activeToMs;
            long centis = Math.max(1L, (to - from) / 10L);
            if (i > 0) sb.append(' ');
            sb.append("{\\k").append(centis).append('}')
                    .append(escape(options.applyCapitalization(trim(ws.get(i).text))));
        }
        return sb.toString();
    }

    /**
     * A context line (a neighbouring line shown for readability while another
     * line is the active one). The preview draws every word on it in the muted
     * context colour, so force PrimaryColour to that colour for the whole event;
     * with no {@code \k} present libass would otherwise paint it in the active
     * colour.
     */
    private static String staticText(CaptionLayout.Line line, CaptionStyleOptions options,
                                     StyleAssMapper.Mapping m) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\\1c").append(bgr(m.secondaryColor))
                .append("\\1a").append(alphaTag(m.secondaryColor)).append('}');
        sb.append(plainText(line, options));
        return sb.toString();
    }

    /** The line's words with no karaoke and no colour override. */
    private static String plainText(CaptionLayout.Line line, CaptionStyleOptions options) {
        StringBuilder sb = new StringBuilder();
        List<CaptionWord> ws = line.words;
        for (int i = 0; i < ws.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(escape(options.applyCapitalization(trim(ws.get(i).text))));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------

    /** ASS time: H:MM:SS.CC (centiseconds). */
    static String formatTime(long ms) {
        long clamped = Math.max(0L, ms);
        long h = clamped / 3600000L;
        long mnt = (clamped % 3600000L) / 60000L;
        long s = (clamped % 60000L) / 1000L;
        long cs = (clamped % 1000L) / 10L;
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, mnt, s, cs);
    }

    /**
     * Neutralises characters libass would otherwise interpret: an override block
     * opener/closer and a backslash (which starts a tag). Also flattens newlines
     * so a stray \n in a token cannot break the Dialogue line format.
     */
    static String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "/")
                .replace("{", "(")
                .replace("}", ")")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    /** Font family names must not contain a comma (it is the field separator). */
    static String escapeStyleName(String family) {
        if (family == null || family.isEmpty()) return "Sans Serif";
        return family.replace(",", " ");
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** Android ARGB -> ASS &HAABBGGRR& (ASS alpha: 00 opaque, FF transparent). */
    static String bgra(int androidColor) {
        return "&H" + alphaTag(androidColor) + bgr(androidColor).substring(2);
    }

    /** Android ARGB -> ASS &HBBGGRR& (colour only). */
    static String bgr(int androidColor) {
        int r = (androidColor >> 16) & 0xFF;
        int g = (androidColor >> 8) & 0xFF;
        int b = androidColor & 0xFF;
        return "&H" + hex2(b) + hex2(g) + hex2(r) + "&";
    }

    /** Two hex digits of ASS alpha (inverted from Android's). */
    static String alphaTag(int androidColor) {
        return hex2(255 - ((androidColor >> 24) & 0xFF));
    }

    private static String hex2(int v) {
        String h = Integer.toHexString(Math.max(0, Math.min(255, v))).toUpperCase(Locale.US);
        return h.length() == 1 ? "0" + h : h;
    }
}

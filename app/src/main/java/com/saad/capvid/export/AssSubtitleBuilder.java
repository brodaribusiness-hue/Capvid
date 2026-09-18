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
 * <h3>What this replaces</h3>
 * The previous version emitted one {@code Dialogue} per <em>word</em>, and every
 * one of them was pinned to the same {@code \pos(x,y)}. Because each word has its
 * own time window, the exported video showed a single word at a time in the
 * middle of the frame - while the on-screen preview showed a line of up to four
 * words with the spoken one highlighted. It also ignored the selected style and
 * every user override (colour, stroke, shadow, background, alignment,
 * capitalisation, line breaks), used a hard-coded {@code textSizeSp * 2.5f} for
 * the font size, and anchored on the text centre where the preview anchors on the
 * baseline. In short: the burn-in did not resemble the preview.
 *
 * <h3>How the two are kept in sync now</h3>
 * <ul>
 *   <li><b>Line composition and timing</b> come from {@link CaptionLayout}, the
 *       exact same class {@code CaptionOverlayView} uses, so which words share a
 *       line and when each line is on screen cannot diverge.</li>
 *   <li><b>Font size</b> is derived from the preview's real pixel text size and
 *       the preview's real view height ({@code previewTextSizePx *
 *       videoHeight / previewOverlayHeightPx}) instead of a magic constant, so
 *       the caption occupies the same fraction of the frame height as it does of
 *       the screen.</li>
 *   <li><b>Vertical anchor</b> converts the preview's baseline Y to libass's
 *       centre-of-line anchor via {@code previewBaselineToCenterPx}.</li>
 *   <li><b>Horizontal anchor</b> maps the preview's LEFT/CENTER/RIGHT rules
 *       (0.06W / posX*W / 0.94W) onto {@code \an4} / {@code \an5} / {@code \an6}.</li>
 *   <li><b>Multi-line pages</b> ({@code pageBreakLines}) emit one event per
 *       visible line with the same inter-line offset the preview uses.</li>
 *   <li><b>Word highlighting</b> uses native ASS karaoke ({@code \k}) with each
 *       word's duration set to "until the next word starts" - the same rule
 *       {@code CaptionLayout.activeWordIndex} uses in the preview.</li>
 * </ul>
 *
 * <h3>Timeline</h3>
 * Event times are written on the ORIGINAL, untrimmed timeline. That is required,
 * not a bug: {@code VideoExporter} trims with {@code -ss}/{@code -to} as
 * <i>output</i> options, which keeps the decoder's original PTS, so the ass
 * filter sees untrimmed timestamps. Offsetting the times here would double-apply
 * the trim and desynchronise the captions.
 *
 * <h3>Known, deliberate differences from the preview</h3>
 * See {@link StyleAssMapper#exportNotes(String)}. Canvas-only effects (true
 * gradient fills, BlurMaskFilter glows, Android {@code Camera} 3D transforms,
 * per-frame animation) have no libass equivalent. Per-word <i>geometric</i>
 * treatments are applied to the whole active line rather than to one word at a
 * time, because a single libass line event cannot vary them word by word; the
 * per-word colour highlight <i>is</i> exact, via karaoke.
 */
public final class AssSubtitleBuilder {

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

        /** Caption anchor as fractions of the frame, matching the preview. */
        public float posXFraction = 0.5f;
        public float posYFraction = 0.85f;

        /** Preview metrics, in device pixels, from CaptionOverlayView. */
        public float previewTextSizePx = 40f;
        public float previewOverlayHeightPx = 1000f;
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

        // Preview pixels -> PlayRes units. libass rescales PlayRes onto the
        // actual frame, so expressing everything in PlayRes keeps the caption at
        // the same fraction of the frame as it is of the screen.
        float overlayH = r.previewOverlayHeightPx > 1f ? r.previewOverlayHeightPx : 1f;
        float scale = H / overlayH;

        float fontSize = Math.max(6f, r.previewTextSizePx * scale * m.sizeScale);
        float outline = m.outlineWidth * scale;
        float shadow = m.shadowDepth * scale;
        float blur = m.blur * scale;
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

        StringBuilder sb = new StringBuilder(4096);

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
                (m.bold || r.bold) ? -1 : 0,
                (m.italic || r.italic) ? -1 : 0,
                m.borderStyle,
                outline,
                shadow,
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
                String text = isActiveLine
                        ? karaokeText(lines.get(li), options, m)
                        : staticText(lines.get(li), options, m);

                sb.append(String.format(Locale.US,
                        "Dialogue: 0,%s,%s,Capvid,,0,0,0,,{\\an%d\\pos(%.1f,%.1f)%s}%s\n",
                        formatTime(startMs), formatTime(endMs),
                        an, baseX, y,
                        (isActiveLine ? m.activeWordTags : m.contextWordTags),
                        text));
            }
        }

        return sb.toString();
    }

    /**
     * The active line: each word carries a {@code \k} karaoke slot lasting until
     * the NEXT word begins, which is the same rule the preview uses to decide
     * which word is highlighted. libass paints a word in SecondaryColour before
     * its slot and PrimaryColour after, so the highlight advances word by word.
     */
    private static String karaokeText(CaptionLayout.Line line, CaptionStyleOptions options,
                                      StyleAssMapper.Mapping m) {
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

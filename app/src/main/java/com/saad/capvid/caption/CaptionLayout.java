package com.saad.capvid.caption;

import com.saad.capvid.model.CaptionWord;
import com.saad.capvid.style.CaptionStyleOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * THE SINGLE SOURCE OF TRUTH for how a flat list of transcribed words is turned
 * into display lines, and for when each of those lines is on screen.
 *
 * <p>Both renderers derive their layout from this class:
 * <ul>
 *   <li>{@link CaptionOverlayView} - the live on-device preview (Android Canvas)</li>
 *   <li>{@code AssSubtitleBuilder} - the .ass file burned in by FFmpeg at export</li>
 * </ul>
 *
 * <p>Before this class existed each renderer had its own copy of the grouping
 * rules. The preview grouped words into lines of up to four and drew the whole
 * line; the exporter emitted one {@code Dialogue} per <em>word</em>, all pinned
 * to the same {@code \pos}, so the exported video showed one word at a time in
 * the middle of the frame while the preview showed a four-word line. Making both
 * sides call {@link #group} is what removes that divergence - line composition
 * and line timing are now literally the same code path.
 *
 * <p>This class is deliberately free of any {@code android.*} import so it can
 * be exercised by plain JVM unit tests ({@code app/src/test}) without Robolectric.
 */
public final class CaptionLayout {

    /** Max words on a line in PUNCTUATION mode - same cadence the preview always used. */
    public static final int DEFAULT_WORDS_PER_LINE = 4;

    /**
     * How long the last line stays on screen after its final word has ended.
     * Matches the trailing buffer {@link CaptionOverlayView} has always applied
     * to the final word, so the last caption does not blink off early.
     */
    public static final long TRAILING_HOLD_MS = 400L;

    private CaptionLayout() {
    }

    /** One display line plus the time window during which it is the ACTIVE line. */
    public static final class Line {

        /** Words on this line, in reading order. Never empty. */
        public final List<CaptionWord> words;

        /** Index of {@link #words}.get(0) in the original flat word list. */
        public final int firstWordIndex;

        /** Start of the first word on this line. */
        public final long startMs;

        /** End of the last word on this line (before {@link #TRAILING_HOLD_MS}). */
        public final long endMs;

        /**
         * Start of the active window: when this line becomes the centred line.
         * Equal to {@link #startMs}.
         */
        public final long activeFromMs;

        /**
         * End of the active window: when the NEXT line takes over as the centred
         * line. For the last line this is {@link #endMs} + {@link #TRAILING_HOLD_MS}.
         *
         * <p>This "active until the next line starts" rule mirrors the rule the
         * preview has always used for words. Whisper word timestamps almost
         * always leave a small gap between words, so keying visibility to the
         * exact [start,end] window would leave the screen blank during every gap.
         */
        public final long activeToMs;

        Line(List<CaptionWord> words, int firstWordIndex, long startMs, long endMs, long activeToMs) {
            this.words = Collections.unmodifiableList(words);
            this.firstWordIndex = firstWordIndex;
            this.startMs = startMs;
            this.endMs = endMs;
            this.activeFromMs = startMs;
            this.activeToMs = activeToMs;
        }

        /** Index of {@code word} on this line, or -1 if it is not on this line. */
        public int indexOf(CaptionWord word) {
            for (int i = 0; i < words.size(); i++) {
                if (words.get(i) == word) return i;
            }
            return -1;
        }

        /** Plain text of the whole line, single-spaced. */
        public String text() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words.size(); i++) {
                if (i > 0) sb.append(' ');
                String t = words.get(i).text;
                sb.append(t == null ? "" : t.trim());
            }
            return sb.toString();
        }
    }

    /**
     * Splits {@code words} into display lines.
     *
     * @param words           transcript words in time order; null/empty yields an empty list
     * @param mode            how aggressively to break (see {@link CaptionStyleOptions.LineBreakMode})
     * @param maxWordsPerLine hard cap on words per line in PUNCTUATION mode
     */
    public static List<Line> group(List<CaptionWord> words,
                                   CaptionStyleOptions.LineBreakMode mode,
                                   int maxWordsPerLine) {
        List<Line> lines = new ArrayList<>();
        if (words == null || words.isEmpty()) return lines;
        int cap = Math.max(1, maxWordsPerLine);
        CaptionStyleOptions.LineBreakMode m =
                mode != null ? mode : CaptionStyleOptions.LineBreakMode.PUNCTUATION;

        List<int[]> spans = new ArrayList<>();

        switch (m) {
            case SINGLE_WORD:
                for (int i = 0; i < words.size(); i++) spans.add(new int[]{i, i + 1});
                break;

            case RANDOM: {
                // Stable seed so re-grouping (e.g. after a style change) does not
                // reshuffle the lines under the user's feet.
                Random rnd = new Random(42);
                int i = 0;
                while (i < words.size()) {
                    int len = 2 + rnd.nextInt(4);
                    int end = Math.min(words.size(), i + len);
                    spans.add(new int[]{i, end});
                    i = end;
                }
                break;
            }

            case PUNCTUATION:
            default: {
                int start = 0;
                for (int i = 0; i < words.size(); i++) {
                    String t = words.get(i).text == null ? "" : words.get(i).text.trim();
                    boolean endsPunctuation = t.matches(".*[.!?,]$");
                    if (endsPunctuation || (i - start + 1) >= cap) {
                        spans.add(new int[]{start, i + 1});
                        start = i + 1;
                    }
                }
                if (start < words.size()) spans.add(new int[]{start, words.size()});
                break;
            }
        }

        for (int s = 0; s < spans.size(); s++) {
            int from = spans.get(s)[0];
            int to = spans.get(s)[1];
            List<CaptionWord> lineWords = new ArrayList<>(to - from);
            for (int i = from; i < to; i++) lineWords.add(words.get(i));

            long startMs = safeStart(lineWords.get(0));
            long endMs = safeEnd(lineWords.get(lineWords.size() - 1));
            long activeTo = (s < spans.size() - 1)
                    ? safeStart(words.get(spans.get(s + 1)[0]))
                    : endMs + TRAILING_HOLD_MS;
            // A zero/negative-length window would make the line invisible, so
            // never let the next line's start precede this line's start.
            if (activeTo <= startMs) activeTo = startMs + 1;
            lines.add(new Line(lineWords, from, startMs, endMs, activeTo));
        }
        return lines;
    }

    /** Convenience overload using {@link #DEFAULT_WORDS_PER_LINE}. */
    public static List<Line> group(List<CaptionWord> words, CaptionStyleOptions.LineBreakMode mode) {
        return group(words, mode, DEFAULT_WORDS_PER_LINE);
    }

    /**
     * Which line is the centred/active line at {@code timeMs}, or -1 when the
     * playhead is before the first line or after the last line's hold window.
     */
    public static int activeLineIndex(List<Line> lines, long timeMs) {
        if (lines == null || lines.isEmpty()) return -1;
        if (timeMs < lines.get(0).activeFromMs) return -1;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (timeMs >= l.activeFromMs && timeMs < l.activeToMs) return i;
        }
        return -1;
    }

    /**
     * Index of the currently-spoken WORD at {@code timeMs}, or -1 if none.
     * Preserved from the preview's original behaviour (a word stays active until
     * the next word begins) so preview animation timing is unchanged.
     */
    public static int activeWordIndex(List<CaptionWord> words, long timeMs) {
        if (words == null || words.isEmpty()) return -1;
        if (timeMs < safeStart(words.get(0))) return -1;
        for (int i = 0; i < words.size(); i++) {
            CaptionWord w = words.get(i);
            long segmentEnd = (i < words.size() - 1)
                    ? safeStart(words.get(i + 1))
                    : safeEnd(w) + TRAILING_HOLD_MS;
            if (timeMs >= safeStart(w) && timeMs < segmentEnd) return i;
        }
        return words.size() - 1;
    }

    private static long safeStart(CaptionWord w) {
        return w == null ? 0L : w.startMs;
    }

    private static long safeEnd(CaptionWord w) {
        if (w == null) return 0L;
        return Math.max(w.endMs, w.startMs);
    }
}

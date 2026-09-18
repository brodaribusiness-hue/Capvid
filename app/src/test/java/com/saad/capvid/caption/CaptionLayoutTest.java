package com.saad.capvid.caption;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.saad.capvid.model.CaptionWord;
import com.saad.capvid.style.CaptionStyleOptions;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JVM tests for the layout engine that BOTH the on-screen preview and the
 * exported .ass file are built from. These are the rules that used to be
 * duplicated (and had drifted apart) in CaptionOverlayView and
 * AssSubtitleBuilder, which is why the preview and the burn-in disagreed.
 */
public class CaptionLayoutTest {

    private static CaptionWord w(String text, long start, long end) {
        return new CaptionWord(text, start, end);
    }

    private static List<CaptionWord> sentence() {
        return new ArrayList<>(Arrays.asList(
                w("hello", 0, 400),
                w("there", 1000, 1400),
                w("world", 2000, 2400),
                w("again", 3000, 3400),
                w("now", 4000, 4400)));
    }

    @Test
    public void punctuationModeBreaksAtFourWordsAndAtPunctuation() {
        List<CaptionWord> words = new ArrayList<>(Arrays.asList(
                w("one", 0, 100), w("two", 200, 300), w("three", 400, 500),
                w("four", 600, 700), w("five", 800, 900), w("six.", 1000, 1100),
                w("seven", 1200, 1300)));

        List<CaptionLayout.Line> lines =
                CaptionLayout.group(words, CaptionStyleOptions.LineBreakMode.PUNCTUATION);

        assertEquals("4-word cap should close the first line", 3, lines.size());
        assertEquals(4, lines.get(0).words.size());
        assertEquals(2, lines.get(1).words.size());
        assertEquals("six.", lines.get(1).words.get(1).text);
        assertEquals(1, lines.get(2).words.size());
    }

    @Test
    public void singleWordModePutsOneWordPerLine() {
        List<CaptionLayout.Line> lines =
                CaptionLayout.group(sentence(), CaptionStyleOptions.LineBreakMode.SINGLE_WORD);
        assertEquals(5, lines.size());
        for (CaptionLayout.Line l : lines) assertEquals(1, l.words.size());
    }

    @Test
    public void randomModeIsDeterministicAcrossCalls() {
        List<CaptionLayout.Line> a =
                CaptionLayout.group(sentence(), CaptionStyleOptions.LineBreakMode.RANDOM);
        List<CaptionLayout.Line> b =
                CaptionLayout.group(sentence(), CaptionStyleOptions.LineBreakMode.RANDOM);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).words.size(), b.get(i).words.size());
        }
    }

    @Test
    public void emptyAndNullInputProduceNoLines() {
        assertTrue(CaptionLayout.group(null, CaptionStyleOptions.LineBreakMode.PUNCTUATION).isEmpty());
        assertTrue(CaptionLayout.group(new ArrayList<CaptionWord>(),
                CaptionStyleOptions.LineBreakMode.PUNCTUATION).isEmpty());
    }

    @Test
    public void everyActiveWindowHasPositiveLength() {
        // Words with identical or backwards timestamps must not produce a
        // zero-length window: such a line would never be visible on screen.
        List<CaptionWord> words = new ArrayList<>(Arrays.asList(
                w("a", 5000, 5000), w("b", 5000, 5000), w("c", 1000, 900)));
        List<CaptionLayout.Line> lines =
                CaptionLayout.group(words, CaptionStyleOptions.LineBreakMode.SINGLE_WORD);
        for (CaptionLayout.Line l : lines) {
            assertTrue("active window must be > 0 for '" + l.text() + "'",
                    l.activeToMs > l.activeFromMs);
        }
    }

    @Test
    public void activeLineIsSelectedByWindowNotByExactWordTimes() {
        // There is a 600ms gap between "hello" (ends 400) and "there" (starts
        // 1000). The first line must still be the active one inside that gap.
        List<CaptionLayout.Line> lines =
                CaptionLayout.group(sentence(), CaptionStyleOptions.LineBreakMode.SINGLE_WORD);
        assertEquals(0, CaptionLayout.activeLineIndex(lines, 700));
        assertEquals(1, CaptionLayout.activeLineIndex(lines, 1000));
    }

    @Test
    public void activeLineIsMinusOneBeforeFirstLineAndAfterLastHold() {
        List<CaptionLayout.Line> lines =
                CaptionLayout.group(sentence(), CaptionStyleOptions.LineBreakMode.SINGLE_WORD);
        assertEquals(-1, CaptionLayout.activeLineIndex(lines, -1));
        CaptionLayout.Line last = lines.get(lines.size() - 1);
        assertEquals(lines.size() - 1, CaptionLayout.activeLineIndex(lines, last.activeToMs - 1));
        assertEquals(-1, CaptionLayout.activeLineIndex(lines, last.activeToMs));
    }

    @Test
    public void lastLineHoldsForTheTrailingBuffer() {
        List<CaptionLayout.Line> lines =
                CaptionLayout.group(sentence(), CaptionStyleOptions.LineBreakMode.SINGLE_WORD);
        CaptionLayout.Line last = lines.get(lines.size() - 1);
        assertEquals(last.endMs + CaptionLayout.TRAILING_HOLD_MS, last.activeToMs);
    }

    @Test
    public void activeWordFollowsNextWordStartRule() {
        List<CaptionWord> words = sentence();
        assertEquals(-1, CaptionLayout.activeWordIndex(words, -5));
        assertEquals(0, CaptionLayout.activeWordIndex(words, 0));
        assertEquals("still active inside the gap", 0, CaptionLayout.activeWordIndex(words, 999));
        assertEquals(1, CaptionLayout.activeWordIndex(words, 1000));
        assertEquals(4, CaptionLayout.activeWordIndex(words, 99999));
    }

    @Test
    public void lineTextJoinsWordsWithSingleSpaces() {
        List<CaptionLayout.Line> lines =
                CaptionLayout.group(sentence(), CaptionStyleOptions.LineBreakMode.PUNCTUATION);
        assertEquals("hello there world again", lines.get(0).text());
    }

    @Test
    public void lineStartAndEndTrackItsFirstAndLastWord() {
        List<CaptionLayout.Line> lines =
                CaptionLayout.group(sentence(), CaptionStyleOptions.LineBreakMode.PUNCTUATION);
        CaptionLayout.Line first = lines.get(0);
        assertEquals(0, first.startMs);
        assertEquals(3400, first.endMs);
        assertEquals(0, first.firstWordIndex);
        assertNotNull(first.words.get(0));
    }

    @Test
    public void maxWordsPerLineCapIsRespected() {
        List<CaptionWord> words = new ArrayList<>();
        for (int i = 0; i < 10; i++) words.add(w("w" + i, i * 1000L, i * 1000L + 500));
        List<CaptionLayout.Line> lines =
                CaptionLayout.group(words, CaptionStyleOptions.LineBreakMode.PUNCTUATION, 3);
        assertEquals(4, lines.size());
        assertEquals(3, lines.get(0).words.size());
        assertEquals(1, lines.get(3).words.size());
    }
}

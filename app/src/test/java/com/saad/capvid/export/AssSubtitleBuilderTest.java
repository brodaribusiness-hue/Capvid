package com.saad.capvid.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.saad.capvid.caption.CaptionLayout;
import com.saad.capvid.model.CaptionWord;
import com.saad.capvid.style.CaptionStyleCatalog;
import com.saad.capvid.style.CaptionStyleDefinition;
import com.saad.capvid.style.CaptionStyleOptions;

import android.graphics.Color;

import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JVM tests for the .ass generator. These assert the specific behaviours that
 * were broken before: every word pinned to one {@code \pos}, no style or user
 * override reaching the file, a hard-coded font-size multiplier, and a
 * centre-anchored export drawn from a baseline-anchored preview.
 */
public class AssSubtitleBuilderTest {

    private static CaptionWord w(String text, long start, long end) {
        return new CaptionWord(text, start, end);
    }

    private static List<CaptionWord> threeWords() {
        return new ArrayList<>(Arrays.asList(
                w("hello", 0, 400),
                w("there", 1000, 1400),
                w("world", 2000, 2400)));
    }

    private static AssSubtitleBuilder.Request req() {
        AssSubtitleBuilder.Request r = new AssSubtitleBuilder.Request();
        r.words = threeWords();
        r.videoWidth = 1920;
        r.videoHeight = 1080;
        r.styleId = "MINIMAL_FADE";
        r.options = new CaptionStyleOptions();
        r.options.lineBreakMode = CaptionStyleOptions.LineBreakMode.SINGLE_WORD;
        r.options.pageBreakLines = 1;
        r.assFontFamilyName = "All Genders v4";
        r.previewTextSizePx = 40f;
        r.previewVideoDisplayHeightPx = 1000f;
        r.previewBaselineToCenterPx = -14f;
        r.previewLineHeightPx = 52f;
        return r;
    }

    private static List<String> dialogues(String ass) {
        List<String> out = new ArrayList<>();
        for (String line : ass.split("\n")) {
            if (line.startsWith("Dialogue: ")) out.add(line);
        }
        return out;
    }

    @Test
    public void scriptInfoDeclaresTheVideoCanvasAndNoWrapping() {
        String ass = AssSubtitleBuilder.build(req());
        assertTrue(ass.contains("PlayResX: 1920"));
        assertTrue(ass.contains("PlayResY: 1080"));
        assertTrue("libass must not re-wrap the lines we placed ourselves",
                ass.contains("WrapStyle: 2"));
        assertTrue(ass.contains("ScaledBorderAndShadow: yes"));
    }

    @Test
    public void fontSizeIsDerivedFromPreviewMetricsNotAMagicConstant() {
        // 40 preview px on a 1000px-high overlay, onto a 1080px-high frame:
        // 40 * (1080/1000) = 43.20
        String ass = AssSubtitleBuilder.build(req());
        assertTrue("expected scaled font size 43.20 in:\n" + styleLine(ass),
                styleLine(ass).contains(",43.20,"));
    }

    @Test
    public void fontSizeScalesLinearlyWithVideoHeight() {
        AssSubtitleBuilder.Request r = req();
        r.videoHeight = 500;                       // scale = 0.5
        assertTrue(styleLine(AssSubtitleBuilder.build(r)).contains(",20.00,"));
    }

    @Test
    public void positionUsesTheRealFontFamilyName() {
        String ass = AssSubtitleBuilder.build(req());
        assertTrue(styleLine(ass).contains("Capvid,All Genders v4,"));
    }

    @Test
    public void centreAlignmentAnchorsOnTheFractionalX() {
        String ass = AssSubtitleBuilder.build(req());
        // 0.5 * 1920 = 960.0 ; y = 0.85*1080 + (-14 * 1.08) = 902.9
        assertTrue("expected \\an5\\pos(960.0,902.9) in:\n" + firstDialogue(ass),
                firstDialogue(ass).contains("\\an5\\pos(960.0,902.9)"));
    }

    @Test
    public void leftAlignmentUsesTheSameSixPercentInsetAsThePreview() {
        AssSubtitleBuilder.Request r = req();
        r.options.alignment = CaptionStyleOptions.Alignment.LEFT;
        String d = firstDialogue(AssSubtitleBuilder.build(r));
        assertTrue(d.contains("\\an4\\pos(115.2,"));   // 1920 * 0.06
    }

    @Test
    public void rightAlignmentUsesTheSameNinetyFourPercentEdgeAsThePreview() {
        AssSubtitleBuilder.Request r = req();
        r.options.alignment = CaptionStyleOptions.Alignment.RIGHT;
        String d = firstDialogue(AssSubtitleBuilder.build(r));
        assertTrue(d.contains("\\an6\\pos(1804.8,"));  // 1920 * 0.94
    }

    @Test
    public void oneEventPerLineWhenOnlyOneLineIsVisibleAtATime() {
        String ass = AssSubtitleBuilder.build(req());
        assertEquals("three words, one per line, one visible line => three events",
                3, dialogues(ass).size());
    }

    @Test
    public void eventWindowsMatchTheSharedLayoutEngineExactly() {
        String ass = AssSubtitleBuilder.build(req());
        List<CaptionLayout.Line> lines = CaptionLayout.group(
                threeWords(), CaptionStyleOptions.LineBreakMode.SINGLE_WORD);
        List<String> ds = dialogues(ass);
        assertEquals(lines.size(), ds.size());
        for (int i = 0; i < lines.size(); i++) {
            String expected = "Dialogue: 0,"
                    + AssSubtitleBuilder.formatTime(lines.get(i).activeFromMs) + ","
                    + AssSubtitleBuilder.formatTime(lines.get(i).activeToMs) + ",";
            assertTrue("event " + i + " should start with '" + expected + "' but was:\n" + ds.get(i),
                    ds.get(i).startsWith(expected));
        }
    }

    @Test
    public void multiLinePagesEmitOneEventPerVisibleLine() {
        AssSubtitleBuilder.Request r = req();
        r.options.pageBreakLines = 2;
        // The window is clamped to the range of lines exactly as the preview
        // clamps it, so the last active line shows the PREVIOUS line rather than
        // running off the end:
        //   active 0 -> lines {0,1};  active 1 -> {1,2};  active 2 -> {1,2}
        // = 6 events for 3 lines.
        assertEquals(6, dialogues(AssSubtitleBuilder.build(r)).size());
    }

    @Test
    public void karaokeDurationsSpanTheWholeActiveWindow() {
        // The active line's \k slots must add up to the window length, otherwise
        // the highlight either stalls before the line changes or overruns it.
        AssSubtitleBuilder.Request r = req();
        r.options.lineBreakMode = CaptionStyleOptions.LineBreakMode.PUNCTUATION;
        r.options.pageBreakLines = 1;
        String ass = AssSubtitleBuilder.build(r);
        List<CaptionLayout.Line> lines = CaptionLayout.group(
                threeWords(), CaptionStyleOptions.LineBreakMode.PUNCTUATION);
        assertEquals(1, lines.size());

        long windowMs = lines.get(0).activeToMs - lines.get(0).activeFromMs;
        long totalCentis = 0;
        Matcher m = Pattern.compile("\\\\k(\\d+)").matcher(firstDialogue(ass));
        int wordCount = 0;
        while (m.find()) {
            totalCentis += Long.parseLong(m.group(1));
            wordCount++;
        }
        assertEquals("one karaoke slot per word", 3, wordCount);
        assertEquals("karaoke must span the window", windowMs, totalCentis * 10L);
    }

    @Test
    public void timesAreOnTheUntrimmedTimeline() {
        // VideoExporter trims with output-side -ss/-to, which keeps the original
        // PTS, so the .ass must NOT be shifted.
        String ass = AssSubtitleBuilder.build(req());
        assertTrue(firstDialogue(ass).startsWith("Dialogue: 0,0:00:00.00,0:00:01.00,"));
    }

    @Test
    public void escapeNeutralisesOverrideSyntax() {
        assertEquals("(a)", AssSubtitleBuilder.escape("{a}"));
        assertEquals("a/b", AssSubtitleBuilder.escape("a\\b"));
        assertEquals("a b", AssSubtitleBuilder.escape("a\nb"));
        assertEquals("", AssSubtitleBuilder.escape(null));
    }

    @Test
    public void styleNameCannotBreakTheCsvFormatLine() {
        assertFalse(AssSubtitleBuilder.escapeStyleName("Roboto, Mono").contains(","));
        assertEquals("Sans Serif", AssSubtitleBuilder.escapeStyleName(null));
    }

    @Test
    public void formatTimeUsesCentiseconds() {
        assertEquals("0:00:00.00", AssSubtitleBuilder.formatTime(0));
        assertEquals("0:00:02.80", AssSubtitleBuilder.formatTime(2800));
        assertEquals("1:02:03.45", AssSubtitleBuilder.formatTime(3723456));
        assertEquals("0:00:00.00", AssSubtitleBuilder.formatTime(-500));
    }

    @Test
    public void boldAndItalicReachTheStyleLine() {
        AssSubtitleBuilder.Request r = req();
        r.bold = true;
        r.italic = true;
        String style = styleLine(AssSubtitleBuilder.build(r));
        assertTrue("Bold/Italic flags should be -1/-1 in: " + style,
                style.contains(",-1,-1,0,0,100,100,"));
    }

    @Test
    public void emptyTranscriptProducesAValidButEventlessFile() {
        AssSubtitleBuilder.Request r = req();
        r.words = new ArrayList<>();
        String ass = AssSubtitleBuilder.build(r);
        assertTrue(ass.contains("[Events]"));
        assertEquals(0, dialogues(ass).size());
    }

    @Test
    public void capitalisationOverrideIsApplied() {
        AssSubtitleBuilder.Request r = req();
        r.options.capitalization = CaptionStyleOptions.Capitalization.UPPERCASE;
        assertTrue(firstDialogue(AssSubtitleBuilder.build(r)).contains("HELLO"));
    }


    // ---- glow: a halo behind the text, not blur on the text ------------

    @Test
    public void glowIsAHaloEmittedBeforeTheSharpTextNotABlurOnTheGlyphs() {
        AssSubtitleBuilder.Request r = req();
        r.styleId = "GLOW_POP";
        List<String> d = dialogues(AssSubtitleBuilder.build(r));

        // pageBreakLines is 1, so every active line yields a halo + text pair.
        assertEquals(6, d.size());
        for (int i = 0; i < d.size(); i += 2) {
            String halo = d.get(i);
            String text = d.get(i + 1);
            // The halo is a thick, blurred outline whose FILL is fully
            // transparent, so only the soft edge shows.
            assertTrue("halo must blur: " + halo, halo.contains("\\blur"));
            assertTrue("halo must hide the fill: " + halo, halo.contains("\\1a&HFF&"));
            assertFalse("the sharp text must not be blurred: " + text,
                    text.contains("\\blur"));
            assertFalse("the sharp text must keep its fill: " + text,
                    text.contains("\\1a&HFF&"));
        }
    }

    @Test
    public void plainStylesEmitNoHalo() {
        AssSubtitleBuilder.Request r = req();
        r.styleId = "MINIMAL_FADE";
        List<String> d = dialogues(AssSubtitleBuilder.build(r));
        assertEquals(3, d.size());
        for (String line : d) {
            assertFalse("MINIMAL_FADE should not glow: " + line, line.contains("\\blur"));
        }
    }

    // ---- gradient: clipped colour bands -------------------------------

    @Test
    public void gradientStylesEmitOneClippedEventPerBand() {
        AssSubtitleBuilder.Request r = req();
        r.styleId = "GRADIENT_TEXT";
        List<String> d = dialogues(AssSubtitleBuilder.build(r));
        assertFalse(d.isEmpty());
        for (String line : d) {
            assertTrue("every gradient event must be clipped: " + line,
                    line.contains("\\clip("));
            assertTrue("every gradient event must set its own fill: " + line,
                    line.contains("\\1c&H"));
        }
        // pageBreakLines is 1, so each active line yields exactly one band stack
        assertEquals(AssSubtitleBuilder.GRADIENT_BANDS, d.size() / 3);
    }

    @Test
    public void gradientBandsStillCarryTheKaraokeTimings() {
        AssSubtitleBuilder.Request r = req();
        r.styleId = "GRADIENT_TEXT";
        for (String line : dialogues(AssSubtitleBuilder.build(r))) {
            assertTrue("each band must repeat the karaoke slots: " + line,
                    line.contains("{\\k"));
        }
    }

    @Test
    public void gradientInterpolationReachesBothEndStops() {
        int[] stops = {0xFF000000, 0xFFFFFFFF};
        assertEquals(0xFF000000, AssSubtitleBuilder.sampleGradient(stops, 0f));
        assertEquals(0xFFFFFFFF, AssSubtitleBuilder.sampleGradient(stops, 1f));
        // midpoint of black and white is mid grey in every channel
        int mid = AssSubtitleBuilder.sampleGradient(stops, 0.5f);
        assertEquals(128, (mid >> 16) & 0xFF);
        assertEquals(128, (mid >> 8) & 0xFF);
        assertEquals(128, mid & 0xFF);
        // out of range is clamped, not extrapolated
        assertEquals(0xFF000000, AssSubtitleBuilder.sampleGradient(stops, -2f));
        assertEquals(0xFFFFFFFF, AssSubtitleBuilder.sampleGradient(stops, 9f));
    }

    // ---- font weight ---------------------------------------------------

    @Test
    public void boldFontAssetSetsTheStyleBoldFlag() {
        AssSubtitleBuilder.Request plain = req();
        plain.fontAssetIsBold = false;
        String[] withoutBold = styleLine(AssSubtitleBuilder.build(plain)).split(",");

        AssSubtitleBuilder.Request boldReq = req();
        boldReq.fontAssetIsBold = true;
        String[] withBold = styleLine(AssSubtitleBuilder.build(boldReq)).split(",");

        // Field 8 of the V4+ Style line is Bold: 0 off, -1 on.
        assertEquals("0", withoutBold[7].trim());
        assertEquals("-1", withBold[7].trim());
    }


    // ---- animation -----------------------------------------------------

    @Test
    public void animatedStylesReachTheExportedLine() {
        AssSubtitleBuilder.Request r = req();
        r.styleId = "BLUR_TO_FOCUS";
        boolean sawTransform = false;
        for (String line : dialogues(AssSubtitleBuilder.build(r))) {
            if (line.contains("{\\k")) {          // the active line
                assertTrue("BLUR_TO_FOCUS must blur then sharpen: " + line,
                        line.contains("\\blur10") && line.contains("\\t(0,300,\\blur0)"));
                sawTransform = true;
            }
        }
        assertTrue("expected at least one active line", sawTransform);
    }

    @Test
    public void staticStylesEmitNoTransform() {
        AssSubtitleBuilder.Request r = req();
        r.styleId = "MINIMAL_FADE";
        for (String line : dialogues(AssSubtitleBuilder.build(r))) {
            assertFalse("MINIMAL_FADE is static: " + line, line.contains("\\t("));
        }
    }

    /**
     * libass only interpolates the tags that mix their new value with the
     * current one by the \t power factor (ass_parse.c). A tag outside that set
     * inside a \t silently does nothing, which is how an "animation" ends up
     * exported as a static frame while the notes claim otherwise.
     *
     * <p>Every tag name in the string is checked, including the ones nested
     * inside \t(...), so a chained transform cannot smuggle in a dead tag.
     */
    @Test
    public void animationsOnlyUseTagsLibassActuallyInterpolates() {
        String[] interpolable = {
                "blur", "1c", "2c", "3c", "4c", "alpha", "1a", "2a", "3a", "4a",
                "frx", "fry", "frz", "fax", "fay", "fscx", "fscy", "fs", "fsp",
                "bord", "xbord", "ybord", "shad", "xshad", "yshad", "clip", "iclip"
        };
        String[] animated = {
                "BLUR_TO_FOCUS", "GLITCH_FLICKER", "SHAKE_WIGGLE_EMPHASIS",
                "TILT_PERSPECTIVE_3D", "ROTATE_IN_3D_FLIP", "CUBE_ROTATE_3D"
        };
        for (String id : animated) {
            String tags = StyleAssMapper.animationTagsFor(id);
            assertFalse(id + " should be animated", tags.isEmpty());
            assertTrue(id + " must actually transform something: " + tags,
                    tags.contains("\\t(") || tags.contains("\\fr"));
            // An ASS tag name is an optional single leading digit (\1c, \4a)
            // followed by letters. Matching [a-z0-9]+ instead swallows the
            // tag's VALUE - \blur10 reads as a tag called "blur10".
            Matcher m = Pattern.compile("\\\\([0-9]?[a-z]+)").matcher(tags);
            int checked = 0;
            while (m.find()) {
                String name = m.group(1);
                if ("t".equals(name)) continue;      // the transform wrapper itself
                boolean ok = false;
                for (String allowed : interpolable) {
                    if (allowed.equals(name)) { ok = true; break; }
                }
                assertTrue(id + " uses \\" + name + ", which libass does not "
                        + "interpolate inside \\t", ok);
                checked++;
            }
            assertTrue(id + " should set at least one animated tag", checked > 0);
        }
    }

    /**
     * The UI shows exportNotes verbatim, and it names the band count. If
     * GRADIENT_BANDS ever changes and the note does not, the app would describe
     * its own output incorrectly - which is the exact failure mode this whole
     * file exists to prevent.
     */
    @Test
    public void theExportedNoteNamesTheRealBandCount() {
        String note = StyleAssMapper.exportNotes("GRADIENT_TEXT");
        assertTrue("note should mention the bands: " + note,
                note.contains(String.valueOf(AssSubtitleBuilder.GRADIENT_BANDS)));

        AssSubtitleBuilder.Request r = req();
        r.styleId = "GRADIENT_TEXT";
        int bands = 0;
        for (String line : dialogues(AssSubtitleBuilder.build(r))) {
            if (line.contains("\\clip(")) bands++;
        }
        // 3 lines, pageBreakLines 1, so one band stack per line
        assertEquals(AssSubtitleBuilder.GRADIENT_BANDS, bands / 3);
    }

    // ---- shadow direction and word spacing -----------------------------

    /** The Style's single Shadow field must stay 0; the offset goes per event. */
    @Test
    public void theStyleShadowFieldIsAlwaysZero() {
        AssSubtitleBuilder.Request r = req();
        r.options.shadowOn = true;
        r.options.shadowOffsetPx = 6f;
        // Shadow is field 18 of the V4+ Style line.
        assertEquals("0.00", styleLine(AssSubtitleBuilder.build(r)).split(",")[17].trim());
    }

    @Test
    public void aDownShadowMovesOnlyY() {
        AssSubtitleBuilder.Request r = req();
        r.options.shadowOn = true;
        r.options.shadowOffsetPx = 6f;
        r.options.shadowDirection = CaptionStyleOptions.ShadowDirection.DOWN;
        String d = firstDialogue(AssSubtitleBuilder.build(r));
        assertTrue("y must move: " + d, d.contains("\\yshad6.48"));
        assertTrue("x must not: " + d, d.contains("\\xshad0.00"));
    }

    @Test
    public void aRightShadowMovesOnlyX() {
        AssSubtitleBuilder.Request r = req();
        r.options.shadowOn = true;
        r.options.shadowOffsetPx = 6f;
        r.options.shadowDirection = CaptionStyleOptions.ShadowDirection.RIGHT;
        String d = firstDialogue(AssSubtitleBuilder.build(r));
        assertTrue("x must move: " + d, d.contains("\\xshad6.48"));
        assertTrue("y must not: " + d, d.contains("\\yshad0.00"));
    }

    @Test
    public void noShadowEmitsNoShadowTags() {
        assumeRealColours();
        AssSubtitleBuilder.Request r = req();
        r.options.shadowOn = false;
        for (String line : dialogues(AssSubtitleBuilder.build(r))) {
            assertFalse("no shadow expected: " + line, line.contains("shad"));
        }
    }

    /**
     * req() defaults to SINGLE_WORD, where every line holds one word and there
     * is no gap to export - so both of these need a line that actually has
     * several words on it.
     */
    private static AssSubtitleBuilder.Request multiWordReq() {
        AssSubtitleBuilder.Request r = req();
        r.options.lineBreakMode = CaptionStyleOptions.LineBreakMode.PUNCTUATION;
        return r;
    }

    @Test
    public void thePreviewWordSpacingReachesTheExport() {
        AssSubtitleBuilder.Request r = multiWordReq();
        r.options.wordSpacingPx = 22f;
        boolean sawTracking = false;
        for (String line : dialogues(AssSubtitleBuilder.build(r))) {
            if (line.contains("hello") && line.contains("there")) {
                assertTrue("a multi-word line must carry the gap: " + line,
                        line.contains("\\fsp"));
                sawTracking = true;
            }
        }
        assertTrue("expected a line with more than one word on it", sawTracking);
    }

    @Test
    public void zeroWordSpacingEmitsNoTrackingTag() {
        AssSubtitleBuilder.Request r = multiWordReq();
        r.options.wordSpacingPx = 0f;
        for (String line : dialogues(AssSubtitleBuilder.build(r))) {
            assertFalse("no tracking expected: " + line, line.contains("\\fsp"));
        }
    }

    @Test
    public void aSingleWordLineGetsNoTrackingTag() {
        // Nothing to space out, so emitting \fsp would only shift the word.
        AssSubtitleBuilder.Request r = req();
        r.options.wordSpacingPx = 22f;
        for (String line : dialogues(AssSubtitleBuilder.build(r))) {
            assertFalse("single-word line must not be tracked: " + line,
                    line.contains("\\fsp"));
        }
    }

    @Test
    public void theActiveWordBoxIsReportedAsNotExported() {
        CaptionStyleOptions withBox = new CaptionStyleOptions();
        withBox.activeWordBgOn = true;
        assertFalse(StyleAssMapper.optionNotes(withBox).isEmpty());

        CaptionStyleOptions without = new CaptionStyleOptions();
        without.activeWordBgOn = false;
        assertEquals("", StyleAssMapper.optionNotes(without));
    }

    // ---- preview and export must agree on colour -----------------------

    /**
     * CaptionOverlayView now takes its colours from this same mapping, so the
     * active-word colour the user picks in the Color tab is one value in one
     * place. These run without real catalog colours because the override is a
     * plain int, not something Color.parseColor has to resolve.
     */
    @Test
    public void theActiveWordColourOverrideReachesTheMapping() {
        CaptionStyleOptions o = new CaptionStyleOptions();
        o.activeWordColorOn = true;
        o.activeWordColor = 0xFF123456;
        assertEquals(0xFF123456, StyleAssMapper.map("MINIMAL_FADE", o).primaryColor);
    }

    @Test
    public void switchingTheActiveWordColourOffKeepsTheTemplateColour() {
        CaptionStyleOptions o = new CaptionStyleOptions();
        o.activeWordColorOn = false;
        o.activeWordColor = 0xFF123456;
        assertNotEquals("the override must not leak through when switched off",
                0xFF123456, StyleAssMapper.map("MINIMAL_FADE", o).primaryColor);
    }

    // ---- legibility ----------------------------------------------------

    /**
     * android.graphics.Color is a no-op stub under JVM unit tests
     * (returnDefaultValues = true), which makes Color.parseColor return 0 and
     * every catalog colour black. A test that measures contrast off the catalog
     * would then compare black against black, get 1.0, and either fail for a
     * reason that has nothing to do with the code or - worse - pass vacuously.
     * So those tests skip and say why. tools/audit_contrast.py does the same
     * check for real: it reads the hex literals straight out of the catalog
     * source, so the stub cannot reach it, and CI runs it.
     */
    private static void assumeRealColours() {
        Assume.assumeTrue("android.graphics.Color is stubbed in JVM unit tests",
                Color.parseColor("#FFFFFF") != 0);
    }

    private static StyleAssMapper.Mapping mappingWith(int fill, int outline) {
        StyleAssMapper.Mapping m = new StyleAssMapper.Mapping();
        m.primaryColor = fill;
        m.outlineColor = outline;
        m.borderStyle = 1;
        return m;
    }

    /**
     * The point of the whole exercise: run every template in the catalog
     * through the real mapper and score the result against the worst frame it
     * will ever sit on. This is the same measurement tools/audit_contrast.py
     * makes in Python, so the two disagree if either drifts.
     */
    @Test
    public void everyTemplateStaysReadableOnTheWorstFrame() {
        assumeRealColours();
        int checked = 0;
        for (CaptionStyleDefinition def : CaptionStyleCatalog.ALL_STYLES) {
            StyleAssMapper.Mapping m = StyleAssMapper.map(def.id, null);
            float worst = StyleAssMapper.worstCaseOverFrames(StyleAssMapper.blockColours(m));
            assertTrue(def.id + " (" + def.treatment + ") only holds "
                    + String.format(Locale.US, "%.2f", worst)
                    + ":1 on its worst frame, need " + StyleAssMapper.MIN_WORST_CASE,
                    worst >= StyleAssMapper.MIN_WORST_CASE);
            checked++;
        }
        assertEquals(60, checked);
    }

    /** White on black is the reference treatment; it must score ~4.62:1. */
    @Test
    public void whiteOnBlackIsTheReferencePoint() {
        float worst = StyleAssMapper.worstCaseOverFrames(
                new int[]{0xFFFFFFFF, 0xFF000000});
        assertEquals(4.62f, worst, 0.05f);
    }

    /**
     * A mid-luminance fill cannot be rescued by one extra colour, which is the
     * reason the legibility shadow exists at all.
     */
    @Test
    public void oneExtraColourCannotRescueAMidLuminanceFill() {
        int red = 0xFFFF3B30;
        assertTrue(StyleAssMapper.worstCaseOverFrames(new int[]{red, 0xFF000000})
                < StyleAssMapper.MIN_WORST_CASE);
        assertTrue(StyleAssMapper.worstCaseOverFrames(
                new int[]{red, 0xFFFAFAFA, 0xFF0A0A0F}) >= 4.0f);
    }

    @Test
    public void aTemplateThatAlreadyReadsGetsNoLegibilityShadow() {
        assumeRealColours();
        StyleAssMapper.Mapping m = StyleAssMapper.map("MINIMAL_FADE", null);
        assertEquals(0f, m.shadowY, 0.001f);
        assertEquals(0f, m.shadowX, 0.001f);
    }

    @Test
    public void aMidLuminanceTemplateGetsTheLegibilityShadow() {
        assumeRealColours();
        StyleAssMapper.Mapping m = StyleAssMapper.map("PUNCH_IN", null);
        assertTrue("PUNCH_IN should have been given a shadow", m.shadowY != 0f);
        assertTrue(StyleAssMapper.worstCaseOverFrames(StyleAssMapper.blockColours(m))
                >= StyleAssMapper.MIN_WORST_CASE);
    }

    /**
     * libass repaints a box in BackColour once a shadow offset is set
     * (ass_render.c:2737), so a box style must never be given one - it would
     * recolour the box. Box styles get their contrast from the palette.
     */
    @Test
    public void noBoxStyleIsGivenALegibilityShadow() {
        assumeRealColours();
        for (CaptionStyleDefinition def : CaptionStyleCatalog.ALL_STYLES) {
            StyleAssMapper.Mapping m = StyleAssMapper.map(def.id, null);
            if (m.borderStyle == 3) {
                assertEquals(def.id + " is a box and must not gain a shadow",
                        0f, m.shadowY, 0.001f);
            }
        }
    }

    /**
     * A shadow offset smaller than the outline width is painted over by the
     * main layer, so the colour the contrast score counts would not be on
     * screen at all.
     */
    @Test
    public void theLegibilityShadowClearsTheOutline() {
        assumeRealColours();
        int shifted = 0;
        for (CaptionStyleDefinition def : CaptionStyleCatalog.ALL_STYLES) {
            StyleAssMapper.Mapping m = StyleAssMapper.map(def.id, null);
            if (m.borderStyle == 1 && m.shadowY != 0f) {
                assertTrue(def.id + " shadow " + m.shadowY
                        + " is hidden behind its " + m.outlineWidth + " outline",
                        m.shadowY > m.outlineWidth);
                assertTrue(def.id + " shadow detaches from the text",
                        m.shadowY <= StyleAssMapper.LEGIBILITY_SHADOW_MAX);
                shifted++;
            }
        }
        assertTrue("expected some templates to need the legibility shadow", shifted > 0);
    }

    @Test
    public void legibilityLeavesASoundPaletteAlone() {
        StyleAssMapper.Mapping m = mappingWith(0xFFFFFFFF, 0xFF000000);
        StyleAssMapper.ensureLegible(m);
        assertEquals(0f, m.shadowY, 0.001f);
        assertEquals(0xFF000000, m.outlineColor);
    }

    @Test
    public void legibilityAddsADarkShadowWhenTheBlockHasNoDarkElement() {
        // White fill, white outline: readable on dark footage, invisible on
        // light footage. The block needs a dark element.
        StyleAssMapper.Mapping m = mappingWith(0xFFFFFFFF, 0xFFFFFFFF);
        StyleAssMapper.ensureLegible(m);
        assertEquals(StyleAssMapper.NEAR_BLACK, m.shadowColor);
        assertEquals(0xFFFFFFFF, m.outlineColor);
        assertTrue(StyleAssMapper.worstCaseOverFrames(StyleAssMapper.blockColours(m))
                >= StyleAssMapper.MIN_WORST_CASE);
    }

    @Test
    public void legibilityAddsALightShadowWhenTheBlockHasNoLightElement() {
        // Red fill on a black outline: both dark, so nothing separates on dark
        // footage. One extra colour cannot fix a mid-luminance fill, but a
        // light shadow alongside the existing dark outline can.
        StyleAssMapper.Mapping m = mappingWith(0xFFFF3B30, 0xFF000000);
        StyleAssMapper.ensureLegible(m);
        assertEquals(StyleAssMapper.NEAR_WHITE, m.shadowColor);
        assertEquals(0xFF000000, m.outlineColor);
        assertTrue(StyleAssMapper.worstCaseOverFrames(StyleAssMapper.blockColours(m))
                >= StyleAssMapper.MIN_WORST_CASE);
    }

    @Test
    public void legibilityRepaintsTheOutlineOnlyWhenBothItAndTheFillAreMid() {
        // Red on blue: neither is light or dark, so no single added colour
        // reaches the bar and the outline has to become the light element.
        StyleAssMapper.Mapping m = mappingWith(0xFFFF3B30, 0xFF2979FF);
        StyleAssMapper.ensureLegible(m);
        assertEquals(StyleAssMapper.NEAR_WHITE, m.outlineColor);
        assertEquals(StyleAssMapper.NEAR_BLACK, m.shadowColor);
        assertTrue(StyleAssMapper.worstCaseOverFrames(StyleAssMapper.blockColours(m))
                >= StyleAssMapper.MIN_WORST_CASE);
    }

    @Test
    public void legibilityNeverTouchesABoxStyle() {
        // BorderStyle 3: libass repaints the box in BackColour once a shadow
        // offset exists (ass_render.c:2737), which would recolour the box.
        StyleAssMapper.Mapping m = mappingWith(0xFFFFD400, 0xFFFFFFFF);
        m.borderStyle = 3;
        StyleAssMapper.ensureLegible(m);
        assertEquals(0f, m.shadowY, 0.001f);
        assertEquals(0xFFFFFFFF, m.outlineColor);
    }

    /** A user's own shadow choice still wins over the automatic one. */
    @Test
    public void theUserShadowOverridesTheLegibilityShadow() {
        CaptionStyleOptions o = new CaptionStyleOptions();
        o.shadowOn = true;
        o.shadowOffsetPx = 9f;
        o.shadowDirection = CaptionStyleOptions.ShadowDirection.RIGHT;
        StyleAssMapper.Mapping m = StyleAssMapper.map("PUNCH_IN", o);
        assertEquals(9f, m.shadowX, 0.001f);
        assertEquals(0f, m.shadowY, 0.001f);
    }

    // ---- helpers -------------------------------------------------------

    private static String styleLine(String ass) {
        for (String line : ass.split("\n")) {
            if (line.startsWith("Style: ")) return line;
        }
        throw new AssertionError("no Style: line in:\n" + ass);
    }

    private static String firstDialogue(String ass) {
        List<String> d = dialogues(ass);
        if (d.isEmpty()) throw new AssertionError("no Dialogue lines in:\n" + ass);
        return d.get(0);
    }
}

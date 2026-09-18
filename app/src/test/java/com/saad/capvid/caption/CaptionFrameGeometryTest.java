package com.saad.capvid.caption;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * JVM tests for the letterbox geometry.
 *
 * <p>These pin down the fix for the export/preview position divergence: the
 * caption is positioned as a fraction of the video frame, and both the overlay
 * and the exporter have to agree on where that frame sits inside the view.
 * Getting this wrong is what put the burned-in caption in a different place
 * than the one on screen.
 */
public class CaptionFrameGeometryTest {

    private static final float EPS = 0.01f;

    @Test
    public void landscapeVideoInAPortraitViewIsLetterboxedAndCentred() {
        // 1080x2400 phone showing a 1920x1080 clip: the picture is a band
        // across the middle, 1080 wide and 607.5 tall.
        CaptionFrameGeometry.Rect r =
                CaptionFrameGeometry.fittedVideoRect(1080f, 2400f, 1920, 1080);

        assertEquals(0f, r.left, EPS);
        assertEquals(1080f, r.width, EPS);
        assertEquals(607.5f, r.height, EPS);
        assertEquals((2400f - 607.5f) / 2f, r.top, EPS);
    }

    @Test
    public void portraitVideoInALandscapeViewIsPillarboxedAndCentred() {
        // 2400x1080 view showing a 1080x1920 clip: height fills, width does not.
        CaptionFrameGeometry.Rect r =
                CaptionFrameGeometry.fittedVideoRect(2400f, 1080f, 1080, 1920);

        assertEquals(1080f, r.height, EPS);
        assertEquals(607.5f, r.width, EPS);
        assertEquals((2400f - 607.5f) / 2f, r.left, EPS);
        assertEquals(0f, r.top, EPS);
    }

    @Test
    public void matchingAspectFillsTheWholeView() {
        CaptionFrameGeometry.Rect r =
                CaptionFrameGeometry.fittedVideoRect(1920f, 1080f, 1280, 720);

        assertEquals(0f, r.left, EPS);
        assertEquals(0f, r.top, EPS);
        assertEquals(1920f, r.width, EPS);
        assertEquals(1080f, r.height, EPS);
    }

    @Test
    public void theRectNeverExceedsTheView() {
        // Whatever the aspect ratio, the fit must stay inside the view - a rect
        // larger than the view would put the caption off the picture.
        int[][] cases = {{1920, 1080}, {1080, 1920}, {1000, 1000}, {3840, 2160}, {640, 360}};
        float[][] views = {{1080f, 2400f}, {2400f, 1080f}, {1440f, 1440f}};
        for (float[] v : views) {
            for (int[] c : cases) {
                CaptionFrameGeometry.Rect r =
                        CaptionFrameGeometry.fittedVideoRect(v[0], v[1], c[0], c[1]);
                assertTrue(r.left >= -EPS);
                assertTrue(r.top >= -EPS);
                assertTrue(r.width <= v[0] + EPS);
                assertTrue(r.height <= v[1] + EPS);
                assertTrue(r.left + r.width <= v[0] + EPS);
                assertTrue(r.top + r.height <= v[1] + EPS);
            }
        }
    }

    @Test
    public void unknownVideoDimensionsFallBackToTheWholeView() {
        // Before the player reports the size the caption still has to draw
        // somewhere sensible rather than collapsing to zero.
        CaptionFrameGeometry.Rect r =
                CaptionFrameGeometry.fittedVideoRect(1080f, 2400f, 0, 0);

        assertEquals(0f, r.left, EPS);
        assertEquals(0f, r.top, EPS);
        assertEquals(1080f, r.width, EPS);
        assertEquals(2400f, r.height, EPS);
    }

    @Test
    public void degenerateViewSizeDoesNotProduceNaNOrZero() {
        CaptionFrameGeometry.Rect r =
                CaptionFrameGeometry.fittedVideoRect(0f, 0f, 1920, 1080);

        assertTrue(r.width > 0f);
        assertTrue(r.height > 0f);
        assertTrue(!Float.isNaN(r.width));
        assertTrue(!Float.isNaN(r.height));
    }

    @Test
    public void scaleIsViewPixelsPerVideoPixel() {
        CaptionFrameGeometry.Rect r =
                CaptionFrameGeometry.fittedVideoRect(1080f, 2400f, 1920, 1080);

        // 607.5 view px of picture height for 1080 video px.
        assertEquals(607.5f / 1080f, r.scaleY(1080), 0.0001f);
        assertEquals(1080f / 1920f, r.scaleX(1920), 0.0001f);
    }

    @Test
    public void fractionConversionRoundTrips() {
        // The preview converts a video fraction to view pixels; the exporter
        // converts it straight to video pixels. Doing one then undoing it must
        // land back on the same fraction, which is the property that makes the
        // two agree.
        CaptionFrameGeometry.Rect r =
                CaptionFrameGeometry.fittedVideoRect(1080f, 2400f, 1920, 1080);

        float yFraction = 0.85f;
        float viewY = CaptionFrameGeometry.videoFractionYToView(r, yFraction);
        float backToFraction = (viewY - r.top) / r.height;
        assertEquals(yFraction, backToFraction, 0.0001f);

        float xFraction = 0.5f;
        float viewX = CaptionFrameGeometry.videoFractionXToView(r, xFraction);
        assertEquals(xFraction, (viewX - r.left) / r.width, 0.0001f);
    }
}

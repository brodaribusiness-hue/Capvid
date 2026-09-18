package com.saad.capvid.caption;

/**
 * Where the video actually sits inside the preview view, and how to convert
 * between view pixels and video pixels.
 *
 * <h3>Why this exists</h3>
 * {@code activity_preview.xml} puts the {@code VideoView} and the
 * {@code CaptionOverlayView} side by side as {@code match_parent} siblings, so
 * the overlay covers the whole screen while {@code VideoView} letterboxes the
 * video into an aspect-fitted rect in the middle of it. On a portrait phone
 * playing a landscape clip that rect can be a band occupying the middle third
 * of the screen.
 *
 * The caption used to be positioned as {@code posYFraction * overlayHeight} -
 * a fraction of the <i>screen</i> - while the exporter wrote
 * {@code posYFraction * videoHeight}, a fraction of the <i>video</i>. Those are
 * different places: with the caption at 0.85 of a 2400 px screen it sat in the
 * black bar below a video that only spanned y=720..1680, but the burn-in put it
 * at 0.85 of 1080, i.e. inside the picture. The exported caption was therefore
 * in a different position than the one on screen, and because the font size was
 * scaled by {@code videoHeight / overlayHeight} rather than by the displayed
 * video height it also came out several times too small.
 *
 * <h3>The rule now</h3>
 * {@code posXFraction}/{@code posYFraction} are fractions of the <b>video
 * frame</b>, in both places. The preview converts them to view pixels through
 * {@link #fittedVideoRect}; the exporter multiplies them straight into the
 * video's pixel dimensions. Same input, same meaning, no way to drift.
 *
 * <p>Pure Java and free of Android imports so it can be unit tested on the JVM.
 */
public final class CaptionFrameGeometry {

    /** An axis-aligned rect in view pixels. */
    public static final class Rect {
        public final float left;
        public final float top;
        public final float width;
        public final float height;

        public Rect(float left, float top, float width, float height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        /** View pixels per video pixel, horizontally. */
        public float scaleX(int videoWidth) {
            return videoWidth > 0 ? width / videoWidth : 0f;
        }

        /** View pixels per video pixel, vertically. */
        public float scaleY(int videoHeight) {
            return videoHeight > 0 ? height / videoHeight : 0f;
        }
    }

    private CaptionFrameGeometry() {
    }

    /**
     * The aspect-fitted, centred rect a video of {@code videoWidth x videoHeight}
     * occupies inside a view of {@code viewWidth x viewHeight}. This is the same
     * fit {@code VideoView} performs: scale uniformly until one axis fills, then
     * centre on the other.
     *
     * <p>When the video dimensions are not known yet (before the player has
     * prepared) the whole view is returned, which degrades to the old behaviour
     * rather than to nothing.
     */
    public static Rect fittedVideoRect(float viewWidth, float viewHeight,
                                       int videoWidth, int videoHeight) {
        if (viewWidth <= 1f || viewHeight <= 1f) {
            return new Rect(0f, 0f, Math.max(1f, viewWidth), Math.max(1f, viewHeight));
        }
        if (videoWidth <= 0 || videoHeight <= 0) {
            return new Rect(0f, 0f, viewWidth, viewHeight);
        }
        float videoAspect = (float) videoWidth / (float) videoHeight;
        float viewAspect = viewWidth / viewHeight;

        float w;
        float h;
        if (videoAspect > viewAspect) {
            // Video is wider than the view: width fills, height letterboxes.
            w = viewWidth;
            h = viewWidth / videoAspect;
        } else {
            // Video is taller: height fills, width pillarboxes.
            h = viewHeight;
            w = viewHeight * videoAspect;
        }
        return new Rect((viewWidth - w) / 2f, (viewHeight - h) / 2f, w, h);
    }

    /**
     * A caption X expressed as a fraction of the video frame, in view pixels.
     */
    public static float videoFractionXToView(Rect videoRect, float fractionOfVideoWidth) {
        return videoRect.left + fractionOfVideoWidth * videoRect.width;
    }

    /**
     * A caption Y expressed as a fraction of the video frame, in view pixels.
     */
    public static float videoFractionYToView(Rect videoRect, float fractionOfVideoHeight) {
        return videoRect.top + fractionOfVideoHeight * videoRect.height;
    }
}

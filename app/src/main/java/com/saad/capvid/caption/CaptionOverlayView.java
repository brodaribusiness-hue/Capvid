package com.saad.capvid.caption;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.saad.capvid.font.FontManager;
import com.saad.capvid.font.StyleFontMap;
import com.saad.capvid.model.CaptionWord;
import com.saad.capvid.style.CaptionStyleOptions;
import com.saad.capvid.style.renderer.ModernCaptionRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CaptionOverlayView extends View {

    public enum CaptionStyleType {
        MINIMAL_FADE, KARAOKE_HIGHLIGHT, HIGHLIGHT_BOX_MARKER, BOUNCE_POP,
        WORD_POP_SCALE, GLOW_POP, COLOR_SPLASH, SHADOW_PULSE,
        UNDERLINE_DRAW, SLIDE_IN_CASCADE,
        TYPEWRITER, ZIGZAG_CALLIGRAPHY, SHAKE_WIGGLE_EMPHASIS, BLUR_TO_FOCUS,
        ROTATE_IN_3D_FLIP, GRADIENT_WAVE, STAMP_IMPACT,
        DEPTH_STACK_3D, CUBE_ROTATE_3D, TILT_PERSPECTIVE_3D,
        // --- new modern/advanced styles ---
        NEON_OUTLINE_GLOW, LIQUID_GRADIENT_SWEEP, GLASSMORPHISM_CARD,
        CHROME_METALLIC, DUOTONE_SPLIT, CONFETTI_POP,
        MARKER_HIGHLIGHT_ROTATE, COMIC_BOUNCE_OUTLINE,
        CINEMATIC_LETTERBOX, SPLIT_REVEAL_SCAN,
        // --- Featured tab additions (12) ---
        BACKGROUND_CARD, GRADIENT_TEXT, SOFT_SHADOW_RIGHT, ROUNDED_PILL_HIGHLIGHT,
        OUTLINE_STROKE, DUAL_TONE, SUBTITLE_BAR, BOX_GLOW_COMBO,
        LINE_UNDERLINE_SWEEP, SOFT_CARD_SHADOW, CORNER_ROUNDED_HIGHLIGHT, CLEAN_CAPS,
        // --- Viral tab additions (18) ---
        EMOJI_POP_ACCENT, FLASH_CUT, RAINBOW_CYCLE, PUNCH_IN, STICKER_POP,
        BOLD_DROP_SHADOW_BOUNCE, COMIC_POP, SPEED_RAMP_TEXT, GLITCH_FLICKER,
        FIRE_HIGHLIGHT, ICE_HIGHLIGHT, PULSE_BEAT, MEGA_BOLD_CAPS, WAVY_BASELINE,
        SINGLE_WORD_FLASH, NEON_PULSE_TEXT, HYPE_BOUNCE_GLOW, TURBO_SHAKE_POP
    }

    private static final int WORDS_PER_LINE = 4;
    private static final float WORD_SPACING = 22f;

    private List<CaptionWord> words;
    private List<List<CaptionWord>> lineGroups = new ArrayList<>();
    private long currentTimeMs = 0;
    private CaptionStyleType styleType = CaptionStyleType.MINIMAL_FADE;
    private CaptionStyleOptions options = new CaptionStyleOptions();

    private float posXFraction = 0.5f;
    private float posYFraction = 0.85f;
    private float textSizeSp = 20f;

    private boolean bold = false;
    private boolean italic = false;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeWordBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Camera camera = new Camera();
    private final Typeface defaultTypeface;

    private float dragStartX, dragStartY;
    private boolean dragging = false;

    public CaptionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        bgPaint.setColor(Color.BLACK);
        bgPaint.setAlpha(160);
        defaultTypeface = Typeface.DEFAULT;
        textPaint.setTypeface(defaultTypeface);
        setTextSizeSp(textSizeSp);
    }

    public void setWords(List<CaptionWord> words) { this.words = words; regroupLines(); invalidate(); }
    public void setCurrentTimeMs(long timeMs) { this.currentTimeMs = timeMs; invalidate(); }
    public void setStyleType(CaptionStyleType type) { this.styleType = type; invalidate(); }
    public CaptionStyleType getStyleType() { return styleType; }

    public void setStyleOptions(CaptionStyleOptions o) { this.options = o; regroupLines(); invalidate(); }
    public CaptionStyleOptions getStyleOptions() { return options; }

    public void setBold(boolean bold) { this.bold = bold; invalidate(); }
    public void setItalic(boolean italic) { this.italic = italic; invalidate(); }
    public boolean getBold() { return bold; }
    public boolean getItalic() { return italic; }

    public void setTextSizeSp(float sp) {
        this.textSizeSp = sp;
        float density = getResources().getDisplayMetrics().scaledDensity;
        textPaint.setTextSize(sp * density);
        invalidate();
    }

    public float getTextSizeSp() { return textSizeSp; }
    public float getPosXFraction() { return posXFraction; }
    public float getPosYFraction() { return posYFraction; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int activeIndex = findActiveWordIndex();
        if (activeIndex == -1) return;

        float progress = getProgress(words.get(activeIndex));
        float cx = posXFraction * getWidth();
        float cy = posYFraction * getHeight();

        if (styleType == CaptionStyleType.ZIGZAG_CALLIGRAPHY) {
            drawZigzagCalligraphy(canvas, activeIndex, cx, cy);
            return;
        }

        if (lineGroups.isEmpty()) regroupLines();

        int activeLine = lineIndexOf(activeIndex);
        if (activeLine == -1) activeLine = 0;

        int pages = Math.max(1, Math.min(4, options.pageBreakLines));
        int windowStart = activeLine - (pages - 1) / 2;
        windowStart = Math.max(0, Math.min(windowStart, Math.max(0, lineGroups.size() - pages)));
        int windowEnd = Math.min(lineGroups.size(), windowStart + pages);

        applyStyleFont();
        float lineHeight = textPaint.getTextSize() + options.lineSpacingPx;
        resetFont();

        // Caption background spans the whole visible block (all pageBreakLines)
        if (options.captionBgOn) {
            drawCaptionBackground(canvas, cx, cy, windowStart, windowEnd, lineHeight);
        }

        for (int li = windowStart; li < windowEnd; li++) {
            float lineY = cy + (li - activeLine) * lineHeight;
            drawLine(canvas, lineGroups.get(li), activeIndex, cx, lineY, progress);
        }
    }

    private void drawLine(Canvas canvas, List<CaptionWord> line, int activeIndex, float cx, float cy, float progress) {
        float totalWidth = 0f;
        for (int i = 0; i < line.size(); i++) {
            totalWidth += textPaint.measureText(displayText(line.get(i)));
            if (i < line.size() - 1) totalWidth += options.wordSpacingPx;
        }

        float x;
        switch (options.alignment) {
            case LEFT: x = getWidth() * 0.06f; break;
            case RIGHT: x = getWidth() * 0.94f - totalWidth; break;
            case CENTER:
            default: x = cx - totalWidth / 2f; break;
        }

        for (CaptionWord w : line) {
            String text = displayText(w);
            float wWidth = textPaint.measureText(text);
            float wordCenterX = x + wWidth / 2f;
            boolean isActive = words.indexOf(w) == activeIndex;

            if (isActive) {
                applyStyleFont();
                applyShadow(textPaint);
                if (options.activeWordBgOn) {
                    drawActiveWordBackground(canvas, text, wordCenterX, cy);
                }
                drawActiveStyle(canvas, wordAsDisplayed(w, text), wordCenterX, cy, progress);
                if (options.strokeOn) {
                    drawStrokePass(canvas, text, wordCenterX, cy);
                }
                textPaint.clearShadowLayer();
                resetFont();
            } else {
                Paint contextPaint = new Paint(textPaint);
                contextPaint.setColor(Color.argb(190, 255, 255, 255));
                contextPaint.clearShadowLayer();
                contextPaint.setMaskFilter(null);
                contextPaint.setShader(null);
                applyShadow(contextPaint);
                canvas.drawText(text, wordCenterX, cy, contextPaint);
                if (options.strokeOn) {
                    drawStrokePass(canvas, text, wordCenterX, cy);
                }
            }

            x += wWidth + options.wordSpacingPx;
        }
    }

    private String displayText(CaptionWord w) {
        return options.applyCapitalization(w.text);
    }

    /** A lightweight display-only copy carrying the capitalized text, so the
     *  existing drawXxx()/ModernCaptionRenderer methods (which read w.text)
     *  show capitalized text without mutating the real transcript data. */
    private CaptionWord wordAsDisplayed(CaptionWord original, String displayText) {
        if (displayText.equals(original.text)) return original;
        return new CaptionWord(displayText, original.startMs, original.endMs);
    }

    private void applyShadow(Paint p) {
        if (!options.shadowOn) return;
        float dx = options.shadowDirection == CaptionStyleOptions.ShadowDirection.RIGHT ? options.shadowOffsetPx : 0f;
        float dy = options.shadowDirection == CaptionStyleOptions.ShadowDirection.DOWN ? options.shadowOffsetPx : 0f;
        p.setShadowLayer(6f, dx, dy, options.shadowColor);
    }

    private void drawStrokePass(Canvas canvas, String text, float cx, float cy) {
        strokePaint.set(textPaint);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(options.strokeWidthPx);
        strokePaint.setColor(options.strokeColor);
        strokePaint.setShader(null);
        strokePaint.clearShadowLayer();
        canvas.drawText(text, cx, cy, strokePaint);
    }

    private void drawActiveWordBackground(Canvas canvas, String text, float cx, float cy) {
        float w = textPaint.measureText(text);
        float pad = 12f;
        android.graphics.RectF box = new android.graphics.RectF(
                cx - w / 2f - pad, cy - textPaint.getTextSize() - pad / 2f, cx + w / 2f + pad, cy + pad / 2f);
        activeWordBgPaint.setColor(options.activeWordBgColor);
        canvas.drawRoundRect(box, options.activeWordBgCornerRadiusPx, options.activeWordBgCornerRadiusPx, activeWordBgPaint);
    }

    private void drawCaptionBackground(Canvas canvas, float cx, float cy, int windowStart, int windowEnd, float lineHeight) {
        float maxWidth = 0f;
        for (int li = windowStart; li < windowEnd; li++) {
            float w = 0f;
            List<CaptionWord> line = lineGroups.get(li);
            for (int i = 0; i < line.size(); i++) {
                w += textPaint.measureText(displayText(line.get(i)));
                if (i < line.size() - 1) w += options.wordSpacingPx;
            }
            maxWidth = Math.max(maxWidth, w);
        }
        float pad = 20f;
        float top = cy - textPaint.getTextSize() - pad;
        float bottom = cy + ((windowEnd - windowStart) - 1) * lineHeight + pad;
        android.graphics.RectF box = new android.graphics.RectF(
                cx - maxWidth / 2f - pad, top, cx + maxWidth / 2f + pad, bottom);
        captionBgPaint.setColor(options.captionBgColor);
        canvas.drawRoundRect(box, 12f, 12f, captionBgPaint);
    }

    /**
     * Splits the flat word list into display lines according to
     * options.lineBreakMode. Called whenever words or lineBreakMode change.
     * PUNCTUATION: a line ends at a word ending in . ! ? , (or WORDS_PER_LINE
     * words, whichever comes first) — same visual cadence as the original
     * fixed 4-words-per-line behaviour, but break-aware.
     * SINGLE_WORD: one word per line.
     * RANDOM: pseudo-random 2-5 word groups (stable seed so it doesn't
     * re-shuffle every recompose).
     */
    private void regroupLines() {
        lineGroups = new ArrayList<>();
        if (words == null || words.isEmpty()) return;

        switch (options.lineBreakMode) {
            case SINGLE_WORD:
                for (CaptionWord w : words) lineGroups.add(Collections.singletonList(w));
                break;

            case RANDOM: {
                Random rnd = new Random(42);
                int i = 0;
                while (i < words.size()) {
                    int len = 2 + rnd.nextInt(4);
                    int end = Math.min(words.size(), i + len);
                    lineGroups.add(new ArrayList<>(words.subList(i, end)));
                    i = end;
                }
                break;
            }

            case PUNCTUATION:
            default: {
                List<CaptionWord> current = new ArrayList<>();
                for (CaptionWord w : words) {
                    current.add(w);
                    String t = w.text == null ? "" : w.text.trim();
                    boolean endsPunctuation = t.matches(".*[.!?,]$");
                    if (endsPunctuation || current.size() >= WORDS_PER_LINE) {
                        lineGroups.add(current);
                        current = new ArrayList<>();
                    }
                }
                if (!current.isEmpty()) lineGroups.add(current);
                break;
            }
        }
    }

    private int lineIndexOf(int wordIndex) {
        if (wordIndex < 0 || wordIndex >= words.size()) return -1;
        CaptionWord target = words.get(wordIndex);
        for (int li = 0; li < lineGroups.size(); li++) {
            if (lineGroups.get(li).contains(target)) return li;
        }
        return -1;
    }

    private void applyStyleFont() {
        String asset = options.fontAssetOverride != null
                ? options.fontAssetOverride
                : StyleFontMap.get(styleType).assetFileName;
        textPaint.setTypeface(FontManager.get(getContext(), asset));
        textPaint.setFakeBoldText(bold);
        textPaint.setTextSkewX(italic ? -0.25f : 0f);
    }

    private void resetFont() {
        textPaint.setTypeface(defaultTypeface);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSkewX(0f);
    }

    private void drawActiveStyle(Canvas canvas, CaptionWord w, float x, float y, float progress) {
        switch (styleType) {
            case MINIMAL_FADE: drawMinimalFade(canvas, w, x, y, progress); break;
            case KARAOKE_HIGHLIGHT: drawKaraokeHighlight(canvas, w, x, y, progress); break;
            case HIGHLIGHT_BOX_MARKER: drawHighlightBoxMarker(canvas, w, x, y); break;
            case BOUNCE_POP: drawBouncePop(canvas, w, x, y, progress); break;
            case WORD_POP_SCALE: drawWordPopScale(canvas, w, x, y, progress); break;
            case GLOW_POP: drawGlowPop(canvas, w, x, y); break;
            case COLOR_SPLASH: drawColorSplash(canvas, w, x, y); break;
            case SHADOW_PULSE: drawShadowPulse(canvas, w, x, y, progress); break;
            case UNDERLINE_DRAW: drawUnderlineDraw(canvas, w, x, y, progress); break;
            case SLIDE_IN_CASCADE: drawSlideInCascade(canvas, w, x, y, progress); break;
            case TYPEWRITER: drawTypewriter(canvas, w, x, y, progress); break;
            case SHAKE_WIGGLE_EMPHASIS: drawShakeWiggle(canvas, w, x, y, progress); break;
            case BLUR_TO_FOCUS: drawBlurToFocus(canvas, w, x, y, progress); break;
            case ROTATE_IN_3D_FLIP: drawRotateIn3DFlip(canvas, w, x, y, progress); break;
            case GRADIENT_WAVE: drawGradientWave(canvas, w, x, y, progress); break;
            case STAMP_IMPACT: drawStampImpact(canvas, w, x, y, progress); break;
            case DEPTH_STACK_3D: drawDepthStack3D(canvas, w, x, y); break;
            case CUBE_ROTATE_3D: drawCubeRotate3D(canvas, w, x, y, progress); break;
            case TILT_PERSPECTIVE_3D: drawTiltPerspective3D(canvas, w, x, y, progress); break;
            // All remaining ids (the 10 original "modern" styles + every new
            // Featured/Viral catalog template) are rendered generically from
            // their CaptionStyleDefinition.treatment — no per-id case needed,
            // so new catalog entries never require touching this switch again.
            default:
                drawByTreatmentLookup(canvas, w, x, y, progress);
                break;
        }
    }

    /**
     * Generic fallback used by every catalog id that has no hand-written
     * drawXxx() above: looks up its CaptionStyleDefinition (for color
     * swatches + font) and renders purely from def.treatment. This is how
     * all Featured/Viral catalog templates beyond the original 20 render
     * live in the preview — adding a new template to CaptionStyleCatalog
     * never requires editing this file again.
     */
    private void drawByTreatmentLookup(Canvas canvas, CaptionWord w, float x, float y, float progress) {
        com.saad.capvid.style.CaptionStyleDefinition def =
                com.saad.capvid.style.CaptionStyleCatalog.byId(styleType.name());
        if (def == null) {
            canvas.drawText(w.text, x, y, textPaint);
            return;
        }
        ModernCaptionRenderer.drawByTreatment(def.treatment, canvas, textPaint, w, x, y, progress, def.swatchColors);
    }

    private int findActiveWordIndex() {
        if (words == null || words.isEmpty()) return -1;
        if (currentTimeMs < words.get(0).startMs) return -1;
        // A word stays "active" from its own start until the NEXT word's start
        // (not just until its own endMs). Whisper timestamps almost always leave
        // a small gap between words, and the old exact-window check meant no
        // caption was drawn at all during every such gap. The last word gets a
        // short trailing buffer instead of a "next start" to fall back on.
        for (int i = 0; i < words.size(); i++) {
            CaptionWord w = words.get(i);
            long segmentEnd = (i < words.size() - 1) ? words.get(i + 1).startMs : w.endMs + 400;
            if (currentTimeMs >= w.startMs && currentTimeMs < segmentEnd) return i;
        }
        return words.size() - 1;
    }

    private float getProgress(CaptionWord w) {
        long duration = Math.max(1, w.endMs - w.startMs);
        float p = (currentTimeMs - w.startMs) / (float) duration;
        return Math.max(0f, Math.min(1f, p));
    }

    private void drawMinimalFade(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        textPaint.setAlpha((int) (255 * Math.min(1f, progress * 4f)));
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.setAlpha(255);
    }

    private void drawKaraokeHighlight(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float textWidth = textPaint.measureText(w.text);
        float left = cx - textWidth / 2f;
        textPaint.setColor(Color.GRAY);
        canvas.drawText(w.text, cx, cy, textPaint);
        canvas.save();
        canvas.clipRect(left, cy - textPaint.getTextSize(), left + textWidth * progress, cy + 10);
        textPaint.setColor(Color.YELLOW);
        canvas.drawText(w.text, cx, cy, textPaint);
        canvas.restore();
        textPaint.setColor(Color.WHITE);
    }

    private void drawHighlightBoxMarker(Canvas canvas, CaptionWord w, float cx, float cy) {
        float textWidth = textPaint.measureText(w.text);
        float padding = 14f;

        Paint boxPaint = new Paint(bgPaint);
        boxPaint.setColor(Color.rgb(45, 0, 70));
        boxPaint.setAlpha(230);
        canvas.drawRect(cx - textWidth / 2f - padding, cy - textPaint.getTextSize(),
                cx + textWidth / 2f + padding, cy + padding, boxPaint);

        textPaint.setColor(Color.rgb(120, 100, 0));
        canvas.drawText(w.text, cx + 3f, cy + 3f, textPaint);
        canvas.drawText(w.text, cx + 1.5f, cy + 1.5f, textPaint);

        textPaint.setColor(Color.YELLOW);
        textPaint.setShadowLayer(10f, 0, 0, Color.rgb(255, 230, 0));
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.clearShadowLayer();
        textPaint.setColor(Color.WHITE);
    }

    private void drawBouncePop(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float scale = progress < 0.5f ? 1f + 0.4f * (progress / 0.5f) : 1.4f - 0.4f * ((progress - 0.5f) / 0.5f);
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        textPaint.setColor(Color.RED);
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.setColor(Color.WHITE);
        canvas.restore();
    }

    private void drawWordPopScale(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float scale = 1f + 0.15f * (float) Math.sin(progress * Math.PI);
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        canvas.drawText(w.text, cx, cy, textPaint);
        canvas.restore();
    }

    private void drawGlowPop(Canvas canvas, CaptionWord w, float cx, float cy) {
        textPaint.setShadowLayer(20f, 0, 0, Color.CYAN);
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.clearShadowLayer();
    }

    private void drawColorSplash(Canvas canvas, CaptionWord w, float cx, float cy) {
        int[] colors = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.MAGENTA};
        textPaint.setColor(colors[Math.abs(w.text.hashCode()) % colors.length]);
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.setColor(Color.WHITE);
    }

    private void drawShadowPulse(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float radius = 10f + 15f * (float) Math.abs(Math.sin(progress * Math.PI));
        textPaint.setColor(Color.rgb(255, 215, 0));
        textPaint.setShadowLayer(radius, 0, 0, Color.BLACK);
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.clearShadowLayer();
        textPaint.setColor(Color.WHITE);
    }

    private void drawUnderlineDraw(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        textPaint.setColor(Color.rgb(0, 191, 255));
        canvas.drawText(w.text, cx, cy, textPaint);
        float textWidth = textPaint.measureText(w.text);
        float left = cx - textWidth / 2f;
        canvas.drawLine(left, cy + 8f, left + textWidth * progress, cy + 8f, textPaint);
        textPaint.setColor(Color.WHITE);
    }

    private void drawSlideInCascade(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float slideOffset = (1f - Math.min(1f, progress * 3f)) * 200f;
        textPaint.setColor(Color.rgb(0, 150, 60));
        canvas.drawText(w.text, cx - slideOffset, cy, textPaint);
        textPaint.setColor(Color.WHITE);
    }

    private void drawTypewriter(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        int charsToShow = Math.max(1, (int) Math.ceil(progress * w.text.length()));
        String visibleText = w.text.substring(0, Math.min(charsToShow, w.text.length()));
        boolean cursorOn = (currentTimeMs / 300) % 2 == 0;
        String display = visibleText + (cursorOn ? "|" : "");

        float fullWidth = textPaint.measureText(w.text);
        float startX = cx - fullWidth / 2f;

        Paint.Align originalAlign = textPaint.getTextAlign();
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(display, startX, cy, textPaint);
        textPaint.setTextAlign(originalAlign);
    }

    private void drawZigzagCalligraphy(Canvas canvas, int activeIndex, float cx, float cy) {
        String prevText = activeIndex > 0 ? words.get(activeIndex - 1).text : "";
        String activeText = words.get(activeIndex).text;
        String nextText = activeIndex < words.size() - 1 ? words.get(activeIndex + 1).text : "";

        float offsetX = 80f;
        float offsetY = textPaint.getTextSize() * 1.2f;
        float originalSize = textPaint.getTextSize();

        textPaint.setTextSize(originalSize * 0.75f);
        canvas.drawText(prevText, cx - offsetX, cy - offsetY, textPaint);
        canvas.drawText(nextText, cx + offsetX, cy + offsetY, textPaint);
        textPaint.setTextSize(originalSize);

        textPaint.setTypeface(FontManager.get(getContext(), "Calligrapher-JRxaE.ttf"));
        textPaint.setFakeBoldText(bold);
        textPaint.setTextSkewX(italic ? -0.25f : 0f);
        canvas.drawText(activeText, cx, cy, textPaint);
        resetFont();
    }

    private void drawShakeWiggle(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float shakeAmount = 6f * (float) Math.sin(progress * Math.PI * 8);
        canvas.drawText(w.text, cx + shakeAmount, cy, textPaint);
    }

    private void drawBlurToFocus(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float blurRadius = Math.max(0.01f, 15f * (1f - progress));
        textPaint.setColor(Color.rgb(255, 110, 199));
        textPaint.setMaskFilter(new BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL));
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.setMaskFilter(null);
        textPaint.setColor(Color.WHITE);
    }

    private void drawRotateIn3DFlip(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float angle = 180f * (1f - Math.min(1f, progress * 2f));
        camera.save();
        camera.rotateY(angle);
        Matrix matrix = new Matrix();
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-cx, -cy);
        matrix.postTranslate(cx, cy);
        canvas.save();
        canvas.concat(matrix);
        textPaint.setColor(Color.rgb(26, 35, 126));
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.setColor(Color.WHITE);
        canvas.restore();
    }

    private void drawGradientWave(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float textWidth = textPaint.measureText(w.text);
        float left = cx - textWidth / 2f;
        float shift = progress * textWidth * 2f;
        LinearGradient gradient = new LinearGradient(
                left - shift, cy, left + textWidth - shift, cy,
                new int[]{Color.MAGENTA, Color.CYAN, Color.YELLOW},
                null, Shader.TileMode.MIRROR);
        textPaint.setShader(gradient);
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.setShader(null);
    }

    private void drawStampImpact(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float scale = progress < 0.15f ? 1.6f - (0.6f * (progress / 0.15f)) : 1f;
        int alpha = progress < 0.05f ? (int) (255 * (progress / 0.05f)) : 255;
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        textPaint.setColor(Color.rgb(176, 38, 255));
        textPaint.setAlpha(alpha);
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.setAlpha(255);
        textPaint.setColor(Color.WHITE);
        canvas.restore();
    }

    private void drawDepthStack3D(Canvas canvas, CaptionWord w, float cx, float cy) {
        int[] shades = {Color.rgb(120, 85, 0), Color.rgb(190, 140, 0), Color.rgb(230, 175, 0)};
        for (int i = shades.length - 1; i >= 0; i--) {
            textPaint.setColor(shades[i]);
            float offset = (i + 1) * 4f;
            canvas.drawText(w.text, cx + offset, cy + offset, textPaint);
        }
        textPaint.setColor(Color.rgb(255, 193, 7));
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.setColor(Color.WHITE);
    }

    private void drawCubeRotate3D(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float angle = 360f * progress;
        camera.save();
        camera.rotateY(angle % 360f);
        Matrix matrix = new Matrix();
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-cx, -cy);
        matrix.postTranslate(cx, cy);
        canvas.save();
        canvas.concat(matrix);
        canvas.drawText(w.text, cx, cy, textPaint);
        canvas.restore();
    }

    private void drawTiltPerspective3D(Canvas canvas, CaptionWord w, float cx, float cy, float progress) {
        float tilt = 25f * (1f - Math.min(1f, progress * 3f));
        camera.save();
        camera.rotateX(tilt);
        Matrix matrix = new Matrix();
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-cx, -cy);
        matrix.postTranslate(cx, cy);
        canvas.save();
        canvas.concat(matrix);
        textPaint.setColor(Color.rgb(207, 245, 255));
        canvas.drawText(w.text, cx, cy, textPaint);
        textPaint.setColor(Color.WHITE);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragStartX = event.getX(); dragStartY = event.getY(); dragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float dx = event.getX() - dragStartX;
                    float dy = event.getY() - dragStartY;
                    posXFraction = Math.max(0.1f, Math.min(0.9f, posXFraction + dx / getWidth()));
                    posYFraction = Math.max(0.1f, Math.min(0.9f, posYFraction + dy / getHeight()));
                    dragStartX = event.getX(); dragStartY = event.getY();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }
}

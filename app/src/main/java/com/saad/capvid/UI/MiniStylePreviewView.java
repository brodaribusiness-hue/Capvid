package com.saad.capvid.ui.template;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.saad.capvid.style.CaptionStyleDefinition;

/**
 * Draws a static single-frame preview of a caption style inside a template
 * card (matches the "on the live television" preview boxes in the picker).
 * This is intentionally simpler than the full CaptionOverlayView animation
 * engine — a picker card only needs to communicate look & feel at a glance.
 *
 * Typeface is set from outside via setTypeface() once FontManager has
 * loaded/cached the real font for this style's fontAsset.
 */
public class MiniStylePreviewView extends View {

    private CaptionStyleDefinition def;
    private String previewText = "on the live television";
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MiniStylePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        textPaint.setTextSize(dp(20));
        textPaint.setTextAlign(Paint.Align.CENTER);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(2.5f));
    }

    public void bind(CaptionStyleDefinition def, String previewText) {
        this.def = def;
        this.previewText = previewText;
        invalidate();
    }

    public void setTypeface(Typeface tf) {
        textPaint.setTypeface(tf);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (def == null) return;

        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        int[] c = def.swatchColors;

        switch (def.treatment) {
            case BOX_SOLID: {
                textPaint.setColor(c[1]);
                float textW = textPaint.measureText(previewText);
                RectF box = new RectF(cx - textW / 2 - dp(16), cy - dp(22), cx + textW / 2 + dp(16), cy + dp(22));
                boxPaint.setColor(c[0]);
                canvas.drawRoundRect(box, dp(10), dp(10), boxPaint);
                canvas.drawText(previewText, cx, cy + dp(7), textPaint);
                break;
            }
            case BOX_TRANSLUCENT: {
                textPaint.setColor(c[3]);
                float textW = textPaint.measureText(previewText);
                RectF box = new RectF(cx - textW / 2 - dp(16), cy - dp(22), cx + textW / 2 + dp(16), cy + dp(22));
                boxPaint.setColor(Color.argb(235, Color.red(c[0]), Color.green(c[0]), Color.blue(c[0])));
                canvas.drawRoundRect(box, dp(10), dp(10), boxPaint);
                canvas.drawText(previewText, cx, cy + dp(7), textPaint);
                break;
            }
            case OUTLINE_GLOW: {
                outlinePaint.setColor(c[0]);
                outlinePaint.setMaskFilter(new android.graphics.BlurMaskFilter(dp(6), android.graphics.BlurMaskFilter.Blur.NORMAL));
                canvas.drawText(previewText, cx, cy + dp(7), outlinePaint);
                outlinePaint.setMaskFilter(null);
                textPaint.setColor(Color.WHITE);
                canvas.drawText(previewText, cx, cy + dp(7), textPaint);
                break;
            }
            case GRADIENT_FILL: {
                float textW = textPaint.measureText(previewText);
                Shader shader = new LinearGradient(cx - textW / 2, 0, cx + textW / 2, 0,
                        c[0], c[1], Shader.TileMode.CLAMP);
                textPaint.setShader(shader);
                canvas.drawText(previewText, cx, cy + dp(7), textPaint);
                textPaint.setShader(null);
                break;
            }
            case CHROME: {
                Shader shader = new LinearGradient(0, cy - dp(15), 0, cy + dp(15),
                        new int[]{c[0], c[1], c[2]}, null, Shader.TileMode.CLAMP);
                textPaint.setShader(shader);
                outlinePaint.setColor(c[3]);
                canvas.drawText(previewText, cx, cy + dp(7), outlinePaint);
                canvas.drawText(previewText, cx, cy + dp(7), textPaint);
                textPaint.setShader(null);
                break;
            }
            case SPLIT_HALF: {
                String[] words = previewText.split(" ");
                String firstHalf = String.join(" ", java.util.Arrays.copyOfRange(words, 0, words.length / 2));
                String secondHalf = String.join(" ", java.util.Arrays.copyOfRange(words, words.length / 2, words.length));
                textPaint.setColor(c[1]);
                canvas.drawText(firstHalf, cx, cy - dp(2), textPaint);
                float secondW = textPaint.measureText(secondHalf);
                RectF box = new RectF(cx - secondW / 2 - dp(10), cy + dp(10), cx + secondW / 2 + dp(10), cy + dp(38));
                Shader shader = new LinearGradient(box.left, 0, box.right, 0, c[2], c[3], Shader.TileMode.CLAMP);
                boxPaint.setShader(shader);
                canvas.drawRoundRect(box, dp(6), dp(6), boxPaint);
                boxPaint.setShader(null);
                textPaint.setColor(Color.WHITE);
                canvas.drawText(secondHalf, cx, cy + dp(31), textPaint);
                break;
            }
            case ROTATED_MARKER: {
                canvas.save();
                canvas.rotate(-6, cx, cy);
                textPaint.setColor(c[1]);
                float textW = textPaint.measureText(previewText);
                RectF box = new RectF(cx - textW / 2 - dp(14), cy - dp(20), cx + textW / 2 + dp(14), cy + dp(20));
                boxPaint.setColor(c[3]);
                canvas.drawRoundRect(box, dp(20), dp(20), boxPaint);
                canvas.drawText(previewText, cx, cy + dp(7), textPaint);
                canvas.restore();
                break;
            }
            case COMIC_OUTLINE: {
                outlinePaint.setColor(c[1]);
                outlinePaint.setStrokeWidth(dp(4));
                canvas.drawText(previewText, cx, cy + dp(7), outlinePaint);
                textPaint.setColor(c[0]);
                canvas.drawText(previewText, cx, cy + dp(7), textPaint);
                break;
            }
            case SERIF_ITALIC: {
                textPaint.setColor(c[0]);
                Typeface base = textPaint.getTypeface() != null ? textPaint.getTypeface() : Typeface.SERIF;
                textPaint.setTypeface(Typeface.create(base, Typeface.ITALIC));
                canvas.drawText(previewText, cx, cy + dp(7), textPaint);
                break;
            }
            case PLAIN:
            default: {
                textPaint.setColor(c[1] != 0 ? c[1] : c[0]);
                canvas.drawText(previewText, cx, cy + dp(7), textPaint);
                break;
            }
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}

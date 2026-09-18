package com.saad.capvid.style.renderer;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.saad.capvid.model.CaptionWord;
import com.saad.capvid.style.CaptionStyleDefinition;

/**
 * Draws the active word for every catalog template that isn't one of the
 * original 20 hand-written legacy styles — called from
 * CaptionOverlayView.drawByTreatmentLookup() the same way the legacy
 * draw___ methods are — mutates the shared textPaint and MUST leave its
 * color back on Color.WHITE before returning (matching the existing
 * convention).
 *
 * Dispatch is keyed on CaptionStyleDefinition.PreviewTreatment (10 values)
 * instead of the specific style id, so any number of Featured/Viral catalog
 * templates can share one of these 10 visual treatments — new templates
 * added to CaptionStyleCatalog never require a new case here. This mirrors
 * the same treatment-driven approach MiniStylePreviewView already uses for
 * static card previews.
 */
public class ModernCaptionRenderer {

    public static void drawByTreatment(CaptionStyleDefinition.PreviewTreatment treatment, Canvas canvas,
                                        Paint textPaint, CaptionWord w, float cx, float cy, float progress,
                                        int[] swatch) {

        switch (treatment) {
            case OUTLINE_GLOW: {
                Paint glow = new Paint(textPaint);
                glow.setStyle(Paint.Style.STROKE);
                glow.setStrokeWidth(6f);
                glow.setColor(swatch[0]);
                glow.setMaskFilter(new BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL));
                canvas.drawText(w.text, cx, cy, glow);
                textPaint.setColor(Color.WHITE);
                canvas.drawText(w.text, cx, cy, textPaint);
                break;
            }

            case GRADIENT_FILL: {
                float textWidth = textPaint.measureText(w.text);
                float sweep = textWidth * (0.4f + 0.6f * progress);
                Shader shader = new LinearGradient(cx - sweep, 0, cx + sweep, 0,
                        new int[]{swatch[0], swatch[1], swatch[2]}, null, Shader.TileMode.CLAMP);
                textPaint.setShader(shader);
                canvas.drawText(w.text, cx, cy, textPaint);
                textPaint.setShader(null);
                textPaint.setColor(Color.WHITE);
                break;
            }

            case BOX_TRANSLUCENT: {
                float textWidth = textPaint.measureText(w.text);
                RectF box = new RectF(cx - textWidth / 2f - 14f, cy - textPaint.getTextSize() - 6f,
                        cx + textWidth / 2f + 14f, cy + 10f);
                Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                boxPaint.setColor(Color.argb(235, Color.red(swatch[0]), Color.green(swatch[0]), Color.blue(swatch[0])));
                canvas.drawRoundRect(box, 10f, 10f, boxPaint);
                textPaint.setColor(swatch[3]);
                canvas.drawText(w.text, cx, cy, textPaint);
                textPaint.setColor(Color.WHITE);
                break;
            }

            case CHROME: {
                float textH = textPaint.getTextSize();
                Shader shader = new LinearGradient(0, cy - textH, 0, cy + 4f,
                        new int[]{swatch[0], swatch[1], swatch[2]}, null, Shader.TileMode.CLAMP);
                Paint outline = new Paint(textPaint);
                outline.setStyle(Paint.Style.STROKE);
                outline.setStrokeWidth(2.5f);
                outline.setColor(swatch[3]);
                canvas.drawText(w.text, cx, cy, outline);
                textPaint.setShader(shader);
                canvas.drawText(w.text, cx, cy, textPaint);
                textPaint.setShader(null);
                textPaint.setColor(Color.WHITE);
                break;
            }

            case SPLIT_HALF: {
                float textWidth = textPaint.measureText(w.text);
                RectF box = new RectF(cx - textWidth / 2f - 8f, cy - textPaint.getTextSize() - 4f,
                        cx - textWidth / 2f - 8f + (textWidth + 16f) * Math.min(1f, progress + 0.3f), cy + 6f);
                Paint boxPaint = new Paint();
                boxPaint.setShader(new LinearGradient(box.left, 0, box.right, 0, swatch[2], swatch[3], Shader.TileMode.CLAMP));
                canvas.drawRoundRect(box, 6f, 6f, boxPaint);
                textPaint.setColor(Color.WHITE);
                canvas.drawText(w.text, cx, cy, textPaint);
                break;
            }

            case ROTATED_MARKER: {
                canvas.save();
                canvas.rotate(-6, cx, cy);
                float textWidth = textPaint.measureText(w.text);
                RectF box = new RectF(cx - textWidth / 2f - 10f, cy - textPaint.getTextSize() - 6f,
                        cx + textWidth / 2f + 10f, cy + 8f);
                Paint boxPaint = new Paint();
                boxPaint.setColor(swatch[3]);
                canvas.drawRoundRect(box, 16f, 16f, boxPaint);
                textPaint.setColor(swatch[1]);
                canvas.drawText(w.text, cx, cy, textPaint);
                canvas.restore();
                textPaint.setColor(Color.WHITE);
                break;
            }

            case COMIC_OUTLINE: {
                float bounce = -4f * (float) Math.sin(progress * Math.PI);
                Paint outline = new Paint(textPaint);
                outline.setStyle(Paint.Style.STROKE);
                outline.setStrokeWidth(4.5f);
                outline.setColor(swatch[1]);
                canvas.drawText(w.text, cx, cy + bounce, outline);
                textPaint.setColor(swatch[0]);
                canvas.drawText(w.text, cx, cy + bounce, textPaint);
                textPaint.setColor(Color.WHITE);
                break;
            }

            case SERIF_ITALIC: {
                Typeface base = textPaint.getTypeface() != null ? textPaint.getTypeface() : Typeface.SERIF;
                Typeface original = textPaint.getTypeface();
                textPaint.setTypeface(Typeface.create(base, Typeface.ITALIC));
                textPaint.setColor(swatch[0]);
                canvas.drawText(w.text, cx, cy, textPaint);
                textPaint.setTypeface(original);
                textPaint.setColor(Color.WHITE);
                break;
            }

            case BOX_SOLID: {
                float textWidth = textPaint.measureText(w.text);
                RectF box = new RectF(cx - textWidth / 2f - 12f, cy - textPaint.getTextSize() - 6f,
                        cx + textWidth / 2f + 12f, cy + 8f);
                Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                boxPaint.setColor(swatch[0]);
                canvas.drawRoundRect(box, 8f, 8f, boxPaint);
                textPaint.setColor(swatch[1]);
                canvas.drawText(w.text, cx, cy, textPaint);
                textPaint.setColor(Color.WHITE);
                break;
            }

            case PLAIN:
            default: {
                float scale = 0.7f + 0.3f * Math.min(1f, progress * 3f);
                canvas.save();
                canvas.scale(scale, scale, cx, cy);
                textPaint.setColor(swatch[0]);
                canvas.drawText(w.text, cx, cy, textPaint);
                canvas.restore();
                textPaint.setColor(Color.WHITE);
                break;
            }
        }
    }
}

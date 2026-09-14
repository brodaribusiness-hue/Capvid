package com.saad.capvid.export;

import com.saad.capvid.model.CaptionWord;

import java.util.List;
import java.util.Locale;

public class AssSubtitleBuilder {

    public static String build(List<CaptionWord> words, int videoWidth, int videoHeight,
                                float posXFraction, float posYFraction, float fontSizePx,
                                String fontFamilyName, boolean bold, boolean italic) {

        StringBuilder sb = new StringBuilder();
        sb.append("[Script Info]\nScriptType: v4.00+\nPlayResX: ").append(videoWidth)
                .append("\nPlayResY: ").append(videoHeight).append("\n\n");

        sb.append("[V4+ Styles]\n");
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, OutlineColour, BackColour, Bold, Italic, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n");
        sb.append(String.format(Locale.US,
                "Style: Default,%s,%d,&H00FFFFFF,&H00000000,&H80000000,%d,%d,1,2,0,5,10,10,10,1\n\n",
                fontFamilyName, (int) fontSizePx, bold ? -1 : 0, italic ? -1 : 0));

        sb.append("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        int x = (int) (posXFraction * videoWidth);
        int y = (int) (posYFraction * videoHeight);

        for (CaptionWord w : words) {
            String text = String.format(Locale.US, "{\\pos(%d,%d)}%s", x, y, escape(w.text));
            sb.append(String.format(Locale.US, "Dialogue: 0,%s,%s,Default,,0,0,0,,%s\n",
                    formatTime(w.startMs), formatTime(w.endMs), text));
        }

        return sb.toString();
    }

    private static String formatTime(long ms) {
        long h = ms / 3600000, m = (ms % 3600000) / 60000, s = (ms % 60000) / 1000, cs = (ms % 1000) / 10;
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs);
    }

    private static String escape(String text) {
        return text.replace("{", "(").replace("}", ")");
    }
}

package com.saad.capvid.font;

import android.content.Context;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class FontManager {

    public static final String[] FONT_FILES = {
            "All-Genders-Regular-v4.otf",
            "Calligrapher-JRxaE.ttf",
            "ChauPhilomeneOne-Regular.ttf",
            "JavaCalligraphy-w1Pw6.ttf",
            "Jost-Black.ttf",
            "KhatijaCalligraphy-0Z5o.otf",
            "RobotoMono-Bold.ttf",
            "RobotoMono-Regular.ttf",
            "SoulDaisy.otf",
            "solid 3d.ttf"
    };

    private static final Map<String, Typeface> cache = new HashMap<>();

    public static Typeface get(Context context, String assetFileName) {
        if (cache.containsKey(assetFileName)) return cache.get(assetFileName);
        try {
            Typeface tf = Typeface.createFromAsset(context.getAssets(), "fonts/" + assetFileName);
            cache.put(assetFileName, tf);
            return tf;
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    public static File copyFontsToInternal(Context context) {
        File fontsDir = new File(context.getFilesDir(), "fonts");
        if (!fontsDir.exists()) fontsDir.mkdirs();

        for (String fileName : FONT_FILES) {
            File outFile = new File(fontsDir, fileName);
            if (outFile.exists() && outFile.length() > 0) continue;
            try (InputStream in = context.getAssets().open("fonts/" + fileName);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            } catch (Exception ignored) {
            }
        }

        return fontsDir;
    }
}

package com.saad.capvid.font;

import com.saad.capvid.caption.CaptionOverlayView.CaptionStyleType;

import java.util.HashMap;
import java.util.Map;

public class StyleFontMap {

    public static class FontInfo {
        public final String assetFileName;
        public final String assFamilyName;

        public FontInfo(String assetFileName, String assFamilyName) {
            this.assetFileName = assetFileName;
            this.assFamilyName = assFamilyName;
        }
    }

    private static final Map<CaptionStyleType, FontInfo> MAP = new HashMap<>();

    static {
        FontInfo defaultFont = new FontInfo("All-Genders-Regular-v4.otf", "All Genders");
        FontInfo typewriterFont = new FontInfo("RobotoMono-Bold.ttf", "Roboto Mono");
        FontInfo calligraphyFont = new FontInfo("Calligrapher-JRxaE.ttf", "Calligrapher");
        FontInfo markerFont = new FontInfo("Jost-Black.ttf", "Jost");
        FontInfo stampFont = new FontInfo("ChauPhilomeneOne-Regular.ttf", "Chau Philomene One");
        FontInfo bounceFont = new FontInfo("SoulDaisy.otf", "Soul Daisy");
        FontInfo solid3dFont = new FontInfo("solid 3d.ttf", "Solid 3D");

        MAP.put(CaptionStyleType.MINIMAL_FADE, defaultFont);
        MAP.put(CaptionStyleType.KARAOKE_HIGHLIGHT, defaultFont);
        MAP.put(CaptionStyleType.WORD_POP_SCALE, defaultFont);
        MAP.put(CaptionStyleType.GLOW_POP, defaultFont);
        MAP.put(CaptionStyleType.COLOR_SPLASH, defaultFont);
        MAP.put(CaptionStyleType.SHADOW_PULSE, defaultFont);
        MAP.put(CaptionStyleType.UNDERLINE_DRAW, defaultFont);
        MAP.put(CaptionStyleType.SLIDE_IN_CASCADE, defaultFont);
        MAP.put(CaptionStyleType.SHAKE_WIGGLE_EMPHASIS, defaultFont);
        MAP.put(CaptionStyleType.BLUR_TO_FOCUS, defaultFont);
        MAP.put(CaptionStyleType.GRADIENT_WAVE, defaultFont);

        MAP.put(CaptionStyleType.TYPEWRITER, typewriterFont);
        MAP.put(CaptionStyleType.ZIGZAG_CALLIGRAPHY, calligraphyFont);
        MAP.put(CaptionStyleType.HIGHLIGHT_BOX_MARKER, markerFont);
        MAP.put(CaptionStyleType.STAMP_IMPACT, stampFont);
        MAP.put(CaptionStyleType.BOUNCE_POP, bounceFont);

        MAP.put(CaptionStyleType.DEPTH_STACK_3D, solid3dFont);
        MAP.put(CaptionStyleType.CUBE_ROTATE_3D, solid3dFont);
        MAP.put(CaptionStyleType.TILT_PERSPECTIVE_3D, solid3dFont);
        MAP.put(CaptionStyleType.ROTATE_IN_3D_FLIP, solid3dFont);

        // --- new modern/advanced styles ---
        MAP.put(CaptionStyleType.NEON_OUTLINE_GLOW, defaultFont);
        MAP.put(CaptionStyleType.LIQUID_GRADIENT_SWEEP, defaultFont);
        MAP.put(CaptionStyleType.GLASSMORPHISM_CARD, defaultFont);
        MAP.put(CaptionStyleType.CHROME_METALLIC, markerFont);
        MAP.put(CaptionStyleType.DUOTONE_SPLIT, markerFont);
        MAP.put(CaptionStyleType.CONFETTI_POP, bounceFont);
        MAP.put(CaptionStyleType.MARKER_HIGHLIGHT_ROTATE, defaultFont);
        MAP.put(CaptionStyleType.COMIC_BOUNCE_OUTLINE, stampFont);
        MAP.put(CaptionStyleType.CINEMATIC_LETTERBOX, defaultFont);
        MAP.put(CaptionStyleType.SPLIT_REVEAL_SCAN, defaultFont);

        // --- Featured tab additions (12) ---
        MAP.put(CaptionStyleType.BACKGROUND_CARD, defaultFont);
        MAP.put(CaptionStyleType.GRADIENT_TEXT, markerFont);
        MAP.put(CaptionStyleType.SOFT_SHADOW_RIGHT, defaultFont);
        MAP.put(CaptionStyleType.ROUNDED_PILL_HIGHLIGHT, defaultFont);
        MAP.put(CaptionStyleType.OUTLINE_STROKE, markerFont);
        MAP.put(CaptionStyleType.DUAL_TONE, defaultFont);
        MAP.put(CaptionStyleType.SUBTITLE_BAR, typewriterFont);
        MAP.put(CaptionStyleType.BOX_GLOW_COMBO, defaultFont);
        MAP.put(CaptionStyleType.LINE_UNDERLINE_SWEEP, defaultFont);
        MAP.put(CaptionStyleType.SOFT_CARD_SHADOW, defaultFont);
        MAP.put(CaptionStyleType.CORNER_ROUNDED_HIGHLIGHT, markerFont);
        MAP.put(CaptionStyleType.CLEAN_CAPS, markerFont);

        // --- Viral tab additions (18) ---
        MAP.put(CaptionStyleType.EMOJI_POP_ACCENT, bounceFont);
        MAP.put(CaptionStyleType.FLASH_CUT, markerFont);
        MAP.put(CaptionStyleType.RAINBOW_CYCLE, defaultFont);
        MAP.put(CaptionStyleType.PUNCH_IN, bounceFont);
        MAP.put(CaptionStyleType.STICKER_POP, stampFont);
        MAP.put(CaptionStyleType.BOLD_DROP_SHADOW_BOUNCE, markerFont);
        MAP.put(CaptionStyleType.COMIC_POP, stampFont);
        MAP.put(CaptionStyleType.SPEED_RAMP_TEXT, defaultFont);
        MAP.put(CaptionStyleType.GLITCH_FLICKER, typewriterFont);
        MAP.put(CaptionStyleType.FIRE_HIGHLIGHT, bounceFont);
        MAP.put(CaptionStyleType.ICE_HIGHLIGHT, defaultFont);
        MAP.put(CaptionStyleType.PULSE_BEAT, defaultFont);
        MAP.put(CaptionStyleType.MEGA_BOLD_CAPS, markerFont);
        MAP.put(CaptionStyleType.WAVY_BASELINE, calligraphyFont);
        MAP.put(CaptionStyleType.SINGLE_WORD_FLASH, markerFont);
        MAP.put(CaptionStyleType.NEON_PULSE_TEXT, defaultFont);
        MAP.put(CaptionStyleType.HYPE_BOUNCE_GLOW, bounceFont);
        MAP.put(CaptionStyleType.TURBO_SHAKE_POP, stampFont);
    }

    public static FontInfo get(CaptionStyleType style) {
        FontInfo info = MAP.get(style);
        return info != null ? info : MAP.get(CaptionStyleType.MINIMAL_FADE);
    }
}

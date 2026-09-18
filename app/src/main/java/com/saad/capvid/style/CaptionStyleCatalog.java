package com.saad.capvid.style;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

import static com.saad.capvid.style.CaptionStyleCategory.*;
import static com.saad.capvid.style.CaptionStyleDefinition.PreviewTreatment.*;

/**
 * Single source of truth for the template picker.
 * TemplatePickerBottomSheet reads ALL_STYLES and filters by category.
 *
 * 60 templates total: exactly 30 in FEATURED, exactly 30 in VIRAL (spec:
 * "Featured tab > 30 templates ... Viral tab > 30 templates different from
 * the features tab"). Each style belongs to exactly one of those two tabs
 * now (CUSTOM is reserved for user-saved templates via
 * CustomTemplateManager).
 *
 * IDs for the first 30 below are unchanged from the original catalog and
 * MUST keep matching CaptionOverlayView.CaptionStyleType / StyleFontMap /
 * AssSubtitleBuilder. The 30 new ids (BACKGROUND_CARD ... TURBO_SHAKE_POP)
 * are rendered live via the generic treatment-based fallback in
 * CaptionOverlayView (see drawByTreatmentLookup) — no bespoke draw code
 * needed for them.
 */
public class CaptionStyleCatalog {

    public static final List<CaptionStyleDefinition> ALL_STYLES = buildCatalog();

    private static List<CaptionStyleDefinition> buildCatalog() {
        List<CaptionStyleDefinition> list = new ArrayList<>();

        // ================= FEATURED (30) =================
        list.add(style("MINIMAL_FADE", "Minimal Fade", cats(FEATURED),
                colors("#FFFFFF", "#FFFFFF", "#FFFFFF", "#000000"), "All-Genders-Regular-v4.otf", true, PLAIN, false));

        list.add(style("KARAOKE_HIGHLIGHT", "Karaoke Highlight", cats(FEATURED),
                colors("#FFFFFF", "#FFD400", "#FFD400", "#000000"), "All-Genders-Regular-v4.otf", true, PLAIN, false));

        list.add(style("GLOW_POP", "Glow Pop", cats(FEATURED),
                colors("#FFFFFF", "#FFFFFF", "#FFFFFF", "#7B2FF7"), "All-Genders-Regular-v4.otf", true, OUTLINE_GLOW, false));

        list.add(style("SHADOW_PULSE", "Shadow Pulse", cats(FEATURED),
                colors("#FFC107", "#FFFFFF", "#FFFFFF", "#000000"), "All-Genders-Regular-v4.otf", true, PLAIN, false));

        list.add(style("UNDERLINE_DRAW", "Underline Draw", cats(FEATURED),
                colors("#FFFFFF", "#2979FF", "#2979FF", "#000000"), "All-Genders-Regular-v4.otf", true, PLAIN, false));

        list.add(style("SLIDE_IN_CASCADE", "Slide-in Cascade", cats(FEATURED),
                colors("#FFFFFF", "#1B5E20", "#1B5E20", "#000000"), "All-Genders-Regular-v4.otf", true, PLAIN, false));

        list.add(style("TYPEWRITER", "Typewriter", cats(FEATURED),
                colors("#FFFFFF", "#FFFFFF", "#FFFFFF", "#000000"), "RobotoMono-Bold.ttf", true, PLAIN, false));

        list.add(style("BLUR_TO_FOCUS", "Blur-to-Focus", cats(FEATURED),
                colors("#FFFFFF", "#FF4FA0", "#FF4FA0", "#000000"), "All-Genders-Regular-v4.otf", true, PLAIN, false));

        list.add(style("ROTATE_IN_3D_FLIP", "Rotate-In 3D Flip", cats(FEATURED),
                colors("#FFFFFF", "#1A237E", "#1A237E", "#000000"), "solid 3d.ttf", true, PLAIN, false));

        list.add(style("DEPTH_STACK_3D", "Depth Stack 3D", cats(FEATURED),
                colors("#FFC107", "#FF8F00", "#FFFFFF", "#000000"), "solid 3d.ttf", true, PLAIN, false));

        list.add(style("CUBE_ROTATE_3D", "Cube Rotate 3D", cats(FEATURED),
                colors("#FFFFFF", "#FFFFFF", "#FFFFFF", "#000000"), "solid 3d.ttf", true, PLAIN, false));

        list.add(style("TILT_PERSPECTIVE_3D", "Tilt Perspective 3D", cats(FEATURED),
                colors("#B3E5FC", "#FFFFFF", "#FFFFFF", "#000000"), "solid 3d.ttf", true, PLAIN, false));

        list.add(style("LIQUID_GRADIENT_SWEEP", "Liquid Gradient Sweep", cats(FEATURED),
                colors("#7B2FF7", "#2979FF", "#00E5FF", "#FFFFFF"), "All-Genders-Regular-v4.otf", false, GRADIENT_FILL, true));

        list.add(style("GLASSMORPHISM_CARD", "Glassmorphism Card", cats(FEATURED),
                colors("#0A0A0F", "#FFFFFF", "#D8D8D8", "#000000"), "All-Genders-Regular-v4.otf", false, BOX_TRANSLUCENT, true));

        list.add(style("CHROME_METALLIC", "Chrome Metallic", cats(FEATURED),
                colors("#E0E0E0", "#9E9E9E", "#FFFFFF", "#000000"), "Jost-Black.ttf", false, CHROME, true));

        list.add(style("DUOTONE_SPLIT", "Duotone Split", cats(FEATURED),
                colors("#000000", "#FFFFFF", "#7B2FF7", "#2979FF"), "Jost-Black.ttf", false, SPLIT_HALF, true));

        list.add(style("CINEMATIC_LETTERBOX", "Cinematic Letterbox", cats(FEATURED),
                colors("#FFFFFF", "#FFFFFF", "#FFFFFF", "#000000"), "All-Genders-Regular-v4.otf", false, SERIF_ITALIC, true));

        list.add(style("SPLIT_REVEAL_SCAN", "Split Reveal Scan", cats(FEATURED),
                colors("#D8D8D8", "#000000", "#7B2FF7", "#2979FF"), "All-Genders-Regular-v4.otf", false, SPLIT_HALF, true));

        list.add(style("BACKGROUND_CARD", "Background Card", cats(FEATURED),
                colors("#030304", "#FFFFFF", "#D8D8D8", "#000000"), "All-Genders-Regular-v4.otf", false, BOX_TRANSLUCENT, true));

        list.add(style("GRADIENT_TEXT", "Gradient Text", cats(FEATURED),
                colors("#2979FF", "#7B2FF7", "#FF4FA0", "#FFFFFF"), "Jost-Black.ttf", false, GRADIENT_FILL, true));

        list.add(style("SOFT_SHADOW_RIGHT", "Soft Shadow Right", cats(FEATURED),
                colors("#FFFFFF", "#FFFFFF", "#FFFFFF", "#333333"), "All-Genders-Regular-v4.otf", false, PLAIN, true));

        list.add(style("ROUNDED_PILL_HIGHLIGHT", "Rounded Pill Highlight", cats(FEATURED),
                colors("#001A4D", "#FFFFFF", "#FFFFFF", "#000000"), "All-Genders-Regular-v4.otf", false, BOX_SOLID, true));

        list.add(style("OUTLINE_STROKE", "Outline Stroke", cats(FEATURED),
                colors("#FFFFFF", "#FFFFFF", "#FFFFFF", "#000000"), "Jost-Black.ttf", false, COMIC_OUTLINE, true));

        list.add(style("DUAL_TONE", "Dual Tone", cats(FEATURED),
                colors("#FFFFFF", "#FFD400", "#FFD400", "#000000"), "All-Genders-Regular-v4.otf", false, PLAIN, true));

        list.add(style("SUBTITLE_BAR", "Subtitle Bar", cats(FEATURED),
                colors("#000000", "#FFFFFF", "#D8D8D8", "#000000"), "RobotoMono-Regular.ttf", false, BOX_SOLID, true));

        list.add(style("BOX_GLOW_COMBO", "Box + Glow Combo", cats(FEATURED),
                colors("#7B2FF7", "#FFFFFF", "#FFFFFF", "#000000"), "All-Genders-Regular-v4.otf", false, OUTLINE_GLOW, true));

        list.add(style("LINE_UNDERLINE_SWEEP", "Line Underline Sweep", cats(FEATURED),
                colors("#FFFFFF", "#2979FF", "#2979FF", "#000000"), "All-Genders-Regular-v4.otf", false, PLAIN, true));

        list.add(style("SOFT_CARD_SHADOW", "Soft Card Shadow", cats(FEATURED),
                colors("#0A0A0F", "#FFFFFF", "#EFEFEF", "#000000"), "All-Genders-Regular-v4.otf", false, BOX_TRANSLUCENT, true));

        list.add(style("CORNER_ROUNDED_HIGHLIGHT", "Corner Rounded Highlight", cats(FEATURED),
                colors("#FFD400", "#000000", "#FFFFFF", "#000000"), "Jost-Black.ttf", false, BOX_SOLID, true));

        list.add(style("CLEAN_CAPS", "Clean Caps", cats(FEATURED),
                colors("#FFFFFF", "#FFFFFF", "#FFFFFF", "#000000"), "Jost-Black.ttf", false, PLAIN, true));

        // ================= VIRAL (30) =================
        list.add(style("HIGHLIGHT_BOX_MARKER", "Highlight Box Marker", cats(VIRAL),
                colors("#2B0F4A", "#FFEE58", "#2B0F4A", "#2B0F4A"), "Jost-Black.ttf", true, BOX_SOLID, false));

        list.add(style("BOUNCE_POP", "Bounce Pop", cats(VIRAL),
                colors("#FF3B30", "#FF3B30", "#FF3B30", "#FFFFFF"), "SoulDaisy.otf", true, PLAIN, false));

        list.add(style("WORD_POP_SCALE", "Word Pop Scale", cats(VIRAL),
                colors("#FFFFFF", "#8BC34A", "#8BC34A", "#7B2FF7"), "All-Genders-Regular-v4.otf", true, PLAIN, false));

        list.add(style("COLOR_SPLASH", "Color Splash", cats(VIRAL),
                colors("#000000", "#FFFFFF", "#FFFFFF", "#5C0926"), "All-Genders-Regular-v4.otf", true, ROTATED_MARKER, false));

        list.add(style("ZIGZAG_CALLIGRAPHY", "Zigzag Calligraphy", cats(VIRAL),
                colors("#FFFFFF", "#FFFFFF", "#FFFFFF", "#000000"), "Calligrapher-JRxaE.ttf", true, PLAIN, false));

        list.add(style("SHAKE_WIGGLE_EMPHASIS", "Shake / Wiggle Emphasis", cats(VIRAL),
                colors("#FFFFFF", "#FFEB3B", "#FFEB3B", "#000000"), "All-Genders-Regular-v4.otf", true, PLAIN, false));

        list.add(style("GRADIENT_WAVE", "Gradient Wave", cats(VIRAL),
                colors("#FF6EC7", "#7B2FF7", "#2979FF", "#FFFFFF"), "All-Genders-Regular-v4.otf", true, GRADIENT_FILL, false));

        list.add(style("STAMP_IMPACT", "Stamp Impact", cats(VIRAL),
                colors("#7B2FF7", "#FFFFFF", "#FFFFFF", "#000000"), "ChauPhilomeneOne-Regular.ttf", true, PLAIN, false));

        list.add(style("NEON_OUTLINE_GLOW", "Neon Outline Glow", cats(VIRAL),
                colors("#00E5FF", "#FF2DF1", "#000000", "#000000"), "All-Genders-Regular-v4.otf", false, OUTLINE_GLOW, true));

        list.add(style("CONFETTI_POP", "Confetti Pop", cats(VIRAL),
                colors("#FF3B30", "#FFD400", "#8BC34A", "#2979FF"), "SoulDaisy.otf", false, PLAIN, true));

        list.add(style("MARKER_HIGHLIGHT_ROTATE", "Marker Highlight Rotate", cats(VIRAL),
                colors("#000000", "#FFFFFF", "#FFFFFF", "#5C0926"), "All-Genders-Regular-v4.otf", false, ROTATED_MARKER, true));

        list.add(style("COMIC_BOUNCE_OUTLINE", "Comic Bounce Outline", cats(VIRAL),
                colors("#FFEB3B", "#000000", "#FFFFFF", "#000000"), "ChauPhilomeneOne-Regular.ttf", false, COMIC_OUTLINE, true));

        list.add(style("EMOJI_POP_ACCENT", "Emoji Pop Accent", cats(VIRAL),
                colors("#FFD400", "#FF3B30", "#FFFFFF", "#000000"), "SoulDaisy.otf", false, PLAIN, true));

        list.add(style("FLASH_CUT", "Flash Cut", cats(VIRAL),
                colors("#FFFFFF", "#000000", "#FFFFFF", "#000000"), "Jost-Black.ttf", false, BOX_SOLID, true));

        list.add(style("RAINBOW_CYCLE", "Rainbow Cycle", cats(VIRAL),
                colors("#FF3B30", "#FFD400", "#2979FF", "#7B2FF7"), "All-Genders-Regular-v4.otf", false, GRADIENT_FILL, true));

        list.add(style("PUNCH_IN", "Punch In", cats(VIRAL),
                colors("#FF3B30", "#FFFFFF", "#FFFFFF", "#000000"), "SoulDaisy.otf", false, PLAIN, true));

        list.add(style("STICKER_POP", "Sticker Pop", cats(VIRAL),
                colors("#FFD400", "#000000", "#FFFFFF", "#000000"), "ChauPhilomeneOne-Regular.ttf", false, BOX_SOLID, true));

        list.add(style("BOLD_DROP_SHADOW_BOUNCE", "Bold Drop Shadow Bounce", cats(VIRAL),
                colors("#FFFFFF", "#7B2FF7", "#FFFFFF", "#000000"), "Jost-Black.ttf", false, COMIC_OUTLINE, true));

        list.add(style("COMIC_POP", "Comic Pop", cats(VIRAL),
                colors("#FFEE58", "#E91E63", "#FFFFFF", "#000000"), "ChauPhilomeneOne-Regular.ttf", false, COMIC_OUTLINE, true));

        list.add(style("SPEED_RAMP_TEXT", "Speed Ramp Text", cats(VIRAL),
                colors("#FFFFFF", "#2979FF", "#FFFFFF", "#000000"), "All-Genders-Regular-v4.otf", false, PLAIN, true));

        list.add(style("GLITCH_FLICKER", "Glitch Flicker", cats(VIRAL),
                colors("#00E5FF", "#FF2DF1", "#FFFFFF", "#000000"), "RobotoMono-Bold.ttf", false, OUTLINE_GLOW, true));

        list.add(style("FIRE_HIGHLIGHT", "Fire Highlight", cats(VIRAL),
                colors("#FF3B30", "#FF8F00", "#FFD400", "#000000"), "SoulDaisy.otf", false, GRADIENT_FILL, true));

        list.add(style("ICE_HIGHLIGHT", "Ice Highlight", cats(VIRAL),
                colors("#00E5FF", "#B3E5FC", "#FFFFFF", "#000000"), "All-Genders-Regular-v4.otf", false, GRADIENT_FILL, true));

        list.add(style("PULSE_BEAT", "Pulse Beat", cats(VIRAL),
                colors("#FF3B30", "#FFFFFF", "#FFFFFF", "#7B2FF7"), "All-Genders-Regular-v4.otf", false, PLAIN, true));

        list.add(style("MEGA_BOLD_CAPS", "Mega Bold Caps", cats(VIRAL),
                colors("#050505", "#FFD400", "#FFD400", "#000000"), "Jost-Black.ttf", false, BOX_SOLID, true));

        list.add(style("WAVY_BASELINE", "Wavy Baseline", cats(VIRAL),
                colors("#FFFFFF", "#FF6EC7", "#FF6EC7", "#000000"), "Calligrapher-JRxaE.ttf", false, PLAIN, true));

        list.add(style("SINGLE_WORD_FLASH", "Single Word Flash", cats(VIRAL),
                colors("#FFFFFF", "#000000", "#FFFFFF", "#000000"), "Jost-Black.ttf", false, BOX_SOLID, true));

        list.add(style("NEON_PULSE_TEXT", "Neon Pulse Text", cats(VIRAL),
                colors("#FF2DF1", "#00E5FF", "#000000", "#000000"), "All-Genders-Regular-v4.otf", false, OUTLINE_GLOW, true));

        list.add(style("HYPE_BOUNCE_GLOW", "Hype Bounce Glow", cats(VIRAL),
                colors("#FFD400", "#FF3B30", "#000000", "#000000"), "SoulDaisy.otf", false, OUTLINE_GLOW, true));

        list.add(style("TURBO_SHAKE_POP", "Turbo Shake Pop", cats(VIRAL),
                colors("#8BC34A", "#FFFFFF", "#FFFFFF", "#000000"), "ChauPhilomeneOne-Regular.ttf", false, COMIC_OUTLINE, true));

        return list;
    }

    public static List<CaptionStyleDefinition> byCategory(CaptionStyleCategory category) {
        List<CaptionStyleDefinition> result = new ArrayList<>();
        for (CaptionStyleDefinition d : ALL_STYLES) {
            if (d.categories.contains(category)) result.add(d);
        }
        return result;
    }

    public static CaptionStyleDefinition byId(String id) {
        for (CaptionStyleDefinition d : ALL_STYLES) {
            if (d.id.equals(id)) return d;
        }
        return null;
    }

    // ---- small helpers to keep the table above readable ----
    private static List<CaptionStyleCategory> cats(CaptionStyleCategory c) {
        return java.util.Collections.singletonList(c);
    }

    private static int[] colors(String a, String b, String c, String d) {
        return new int[]{Color.parseColor(a), Color.parseColor(b), Color.parseColor(c), Color.parseColor(d)};
    }

    private static CaptionStyleDefinition style(String id, String name, List<CaptionStyleCategory> cats,
                                                 int[] swatches, String font, boolean lite,
                                                 CaptionStyleDefinition.PreviewTreatment t, boolean modern) {
        return new CaptionStyleDefinition(id, name, cats, swatches, font, lite, t, modern);
    }
}

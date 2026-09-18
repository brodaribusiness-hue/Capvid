package com.saad.capvid.font;

import com.saad.capvid.caption.CaptionOverlayView.CaptionStyleType;
import com.saad.capvid.style.CaptionStyleCatalog;
import com.saad.capvid.style.CaptionStyleDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves, for a given caption style, (a) which bundled font asset to render
 * with and (b) the exact font FAMILY NAME libass has to be given in the .ass
 * file so the exported video uses that same font.
 *
 * <h3>Why the asset comes from CaptionStyleCatalog</h3>
 * This class used to keep its own 60-entry table of style -&gt; font. That table
 * silently drifted out of sync with the catalog: SUBTITLE_BAR was declared as
 * "RobotoMono-Regular.ttf" in the catalog (so the template picker previewed
 * Regular) while this class mapped it to "RobotoMono-Bold.ttf" (so the overlay
 * and the export rendered Bold). The catalog is now the single source of truth
 * and this class only derives from it, so the two can no longer disagree.
 *
 * <h3>Why ASS_FAMILY_NAMES has to be exact</h3>
 * At export time FFmpeg hands the .ass file to libass, and libass resolves the
 * "Fontname" of a Style by matching it against the family name recorded in each
 * font file's OpenType {@code name} table (name ID 1) for the fonts it finds in
 * {@code fontsdir}. If the name we write does not match, libass does NOT error -
 * it silently substitutes its built-in default face, so the exported video comes
 * out in a different font than the on-screen preview with no warning at all.
 *
 * The values below are the literal name-ID-1 strings read out of the bundled
 * files (verified with fontTools; {@code tools/audit_static.py} re-checks them
 * on every run and fails the build if a font file changes underneath us):
 *
 * <pre>
 *   All-Genders-Regular-v4.otf    -&gt; "All Genders v4"      (was wrongly "All Genders")
 *   Jost-Black.ttf                -&gt; "Jost Black"          (was wrongly "Jost")
 *   solid 3d.ttf                  -&gt; "Solid 3d"            (lower-case d, as recorded)
 * </pre>
 */
public class StyleFontMap {

    public static class FontInfo {
        public final String assetFileName;
        public final String assFamilyName;

        public FontInfo(String assetFileName, String assFamilyName) {
            this.assetFileName = assetFileName;
            this.assFamilyName = assFamilyName;
        }
    }

    /**
     * asset filename -&gt; real family name recorded in that file's name table.
     * Every entry in {@link FontManager#FONT_FILES} must appear here, otherwise
     * {@link #familyNameFor(String)} falls back to a filename-derived guess that
     * libass will not match.
     */
    private static final Map<String, String> ASS_FAMILY_NAMES = new HashMap<>();

    static {
        ASS_FAMILY_NAMES.put("All-Genders-Regular-v4.otf", "All Genders v4");
        ASS_FAMILY_NAMES.put("Calligrapher-JRxaE.ttf", "Calligrapher");
        ASS_FAMILY_NAMES.put("ChauPhilomeneOne-Regular.ttf", "Chau Philomene One");
        ASS_FAMILY_NAMES.put("JavaCalligraphy-w1Pw6.ttf", "Java Calligraphy");
        ASS_FAMILY_NAMES.put("Jost-Black.ttf", "Jost Black");
        ASS_FAMILY_NAMES.put("KhatijaCalligraphy-0Z5o.otf", "Khatija Calligraphy");
        ASS_FAMILY_NAMES.put("RobotoMono-Bold.ttf", "Roboto Mono");
        ASS_FAMILY_NAMES.put("RobotoMono-Regular.ttf", "Roboto Mono");
        ASS_FAMILY_NAMES.put("SoulDaisy.otf", "Soul Daisy");
        ASS_FAMILY_NAMES.put("solid 3d.ttf", "Solid 3d");
    }

    /** Fallback used when a style id has no catalog entry (should never happen). */
    private static final String DEFAULT_ASSET = "All-Genders-Regular-v4.otf";

    /**
     * @return the font to use for {@code style}, taken from that style's
     *         CaptionStyleDefinition.fontAsset.
     */
    public static FontInfo get(CaptionStyleType style) {
        return get(style != null ? style.name() : null);
    }

    /**
     * @param styleId a CaptionStyleType name, or null
     */
    public static FontInfo get(String styleId) {
        String asset = assetForStyleId(styleId);
        return new FontInfo(asset, familyNameFor(asset));
    }

    /** The catalog is the single source of truth for which font a style uses. */
    public static String assetForStyleId(String styleId) {
        if (styleId != null) {
            CaptionStyleDefinition def = CaptionStyleCatalog.byId(styleId);
            if (def != null && def.fontAsset != null && !def.fontAsset.isEmpty()) {
                return def.fontAsset;
            }
        }
        return DEFAULT_ASSET;
    }

    /**
     * The libass family name for an arbitrary asset - needed for the Fonts tab,
     * where the user can override a template's font with any bundled file.
     */
    public static String familyNameFor(String assetFileName) {
        if (assetFileName == null) return familyNameFor(DEFAULT_ASSET);
        String known = ASS_FAMILY_NAMES.get(assetFileName);
        if (known != null) return known;
        // Unknown file: strip the extension and the trailing style suffix so we
        // still produce something plausible rather than a filename with ".ttf".
        String base = assetFileName.replaceAll("\\.(?i:ttf|otf|ttc)$", "");
        base = base.replaceAll("[-_ ](Regular|Bold|Italic|Black|Medium|Light|Thin|SemiBold|ExtraBold)$", "");
        return base.replace('-', ' ').replace('_', ' ');
    }
}

package com.saad.capvid.style;

/**
 * Tabs shown at the top of the style/template picker: Custom, Featured,
 * Viral — matches the app's spec (Featured tab: 30 templates, Viral tab:
 * 30 templates, all different; Custom tab: user-saved templates).
 */
public enum CaptionStyleCategory {
    CUSTOM("Custom"),
    FEATURED("Featured"),
    VIRAL("Viral");

    private final String label;

    CaptionStyleCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

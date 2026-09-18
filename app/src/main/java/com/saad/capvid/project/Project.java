package com.saad.capvid.project;

import com.saad.capvid.model.CaptionWord;
import com.saad.capvid.style.CaptionStyleOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * A saved editing session: which video, what was transcribed, and the
 * trim/scale/style state so PreviewActivity can resume exactly where the
 * user left off instead of re-transcribing from scratch.
 */
public class Project {
    public String id;
    public String name;
    public String videoUri;
    public long createdAtMs;
    public long lastEditedMs;

    public long trimStartMs = 0;
    public long trimEndMs = -1;
    public float scaleFactor = 1f;
    public String styleId = "MINIMAL_FADE";

    /**
     * The rest of the editing state. These used to be held only by
     * CaptionOverlayView, so reopening a project restored the transcript and the
     * template id but silently reset text size, bold/italic, caption position and
     * every Color/Fonts/Breaks override back to defaults.
     */
    public float textSizeSp = 20f;
    public boolean bold = false;
    public boolean italic = false;
    public float posXFraction = 0.5f;
    public float posYFraction = 0.85f;
    public CaptionStyleOptions options = new CaptionStyleOptions();

    public List<CaptionWord> words = new ArrayList<>();
}

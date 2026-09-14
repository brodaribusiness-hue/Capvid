package com.saad.capvid.project;

import com.saad.capvid.model.CaptionWord;

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

    public List<CaptionWord> words = new ArrayList<>();
}

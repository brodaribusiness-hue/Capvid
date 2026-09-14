package com.saad.capvid.model;

public class CaptionWord {
    public String text;
    public long startMs;
    public long endMs;

    public CaptionWord(String text, long startMs, long endMs) {
        this.text = text;
        this.startMs = startMs;
        this.endMs = endMs;
    }
}

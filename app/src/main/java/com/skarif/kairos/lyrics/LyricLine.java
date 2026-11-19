package com.skarif.kairos.lyrics;

/** One line of lyrics. {@code timeMs} is -1 for plain (unsynced) lyrics. */
public final class LyricLine {
    public final long timeMs;
    public final String text;

    public LyricLine(long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text;
    }

    @Override
    public String toString() {
        return timeMs + " " + text;
    }
}

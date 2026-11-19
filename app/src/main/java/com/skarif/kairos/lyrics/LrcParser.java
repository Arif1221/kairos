package com.skarif.kairos.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LRC text, the format used by .lrc files and by most embedded synced lyrics.
 *
 * Handles: several timestamps on one line ([00:12.00][01:30.50]text), 1-3 digit fractions,
 * the [offset:+/-ms] tag, enhanced word timestamps (<00:12.30>), and metadata tags like [ar:...].
 * Text with no timestamps at all is returned as plain lyrics.
 */
public final class LrcParser {
    /** Singer tags some lyric sources put before a line: "v1:", "v2: ". Not part of the words. */
    private static final java.util.regex.Pattern VOICE = java.util.regex.Pattern.compile("^v\\d{1,2}:\\s*");

    public static String stripVoice(String line) {
        return VOICE.matcher(line).replaceFirst("");
    }

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TIME = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");
    private static final Pattern META = Pattern.compile("^\\[([a-zA-Z#]+):(.*)]$");
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*]$", Pattern.CASE_INSENSITIVE);

    private LrcParser() {}

    /** True if the text contains at least one LRC timestamp. */
    public static boolean looksSynced(String text) {
        return text != null && TIME.matcher(text).find();
    }

    public static Lyrics parse(String text, String source) {
        List<LyricLine> timed = new ArrayList<>();
        List<LyricLine> plain = new ArrayList<>();
        long offset = 0;
        if (text == null) return new Lyrics(false, plain, source);

        String[] rows = text.replace("﻿", "").split("\\r\\n|\\r|\\n");
        for (String raw : rows) {
            String row = raw.trim();
            Matcher off = OFFSET.matcher(row);
            if (off.matches()) {
                offset = Long.parseLong(off.group(1).replace("+", ""));
                continue;
            }
            Matcher m = TIME.matcher(row);
            List<Long> stamps = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                stamps.add(toMs(m.group(1), m.group(2), m.group(3)));
                end = m.end();
            }
            if (!stamps.isEmpty()) {
                String lyric = VOICE.matcher(WORD_TIME.matcher(row.substring(end)).replaceAll("").trim()).replaceFirst("");
                for (long t : stamps) timed.add(new LyricLine(t, lyric));
            } else if (!META.matcher(row).matches()) {
                plain.add(new LyricLine(-1, VOICE.matcher(row).replaceFirst("")));
            }
        }

        if (!timed.isEmpty()) {
            List<LyricLine> shifted = new ArrayList<>(timed.size());
            for (LyricLine l : timed) shifted.add(new LyricLine(Math.max(0, l.timeMs - offset), l.text));
            shifted.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
            return new Lyrics(true, shifted, source);
        }
        return new Lyrics(false, trimBlankEdges(plain), source);
    }

    /** Builds synced lyrics from already-timed lines (ID3 SYLT frames). */
    public static Lyrics fromTimed(List<LyricLine> lines, String source) {
        List<LyricLine> copy = new ArrayList<>();
        for (LyricLine l : lines) copy.add(new LyricLine(l.timeMs, l.text.trim()));
        copy.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        return new Lyrics(true, copy, source);
    }

    private static long toMs(String min, String sec, String frac) {
        long ms = Long.parseLong(min) * 60_000L + Long.parseLong(sec) * 1000L;
        if (frac != null) {
            int f = Integer.parseInt(frac);
            if (frac.length() == 1) f *= 100;
            else if (frac.length() == 2) f *= 10;
            ms += f;
        }
        return ms;
    }

    private static List<LyricLine> trimBlankEdges(List<LyricLine> lines) {
        int s = 0, e = lines.size();
        while (s < e && lines.get(s).text.isEmpty()) s++;
        while (e > s && lines.get(e - 1).text.isEmpty()) e--;
        return new ArrayList<>(lines.subList(s, e));
    }
}

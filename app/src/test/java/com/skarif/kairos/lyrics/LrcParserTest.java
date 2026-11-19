package com.skarif.kairos.lyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LrcParserTest {

    @Test
    public void parsesMultipleStampsFractionsAndWordTimes() {
        Lyrics l = LrcParser.parse("[ti:x]\n[00:01.5]a\n[00:00.25][00:03.123]b <00:03.50>c\n", "t");
        assertTrue(l.synced);
        assertEquals(3, l.lines.size());
        assertEquals(250L, l.lines.get(0).timeMs);
        assertEquals(1500L, l.lines.get(1).timeMs);
        assertEquals(3123L, l.lines.get(2).timeMs);
        assertEquals("b c", l.lines.get(2).text);
    }

    @Test
    public void appliesOffsetTag() {
        Lyrics l = LrcParser.parse("[offset:+500]\n[00:10.00]Ten", "t");
        assertEquals(9500L, l.lines.get(0).timeMs);
    }

    @Test
    public void indexAtUsesLastLineNotAfterPosition() {
        Lyrics l = LrcParser.parse("[00:01.00]a\n[00:02.00]b\n[00:03.00]c", "t");
        assertEquals(-1, l.indexAt(500));
        assertEquals(1, l.indexAt(2999));
        assertEquals(2, l.indexAt(60_000));
    }

    @Test
    public void textWithoutStampsIsPlain() {
        Lyrics l = LrcParser.parse("\nOne\n\nTwo\n", "t");
        assertFalse(l.synced);
        assertEquals(3, l.lines.size());
    }

    @Test
    public void readsReplayGainNumbers() {
        assertEquals(-6.48f, TagReader.parseGain("-6.48 dB"), 0.001f);
        assertEquals(-7.25f, TagReader.parseGain("-7,25 dB"), 0.001f);
    }
}

import com.skarif.kairos.lyrics.*;

import java.io.RandomAccessFile;
import java.util.List;

/** Plain-Java checks run here (JUnit is not downloadable in this sandbox). */
public class LyricsCheck {
    static int fails = 0;

    static void eq(Object want, Object got, String what) {
        boolean ok = want == null ? got == null : want.equals(got);
        System.out.println((ok ? "PASS " : "FAIL ") + what + (ok ? "" : "  want=" + want + " got=" + got));
        if (!ok) fails++;
    }

    static TagReader.Result read(String dir, String name) throws Exception {
        try (RandomAccessFile f = new RandomAccessFile(dir + "/" + name, "r")) {
            return TagReader.read(new ByteSource.OfChannel(f.getChannel()));
        }
    }

    public static void main(String[] a) throws Exception {
        String d = a[0];

        // LRC parser
        Lyrics l = LrcParser.parse("[ti:x]\n[00:01.5]a\n[00:00.25][00:03.123]b <00:03.50>c\n\n[00:02]\n", "t");
        eq(true, l.synced, "lrc synced");
        eq(4, l.lines.size(), "lrc line count (two stamps on one line)");
        eq(250L, l.lines.get(0).timeMs, "lrc 2-digit fraction");
        eq(1500L, l.lines.get(1).timeMs, "lrc 1-digit fraction");
        eq("", l.lines.get(2).text, "lrc empty instrumental line kept");
        eq("b c", l.lines.get(3).text, "lrc word timestamps stripped");
        eq(3123L, l.lines.get(3).timeMs, "lrc 3-digit fraction");
        eq(1, l.indexAt(1600), "indexAt middle");
        eq(-1, l.indexAt(100), "indexAt before first");
        eq(3, l.indexAt(999_999), "indexAt after last");
        Lyrics p = LrcParser.parse("\n\nPlain one\n\nPlain two\n\n", "t");
        eq(false, p.synced, "plain not synced");
        eq(3, p.lines.size(), "plain keeps inner blank, trims edges");

        // ID3v2.4 USLT (LRC) after 300 KB cover art, with ReplayGain
        TagReader.Result r = read(d, "v24_uslt_lrc.mp3");
        eq(true, LrcParser.looksSynced(r.lyricsText), "v2.4 USLT found past cover art");
        Lyrics v24 = LrcParser.parse(r.lyricsText, Lyrics.SOURCE_EMBEDDED);
        eq(4, v24.lines.size(), "v2.4 LRC lines");
        eq(41200L, v24.lines.get(0).timeMs, "v2.4 first timestamp");
        eq(48500L, v24.lines.get(1).timeMs, "v2.4 1-digit fraction");
        eq(-6.48f, r.trackGainDb, "v2.4 TXXX ReplayGain");

        r = read(d, "v23_uslt_utf16.mp3");
        eq("Line one\nলাইন দুই\nLine three", r.lyricsText, "v2.3 UTF-16 USLT incl. Bengali");

        r = read(d, "v23_sylt.mp3");
        eq(3, r.syncedLines.size(), "SYLT line count");
        eq("Second", r.syncedLines.get(1).text, "SYLT text (per-string BOM)");
        eq(2500L, r.syncedLines.get(1).timeMs, "SYLT ms timestamp");

        r = read(d, "lrc.flac");
        eq("flac", r.container, "flac detected");
        Lyrics fl = LrcParser.parse(r.lyricsText, Lyrics.SOURCE_EMBEDDED);
        eq(9500L, fl.lines.get(0).timeMs, "flac LRC with [offset:+500]");
        eq(-7.25f, r.trackGainDb, "flac track gain with comma decimal");
        eq(1.10f, r.albumGainDb, "flac album gain");

        r = read(d, "lyr.m4a");
        eq("mp4", r.container, "mp4 detected");
        eq("Plain line A\nPlain line B", r.lyricsText, "m4a ©lyr");
        eq(-3.20f, r.trackGainDb, "m4a freeform ReplayGain");

        r = read(d, "v23_unsync.mp3");
        eq("café ÿé end", r.lyricsText, "v2.3 whole-tag unsynchronisation");

        r = read(d, "v24_frame_unsync.mp3");
        eq("xÿéy��z", r.lyricsText.replaceAll("[\\u00ff\\u00e0]", "") .isEmpty() ? "" : r.lyricsText, "v2.4 frame unsync + DLI");

        // Robustness: random garbage must not throw
        java.util.Random rnd = new java.util.Random(1);
        for (int i = 0; i < 2000; i++) {
            byte[] junk = new byte[rnd.nextInt(600)];
            rnd.nextBytes(junk);
            if (junk.length > 3 && i % 2 == 0) { junk[0] = 'I'; junk[1] = 'D'; junk[2] = '3'; junk[3] = (byte) (2 + i % 3); }
            if (junk.length > 3 && i % 3 == 0) { junk[0] = 'f'; junk[1] = 'L'; junk[2] = 'a'; junk[3] = 'C'; }
            try { TagReader.read(new ByteSource.OfBytes(junk)); }
            catch (java.io.EOFException ok) { }
            catch (Exception e) { fails++; System.out.println("FAIL fuzz " + i + " " + e); break; }
        }
        System.out.println("fuzz done");
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}

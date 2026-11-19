package com.skarif.kairos.lyrics;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads embedded lyrics and ReplayGain from audio file tags, without any library.
 *
 * Supported:
 *  - MP3 (and anything else starting with ID3v2.2/2.3/2.4): USLT (plain or LRC text), SYLT (timed, ms),
 *    TXXX (REPLAYGAIN_*, LYRICS). All four text encodings, tag and frame unsynchronisation.
 *  - FLAC: Vorbis comments LYRICS / UNSYNCEDLYRICS / SYNCEDLYRICS, REPLAYGAIN_*.
 *  - M4A / ALAC / AAC in MP4: the ©lyr atom and iTunes "----" ReplayGain atoms.
 *
 * Not yet: Ogg Vorbis / Opus comments (they need Ogg page reassembly).
 */
public final class TagReader {

    public static final class Result {
        /** Raw lyric text (may itself be LRC). Null if none. */
        public String lyricsText;
        /** Lines from an ID3 SYLT frame. Empty if none. */
        public final List<LyricLine> syncedLines = new ArrayList<>();
        public Float trackGainDb;
        public Float albumGainDb;
        public String container = "unknown";

        public boolean hasLyrics() {
            return !syncedLines.isEmpty() || (lyricsText != null && !lyricsText.trim().isEmpty());
        }
    }

    private static final int MAX_TEXT = 1 << 20;          // a lyrics frame bigger than 1 MB is not lyrics
    private static final int MAX_UNSYNC_TAG = 32 << 20;   // whole-tag unsynchronisation needs the tag in memory
    private static final Pattern GAIN = Pattern.compile("([-+]?\\d+(?:[.,]\\d+)?)");

    private TagReader() {}

    public static Result read(ByteSource src) throws IOException {
        Result r = new Result();
        long len = src.length();
        if (len < 12) return r;
        byte[] h = new byte[12];
        src.readFully(0, h, 0, 12);

        if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') {
            r.container = "id3";
            long end = readId3(src, 0, r);
            if (end + 4 <= len) {
                byte[] m = new byte[4];
                src.readFully(end, m, 0, 4);
                if (isFlac(m, 0)) readFlac(src, end, r);
            }
        } else if (isFlac(h, 0)) {
            readFlac(src, 0, r);
        } else if (h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            r.container = "mp4";
            readMp4Atoms(src, 0, len, r, 0);
        }
        return r;
    }

    // ---------------------------------------------------------------- ID3v2

    private static long readId3(ByteSource src, long start, Result r) throws IOException {
        byte[] hd = new byte[10];
        src.readFully(start, hd, 0, 10);
        int major = hd[3] & 0xff;
        int flags = hd[5] & 0xff;
        long size = syncsafe(hd, 6);
        long end = start + 10 + size + ((major == 4 && (flags & 0x10) != 0) ? 10 : 0);
        if (major < 2 || major > 4) return end;

        ByteSource body = src;
        long p = start + 10;
        long bodyEnd = Math.min(start + 10 + size, src.length());

        if ((flags & 0x80) != 0 && major <= 3) {
            // Whole-tag unsynchronisation (old iTunes): undo it in memory, then parse that.
            int n = (int) Math.min(bodyEnd - p, MAX_UNSYNC_TAG);
            byte[] raw = new byte[n];
            src.readFully(p, raw, 0, n);
            byte[] clean = deUnsync(raw);
            body = new ByteSource.OfBytes(clean);
            p = 0;
            bodyEnd = clean.length;
        }

        if ((flags & 0x40) != 0 && major >= 3) {
            byte[] e = new byte[4];
            body.readFully(p, e, 0, 4);
            p += (major == 4) ? syncsafe(e, 0) : (int32(e, 0) + 4);
        }

        int hdrLen = (major == 2) ? 6 : 10;
        byte[] fh = new byte[hdrLen];
        while (p + hdrLen <= bodyEnd) {
            body.readFully(p, fh, 0, hdrLen);
            if (fh[0] == 0) break; // padding
            String id = new String(fh, 0, major == 2 ? 3 : 4, StandardCharsets.ISO_8859_1);
            long fsize = (major == 2) ? int24(fh, 3) : (major == 4 ? syncsafe(fh, 4) : int32(fh, 4));
            if (fsize <= 0 || p + hdrLen + fsize > bodyEnd) break;
            int fflags = (major >= 3) ? (fh[9] & 0xff) : 0;
            long dataPos = p + hdrLen;
            p = dataPos + fsize;

            String kind = frameKind(id);
            if (kind == null || fsize > MAX_TEXT) continue;

            int skip = 0;
            boolean frameUnsync = false;
            if (major == 4) {
                if ((fflags & 0x0C) != 0) continue;      // compressed or encrypted
                if ((fflags & 0x40) != 0) skip += 1;    // grouping id
                if ((fflags & 0x01) != 0) skip += 4;    // data length indicator
                frameUnsync = (fflags & 0x02) != 0;
            } else if (major == 3) {
                if ((fflags & 0xC0) != 0) continue;      // compressed or encrypted
                if ((fflags & 0x20) != 0) skip += 1;    // grouping id
            }
            if (fsize <= skip) continue;
            byte[] d = new byte[(int) (fsize - skip)];
            body.readFully(dataPos + skip, d, 0, d.length);
            if (frameUnsync) d = deUnsync(d);

            try {
                switch (kind) {
                    case "USLT": parseUslt(d, r); break;
                    case "SYLT": parseSylt(d, r); break;
                    case "TXXX": parseTxxx(d, r); break;
                    default: break;
                }
            } catch (IndexOutOfBoundsException ignored) {
                // A malformed frame should never stop playback or the rest of the tag.
            }
        }
        return end;
    }

    private static String frameKind(String id) {
        switch (id) {
            case "USLT": case "ULT": return "USLT";
            case "SYLT": case "SLT": return "SYLT";
            case "TXXX": case "TXX": return "TXXX";
            default: return null;
        }
    }

    private static void parseUslt(byte[] d, Result r) {
        int enc = d[0] & 0xff;
        int i = afterTerminator(d, 4, enc);     // skip 3-byte language + content descriptor
        String text = decode(d, i, d.length, enc);
        if (text.trim().isEmpty()) return;
        // Prefer LRC-formatted text if a file carries more than one USLT frame.
        if (r.lyricsText == null || (!LrcParser.looksSynced(r.lyricsText) && LrcParser.looksSynced(text))) {
            r.lyricsText = text;
        }
    }

    private static void parseSylt(byte[] d, Result r) {
        int enc = d[0] & 0xff;
        int format = d[4] & 0xff;               // 1 = MPEG frames, 2 = milliseconds
        if (format != 2 || !r.syncedLines.isEmpty()) return;
        int i = afterTerminator(d, 6, enc);     // language(3) + format(1) + type(1), then descriptor
        while (i < d.length) {
            int e = findTerminator(d, i, enc);
            String text = decode(d, i, e, enc);
            i = e + termLen(enc);
            if (i + 4 > d.length) break;
            long t = int32(d, i);
            i += 4;
            r.syncedLines.add(new LyricLine(t, LrcParser.stripVoice(text.replace("\r", "").replace("\n", " ").trim())));
        }
    }

    private static void parseTxxx(byte[] d, Result r) {
        int enc = d[0] & 0xff;
        int e = findTerminator(d, 1, enc);
        String desc = decode(d, 1, e, enc).trim().toUpperCase(Locale.ROOT);
        String value = decode(d, e + termLen(enc), d.length, enc);
        applyKey(desc, value, r);
    }

    // ---------------------------------------------------------------- FLAC

    private static boolean isFlac(byte[] b, int o) {
        return b[o] == 'f' && b[o + 1] == 'L' && b[o + 2] == 'a' && b[o + 3] == 'C';
    }

    private static void readFlac(ByteSource src, long start, Result r) throws IOException {
        r.container = "flac";
        long p = start + 4;
        long len = src.length();
        byte[] bh = new byte[4];
        while (p + 4 <= len) {
            src.readFully(p, bh, 0, 4);
            boolean last = (bh[0] & 0x80) != 0;
            int type = bh[0] & 0x7f;
            long blen = int24(bh, 1);
            if (type == 4 && blen <= MAX_TEXT * 4L && p + 4 + blen <= len) {
                byte[] d = new byte[(int) blen];
                src.readFully(p + 4, d, 0, d.length);
                parseVorbisComment(d, r);
            }
            p += 4 + blen;
            if (last) break;
        }
    }

    private static void parseVorbisComment(byte[] d, Result r) {
        try {
            int i = 0;
            long vendor = le32(d, i);
            i += 4 + (int) vendor;
            long count = le32(d, i);
            i += 4;
            for (long c = 0; c < count && i + 4 <= d.length; c++) {
                int n = (int) le32(d, i);
                i += 4;
                if (n < 0 || i + n > d.length) break;
                String kv = new String(d, i, n, StandardCharsets.UTF_8);
                i += n;
                int eq = kv.indexOf('=');
                if (eq > 0) applyKey(kv.substring(0, eq).toUpperCase(Locale.ROOT), kv.substring(eq + 1), r);
            }
        } catch (IndexOutOfBoundsException ignored) {
        }
    }

    // ---------------------------------------------------------------- MP4

    private static final byte[] LYR = {(byte) 0xA9, 'l', 'y', 'r'};

    private static void readMp4Atoms(ByteSource src, long start, long end, Result r, int depth) throws IOException {
        if (depth > 6) return;
        long p = start;
        byte[] h = new byte[16];
        while (p + 8 <= end) {
            src.readFully(p, h, 0, 8);
            long size = int32(h, 0);
            int hdr = 8;
            if (size == 1) {
                if (p + 16 > end) return;
                src.readFully(p + 8, h, 8, 8);
                size = (int32(h, 8) << 32) | int32(h, 12);
                hdr = 16;
            } else if (size == 0) {
                size = end - p;
            }
            if (size < hdr || p + size > end) return;
            String type = new String(h, 4, 4, StandardCharsets.ISO_8859_1);
            long body = p + hdr;
            long bodyEnd = p + size;

            if (type.equals("moov") || type.equals("udta") || type.equals("ilst")) {
                readMp4Atoms(src, body, bodyEnd, r, depth + 1);
            } else if (type.equals("meta")) {
                // ISO "meta" is a full box (4 bytes version/flags); QuickTime's is not. Look for hdlr to tell.
                byte[] probe = new byte[4];
                long offset = 0;
                if (body + 12 <= bodyEnd) {
                    src.readFully(body + 8, probe, 0, 4);
                    if (new String(probe, StandardCharsets.ISO_8859_1).equals("hdlr")) offset = 4;
                }
                readMp4Atoms(src, body + offset, bodyEnd, r, depth + 1);
            } else if (h[4] == LYR[0] && h[5] == LYR[1] && h[6] == LYR[2] && h[7] == LYR[3]) {
                String text = mp4DataString(src, body, bodyEnd);
                if (text != null && !text.trim().isEmpty()) r.lyricsText = text;
            } else if (type.equals("----")) {
                readFreeform(src, body, bodyEnd, r);
            }
            p += size;
        }
    }

    /** Reads the UTF-8 payload of the first "data" child atom. */
    private static String mp4DataString(ByteSource src, long start, long end) throws IOException {
        byte[] h = new byte[8];
        long p = start;
        while (p + 8 <= end) {
            src.readFully(p, h, 0, 8);
            long size = int32(h, 0);
            if (size < 8 || p + size > end) return null;
            if (new String(h, 4, 4, StandardCharsets.ISO_8859_1).equals("data") && size >= 16) {
                int n = (int) Math.min(size - 16, MAX_TEXT);
                byte[] d = new byte[n];
                src.readFully(p + 16, d, 0, n);
                return new String(d, StandardCharsets.UTF_8);
            }
            p += size;
        }
        return null;
    }

    private static void readFreeform(ByteSource src, long start, long end, Result r) throws IOException {
        String name = null;
        String value = null;
        byte[] h = new byte[8];
        long p = start;
        while (p + 8 <= end) {
            src.readFully(p, h, 0, 8);
            long size = int32(h, 0);
            if (size < 8 || p + size > end || size > 4096) return;
            String type = new String(h, 4, 4, StandardCharsets.ISO_8859_1);
            if (type.equals("name") && size > 12) {
                byte[] d = new byte[(int) size - 12];
                src.readFully(p + 12, d, 0, d.length);
                name = new String(d, StandardCharsets.UTF_8);
            } else if (type.equals("data") && size > 16) {
                byte[] d = new byte[(int) size - 16];
                src.readFully(p + 16, d, 0, d.length);
                value = new String(d, StandardCharsets.UTF_8);
            }
            p += size;
        }
        if (name != null && value != null) applyKey(name.toUpperCase(Locale.ROOT), value, r);
    }

    // ---------------------------------------------------------------- shared

    private static void applyKey(String key, String value, Result r) {
        switch (key) {
            case "LYRICS":
            case "UNSYNCEDLYRICS":
            case "UNSYNCED LYRICS":
            case "SYNCEDLYRICS":
                if (value.trim().isEmpty()) return;
                if (r.lyricsText == null || (!LrcParser.looksSynced(r.lyricsText) && LrcParser.looksSynced(value))) {
                    r.lyricsText = value;
                }
                break;
            case "REPLAYGAIN_TRACK_GAIN":
                r.trackGainDb = parseGain(value);
                break;
            case "REPLAYGAIN_ALBUM_GAIN":
                r.albumGainDb = parseGain(value);
                break;
            default:
                break;
        }
    }

    static Float parseGain(String s) {
        Matcher m = GAIN.matcher(s);
        if (!m.find()) return null;
        try {
            return Float.parseFloat(m.group(1).replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int termLen(int enc) {
        return (enc == 1 || enc == 2) ? 2 : 1;
    }

    private static int findTerminator(byte[] d, int from, int enc) {
        if (termLen(enc) == 1) {
            for (int i = from; i < d.length; i++) if (d[i] == 0) return i;
            return d.length;
        }
        for (int i = from; i + 1 < d.length; i += 2) if (d[i] == 0 && d[i + 1] == 0) return i;
        return d.length;
    }

    private static int afterTerminator(byte[] d, int from, int enc) {
        return Math.min(d.length, findTerminator(d, from, enc) + termLen(enc));
    }

    private static String decode(byte[] d, int s, int e, int enc) {
        if (e <= s) return "";
        Charset cs;
        switch (enc) {
            case 0: cs = StandardCharsets.ISO_8859_1; break;
            case 1:
                if (e - s >= 2 && (d[s] & 0xff) == 0xFF && (d[s + 1] & 0xff) == 0xFE) { s += 2; cs = StandardCharsets.UTF_16LE; }
                else if (e - s >= 2 && (d[s] & 0xff) == 0xFE && (d[s + 1] & 0xff) == 0xFF) { s += 2; cs = StandardCharsets.UTF_16BE; }
                else cs = StandardCharsets.UTF_16LE;
                break;
            case 2: cs = StandardCharsets.UTF_16BE; break;
            default: cs = StandardCharsets.UTF_8; break;
        }
        String out = new String(d, s, e - s, cs);
        int z = out.length();
        while (z > 0 && out.charAt(z - 1) == '\u0000') z--;
        return out.substring(0, z);
    }

    private static byte[] deUnsync(byte[] in) {
        byte[] out = new byte[in.length];
        int o = 0;
        for (int i = 0; i < in.length; i++) {
            out[o++] = in[i];
            if ((in[i] & 0xff) == 0xFF && i + 1 < in.length && in[i + 1] == 0) i++;
        }
        byte[] res = new byte[o];
        System.arraycopy(out, 0, res, 0, o);
        return res;
    }

    private static long syncsafe(byte[] b, int o) {
        return ((b[o] & 0x7fL) << 21) | ((b[o + 1] & 0x7fL) << 14) | ((b[o + 2] & 0x7fL) << 7) | (b[o + 3] & 0x7fL);
    }

    private static long int32(byte[] b, int o) {
        return ((b[o] & 0xffL) << 24) | ((b[o + 1] & 0xffL) << 16) | ((b[o + 2] & 0xffL) << 8) | (b[o + 3] & 0xffL);
    }

    private static long int24(byte[] b, int o) {
        return ((b[o] & 0xffL) << 16) | ((b[o + 1] & 0xffL) << 8) | (b[o + 2] & 0xffL);
    }

    private static long le32(byte[] b, int o) {
        if (o + 4 > b.length) throw new IndexOutOfBoundsException();
        return (b[o] & 0xffL) | ((b[o + 1] & 0xffL) << 8) | ((b[o + 2] & 0xffL) << 16) | ((b[o + 3] & 0xffL) << 24);
    }
}

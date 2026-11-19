"""Builds audio files with embedded lyrics using mutagen (an independent tag writer)."""
import os, struct, subprocess, sys
from mutagen.id3 import ID3, USLT, SYLT, TXXX, APIC, TIT2, ID3NoHeaderError
from mutagen.flac import FLAC
from mutagen.mp4 import MP4, MP4FreeForm

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
LRC = "[ar:Coastal Hours]\n[00:41.20]Streetlights counting down the miles\n[00:48.5]Radio low, the window wide\n[00:56.00][01:40.00]You said the night was ours to keep\n"

def ff(name, ext_args):
    p = os.path.join(OUT, name)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=2"] + ext_args + [p], check=True)
    return p

# 1. MP3, ID3v2.4, UTF-8 USLT holding LRC, ReplayGain, and 300 KB of cover art to skip over.
p = ff("v24_uslt_lrc.mp3", ["-c:a", "libmp3lame"])
t = ID3()
t.add(TIT2(encoding=3, text="Midnight Ride"))
t.add(APIC(encoding=3, mime="image/jpeg", type=3, desc="cover", data=os.urandom(300_000)))
t.add(USLT(encoding=3, lang="eng", desc="", text=LRC))
t.add(TXXX(encoding=3, desc="REPLAYGAIN_TRACK_GAIN", text="-6.48 dB"))
t.save(p, v2_version=4)

# 2. MP3, ID3v2.3, UTF-16 plain lyrics with a descriptor.
p = ff("v23_uslt_utf16.mp3", ["-c:a", "libmp3lame"])
t = ID3()
t.add(USLT(encoding=1, lang="eng", desc="Lyrics", text="Line one\nলাইন দুই\nLine three"))
t.save(p, v2_version=3)

# 3. MP3, ID3v2.3, SYLT in milliseconds (UTF-16, so every string carries its own BOM).
p = ff("v23_sylt.mp3", ["-c:a", "libmp3lame"])
t = ID3()
t.add(SYLT(encoding=1, lang="eng", format=2, type=1, desc="", text=[("First", 1000), ("Second", 2500), ("Third", 4000)]))
t.save(p, v2_version=3)

# 4. FLAC with LRC in LYRICS, an [offset] tag and both ReplayGain values.
p = ff("lrc.flac", ["-c:a", "flac"])
f = FLAC(p)
f["LYRICS"] = "[offset:+500]\n[00:10.00]Ten\n[00:20.00]Twenty"
f["REPLAYGAIN_TRACK_GAIN"] = "-7,25 dB"
f["REPLAYGAIN_ALBUM_GAIN"] = "+1.10 dB"
f.save()

# 5. M4A (AAC) with plain ©lyr and an iTunes freeform ReplayGain atom.
p = ff("lyr.m4a", ["-c:a", "aac"])
m = MP4(p)
m["\xa9lyr"] = ["Plain line A\nPlain line B"]
m["----:com.apple.iTunes:replaygain_track_gain"] = [MP4FreeForm(b"-3.20 dB")]
m.save()

# 6. Hand-built ID3v2.3 with whole-tag unsynchronisation (old iTunes style).
def unsync(b):
    out = bytearray()
    for i, x in enumerate(b):
        out.append(x)
        if x == 0xFF and (i + 1 == len(b) or b[i + 1] >= 0xE0 or b[i + 1] == 0):
            out.append(0)
    return bytes(out)

def syncsafe(n):
    return bytes([(n >> 21) & 0x7F, (n >> 14) & 0x7F, (n >> 7) & 0x7F, n & 0x7F])

text = "caf\xe9 \xff\xe9 end".encode("latin-1")          # contains 0xFF followed by 0xE9
frame_data = b"\x00eng\x00" + text
frame = b"USLT" + struct.pack(">I", len(frame_data)) + b"\x00\x00" + frame_data
body = unsync(frame)
tag = b"ID3\x03\x00\x80" + syncsafe(len(body)) + body
open(os.path.join(OUT, "v23_unsync.mp3"), "wb").write(tag + b"\xff\xfb\x90\x00" + b"\x00" * 400)

# 7. Hand-built ID3v2.4 frame with per-frame unsync + data length indicator flags.
data = b"\x03eng\x00" + "xÿéy".encode("utf-8") + b"\xff\xe0z"
dli = syncsafe(len(data))
fdata = dli + unsync(data)
frame = b"USLT" + syncsafe(len(fdata)) + b"\x00" + bytes([0x02 | 0x01]) + fdata
tag = b"ID3\x04\x00\x00" + syncsafe(len(frame) + 20) + frame + b"\x00" * 20
open(os.path.join(OUT, "v24_frame_unsync.mp3"), "wb").write(tag + b"\xff\xfb\x90\x00" + b"\x00" * 400)
print("fixtures ok:", sorted(os.listdir(OUT)))

"""Kairos icon: a record in the album-colour glow. Writes one SVG master and the three Android
adaptive-icon layers from the same geometry (108 x 108 viewport, safe zone = 33 radius circle)."""
import math, sys
out = sys.argv[1]
C = 54.0
R = 24.0          # disc radius: leaves a ring of the colour glow visible around the record
LR = 9.6          # label radius
MS = LR / 11.5    # the K mark was drawn for an 11.5 label; scaled about the centre

def circle(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,1 {2*r:.2f},0a{r:.2f},{r:.2f} 0 1,1 {-2*r:.2f},0z"

def ring(r, w):
    # a ring as an even-odd pair of circles
    return circle(C, C, r + w / 2) + circle(C, C, r - w / 2)

def sector(a0, a1, r0, r1):
    def p(a, r): return C + r * math.cos(math.radians(a)), C + r * math.sin(math.radians(a))
    x0, y0 = p(a0, r1); x1, y1 = p(a1, r1); x2, y2 = p(a1, r0); x3, y3 = p(a0, r0)
    return (f"M{x0:.2f},{y0:.2f}A{r1},{r1} 0 0,1 {x1:.2f},{y1:.2f}L{x2:.2f},{y2:.2f}"
            f"A{r0},{r0} 0 0,0 {x3:.2f},{y3:.2f}z")

# The K mark on the label: a stem and a play triangle as its arms (the brand mark, kept).
STEM = "M48.6,48.9a1.3,1.3 0 0,1 1.3,-1.3h0.9a1.3,1.3 0 0,1 1.3,1.3v10.2a1.3,1.3 0 0,1 -1.3,1.3h-0.9a1.3,1.3 0 0,1 -1.3,-1.3z"
TRI = "M53.4,49.6c0,-1 1.1,-1.6 1.9,-1.1l6.0,3.9c0.8,0.5 0.8,1.7 0,2.2l-6.0,3.9c-0.8,0.5 -1.9,-0.1 -1.9,-1.1z"


import re
def scale_path(d, s, cx=54.0, cy=54.0):
    """Scales an SVG path about (cx, cy): absolute points move, relative steps and radii shrink."""
    out = []
    for cmd, args in re.findall(r"([MmLlHhVvCcAaZz])([^MmLlHhVvCcAaZz]*)", d):
        nums = [float(x) for x in re.findall(r"-?\d*\.?\d+(?:e-?\d+)?", args)]
        if cmd in "Zz":
            out.append(cmd); continue
        if cmd == "M" or cmd == "L" or cmd == "C":
            nums = [cx + (v - cx) * s if i % 2 == 0 else cy + (v - cy) * s for i, v in enumerate(nums)]
        elif cmd in "Aa":
            res = []
            for i in range(0, len(nums), 7):
                rx, ry, rot, la, sw, x, y = nums[i:i + 7]
                if cmd == "A": x, y = cx + (x - cx) * s, cy + (y - cy) * s
                else: x, y = x * s, y * s
                res += [rx * s, ry * s, rot, int(la), int(sw), x, y]
            out.append(cmd + " ".join(f"{v:.3f}" if isinstance(v, float) else str(v) for v in res)); continue
        else:
            nums = [v * s for v in nums]
        out.append(cmd + " ".join(f"{v:.3f}" for v in nums))
    return "".join(out)

STEM_S = scale_path(STEM, MS)
TRI_S = scale_path(TRI, MS)

grooves = []
r = LR + 3.0
k = 0
while r < R - 1.5:
    grooves.append((r, 0.12 if k % 4 == 0 else 0.055))
    r += 1.9; k += 1

# ---------------- SVG master
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="1024" height="1024">
<defs>
 <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
  <stop offset="0" stop-color="#2A0F5C"/><stop offset="0.55" stop-color="#8A1E78"/><stop offset="1" stop-color="#F0713C"/>
 </linearGradient>
 <radialGradient id="blobA" cx="26" cy="24" r="42" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#FF5FA8" stop-opacity="0.75"/><stop offset="1" stop-color="#FF5FA8" stop-opacity="0"/>
 </radialGradient>
 <radialGradient id="blobB" cx="90" cy="94" r="40" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#FFC04D" stop-opacity="0.7"/><stop offset="1" stop-color="#FFC04D" stop-opacity="0"/>
 </radialGradient>
 <radialGradient id="shadow" cx="{C+2}" cy="{C+3.5}" r="{R+4}" gradientUnits="userSpaceOnUse">
  <stop offset="0.82" stop-color="#000" stop-opacity="0.45"/><stop offset="1" stop-color="#000" stop-opacity="0"/>
 </radialGradient>
 <radialGradient id="vinyl" cx="{C-6}" cy="{C-8}" r="{R+6}" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#26242C"/><stop offset="1" stop-color="#08080A"/>
 </radialGradient>
 <linearGradient id="label" x1="0" y1="0" x2="1" y2="1">
  <stop offset="0" stop-color="#FF5FA8"/><stop offset="1" stop-color="#FFB547"/>
 </linearGradient>
</defs>
<rect width="108" height="108" fill="url(#bg)"/>
<rect width="108" height="108" fill="url(#blobA)"/>
<rect width="108" height="108" fill="url(#blobB)"/>
<path d="{circle(C+2, C+3.5, R+4)}" fill="url(#shadow)"/>
<path d="{circle(C, C, R)}" fill="url(#vinyl)"/>
''' + "".join(f'<path d="{ring(rr, 0.45)}" fill="#FFFFFF" fill-opacity="{a}" fill-rule="evenodd"/>\n' for rr, a in grooves) + f'''
<path d="{sector(-62, -30, LR + 1.5, R - 0.8)}" fill="#FFFFFF" fill-opacity="0.09"/>
<path d="{sector(118, 140, LR + 1.5, R - 0.8)}" fill="#FFFFFF" fill-opacity="0.055"/>
<path d="{circle(C, C, LR)}" fill="url(#label)"/>
<path d="{ring(LR - 0.3, 0.5)}" fill="#000" fill-opacity="0.18" fill-rule="evenodd"/>
<g transform="translate({C} {C}) scale({MS:.4f}) translate({-C} {-C})"><path d="{STEM}" fill="#17121F"/><path d="{TRI}" fill="#17121F"/></g>
</svg>'''
open(f"{out}/icon.svg", "w").write(svg)

# ---------------- Android layers
NS = 'xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"'
HEAD = '<vector {ns}\n    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'.format(ns=NS)

def grad_path(d, grad_xml):
    return f'''    <path android:pathData="{d}">
        <aapt:attr name="android:fillColor">
{grad_xml}
        </aapt:attr>
    </path>
'''

def lin(x1, y1, x2, y2, stops):
    s = "".join(f'                <item android:offset="{o}" android:color="{c}" />\n' for o, c in stops)
    return f'            <gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}">\n{s}            </gradient>'

def rad(cx, cy, r, stops):
    s = "".join(f'                <item android:offset="{o}" android:color="{c}" />\n' for o, c in stops)
    return f'            <gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}">\n{s}            </gradient>'

full = "M0,0h108v108h-108z"
bg = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Kairos icon background: the album-art glow in miniature (deep violet to magenta to amber, two soft lights). -->\n'
      + HEAD
      + grad_path(full, lin(0, 0, 108, 108, [(0, "#FF2A0F5C"), (0.55, "#FF8A1E78"), (1, "#FFF0713C")]))
      + grad_path(full, rad(26, 24, 42, [(0, "#BFFF5FA8"), (1, "#00FF5FA8")]))
      + grad_path(full, rad(90, 94, 40, [(0, "#B3FFC04D"), (1, "#00FFC04D")]))
      + "</vector>\n")
fg = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Kairos icon: a record (grooves, two still reflections) whose label carries the K mark,\n     a stem with a play triangle for arms. Drawn from docs/store/icon.svg\'s geometry. -->\n'
      + HEAD
      + grad_path(circle(C + 2, C + 3.5, R + 4), rad(C + 2, C + 3.5, R + 4, [(0.82, "#73000000"), (1, "#00000000")]))
      + grad_path(circle(C, C, R), rad(C - 6, C - 8, R + 6, [(0, "#FF26242C"), (1, "#FF08080A")]))
      + "".join(f'    <path android:fillType="evenOdd" android:fillColor="#{int(a*255):02X}FFFFFF" android:pathData="{ring(rr, 0.45)}" />\n' for rr, a in grooves)
      + f'    <path android:fillColor="#17FFFFFF" android:pathData="{sector(-62, -30, LR + 1.5, R - 0.8)}" />\n'
      + f'    <path android:fillColor="#0EFFFFFF" android:pathData="{sector(118, 140, LR + 1.5, R - 0.8)}" />\n'
      + grad_path(circle(C, C, LR), lin(C - LR, C - LR, C + LR, C + LR, [(0, "#FFFF5FA8"), (1, "#FFFFB547")]))
      + f'    <path android:fillType="evenOdd" android:fillColor="#2E000000" android:pathData="{ring(LR - 0.3, 0.5)}" />\n'
      + f'    <group android:pivotX="{C}" android:pivotY="{C}" android:scaleX="{MS:.4f}" android:scaleY="{MS:.4f}">\n'
      + f'        <path android:fillColor="#FF17121F" android:pathData="{STEM}" />\n'
      + f'        <path android:fillColor="#FF17121F" android:pathData="{TRI}" />\n'
      + '    </group>\n'
      + "</vector>\n")
mono = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Themed icon (Android 13+): the record in one colour, the K cut out of the label. -->\n'
        + HEAD
        + f'    <path android:fillType="evenOdd" android:fillColor="#FFFFFFFF" android:pathData="{ring(R - 1.2, 2.4)}" />\n'
        + f'    <path android:fillType="evenOdd" android:fillColor="#FFFFFFFF" android:pathData="{ring(R - 6, 1.1)}" />\n'
        + f'    <path android:fillType="evenOdd" android:fillColor="#FFFFFFFF" android:pathData="{ring(R - 10, 1.1)}" />\n'
        + f'    <path android:fillType="evenOdd" android:fillColor="#FFFFFFFF" android:pathData="{circle(C, C, LR)}{STEM_S}{TRI_S}" />\n'
        + "</vector>\n")
open(f"{out}/ic_launcher_background.xml", "w").write(bg)
open(f"{out}/ic_launcher_foreground.xml", "w").write(fg)
open(f"{out}/ic_launcher_monochrome.xml", "w").write(mono)
# a monochrome SVG twin for the preview
mono_svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512"><rect width="108" height="108" fill="#1F2A24"/>
<g fill="#CFE8D6" fill-rule="evenodd"><path d="{ring(R-1.2,2.4)}"/><path d="{ring(R-6,1.1)}"/><path d="{ring(R-10,1.1)}"/><path d="{circle(C,C,LR)}{STEM_S}{TRI_S}"/></g></svg>'''
open(f"{out}/mono.svg", "w").write(mono_svg)

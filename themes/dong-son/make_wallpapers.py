#!/usr/bin/env python3
"""Draw the dong-son theme's wallpapers: a stylised Dong Son bronze drum face, our own drawing (CC0).

The face sits right of centre on near-black, so the clock, greeting and buttons on the left stay readable:
a 14-point star with feathered rays at the centre, concentric bands of hatching, circles joined by tangent
lines, zigzags and ladders, a ring of fourteen Lac birds with long beaks and spread wings flying
counter-clockwise, and an outer ring of saw-tooth ornaments. Three palettes: copper (morning), antique gold
(evening) and verdigris (night). Line art on flat fills, which survives the launcher's RGB_565 decode
without banding.

    python3 make_wallpapers.py          # writes wallpapers/*.jpg and screenshot.jpg (a placeholder)

Needs Pillow only. The same code gives the same pictures.
"""
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
W, H = 1920, 1080
S = 2                      # supersampling: draw at 2x, then scale down for smooth edges
CX, CY, R = 1330, 540, 500  # the drum face, in 1x pixels

PALETTES = {
    "01-dong-son-copper.jpg": dict(bg=(16, 11, 8), face=(38, 24, 14), band=(30, 19, 11),
                                   line=(176, 112, 60), bright=(214, 150, 88), dim=(110, 70, 38)),
    "02-dong-son-gold.jpg": dict(bg=(14, 11, 8), face=(36, 27, 14), band=(27, 20, 10),
                                 line=(196, 160, 82), bright=(232, 200, 120), dim=(120, 94, 46)),
    "03-dong-son-verdigris.jpg": dict(bg=(9, 12, 11), face=(17, 33, 29), band=(13, 25, 22),
                                      line=(84, 158, 138), bright=(128, 196, 172), dim=(52, 98, 86)),
}


def P(x, y):
    """1x coordinates to the supersampled canvas."""
    return (x * S, y * S)


def polar(r, a):
    return (CX + r * math.cos(a), CY + r * math.sin(a))


class Face:
    def __init__(self, draw, pal):
        self.d = draw
        self.p = pal

    def ring(self, r, width, colour):
        self.d.ellipse([P(CX - r, CY - r), P(CX + r, CY + r)], outline=colour, width=max(1, round(width * S)))

    def disc(self, r, colour):
        self.d.ellipse([P(CX - r, CY - r), P(CX + r, CY + r)], fill=colour)

    def line(self, a, b, colour, width=1.4):
        self.d.line([P(*a), P(*b)], fill=colour, width=max(1, round(width * S)))

    def poly(self, pts, colour):
        self.d.polygon([P(*q) for q in pts], fill=colour)

    def circle(self, c, r, colour, width=1.4, fill=None):
        x, y = c
        self.d.ellipse([P(x - r, y - r), P(x + r, y + r)], outline=colour, fill=fill, width=max(1, round(width * S)))

    # ---- bands, inside radius r0 and outside r1 (fractions of R) ---------------------------------------------

    def border(self, r0, r1):
        self.ring(r0 * R, 1.6, self.p["line"])
        self.ring(r1 * R, 1.6, self.p["line"])

    def hatched(self, r0, r1, n, slant=0.0):
        """Short strokes across the band, optionally slanted."""
        self.border(r0, r1)
        for i in range(n):
            a = 2 * math.pi * i / n
            self.line(polar(r0 * R + 2, a), polar(r1 * R - 2, a + slant), self.p["dim"], 1.2)

    def ladder(self, r0, r1, n):
        """Two rails with rungs: the ladder band."""
        self.border(r0, r1)
        for i in range(n):
            a = 2 * math.pi * i / n
            self.line(polar(r0 * R, a), polar(r1 * R, a), self.p["line"], 1.4)

    def zigzag(self, r0, r1, n):
        self.border(r0, r1)
        pts = []
        for i in range(2 * n + 1):
            a = math.pi * i / n
            pts.append(polar((r0 if i % 2 == 0 else r1) * R + (2 if i % 2 == 0 else -2), a))
        for a, b in zip(pts, pts[1:]):
            self.line(a, b, self.p["line"], 1.6)

    def circles(self, r0, r1, n):
        """Circles with a dot at the centre, each joined to the next by two tangent lines."""
        self.border(r0, r1)
        rc = (r0 + r1) / 2 * R
        cr = (r1 - r0) * R * 0.32
        centres = [(polar(rc, 2 * math.pi * i / n), 2 * math.pi * i / n) for i in range(n)]
        for i, (c, a) in enumerate(centres):
            self.circle(c, cr, self.p["line"], 1.4)
            self.circle(c, cr * 0.28, self.p["bright"], 1, fill=self.p["bright"])
            nc, na = centres[(i + 1) % n]
            # tangents: from this circle's outer side to the next one's inner side, and the reverse
            ox, oy = math.cos(a) * cr, math.sin(a) * cr
            self.line((c[0] + ox, c[1] + oy), (nc[0] - math.cos(na) * cr, nc[1] - math.sin(na) * cr), self.p["dim"], 1.2)
            self.line((c[0] - ox, c[1] - oy), (nc[0] + math.cos(na) * cr, nc[1] + math.sin(na) * cr), self.p["dim"], 1.2)

    def chevrons(self, r0, r1, n):
        """Rows of nested chevrons pointing counter-clockwise, the band's feathered dancers made geometric."""
        self.border(r0, r1)
        span = 2 * math.pi / n
        for i in range(n):
            a = span * i
            for k in range(3):
                tip = a - span * (0.15 + 0.22 * k)
                back = tip + span * 0.32
                mid = (r0 + r1) / 2 * R
                self.line(polar(r0 * R + 4, back), polar(mid, tip), self.p["line"], 1.5)
                self.line(polar(mid, tip), polar(r1 * R - 4, back), self.p["line"], 1.5)

    def sawtooth(self, r0, r1, n):
        """Triangles pointing outward, filled, on the outer ring."""
        self.border(r0, r1)
        for i in range(n):
            a = 2 * math.pi * i / n
            h = math.pi / n * 0.8
            self.poly([polar(r0 * R + 2, a - h), polar(r0 * R + 2, a + h), polar(r1 * R - 2, a)], self.p["dim"])

    # ---- centre star -------------------------------------------------------------------------------------

    def star14(self, r0, r1):
        inner, outer = r0 * R, r1 * R
        self.disc(inner, self.p["bright"])
        n = 14
        for i in range(n):
            a = 2 * math.pi * i / n - math.pi / 2
            half = math.pi / n * 0.62
            self.poly([polar(inner * 0.9, a - half), polar(outer, a), polar(inner * 0.9, a + half)], self.p["bright"])
            # feathers between this ray and the next: nested V shapes opening towards the centre
            g = a + math.pi / n
            for k in range(3):
                rr = inner + (outer - inner) * (0.35 + 0.22 * k)
                w = math.pi / n * (0.25 + 0.12 * k)
                self.line(polar(rr - 12, g - w), polar(rr, g), self.p["line"], 1.4)
                self.line(polar(rr, g), polar(rr - 12, g + w), self.p["line"], 1.4)
        self.ring(outer + 4, 1.6, self.p["line"])

    # ---- birds -------------------------------------------------------------------------------------------

    def bird(self, a, rad, size):
        """A Lac bird flying counter-clockwise at angle a: long beak, crest, spread wings and a long tail."""
        px, py = polar(rad, a)
        dx, dy = math.sin(a), -math.cos(a)        # heading: counter-clockwise on screen (y points down)
        nx, ny = -dy, dx                           # towards the outside of the drum

        def w(lx, ly):
            return (px + (lx * dx + ly * nx) * size, py + (lx * dy + ly * ny) * size)

        col = self.p["bright"]
        # long neck and body, tapering to the tail
        self.poly([w(0.36, 0.03), w(0.12, 0.06), w(-0.22, 0.09), w(-0.42, 0.04), w(-0.42, -0.04),
                   w(-0.22, -0.09), w(0.12, -0.06), w(0.36, -0.03)], col)
        # head and the long beak
        self.circle(w(0.4, 0.0), 0.06 * size, col, 1, fill=col)
        self.poly([w(0.44, 0.028), w(0.86, 0.0), w(0.44, -0.028)], col)
        # crest swept back from the head
        self.line(w(0.38, 0.04), w(0.24, 0.17), col, 1.5)
        self.line(w(0.36, 0.05), w(0.28, 0.2), col, 1.1)
        # wings spread to both sides, swept back, with a serrated trailing edge for the feathers
        for side in (1, -1):
            self.poly([w(0.1, 0.06 * side), w(-0.02, 0.44 * side), w(-0.1, 0.46 * side), w(-0.12, 0.36 * side),
                       w(-0.18, 0.38 * side), w(-0.19, 0.27 * side), w(-0.25, 0.28 * side), w(-0.24, 0.17 * side),
                       w(-0.3, 0.16 * side), w(-0.2, 0.06 * side)], col)
        # tail: a fan of three long feathers
        for end in (-0.13, 0.0, 0.13):
            self.line(w(-0.4, end * 0.2), w(-0.72, end), col, 1.5)

    def birds(self, r0, r1, n):
        self.border(r0, r1)
        mid = (r0 + r1) / 2 * R
        size = (r1 - r0) * R * 0.68
        for i in range(n):
            self.bird(2 * math.pi * i / n, mid, size)


def draw_face(img, pal):
    d = ImageDraw.Draw(img)
    f = Face(d, pal)
    f.disc(R, pal["face"])
    f.ring(R - 3, 3, pal["dim"])
    f.ring(R - 10, 1.4, pal["line"])
    # rings from the rim inwards, each band a little darker than the face so the motifs read as cast relief
    f.disc(0.985 * R, pal["band"])
    f.sawtooth(0.92, 0.975, 72)
    f.hatched(0.885, 0.92, 180)
    f.circles(0.82, 0.885, 40)
    f.ladder(0.79, 0.82, 120)
    f.birds(0.55, 0.79, 14)
    f.ladder(0.52, 0.55, 96)
    f.chevrons(0.40, 0.52, 22)
    f.zigzag(0.36, 0.40, 36)
    f.circles(0.29, 0.36, 22)
    f.hatched(0.26, 0.29, 84, slant=0.03)
    f.disc(0.255 * R, pal["face"])
    f.star14(0.07, 0.22)


def picture(name, pal):
    img = Image.new("RGB", (W * S, H * S), pal["bg"])
    draw_face(img, pal)
    img = img.resize((W, H), Image.LANCZOS)
    # A faint shade over the face's left edge and under the dock keeps the UI on top easy to read.
    left = Image.new("L", (W, H), 0)
    for i in range(24):
        ImageDraw.Draw(left).rectangle([0, 0, 760 + (23 - i) * 12, H], fill=5 * (i + 1))
    bottom = Image.new("L", (W, H), 0)
    for i in range(16):
        ImageDraw.Draw(bottom).rectangle([0, 900 + i * 6, W, H], fill=40 + i * 4)
    shade = ImageChops.lighter(left, bottom)
    img = Image.composite(Image.new("RGB", (W, H), pal["bg"]), img, shade)
    return img


def main():
    out = HERE / "wallpapers"
    out.mkdir(exist_ok=True)
    pictures = {}
    for name, pal in PALETTES.items():
        img = picture(name, pal)
        img.save(out / name, "JPEG", quality=82, optimize=True, progressive=False)   # baseline: faster to decode on the TV
        pictures[name] = img
        print(name, (out / name).stat().st_size // 1024, "KB")
    # A placeholder until a screenshot from the TV replaces it (themes/README.md asks for a real one).
    shot = pictures["02-dong-son-gold.jpg"].resize((1280, 720), Image.LANCZOS)
    ImageDraw.Draw(shot).text((24, 690), "placeholder: replace with a screenshot from the TV",
                              fill=(200, 200, 200), font=ImageFont.load_default())
    shot.save(HERE / "screenshot.jpg", "JPEG", quality=82, optimize=True)


if __name__ == "__main__":
    main()

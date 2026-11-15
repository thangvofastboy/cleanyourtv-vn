#!/usr/bin/env python3
"""Draw the dong-son theme's wallpapers: a stylised Dong Son bronze drum face, our own drawing (CC0).

The face sits right of centre on near-black, so the clock, greeting and buttons on the left stay readable:
a faceted 14-point sun star with feathered rays at the centre, concentric bands of hatching, tangent-joined
eye circles, zigzags, ladders and chevrons, a ring of fourteen graceful Lac birds flying counter-clockwise,
and an outer saw-tooth rim with subtle metallic sheen and atmospheric glow. Three palettes: copper (morning),
antique gold (evening) and verdigris (night). Smoothly dithered gradients survive the launcher's RGB_565
decode without banding.

    python3 make_wallpapers.py          # writes wallpapers/*.jpg and screenshot.jpg (a placeholder)

Needs Pillow only. The same code gives the same pictures.
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
W, H = 1920, 1080
S = 2                      # supersampling: draw at 2x, then scale down for smooth edges
CX, CY, R = 1320, 680, 475  # the drum face, in 1x pixels: clears the top buttons and frames the right

PALETTES = {
    "01-dong-son-copper.jpg": dict(
        bg=(11, 8, 6), glow=(48, 27, 15), face=(34, 21, 13), band=(23, 14, 9),
        line=(182, 114, 60), mid=(142, 86, 44), bright=(234, 172, 106), dim=(92, 56, 30),
    ),
    "02-dong-son-gold.jpg": dict(
        bg=(10, 9, 6), glow=(46, 35, 14), face=(32, 24, 12), band=(22, 16, 8),
        line=(202, 162, 78), mid=(158, 124, 56), bright=(246, 212, 124), dim=(102, 80, 38),
    ),
    "03-dong-son-verdigris.jpg": dict(
        bg=(7, 10, 10), glow=(15, 38, 33), face=(14, 29, 25), band=(9, 20, 17),
        line=(84, 164, 140), mid=(62, 128, 108), bright=(142, 216, 188), dim=(44, 88, 76),
    ),
}


def P(x, y):
    """1x coordinates to the supersampled canvas."""
    return (x * S, y * S)


def polar(r, a):
    return (CX + r * math.cos(a), CY + r * math.sin(a))


def bezier_cubic(p0, p1, p2, p3, steps=16):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        u = 1.0 - t
        x = u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0]
        y = u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def mix(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


class Face:
    def __init__(self, draw, pal):
        self.d = draw
        self.p = pal

    def ring(self, r, width, colour):
        self.d.ellipse([P(CX - r, CY - r), P(CX + r, CY + r)],
                       outline=colour, width=max(1, round(width * S)))

    def disc(self, r, colour):
        self.d.ellipse([P(CX - r, CY - r), P(CX + r, CY + r)], fill=colour)

    def line(self, a, b, colour, width=1.4):
        self.d.line([P(*a), P(*b)], fill=colour, width=max(1, round(width * S)))

    def poly(self, pts, colour, outline=None):
        self.d.polygon([P(*q) for q in pts], fill=colour, outline=outline)

    def circle(self, c, r, colour, width=1.4, fill=None):
        x, y = c
        self.d.ellipse([P(x - r, y - r), P(x + r, y + r)],
                       outline=colour, fill=fill, width=max(1, round(width * S)))

    def dot(self, c, r, colour):
        x, y = c
        self.d.ellipse([P(x - r, y - r), P(x + r, y + r)], fill=colour)

    # ---- bands, inside radius r0 and outside r1 (fractions of R) ----

    def border(self, r0, r1, w=1.4):
        self.ring(r0 * R, w, self.p["line"])
        self.ring(r1 * R, w, self.p["line"])

    def beaded(self, r_mid, n, dot_r=1.8):
        for i in range(n):
            a = 2 * math.pi * i / n
            self.dot(polar(r_mid * R, a), dot_r, self.p["bright"] if i % 2 == 0 else self.p["line"])

    def hatched(self, r0, r1, n, slant=0.0):
        self.border(r0, r1, 1.2)
        for i in range(n):
            a = 2 * math.pi * i / n
            self.line(polar(r0 * R + 2, a), polar(r1 * R - 2, a + slant), self.p["dim"], 1.15)

    def ladder(self, r0, r1, n):
        self.border(r0, r1, 1.3)
        for i in range(n):
            a = 2 * math.pi * i / n
            self.line(polar(r0 * R, a), polar(r1 * R, a), self.p["mid"], 1.2)

    def zigzag(self, r0, r1, n):
        self.border(r0, r1, 1.3)
        pts = []
        for i in range(2 * n + 1):
            a = math.pi * i / n
            pts.append(polar((r0 if i % 2 == 0 else r1) * R + (2.5 if i % 2 == 0 else -2.5), a))
        for a, b in zip(pts, pts[1:]):
            self.line(a, b, self.p["line"], 1.5)

    def circles(self, r0, r1, n):
        """Concentric eye-circles with a luminous centre dot, joined by tangent lines."""
        self.border(r0, r1, 1.3)
        rc = (r0 + r1) / 2 * R
        cr = (r1 - r0) * R * 0.33
        centres = [(polar(rc, 2 * math.pi * i / n), 2 * math.pi * i / n) for i in range(n)]
        for i, (c, a) in enumerate(centres):
            nc, na = centres[(i + 1) % n]
            ox, oy = math.cos(a) * cr, math.sin(a) * cr
            self.line((c[0] + ox, c[1] + oy), (nc[0] - math.cos(na) * cr, nc[1] - math.sin(na) * cr), self.p["dim"], 1.1)
            self.line((c[0] - ox, c[1] - oy), (nc[0] + math.cos(na) * cr, nc[1] + math.sin(na) * cr), self.p["dim"], 1.1)
        for c, _ in centres:
            self.circle(c, cr, self.p["line"], 1.4, fill=self.p["band"])
            self.circle(c, cr * 0.62, self.p["mid"], 1.0)
            self.dot(c, cr * 0.28, self.p["bright"])

    def chevrons(self, r0, r1, n):
        """Nested chevrons pointing counter-clockwise, with alternating stroke weight."""
        self.border(r0, r1, 1.3)
        span = 2 * math.pi / n
        mid = (r0 + r1) / 2 * R
        for i in range(n):
            a = span * i
            for k in range(3):
                tip = a - span * (0.14 + 0.23 * k)
                back = tip + span * 0.32
                col = self.p["bright"] if k == 0 else (self.p["line"] if k == 1 else self.p["mid"])
                w = 1.6 if k == 0 else 1.25
                self.line(polar(r0 * R + 3.5, back), polar(mid, tip), col, w)
                self.line(polar(mid, tip), polar(r1 * R - 3.5, back), col, w)

    def sawtooth(self, r0, r1, n):
        """Faceted two-tone triangles pointing outward on the outer rim."""
        self.border(r0, r1, 1.4)
        for i in range(n):
            a = 2 * math.pi * i / n
            h = math.pi / n * 0.84
            p_l = polar(r0 * R + 1.5, a - h)
            p_r = polar(r0 * R + 1.5, a + h)
            p_tip = polar(r1 * R - 1.5, a)
            p_mid = polar(r0 * R + 1.5, a)
            self.poly([p_l, p_tip, p_mid], self.p["mid"])
            self.poly([p_mid, p_tip, p_r], self.p["dim"])
            self.line(p_l, p_tip, self.p["line"], 0.9)
            self.line(p_tip, p_r, self.p["dim"], 0.9)

    # ---- faceted 14-point centre sun star ----

    def star14(self, r0, r1):
        inner, outer = r0 * R, r1 * R
        n = 14
        for i in range(n):
            a = 2 * math.pi * i / n - math.pi / 2
            half = math.pi / n * 0.64
            base_l = polar(inner * 0.88, a - half)
            base_r = polar(inner * 0.88, a + half)
            tip = polar(outer, a)
            center = (CX, CY)
            # Two-tone faceted ray for subtle 3D cast-bronze relief
            self.poly([center, base_l, tip], self.p["bright"])
            self.poly([center, tip, base_r], self.p["line"])
            # Feathered V-motifs between rays
            g = a + math.pi / n
            for k in range(3):
                rr = inner + (outer - inner) * (0.34 + 0.22 * k)
                w = math.pi / n * (0.24 + 0.11 * k)
                col = self.p["bright"] if k == 2 else self.p["line"]
                self.line(polar(rr - 13, g - w), polar(rr, g), col, 1.3)
                self.line(polar(rr, g), polar(rr - 13, g + w), col, 1.3)
        # Central sun boss with delicate inner ring
        self.disc(inner, self.p["bright"])
        self.ring(inner * 0.72, 1.4, self.p["face"])
        self.disc(inner * 0.38, self.p["line"])
        self.ring(outer + 5, 1.6, self.p["line"])
        self.ring(outer + 9, 0.9, self.p["dim"])

    # ---- graceful stylised Lac birds ----

    def bird(self, a, rad, size):
        """A stylised Lac bird flying counter-clockwise with sweeping curved wings, arched neck, and tail plumes."""
        px, py = polar(rad, a)
        dx, dy = math.sin(a), -math.cos(a)        # counter-clockwise heading
        nx, ny = -dy, dx                           # outward radial normal

        def w(lx, ly):
            return (px + (lx * dx + ly * nx) * size, py + (lx * dy + ly * ny) * size)

        bright = self.p["bright"]
        mid = self.p["line"]

        # Sleek curved body & arched neck
        body_pts = (
            bezier_cubic(w(0.42, 0.025), w(0.26, 0.05), w(0.05, 0.08), w(-0.22, 0.075), 10)
            + bezier_cubic(w(-0.22, 0.075), w(-0.34, 0.06), w(-0.40, 0.03), w(-0.44, 0.02), 6)
            + [w(-0.44, -0.02)]
            + bezier_cubic(w(-0.44, -0.02), w(-0.36, -0.045), w(-0.26, -0.075), w(-0.20, -0.075), 6)
            + bezier_cubic(w(-0.20, -0.075), w(0.05, -0.08), w(0.26, -0.05), w(0.42, -0.025), 10)
        )
        self.poly(body_pts, bright)

        # Head & long tapered beak
        self.dot(w(0.44, 0.0), 0.052 * size, bright)
        self.dot(w(0.445, 0.0), 0.018 * size, self.p["band"])  # subtle eye dot
        self.poly([w(0.47, 0.026), w(0.88, 0.0), w(0.47, -0.026)], bright)

        # Swept-back graceful crest plumes
        crest1 = bezier_cubic(w(0.42, 0.03), w(0.35, 0.13), w(0.28, 0.17), w(0.18, 0.21), 10)
        for p0, p1 in zip(crest1, crest1[1:]):
            self.line(p0, p1, bright, 1.45)
        crest2 = bezier_cubic(w(0.39, 0.04), w(0.34, 0.10), w(0.30, 0.13), w(0.24, 0.16), 8)
        for p0, p1 in zip(crest2, crest2[1:]):
            self.line(p0, p1, mid, 1.1)

        # Sweeping curved wings with stylised geometric feather tips
        for side in (1, -1):
            lead = bezier_cubic(
                w(0.12, 0.05 * side),
                w(0.08, 0.24 * side),
                w(-0.01, 0.40 * side),
                w(-0.12, 0.49 * side),
                12,
            )
            trail = [
                w(-0.14, 0.39 * side),
                w(-0.19, 0.41 * side),
                w(-0.20, 0.30 * side),
                w(-0.26, 0.31 * side),
                w(-0.25, 0.20 * side),
                w(-0.31, 0.20 * side),
                w(-0.22, 0.06 * side),
            ]
            self.poly(lead + trail, bright if side == 1 else mix(bright, mid, 0.25))

        # Three flowing curved tail plumes
        for end_y, col, lw in [(-0.14, mid, 1.3), (0.0, bright, 1.5), (0.14, mid, 1.3)]:
            tail = bezier_cubic(w(-0.42, end_y * 0.15), w(-0.54, end_y * 0.45),
                                w(-0.64, end_y * 0.8), w(-0.75, end_y), 8)
            for p0, p1 in zip(tail, tail[1:]):
                self.line(p0, p1, col, lw)

    def birds(self, r0, r1, n):
        self.border(r0, r1, 1.4)
        mid = (r0 + r1) / 2 * R
        size = (r1 - r0) * R * 0.70
        for i in range(n):
            self.bird(2 * math.pi * i / n, mid, size)


def make_backdrop(pal):
    """Smooth radial atmospheric glow fading into deep obsidian, with subtle micro-dithering."""
    bw, bh = 480, 270
    bx, by = CX * bw / W, CY * bh / H
    max_d = math.hypot(bw, bh) * 0.68
    bg = pal["bg"]
    glow = pal["glow"]
    buf = bytearray(bw * bh * 3)
    rng = random.Random(42)
    idx = 0
    for y in range(bh):
        dy = y - by
        for x in range(bw):
            d = math.hypot(x - bx, dy) / max_d
            t = max(0.0, 1.0 - d)
            t = t * t * (3.0 - 2.0 * t)  # smoothstep
            n = rng.randint(-1, 1)
            for c_bg, c_gl in zip(bg, glow):
                v = round(c_bg + (c_gl - c_bg) * t) + n
                buf[idx] = max(0, min(255, v))
                idx += 1
    small = Image.frombytes("RGB", (bw, bh), bytes(buf))
    return small.resize((W, H), Image.BICUBIC)


def draw_face(canvas, pal):
    # Soft outer halo behind the bronze drum
    halo = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    hr = (R + 28) * S
    hd.ellipse([CX * S - hr, CY * S - hr, CX * S + hr, CY * S + hr], fill=pal["glow"] + (190,))
    halo = halo.filter(ImageFilter.GaussianBlur(radius=36 * S))

    # Subtle ambient glow behind the central 14-point sun
    sun_halo = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sun_halo)
    sr = int(R * 0.26 * S)
    sd.ellipse([CX * S - sr, CY * S - sr, CX * S + sr, CY * S + sr], fill=pal["bright"] + (55,))
    sun_halo = sun_halo.filter(ImageFilter.GaussianBlur(radius=28 * S))

    layer = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    f = Face(d, pal)

    # Outer architectural orbital hairlines (subtle modern framing)
    f.ring(R * 1.12, 0.8, pal["dim"])
    f.ring(R * 1.045, 1.0, pal["dim"])

    # Drum body & metallic rim
    f.disc(R, pal["face"])
    f.ring(R - 2, 3.2, pal["dim"])
    f.ring(R - 7, 1.4, pal["line"])
    # Subtle top-right metallic rim highlight arc
    d.arc([P(CX - R + 3, CY - R + 3), P(CX + R - 3, CY + R - 3)],
          start=215, end=355, fill=pal["bright"], width=round(2.0 * S))

    # Concentric bands from rim inwards with alternating tonal depth
    f.disc(0.982 * R, pal["band"])
    f.sawtooth(0.922, 0.976, 72)
    f.hatched(0.888, 0.922, 180)
    f.disc(0.888 * R, pal["face"])
    f.circles(0.822, 0.888, 40)
    f.ladder(0.794, 0.822, 120)
    f.disc(0.794 * R, pal["band"])
    f.birds(0.552, 0.794, 14)
    f.disc(0.552 * R, pal["face"])
    f.ladder(0.522, 0.552, 96)
    f.disc(0.522 * R, pal["band"])
    f.chevrons(0.402, 0.522, 22)
    f.zigzag(0.362, 0.402, 36)
    f.disc(0.362 * R, pal["face"])
    f.circles(0.292, 0.362, 22)
    f.hatched(0.262, 0.292, 84, slant=0.03)
    f.disc(0.258 * R, pal["band"])
    f.beaded(0.242, 56, 1.5)
    f.star14(0.068, 0.220)

    # Composite halo, drum face, and central sun glow onto the 1x backdrop
    base = canvas.convert("RGBA")
    base = Image.alpha_composite(base, halo.resize((W, H), Image.LANCZOS))
    base = Image.alpha_composite(base, layer.resize((W, H), Image.LANCZOS))
    base = Image.alpha_composite(base, sun_halo.resize((W, H), Image.LANCZOS))
    return base.convert("RGB")


def apply_ui_vignette(img, pal):
    """Smooth Gaussian-softened shade on the left and bottom so the clock, greeting and dock stay readable."""
    mask = Image.new("L", (W, H), 0)
    md = ImageDraw.Draw(mask)
    # Left zone for clock & greeting
    md.rectangle([0, 0, 740, H], fill=135)
    # Bottom zone for dock
    md.rectangle([0, 880, W, H], fill=150)
    mask = mask.filter(ImageFilter.GaussianBlur(radius=95))
    return Image.composite(Image.new("RGB", (W, H), pal["bg"]), img, mask)


def picture(name, pal):
    backdrop = make_backdrop(pal)
    img = draw_face(backdrop, pal)
    return apply_ui_vignette(img, pal)


def main():
    out = HERE / "wallpapers"
    out.mkdir(exist_ok=True)
    pictures = {}
    for name, pal in PALETTES.items():
        img = picture(name, pal)
        img.save(out / name, "JPEG", quality=84, optimize=True, progressive=False)   # baseline: faster to decode on the TV
        pictures[name] = img
        print(name, (out / name).stat().st_size // 1024, "KB")
    # A placeholder until a screenshot from the TV replaces it (themes/README.md asks for a real one).
    shot = pictures["02-dong-son-gold.jpg"].resize((1280, 720), Image.LANCZOS)
    ImageDraw.Draw(shot).text((24, 690), "placeholder: replace with a screenshot from the TV",
                              fill=(200, 200, 200), font=ImageFont.load_default())
    shot.save(HERE / "screenshot.jpg", "JPEG", quality=82, optimize=True)


if __name__ == "__main__":
    main()

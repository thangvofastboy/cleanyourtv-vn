#!/usr/bin/env python3
"""Draw the viet-nam theme's wallpapers: the national flag and a map of Viet Nam, our own drawing (CC0).

A stylised waving flag (morning/night) or faceted gold star emblem (evening) sits on the left with a soft
atmospheric fade so the clock, greeting and dock stay readable; a luminous golden-outlined map of Viet Nam
sits on the right with Phu Quoc, Con Dao, the coastal islands, and the Hoang Sa (Paracel) and Truong Sa
(Spratly) archipelagos inside modern corner-bracketed frames, labelled in Vietnamese, with "Bien Dong" on
the sea.

The flag follows the official construction: a red field (#DA251D) two units high for three long (2:3), and a
gold (#FFCD00) five-pointed star at the centre whose points lie on a circle with a radius of one fifth of the
flag's length (the star is 2/5 of the length across, 3/5 of the height), as Star.java draws it in the launcher.

The outline is not drawn by hand. It comes from Natural Earth's 1:10m Admin 0 countries in the Viet Nam point of
view (public domain), which assigns both archipelagos to Viet Nam:

    https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_0_countries_vnm.geojson
    https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_minor_islands.geojson

The script downloads both into a temporary folder with curl (or takes a folder holding them with --geodata) and
never writes them into the repository. Named features Natural Earth does not carry are in ISLANDS below with their
sources (CREDITS.md).

    python3 -I make_wallpapers.py                     # downloads the geodata, writes wallpapers/ and screenshot.jpg
    python3 -I make_wallpapers.py --geodata /tmp/ne   # uses the two files already in /tmp/ne
    python3 -I make_wallpapers.py --preview DIR       # also writes PNG previews (plain, and with a mock dock) to DIR

Needs Pillow and a font with Vietnamese diacritics (Noto Sans or DejaVu Sans, looked up below). The same data gives
the same pictures.
"""
import argparse
import json
import math
import random
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
W, H = 1920, 1080
S = 2                       # supersampling: draw at 2x, then scale down for smooth edges

RED = (0xDA, 0x25, 0x1D)
GOLD = (0xFF, 0xCD, 0x00)

NE_BASE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/"
NE_COUNTRIES = "ne_10m_admin_0_countries_vnm.geojson"
NE_ISLANDS = "ne_10m_minor_islands.geojson"

# The map frame: latitude 23.45 N at y 182 down to 6.55 N at y 1064 (clear of the top-right buttons, the whole of
# Truong Sa on screen), equirectangular with the longitude scaled by cos(15 N), the map's middle latitude.
LAT_N, LAT_S, Y_TOP, Y_BOT = 23.45, 6.55, 182, 1064
LON_E, X_E = 117.9, 1880
PX_PER_DEG = (Y_BOT - Y_TOP) / (LAT_N - LAT_S)
PX_PER_DEG_LON = PX_PER_DEG * math.cos(math.radians(15))

# The archipelagos: what counts as one of their islands (Natural Earth's minor islands are filtered by these boxes)
# and where their frame goes.
HOANG_SA = dict(lon=(110.9, 113.2), lat=(15.5, 17.3))
TRUONG_SA = dict(lon=(111.5, 116.3), lat=(7.0, 11.7))

# Main named features, so each group is complete even where Natural Earth has no polygon or point for them.
# Approximate positions (degrees, to about 0.01). 21 of the 30 lie within 0.05 degree of a Natural Earth shape or point;
# the Truong Sa ones Natural Earth lacks were checked against English Wikipedia's "List of maritime features in the
# Spratly Islands" and "Fiery Cross Reef" (see CREDITS.md).
ISLANDS = [
    # Hoang Sa (Paracel Islands)
    ("Phu Lam (Woody Island)", 16.834, 112.339),
    ("Cay (Tree Island)", 16.979, 112.268),
    ("Linh Con (Lincoln Island)", 16.669, 112.729),
    ("Hoang Sa (Pattle Island)", 16.533, 111.608),
    ("Quang Hoa (Duncan Island)", 16.450, 111.712),
    ("Duy Mong (Drummond Island)", 16.464, 111.739),
    ("Tri Ton (Triton Island)", 15.783, 111.202),
    ("Da Bac (North Reef)", 17.083, 111.500),
    ("Bom Bay (Bombay Reef)", 16.033, 112.533),
    # Truong Sa (Spratly Islands)
    ("Truong Sa (Spratly Island)", 8.644, 111.920),
    ("An Bang (Amboyna Cay)", 7.900, 112.917),
    ("Song Tu Tay (Southwest Cay)", 11.433, 114.333),
    ("Song Tu Dong (Northeast Cay)", 11.454, 114.355),
    ("Thi Tu (Thitu Island)", 11.055, 114.284),
    ("Loai Ta (Loaita Island)", 10.667, 114.417),
    ("Ba Binh (Itu Aba Island)", 10.377, 114.365),
    ("Son Ca (Sand Cay)", 10.376, 114.479),
    ("Nam Yet (Namyit Island)", 10.181, 114.362),
    ("Sinh Ton (Sin Cowe Island)", 9.885, 114.329),
    ("Da Tay (West Reef)", 8.850, 112.217),
    ("Da Lon (Discovery Great Reef)", 10.050, 113.850),
    ("Chu Thap (Fiery Cross Reef)", 9.550, 112.890),
    ("Vanh Khan (Mischief Reef)", 9.900, 115.533),
    ("Hoa Lau (Swallow Reef)", 7.373, 113.836),
    ("Thuyen Chai (Barque Canada Reef)", 8.167, 113.300),
    ("Ben Lac (West York Island)", 11.083, 115.017),
    ("Vinh Vien (Nanshan Island)", 10.723, 115.813),
    ("Binh Nguyen (Flat Island)", 10.817, 115.833),
    ("Co May (Second Thomas Shoal)", 9.733, 115.867),
    ("Suoi Nga (Commodore Reef)", 8.367, 115.233),
]

PALETTES = {
    # morning: stylised waving flag on the left, warm crimson-obsidian sea and luminous gold map
    "01-co-va-ban-do.jpg": dict(
        flag=0.58, bg=(14, 7, 7), glow_l=(46, 13, 11), glow_r=(38, 16, 12),
        land=(76, 23, 17), coast=GOLD, island=GOLD,
        grid=(36, 16, 14), ink=(255, 224, 134), sea=(214, 162, 100),
    ),
    # evening: faceted gold star emblem on the left, map in antique gold on deep crimson
    "02-ban-do-vang.jpg": dict(
        flag=0.0, mark=True, bg=(16, 6, 6), glow_l=(52, 22, 10), glow_r=(44, 16, 12),
        land=(86, 26, 18), coast=(255, 214, 72), island=(255, 214, 72),
        grid=(42, 17, 14), ink=(255, 228, 144), sea=(222, 168, 104),
    ),
    # night: nocturnal midnight-indigo sea, soft glowing flag and luminous constellation coastline
    "03-dem-co-va-ban-do.jpg": dict(
        flag=0.38, bg=(7, 8, 13), glow_l=(28, 12, 18), glow_r=(18, 22, 34),
        land=(40, 18, 22), coast=(232, 188, 42), island=(240, 200, 72),
        grid=(22, 24, 34), ink=(232, 204, 124), sea=(172, 148, 116),
    ),
}

FONT_DIRS = ["/usr/share/fonts/truetype/noto", "/usr/share/fonts/noto", "/usr/share/fonts/truetype/dejavu",
             "/usr/share/fonts/TTF"]


def font(bold, size):
    names = ["NotoSans-Bold.ttf", "DejaVuSans-Bold.ttf"] if bold else ["NotoSans-Regular.ttf", "DejaVuSans.ttf"]
    for d in FONT_DIRS:
        for n in names:
            p = Path(d) / n
            if p.is_file():
                return ImageFont.truetype(str(p), size * S)
    raise SystemExit("no Noto Sans or DejaVu Sans font found: the labels need Vietnamese diacritics")


def fetch(geodata):
    """The two GeoJSON files: from --geodata, or downloaded into a fresh temporary folder."""
    folder = Path(geodata) if geodata else Path(tempfile.mkdtemp(prefix="ne-vn-"))
    for name in (NE_COUNTRIES, NE_ISLANDS):
        target = folder / name
        if not target.is_file():
            subprocess.run(["curl", "-sSfL", "--max-time", "300", "-o", str(target), NE_BASE + name], check=True)
    return folder


def load(folder):
    """Viet Nam's polygons (outer rings) and the minor-island points inside the two archipelago boxes."""
    data = json.loads((folder / NE_COUNTRIES).read_text(encoding="utf-8"))
    rings = []
    for feature in data["features"]:
        if feature["properties"].get("ADM0_A3") != "VNM":
            continue
        geom = feature["geometry"]
        polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
        rings.extend(poly[0] for poly in polys)
    if not rings:
        raise SystemExit("no VNM feature in " + NE_COUNTRIES)
    points = []
    data = json.loads((folder / NE_ISLANDS).read_text(encoding="utf-8"))
    for feature in data["features"]:
        geom = feature["geometry"]
        if not geom:
            continue
        polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
        for poly in polys:
            ring = poly[0]
            lon = sum(p[0] for p in ring) / len(ring)
            lat = sum(p[1] for p in ring) / len(ring)
            if inside(HOANG_SA, lon, lat) or inside(TRUONG_SA, lon, lat):
                points.append((lon, lat))
    return rings, points


COASTAL_ISLANDS = [  # name, lat, lon, label offset x/y in 1x px, anchor
    ("Cô Tô", 20.98, 107.77, 9, -6, "ls"),
    ("Cát Bà", 20.80, 107.03, 9, 10, "lt"),
    ("Bạch Long Vĩ", 20.13, 107.72, 9, 0, "lm"),
    ("Cồn Cỏ", 17.16, 107.34, 9, 0, "lm"),
    ("Lý Sơn", 15.38, 109.12, 9, 0, "lm"),
    ("Phú Quý", 10.52, 108.93, 9, 0, "lm"),
    ("Côn Đảo", 8.69, 106.61, 9, 0, "lm"),
    ("Phú Quốc", 10.23, 103.96, -9, 0, "rm"),
    ("Nam Du", 9.68, 104.35, -9, 0, "rm"),
    ("Thổ Chu", 9.30, 103.48, 0, 9, "mt"),
]


def inside(box, lon, lat):
    return box["lon"][0] <= lon <= box["lon"][1] and box["lat"][0] <= lat <= box["lat"][1]


def xy(lon, lat):
    """Degrees to supersampled pixels."""
    return ((X_E - (LON_E - lon) * PX_PER_DEG_LON) * S, (Y_TOP + (LAT_N - lat) * PX_PER_DEG) * S)


def mix(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


def star(cx, cy, outer, turn=0.0):
    inner = outer * math.sin(math.radians(18)) / math.sin(math.radians(54))
    pts = []
    for i in range(10):
        a = math.radians(-90 + turn + 36 * i)
        r = outer if i % 2 == 0 else inner
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def make_backdrop(pal):
    """Dual-aura atmospheric backdrop with smoothstep falloff and micro-dithering."""
    bw, bh = 480, 270
    lx, ly = 440 * bw / W, 580 * bh / H
    rx, ry = 1360 * bw / W, 600 * bh / H
    rad_l = bw * 0.42
    rad_r = bw * 0.46
    bg = pal["bg"]
    gl = pal["glow_l"]
    gr = pal["glow_r"]
    buf = bytearray(bw * bh * 3)
    rng = random.Random(2026)
    idx = 0
    for y in range(bh):
        for x in range(bw):
            dl = max(0.0, 1.0 - math.hypot(x - lx, y - ly) / rad_l)
            dr = max(0.0, 1.0 - math.hypot(x - rx, y - ry) / rad_r)
            tl = dl * dl * (3.0 - 2.0 * dl)
            tr = dr * dr * (3.0 - 2.0 * dr)
            n = rng.randint(-1, 1)
            for c_bg, c_l, c_r in zip(bg, gl, gr):
                v = round(c_bg + (c_l - c_bg) * tl + (c_r - c_bg) * tr) + n
                buf[idx] = max(0, min(255, v))
                idx += 1
    return Image.frombytes("RGB", (bw, bh), bytes(buf)).resize((W, H), Image.BICUBIC)


def flag(width):
    """The flat flag, `width` pixels long: 2:3, the star's circumradius a fifth of the length."""
    height = round(width * 2 / 3)
    img = Image.new("RGB", (width, height), RED)
    ImageDraw.Draw(img).polygon(star(width / 2, height / 2, width / 5), fill=GOLD)
    return img


def waving_flag(canvas, pal):
    """Stylised silk-wave flag on the left, softly feathered at its edges so it blends into the dark canvas."""
    fw = 960 * S
    flat = flag(fw)
    fh = flat.height
    layer = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    x0, y0 = 20 * S, 240 * S
    wave = 540 * S
    step = 2 * S
    for x in range(0, fw, step):
        t = x / fw
        phase = 2 * math.pi * x / wave + 0.55
        dy = (7 + 24 * t) * S * math.sin(phase)
        shade = pal["flag"] * (1.0 + 0.14 * math.cos(phase))
        # Soft horizontal edge fade on the left and right ends of the silk banner
        edge_a = min(1.0, t / 0.08) * min(1.0, (1.0 - t) / 0.12)
        alpha = int(255 * (edge_a ** 0.75))
        strip = flat.crop((x, 0, min(fw, x + step), fh)).point(lambda v, f=shade: min(255, int(v * f)))
        strip_rgba = strip.convert("RGBA")
        # Vertical soft fade at top and bottom edges of the banner
        mask = Image.new("L", strip_rgba.size, alpha)
        md = ImageDraw.Draw(mask)
        fade_h = 14 * S
        for yy in range(fade_h):
            a_top = int(alpha * (yy / fade_h))
            md.line([(0, yy), (strip_rgba.width, yy)], fill=a_top)
            md.line([(0, fh - 1 - yy), (strip_rgba.width, fh - 1 - yy)], fill=a_top)
        strip_rgba.putalpha(mask)
        layer.paste(strip_rgba, (x0 + x, round(y0 + dy)), strip_rgba)
    canvas.alpha_composite(layer)


def stylised_star(canvas, cx, cy, outer, pal):
    """A sculpted, faceted gold five-pointed star emblem with concentric Dong-Son-inspired rings."""
    layer = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    bg = pal["bg"]

    def m(t, alpha=255):
        return mix(bg, GOLD, t) + (alpha,)

    # Subtle outer orbital hairlines & concentric emblem rings
    for rr, w, t in [(1.48, 1.0, 0.14), (1.30, 2.0, 0.30), (1.20, 1.2, 0.22), (1.10, 1.2, 0.18)]:
        r = outer * rr
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=m(t), width=round(w * S))

    # Ring of 40 delicate beads
    n = 40
    for i in range(n):
        a = 2 * math.pi * i / n
        r = outer * 1.25
        x, y = cx + r * math.cos(a), cy + r * math.sin(a)
        d = 2.6 * S if i % 2 == 0 else 1.8 * S
        draw.ellipse([x - d, y - d, x + d, y + d], fill=m(0.36 if i % 2 == 0 else 0.24))

    # Radial compass rays between the star points
    for i in range(5):
        a = math.radians(-90 + 36 + 72 * i)
        draw.line([(cx + outer * 0.56 * math.cos(a), cy + outer * 0.56 * math.sin(a)),
                   (cx + outer * 1.13 * math.cos(a), cy + outer * 1.13 * math.sin(a))],
                  fill=m(0.28), width=round(1.6 * S))

    # Faceted 3D-style 5-pointed star (10 triangles meeting at the centre)
    pts = star(cx, cy, outer)
    for i in range(10):
        p1 = pts[i]
        p2 = pts[(i + 1) % 10]
        # Alternate bright & warm-gold facets around each point
        t_val = 0.54 if i % 2 == 0 else 0.32
        draw.polygon([(cx, cy), p1, p2], fill=m(t_val))

    # Crisp luminous rim and inner echo star
    draw.line(pts + [pts[0]], fill=m(0.76), width=round(2.2 * S), joint="curve")
    inner = star(cx, cy, outer * 0.50)
    draw.line(inner + [inner[0]], fill=m(0.50), width=round(1.4 * S), joint="curve")

    # Soft golden halo behind the star
    halo = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    hr = outer * 0.95
    hd.ellipse([cx - hr, cy - hr, cx + hr, cy + hr], fill=GOLD + (42,))
    halo = halo.filter(ImageFilter.GaussianBlur(radius=34 * S))
    canvas.alpha_composite(halo)
    canvas.alpha_composite(layer)


def modern_box(draw, box, colour, fill_tint, width):
    """Architectural corner-bracket frame with ultra-faint dashed connecting hairlines."""
    (x1, y1), (x2, y2) = xy(box["lon"][0], box["lat"][1]), xy(box["lon"][1], box["lat"][0])
    # Subtle translucent fill inside the archipelago frame
    draw.rectangle([x1, y1, x2, y2], fill=fill_tint)

    # Faint dashed hairlines along the sides
    faint = colour[:3] + (85,)
    dash, gap = 7 * S, 8 * S
    for (ax, ay), (bx, by) in [((x1, y1), (x2, y1)), ((x2, y1), (x2, y2)),
                               ((x2, y2), (x1, y2)), ((x1, y2), (x1, y1))]:
        length = math.hypot(bx - ax, by - ay)
        n = int(length // (dash + gap))
        for i in range(n + 1):
            s0 = i * (dash + gap)
            s1 = min(length, s0 + dash)
            if s0 >= length:
                break
            draw.line([(ax + (bx - ax) * s0 / length, ay + (by - ay) * s0 / length),
                       (ax + (bx - ax) * s1 / length, ay + (by - ay) * s1 / length)], fill=faint, width=S)

    # Crisp solid L-shaped corner brackets
    blen = 18 * S
    bw = round(width * 1.4)
    for cx, cy, sx, sy in [(x1, y1, 1, 1), (x2, y1, -1, 1), (x2, y2, -1, -1), (x1, y2, 1, -1)]:
        draw.line([(cx, cy), (cx + sx * blen, cy)], fill=colour, width=bw)
        draw.line([(cx, cy), (cx, cy + sy * blen)], fill=colour, width=bw)


def label(draw, x, y, lines, fonts, colour, bg, anchor="la"):
    """Text lines from (x, y) in 1x pixels, with a thin dark outline so they read over any fill."""
    yy = y * S
    for text, f in zip(lines, fonts):
        draw.text((x * S, yy), text, font=f, fill=colour, anchor=anchor, stroke_width=2 * S, stroke_fill=bg)
        yy += f.size * 1.28


def draw_map(canvas, rings, points, pal):
    # Subtle graticule layer
    grid_layer = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grid_layer)
    grid_col = pal["grid"] + (140,)
    for lon in range(105, 120, 5):
        x, _ = xy(lon, 0)
        gd.line([(x, Y_TOP * S), (x, Y_BOT * S)], fill=grid_col, width=S)
    for lat in range(10, 25, 5):
        _, y = xy(0, lat)
        gd.line([(xy(102, 0)[0] - 40 * S, y), (X_E * S, y)], fill=grid_col, width=S)
    canvas.alpha_composite(grid_layer)

    # Soft golden coastal glow layer
    glow_layer = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    gld = ImageDraw.Draw(glow_layer)
    coast_glow = pal["coast"] + (115,)
    for ring in rings:
        pts = [xy(lon, lat) for lon, lat in ring]
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        if max(xs) - min(xs) < 5 * S and max(ys) - min(ys) < 5 * S:
            continue
        gld.line(pts + [pts[0]], fill=coast_glow, width=round(5.0 * S), joint="curve")
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(radius=7 * S))
    canvas.alpha_composite(glow_layer)

    # Main map layer: draw archipelago frames first so island beacons sit crisply on top
    map_layer = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(map_layer)
    frame_col = pal["coast"] + (210,)
    fill_tint = pal["land"] + (42,)
    modern_box(draw, HOANG_SA, frame_col, fill_tint, round(1.5 * S))
    modern_box(draw, TRUONG_SA, frame_col, fill_tint, round(1.5 * S))

    for ring in rings:
        pts = [xy(lon, lat) for lon, lat in ring]
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        if max(xs) - min(xs) < 5 * S and max(ys) - min(ys) < 5 * S:
            cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
            r = 2.6 * S
            draw.ellipse([cx - r * 1.9, cy - r * 1.9, cx + r * 1.9, cy + r * 1.9], fill=pal["island"] + (55,))
            draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=pal["island"] + (255,))
            continue
        draw.polygon(pts, fill=pal["land"] + (235,))
        draw.line(pts + [pts[0]], fill=pal["coast"] + (255,), width=round(1.6 * S), joint="curve")

    # Archipelago island beacons
    for lon, lat in points + [(lon, lat) for _, lat, lon in ISLANDS]:
        cx, cy = xy(lon, lat)
        r = 2.6 * S
        draw.ellipse([cx - r * 2.1, cy - r * 2.1, cx + r * 2.1, cy + r * 2.1], fill=pal["island"] + (60,))
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=pal["island"] + (255,))

    bold, small = font(True, 21), font(False, 18)
    hx, hy = xy(HOANG_SA["lon"][1], HOANG_SA["lat"][1])
    label(draw, hx / S + 14, hy / S + 2, ["Quần đảo", "Hoàng Sa", "(Việt Nam)"], [bold, bold, small],
          pal["ink"], pal["bg"])
    tx, ty = xy(TRUONG_SA["lon"][0], TRUONG_SA["lat"][0])
    label(draw, tx / S - 16, ty / S - 92, ["Quần đảo", "Trường Sa", "(Việt Nam)"], [bold, bold, small],
          pal["ink"], pal["bg"], anchor="ra")

    # Coastal islands with subtle beacon rings
    tiny = font(False, 19)
    for name, lat, lon, dx, dy, anchor in COASTAL_ISLANDS:
        cx, cy = xy(lon, lat)
        r = 3.1 * S
        draw.ellipse([cx - r * 1.9, cy - r * 1.9, cx + r * 1.9, cy + r * 1.9], outline=pal["island"] + (110,), width=S)
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=pal["island"] + (255,))
        draw.text((cx + dx * S, cy + dy * S), name, font=tiny, fill=pal["ink"], anchor=anchor,
                  stroke_width=2 * S, stroke_fill=pal["bg"])

    # Sea label
    sx, sy = xy(112.6, 14.2)
    draw.text((sx, sy), "B I Ể N   Đ Ô N G", font=font(True, 25), fill=pal["sea"], anchor="mm",
              stroke_width=2 * S, stroke_fill=pal["bg"])

    canvas.alpha_composite(map_layer)


def apply_ui_vignette(img, pal):
    """Smooth Gaussian-softened bottom shade so the dock stays crisp and legible."""
    mask = Image.new("L", (W, H), 0)
    md = ImageDraw.Draw(mask)
    md.rectangle([0, 910, W, H], fill=135)
    mask = mask.filter(ImageFilter.GaussianBlur(radius=75))
    return Image.composite(Image.new("RGB", (W, H), pal["bg"]), img, mask)


def picture(pal, rings, points):
    backdrop = make_backdrop(pal).convert("RGBA")
    canvas = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    if pal["flag"]:
        waving_flag(canvas, pal)
    if pal.get("mark"):
        stylised_star(canvas, 450 * S, 580 * S, 215 * S, pal)
    draw_map(canvas, rings, points, pal)
    composed = Image.alpha_composite(backdrop, canvas.resize((W, H), Image.LANCZOS)).convert("RGB")
    return apply_ui_vignette(composed, pal)


def with_dock(img):
    """A preview of how Home covers it: a dark band where the dock sits, as a check that the map still reads."""
    out = img.copy()
    band = Image.new("RGB", (W, 250), (20, 20, 24))
    out.paste(Image.blend(out.crop((0, 700, W, 950)), band, 0.55), (0, 700))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--geodata", help="folder holding the two Natural Earth GeoJSON files (else downloaded)")
    ap.add_argument("--preview", help="also write PNG previews to this folder")
    args = ap.parse_args()
    rings, points = load(fetch(args.geodata))
    out = HERE / "wallpapers"
    out.mkdir(exist_ok=True)
    pictures = {}
    for name, pal in PALETTES.items():
        img = picture(pal, rings, points)
        img.save(out / name, "JPEG", quality=84, optimize=True, progressive=False)   # baseline: faster to decode on the TV
        pictures[name] = img
        print(name, (out / name).stat().st_size // 1024, "KB")
        if args.preview:
            preview = Path(args.preview)
            preview.mkdir(parents=True, exist_ok=True)
            stem = name.rsplit(".", 1)[0]
            img.save(preview / (stem + ".png"))
            with_dock(img).save(preview / (stem + "-dock.png"))
    # A placeholder until a screenshot from the TV replaces it (themes/README.md asks for a real one).
    shot = pictures["01-co-va-ban-do.jpg"].resize((1280, 720), Image.LANCZOS)
    ImageDraw.Draw(shot).text((24, 690), "placeholder: replace with a screenshot from the TV",
                              fill=(200, 200, 200), font=ImageFont.load_default())
    shot.save(HERE / "screenshot.jpg", "JPEG", quality=82, optimize=True)


if __name__ == "__main__":
    main()

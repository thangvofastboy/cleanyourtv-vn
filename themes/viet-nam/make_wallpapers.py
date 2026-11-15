#!/usr/bin/env python3
"""Draw the viet-nam theme's wallpapers: the national flag and a map of Viet Nam, our own drawing (CC0).

The flag waves on the left, darkened so the clock, greeting and dock stay readable; the map sits on the right
half: the mainland with Phu Quoc, Con Dao and the coastal islands, the Hoang Sa (Paracel) and Truong Sa (Spratly)
archipelagos as island dots inside a thin dashed outline each, labelled in Vietnamese, and "Bien Dong" on the sea.

The flag follows the official construction: a red field (#DA251D) two units high for three long (2:3), and a gold
(#FFCD00) five-pointed star at the centre whose points lie on a circle with a radius of one fifth of the flag's
length (the star is 2/5 of the length across, 3/5 of the height), as Star.java draws it in the launcher.

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
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

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
# and where their dashed outline goes.
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
    # morning: the flag in front, warm red-brown sea
    "01-co-va-ban-do.jpg": dict(flag=0.56, bg=(24, 9, 8), land=(92, 30, 21), coast=GOLD, island=GOLD,
                                grid=(44, 18, 15), ink=(255, 222, 128), sea=(205, 150, 90)),
    # evening: the map alone in gold on deep red
    "02-ban-do-vang.jpg": dict(flag=0.0, mark=(46, 13, 10), bg=(30, 8, 7), land=(104, 34, 22), coast=(255, 214, 72),
                               island=(255, 214, 72), grid=(52, 18, 15), ink=(255, 226, 140), sea=(214, 158, 96)),
    # night: a dark flag and a dim map
    "03-dem-co-va-ban-do.jpg": dict(flag=0.38, bg=(9, 9, 13), land=(60, 22, 18), coast=(226, 182, 20),
                                    island=(226, 182, 20), grid=(26, 24, 30), ink=(232, 200, 110), sea=(170, 140, 110)),
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


def inside(box, lon, lat):
    return box["lon"][0] <= lon <= box["lon"][1] and box["lat"][0] <= lat <= box["lat"][1]


def xy(lon, lat):
    """Degrees to supersampled pixels."""
    return ((X_E - (LON_E - lon) * PX_PER_DEG_LON) * S, (Y_TOP + (LAT_N - lat) * PX_PER_DEG) * S)


def star(cx, cy, outer, turn=0.0):
    inner = outer * math.sin(math.radians(18)) / math.sin(math.radians(54))
    pts = []
    for i in range(10):
        a = math.radians(-90 + turn + 36 * i)
        r = outer if i % 2 == 0 else inner
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def flag(width):
    """The flat flag, `width` pixels long: 2:3, the star's circumradius a fifth of the length."""
    height = round(width * 2 / 3)
    img = Image.new("RGB", (width, height), RED)
    ImageDraw.Draw(img).polygon(star(width / 2, height / 2, width / 5), fill=GOLD)
    return img


def waving_flag(canvas, pal):
    """The flag waving from a pole off the left edge, in column strips: each moves up or down along a sine and is
    shaded a little with it (soft enough that RGB_565's steps stay a few broad folds)."""
    fw = 1000 * S
    flat = flag(fw)
    fh = flat.height
    x0, y0 = 0, 216 * S                     # the whole flag on screen, its star centred in it
    wave = 560 * S
    step = 3 * S
    for x in range(0, fw, step):
        t = x / fw
        phase = 2 * math.pi * x / wave + 0.6
        dy = (8 + 26 * t) * S * math.sin(phase)
        f = pal["flag"] * (1 + 0.11 * math.cos(phase))
        strip = flat.crop((x, 0, min(fw, x + step), fh)).point(lambda v, f=f: min(255, int(v * f)))
        canvas.paste(strip, (x0 + x, round(y0 + dy)))


def dashed_box(draw, box, colour, width):
    """A rounded dashed outline around an archipelago's box."""
    (x1, y1), (x2, y2) = xy(box["lon"][0], box["lat"][1]), xy(box["lon"][1], box["lat"][0])
    r = 18 * S
    path = []
    for (ax, ay), (bx, by) in [((x1 + r, y1), (x2 - r, y1)), ((x2, y1 + r), (x2, y2 - r)),
                               ((x2 - r, y2), (x1 + r, y2)), ((x1, y2 - r), (x1, y1 + r))]:
        path.append(((ax, ay), (bx, by)))
    dash, gap = 9 * S, 7 * S
    for (ax, ay), (bx, by) in path:
        length = math.hypot(bx - ax, by - ay)
        n = int(length // (dash + gap))
        for i in range(n + 1):
            s0 = i * (dash + gap)
            s1 = min(length, s0 + dash)
            if s0 >= length:
                break
            draw.line([(ax + (bx - ax) * s0 / length, ay + (by - ay) * s0 / length),
                       (ax + (bx - ax) * s1 / length, ay + (by - ay) * s1 / length)], fill=colour, width=width)
    for cx, cy, start in [(x1 + r, y1 + r, 180), (x2 - r, y1 + r, 270), (x2 - r, y2 - r, 0), (x1 + r, y2 - r, 90)]:
        draw.arc([cx - r, cy - r, cx + r, cy + r], start, start + 90, fill=colour, width=width)


def label(draw, x, y, lines, fonts, colour, bg, anchor="la"):
    """Text lines from (x, y) in 1x pixels, with a thin dark outline so they read over any fill."""
    yy = y * S
    for text, f in zip(lines, fonts):
        draw.text((x * S, yy), text, font=f, fill=colour, anchor=anchor, stroke_width=2 * S, stroke_fill=bg)
        yy += f.size * 1.28


def draw_map(canvas, rings, points, pal):
    draw = ImageDraw.Draw(canvas)
    # A faint graticule every 5 degrees on the map's half.
    for lon in range(105, 120, 5):
        x, _ = xy(lon, 0)
        draw.line([(x, Y_TOP * S), (x, Y_BOT * S)], fill=pal["grid"], width=S)
    for lat in range(10, 25, 5):
        _, y = xy(0, lat)
        draw.line([(xy(102, 0)[0] - 40 * S, y), (X_E * S, y)], fill=pal["grid"], width=S)
    # Land, then the gold coastline over it; the tiniest islands get a dot so they do not vanish.
    for ring in rings:
        pts = [xy(lon, lat) for lon, lat in ring]
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        if max(xs) - min(xs) < 5 * S and max(ys) - min(ys) < 5 * S:
            cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
            r = 2.6 * S
            draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=pal["island"])
            continue
        draw.polygon(pts, fill=pal["land"])
        draw.line(pts + [pts[0]], fill=pal["coast"], width=round(1.6 * S), joint="curve")
    for lon, lat in points + [(lon, lat) for _, lat, lon in ISLANDS]:
        cx, cy = xy(lon, lat)
        r = 2.6 * S
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=pal["island"])
    dim = tuple(int(c * 0.78) for c in pal["coast"])
    dashed_box(draw, HOANG_SA, dim, round(1.4 * S))
    dashed_box(draw, TRUONG_SA, dim, round(1.4 * S))
    bold, small = font(True, 21), font(False, 18)
    # Hoang Sa: right of its box, above the dock band.
    hx, hy = xy(HOANG_SA["lon"][1], HOANG_SA["lat"][1])
    label(draw, hx / S + 12, hy / S + 2, ["Quần đảo", "Hoàng Sa", "(Việt Nam)"], [bold, bold, small],
          pal["ink"], pal["bg"])
    # Truong Sa: left of its box, under the dock band.
    tx, ty = xy(TRUONG_SA["lon"][0], TRUONG_SA["lat"][0])
    label(draw, tx / S - 14, ty / S - 92, ["Quần đảo", "Trường Sa", "(Việt Nam)"], [bold, bold, small],
          pal["ink"], pal["bg"], anchor="ra")
    # The sea's name, spaced out, between the coast and the two groups.
    sx, sy = xy(112.6, 14.2)
    draw.text((sx, sy), "B I Ể N   Đ Ô N G", font=font(True, 25), fill=pal["sea"], anchor="mm",
              stroke_width=2 * S, stroke_fill=pal["bg"])


def picture(pal, rings, points):
    canvas = Image.new("RGB", (W * S, H * S), pal["bg"])
    if pal["flag"]:
        waving_flag(canvas, pal)
    if pal.get("mark"):
        # No flag: the flag's star alone, large and barely lighter than the ground, behind the clock and greeting.
        ImageDraw.Draw(canvas).polygon(star(470 * S, 560 * S, 330 * S), fill=pal["mark"])
    draw_map(canvas, rings, points, pal)
    return canvas.resize((W, H), Image.LANCZOS)


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
        img.save(out / name, "JPEG", quality=82, optimize=True, progressive=False)   # baseline: faster to decode on the TV
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

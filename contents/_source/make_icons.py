"""Builds the project icons for Modrinth and CurseForge from the Diorama icon.

Usage: python -I make_icons.py <output_dir>
Reads diorama-original.png next to this script. Each icon is the Diorama artwork inside a rounded
border, with a tab on the border that holds a pixel-art glyph for the addon.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
S = 512          # final icon size
SS = 4           # supersampling for smooth edges

# --- Pixel-art glyphs (one char = one glyph pixel, '.' = empty). A dark outline is added automatically.
GLYPHS = {
    "flag": [
        "PQ.............",
        "PQRRRR.....RR..",
        "PQRRRRRR.RRRRR.",
        "PQRRRWRRRRRRRR.",
        "PQRRWWWRRRRRRR.",
        "PQRRRWRRRRRRRR.",
        "PQRRRRRRRRRRRR.",
        "PQDRRRRRRRRRRD.",
        "PQDDDRRRRRDDD..",
        "PQ...DDDDD.....",
        "PQ.............",
        "PQ.............",
        "PQ.............",
        "PQ.............",
    ],
    "globe": [
        "....BBBBBB....",
        "..BBGGBBBBBB..",
        ".BGGGGGBBBBBB.",
        ".BBGGGGBBBGGB.",
        "BBBBGGGBBGGGGB",
        "BBBBBGBBBGGGGB",
        "BBBBBBBBBBGGGB",
        "BBBGGBBBBBBGBB",
        "BBGGGGGBBBBBBB",
        "BBBGGGGGBBBBBB",
        ".BBBGGGGBBBBB.",
        ".BBBBGGGBBBBB.",
        "..BBBBGBBBBB..",
        "....BBBBBB....",
    ],
    "map": [
        "....RRR.......",
        "...RRWRR......",
        "...RRRRR......",
        "....RRR.......",
        "LLLL.RMMMLLLLL",
        "LLLLMMMMMLLLLL",
        "LGGLMMMMMLLGLL",
        "LGGGMMGMMLGGGL",
        "LLGLMGGGMLLGLL",
        "LLLLMMGMMLLLLL",
        "LLRLMMMMMLLLLL",
        "LLLLMMMRMLRLLL",
        "LLLLMMMMMLLLLL",
        "LLLL.....LLLLL",
    ],
}


def _colombia_flag():
    """Waving Colombian flag on a pole: yellow half, blue and red quarters (lower case = folded shade)."""
    wave = [0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1]
    bands = "YYYYUUCC"   # 2:1:1
    rows = [["."] * (2 + len(wave)) for _ in range(14)]
    for y in range(len(rows)):
        rows[y][0], rows[y][1] = "P", "Q"
    for c, off in enumerate(wave):
        for i, band in enumerate(bands):
            rows[1 + off + i][2 + c] = band.lower() if off else band
    return ["".join(r) for r in rows]


GLYPHS["colombia"] = _colombia_flag()

GLYPH_COLORS = {
    "P": (214, 214, 214), "Q": (150, 150, 150),          # pole
    "R": (214, 54, 54), "D": (150, 30, 36), "W": (255, 220, 120),
    "B": (64, 140, 222), "G": (96, 196, 92),
    "L": (240, 222, 170), "M": (200, 176, 120),
    "Y": (252, 209, 22), "y": (214, 172, 10),            # Colombian flag
    "U": (0, 56, 147), "u": (0, 40, 112),
    "C": (206, 17, 38), "c": (160, 10, 28),
}
OUTLINE = (24, 22, 28)


def glyph_image(name, px):
    rows = GLYPHS[name]
    h, w = len(rows), len(rows[0])
    filled = {(x, y) for y, r in enumerate(rows) for x, c in enumerate(r) if c != "."}
    img = Image.new("RGBA", ((w + 2) * px, (h + 2) * px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for (x, y) in filled:  # outline: every empty neighbour (incl. diagonals) of a filled pixel
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in filled:
                    gx, gy = x + dx + 1, y + dy + 1
                    d.rectangle([gx * px, gy * px, gx * px + px - 1, gy * px + px - 1], fill=OUTLINE)
    for (x, y) in filled:
        c = GLYPH_COLORS[rows[y][x]]
        d.rectangle([(x + 1) * px, (y + 1) * px, (x + 1) * px + px - 1, (y + 1) * px + px - 1], fill=c)
    return img


def aa_mask(shapes):
    """shapes: list of ('rr', box, radius) or ('poly', points) drawn at 1x coordinates, antialiased."""
    m = Image.new("L", (S * SS, S * SS), 0)
    d = ImageDraw.Draw(m)
    for sh in shapes:
        if sh[0] == "rr":
            box = [v * SS for v in sh[1]]
            d.rounded_rectangle(box, radius=sh[2] * SS, fill=255)
        elif sh[0] == "poly":
            d.polygon([(x * SS, y * SS) for x, y in sh[1]], fill=255)
        elif sh[0] == "rr-":  # subtract
            box = [v * SS for v in sh[1]]
            d.rounded_rectangle(box, radius=sh[2] * SS, fill=0)
        elif sh[0] == "rect":
            d.rectangle([v * SS for v in sh[1]], fill=255)
        elif sh[0] == "ellipse-":  # subtract
            d.ellipse([v * SS for v in sh[1]], fill=0)
    return m.resize((S, S), Image.LANCZOS)


def gradient(c1, c2):
    g = Image.new("RGBA", (S, S))
    top, bot = Image.new("RGBA", (S, S), c1), Image.new("RGBA", (S, S), c2)
    ramp = Image.linear_gradient("L").resize((S, S))
    g.paste(top)
    g.paste(bot, mask=ramp)
    return g


def cover(img, w, h, anchor_y=0.5):
    sw, sh = img.size
    scale = max(w / sw, h / sh)
    r = img.resize((round(sw * scale), round(sh * scale)), Image.LANCZOS)
    x0 = (r.width - w) // 2
    y0 = int((r.height - h) * anchor_y)
    return r.crop((x0, y0, x0 + w, y0 + h))


def compose(art, frame_shapes, window_box, window_r, tab_shapes, chip, glyph, glyph_px, colors,
            glyph_center=None, window_shapes=None):
    """frame_shapes: silhouette. window: rounded box showing the artwork (or window_shapes, cut to any
    shape inside window_box). tab_shapes: drawn over the art. chip: optional (box, radius) plate under
    the glyph."""
    border1, border2, chip_col, edge = colors
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    frame = aa_mask(frame_shapes)
    out.paste(gradient(border1, border2), mask=frame)

    x0, y0, x1, y1 = window_box
    win = aa_mask(window_shapes or [("rr", window_box, window_r)])

    # thin dark inner edge around the artwork window
    out.paste(Image.new("RGBA", (S, S), edge), mask=win.filter(ImageFilter.MaxFilter(7)))
    art_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    art_layer.paste(cover(art, x1 - x0, y1 - y0), (x0, y0))
    out.paste(art_layer, mask=win)

    if tab_shapes:
        tab = aa_mask(tab_shapes)
        shadow = tab.filter(ImageFilter.GaussianBlur(8)).point(lambda v: int(v * 0.55))
        out.paste(Image.new("RGBA", (S, S), (0, 0, 0, 255)), (0, 4), mask=shadow)
        out.paste(gradient(border1, border2), mask=tab)
        out.putalpha(frame_or(out, frame, tab))

    if chip:
        cm = aa_mask([("rr", chip[0], chip[1])])
        out.paste(Image.new("RGBA", (S, S), chip_col), mask=cm)
        cx, cy = (chip[0][0] + chip[0][2]) / 2, (chip[0][1] + chip[0][3]) / 2
    else:
        cx, cy = glyph_center
    if glyph_center:
        cx, cy = glyph_center
    g = glyph_image(glyph, glyph_px)
    out.alpha_composite(g, (round(cx - g.width / 2), round(cy - g.height / 2)))
    return out


def frame_or(out, frame, tab):
    """Alpha = silhouette ∪ tab (the shadow must not leak outside the icon)."""
    from PIL import ImageChops
    return ImageChops.lighter(frame, tab)


# ---------------------------------------------------------------- variants
def opt_a(art, glyph, pal):
    """Bottom-centre tab rising from the border into the artwork."""
    T, R = 22, 72
    frame = [("rr", (0, 0, S - 1, S - 1), R)]
    tab = [("rr", (S / 2 - 108, S - 196, S / 2 + 108, S - 1), 40)]
    chip = ((S / 2 - 86, S - 174, S / 2 + 86, S - 24), 26)
    return compose(art, frame, (T, T, S - T, S - T), R - T, tab, chip, glyph, 9, pal)


def opt_b(art, glyph, pal):
    """Rounded badge fused into the bottom-right corner."""
    T, R = 22, 72
    frame = [("rr", (0, 0, S - 1, S - 1), R)]
    tab = [("rr", (S - 216, S - 216, S - 1, S - 1), R)]
    chip = ((S - 192, S - 192, S - 24, S - 24), 52)
    return compose(art, frame, (T, T, S - T, S - T), R - T, tab, chip, glyph, 9, pal)


def opt_c(art, glyph, pal):
    """Folder: a tab sticks out above the top-left of the frame."""
    T, R, top = 20, 56, 116
    frame = [("rr", (0, top, S - 1, S - 1), R), ("rr", (0, 0, 240, top + R), 48)]
    chip = ((24, 18, 216, top + 6), 30)
    return compose(art, frame, (T, top + T, S - T, S - T), R - T, None, chip, glyph, 6, pal,
                   glyph_center=(120, 64))


def opt_d(art, glyph, pal):
    """Bookmark ribbon hanging from the top border, on the right."""
    T, R = 22, 72
    frame = [("rr", (0, 0, S - 1, S - 1), R)]
    x0, x1, y1 = S - 230, S - 64, 250
    tab = [("poly", [(x0, 0), (x1, 0), (x1, y1), ((x0 + x1) / 2, y1 - 40), (x0, y1)])]
    return compose(art, frame, (T, T, S - T, S - T), R - T, tab, None, glyph, 8, pal,
                   glyph_center=((x0 + x1) / 2, 112))


def opt_e(art, glyph, pal):
    """Wide banner along the bottom border."""
    T, R = 18, 72
    frame = [("rr", (0, 0, S - 1, S - 1), R)]
    tab = [("rr", (70, S - 176, S - 70, S - 1), 44)]
    chip = ((94, S - 154, S - 94, S - 22), 28)
    return compose(art, frame, (T, T, S - T, S - T), R - T, tab, chip, glyph, 8, pal)


def opt_f(art, glyph, pal):
    """Circular medallion overlapping the top-left corner of a thick frame."""
    T, R = 26, 80
    frame = [("rr", (0, 0, S - 1, S - 1), R)]
    tab = [("rr", (0, 0, 216, 216), 108)]
    chip = ((20, 20, 196, 196), 88)
    return compose(art, frame, (T, T, S - T, S - T), R - T, tab, chip, glyph, 9, pal)


def opt_g(art, glyph, pal):
    """The frame grows into a square block at the bottom-right corner that holds the glyph; the artwork
    wraps around it with rounded corners, so the block reads as part of the frame."""
    T, R = 22, 72
    n = 296          # where the corner block starts (x and y)
    wr = R - T       # artwork corner radius
    f = 36           # rounding of the block's inner corner
    frame = [("rr", (0, 0, S - 1, S - 1), R)]
    window = [
        ("rr", (T, T, S - T, n), wr),                          # top band
        ("rr", (T, T, n, S - T), wr),                          # left band
        ("rect", (n - 1, n - 1, n + f, n + f)),                # fill the inner corner...
        ("ellipse-", (n, n, n + 2 * f, n + 2 * f)),            # ...minus a quarter circle
    ]
    chip = ((n + T, n + T, S - T - 2, S - T - 2), wr - 6)
    return compose(art, frame, (T, T, S - T, S - T), wr, None, chip, glyph, 9, pal, window_shapes=window)


VARIANTS = {"A": opt_a, "B": opt_b, "C": opt_c, "D": opt_d, "E": opt_e, "F": opt_f, "G": opt_g}

# palettes: (border top, border bottom, chip, inner edge)
PALETTES = {
    "teal": ((72, 196, 170, 255), (30, 120, 112, 255), (20, 70, 66, 255), (10, 30, 30, 255)),
    "gold": ((246, 196, 74, 255), (196, 128, 28, 255), (92, 56, 14, 255), (40, 24, 6, 255)),
    "slate": ((70, 78, 92, 255), (34, 38, 48, 255), (226, 178, 62, 255), (12, 14, 18, 255)),
    "red": ((222, 76, 70, 255), (150, 34, 40, 255), (70, 16, 20, 255), (34, 8, 10, 255)),
    "blue": ((86, 150, 236, 255), (36, 82, 170, 255), (20, 40, 86, 255), (10, 18, 40, 255)),
}

OPTION_PALETTE = {"A": "teal", "B": "slate", "C": "gold", "D": "red", "E": "teal", "F": "gold"}


def save_small(img, path):
    """Modrinth caps icons at 256 KiB: quantize to a palette (with alpha) if the PNG is too large."""
    img.save(path, optimize=True)
    if path.stat().st_size > 250 * 1024:
        img.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.FLOYDSTEINBERG) \
            .save(path, optimize=True)


def sheet(icons, path, title_h=0):
    """Preview sheet: each option at 256, 96 and 48 px on light and dark backgrounds."""
    cell_w, cell_h = 300, 430
    out = Image.new("RGB", (cell_w * len(icons), cell_h), (245, 245, 245))
    d = ImageDraw.Draw(out)
    d.rectangle([0, 300, out.width, cell_h], fill=(30, 32, 38))
    for i, (key, ic) in enumerate(icons.items()):
        x = i * cell_w
        out.paste(ic.resize((256, 256), Image.LANCZOS), (x + 22, 22), ic.resize((256, 256), Image.LANCZOS))
        d.text((x + 140, 284), key, fill=(20, 20, 20), anchor="mm", font_size=22)
        for j, (bg_y, size) in enumerate(((310, 96), (310, 48))):
            small = ic.resize((size, size), Image.LANCZOS)
            px = x + 30 + j * 130
            out.paste(small, (px, bg_y + (110 - size) // 2), small)
    out.save(path)


def main():
    outdir = Path(sys.argv[1])
    outdir.mkdir(parents=True, exist_ok=True)
    art = Image.open(HERE / "diorama-original.png").convert("RGBA")
    mode = sys.argv[2] if len(sys.argv) > 2 else "options"
    if mode == "options":
        icons = {}
        for key, fn in VARIANTS.items():
            ic = fn(art, "flag", PALETTES[OPTION_PALETTE[key]])
            save_small(ic, outdir / f"claims-option-{key}.png")
            icons[key] = ic
        sheet(icons, outdir / "claims-options-sheet.png")
    elif mode == "pack":  # python make_icons.py <dir> pack [palette]: icon of the Spanish resource pack
        pal = sys.argv[3] if len(sys.argv) > 3 else "gold"
        icon = opt_g(art, "colombia", PALETTES[pal])
        save_small(icon, outdir / "pack.png")
        sheet({"pack": icon}, outdir / "pack-preview.png")
    else:  # final: python make_icons.py <dir> final <variant>
        variant = sys.argv[3]
        icons = {}
        for name, glyph, pal in (("claims", "flag", "gold"), ("webmap", "globe", "blue"),
                                 ("clientmap", "map", "teal")):
            icon = VARIANTS[variant](art, glyph, PALETTES[pal])
            save_small(icon, outdir / f"{name}-icon.png")
            icons[name] = icon
        sheet(icons, outdir / "icons-preview.png")


if __name__ == "__main__":
    main()

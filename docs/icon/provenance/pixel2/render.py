#!/usr/bin/env python3
"""Round-3 iteration-2 pixel icons (user-feedback revisions).

Only integer nearest-neighbor enlargement, alpha compositing, 90-degree
transpose and analytic isometric projection of real textures. No generated art.

Sources: pixel/sources (1.21.4 client jar extracts + supplied skin) and
pixel2/sources (HUD hardcore heart sprites and the real mob_effect/speed icon,
extracted from the same official client jar).
"""
from pathlib import Path
import hashlib
import json

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
SRC = ROOT.parent / "pixel" / "sources"
SRC2 = ROOT / "sources"
entries = []


def load(name, extra=False):
    base = SRC2 if extra else SRC
    return Image.open(base / (name + ".png")).convert("RGBA")


def canvas(w=32, h=None):
    return Image.new("RGBA", (w, h or w))


def scale(im, factor=2):
    return im.resize((im.width * factor, im.height * factor), Image.Resampling.NEAREST)


def save(project, slug, im, notes, source):
    im.save(ROOT / (slug + ".png"))
    entries.append(
        dict(
            project=project,
            label=notes,
            path="pixel2/" + slug + ".png",
            method="Pillow deterministic native-pixel composition",
            source=source,
            notes=notes,
        )
    )


def face(skin):
    """Player head front: 8x8 base face plus the 8x8 hat overlay layer."""
    result = skin.crop((8, 8, 16, 16))
    result.alpha_composite(skin.crop((40, 8, 48, 16)))
    return result


def iso_block(top_tex, side_tex, overlay_tex, size=16, left_shade=0.88, right_shade=0.72, pitch=1.0):
    """Analytic 2:1 isometric cube of one 16x16 block texture, nearest-sampled.

    Screen mapping: X = (u - v)*s, Y = (u + v)*s/2 - h*s, translated so the cube
    fills a size x size image; u,v are the top-face axes in texels and h the height.
    """
    top = top_tex.convert("RGBA")
    side = side_tex.convert("RGBA").copy()
    overlay = overlay_tex.convert("RGBA").copy()
    overlay.putdata([(r * 145 // 255, g * 189 // 255, b * 89 // 255, a) for r, g, b, a in overlay.get_flattened_data()])
    side.alpha_composite(overlay)
    tp, sp = top.load(), side.load()
    out = Image.new("RGBA", (size, round(size * (pitch + 1) / 2)))
    op = out.load()
    s = size / 32.0
    for y in range(out.height):
        for x in range(size):
            sx, sy = x + 0.5 - 16 * s, y + 0.5 - 16 * s
            xn, yn = sx / s, sy / s
            uu, vv = (xn + 2 * yn / pitch + 32 / pitch) / 2, (2 * yn / pitch + 32 / pitch - xn) / 2
            if 0 <= uu < 16 and 0 <= vv < 16:
                op[x, y] = tp[int(uu), int(vv)]
                continue
            lu = xn + 16
            lh = (lu + 16) * pitch / 2 - yn
            if 0 <= lu < 16 and 0 <= lh < 16:
                r, g, b, a = sp[int(lu), 15 - int(lh)]
                op[x, y] = (int(r * left_shade), int(g * left_shade), int(b * left_shade), a)
                continue
            rv = 16 - xn
            rh = (16 + rv) * pitch / 2 - yn
            if 0 <= rv < 16 and 0 <= rh < 16:
                r, g, b, a = sp[int(rv), 15 - int(rh)]
                op[x, y] = (int(r * right_shade), int(g * right_shade), int(b * right_shade), a)
    return out


SKIN = "Supplied skin (both layers, head front)"
VANILLA = "Official Java 1.21.4 client.jar textures; see provenance.json"

# ---------------------------------------------------------------- BrainageMinigames
# Swords behind, supplied skin face (16x16) on top of everything.
skin_face = face(load("supplied-skin"))
im = canvas()
im.alpha_composite(scale(load("diamond_sword")))
im.alpha_composite(scale(load("iron_sword").transpose(Image.Transpose.ROTATE_90)))
im.alpha_composite(scale(skin_face, 2), (8, 8))
save(
    "BrainageMinigames",
    "brainage-minigames",
    im,
    "Supplied skin face (both layers) doubled to 16x16 and placed in FRONT of everything, centred; diamond sword and the 90-degrees-CCW iron sword behind it.",
    SKIN + " + " + VANILLA,
)

# ---------------------------------------------------------------- AcceleratedDamage
# Real Speed status-effect icon doubled as the background, HUD hardcore half heart
# in front (container outline sprite + hardcore_half fill), both at 2x.
effect = load("speed", extra=True)
container = load("container_hardcore", extra=True)
fill = load("hardcore_half", extra=True)
heart = canvas(9)
heart.alpha_composite(container)
heart.alpha_composite(fill)
for down in (1, 3):
    for left in (3, 5, 7):
        im = canvas(36)
        im.alpha_composite(scale(effect, 2))
        im.alpha_composite(scale(heart, 2), (9 - left, 9 + down))
        save(
            "AcceleratedDamage",
            f"accelerated-damage-down{down}-left{left}",
            im,
            f"Real Speed status-effect icon DOUBLED (36x36) as the background; HUD hardcore half heart at 2x moved {down} px down and {left} px left. HUD relative sizes preserved exactly (effect icon 18x18, heart 9x9, both at 2x).",
            "Official Java 1.21.4 client.jar: mob_effect/speed.png, gui/sprites/hud/heart/container_hardcore.png, gui/sprites/hud/heart/hardcore_half.png",
        )

# ---------------------------------------------------------------- GetEnchantInfo
# First-pass composition (enchanted book doubled + native 16x16 information
# symbol) with the eight pixels the user marked filled in the disc blue.
info = canvas(16)
d = ImageDraw.Draw(info)
d.ellipse((0, 0, 15, 15), fill="#1863ba")
d.rectangle((7, 3, 8, 4), fill="white")
d.rectangle((7, 6, 8, 11), fill="white")
d.rectangle((5, 11, 10, 12), fill="white")
for x, y in ((4, 0), (11, 0), (0, 4), (15, 4), (0, 11), (15, 11), (4, 15), (11, 15)):
    info.putpixel((x, y), (24, 99, 186, 255))
im = scale(load("enchanted_book"))
im.alpha_composite(info, (8, 8))
save(
    "GetEnchantInfo",
    "get-enchant-info",
    im,
    "Enchanted book doubled to 32x32; native 16x16 information symbol centred in front, with the eight marked pixels filled blue ((4,0),(11,0),(0,4),(15,4),(0,11),(15,11),(4,15),(11,15)) so the disc reaches its full symmetric extent.",
    VANILLA,
)

# ---------------------------------------------------------------- MilkablePlayers
# The user kept the first-pass 32x32 composition (milk bucket doubled with the
# doubled Steve face centred in front), so this revision does not re-emit it.

# ---------------------------------------------------------------- SimpleTwitchChat
# Bottom border moved up one pixel so the three dots are vertically centred;
# tail is an exact right-angle isosceles triangle stopping one pixel above the
# image edge, consistent with the empty margin on the other three sides.


def twitch_bubble(with_dots=True):
    bubble = canvas(16)
    d = ImageDraw.Draw(bubble)
    d.rectangle((1, 1, 14, 10), fill="#9146ff")
    d.rectangle((2, 2, 13, 9), fill="white")
    for i in range(4):
        d.rectangle((1, 11 + i, 4 - i, 11 + i), fill="#9146ff")
    if with_dots:
        for x in (4, 7, 10):
            d.rectangle((x, 5, x + 1, 6), fill="black")
    return bubble


bubble = twitch_bubble()
save(
    "SimpleTwitchChat",
    "simple-twitch-chat",
    bubble,
    "Native 16x16 speech bubble: white fill, purple border, three black dots vertically centred in the interior, and a solid purple right-angle isosceles triangle tail (rows 11-14, cols 1-4) that stops one pixel above the bottom edge like the other three margins.",
    "Original deterministic pixel symbol",
)

# ---------------------------------------------------------------- TwitchPlaysMinecraft
# SimpleTwitchChat bubble at 64x scale (1024x1024 canvas, not shrunk), purple
# isosceles play triangle at high resolution inside on the LEFT and the grass
# block at 4x resolution (384 px wide, true-isometric attitude) inside on the RIGHT.
twitch = scale(twitch_bubble(with_dots=False), 64)
d = ImageDraw.Draw(twitch)
# interior of the 64x-scaled bubble spans x 128..895, y 128..605
d.polygon([(232, 206), (232, 525), (423, 365)], fill="#9146ff")
top_tex = load("grass_block_top").copy()
top_tex.putdata(
    [(r * 145 // 255, g * 189 // 255, b * 89 // 255, a) for r, g, b, a in top_tex.get_flattened_data()]
)
grass = iso_block(
    top_tex, load("grass_block_side"), load("grass_block_side_overlay"), size=320, pitch=1.1547
)
twitch.alpha_composite(grass, (472, 194))
save(
    "TwitchPlaysMinecraft",
    "twitch-plays-minecraft",
    twitch,
    "SimpleTwitchChat bubble at its native 16x scale rendered 64x (1024x1024, not shrunk): purple isosceles play triangle at 4x resolution inside on the left, 4x-resolution 384x414 isometric grass block inside on the right at the true isometric attitude (35.264 deg, top face 1:sqrt(3)), top face tinted with the plains grass colour (#91BD59). Both elements are centred on the centre of the SPEECH BUBBLE'S INTERIOR (x 128..895, y 128..605, centre 511.5,366.5) rather than the image centre: triangle 232..423 (y 206..525), block 472..791 (y 194..538, 320x345 keeping the 4x aspect), equal 104 px interior side margins and equal 66/67 px interior top/bottom margins around the block. The block canvas is sized to the cube's full projected height so no corner is clipped.",
    VANILLA,
)

# ---------------------------------------------------------------- NPCAddons
# The 7x7 top-right plus composition was confirmed perfect by the user, so this
# revision does not re-emit NPCAddons; the first-pass files remain authoritative.

# ---------------------------------------------------------------- BrainageLib (service variants)
SERVICE = {
    "brainage-lib-msaa4": (
        "BrainageLib — existing NMSR renderer, 4x MSAA, no SMAA",
        "Locally self-hosted unmodified NMSR (commit bf87e8275005) rendering the authorized supplied skin head isometrically, both layers. Rendering configuration sample_count=4, use_smaa=false removes the two 1-pixel side protrusions and cleans the edges while keeping the identical silhouette bounding box.",
        {"sample_count": 4, "use_smaa": False},
    ),
    "brainage-lib-default": (
        "BrainageLib — existing NMSR renderer, default settings (previous output)",
        "Same service and skin with no [rendering] overrides — this is the file reviewed previously, kept for comparison. It contains two 1-pixel protrusions at the left and right silhouette extremes (y=381).",
        {"sample_count": None, "use_smaa": None},
    ),
}
for slug, (label, notes, rendering) in SERVICE.items():
    path = ROOT / (slug + ".png")
    if not path.exists():
        continue
    with Image.open(path) as chk:
        chk.load()
        size = list(chk.size)
    entries.append(
        dict(
            project="BrainageLib",
            label=label,
            path="pixel2/" + slug + ".png",
            method="Existing NMSR renderer, self-hosted unmodified; multipart supplied skin, both layers",
            source="https://github.com/NickAcPT/nmsr-rs commit bf87e8275005601c12768a9d74c016d0337d58b0, /headiso, rendering="
            + json.dumps(rendering)
            + f"; output {size[0]}x{size[1]} sha256 {hashlib.sha256(path.read_bytes()).hexdigest()[:16]}",
            notes=notes,
        )
    )

(ROOT / "manifest.json").write_text(json.dumps({"entries": entries}, indent=2) + "\n")
report = []
for e in entries:
    p = ROOT / Path(e["path"]).name
    with Image.open(p) as chk:
        chk.load()
        report.append(dict(path=e["path"], size=list(chk.size), mode=chk.mode, alpha=chk.getextrema()[3], sha256=hashlib.sha256(p.read_bytes()).hexdigest()[:16]))
print(json.dumps(report, indent=2))

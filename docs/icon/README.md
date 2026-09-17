# SimpleTwitchChat icon

## What this is

`docs/icon/icon.png` — the mod's icon: 16x16 PNG, 8-bit RGBA, non-interlaced, 158 bytes,
sha256 `267929ec3e9ec6227886c726a5754c7c1f1b7fe3bd6e340bdfb18a6a01e24df4`.

## How it was made

**Original generated pixel art, drawn natively at 16x16** — no textures, no renderer, no
antialiasing, no resampling of any kind. `provenance/pixel2/render.py` (Pillow 12.3.0)
paints it with `ImageDraw` rectangles only:

- purple `#9146FF` border rectangle at (1,1)-(14,10);
- white fill rectangle at (2,2)-(13,9);
- a solid purple right-angle isosceles tail: rows 11-14, columns 1-4, with the row width
  shrinking by one pixel per row, stopping one pixel above the bottom edge like the other
  three margins;
- three black 2x2 dots at x = 4, 7, 10, rows 5-6 — vertically centred in the interior.

Because the pixels are painted directly at the icon's final size, this icon has **no source
textures at all**: it is not derived from a vanilla asset, a capture, a shader or a skin.

## Provenance files

| file | what it is |
|---|---|
| `provenance/pixel2/render.py` | the author script that generated this icon (and the other round-3 pixel icons) |
| `provenance/pixel2/metadata.json` | this icon's entry extracted from `provenance/from-round3/pixel2/manifest.json`: label, method, source line, notes |
| `provenance/pixel/sources/*.png`, `provenance/pixel2/sources/*.png` | the textures the shared script reads for its *other* sections (swords, skin, grass block, effect/heart sprites) — none of them is used by this icon; they are kept so the script runs unmodified |
| `provenance/pixel/source-provenance.json` | jar member / sha256 / origin per source file, and which files this icon uses (none) |

## How to regenerate

From `docs/icon/provenance` (Pillow 12.3.0):

```
python3 pixel2/render.py
```

This rewrites `pixel2/simple-twitch-chat.png` and the other round-3 pixel icons; compare
`pixel2/simple-twitch-chat.png` with the sha256 above.

## Notes

- This bubble is the source symbol for the TwitchPlaysMinecraft icon, which re-uses the same
  `twitch_bubble()` function at 64x (that icon is a separate mod's deliverable).
- The three dots are 2x2, not 3x3, so the bubble reads correctly at 16x16 without a
  hint of blur; the icon is intentionally kept at native scale rather than being upscaled
  like most other icons in this set.
- Not copied: the other candidate variants of this and other mods' icons, the retired
  `get-enchant-info.png`, and the 28 MB `pixel2/jar/client-1.21.4.jar` (over the 5 MB
  single-file limit; this icon needs no textures to regenerate).

## Working-tree note

The round-3 working tree that produced this icon was cleaned up after integration. Every file needed to regenerate the icon was copied into `provenance/`; the copies live under `provenance/from-round3/` when they came from the working tree. Any remaining `round3/...` mention records where something came from, not a path that still exists.

# SimpleTwitchChat icon

## What this is

`docs/icon/icon.png` is the mod's icon: 1024x1024 PNG, 8-bit RGBA (opaque), non-interlaced,
1,144,115 bytes, sha256
`4b09cd939c58c9d5f9c5721cb296126dc3073d09b1843fa29521a2320debb044`.

It is the unretouched Blender render of `provenance/final.blend`: no painting, compositing,
sharpening or colour correction. The mod ships byte-identical copies at
`common/src/main/resources/assets/simpletwitchchat/icon.png` (the in-jar icon for Fabric and
NeoForge, and the file the conventions plugin uploads as the Modrinth project icon) and
`fabric/src/gametest/resources/assets/simpletwitchchat/icon.png`.

## How it was made

A Minecraft-style 3D scene built in Blender **5.1.1** (build `b70da489d7f4`) from vanilla
textures: a solid oak-plank sign board on a short oak-log post with a small oak-log stub behind
it, purple text dashes and a purple Twitch-style chat bubble with three white dots, on a
lavender studio floor with a real contact shadow. Three images are packed inside the `.blend`,
so it renders without a client jar:

| packed image | sha256 |
|---|---|
| vanilla `oak_planks.png` | `3a33db67a3ba30537d0890a5cb37c8087ca336be89cbb1393a71c23224e361a8` |
| vanilla `oak_log.png` | `060c93128fb65aa350a84e4b6576e616c47d40211bbe537cfe923a81d072e30c` |
| authored `purple-pixels.png` | `b7924cb10bf9f81a9cc0260bd1e652bb5a70fd74647fe12cfd37a5ddb31719dd` |

All textures use `Closest` sampling and block-face UVs at 16 texels per block, so they keep
their Minecraft pixel scale.

Render settings: Cycles on CPU, 4 fixed threads, 1024x1024, 128 samples, seed `20261014`,
denoising on; 40.2 s, run inside `heavy.slice`. The render was made from the saved and
reopened `.blend`.

The scene went through eight review rounds. The last change (round 8) extended the oak-log stub
behind the board down to the board's bottom plane so no background shows through under its
overhang; the stub stays a single closed cuboid with continuous bark UVs. The full round
history lives outside the repository; only the final scene and the round-8 scripts and records
are kept here.

## Provenance files

| file | what it is |
|---|---|
| `provenance/final.blend` | the final scene with all three images packed, sha256 `1d871dc52e37af800b27f9a2114f64475182c2ed9a9d9d3a551b058789fe1d34` |
| `provenance/extend-stub.py` | the round-8 Blender script: extends the stub's four bottom vertices and lower bark UVs, saves, reopens, checks invariants and gap rays, and renders. It reads the round-6 scene from `source-r6/`, which is not copied; it documents the last edit and is not needed to re-render |
| `provenance/package-evidence.py` | the round-8 packaging script (128/64 Lanczos thumbnails, comparison crops, hashes); also needs the uncopied round-6 files |
| `provenance/blender.sh` | the launcher used; it refuses to run outside `heavy.slice` and points at the Blender 5.1.1 runtime that was installed under `/tmp/icon-ideas/runtime-r3/` |
| `provenance/render-metadata.json` | Blender version/build, engine, device, samples, seed, threads, render time, output and `.blend` sha256 |
| `provenance/exports.json` | sha256, mode and size of every exported file |
| `provenance/invariant-report.json` | the checks that only the stub geometry/UVs changed from round 6 |
| `provenance/source-evidence.json` | hashes of the round-6 inputs |
| `provenance/crops.json` | the comparison crop used in review |
| `provenance/delivery-verification.json` | final delivery checks (hashes match, PNGs decode, scene reopened and rendered) |

## How to regenerate

Install Blender 5.1.1, then from `docs/icon/provenance` render the packed scene through `heavy`:

```sh
heavy status
heavy --label stc-icon -- blender --background final.blend -noaudio --threads 4 \
  --render-output /tmp/stc-icon- --render-frame 1
```

(`blender.sh` can replace `blender` if the original runtime still exists.) Blender writes
`/tmp/stc-icon-0001.png`; copy it over `docs/icon/icon.png` and the two in-repo copies listed
above. CPU Cycles with the same seed is close to, but not guaranteed to be, bit-identical with
the shipped icon; compare against the sha256 above.

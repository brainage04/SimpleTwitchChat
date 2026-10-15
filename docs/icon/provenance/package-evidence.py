"""Package unretouched Blender output, matched native crops and gap measurements."""
from collections import Counter
from pathlib import Path
import argparse
import hashlib
import json

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
ICON = ROOT.parent
DELIVERY = ICON.parent


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def dump(name, value):
    (ROOT / name).write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')


def metrics(a, b, mask):
    differences = np.abs(a[:, :, :3].astype(np.int16) - b[:, :, :3].astype(np.int16))[mask]
    return {'pixels': int(mask.sum()), 'changed_pixels': int(np.any(differences != 0, axis=1).sum()),
            'maximum_absolute_channel_difference_8bit': int(differences.max()),
            'mean_absolute_channel_difference_8bit': float(differences.mean())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--font', required=True)
    args = parser.parse_args()
    before = Image.open(ROOT / 'source-r6/icon.png').convert('RGBA')
    after = Image.open(ICON / 'icon.png').convert('RGBA')
    assert before.size == after.size == (1024, 1024)
    assert after.getchannel('A').getextrema() == (255, 255)
    render_metadata = json.loads((ROOT / 'render-metadata.json').read_text())
    assert sha(ICON / 'icon.png') == render_metadata['output_sha256']
    for size in (128, 64):
        after.resize((size, size), Image.Resampling.LANCZOS).save(ICON / f'icon-{size}.png')
    crops = json.loads((ROOT / 'crops.json').read_text())
    box = tuple(crops['crop_box'])
    assert box == (384, 208, 574, 298)
    crop_size = ((box[2] - box[0]) * 4, (box[3] - box[1]) * 4)
    canvas = Image.new('RGB', (1600, 1312), '#181b20')
    draw = ImageDraw.Draw(canvas)
    title = ImageFont.truetype(args.font, 25)
    caption = ImageFont.truetype(args.font, 19)
    small = ImageFont.truetype(args.font, 17)
    placements = []
    for label, image, x in (('Round 6 | original', before, 20), ('Round 8 | bottom extended only', after, 812)):
        draw.text((x, 14), label, fill='#f4f4f6', font=title)
        canvas.paste(image.resize((768, 768), Image.Resampling.LANCZOS).convert('RGB'), (x, 52))
        draw.text((x, 840), label.split(' | ')[0] + ' | stub base | exact 4x nearest-neighbor', fill='#f4f4f6', font=caption)
        crop = image.crop(box).resize(crop_size, Image.Resampling.NEAREST).convert('RGB')
        canvas.paste(crop, (x, 876))
        # Exact output equality, not an assertion about the rescaled screenshot.
        pasted = canvas.crop((x, 876, x + crop_size[0], 876 + crop_size[1]))
        assert pasted.tobytes() == crop.tobytes()
        placements.append({'round': label.split(' | ')[0], 'xy': [x, 876], 'size': list(crop_size)})
    draw.text((20, 1252), 'Native region [384,208,574,298), matching the visible region in Thomas\'s marked screenshot.', fill='#c6c8cf', font=small)
    draw.text((20, 1280), 'Crops contain only source PNG pixels: no red square, overlays, retouching, sharpening or color changes.', fill='#c6c8cf', font=small)
    canvas.save(DELIVERY / 'compare.png')
    crops['comparison_size'] = list(canvas.size)
    crops['comparison_crop_placements'] = placements
    crops['comparison_full_icon_preview_size'] = [768, 768]
    crops['comparison_crop_pixel_equality_checked'] = True
    dump('crops.json', crops)
    gap = json.loads((ROOT / 'gap-verification.json').read_text())
    mask = np.zeros((1024, 1024), dtype=bool)
    for x, y in gap['gap_pixel_centers_xy']:
        mask[y, x] = True
    Image.fromarray(mask.astype(np.uint8) * 255).save(ROOT / 'former-gap-mask.png')
    a, b = np.array(before), np.array(after)
    old_values = a[:, :, :3][mask].astype(np.int16)
    new_values = b[:, :, :3][mask].astype(np.int16)
    old_cool = old_values[:, 2] >= old_values[:, 0]
    new_cool = new_values[:, 2] >= new_values[:, 0]
    # This is a separate color check, not the geometric definition of the gap.
    # The local lavender background is blue-dominant, oak is red-dominant.
    color_check = {
        'criterion': 'A local background-like pixel has blue >= red. Geometry-derived former-gap pixel centers only.',
        'tested_pixels': int(mask.sum()),
        'background_like_pixels_before': int(old_cool.sum()),
        'background_like_pixels_after': int(new_cool.sum()),
        'final_red_minus_blue_minimum': int((new_values[:, 0] - new_values[:, 2]).min()),
        'final_red_minus_blue_maximum': int((new_values[:, 0] - new_values[:, 2]).max()),
        'final_all_red_dominant_wood': not bool(new_cool.any()),
        'final_rgb_minimum': new_values.min(axis=0).tolist(),
        'final_rgb_maximum': new_values.max(axis=0).tolist(),
        'alpha_difference_maximum': int(np.abs(a[:, :, 3].astype(np.int16) - b[:, :, 3].astype(np.int16)).max()),
        'geometric_gap_background_centers_after': gap['background_pixel_centers_after'],
        'geometric_gap_background_subpixels_after': gap['background_subpixels_after'],
    }
    dump('gap-pixel-check.json', color_check)
    assert color_check['final_all_red_dominant_wood'], color_check
    assert gap['background_pixel_centers_after'] == gap['background_subpixels_after'] == 0
    crop_mask = np.zeros_like(mask)
    crop_mask[box[1]:box[3], box[0]:box[2]] = True
    dump('pixel-differences.json', {
        'whole_image': metrics(a, b, np.ones_like(mask)),
        'matched_crop': metrics(a, b, crop_mask),
        'former_gap': metrics(a, b, mask),
        'outside_matched_crop': metrics(a, b, ~crop_mask),
        'note': 'Same scene parameters except the requested geometry/UV extension. Indirect illumination and sampling can change exterior pixels; no compositing is used to force equality.'})
    source = json.loads((ROOT / 'source-evidence.json').read_text())
    source_root = Path(source['round6_root'])
    current = {str(p.relative_to(source_root)): sha(p) for p in sorted(source_root.rglob('*')) if p.is_file()}
    preservation = {'round6_files_before': source['round6_files_before'], 'round6_files_after': current,
                    'checked_file_count': len(current), 'all_source_files_unchanged': current == source['round6_files_before'],
                    'feedback_images_unchanged': all(sha(DELIVERY / name) == digest for name, digest in source['feedback'].items()),
                    'round7_accessed': False}
    dump('preservation-verification.json', preservation)
    assert preservation['all_source_files_unchanged'] and preservation['feedback_images_unchanged']
    exports = {}
    for path in (ICON / 'icon.png', ICON / 'icon-128.png', ICON / 'icon-64.png', DELIVERY / 'compare.png'):
        with Image.open(path) as image:
            exports[str(path.relative_to(DELIVERY))] = {'size': list(image.size), 'mode': image.mode, 'sha256': sha(path)}
    exports['simpletwitchchat/provenance/final.blend'] = {'sha256': sha(ROOT / 'final.blend')}
    dump('exports.json', exports)
    print(json.dumps({'gap_pixel_check': color_check, 'exports': exports,
                      'source_preserved': preservation['all_source_files_unchanged']}, indent=2))


if __name__ == '__main__':
    main()

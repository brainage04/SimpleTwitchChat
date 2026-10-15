"""Extend only the round-6 stub's lower vertices and metric bark UVs, then render."""
from array import array
from collections import Counter
from pathlib import Path
import copy
import hashlib
import json
import math
import runpy
import struct
import time

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / 'source-r6'
SOURCE_SHA = '51921fb6feeab6a5c396ff793e4f5182641dce650c979dde38a44608a735b523'
STUB = 'Small upper oak-log stub left of board center'
BOARD = 'Solid oak board - single closed cuboid'
POST = 'Centered short thick square oak-log post'
FLOOR = 'Lavender studio floor with real contact shadow'
UV_NAME = 'Block face metric UV: 16 texels per unit'
CROP = tuple(json.loads((ROOT / 'crops.json').read_text())['crop_box'])
# Import only the existing read-only recorder. Its historical main is not called.
recorder = runpy.run_path(str(SOURCE / 'scene-recorder.py'))


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def dump(name, value):
    (ROOT / name).write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')


def f32(value):
    return struct.unpack('f', struct.pack('f', value))[0]


def buffer_hash(collection, field, width, kind):
    values = array(kind, [0]) * (len(collection) * width)
    collection.foreach_get(field, values)
    return hashlib.sha256(values.tobytes()).hexdigest()


def snapshot():
    state = recorder['snapshot']()
    state['mesh_buffer_hashes'] = {
        obj.name: {
            'vertices_float32': buffer_hash(obj.data.vertices, 'co', 3, 'f'),
            'edges_int32': buffer_hash(obj.data.edges, 'vertices', 2, 'i'),
            'loop_vertices_int32': buffer_hash(obj.data.loops, 'vertex_index', 1, 'i'),
            'polygon_starts_int32': buffer_hash(obj.data.polygons, 'loop_start', 1, 'i'),
            'polygon_lengths_int32': buffer_hash(obj.data.polygons, 'loop_total', 1, 'i'),
            'uv_float32': {uv.name: buffer_hash(uv.data, 'uv', 2, 'f')
                           for uv in obj.data.uv_layers},
        } for obj in bpy.context.scene.objects if obj.type == 'MESH'
    }
    return state


def bounds(obj):
    points = [obj.matrix_world @ v.co for v in obj.data.vertices]
    return [[min(p[i] for p in points) for i in range(3)],
            [max(p[i] for p in points) for i in range(3)]]


def project(point):
    p = world_to_camera_view(bpy.context.scene, bpy.context.scene.camera, Vector(point))
    return [float(p.x) * 1024, (1 - float(p.y)) * 1024]


def camera_ray(x, y):
    camera = bpy.context.scene.camera
    assert camera.data.type == 'ORTHO'
    assert camera.data.shift_x == camera.data.shift_y == 0
    s = camera.data.ortho_scale
    origin = camera.matrix_world @ Vector(((x / 1024 - 0.5) * s, (0.5 - y / 1024) * s, 0))
    direction = (camera.matrix_world.to_3x3() @ Vector((0, 0, -1))).normalized()
    return origin, direction


def ray_box(origin, direction, box):
    near, far = -math.inf, math.inf
    for axis in range(3):
        if abs(direction[axis]) < 1e-12:
            if not box[0][axis] <= origin[axis] <= box[1][axis]:
                return None
            continue
        a = (box[0][axis] - origin[axis]) / direction[axis]
        b = (box[1][axis] - origin[axis]) / direction[axis]
        near, far = max(near, min(a, b)), min(far, max(a, b))
    return near if far >= max(near, 0) else None


def first_hit(graph, origin, direction):
    result = bpy.context.scene.ray_cast(graph, origin, direction)
    return result[4].name if result[0] else 'WORLD'


def baseline_gap(extension):
    graph = bpy.context.evaluated_depsgraph_get()
    centers = []
    counts = Counter()
    for y in range(CROP[1], CROP[3]):
        for x in range(CROP[0], CROP[2]):
            origin, direction = camera_ray(x + 0.5, y + 0.5)
            name = first_hit(graph, origin, direction)
            counts[name] += 1
            if name in (FLOOR, 'WORLD') and ray_box(origin, direction, extension) is not None:
                centers.append([x, y])
    assert centers, 'The delivered round-6 gap must be represented in this crop'
    # Cover all neighboring edge pixels too; use 8x8 subpixel rays per candidate pixel.
    candidates = sorted({(x + dx, y + dy) for x, y in centers
                         for dx in (-1, 0, 1) for dy in (-1, 0, 1)})
    samples = []
    for x, y in candidates:
        for sy in range(8):
            for sx in range(8):
                point = [x + (sx + 0.5) / 8, y + (sy + 0.5) / 8]
                origin, direction = camera_ray(*point)
                if ray_box(origin, direction, extension) is not None:
                    name = first_hit(graph, origin, direction)
                    if name in (FLOOR, 'WORLD'):
                        samples.append(point)
    return {'crop_box': CROP, 'gap_pixel_centers_xy': centers,
            'gap_subpixel_positions_xy': samples,
            'crop_baseline_first_hits': dict(counts),
            'subpixel_grid': [8, 8], 'candidate_pixels': len(candidates),
            'definition': 'Camera rays that hit background in round 6 and intersect the requested downward-only extension volume. Unchanged background outside the stub silhouette is not a gap.'}


def final_gap(before):
    graph = bpy.context.evaluated_depsgraph_get()
    center_hits = Counter(first_hit(graph, *camera_ray(x + 0.5, y + 0.5))
                          for x, y in before['gap_pixel_centers_xy'])
    subpixel_hits = Counter(first_hit(graph, *camera_ray(*point))
                            for point in before['gap_subpixel_positions_xy'])
    center_background = sum(center_hits[name] for name in (FLOOR, 'WORLD'))
    subpixel_background = sum(subpixel_hits[name] for name in (FLOOR, 'WORLD'))
    result = {**before, 'final_gap_center_first_hits': dict(center_hits),
              'final_gap_subpixel_first_hits': dict(subpixel_hits),
              'background_pixel_centers_before': len(before['gap_pixel_centers_xy']),
              'background_pixel_centers_after': center_background,
              'background_subpixels_before': len(before['gap_subpixel_positions_xy']),
              'background_subpixels_after': subpixel_background,
              'all_gap_rays_hit_extended_stub': set(center_hits) == set(subpixel_hits) == {STUB}}
    dump('gap-verification.json', result)
    assert center_background == subpixel_background == 0
    assert result['all_gap_rays_hit_extended_stub']
    return result


def main():
    assert 'heavy.slice' in Path('/proc/self/cgroup').read_text()
    assert bpy.app.version_string == '5.1.1'
    assert bpy.app.build_hash.decode() == 'b70da489d7f4'
    assert sha(SOURCE / 'final.blend') == SOURCE_SHA
    assert not (ROOT / 'final.blend').exists(), 'Refusing to replace a saved deliverable'
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE / 'final.blend'))
    bpy.context.view_layer.update()
    before = snapshot()
    assert before == json.loads((SOURCE / 'final-scene.json').read_text())
    dump('baseline-scene.json', before)
    stub, board, post = (bpy.data.objects[name] for name in (STUB, BOARD, POST))
    old_bounds, board_bounds, post_bounds = bounds(stub), bounds(board), bounds(post)
    old_bottom, top = old_bounds[0][2], old_bounds[1][2]
    new_bottom = board_bounds[0][2]
    assert old_bottom == board_bounds[1][2]
    assert new_bottom < post_bounds[1][2] < old_bottom
    delta = new_bottom - old_bottom
    extension = [old_bounds[0][:], old_bounds[1][:]]
    extension[0][2], extension[1][2] = new_bottom, old_bottom
    gap_before = baseline_gap(extension)
    mesh = stub.data
    assert len(mesh.vertices) == 8 and len(mesh.polygons) == 6 and len(mesh.uv_layers) == 1
    assert stub.matrix_world == __import__('mathutils').Matrix.Identity(4)
    lower = [v.index for v in mesh.vertices if v.co.z == old_bottom]
    assert lower == [0, 1, 2, 3]
    side_loops = sorted(li for p in mesh.polygons if abs(p.normal.z) < 0.5
                        for li in p.loop_indices if mesh.loops[li].vertex_index in lower)
    assert side_loops == [8, 9, 12, 13, 16, 17, 20, 21]
    expected = copy.deepcopy(before)
    expected_stub = expected['objects'][STUB]
    for vi in lower:
        mesh.vertices[vi].co.z = new_bottom
        expected_stub['mesh']['vertices'][vi][2] = new_bottom
    uv = mesh.uv_layers[UV_NAME]
    for li in side_loops:
        expected_v = f32(expected_stub['mesh']['uv_layers'][UV_NAME][li][1] + delta)
        uv.data[li].uv.y = expected_v
        expected_stub['mesh']['uv_layers'][UV_NAME][li][1] = expected_v
    # Metadata follows the actual operation; no material or texture change.
    stub['size_blocks_xyz'] = [0.25, 0.25, 1.03125]
    stub['texture_note'] = 'Original upper-stub footprint; one continuous oak log extends behind the board to its bottom face'
    expected_stub['custom_properties']['size_blocks_xyz'] = [0.25, 0.25, 1.03125]
    expected_stub['custom_properties']['texture_note'] = stub['texture_note']
    mesh.update()
    bpy.context.view_layer.update()
    # Expected byte hashes are calculated from the explicitly permitted deltas.
    expected['mesh_buffer_hashes'][STUB]['vertices_float32'] = hashlib.sha256(
        array('f', [c for v in expected_stub['mesh']['vertices'] for c in v]).tobytes()).hexdigest()
    expected['mesh_buffer_hashes'][STUB]['uv_float32'][UV_NAME] = hashlib.sha256(
        array('f', [c for v in expected_stub['mesh']['uv_layers'][UV_NAME] for c in v]).tobytes()).hexdigest()
    assert snapshot() == expected
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / 'final.blend'), relative_remap=False)
    bpy.ops.wm.open_mainfile(filepath=str(ROOT / 'final.blend'))
    bpy.context.view_layer.update()
    after = snapshot()
    dump('final-scene.json', after)
    old_vertices = before['objects'][STUB]['mesh']['vertices']
    new_vertices = after['objects'][STUB]['mesh']['vertices']
    old_uv = before['objects'][STUB]['mesh']['uv_layers'][UV_NAME]
    new_uv = after['objects'][STUB]['mesh']['uv_layers'][UV_NAME]
    checks = {key + '_exact': before[key] == after[key] for key in before
              if key not in ('objects', 'mesh_buffer_hashes')}
    checks.update({
        'entire_record_equals_only_requested_delta': after == expected,
        'all_24_other_objects_exact': all(before['objects'][n] == after['objects'][n] for n in before['objects'] if n != STUB),
        'other_mesh_float_and_topology_buffers_exact': all(before['mesh_buffer_hashes'][n] == after['mesh_buffer_hashes'][n] for n in before['mesh_buffer_hashes'] if n != STUB),
        'only_four_lower_vertices_changed': [i for i, (a, b) in enumerate(zip(old_vertices, new_vertices)) if a != b] == lower,
        'all_stub_xy_coordinates_exact': all(a[:2] == b[:2] for a, b in zip(old_vertices, new_vertices)),
        'all_top_vertices_exact': old_vertices[4:] == new_vertices[4:],
        'only_eight_lower_side_v_coordinates_changed': [i for i, (a, b) in enumerate(zip(old_uv, new_uv)) if a != b] == side_loops and all(a[0] == b[0] for a, b in zip(old_uv, new_uv)),
        'top_and_bottom_face_uvs_exact': old_uv[:8] == new_uv[:8],
        'side_vertical_metric_uv_exact': all(new_uv[li + 2][1] - new_uv[li][1] == top - new_bottom for li in (8, 12, 16, 20)),
        'nominal_extension_14_texels': abs(-delta * 16 - 14) < 1e-6,
        'all_three_source_images_still_packed': len(after['images']) == 3 and all(v['packed_sha256'] for v in after['images'].values()),
    })
    report = {'all_checks_pass': all(checks.values()), 'checks': checks,
              'old_bottom_z': old_bottom, 'new_bottom_z': new_bottom, 'fixed_top_z': top,
              'downward_length_blocks': -delta, 'downward_length_texels': -delta * 16,
              'old_bounds': old_bounds, 'new_bounds': bounds(bpy.data.objects[STUB]),
              'board_bounds': board_bounds, 'unchanged_post_bounds': post_bounds,
              'changed_vertex_indices': lower, 'changed_uv_loop_indices': side_loops,
              'old_lower_side_v': old_uv[8][1], 'new_lower_side_v': new_uv[8][1],
              'fixed_upper_side_v': new_uv[10][1],
              'uv_policy': 'Keep upper UVs and every U coordinate. Lower side V changes by the same amount as Z; 16x16 packed oak_log, REPEAT, Closest, no stretching.',
              'source_blend_sha256': SOURCE_SHA, 'final_blend_sha256': sha(ROOT / 'final.blend'),
              'packed_image_hashes': {name: value['packed_sha256'] for name, value in after['images'].items()},
              'projected_old_lower_corners': [project(v) for v in old_vertices[:4]],
              'projected_new_lower_corners': [project(v) for v in new_vertices[:4]]}
    dump('invariant-report.json', report)
    assert report['all_checks_pass'], [k for k, v in checks.items() if not v]
    gap = final_gap(gap_before)
    scene = bpy.context.scene
    assert scene.render.engine == 'CYCLES' and scene.cycles.device == 'CPU'
    assert scene.render.resolution_x == scene.render.resolution_y == 1024
    assert scene.render.resolution_percentage == 100 and scene.cycles.samples == 128
    assert scene.render.threads_mode == 'FIXED' and scene.render.threads == 4
    assert scene.cycles.seed == 20261014 and scene.cycles.use_denoising
    assert scene.render.filepath == '//../icon.png'
    start = time.perf_counter()
    bpy.ops.render.render(write_still=True)
    elapsed = time.perf_counter() - start
    metadata = {'output': str(ROOT.parent / 'icon.png'), 'output_sha256': sha(ROOT.parent / 'icon.png'),
                'seconds': elapsed, 'blender': bpy.app.version_string, 'build_hash': bpy.app.build_hash.decode(),
                'engine': scene.render.engine, 'device': scene.cycles.device, 'threads': scene.render.threads,
                'size': [1024, 1024], 'samples': scene.cycles.samples, 'seed': scene.cycles.seed,
                'denoising': scene.cycles.use_denoising, 'rendered_reopened_saved_blend': True,
                'cgroup': Path('/proc/self/cgroup').read_text().strip(),
                'patch_sha256': sha(__file__), 'saved_blend_sha256': sha(ROOT / 'final.blend')}
    dump('render-metadata.json', metadata)
    print('STUB_EXTENSION_COMPLETE ' + json.dumps({'checks': checks,
          'background_pixel_centers_after': gap['background_pixel_centers_after'],
          'background_subpixels_after': gap['background_subpixels_after'], 'render': metadata}), flush=True)


if __name__ == '__main__':
    main()

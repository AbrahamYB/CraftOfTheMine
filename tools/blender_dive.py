"""The Tears of the Kingdom "dive" of the rider, authored as keyframes in Blender and baked into src/main/resources/assets/botwclimb/totk/dive.json.
Run (Blender 5, no window):  blender -b --python tools/blender_dive.py -- <repo root> [preview output folder]

What the footage shows (up.mov, in the void, character seen from behind, 8 frames a second): the rider is not spinning. They are
diving upward: for about 12 ticks they hang upright with the right arm stretched up over the head and the legs swaying, then they
lean slightly into the dive (head still up, about 10 degrees), hold it for about 6 ticks, lean
back upright and sway again. The whole cycle takes 20 ticks (1.0 s) and repeats for as long as the void lasts.

The rig is six boxes sharing the proportions of the player model; only their rotations are keyed. The baked channels are the ones
the mod needs, in radians (arms and legs: Minecraft's xRot, positive swings forward) except `tilt`, which is the roll of the whole
body in degrees (positive: head toward the right of the screen). One sample per tick, the last equal to the first so it loops.
"""
import json
import math
import os
import sys

import bpy

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
root = args[0] if args else "."
preview = args[1] if len(args) > 1 else None
PERIOD = 20

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.fps = 20
scene.frame_start, scene.frame_end = 0, PERIOD


def box(name, size, loc, parent=None):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    o = bpy.context.active_object
    o.name = name
    o.scale = size
    if parent:
        o.parent = parent
    return o


# joints: empties that carry the rotation, boxes hang off them
rig = bpy.data.objects.new("rig", None)
scene.collection.objects.link(rig)           # rotates about the view axis (the roll)
body_j = bpy.data.objects.new("body", None); scene.collection.objects.link(body_j); body_j.parent = rig; body_j.location = (0, 0, 0)
arm_r = bpy.data.objects.new("armR", None); scene.collection.objects.link(arm_r); arm_r.parent = body_j; arm_r.location = (-0.375, 0, 0.375)
arm_l = bpy.data.objects.new("armL", None); scene.collection.objects.link(arm_l); arm_l.parent = body_j; arm_l.location = (0.375, 0, 0.375)
leg_r = bpy.data.objects.new("legR", None); scene.collection.objects.link(leg_r); leg_r.parent = body_j; leg_r.location = (-0.125, 0, -0.375)
leg_l = bpy.data.objects.new("legL", None); scene.collection.objects.link(leg_l); leg_l.parent = body_j; leg_l.location = (0.125, 0, -0.375)
box("torso", (0.5, 0.25, 0.75), (0, 0, 0), body_j)
box("head", (0.5, 0.5, 0.5), (0, 0, 0.625), body_j)
box("armR_box", (0.25, 0.25, 0.75), (0, 0, -0.375), arm_r).parent = arm_r
box("armL_box", (0.25, 0.25, 0.75), (0, 0, -0.375), arm_l).parent = arm_l
box("legR_box", (0.25, 0.25, 0.75), (0, 0, -0.375), leg_r).parent = leg_r
box("legL_box", (0.25, 0.25, 0.75), (0, 0, -0.375), leg_l).parent = leg_l

# ---- keys: (tick, value). Interpolation is Blender's default (Bezier), so every move eases in and out.
KEYS = {
    # Swimming upward, head first and upright. The arms stay stretched overhead and open and close sideways (the pull), once per
    # 20-tick cycle. The legs do NOT go sideways: they flutter-kick, one up while the other goes down, forward and back, twice per
    # cycle (a kick every 10 ticks), together with the pull.
    "tilt":  [(0, 0), (20, 0)],
    "armR":  [(0, -3.05), (20, -3.05)],
    "armL":  [(0, -3.05), (20, -3.05)],
    "legR":  [(0, 0.0), (5, 0.55), (10, 0.0), (15, -0.55), (20, 0.0)],
    "legL":  [(0, 0.0), (5, -0.55), (10, 0.0), (15, 0.55), (20, 0.0)],
    "lean":  [(0, 0.0), (20, 0.0)],
    "legRz": [(0, -0.04), (20, -0.04)],
    "legLz": [(0, 0.04), (20, 0.04)],
    "armRz": [(0, 0.06), (10, 0.6), (20, 0.06)],
    "armLz": [(0, -0.06), (10, -0.6), (20, -0.06)],
}
# where each channel lives on the rig
TARGET = {
    "tilt": (rig, 1, math.radians(1)),     # degrees keyed as radians*? (keyed in degrees, stored as radians)
    "armR": (arm_r, 0, 1.0), "armL": (arm_l, 0, 1.0),
    "legR": (leg_r, 0, 1.0), "legL": (leg_l, 0, 1.0),
    "lean": (body_j, 0, 1.0),
    "legRz": (leg_r, 1, 1.0), "legLz": (leg_l, 1, 1.0),
    "armRz": (arm_r, 1, 1.0), "armLz": (arm_l, 1, 1.0),
}
for ch, keys in KEYS.items():
    obj, axis, scale = TARGET[ch]
    for tick, val in keys:
        scene.frame_set(tick)
        obj.rotation_euler[axis] = val * scale
        obj.keyframe_insert("rotation_euler", index=axis, frame=tick)

# ---- bake: evaluate the animation at every tick
samples = []
for tick in range(PERIOD + 1):
    scene.frame_set(tick)
    bpy.context.view_layer.update()
    samples.append({
        "tilt": round(math.degrees(rig.rotation_euler[1]), 3),
        "armR": round(arm_r.rotation_euler[0], 4), "armL": round(arm_l.rotation_euler[0], 4),
        "legR": round(leg_r.rotation_euler[0], 4), "legL": round(leg_l.rotation_euler[0], 4),
        "lean": round(body_j.rotation_euler[0], 4),
        "legRz": round(leg_r.rotation_euler[1], 4), "legLz": round(leg_l.rotation_euler[1], 4),
        "armRz": round(arm_r.rotation_euler[1], 4), "armLz": round(arm_l.rotation_euler[1], 4),
    })
out = os.path.join(root, "src/main/resources/assets/botwclimb/totk/dive.json")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w", encoding="utf-8") as f:
    json.dump({"period": PERIOD, "samples": samples}, f, indent=1)
print("wrote", out, len(samples), "samples")

# ---- a preview of the cycle for checking it against the footage (flat shading, seen from behind)
if preview:
    os.makedirs(preview, exist_ok=True)
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    scene.collection.objects.link(cam)
    cam.location = (0, -5, 0.2)
    cam.rotation_euler = (math.radians(90), 0, 0)
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = 3.2
    scene.camera = cam
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.render.resolution_x = scene.render.resolution_y = 300
    scene.display.shading.light = "FLAT"
    scene.display.shading.color_type = "OBJECT"
    scene.render.image_settings.file_format = "PNG"
    for tick in range(0, PERIOD, 2):
        scene.frame_set(tick)
        scene.render.filepath = os.path.join(preview, f"dive_{tick:02d}.png")
        bpy.ops.render.render(write_still=True)
    print("preview in", preview)

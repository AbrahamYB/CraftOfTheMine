"""Renders a rough preview of the shrine terminal block models (run from the project root: python tools/preview_terminal.py <out.png>).
It draws each cuboid face with its real texture region, lit by direction, from a front-left-above angle, dormant and active side by side.
It is only a design aid: it is not Minecraft's own renderer (no ambient occlusion, no emissive glow)."""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, "src/main/resources/assets/botwclimb/models/block")
TEX = os.environ.get("TEXDIR") or os.path.join(ROOT, "src/main/resources/assets/botwclimb/textures/block")
SHADE = {"up": 1.0, "south": 0.82, "east": 0.7, "west": 0.62, "north": 0.55, "down": 0.45}
YAW, PITCH, SCALE = math.radians(-32), math.radians(-32), 17


def project(p):
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    xr = x * math.cos(YAW) + z * math.sin(YAW)
    zr = -x * math.sin(YAW) + z * math.cos(YAW)
    yr = y * math.cos(PITCH) - zr * math.sin(PITCH)
    zr2 = y * math.sin(PITCH) + zr * math.cos(PITCH)
    return np.array([xr * SCALE, -yr * SCALE]), zr2


def default_uv(name, a, b):
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {"up": [x1, z1, x2, z2], "down": [x1, 16 - z2, x2, 16 - z1], "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
            "south": [x1, 16 - y2, x2, 16 - y1], "west": [z1, 16 - y2, z2, 16 - y1], "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1]}[name]


def face_geom(name, a, b):
    x1, y1, z1 = a
    x2, y2, z2 = b
    return {"up": ((x1, y2, z1), (x2 - x1, 0, 0), (0, 0, z2 - z1)), "down": ((x1, y1, z2), (x2 - x1, 0, 0), (0, 0, z1 - z2)),
            "north": ((x2, y2, z1), (x1 - x2, 0, 0), (0, y1 - y2, 0)), "south": ((x1, y2, z2), (x2 - x1, 0, 0), (0, y1 - y2, 0)),
            "west": ((x1, y2, z1), (0, 0, z2 - z1), (0, y1 - y2, 0)), "east": ((x2, y2, z2), (0, 0, z1 - z2), (0, y1 - y2, 0))}[name]


def render(model_name):
    m = json.load(open(os.path.join(MODELS, model_name + ".json")))
    texs = {}
    for k, v in m["textures"].items():
        img = Image.open(os.path.join(TEX, v.split("/")[-1] + ".png")).convert("RGBA")
        meta = os.path.join(TEX, v.split("/")[-1] + ".png.mcmeta")
        if os.path.exists(meta):  # an animated texture: show its first frame
            a = json.load(open(meta))["animation"]
            img = img.crop((0, 0, a.get("width", img.width), a.get("height", img.width)))
        texs[k] = img
    W = H = 520
    canvas = Image.new("RGBA", (W, H), (150, 190, 225, 255))
    ImageDraw.Draw(canvas).rectangle([0, 400, W, H], fill=(140, 135, 120, 255))
    faces = []
    for e in m["elements"]:
        a, b = e["from"], e["to"]
        for name, f in e["faces"].items():
            o, u, v = face_geom(name, a, b)
            pts, depth = zip(*[project(np.array(o) + s * np.array(u) + t * np.array(v)) for s, t in ((0, 0), (1, 0), (1, 1), (0, 1))])
            normal = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}[name]
            n0, nd = project(np.array((8, 8, 8)) + np.array(normal))
            _, d0 = project(np.array((8, 8, 8)))
            if nd - d0 > 0:  # facing away
                continue
            faces.append((sum(depth) / 4, name, f, a, b, o, u, v))
    faces.sort(key=lambda t: -t[0])
    for _, name, f, a, b, o, u, v in faces:
        tex = texs[f["texture"][1:]]
        uv = f.get("uv") or default_uv(name, a, b)
        kx, ky = tex.width / 16, tex.height / 16  # a model's uv always spans the whole texture as 16 x 16, whatever its size
        sx0, sx1 = uv[0] * kx, uv[2] * kx
        sy0, sy1 = uv[1] * ky, uv[3] * ky
        w, h = max(sx1 - sx0, 1e-6), max(sy1 - sy0, 1e-6)
        pts = [project(np.array(o) + s * np.array(u) + t * np.array(v))[0] + np.array([W / 2, H / 2 + 20]) for s, t in ((0, 0), (1, 0), (1, 1), (0, 1))]
        U, V = (pts[1] - pts[0]) / w, (pts[3] - pts[0]) / h
        M = np.array([[U[0], V[0]], [U[1], V[1]]])
        if abs(np.linalg.det(M)) < 1e-9:
            continue
        inv = np.linalg.inv(M)
        c = pts[0] - M @ np.array([sx0, sy0])
        # dest -> source: s = inv @ (d - c)
        coeffs = (inv[0, 0], inv[0, 1], -(inv @ c)[0], inv[1, 0], inv[1, 1], -(inv @ c)[1])
        layer = tex.transform((W, H), Image.AFFINE, coeffs, Image.NEAREST)
        mask = Image.new("L", (W, H), 0)
        ImageDraw.Draw(mask).polygon([tuple(p) for p in pts], fill=255)
        r, g, bch, al = layer.split()
        sh = SHADE[name]
        shaded = Image.merge("RGBA", [ch.point(lambda x: int(x * sh)) for ch in (r, g, bch)] + [al])
        canvas.paste(shaded, (0, 0), Image.composite(al, Image.new("L", (W, H), 0), mask))
    return canvas


if __name__ == "__main__":
    out = sys.argv[1]
    a, b = render("shrine_terminal_off"), render("shrine_terminal_on")
    sheet = Image.new("RGBA", (a.width * 2, a.height))
    sheet.paste(a, (0, 0))
    sheet.paste(b, (a.width, 0))
    sheet.convert("RGB").save(out)
    print(out)

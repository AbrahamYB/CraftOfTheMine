"""Draws the cocoon the way the game builds it (same maths as CocoonMath/MudCocoon), a few moments of the ride side by side.
Run from the project root: python tools/preview_cocoon.py  ->  concept/cocoon_preview.png"""
import math
from PIL import Image, ImageDraw

PETALS, STACKS, COLS, R, MID = 8, 14, 3, 1.2, 0.9


def point(phi, lam, petal, op, rx, ry, rz):
    x, y, z = rx * math.sin(phi) * math.cos(lam), ry * (1 - math.cos(phi)), rz * math.sin(phi) * math.sin(lam)
    ax, az = -math.sin(petal), math.cos(petal)
    c, s = math.cos(-op), math.sin(-op)
    dot = ax * x + az * z
    cx, cy, cz = -az * y, az * x - ax * z, ax * y
    return (x * c + cx * s + ax * dot * (1 - c), y * c + cy * s + MID - ry, z * c + cz * s + az * dot * (1 - c))


def quads(op, stretch):
    rxz, ry = R * (1 - 0.1 * stretch), R * (1 + 0.35 * stretch)
    pw = 2 * math.pi / PETALS
    for j in range(PETALS):
        mid = j * pw
        for c in range(COLS):
            l0 = mid - pw / 2 + c * pw / COLS
            l1 = l0 + pw / COLS
            for i in range(STACKS):
                f0, f1 = math.pi * i / STACKS, math.pi * (i + 1) / STACKS
                yield [point(f, l, mid, op, rxz, ry, rxz) for f, l in ((f0, l0), (f0, l1), (f1, l1), (f1, l0))]


def frame(op, stretch, hole, cam=(0.45, 0.35), size=(200, 300), scale=62):
    im = Image.new("RGB", size, (150, 190, 235))
    d = ImageDraw.Draw(im)
    gy = size[1] - 55
    d.rectangle([0, gy, size[0], size[1]], fill=(80, 140, 70))  # the ground, a little under the feet
    yaw, pitch = cam
    cyaw, syaw, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)

    def proj(p):
        x, y, z = p
        x, z = x * cyaw - z * syaw, x * syaw + z * cyaw
        y, z = y * cp - z * sp, y * sp + z * cp
        return (size[0] / 2 + x * scale, gy - y * scale), z

    if hole > 0:
        rr = 1.9 * hole
        pts = [proj((rr * math.cos(a) * 1.0, 0.02, rr * math.sin(a)))[0] for a in [k * math.pi / 15 for k in range(30)]]
        d.polygon(pts, fill=(40, 38, 42), outline=(70, 66, 72))
    polys = []
    for q in quads(op, stretch):
        pr = [proj(p) for p in q]
        depth = sum(p[1] for p in pr) / 4
        # shade by the facing of the quad (cheap): brighter to the upper left
        a, b, c = q[0], q[1], q[2]
        n = (((b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1])), ((b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2])), ((b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])))
        ln = math.sqrt(sum(v * v for v in n)) or 1
        light = abs(n[1] * 0.7 + n[0] * 0.3) / ln
        polys.append((depth, [p[0] for p in pr], int(60 + 90 * light)))
    for depth, pts, sh in sorted(polys, key=lambda t: -t[0]):
        d.polygon(pts, fill=(sh, sh - 3, sh + 2))
    return im


frames = [("t=0 open wide", 1.85, 0, 0), ("t=4", 1.3, 0, 0), ("t=10", 0.75, 0, 0), ("t=15", 0.2, 0, 0), ("closed", 0, 0, 0),
          ("rising fast", 0, 1.0, 0), ("landing: ground opens", 0, 0.2, 0.7), ("set down: opens", 1.2, 0, 1.0)]
sheet = Image.new("RGB", (200 * 4, 300 * 2))
for k, (name, op, st, hole) in enumerate(frames):
    im = frame(op, st, hole)
    ImageDraw.Draw(im).text((6, 6), name, fill=(0, 0, 0))
    sheet.paste(im, ((k % 4) * 200, (k // 4) * 300))
sheet.save("concept/cocoon_preview.png")
print("ok")

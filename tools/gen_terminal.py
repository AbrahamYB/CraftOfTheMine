"""Generates the shrine terminal's 16-pixels-per-block textures, block models, blockstate, item model, loot table, tag entry and lang
line (run from the project root: python tools/gen_terminal.py). Teal Zonai stone with gold trim and a glyph disc on a pedestal.
The disc, its rim and the base ring are animated textures (frames stacked top to bottom, see the .mcmeta files): dormant the gold
breathes slowly with a faint spark running round the ring; active the green is brighter, pulses, and a light chases round the ring
and along the rim. Everything is drawn symmetrically about the block centre, with a pixel of margin between the ring and the edge."""
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
TEX = os.path.join(RES, "assets/botwclimb/textures/block")
MODELS = os.path.join(RES, "assets/botwclimb/models")
rng = np.random.default_rng(77)

TEAL, TEAL_DARK, TEAL_DEEP, GOLD = (46, 138, 123), (29, 90, 82), (20, 66, 59), (214, 160, 67)
GREEN, GREEN_HI, GOLD_HI = (95, 227, 161), (205, 255, 228), (252, 226, 150)
DISC_ON_BG, SIDE_ON_BG = (14, 52, 46), (22, 78, 66)
FRAMES = 16

# the eye-and-scroll emblem as 12 x 12 pixels (rows 3..8), mirror-symmetric about the disc's centre
EMBLEM = {3: (5, 6), 4: (4, 5, 6, 7), 5: (3, 5, 6, 8), 6: (4, 5, 6, 7), 7: (3, 8), 8: (4, 7)}
PUPIL = ((5, 5), (6, 5))


def mix(bg, ink, k):
    return tuple(b + (i - b) * k for b, i in zip(bg, ink))


def ring_pixels(n, center, r0, r1):
    """Pixels whose centre is between r0 and r1 from the middle, in order round the ring (so a light can run along it)."""
    px = [(x, y) for y in range(n) for x in range(n) if r0 <= math.hypot(x - center, y - center) < r1]
    return sorted(px, key=lambda p: math.atan2(p[1] - center, p[0] - center))


def chase(i, pos, length, tail=3.0):
    """How much a running light at ring position `pos` (0..length) lights up the pixel, given frame i of FRAMES."""
    head = i / FRAMES * length
    d = (head - pos) % length
    return max(0.0, 1 - d / tail) if d < tail else 0.0


def lit(bg, ink, hi, k):
    """Colour at brightness k: up to 1 it fades from the background to the ink, past 1 it turns towards the bright highlight."""
    return mix(bg, ink, k) if k <= 1 else mix(ink, hi, min(1.0, k - 1))


def noise_base(w, h, rgb, amp):
    n = rng.normal(0, amp, (h, w, 1))
    return np.clip(np.array(rgb, float)[None, None, :] + n, 0, 255)


def disc_frames(on):
    bg = DISC_ON_BG if on else TEAL_DEEP
    ink, hi = (GREEN, GREEN_HI) if on else (GOLD, GOLD_HI)
    base = noise_base(12, 12, bg, 2)
    ring = ring_pixels(12, 5.5, 3.9, 4.9)  # a pixel of margin all round, symmetric about the centre
    frames = []
    for i in range(FRAMES):
        t = i / FRAMES
        breathe = (0.8 + 0.2 * math.sin(math.tau * t)) if on else (0.58 + 0.16 * math.sin(math.tau * t))
        img = base.copy()
        for idx, (x, y) in enumerate(ring):
            img[y, x] = lit(bg, ink, hi, breathe + (0.6 if on else 0.4) * chase(i, idx, len(ring)))
        for y, xs in EMBLEM.items():
            for x in xs:
                img[y, x] = lit(bg, ink, hi, breathe)
        pk = 0.5 + 0.5 * math.sin(math.tau * t + 1.0)
        for x, y in PUPIL:
            img[y, x] = lit(bg, ink, hi, breathe + (0.2 + 0.5 * pk if on else 0.1 + 0.3 * pk))
        frames.append(np.clip(img, 0, 255).astype(np.uint8))
    return frames


def side_frames(on):
    bg = SIDE_ON_BG if on else TEAL_DARK
    ink, hi = (GREEN, GREEN_HI) if on else (GOLD, GOLD_HI)
    base = noise_base(12, 3, bg, 2)
    frames = []
    for i in range(FRAMES):
        t = i / FRAMES
        breathe = (0.8 + 0.2 * math.sin(math.tau * t)) if on else (0.55 + 0.15 * math.sin(math.tau * t))
        img = base.copy()
        img[0, :] = lit(bg, ink, hi, breathe)
        img[2, :] = lit(bg, ink, hi, breathe)
        for x in range(1, 12, 3):  # the marks on the rim light up one after another, going round
            img[1, x] = lit(bg, ink, hi, breathe * 0.6 + (0.9 if on else 0.4) * chase(i, x, 12, 4.0 if on else 2.0))
        frames.append(np.clip(img, 0, 255).astype(np.uint8))
    return frames


def base_frames(on):
    ink, hi = (GREEN, GREEN_HI) if on else (GOLD, GOLD_HI)
    stone = noise_base(16, 16, TEAL, 6)
    ring = ring_pixels(16, 7.5, 6.3, 7.3)  # a pixel of margin to the block edge
    frames = []
    for i in range(FRAMES):
        t = i / FRAMES
        breathe = (0.85 + 0.15 * math.sin(math.tau * t)) if on else (0.72 + 0.1 * math.sin(math.tau * t))
        img = stone.copy()
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                if r < 5.4:
                    img[y, x] = mix(TEAL_DEEP, DISC_ON_BG, 0.6) if on else TEAL_DEEP
                elif r < 6.3:
                    img[y, x] = img[y, x] * 0.82
        for idx, (x, y) in enumerate(ring):
            img[y, x] = lit(TEAL, ink, hi, breathe + (0.6 if on else 0.2) * chase(i, idx, len(ring), 5.0))
        frames.append(np.clip(img, 0, 255).astype(np.uint8))
    return frames


def save_strip(frames, name, frametime, width=None, height=None):
    strip = Image.fromarray(np.concatenate(frames, axis=0), "RGB")
    strip.save(os.path.join(TEX, name + ".png"))
    anim = {"frametime": frametime, "interpolate": True}
    if width:
        anim.update(width=width, height=height)
    write_json(os.path.join(TEX, name + ".png.mcmeta"), {"animation": anim})


def stone_tile():
    return Image.fromarray(noise_base(16, 16, TEAL, 8).astype(np.uint8), "RGB")


def gold_tile():
    return Image.fromarray(noise_base(16, 16, GOLD, 6).astype(np.uint8), "RGB")


# ---------------------------------------------------------------- models
def face(tex, uv=None):
    f = {"texture": "#" + tex}
    if uv:
        f["uv"] = uv
    return f


def box(a, b, tex, omit=(), faces=None, glow=()):
    fs = {}
    for name in ("north", "east", "south", "west", "up", "down"):
        if name in omit:
            continue
        fs[name] = face(tex)
    for name, (t, uv) in (faces or {}).items():
        fs[name] = face(t, uv)
    for name in glow:
        if name in fs:
            fs[name]["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return {"from": a, "to": b, "faces": fs}


def model(on):
    glow = ("up",) if on else ()
    els = [
        box([0, 0, 0], [16, 2.5, 16], "stone", omit=("up",)),
        box([0, 2.5, 0], [16, 3, 16], "gold", faces={"up": ("base", [0, 0, 16, 16])}, glow=glow),
        box([4, 3, 4], [12, 7, 12], "stone"),
        box([3.8, 3, 3.8], [12.2, 3.6, 12.2], "gold"),
        box([3.8, 6.4, 3.8], [12.2, 7, 12.2], "gold"),
    ]
    lo, hi = 1.3333, 14.6667  # the 10-unit part of the disc's 12 units, in 16-space
    full = [0, 0, 16, 16]
    els.append(box([3, 7, 2], [13, 10, 14], "stone", faces={  # the disc is two crossed boxes: this one is 10 wide, 12 deep
        "up": ("disc", [lo, 0, hi, 16]), "north": ("side", [lo, 0, hi, 16]), "south": ("side", [lo, 0, hi, 16]),
        "east": ("side", full), "west": ("side", full)}, glow=glow))
    els.append(box([2, 7, 3], [14, 10, 13], "stone", faces={
        "up": ("disc", [0, lo, 16, hi]), "north": ("side", full), "south": ("side", full),
        "east": ("side", [lo, 0, hi, 16]), "west": ("side", [lo, 0, hi, 16])}, glow=glow))
    tag = "on" if on else "off"
    return {
        "textures": {
            "particle": "botwclimb:block/shrine_terminal_stone",
            "stone": "botwclimb:block/shrine_terminal_stone",
            "gold": "botwclimb:block/shrine_terminal_gold",
            "base": "botwclimb:block/shrine_terminal_base_" + tag,
            "disc": "botwclimb:block/shrine_terminal_disc_" + tag,
            "side": "botwclimb:block/shrine_terminal_side_" + tag,
        },
        "elements": els,
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    os.makedirs(TEX, exist_ok=True)
    old = os.path.join(TEX, "shrine_terminal_base.png")  # the earlier single-frame base texture
    if os.path.exists(old):
        os.remove(old)
    for on, tag in ((False, "off"), (True, "on")):
        ft = 3  # same speed dormant and active, so every texture shares one global clock and switching never jumps the light
        save_strip(disc_frames(on), "shrine_terminal_disc_" + tag, ft)
        save_strip(side_frames(on), "shrine_terminal_side_" + tag, ft, 12, 3)
        save_strip(base_frames(on), "shrine_terminal_base_" + tag, ft)
    stone_tile().save(os.path.join(TEX, "shrine_terminal_stone.png"))
    gold_tile().save(os.path.join(TEX, "shrine_terminal_gold.png"))
    write_json(os.path.join(MODELS, "block/shrine_terminal_off.json"), model(False))
    write_json(os.path.join(MODELS, "block/shrine_terminal_on.json"), model(True))
    write_json(os.path.join(MODELS, "item/shrine_terminal.json"), {
        "parent": "botwclimb:block/shrine_terminal_off",
        "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.55, 0.55, 0.55]},
                    "fixed": {"scale": [0.55, 0.55, 0.55]}, "ground": {"scale": [0.4, 0.4, 0.4]},
                    "thirdperson_righthand": {"scale": [0.4, 0.4, 0.4]}, "firstperson_righthand": {"scale": [0.4, 0.4, 0.4]}}})
    write_json(os.path.join(RES, "assets/botwclimb/blockstates/shrine_terminal.json"), {"variants": {
        "lit=false": {"model": "botwclimb:block/shrine_terminal_off"},
        "lit=true": {"model": "botwclimb:block/shrine_terminal_on"}}})
    write_json(os.path.join(RES, "data/botwclimb/loot_table/blocks/shrine_terminal.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                   "entries": [{"type": "minecraft:item", "name": "botwclimb:shrine_terminal"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "botwclimb:blocks/shrine_terminal"})
    tag = os.path.join(RES, "data/minecraft/tags/block/mineable/pickaxe.json")
    with open(tag, encoding="utf-8") as f:
        t = json.load(f)
    if "botwclimb:shrine_terminal" not in t["values"]:
        t["values"].append("botwclimb:shrine_terminal")
    write_json(tag, t)
    lang = os.path.join(RES, "assets/botwclimb/lang/en_us.json")
    with open(lang, encoding="utf-8") as f:
        text = f.read()
    if "block.botwclimb.shrine_terminal" not in text:
        text = text.rstrip().rstrip("}").rstrip() + ',\n  "block.botwclimb.shrine_terminal": "Shrine Terminal"\n}\n'
        with open(lang, "w", encoding="utf-8") as f:
            f.write(text)
    print("terminal assets written")


if __name__ == "__main__":
    main()

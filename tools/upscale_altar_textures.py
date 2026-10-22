"""Takes the altar block textures from 64x64 to 128x128 (run once from the project root; a texture that is already 128 is left alone).
It keeps the look (the obsidian grain, the light border, the dark disc under the rings, the dark panel under the runes), but redraws
the shapes with smooth 128 edges and adds a finer grain, so the block has twice the detail."""
import os
import random

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(HERE, "src/main/resources/assets/botwclimb/textures/block")
random.seed(1288)
np.random.seed(1288)


def blur(a, passes=3):
    a = a.astype(float)
    for _ in range(passes):
        p = np.pad(a, 1, mode="edge")
        a = (p[:-2, 1:-1] + p[2:, 1:-1] + p[1:-1, :-2] + p[1:-1, 2:] + 4 * p[1:-1, 1:-1]) / 8
    return a


def upscale(name, dark_shape):
    path = os.path.join(DIR, name)
    src = Image.open(path).convert("RGB")
    if src.size != (64, 64):
        print(name, "is already", src.size, "- skipped")
        return
    a = np.array(src).astype(float)
    lum = a.mean(axis=2)
    up = np.repeat(np.repeat(a, 2, axis=0), 2, axis=1)  # nearest 2x: the same look
    if dark_shape:
        mask64 = lum < 52
        mask = np.repeat(np.repeat(mask64, 2, axis=0), 2, axis=1).astype(float)
        mask = blur(mask, 6) > 0.5  # smooth the stair-steps of the shape into clean 128 edges
        was_dark = np.repeat(np.repeat(mask64, 2, axis=0), 2, axis=1)
        dark_pal = up[was_dark] if was_dark.any() else up.reshape(-1, 3)
        base_pal = up[~was_dark]
        out = up.copy()
        for y in range(128):
            for x in range(128):
                if mask[y, x] and not was_dark[y, x]:
                    out[y, x] = dark_pal[random.randrange(len(dark_pal))]
                elif not mask[y, x] and was_dark[y, x]:
                    out[y, x] = base_pal[random.randrange(len(base_pal))]
        up = out
    # a finer grain than the 64 version had: every 4x4 patch is a little lighter or darker than its neighbours
    grain = np.random.uniform(0.93, 1.07, size=(32, 32))
    grain = np.repeat(np.repeat(grain, 4, axis=0), 4, axis=1)[:, :, None]
    edge = np.zeros((128, 128), bool)
    edge[:8, :] = edge[-8:, :] = edge[:, :8] = edge[:, -8:] = True  # the light frame keeps its colour
    up = np.where(edge[:, :, None], up, up * grain)
    Image.fromarray(np.clip(up, 0, 255).astype("uint8"), "RGB").save(path)
    print(name, "-> 128x128")


upscale("stamina_altar_top.png", True)
upscale("stamina_altar_side.png", True)
upscale("stamina_altar_bottom.png", False)


def refine(name):
    """Detail only a 128 texture can carry: a little noise per 2x2 patch and per texel on top of the 4x4 grain (the frame keeps its colour)."""
    path = os.path.join(DIR, name)
    a = np.array(Image.open(path).convert("RGB")).astype(float)
    if a.shape[0] != 128:
        return
    fine2 = np.repeat(np.repeat(np.random.uniform(0.96, 1.04, size=(64, 64)), 2, axis=0), 2, axis=1)
    fine1 = np.random.uniform(0.975, 1.025, size=(128, 128))
    edge = np.zeros((128, 128), bool)
    edge[:8, :] = edge[-8:, :] = edge[:, :8] = edge[:, -8:] = True
    a = np.where(edge[:, :, None], a, a * (fine2 * fine1)[:, :, None])
    Image.fromarray(np.clip(a, 0, 255).astype("uint8"), "RGB").save(path)
    print(name, "refined")


if os.environ.get("REFINE"):
    for n in ("stamina_altar_top.png", "stamina_altar_side.png", "stamina_altar_bottom.png"):
        refine(n)

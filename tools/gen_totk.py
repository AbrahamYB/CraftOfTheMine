"""Writes textures/totk/glow.png: a soft white radial blob (alpha falls off from the middle), tinted at draw time.
Run from the project root: python tools/gen_totk.py"""
import os
from PIL import Image

N = 64
im = Image.new("RGBA", (N, N))
for y in range(N):
    for x in range(N):
        r = (((x + 0.5) / N * 2 - 1) ** 2 + ((y + 0.5) / N * 2 - 1) ** 2) ** 0.5
        a = max(0.0, 1 - r) ** 1.6
        v = int(255 * a)
        im.putpixel((x, y), (v, v, v, v))  # the glow render type adds the colour: the falloff must be in it, not only in alpha
out = "src/main/resources/assets/botwclimb/textures/totk/"
os.makedirs(out, exist_ok=True)
im.save(out + "glow.png")

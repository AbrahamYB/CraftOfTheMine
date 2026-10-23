"""Generates the gray mud texture for the terminal ride (run from the project root: python tools/gen_mud.py)."""
import random

from PIL import Image

random.seed(7)
im = Image.new("RGBA", (128, 64))
for y in range(64):
    for x in range(128):
        n = random.randint(-7, 7)
        b = (62 + n, 59 + n, 63 + n)
        if random.random() < 0.06:
            b = tuple(c + 16 for c in b)
        if random.random() < 0.05:
            b = tuple(c - 14 for c in b)
        im.putpixel((x, y), b + (255,))
im.save("src/main/resources/assets/botwclimb/textures/entity/mud_hand.png")

"""Paints the terminal cocoon: dark wrapped strips in a hurried ink style, with a Zonai green glow showing in the gaps.
Run from the project root: python tools/gen_cocoon.py
Writes textures/entity/mud_cocoon.png (opaque, lit) and mud_cocoon_glow.png (the glow only, drawn full-bright on top).
The strips run diagonally and wrap round: a strip leaves the right edge exactly where the pattern re-enters on the left."""
import math
import random

from PIL import Image, ImageDraw

random.seed(11)
W = H = 256
SLOPE = 0.5        # the strips climb half the height over one trip round (matches MudCocoon's ridges)
PERIOD = 0.125     # strip spacing in v; SLOPE / PERIOD must be a whole number so the pattern wraps
OUT = "src/main/resources/assets/botwclimb/textures/entity/"

base = Image.new("RGBA", (W, H), (7, 30, 17, 255))   # what shows in the gaps when nothing glows: deep green
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
db, dg = ImageDraw.Draw(base), ImageDraw.Draw(glow)


def wobble(u, seed):
    return 0.011 * math.sin(u * 9 + seed) + 0.006 * math.sin(u * 23 + seed * 2.3)


# the glow: everywhere to begin with, then the strips cover it up leaving thin gaps
for y in range(H):
    for x in range(W):
        n = random.randint(-14, 14)
        glow.putpixel((x, y), (max(0, 10 + n), min(255, 232 + n * 2), max(0, 96 + n), 255))
# a few brighter flecks, like light leaking through the wrapping
for _ in range(220):
    x, y = random.randrange(W), random.randrange(H)
    r = random.choice((1, 1, 2))
    dg.ellipse([x - r, y - r, x + r, y + r], fill=(140, 255, 190, 255))

for k in range(-6, 18):
    seed = random.random() * 6.28
    width = PERIOD * random.uniform(0.29, 0.36)      # half the strip's width, in v
    for shift in (-1, 0, 1):                         # drawn again one trip either side so the wrap is seamless
        top, bot = [], []
        for i in range(0, W + 1, 3):
            u = i / W
            c = (k + 0.5) * PERIOD + SLOPE * (u + shift) + wobble(u, seed)
            jt, jb = random.uniform(-0.004, 0.004), random.uniform(-0.004, 0.004)
            top.append((i, (c - width + jt) * H))
            bot.append((i, (c + width + jb) * H))
        poly = top + bot[::-1]
        shade = random.randint(14, 28)
        fill = (shade, shade + 8, shade + 3, 255)
        for d in (db, dg):                           # the strip covers the glow too: it is dark in both
            d.polygon(poly, fill=fill if d is db else (0, 0, 0, 0))
        # ink outline, and a pale green highlight along the lower edge like the reference's lit folds
        db.line(top, fill=(2, 4, 3, 255), width=2)
        db.line(bot, fill=(2, 4, 3, 255), width=2)
        db.line([(x, y - 2) for x, y in bot], fill=(40, 78, 52, 255), width=1)
        # scribble: short dark hatch strokes across the strip
        for _ in range(26):
            i = random.randrange(0, W)
            u = i / W
            cy = ((k + 0.5) * PERIOD + SLOPE * (u + shift)) * H
            a = random.uniform(-0.9, 0.9)
            ln = random.uniform(6, 16)
            db.line([(i, cy), (i + math.cos(a) * ln, cy + math.sin(a) * ln)], fill=(2, 5, 3, 255), width=1)

# hatching in the gaps (cut out of the glow): thin dark scratches
for _ in range(700):
    x, y = random.randrange(W), random.randrange(H)
    a = random.uniform(-0.6, 0.6) + 0.5
    ln = random.uniform(4, 10)
    dg.line([(x, y), (x + math.cos(a) * ln, y + math.sin(a) * ln)], fill=(0, 0, 0, 0), width=1)

# the glow only shows where the lit texture is not covered, so copy the strips' transparency across
base.save(OUT + "mud_cocoon.png")
glow.save(OUT + "mud_cocoon_glow.png")
sheet = Image.new("RGB", (W * 2, H))
bg = Image.new("RGB", (W, H), (0, 0, 0))
sheet.paste(base.convert("RGB"), (0, 0))
sheet.paste(Image.alpha_composite(bg.convert("RGBA"), glow).convert("RGB"), (W, 0))
sheet.save("concept/cocoon_texture.png")
print("ok")

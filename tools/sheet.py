"""Lays screenshots from run-auto/screenshots out in a contact sheet: python tools/sheet.py <prefix> <first> <last> <out.png>"""
import glob, sys
from PIL import Image, ImageDraw
prefix, lo, hi, out = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
files = [f for f in sorted(glob.glob(f"run-auto/screenshots/{prefix}_*.png")) if lo <= int(f[-7:-4]) <= hi]
cols, w, h = 3, 427, 240
rows = (len(files) + cols - 1) // cols
sheet = Image.new("RGB", (cols * w, rows * h))
for i, f in enumerate(files):
    im = Image.open(f).convert("RGB").resize((w, h))
    ImageDraw.Draw(im).text((6, 6), f[-7:-4], fill=(255, 255, 0))
    sheet.paste(im, ((i % cols) * w, (i // cols) * h))
sheet.save(out)
print(len(files))

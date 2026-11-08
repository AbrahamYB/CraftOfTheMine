"""Builds a contact sheet of the key frames of one version from tools/shots-out (see screenshots_all.ps1).
Usage: python tools/shots_sheet.py 1.21.8   (writes tools/shots-out/sheet_<version>.png; needs Pillow)"""
import glob, os, sys
from PIL import Image, ImageDraw

v = sys.argv[1]
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'shots-out')
a, b = os.path.join(out, v), os.path.join(out, v + '-Pwall')
files = [os.path.join(a, n + '.png') for n in ('plot_outline', 'plot_screen', 'config_screen')]
files += [os.path.join(b, n + '.png') for n in ('wall_in_160', 'wall_open', 'wall_climb_363', 'wall_climb_366')]
ups, downs = sorted(glob.glob(os.path.join(a, 'up_*.png'))), sorted(glob.glob(os.path.join(a, 'down_*.png')))
if ups:
    files += [ups[len(ups) // 4], ups[len(ups) // 2], ups[3 * len(ups) // 4]]
if downs:
    files += [downs[len(downs) // 3], downs[2 * len(downs) // 3]]
files = [f for f in files if os.path.exists(f)]
w, h, cols = 427, 240, 3
rows = (len(files) + cols - 1) // cols
sheet = Image.new('RGB', (cols * w, rows * (h + 16)), (20, 20, 20))
d = ImageDraw.Draw(sheet)
for i, f in enumerate(files):
    x, y = (i % cols) * w, (i // cols) * (h + 16)
    sheet.paste(Image.open(f).convert('RGB').resize((w, h)), (x, y + 16))
    d.text((x + 4, y + 2), v + ' ' + os.path.basename(f), fill=(255, 255, 0))
path = os.path.join(out, 'sheet_%s.png' % v)
sheet.save(path)
print(path, len(files))

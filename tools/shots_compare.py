"""Puts the same frame of every version side by side (tools/shots-out/compare_<scene>.png) and prints how far each differs from the first
version (mean pixel difference, 0-255). Usage: python tools/shots_compare.py 1.21.1 1.21.4 ...   (first version is the reference)"""
import os, sys, glob
import numpy as np
from PIL import Image, ImageDraw

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'shots-out')
versions = sys.argv[1:]
scenes = {  # name -> (folder suffix, file)
    'plot_outline': ('', 'plot_outline.png'), 'plot_screen': ('', 'plot_screen.png'), 'config_screen': ('', 'config_screen.png'),
    'wall_marker': ('-Pwall', 'wall_in_160.png'), 'wall_open': ('-Pwall', 'wall_open.png'),
    'climb_a': ('-Pwall', 'wall_climb_363.png'), 'climb_b': ('-Pwall', 'wall_climb_366.png'),
    'ride_up_void': ('', 'up_052.png'), 'ride_up_late': ('', 'up_064.png'), 'ride_down_world': ('', 'down_037.png'), 'ride_down_void': ('', 'down_073.png'),
}
W, H = 427, 240
for scene, (suffix, name) in scenes.items():
    imgs = []
    for v in versions:
        f = os.path.join(out, v + suffix, name)
        imgs.append(Image.open(f).convert('RGB') if os.path.exists(f) else None)
    cols = 5
    rows = (len(versions) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * W, rows * (H + 16)), (20, 20, 20))
    d = ImageDraw.Draw(sheet)
    ref = np.asarray(imgs[0].resize((W, H))).astype(float) if imgs[0] else None
    diffs = []
    for i, (v, im) in enumerate(zip(versions, imgs)):
        x, y = (i % cols) * W, (i // cols) * (H + 16)
        if im is None:
            d.text((x + 4, y + 2), v + ' MISSING', fill=(255, 80, 80)); diffs.append('missing'); continue
        r = im.resize((W, H))
        sheet.paste(r, (x, y + 16))
        dv = float(np.abs(np.asarray(r).astype(float) - ref).mean()) if ref is not None else 0
        diffs.append('%s %.1f' % (v, dv))
        d.text((x + 4, y + 2), '%s %s diff %.1f' % (v, name, dv), fill=(255, 255, 0))
    sheet.save(os.path.join(out, 'compare_%s.png' % scene))
    print(scene.ljust(16), ' | '.join(diffs))

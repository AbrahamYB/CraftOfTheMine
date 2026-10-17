"""
Exports every paraglider fabric at two texture sizes and builds one overview image per size.

  64x: src/main/resources/assets/botwclimb/textures/entity/paraglider/<id>.png          (128x64, shipped in the mod)
  32x: src/main/resources/assets/botwclimb/textures/entity/paraglider/32x/<id>.png      (64x32, nearest-neighbour half)
  overview_64x.png / overview_32x.png in tools/out/ show every fabric side by side.

Run from the project root:  python tools/export_fabric_sets.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import make_fabrics as mf  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '..')
OUT64 = mf.OUT
OUT32 = os.path.join(OUT64, '32x')
TOOLS_OUT = os.path.join(os.path.dirname(__file__), 'out')


def half(img):
    """128x64 -> 64x32, taking every second pixel (keeps hard edges)."""
    return [[img[y * 2][x * 2] for x in range(64)] for y in range(32)]


def scaled(img, k):
    return [[row[x // k] for x in range(len(row) * k)] for row in img for _ in range(k)]


def contact_sheet(sheets, cols, path, scale):
    """Lays the 128x64 or 64x32 fabric sheets out in a grid, each shown at `scale`."""
    w, h = len(sheets[0][0]), len(sheets[0])
    gap = 4
    rows = (len(sheets) + cols - 1) // cols
    W, H = cols * (w * scale + gap) + gap, rows * (h * scale + gap) + gap
    img = [[(24, 24, 24, 255)] * W for _ in range(H)]
    for n, sheet in enumerate(sheets):
        ox, oy = gap + (n % cols) * (w * scale + gap), gap + (n // cols) * (h * scale + gap)
        for y in range(h):
            for x in range(w):
                c = sheet[y][x]
                for dy in range(scale):
                    for dx in range(scale):
                        img[oy + y * scale + dy][ox + x * scale + dx] = c
    mf.write_png_size(path, img, W, H)


def main():
    os.makedirs(OUT32, exist_ok=True)
    os.makedirs(TOOLS_OUT, exist_ok=True)
    sheets64, sheets32 = [], []
    for fid, (fn, tassel, ribbon, tip, trim, _) in mf.FABRICS.items():
        cv = fn()
        s64 = mf.sheet(cv, tassel, ribbon, tip, trim)
        s32 = half(s64)
        mf.write_png(os.path.join(OUT64, fid + '.png'), s64)
        mf.write_png_size(os.path.join(OUT32, fid + '.png'), s32, 64, 32)
        sheets64.append(s64)
        sheets32.append(s32)
        print('exported', fid)
    contact_sheet(sheets64, 4, os.path.join(TOOLS_OUT, 'overview_64x.png'), 2)
    contact_sheet(sheets32, 4, os.path.join(TOOLS_OUT, 'overview_32x.png'), 4)
    print('done:', len(sheets64), 'fabrics')


if __name__ == '__main__':
    main()

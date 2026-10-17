"""
Exports every paraglider fabric texture (128x64, "64x") into the mod and builds one overview image.

  src/main/resources/assets/botwclimb/textures/entity/paraglider/<id>.png
  tools/out/overview_64x.png

Run from the project root:  python tools/export_fabric_sets.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import make_fabrics as mf  # noqa: E402

TOOLS_OUT = os.path.join(os.path.dirname(__file__), 'out')


def contact_sheet(sheets, cols, path, scale):
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
    os.makedirs(TOOLS_OUT, exist_ok=True)
    sheets = []
    for fid, (fn, tassel, ribbon, tip, trim, _) in mf.FABRICS.items():
        s = mf.sheet(fn(), tassel, ribbon, tip, trim)
        mf.write_png(os.path.join(mf.OUT, fid + '.png'), s)
        sheets.append(s)
        print('exported', fid)
    contact_sheet(sheets, 4, os.path.join(TOOLS_OUT, 'overview_64x.png'), 2)
    print('done:', len(sheets), 'fabrics')


if __name__ == '__main__':
    main()

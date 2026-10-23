"""Switches the stamina altar's block textures between the detailed 128x128 set and a 16x16 vanilla-sized set
(python tools/altar_res.py 16  or  python tools/altar_res.py 128, from the project root). The first run keeps the 128 originals in
tools/altar_128/; the 16 set is those averaged down. The altar's glowing rings and runes read the texture size at run time and follow
it (restart the client to see the change)."""
import os
import shutil
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src/main/resources/assets/botwclimb/textures/block")
BACKUP = os.path.join(ROOT, "tools/altar_128")
NAMES = ("stamina_altar_top.png", "stamina_altar_side.png", "stamina_altar_bottom.png")

if len(sys.argv) != 2 or sys.argv[1] not in ("16", "128"):
    sys.exit(__doc__)
os.makedirs(BACKUP, exist_ok=True)
for n in NAMES:
    kept = os.path.join(BACKUP, n)
    live = os.path.join(TEX, n)
    if not os.path.exists(kept):
        if Image.open(live).width != 128:
            sys.exit(n + " is not the 128 original and there is no backup")
        shutil.copy(live, kept)
    if sys.argv[1] == "128":
        shutil.copy(kept, live)
    else:
        Image.open(kept).convert("RGB").resize((16, 16), Image.BOX).save(live)
print("altar textures:", sys.argv[1])

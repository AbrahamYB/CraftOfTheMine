"""Generates the altar's item models and glow textures (run from the repo root: python tools/altar_item.py).

The block's glow is drawn by AltarRenderer from the local player's upgrades. An item has no block entity, so it gets the same look from
nine item models, one per upgrade count (0 to 8), each with the rings and runes as glowing quads (green when lit, shadowed when not). The game picks the model from the player's upgrades (AltarPhase). The cell pattern here
copies AltarRenderer.tone / hash, so the item matches the block.
"""
import json, math, os
from PIL import Image

ASSETS = 'src/main/resources/assets/botwclimb/'
GRID = 16
GREEN, SHADOW = (0x00, 0xF2, 0x5A), (0x17, 0x51, 0x26)
RINGS = [(0.26, 0.355), (0.125, 0.195)]
TOP = 8  # the dais is 8 px tall
RUNE_TOP = 9  # the 12-row rune picture is hung so its rows 5 to 7 sit on the 5 px base
RUNE = [(5, 6), (5, 7), (5, 8), (5, 9), (6, 5), (6, 10), (7, 6), (7, 7), (7, 8), (7, 9)]


def i32(x):
    x &= 0xFFFFFFFF
    return x - (1 << 32) if x & 0x80000000 else x


def hash_(x, y, s):
    h = i32(x * 374761393 + y * 668265263 + s * 1274126177)
    h = i32((h ^ ((h & 0xFFFFFFFF) >> 13)) * 1274126177)
    h ^= (h & 0xFFFFFFFF) >> 16
    return (h & 0xFFFF) / 65535.0


def smooth(x):
    return x * x * (3 - 2 * x)


def tone(x, y, seed, lit):
    gx = x / (24.0 * GRID / 128.0); gy = y / (24.0 * GRID / 128.0)
    ix, iy = math.floor(gx), math.floor(gy)
    fx, fy = smooth(gx - ix), smooth(gy - iy)
    lerp = lambda t, a, b: a + (b - a) * t
    blotch = lerp(fy, lerp(fx, hash_(ix, iy, seed), hash_(ix + 1, iy, seed)), lerp(fx, hash_(ix, iy + 1, seed), hash_(ix + 1, iy + 1, seed)))
    f = (0.66 if lit else 0.90) + (0.28 if lit else 0.10) * blotch + (0.06 if lit else 0.04) * hash_(x, y, seed + 91)
    return min(255, round(255 * f))


VARIANTS = {'green': (GREEN, True, 255), 'shadow': (SHADOW, False, 230)}


def paint(img, x, y, grey, rgb, alpha):
    img.putpixel((x, y), (min(255, round(rgb[0] * grey / 255)), min(255, round(rgb[1] * grey / 255)), min(255, round(rgb[2] * grey / 255)), alpha))


def ring_texture(ring, variant):
    rgb, lit, alpha = VARIANTS[variant]
    img = Image.new('RGBA', (GRID, GRID), (0, 0, 0, 0))
    r0, r1 = RINGS[ring]
    for qq in range(4):
        for ix in range(GRID):
            for iz in range(GRID):
                dx, dz = (ix + .5) / GRID - .5, (iz + .5) / GRID - .5
                rad = math.hypot(dx, dz)
                if rad < r0 or rad > r1: continue
                deg = math.degrees(math.atan2(-dx, -dz))
                if deg < 0: deg += 360
                if deg < qq * 90 + 2 or deg > (qq + 1) * 90 - 2: continue
                paint(img, ix, iz, tone(ix, iz, ring * 4 + qq, lit), rgb, alpha)
    return img


def rune_texture(side, variant):
    rgb, lit, alpha = VARIANTS[variant]
    img = Image.new('RGBA', (GRID, GRID * 3 // 4), (0, 0, 0, 0))
    for row, col in RUNE:
        paint(img, col, row, tone(col, row, side + 1, lit), rgb, alpha)
    return img


def main():
    tex = ASSETS + 'textures/block/'
    for ring in range(2):
        for v in VARIANTS: ring_texture(ring, v).save(f'{tex}altar_item_ring{ring}_{v}.png')
    for side in range(4):
        for v in VARIANTS: rune_texture(side, v).save(f'{tex}altar_item_rune{side}_{v}.png')

    block = json.load(open(ASSETS + 'models/block/stamina_altar.json'))
    base = block['elements']
    light = {"block_light": 15, "sky_light": 15}
    names = {}
    for n in range(9):
        maxed = n >= 8
        colour = 'green'
        textures = {k: v for k, v in block['textures'].items()}
        elements = json.loads(json.dumps(base))
        for ring in range(2):
            for q in range(4):
                on = maxed or n - (1 - ring) * 4 > q  # the inner ring (1) fills first
                key = f'r{ring}{colour if on else "shadow"}'
                textures[key] = f'botwclimb:block/altar_item_ring{ring}_{colour if on else "shadow"}'
                x0 = 0 if q in (0, 1) else 8
                z0 = 0 if q in (0, 3) else 8
                y = TOP + 0.3 + 0.02 * ring
                elements.append({"from": [x0, y, z0], "to": [x0 + 8, y, z0 + 8], "faces": {"up": {"texture": '#' + key, "uv": [x0, z0, x0 + 8, z0 + 8], "neoforge_data": light}}})
        rune = colour if n >= 1 else 'shadow'
        o = 0.3
        lo, hi = RUNE_TOP - 12, RUNE_TOP
        for side, (face, frm, to) in enumerate([('north', [0, lo, -o], [16, hi, -o]), ('south', [0, lo, 16 + o], [16, hi, 16 + o]),
                                                ('west', [-o, lo, 0], [-o, hi, 16]), ('east', [16 + o, lo, 0], [16 + o, hi, 16])]):
            key = f'run{side}'
            textures[key] = f'botwclimb:block/altar_item_rune{side}_{rune}'
            elements.append({"from": frm, "to": to, "faces": {face: {"texture": '#' + key, "uv": [0, 0, 16, 16], "neoforge_data": light}}})
        model = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": textures, "elements": elements}
        name = f'stamina_altar_phase_{n}'
        json.dump(model, open(f'{ASSETS}models/item/{name}.json', 'w'), indent=2)
        names[n] = name

    # 1.21.1 to 1.21.3: the item model picks the phase with overrides; the base model is phase 0
    json.dump({"parent": "botwclimb:item/stamina_altar_phase_0",
               "overrides": [{"predicate": {"botwclimb:altar_phase": n / 8}, "model": f"botwclimb:item/stamina_altar_phase_{n}"} for n in range(1, 9)]},
              open(ASSETS + 'models/item/stamina_altar.json', 'w'), indent=2)
    # 1.21.4 on: an item definition with a range dispatch on the same property
    definition = {"model": {"type": "minecraft:range_dispatch", "property": "botwclimb:altar_phase",
                            "entries": [{"threshold": n, "model": {"type": "minecraft:model", "model": f"botwclimb:item/stamina_altar_phase_{n}"}} for n in range(1, 9)],
                            "fallback": {"type": "minecraft:model", "model": "botwclimb:item/stamina_altar_phase_0"}}}
    for v in os.listdir('versions'):
        p = f'versions/{v}/resources/assets/botwclimb/items/stamina_altar.json'
        if os.path.exists(p): json.dump(definition, open(p, 'w'), indent=2)
    print('ok')


main()

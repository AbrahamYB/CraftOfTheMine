"""
Generates the paraglider fabric textures (128x64 PNG each) into
src/main/resources/assets/botwclimb/textures/entity/paraglider/.

Every fabric is drawn once as a 40x16 "canopy canvas" seen from above (row 0 = front edge, row 15 = trailing edge,
columns 12..27 = centre panel, 0..11 = player's-left wing, 28..39 = right wing), then copied into the fixed UV layout
used by ParagliderModel. To add a fabric: write one draw function + one FABRICS entry, run this script, and add the
id to FabricRegistry.java.

UV layout of the sheet (must match ParagliderModel):
  centre  net at (0,0)   top (16..31, 0..15)  bottom (32..47, 0..15)
  left    net at (64,0)  top (80..91, 0..15)  bottom (92..103, 0..15)     (wing on the player's left, +x)
  right   net at (0,17)  top (16..27,17..32)  bottom (28..39,17..32)
  edge strip (0..83, 34..37)   tassel (0..3, 40..44)   ribbon net (64..107, 40..60)   strut net (110..113, 40..53)
"""
import os, sys, zlib, struct, math

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'botwclimb',
                   'textures', 'entity', 'paraglider')
W, H = 40, 16


def hexc(s):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


class Canvas:
    def __init__(self, base):
        self.p = [[hexc(base)] * W for _ in range(H)]

    def set(self, x, y, c):
        if 0 <= x < W and 0 <= y < H:
            self.p[y][x] = hexc(c)

    def sym(self, x, y, c):  # draw (x,y) and its mirror
        self.set(x, y, c)
        self.set(W - 1 - x, y, c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c, sym=False):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            (self.sym if sym else self.set)(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def disc(self, cx, cy, r, c, ring=None):
        for y in range(H):
            for x in range(W):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    self.set(x, y, c)
                elif ring and d <= r + 1:
                    self.set(x, y, ring)

    def ring(self, cx, cy, r, c, w=1):
        for y in range(H):
            for x in range(W):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if r - w < d <= r:
                    self.set(x, y, c)

    def ellipse(self, cx, cy, rx, ry, c, fill=True):
        for y in range(H):
            for x in range(W):
                d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
                if (d <= 1) if fill else (0.55 < d <= 1.0):
                    self.set(x, y, c)

    def tri(self, cx, top, h, c, up=True):
        for i in range(h):
            y = top + i if up else top + h - 1 - i
            for x in range(cx - i, cx + i + 1):
                self.set(x, y, c)

    def frame(self, trim, front=True, sides=True, rear=True):
        if front:
            self.rect(0, 0, W - 1, 0, trim)
        if sides:
            self.rect(0, 0, 0, H - 1, trim)
            self.rect(W - 1, 0, W - 1, H - 1, trim)
        if rear:
            self.rect(0, H - 1, W - 1, H - 1, trim)

    def scatter(self, c, pts):
        for x, y in pts:
            self.sym(x, y, c)


C = 19.5  # canvas centre column


# ---------------------------------------------------------------- shared motifs
def tri2(cv, top, h, c, up=True):
    """Triangle centred on the canvas axis (even width, so it stays symmetric)."""
    for i in range(h):
        y = top + i if up else top + h - 1 - i
        for x in range(19 - i, 21 + i):
            cv.set(x, y, c)


def hyrule_crest(cv, top, c, c2=None):
    """Winged Hylian crest: three feather tiers each side, a triforce in the middle, a tail below."""
    c2 = c2 or c
    for t in range(3):                                   # feather tiers
        y = top + 2 + t * 2
        cv.line(17, y + 1, 12 - t, y - 1 - (2 - t) // 2, c2, sym=True)
        cv.line(17, y + 2, 12 - t, y, c2, sym=True)
    tri2(cv, top, 3, c)
    tri2(cv, top + 3, 3, c)
    cv.rect(18, top + 4, 21, top + 5, c)                 # hollow centre of the triforce
    tri2(cv, top + 4, 2, '#00000000' if False else c)
    cv.rect(19, top + 7, 20, top + 11, c)                # tail
    cv.line(17, top + 8, 19, top + 10, c2, sym=True)


def sheikah_eye(cv, top, c, c2):
    """Sheikah eye: lashes, almond outline, iris, and the tear. `c2` is the cloth colour (the eye's inside)."""
    cy = top + 4
    for x in range(12, 28):                              # almond outline
        t = (x + 0.5 - 19.5) / 8.0
        hh = 3.4 * (1 - t * t) ** 0.8
        if hh > 0.4:
            cv.set(x, int(round(cy - hh)), c)
            cv.set(x, int(round(cy + hh)), c)
    cv.set(12, cy, c); cv.set(27, cy, c)
    cv.disc(19.5, cy + 0.5, 2.3, c)                      # iris
    cv.disc(19.5, cy + 0.5, 1.0, c2)                     # pupil
    cv.rect(19, top - 2, 20, top, c)                     # centre lash
    cv.rect(15, top - 1, 15, top + 1, c)                 # side lashes
    cv.rect(24, top - 1, 24, top + 1, c)
    cv.rect(19, cy + 4, 20, cy + 8, c)                   # tear
    cv.rect(18, cy + 8, 21, cy + 8, c)


# ---------------------------------------------------------------- fabrics
def ordinary():
    cv = Canvas('#5c4d56')
    cv.rect(0, 0, 11, 15, '#4f424a')
    cv.rect(28, 0, 39, 15, '#4f424a')
    cv.rect(12, 0, 27, 15, '#665663')
    for x in (12, 27):
        cv.rect(x, 1, x, 14, '#c0ae84')
    cv.frame('#c0ae84')
    for i in range(3):  # stitched chevron at the rear
        cv.line(13, 9 + i, 19, 12 + i - 2 if False else 12 + i, '#a78424', sym=False)
    cv.line(13, 10, 19, 13, '#a78424')
    cv.line(26, 10, 20, 13, '#a78424')
    cv.line(13, 11, 19, 14, '#c0ae84')
    cv.line(26, 11, 20, 14, '#c0ae84')
    return cv


def zonai():
    cv = Canvas('#d1c49a')
    cv.rect(28, 0, 39, 15, '#5d6559')          # slate panel on the right wing, cream on the left (as in the render)
    for i in range(0, 11, 3):                   # triangle pattern on both wings
        for j in range(2, 14, 4):
            cv.tri(1 + i, j, 2, '#b09561')
            cv.tri(29 + i, j, 2, '#4b5247')
    cv.frame('#9d6b11')
    ink, ink2 = '#9d6b11', '#523f27'
    for x0 in (14, 22):                          # eye bars of the totem
        cv.rect(x0, 2, x0 + 3, 2, ink)
        cv.rect(x0 + 1, 3, x0 + 2, 4, ink2)
    cv.rect(19, 2, 20, 13, ink)                  # spine
    for y in (6, 8, 10, 12):                     # rungs
        cv.rect(15, y, 24, y, ink if y % 4 == 2 else ink2)
    cv.rect(17, 13, 22, 14, ink2)
    return cv


def royal():
    cv = Canvas('#81433e')
    cv.rect(0, 0, 11, 15, '#435753')
    cv.rect(28, 0, 39, 15, '#1d2c3d')
    cv.rect(12, 0, 27, 15, '#81433e')
    cv.frame('#ae9257')
    for x in (12, 27):
        cv.rect(x, 1, x, 14, '#ae9257')
    hyrule_crest(cv, 2, '#d8be82', '#ae9257')
    return cv


def sheikah():
    cv = Canvas('#5e6060')
    cv.rect(28, 0, 39, 15, '#1d2a3a')
    cv.rect(26, 0, 27, 15, '#2c3a4a')
    cv.frame('#844f1f')
    cv.rect(0, 1, W - 1, 1, '#cfbd8a')
    sheikah_eye(cv, 4, '#cfbd8a', '#5e6060')
    cv.rect(12, 14, 27, 14, '#844f1f')
    return cv


def gerudo():
    cv = Canvas('#445444')
    cv.rect(0, 0, 11, 15, '#3c4a3c')
    cv.rect(28, 0, 39, 15, '#2c3a34')
    cv.rect(0, 0, W - 1, 2, '#c4940f')           # gold band across the front
    for x in range(0, W, 2):
        cv.set(x, 3, '#c4940f')
    cv.rect(0, 0, 1, H - 1, '#c4940f')
    cv.rect(W - 2, 0, W - 1, H - 1, '#7a5f1a')
    cv.frame('#cbb480', front=False, sides=False)
    cv.disc(C, 8, 4.5, '#7c5637', ring='#cbb480')   # terracotta shield with a crescent
    cv.disc(C - 1, 7.5, 2.6, '#cbb480')
    cv.disc(C + 0.2, 7.5, 2.2, '#7c5637')
    for cx in (6, 33):                              # the two curls
        cv.ring(cx, 10, 3, '#817956')
        cv.set(cx + (2 if cx < 20 else -2), 7, '#817956')
    return cv


def zora():
    cv = Canvas('#1e809c')
    cv.rect(0, 0, 11, 15, '#2c5a6c')
    cv.rect(28, 0, 39, 15, '#0e2e41')
    for k in range(-20, 64, 8):                     # scale lattice
        for y in range(16):
            cv.set(k + y, y, '#87a4a5')
            cv.set(k + 15 - y, y, '#87a4a5')
    cv.frame('#d0bc8b')
    tri2(cv, 8, 5, '#d0bc8b')                         # Zora crest: drop with two fins
    cv.line(16, 9, 14, 13, '#d0bc8b', sym=True)
    cv.line(17, 11, 15, 14, '#d0bc8b', sym=True)
    return cv


def goron():
    cv = Canvas('#ab5436')
    cv.rect(0, 0, 11, 15, '#9a4a30')
    cv.rect(28, 0, 39, 15, '#8a432b')
    cv.frame('#c8b281')
    cv.ring(C, 8, 6.5, '#c8b281', w=1.2)             # big ring with the Goron emblem
    cv.rect(18, 5, 21, 10, '#c8b281')                # rock face / spike
    cv.rect(19, 3, 20, 4, '#c8b281')
    cv.set(18, 7, '#ab5436'); cv.set(21, 7, '#ab5436'); cv.set(19, 9, '#ab5436'); cv.set(20, 9, '#ab5436')
    for x0 in (2, 6):                                # arcs on the wings
        cv.line(x0, 3, x0 + 3, 7, '#c8b281', sym=True)
        cv.line(x0 + 3, 7, x0, 11, '#c8b281', sym=True)
    return cv


def yiga():
    cv = Canvas('#af8912')
    cv.rect(0, 0, 9, 15, '#732d18')
    cv.rect(30, 0, 39, 15, '#3d301c')
    cv.frame('#c5b386')
    cv.disc(C, 8, 7, '#c5b386')
    cv.disc(C, 8, 5, '#af8912')
    cv.disc(C, 8, 3.2, '#a48357')
    cv.ellipse(C, 8, 4.5, 1.8, '#261d16')            # slit eye
    cv.disc(C, 8, 1.1, '#c5b386')
    cv.rect(19, 13, 20, 14, '#732d18')
    return cv


def cece():
    cv = Canvas('#644f5b')
    cv.scatter('#4d4543', [(3, 3), (4, 3), (8, 6), (9, 6), (6, 11), (7, 11), (14, 5), (15, 5), (23, 9), (24, 9),
                           (17, 12), (18, 12), (11, 2), (12, 2), (10, 9), (11, 9)])
    cv.scatter('#6f5a66', [(5, 7), (6, 7), (2, 12), (13, 8), (14, 8), (9, 13), (16, 3)])
    cv.rect(0, 11, 5, 15, '#977e22')                 # gold-olive patch at one corner
    cv.frame('#cbba88')
    cv.rect(15, 0, 24, 3, '#cbba88')                 # the cream tab at the front
    cv.rect(17, 4, 22, 4, '#cbba88')
    cv.rect(19, 5, 20, 6, '#cbba88')
    return cv


def addison():
    cv = Canvas('#775273')
    cv.rect(31, 0, 39, 15, '#282736')
    gold, teal = '#b69114', '#3f9c82'
    for (x, y, w, h, c) in [(3, 3, 3, 2, gold), (8, 8, 2, 3, teal), (13, 2, 3, 3, teal), (15, 9, 3, 2, gold),
                            (21, 4, 3, 3, gold), (24, 10, 3, 3, teal), (7, 12, 3, 2, gold), (27, 6, 2, 3, teal),
                            (19, 6, 2, 2, '#ceb37b'), (11, 5, 2, 2, '#ceb37b'), (30, 12, 3, 2, gold)]:
        cv.rect(x, y, x + w - 1, y + h - 1, c)
    cv.frame('#ceb37b')
    return cv


def chuchu():
    cv = Canvas('#10a3bc')
    cv.rect(0, 0, 8, 15, '#0b7c96')
    cv.rect(31, 0, 39, 15, '#08445b')
    cv.frame('#ceb985')
    for cx in (12, 27):                              # the two big eyes
        cv.ellipse(cx, 7, 4, 4.6, '#ac5f0c')
        cv.ellipse(cx, 7, 3.2, 3.8, '#b8a421')
        cv.disc(cx + (1 if cx < 20 else -1), 7.5, 1.8, '#2b2318')
        cv.rect(cx - 2, 4, cx - 1, 5, '#ceb985')
    cv.rect(18, 11, 21, 11, '#ceb985')               # little mouth
    return cv


def aerocuda():
    cv = Canvas('#b2a43c')
    cv.rect(0, 0, 8, 15, '#666250')
    cv.rect(31, 0, 39, 15, '#555142')
    for (x, y) in [(4, 4), (3, 9), (10, 11), (13, 6), (16, 12), (9, 2), (14, 14), (6, 13)]:
        cv.sym(x + 4, y, '#775023'); cv.sym(x + 5, y, '#775023'); cv.sym(x + 4, y + 1, '#8b7234')
    cv.frame('#cfbb85')
    for x in (15, 23):                               # the two ring "eyes"
        cv.ring(x + 0.5, 3.5, 2.5, '#775023', w=1.2)
        cv.set(x, 3, '#cfbb85'); cv.set(x + 1, 3, '#cfbb85')
    cv.tri(19, 6, 5, '#8b7234')
    return cv


def korok():
    cv = Canvas('#bfba8f')
    cv.rect(31, 0, 39, 15, '#4f5449')
    cv.rect(0, 0, 3, 15, '#a8a27a')
    cv.frame('#d7be84')
    leaf = '#4f944c'
    for (cx, cy) in ((14, 4), (24, 3), (19, 9), (8, 9)):  # leaves
        cv.ellipse(cx, cy, 3, 1.8, leaf)
        cv.line(cx - 2, cy + 1, cx + 2, cy - 1, '#bfba8f')
    brown = '#9c791a'
    for (cx, cy) in ((6, 4), (29, 8), (13, 12), (23, 13)):  # pinwheels
        cv.rect(cx, cy - 1, cx, cy + 1, brown)
        cv.rect(cx - 1, cy, cx + 1, cy, brown)
        cv.set(cx + 1, cy - 1, brown); cv.set(cx - 1, cy + 1, brown)
    return cv


def champion():
    cv = Canvas('#0ea1d1')
    cv.rect(0, 0, 11, 15, '#0b86b0')
    cv.rect(28, 0, 39, 15, '#094664')
    cv.frame('#cdbb89')
    for x in (12, 27):
        cv.rect(x, 1, x, 14, '#cdbb89')
    hyrule_crest(cv, 2, '#cdbb89')
    return cv


def majora():
    cv = Canvas('#524a66')
    cv.rect(0, 0, 11, 15, '#473f5a')
    cv.rect(28, 0, 39, 15, '#3b344b')
    cv.frame('#cdb780')
    spike = '#eda700'
    cv.tri(19, 1, 3, spike)                         # spikes of the mask
    cv.tri(19, 12, 3, spike, up=False)
    cv.tri(13, 8, 3, spike)
    cv.rect(12, 6, 27, 10, '#92472b')                # face
    cv.rect(14, 4, 25, 12, '#92472b')
    cv.rect(15, 5, 18, 8, '#eda700')                 # big eyes
    cv.rect(21, 5, 24, 8, '#eda700')
    cv.rect(16, 6, 17, 7, '#1a1b2b'); cv.rect(22, 6, 23, 7, '#1a1b2b')
    cv.rect(17, 10, 22, 10, '#cdb780')               # mouth
    return cv


def demon():
    cv = Canvas('#54544b')
    cv.rect(0, 0, 11, 15, '#4a4a42')
    cv.rect(28, 0, 39, 15, '#3f3d2f')
    cv.frame('#aa9c62')
    cv.tri(19, 2, 7, '#aa9c62')                      # horned diamond emblem
    cv.tri(19, 8, 6, '#aa9c62', up=False)
    cv.tri(19, 4, 3, '#54544b')
    cv.line(13, 2, 16, 6, '#aa9c62', sym=True)       # horns
    cv.line(12, 1, 13, 2, '#aa9c62', sym=True)
    return cv


# id: (draw fn, tassel colour, ribbon colour, ribbon tip colour, trim colour, has long ribbons)
FABRICS = {
    'ordinary': (ordinary, '#c0ae84', '#c0ae84', '#a78424', '#c0ae84', True),
    'zonai':    (zonai, '#9d6b11', '#9d6b11', '#523f27', '#9d6b11', True),
    'royal_hyrulean': (royal, '#ae9257', '#ae9257', '#d8be82', '#ae9257', True),
    'sheikah':  (sheikah, '#cfbd8a', '#844f1f', '#cfbd8a', '#844f1f', True),
    'gerudo':   (gerudo, '#c4940f', '#c4940f', '#7c5637', '#c4940f', True),
    'zora':     (zora, '#d0bc8b', '#2c5a6c', '#d0bc8b', '#d0bc8b', True),
    'goron':    (goron, '#c8b281', '#835f35', '#c8b281', '#c8b281', True),
    'yiga':     (yiga, '#c5b386', '#732d18', '#c5b386', '#c5b386', True),
    'cece':     (cece, '#cbba88', '#644f5b', '#cbba88', '#cbba88', True),
    'addison':  (addison, '#3f9c82', '#b69114', '#3f9c82', '#ceb37b', False),
    'chuchu':   (chuchu, '#ac5f0c', '#10a3bc', '#ac5f0c', '#ceb985', True),
    'aerocuda': (aerocuda, '#775023', '#8b7234', '#775023', '#cfbb85', True),
    'korok':    (korok, '#9c791a', '#4f944c', '#9c791a', '#d7be84', True),
    'champion': (champion, '#cdbb89', '#cdbb89', '#0ea1d1', '#cdbb89', True),
    'majora':   (majora, '#eda700', '#eda700', '#92472b', '#cdb780', True),
    'demon_king': (demon, '#aa9c62', '#aa9c62', '#54544b', '#aa9c62', True),
}

STRUT = '#b09a6e'


# ---------------------------------------------------------------- sheet assembly
def sheet(cv, tassel, ribbon, tip, trim):
    img = [[(0, 0, 0, 0)] * 128 for _ in range(64)]

    def put(x, y, c):
        img[y][x] = c if len(c) == 4 else hexc(c)

    C_ = cv.p
    for j in range(16):
        for i in range(16):    # centre
            put(16 + i, j, C_[15 - j][27 - i]); put(32 + i, j, C_[15 - j][12 + i])
        for i in range(12):
            put(80 + i, j, C_[15 - j][11 - i]); put(92 + i, j, C_[15 - j][28 + i])   # left wing
            put(16 + i, 17 + j, C_[15 - j][39 - i]); put(28 + i, 17 + j, C_[15 - j][i])  # right wing
    for x in range(0, 64):
        put(x, 16, trim)                      # centre side strips
    for x in range(64, 120):
        put(x, 16, trim)
    for x in range(0, 56):
        put(x, 33, trim)
    for y in range(34, 38):
        for x in range(0, 84):
            put(x, y, trim)
    for y in range(40, 45):
        for x in range(0, 4):
            put(x, y, tassel)
    for y in range(40, 61):                   # ribbon net: body colour, tip (far end) in the first rows
        for x in range(64, 108):
            put(x, y, tip if y < 44 else ribbon)
    for y in range(40, 54):
        for x in range(110, 114):
            put(x, y, STRUT)
    return img


def write_png(path, img):
    raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in img)

    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 128, 64, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(data)


def preview(path, canvases, scale=6):
    cols = 4
    cw, ch = W * scale + 8, H * scale + 8
    rows = (len(canvases) + cols - 1) // cols
    img = [[(20, 20, 20, 255)] * (cols * cw) for _ in range(rows * ch)]
    for n, cv in enumerate(canvases):
        ox, oy = (n % cols) * cw + 4, (n // cols) * ch + 4
        for y in range(H):
            for x in range(W):
                for dy in range(scale):
                    for dx in range(scale):
                        img[oy + y * scale + dy][ox + x * scale + dx] = cv.p[y][x]
    raw = b''.join(b'\x00' + b''.join(bytes(p[:3]) for p in row) for row in img)

    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', cols * cw, rows * ch, 8, 2, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(raw, 6)) + chunk(b'IEND', b''))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    cvs = []
    for fid, (fn, tassel, ribbon, tip, trim, _) in FABRICS.items():
        cv = fn()
        cvs.append(cv)
        write_png(os.path.join(OUT, fid + '.png'), sheet(cv, tassel, ribbon, tip, trim))
    if len(sys.argv) > 1:
        preview(sys.argv[1], cvs)
    print('wrote', len(FABRICS), 'fabrics')

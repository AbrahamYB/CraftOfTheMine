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
  flat preview of the canopy (0..39, 46..61), used by the inventory picker
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
    # flat grey-violet with diagonal two-tone bands, a cream tab at the front and a flat gold corner
    cv = Canvas('#534953')
    for x in range(W):
        for y in range(H):
            if ((x + y) // 4) % 2 == 0:
                cv.set(x, y, '#5e4f5e')
    cv.rect(0, 9, 3, 15, '#8c6f24')
    cv.rect(36, 9, 39, 15, '#8c6f24')
    cv.frame('#cbba88')
    cv.rect(14, 1, 25, 2, '#cbba88')
    cv.rect(16, 3, 23, 4, '#cbba88')
    cv.rect(18, 5, 21, 6, '#cbba88')
    return cv


def addison():
    # flat quilt of 4x4 blocks in three colours, cream stitching on the border
    cv = Canvas('#775273')
    cols = ['#775273', '#3f9c82', '#b69114']
    for by in range(4):
        for bx in range(10):
            c = cols[(bx + by) % 3]
            cv.rect(bx * 4, by * 4, bx * 4 + 3, by * 4 + 3, c)
    cv.rect(31, 0, 39, 15, '#282736')
    cv.frame('#ceb37b')
    return cv


def chuchu():
    # flat cyan canopy; two big amber-ringed eyes with pupils and a smiling mouth
    cv = Canvas('#10a3bc')
    cv.rect(0, 0, 8, 15, '#0b7c96')
    cv.rect(31, 0, 39, 15, '#08445b')
    cv.frame('#ceb985')
    for cx in (13, 26):
        cv.disc(cx + 0.5, 7.5, 4.6, '#ac5f0c')       # amber ring
        cv.disc(cx + 0.5, 7.5, 3.4, '#e8b431')       # yellow iris
        cv.disc(cx + 0.5, 7.5, 1.7, '#2b2318')       # pupil
    cv.line(15, 12, 19, 13, '#ceb985')              # smiling mouth
    cv.line(19, 13, 24, 12, '#ceb985')
    cv.line(14, 11, 15, 12, '#ceb985')
    cv.line(24, 11, 25, 12, '#ceb985')
    return cv


def aerocuda():
    # flat olive-gold with dark chevrons across the front and two round eyes
    cv = Canvas('#b2a43c')
    cv.rect(0, 0, 8, 15, '#8b7234')
    cv.rect(31, 0, 39, 15, '#775023')
    cv.frame('#cfbb85')
    for k in range(3):
        y = 4 + k * 4
        cv.line(10, y + 2, 19, y, '#775023')
        cv.line(19, y, 28, y + 2, '#775023')
    for cx in (15, 24):
        cv.disc(cx + 0.5, 3.5, 2.2, '#775023')
        cv.disc(cx + 0.5, 3.5, 1.0, '#cfbb85')
    return cv


def korok():
    # flat cream canopy with simple green leaves and small brown pinwheels
    cv = Canvas('#bfba8f')
    cv.rect(31, 0, 39, 15, '#4f5449')
    cv.rect(0, 0, 3, 15, '#a8a27a')
    cv.frame('#d7be84')
    leaf, brown = '#4f944c', '#9c791a'
    for (cx, cy) in ((14, 4), (24, 3), (19, 10), (9, 10)):
        cv.tri(cx, cy - 2, 3, leaf, up=True)
        cv.set(cx, cy + 1, leaf)
    for (cx, cy) in ((6, 5), (30, 9), (13, 13), (25, 13)):
        cv.set(cx, cy, brown)
        cv.set(cx - 1, cy, brown)
        cv.set(cx + 1, cy, brown)
        cv.set(cx, cy - 1, brown)
        cv.set(cx, cy + 1, brown)
    return cv


def champion():
    # plain bright blue field, small tan Hylian crest near the front, darker blue triangle at the rear
    cv = Canvas('#0fa1d0')
    cv.rect(0, 0, 11, 15, '#0b86b0')
    cv.rect(28, 0, 39, 15, '#0d4b68')
    tri2(cv, 8, 8, '#0d6f94', up=False)              # rear shading triangle
    cv.frame('#d4bf8a')
    for x in (12, 27):
        cv.rect(x, 1, x, 14, '#9e8e6b')
    # crest near the front: wings, triforce, tail
    for t in range(2):
        cv.line(17, 2 + t * 2, 13, 1 + t * 2, '#d4bf8a', sym=True)
        cv.line(17, 3 + t * 2, 14, 3 + t * 2, '#d4bf8a', sym=True)
    tri2(cv, 1, 2, '#d4bf8a')
    tri2(cv, 3, 3, '#d4bf8a')
    cv.rect(19, 6, 20, 7, '#d4bf8a')
    return cv


def majora():
    # flat purple-grey mask: crisp amber eyes with dark pupils, spikes on top, a stitched mouth band
    cv = Canvas('#4d4763')
    cv.rect(0, 0, 11, 15, '#423b55')
    cv.rect(28, 0, 39, 15, '#3a334c')
    cv.frame('#bca97a')
    cv.tri(19, 1, 3, '#eda700')
    cv.tri(13, 6, 3, '#eda700')
    cv.tri(25, 6, 3, '#eda700')
    for cx in (14, 25):
        cv.disc(cx + 0.5, 8.5, 3.6, '#694626')
        cv.disc(cx + 0.5, 8.5, 2.8, '#eda700')
        cv.disc(cx + 0.5, 8.5, 1.2, '#1a1b2b')
    cv.rect(10, 12, 29, 13, '#97523e')
    for x in range(11, 29, 2):
        cv.set(x, 14, '#cdb780')
    return cv


def demon():
    # flat dark canopy with an outlined khaki diamond emblem and a bar of marks below it
    cv = Canvas('#51524a')
    cv.rect(0, 0, 11, 15, '#474840')
    cv.rect(28, 0, 39, 15, '#3a3b34')
    cv.frame('#ac9d64')
    cv.line(19, 2, 13, 8, '#ac9d64')
    cv.line(13, 8, 19, 14, '#ac9d64')
    cv.line(19, 14, 26, 8, '#ac9d64')
    cv.line(26, 8, 19, 2, '#ac9d64')
    cv.line(19, 5, 16, 8, '#ac9d64')
    cv.line(19, 5, 22, 8, '#ac9d64')
    cv.rect(16, 11, 22, 11, '#ac9d64')
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
    'cece':     (cece, '#cbba88', '#534953', '#cbba88', '#cbba88', True),
    'addison':  (addison, '#3f9c82', '#b69114', '#3f9c82', '#ceb37b', False),
    'chuchu':   (chuchu, '#ac5f0c', '#10a3bc', '#ac5f0c', '#ceb985', True),
    'aerocuda': (aerocuda, '#775023', '#8b7234', '#775023', '#cfbb85', True),
    'korok':    (korok, '#9c791a', '#4f944c', '#9c791a', '#d7be84', True),
    'champion': (champion, '#d4bf8a', '#0d4b68', '#d4bf8a', '#d4bf8a', True),
    'majora':   (majora, '#e9a001', '#e9a001', '#97523e', '#bca97a', True),
    'demon_king': (demon, '#ac9d64', '#ac9d64', '#51524a', '#ac9d64', True),
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
    for y in range(16):                       # flat canopy preview strip used by the inventory fabric picker
        for x in range(40):
            put(x, 46 + y, C_[y][x])
    return img


def write_png(path, img):
    write_png_size(path, img, 128, 64)


def write_png_size(path, img, w, h):
    raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in img)

    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
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

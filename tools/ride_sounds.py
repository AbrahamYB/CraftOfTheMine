"""Synthesises the terminal ride's sounds (all original: sines, noise and envelopes, nothing sampled).

Shapes follow what each moment of the reference footage does (measured with spectrograms): a fluttering low shimmer for the
spiral and the void, a bright chirp for the terminal screen, a stacked bell chord for the flames. Run:
    python tools/ride_sounds.py <out dir>      (writes .wav files; encode them to .ogg with ffmpeg)
"""
import sys
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfilt

SR = 48000
rng = np.random.default_rng(7)


def t_of(sec):
    return np.arange(int(SR * sec)) / SR


def band(x, lo, hi, order=4):
    return sosfilt(butter(order, [lo, hi], btype="band", fs=SR, output="sos"), x)


def low(x, hi, order=4):
    return sosfilt(butter(order, hi, btype="low", fs=SR, output="sos"), x)


def env(t, attack, tau, start=0.0):
    """Quick rise, exponential fall, silent before start."""
    u = t - start
    e = np.where(u < 0, 0.0, np.minimum(1.0, u / max(attack, 1e-4)) * np.exp(-np.maximum(u - attack, 0) / tau))
    return e


def flutter_band(t, lo, hi, n=40, rate=(14, 34), seed=0):
    """Many quiet sines in a band, each pulsing fast and unevenly: the dotted shimmer of the footage."""
    r = np.random.default_rng(seed)
    out = np.zeros_like(t)
    for _ in range(n):
        f = r.uniform(lo, hi) * (1 + 0.004 * np.sin(2 * np.pi * r.uniform(0.2, 0.8) * t + r.uniform(0, 6.3)))
        am = np.abs(np.sin(2 * np.pi * r.uniform(*rate) * t / 2 + r.uniform(0, 6.3))) ** 1.5
        out += np.sin(2 * np.pi * np.cumsum(f) / SR + r.uniform(0, 6.3)) * am * r.uniform(0.4, 1.0)
    return out / n


def chime(t, freqs, start=0.0, spread=0.25, tau=(0.45, 0.9), seed=1):
    """A bell-like chord: the partials come in one after another over `spread` seconds and ring down with a slight waver."""
    r = np.random.default_rng(seed)
    out = np.zeros_like(t)
    for f in freqs:
        s = start + r.uniform(0, spread)
        vib = 1 + 0.003 * np.sin(2 * np.pi * r.uniform(4, 7) * t)
        shimmer = 0.45 + 0.55 * np.abs(np.sin(2 * np.pi * r.uniform(5, 11) * t + r.uniform(0, 6.3)))  # blinking, as in the footage
        amp = r.uniform(0.5, 1.0) * (1.0 if f < 2500 else 0.55)
        out += amp * np.sin(2 * np.pi * np.cumsum(f * vib) / SR) * env(t, 0.012, r.uniform(*tau), s) * shimmer
    return out / len(freqs) * 3


def noise(n):
    return rng.standard_normal(n)


def sweep_noise(t, f0, f1, start, dur, q=0.35):
    """Noise through a band that glides from f0 to f1 (a whoosh), done in short blocks."""
    x = noise(len(t))
    out = np.zeros_like(t)
    blk = 512
    for i in range(0, len(t), blk):
        u = np.clip((t[i] - start) / dur, 0, 1)
        fc = f0 * (f1 / f0) ** u
        lo, hi = fc * (1 - q), min(fc * (1 + q), SR / 2 - 100)
        seg = band(x[max(0, i - 2048):i + blk], lo, hi, 2)[-min(blk, len(t) - i):]
        out[i:i + blk] = seg
    return out


def norm(x, peak=0.9):
    x = x - np.mean(x)
    fade = np.ones_like(x)
    k = int(0.02 * SR)
    fade[-k:] = np.linspace(1, 0, k)
    x = x * fade
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def terminal_open():
    """The destination screen opening: a bright tone that leaps up and glides back down to a pulsing ring, over a soft shimmer."""
    t = t_of(1.5)
    # the glide: 1.3 kHz, up to 3.8 kHz in 0.08 s, back down to 1.55 kHz by 0.2 s, then held and pulsing as it fades
    f = np.where(t < 0.12, 1300, np.where(t < 0.2, 1300 + 2500 * np.sin(np.pi * (t - 0.12) / 0.16), 0))
    f = np.where(t >= 0.2, 1550 + 250 * np.exp(-(t - 0.2) / 0.03), f)
    glide = np.sin(2 * np.pi * np.cumsum(f) / SR)
    pulse = 0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 4.5 * t))
    tone = (glide + 0.1 * np.sin(2 * np.pi * np.cumsum(2 * f) / SR)) * env(t, 0.01, 0.55, 0.0) * pulse
    bed = flutter_band(t, 180, 620, 36, seed=11) * env(t, 0.04, 0.5)
    harm = sum(np.sin(2 * np.pi * h * t) * env(t, 0.01, 0.06, 0.05) for h in (1263, 2526, 3157, 4100)) * 0.12
    return norm(0.5 * tone + 1.1 * bed + harm, 0.7)


def ride_start(length=2.9):
    """The spiral sweeping in: a held bright chord, a quick rising sweep, then the shimmer with a low rumble that lasts until the void."""
    t = t_of(length)
    chord = sum(np.sin(2 * np.pi * f * t + i) * a for i, (f, a) in enumerate([(315, 1), (630, .8), (945, .6), (1262, .5), (1580, .45), (2100, .5), (3700, .35)]))
    chord *= np.clip(t / 0.03, 0, 1) * np.where(t < 0.38, 1, np.exp(-(t - 0.38) / 0.04))
    rise = sum(np.sin(2 * np.pi * np.cumsum(f0 * (1 + 1.6 * np.clip((t - 0.36) / 0.09, 0, 1))) / SR) for f0 in (620, 900, 1250)) * env(t, 0.01, 0.06, 0.36)
    whoosh = sweep_noise(t, 500, 3500, 0.34, 0.12) * env(t, 0.03, 0.15, 0.34)
    tail_env = np.clip((t - 0.42) / 0.08, 0, 1) * np.exp(-np.maximum(t - 0.5, 0) / (length * 0.45))
    shimmer = (flutter_band(t, 280, 720, 48, seed=21) + 0.35 * band(noise(len(t)), 400, 2200)) * tail_env
    rumble = low(noise(len(t)), 90) * 6 * tail_env
    return norm(0.3 * chord + 0.5 * rise + 0.6 * whoosh + 2.4 * shimmer + 0.8 * rumble, 0.85)


def void(length=3.6, hit=0.7):
    """Into the void: a soft broadband hit, then the fluttering low band with a few faint glints, slowly fading."""
    t = t_of(length)
    burst = band(noise(len(t)), 300, 9000) * env(t, 0.005, 0.12) * hit * 0.4
    pad = flutter_band(t, 200, 600, 56, rate=(16, 36), seed=31) * np.clip(t / 0.05, 0, 1) * (0.2 + 0.8 * np.exp(-np.maximum(t - 0.4, 0) / 0.2)) * np.exp(-t / (length * 0.8))  # loud at first, then a quiet hold
    glints = np.zeros_like(t)
    r = np.random.default_rng(32)
    for _ in range(14):
        s, f = r.uniform(0.6, length - 0.4), r.uniform(900, 2600)
        glints += np.sin(2 * np.pi * f * t) * env(t, 0.002, 0.018, s) * 0.05
    chord = sum(np.sin(2 * np.pi * f * t) for f in (421, 527, 585, 644)) * 0.015 * np.clip((t - 0.3) / 0.6, 0, 1) * np.exp(-t / length)
    return norm(burst + 1.4 * pad + glints + chord, 0.75)


def arrive():
    """Set down on the plot: a hit across the whole range and the flutter ringing out over a second."""
    t = t_of(1.4)
    hit = band(noise(len(t)), 150, 9000) * env(t, 0.004, 0.09)
    thump = np.sin(2 * np.pi * np.cumsum(70 * np.exp(-t / 0.15) + 45) / SR) * env(t, 0.004, 0.12)
    ring = flutter_band(t, 200, 620, 48, seed=41) * np.clip(t / 0.01, 0, 1) * np.exp(-np.maximum(t - 0.25, 0) / 0.33)  # holds a quarter second, then rings out
    spark = sum(np.sin(2 * np.pi * f * t) for f in (1900, 2300, 2550)) * env(t, 0.005, 0.07, 0.2) * 0.2
    return norm(0.35 * hit + 0.5 * thump + 2.4 * ring + spark, 0.85)


def jump():
    """The spring: a soft low thump and a whoosh that falls away."""
    t = t_of(0.6)
    thump = np.sin(2 * np.pi * np.cumsum(90 * np.exp(-t / 0.08) + 50) / SR) * env(t, 0.004, 0.12)
    whoosh = sweep_noise(t, 6000, 1200, 0.02, 0.35) * env(t, 0.03, 0.3, 0.02)
    low_band = flutter_band(t, 200, 550, 24, seed=51) * env(t, 0.005, 0.35)
    return norm(0.4 * thump + 0.8 * whoosh + 1.0 * low_band, 0.8)


SPARK = [515, 785, 925, 1230, 1394, 1570, 1851, 2121, 2367, 2777, 3117, 3700, 4675]


def sparkle():
    """The flames: a bell chord whose notes come in one after another, shimmering as it rings down."""
    t = t_of(1.4)
    c = chime(t, SPARK, 0.0, 0.35, tau=(0.14, 0.32), seed=61)
    air = band(noise(len(t)), 2500, 9000) * env(t, 0.05, 0.35) * 0.15
    return norm(c + air, 0.75)


def return_land():
    """A Return's landing: a soft swell in the middle, then the same bell chord as the swirl rises."""
    t = t_of(1.6)
    swell = sum(np.sin(2 * np.pi * np.cumsum(f * (1 + 0.25 * np.clip(t / 0.15, 0, 1))) / SR) for f in (720, 860)) * env(t, 0.15, 0.2)
    thump = np.sin(2 * np.pi * np.cumsum(60 * np.exp(-t / 0.2) + 40) / SR) * env(t, 0.06, 0.15)
    c = chime(t, SPARK, 0.2, 0.35, tau=(0.25, 0.45), seed=71)
    return norm(0.5 * swell + 0.3 * thump + c, 0.8)


if __name__ == "__main__":
    out = sys.argv[1]
    for name, fn in [("terminal_open", terminal_open), ("ride_start", ride_start), ("void", void), ("arrive", arrive),
                     ("jump", jump), ("sparkle", sparkle), ("return_land", return_land)]:
        x = fn()
        wavfile.write(f"{out}/{name}.wav", SR, (x * 32767).astype(np.int16))
        print(name, round(len(x) / SR, 2))

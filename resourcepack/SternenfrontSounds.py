# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy>=2", "scipy>=1.13"]
# ///
"""Synthesizes Sternenfront's sounds into sounds/sternenfront and their sternenfront.* entries in sounds.json.

Run with `uv run SternenfrontSounds.py`. The files are mono, because the client only places mono sounds in the world.
"""
import json
import subprocess
import tempfile
import wave
import zlib
from pathlib import Path

import numpy as np
from scipy import signal

RATE = 22050
ROOT = Path(__file__).parent / "src/assets/blockcraft"
OUT = ROOT / "sounds/sternenfront"
NAMESPACE = "sternenfront."


def samples(seconds):
    return int(round(seconds * RATE))


def time(seconds):
    return np.arange(samples(seconds)) / RATE


def progress(seconds):
    return np.linspace(0, 1, samples(seconds))


def glide(start, end, seconds, curve=1.0):
    return start * (end / start) ** (progress(seconds) ** curve)


def held(frequency, seconds):
    return np.full(samples(seconds), float(frequency))


def phase(frequency):
    return 2 * np.pi * np.cumsum(frequency) / RATE


def sine(frequency):
    return np.sin(phase(frequency))


def partials(frequency, harmonics):
    p = phase(frequency)
    return sum(np.where(k * frequency < RATE / 2, np.sin(k * p), 0) / k for k in harmonics)


def saw(frequency, harmonics=16):
    return partials(frequency, range(1, harmonics + 1))


def square(frequency, harmonics=15):
    return partials(frequency, range(1, harmonics + 1, 2))


def noise(rng, seconds):
    return rng.standard_normal(samples(seconds))


def crackle(rng, seconds, density):
    return (rng.random(samples(seconds)) < density) * rng.standard_normal(samples(seconds))


def filtered(x, kind, cutoff, order=2):
    return signal.sosfilt(signal.butter(order, cutoff, kind, fs=RATE, output="sos"), x)


def swept(x, centre, q=2.0, low=False):
    """A state-variable filter whose cutoff follows `centre` sample by sample."""
    tuning = (2 * np.sin(np.pi * np.minimum(centre, RATE / 7) / RATE)).tolist()
    damping = 1 / q
    lowpass = bandpass = 0.0
    out = []
    for f, sample in zip(tuning, x.tolist()):
        lowpass += f * bandpass
        bandpass += f * (sample - lowpass - damping * bandpass)
        out.append(lowpass if low else bandpass)
    return np.array(out)


def periodic(x, low=None, high=None):
    """Filters in the frequency domain, which keeps a loop seamless where a running filter would leave a seam."""
    frequencies = np.fft.rfftfreq(len(x), 1 / RATE)
    response = np.ones_like(frequencies)
    if high:
        response /= 1 + (frequencies / high) ** 4
    if low:
        response /= 1 + (low / np.maximum(frequencies, 1e-9)) ** 4
    return np.fft.irfft(np.fft.rfft(x) * response, len(x))


def unit(x):
    return x / np.max(np.abs(x))


def decay(seconds, rate, attack=0.002):
    t = time(seconds)
    return np.minimum(t / attack, 1) * np.exp(-t * rate)


def swell(seconds, attack, release):
    t = time(seconds)
    return np.clip(np.minimum(t / attack, (seconds - t) / release), 0, 1)


def layer(*parts):
    placed = [(0, part) if isinstance(part, np.ndarray) else part for part in parts]
    out = np.zeros(max(samples(offset) + len(part) for offset, part in placed))
    for offset, part in placed:
        out[samples(offset):samples(offset) + len(part)] += part
    return out


def echo(x, delay, gain, repeats=2):
    return layer(x, *((delay * k, x * gain ** k) for k in range(1, repeats + 1)))


def thump(start, end, seconds, rate):
    return sine(glide(start, end, seconds)) * decay(seconds, rate)


def bell(frequency, seconds, rate):
    t = time(seconds)
    tones = ((1, 1), (2.76, 0.45), (5.4, 0.2))
    return np.minimum(t / 0.002, 1) * sum(
        loudness * sine(held(frequency * ratio, seconds)) * np.exp(-t * rate * ratio ** 0.5) for ratio, loudness in tones)


def blaster(rng, start, end, seconds, drive=1.5, crack=0.3, tail=0.25):
    """A bolt leaving the barrel: a fast falling chirp, driven into a buzz, with a crack of noise on the shot."""
    f = glide(start, end, seconds, 0.5)
    body = np.tanh(drive * (sine(f) + 0.5 * sine(f * 2.02) + 0.25 * sine(f * 3.03))) * decay(seconds, 5 / seconds, 0.003)
    burst = unit(filtered(noise(rng, 0.04), "bandpass", [1800, 7000])) * decay(0.04, 90, 0.001)
    return echo(layer(body, crack * burst), 0.045, tail)


def heavy_rifle(rng):
    return layer(blaster(rng, 1100, 70, 0.38, drive=3, crack=0.6, tail=0.3), 0.8 * thump(140, 45, 0.25, 14))


def long_rifle(rng):
    return layer(blaster(rng, 3400, 220, 0.42, drive=1.2, crack=0.5, tail=0.4), 0.4 * thump(120, 50, 0.2, 18))


def twin_pistol(rng):
    return layer(blaster(rng, 2700, 700, 0.12, drive=1.8), (0.05, 0.85 * blaster(rng, 2400, 620, 0.12, drive=1.8)))


def bolt_impact(rng):
    sizzle = unit(filtered(noise(rng, 0.3), "bandpass", [2500, 8000])) * decay(0.3, 14, 0.001)
    splash = unit(filtered(noise(rng, 0.08), "bandpass", [200, 1200])) * decay(0.08, 50, 0.001)
    return layer(0.6 * sizzle, splash, 0.7 * thump(300, 80, 0.08, 40))


def blade(frequency):
    """A blade's buzz: a detuned sawtooth pair over a sub octave."""
    return saw(frequency, 12) + 0.8 * saw(frequency * 1.012, 12) + 0.3 * sine(frequency * 0.5)


def saber_hum(rng):
    seconds = 2.0
    t = time(seconds)
    voice = saw(held(90, seconds), 12) + 0.8 * saw(held(91, seconds), 12) + 0.3 * sine(held(45, seconds))
    flutter = 1 + 0.12 * np.sin(2 * np.pi * 1.5 * t) + 0.05 * np.sin(2 * np.pi * 7 * t)
    buzz = unit(periodic(noise(rng, seconds), 150, 900))
    return unit(periodic(voice, high=1400)) * flutter + 0.12 * buzz


def saber_ignite(rng):
    seconds = 0.7
    x = progress(seconds)
    voice = unit(filtered(blade(90 * (0.3 + 0.7 * (1 - np.exp(-x * 6)))), "lowpass", 1600)) * np.minimum(x * 6, 1)
    snap = unit(filtered(noise(rng, 0.18), "bandpass", [900, 5000])) * decay(0.18, 22, 0.001)
    rise = unit(swept(noise(rng, seconds), 300 + 2600 * np.sin(np.pi * x), 2.5)) * np.sin(np.pi * x) ** 2
    return layer(0.8 * voice, 0.6 * snap, 0.4 * rise)


def saber_retract(rng):
    seconds = 0.55
    x = progress(seconds)
    voice = unit(filtered(blade(90 * (1 - 0.72 * x ** 0.8)), "lowpass", 1600)) * (1 - x) ** 1.5
    fall = unit(swept(noise(rng, seconds), 2600 - 2300 * x, 2.5)) * np.sin(np.pi * x) ** 2
    return layer(0.8 * voice, 0.35 * fall, (seconds - 0.03, 0.3 * thump(220, 120, 0.03, 120)))


def saber_swing(rng, seconds, base, depth):
    arc = np.sin(np.pi * progress(seconds)) ** 2
    voice = unit(filtered(blade(base * (1 + depth * arc)), "lowpass", 1600)) * (0.35 + 0.65 * arc)
    whoosh = unit(swept(noise(rng, seconds), 350 + 2000 * arc, 1.5)) * arc
    return 0.8 * voice + 0.45 * whoosh


def saber_clash(rng):
    seconds = 0.6
    t = time(seconds)
    sparks = unit(filtered(crackle(rng, seconds, 0.03), "highpass", 1500)) * np.exp(-t * 4)
    burst = unit(filtered(noise(rng, seconds), "bandpass", [600, 6000])) * decay(seconds, 14, 0.001)
    ring = unit(sum(sine(held(f, seconds)) * np.exp(-t * rate) for f, rate in ((612, 9), (1371, 12), (2189, 15), (3017, 20))))
    flare = unit(filtered(blade(glide(160, 90, seconds)), "lowpass", 2000)) * decay(seconds, 7, 0.001)
    return 0.6 * sparks + 0.7 * burst + 0.4 * ring + 0.6 * flare


def saber_deflect(rng):
    seconds = 0.32
    t = time(seconds)
    zing = sine(glide(3200, 1100, seconds, 0.7) * (1 + 0.03 * np.sin(2 * np.pi * 38 * t))) * decay(seconds, 9)
    spark = unit(filtered(noise(rng, 0.06), "highpass", 2500)) * decay(0.06, 70, 0.0005)
    flare = unit(filtered(blade(glide(150, 95, 0.2)), "lowpass", 1800)) * decay(0.2, 18)
    return layer(0.6 * zing, 0.5 * spark, 0.5 * flare)


def saber_throw(rng):
    seconds = 1.0
    t = time(seconds)
    spin = 0.5 + 0.5 * np.sin(2 * np.pi * 9 * t)
    voice = unit(filtered(blade(95 * (1 + 0.06 * np.sin(2 * np.pi * 9 * t + 1))), "lowpass", 1700)) * (0.35 + 0.65 * spin)
    whoosh = unit(swept(noise(rng, seconds), 600 + 1400 * spin, 2.0)) * spin
    return (0.8 * voice + 0.35 * whoosh) * swell(seconds, 0.08, 0.3)


def force_push(rng):
    seconds = 0.8
    contour = np.sin(np.pi * np.minimum(progress(seconds) * 1.4, 1))
    air = unit(swept(noise(rng, seconds), 150 + 2300 * contour ** 2, 0.8, low=True)) * contour
    return 0.8 * air + 0.6 * sine(glide(70, 35, seconds)) * decay(seconds, 4, 0.03)


def force_grip(rng):
    seconds = 1.2
    t = time(seconds)
    drone = unit(filtered(saw(held(55, seconds)) + saw(held(58.5, seconds)), "lowpass", 380))
    creak = unit(filtered(crackle(rng, seconds, 28 / RATE), "bandpass", [180, 320]))
    return (0.7 * drone * (0.75 + 0.25 * np.sin(2 * np.pi * 6 * t)) + 0.6 * creak * progress(seconds)) * swell(seconds, 0.25, 0.35)


def explosion(rng, seconds, weight):
    t = time(seconds)
    rumble = unit(filtered(noise(rng, seconds), "lowpass", 700, 4)) * decay(seconds, 3.2 / weight, 0.005)
    blast = unit(filtered(noise(rng, seconds), "bandpass", [300, 5000])) * decay(seconds, 11 / weight)
    sub = sine(glide(75, 28, seconds, 0.6)) * decay(seconds, 4 / weight, 0.004)
    sparks = unit(filtered(crackle(rng, seconds, 0.01), "highpass", 2000)) * np.exp(-t * 3)
    return np.tanh(1.6 * (0.8 * rumble + 0.7 * blast + 0.9 * sub + 0.25 * sparks))


def shield(rng, rising):
    seconds = 0.65
    t = time(seconds)
    start, end = (220, 440) if rising else (440, 180)
    chord = unit(sum(sine(glide(start * ratio, end * ratio, seconds)) for ratio in (1, 1.26, 1.5, 2.01)))
    tremolo = 0.65 + 0.35 * np.sin(2 * np.pi * (14 if rising else 9) * t)
    shimmer = unit(filtered(noise(rng, seconds), "highpass", 3000))
    out = (chord * tremolo + 0.15 * shimmer) * swell(seconds, 0.06 if rising else 0.01, 0.25 if rising else 0.4)
    if rising:
        return out
    return out + 0.4 * unit(filtered(crackle(rng, seconds, 0.02), "highpass", 1800)) * np.exp(-t * 5)


def jetpack(rng):
    seconds = 1.0
    flutter = 0.75 + 0.25 * unit(filtered(noise(rng, seconds), "lowpass", 30))
    roar = unit(filtered(noise(rng, seconds), "lowpass", 900, 3))
    hiss = unit(filtered(noise(rng, seconds), "bandpass", [1500, 4500]))
    return layer((roar + 0.3 * hiss) * flutter * swell(seconds, 0.04, 0.4), 0.7 * thump(160, 50, 0.12, 30))


def grapple(rng):
    thwip = sine(glide(500, 2400, 0.08)) * decay(0.08, 25, 0.001)
    cable = unit(swept(noise(rng, 0.45), glide(3200, 700, 0.45), 4.0)) * swell(0.45, 0.02, 0.2)
    t = time(0.25)
    clank = unit(sum(sine(held(f, 0.25)) * np.exp(-t * rate) for f, rate in ((910, 16), (1630, 22), (2470, 30))))
    return layer(0.7 * thwip, (0.05, 0.6 * cable), (0.5, 0.55 * clank))


def horn(frequency, seconds, vibrato=0.0):
    t = time(seconds)
    voice = np.tanh(1.8 * saw(frequency * (1 + vibrato * np.sin(2 * np.pi * 5.5 * t) * np.minimum(t / 0.3, 1)), 20))
    return filtered(voice, "bandpass", [250, 2600]) * swell(seconds, 0.05, 0.12)


def rally(rng):
    return layer(horn(293.66, 0.18), (0.22, horn(293.66, 0.18)), (0.44, horn(392.0, 0.85, 0.012)))


def orbital_incoming(rng):
    seconds = 2.0
    t = time(seconds)
    x = progress(seconds)
    whistle = sine(glide(2600, 420, seconds, 1.3) * (1 + 0.01 * np.sin(2 * np.pi * 7 * t))) * (0.2 + 0.8 * x ** 1.5)
    rumble = unit(filtered(noise(rng, seconds), "lowpass", 250, 3)) * x ** 2
    return (0.8 * whistle + 0.5 * rumble) * swell(seconds, 0.05, 0.03)


def orbital_impact(rng):
    return layer(explosion(rng, 2.4, 1.6), (0.12, 0.6 * explosion(rng, 2.0, 1.4)))


def hitmarker(rng):
    tick = sine(held(2600, 0.05)) * decay(0.05, 110, 0.0005)
    click = unit(filtered(noise(rng, 0.01), "highpass", 4000)) * decay(0.01, 500, 0.0002)
    return layer(tick, 0.4 * click)


def headshot(rng):
    return layer(hitmarker(rng), (0.02, 0.7 * bell(1500, 0.35, 10)))


def kill(rng):
    return layer(0.8 * thump(190, 85, 0.12, 28), (0.03, 0.55 * bell(1050, 0.25, 12)), (0.11, 0.6 * bell(1400, 0.35, 9)))


def kyber_found(rng):
    seconds = 1.6
    t = time(seconds)
    pad = unit(sine(held(330, seconds)) + 0.7 * sine(held(495, seconds)) + 0.4 * sine(held(661, seconds)))
    chimes = [(0.08 * index, 0.5 * bell(f, 1.0, 4.5)) for index, f in enumerate((659.25, 987.77, 1318.5, 1661.2))]
    return layer(0.35 * pad * swell(seconds, 0.5, 0.7) * (0.8 + 0.2 * np.sin(2 * np.pi * 4 * t)), *chimes)


def honk(seconds, frequency=110):
    """Brummi's low "brmm", a buzzing square that sags at its end."""
    t = time(seconds)
    voice = filtered(square(frequency * (1 - 0.15 * (t / seconds) ** 3)), "lowpass", 900)
    return unit(voice) * (0.8 + 0.2 * np.sin(2 * np.pi * 32 * t)) * swell(seconds, 0.015, 0.05)


def beep(start, end, seconds):
    return unit(filtered(square(glide(start, end, seconds), 7), "lowpass", 3000)) * swell(seconds, 0.004, 0.02)


def warble(seconds):
    t = time(seconds)
    f = 720 + 160 * np.sin(2 * np.pi * 14 * t)
    return unit(sine(f) + 0.3 * sine(2 * f)) * swell(seconds, 0.01, 0.06)


def brummi_scared(rng):
    pitches = np.geomspace(600, 1900, 10) * rng.uniform(0.92, 1.08, 10)
    return layer(honk(0.18, 125), *((0.2 + 0.065 * index, 0.55 * beep(f, f * 1.1, 0.05)) for index, f in enumerate(pitches)))


def sand_beast_rumble(rng):
    seconds = 2.0
    t = time(seconds)
    ground = unit(filtered(noise(rng, seconds), "lowpass", 140, 4))
    surge = 0.6 + 0.4 * unit(filtered(noise(rng, seconds), "lowpass", 3, 1))
    tone = sine(held(38, seconds) * (1 + 0.04 * np.sin(2 * np.pi * 0.7 * t)))
    return (0.8 * ground * surge + 0.5 * tone) * swell(seconds, 0.4, 0.6)


def sand_beast_roar(rng):
    seconds = 2.2
    x = progress(seconds)
    pitch = (62 + 48 * np.sin(np.pi * np.minimum(x * 1.3, 1)) ** 1.2) * (1 + 0.02 * unit(filtered(noise(rng, seconds), "lowpass", 8)))
    source = saw(pitch, 40) * (1 + 0.45 * unit(filtered(noise(rng, seconds), "lowpass", 45)))
    throat = sum(gain * filtered(source, "bandpass", band) for gain, band in ((1.0, [280, 480]), (0.7, [700, 1000]), (0.35, [1500, 2100])))
    breath = unit(filtered(noise(rng, seconds), "bandpass", [300, 2500]))
    return np.tanh(2.2 * unit(unit(throat) + 0.4 * breath)) * swell(seconds, 0.18, 0.7)


SOUNDS = {
    "blaster.carbine": (0.85, lambda rng: blaster(rng, 2300, 260, 0.24)),
    "blaster.repeater": (0.85, lambda rng: blaster(rng, 1500, 180, 0.15, drive=2.5, crack=0.45)),
    "blaster.long_rifle": (0.9, long_rifle),
    "blaster.pistol": (0.8, lambda rng: blaster(rng, 2900, 600, 0.13, drive=1.8)),
    "blaster.twin_pistol": (0.8, twin_pistol),
    "blaster.heavy_rifle": (0.9, heavy_rifle),
    "bolt.impact": (0.7, bolt_impact),
    "saber.ignite": (0.75, saber_ignite),
    "saber.retract": (0.7, saber_retract),
    "saber.hum": (0.45, saber_hum),
    "saber.swing": (0.7, lambda rng: [saber_swing(rng, 0.38, 92, 0.5), saber_swing(rng, 0.45, 86, 0.65),
                                      saber_swing(rng, 0.32, 98, 0.4)]),
    "saber.clash": (0.9, saber_clash),
    "saber.deflect": (0.75, saber_deflect),
    "saber.throw": (0.7, saber_throw),
    "force.push": (0.85, force_push),
    "force.grip": (0.75, force_grip),
    "grenade.beep": (0.5, lambda rng: beep(1750, 1750, 0.09)),
    "grenade.explode": (0.85, lambda rng: explosion(rng, 1.4, 1.0)),
    "shield.up": (0.6, lambda rng: shield(rng, True)),
    "shield.down": (0.6, lambda rng: shield(rng, False)),
    "jetpack": (0.7, jetpack),
    "grapple": (0.7, grapple),
    "rally": (0.75, rally),
    "orbital.incoming": (0.8, orbital_incoming),
    "orbital.impact": (0.85, orbital_impact),
    "hitmarker": (0.5, hitmarker),
    "headshot": (0.6, headshot),
    "kill": (0.65, kill),
    "kyber.found": (0.7, kyber_found),
    "brummi.beep": (0.55, lambda rng: [honk(0.38), layer(honk(0.2, 98), (0.24, 0.6 * beep(880, 900, 0.07)),
                                                        (0.34, 0.6 * beep(660, 640, 0.09))),
                                       warble(0.32), layer(0.6 * beep(520, 980, 0.1), (0.13, 0.6 * beep(980, 700, 0.08)),
                                                           (0.25, honk(0.22, 104)))]),
    "brummi.scared": (0.6, brummi_scared),
    "sand_beast.rumble": (0.85, sand_beast_rumble),
    "sand_beast.roar": (0.85, sand_beast_roar),
}
LOOPS = {"saber.hum"}


def master(x, peak, loop):
    x = unit(x) * peak
    if not loop:
        edge = samples(0.004)
        x[:edge] *= np.linspace(0, 1, edge)
        x[-edge:] *= np.linspace(1, 0, edge)
    return x


def encode(x, target, directory):
    source = Path(directory) / "sound.wav"
    with wave.open(str(source), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(RATE)
        output.writeframes((x * 32767).astype("<i2").tobytes())
    subprocess.run([
        "ffmpeg", "-loglevel", "error", "-y", "-i", str(source), "-c:a", "libvorbis", "-q:a", "1", "-ac", "1",
        "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(target),
    ], check=True)


def register(events):
    """Replaces this script's entries in sounds.json and keeps every other one where it stands."""
    path = ROOT / "sounds.json"
    entries = json.loads(path.read_text()) if path.exists() else {}
    entries = {key: value for key, value in entries.items() if not key.startswith(NAMESPACE) or key in events}
    entries.update(events)
    path.write_bytes((json.dumps(entries, indent=2) + "\n").encode())


OUT.mkdir(parents=True, exist_ok=True)
for stale in OUT.glob("*.ogg"):
    stale.unlink()
events = {}
with tempfile.TemporaryDirectory() as directory:
    for event, (peak, make) in SOUNDS.items():
        made = make(np.random.default_rng(zlib.crc32(event.encode())))
        variants = made if isinstance(made, list) else [made]
        names = [event.replace(".", "_") + (f"_{index}" if len(variants) > 1 else "") for index in range(1, len(variants) + 1)]
        for name, variant in zip(names, variants):
            encode(master(variant, peak, event in LOOPS), OUT / f"{name}.ogg", directory)
        events[NAMESPACE + event] = {"sounds": [f"blockcraft:sternenfront/{name}" for name in names]}
register(events)
print(f"Wrote {sum(len(entry['sounds']) for entry in events.values())} sounds for {len(events)} events to {OUT}")

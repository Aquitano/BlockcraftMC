# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy>=2", "scipy>=1.13"]
# ///
"""Synthesizes the story mode's interface, quest and ambience sounds into sounds/story.

Run with `uv run StorySounds.py`. Every sound is mono, so the client can place it in the world. The voices are
StoryVoices.py's, under story.voice, and this script leaves them alone.
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
OUT = ROOT / "sounds/story"
NAMESPACE = "story."
VOICES = "story.voice."


def t(seconds):
    return np.arange(int(round(seconds * RATE))) / RATE


def tone(frequency, seconds, harmonics=(1.0,)):
    x = t(seconds)
    return sum(weight * np.sin(2 * np.pi * frequency * (index + 1) * x) for index, weight in enumerate(harmonics))


def glide(start, end, seconds):
    x = t(seconds)
    return np.sin(2 * np.pi * np.cumsum(np.geomspace(start, end, len(x))) / RATE)


def envelope(seconds, attack, release):
    x = t(seconds)
    return np.minimum(1, x / max(attack, 1e-4)) * np.clip((seconds - x) / max(release, 1e-4), 0, 1)


def decay(seconds, rate, attack=0.003):
    x = t(seconds)
    return np.minimum(1, x / attack) * np.exp(-rate * x)


def noise(rng, seconds):
    return rng.standard_normal(len(t(seconds)))


def band(x, low, high, order=2):
    return signal.sosfilt(signal.butter(order, [low, high], "bandpass", fs=RATE, output="sos"), x)


def lowpass(x, cutoff, order=2):
    return signal.sosfilt(signal.butter(order, cutoff, "lowpass", fs=RATE, output="sos"), x)


def unit(x):
    peak = np.max(np.abs(x))
    return x / peak if peak > 0 else x


def layer(*parts):
    """Each part is a signal, or (start seconds, signal)."""
    placed = [(0.0, part) if isinstance(part, np.ndarray) else part for part in parts]
    length = max(int(round(start * RATE)) + len(x) for start, x in placed)
    out = np.zeros(length)
    for start, x in placed:
        begin = int(round(start * RATE))
        out[begin:begin + len(x)] += x
    return out


def ping(frequency, seconds, rate):
    return tone(frequency, seconds, (1.0, 0.25, 0.08)) * decay(seconds, rate)


def chord(frequencies, seconds, attack, release, harmonics=(1.0, 0.4, 0.2, 0.1)):
    return sum(tone(f, seconds, harmonics) for f in frequencies) * envelope(seconds, attack, release)


def warm(frequencies, seconds, attack, release, rng):
    """Detuned saws through a soft low-pass, like a string pad."""
    x = t(seconds)
    voices = []
    for f in frequencies:
        for detune in (-0.004, 0.0, 0.005):
            voices.append(signal.sawtooth(2 * np.pi * f * (1 + detune) * x + rng.uniform(0, 6.28)))
    return lowpass(sum(voices), 1800) * envelope(seconds, attack, release)


def wind(rng, seconds):
    x = t(seconds)
    gusts = 0.55 + 0.45 * np.sin(2 * np.pi * x / seconds * 3 + 1.3) * np.sin(2 * np.pi * x / seconds * 2)
    hiss = band(noise(rng, seconds), 180, 1400) * gusts
    whistle = band(noise(rng, seconds), 700, 900, 4) * 0.4 * np.clip(gusts - 0.5, 0, 1)
    return (hiss + whistle) * envelope(seconds, 0.6, 0.6)


def base(rng, seconds):
    hum = tone(55, seconds, (1.0, 0.5, 0.3, 0.15)) * 0.5
    machinery = band(noise(rng, seconds), 90, 400) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.7 * t(seconds)))
    clanks = layer(*[(start, 0.6 * band(noise(rng, 0.12), 900, 3000) * decay(0.12, 40)) for start in (1.3, 3.9, 5.2, 7.1)],
                   np.zeros(len(t(seconds))))
    return (hum + 0.6 * machinery + clanks[:len(hum)]) * envelope(seconds, 0.8, 0.8)


def craft_pass(rng, seconds):
    x = t(seconds)
    closeness = np.exp(-((x - seconds / 2) / (seconds / 5)) ** 2)
    pitch = 1 + 0.12 * np.tanh((seconds / 2 - x) * 2)
    engine = np.sin(2 * np.pi * np.cumsum(140 * pitch) / RATE) + 0.5 * np.sin(2 * np.pi * np.cumsum(283 * pitch) / RATE)
    rumble = noise(rng, seconds)
    roar = lowpass(rumble, 500) * (1 - closeness) + lowpass(rumble, 3000) * closeness
    return (0.5 * engine + roar) * (0.15 + closeness) * envelope(seconds, 0.3, 0.6)


def alarm(rng):
    pulse = lambda: tone(392, 0.16, (1.0, 0.5, 0.3)) * envelope(0.16, 0.01, 0.06)
    return layer(pulse(), (0.22, pulse()), (0.44, glide(392, 784, 0.4) * envelope(0.4, 0.02, 0.15)),
                 (0.0, 0.3 * band(noise(rng, 0.9), 1200, 4000) * decay(0.9, 5)))


def boom(rng, seconds):
    return lowpass(noise(rng, seconds), 220) * decay(seconds, 3, 0.01) + 0.6 * glide(70, 35, seconds) * decay(seconds, 2.5)


SOUNDS = {
    "dialogue.open": (0.45, lambda rng: layer(ping(880, 0.09, 30), (0.06, ping(1320, 0.12, 26)),
                                              0.15 * band(noise(rng, 0.18), 1500, 5000) * decay(0.18, 22))),
    "dialogue.type": (0.35, lambda rng: [band(noise(rng, 0.025), 1800 + 300 * i, 3200 + 300 * i) * decay(0.025, 160)
                                         for i in range(3)]),
    "dialogue.select": (0.4, lambda rng: ping(1400, 0.05, 70)),
    "dialogue.choose": (0.5, lambda rng: layer(ping(660, 0.1, 25), (0.07, ping(990, 0.16, 18)))),
    "dialogue.advance": (0.35, lambda rng: ping(1050, 0.05, 60) + 0.3 * band(noise(rng, 0.05), 2000, 5000) * decay(0.05, 80)),
    "dialogue.close": (0.4, lambda rng: layer(ping(990, 0.08, 30), (0.06, ping(660, 0.14, 22)))),
    "stage": (0.55, lambda rng: layer(ping(523.25, 0.4, 7), (0.12, ping(659.25, 0.4, 7)), (0.24, ping(783.99, 0.6, 5)),
                                      0.4 * chord([261.63, 392], 0.9, 0.2, 0.5))),
    "objective.start": (0.6, alarm),
    "objective.complete": (0.6, lambda rng: layer(ping(659.25, 0.5, 6), (0.1, ping(783.99, 0.5, 6)),
                                                  (0.2, ping(987.77, 0.5, 6)), (0.3, ping(1318.5, 0.8, 4)),
                                                  (0.3, 0.35 * chord([329.63, 493.88, 659.25], 1.0, 0.05, 0.7)))),
    "vote.cast": (0.4, lambda rng: ping(783.99, 0.15, 20)),
    "vote.decided": (0.6, lambda rng: layer(0.6 * warm([196, 293.66, 392], 1.4, 0.4, 0.8, rng), (0.2, ping(587.33, 0.8, 4)))),
    "ending.fabrik": (0.75, lambda rng: layer(warm([110, 130.81, 164.81], 3.4, 0.8, 1.6, rng), (0.0, 0.9 * boom(rng, 2.0)),
                                              (1.2, 0.5 * warm([98, 146.83], 2.2, 0.6, 1.4, rng)))),
    "ending.gefangene": (0.7, lambda rng: layer(warm([196, 246.94, 293.66], 3.4, 0.9, 1.6, rng),
                                                (1.0, 0.6 * warm([293.66, 369.99, 440], 2.4, 0.8, 1.4, rng)),
                                                (1.6, 0.4 * ping(880, 1.2, 3)))),
    "ambience.wind": (0.5, lambda rng: wind(rng, 12.0)),
    "ambience.base": (0.45, lambda rng: base(rng, 9.0)),
    "craft.pass": (0.8, lambda rng: craft_pass(rng, 4.0)),
}
STREAMED = {"ambience.wind", "ambience.base", "ending.fabrik", "ending.gefangene"}
DISTANT = {"craft.pass": 96}


def master(x, peak):
    x = unit(x) * peak
    edge = min(len(x) // 4, int(0.004 * RATE))
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
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(source), "-c:a", "libvorbis", "-q:a", "2", "-ac", "1",
                    "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(target)], check=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for stale in OUT.glob("*.ogg"):
        stale.unlink()
    events = {}
    with tempfile.TemporaryDirectory() as directory:
        for event, (peak, make) in SOUNDS.items():
            made = make(np.random.default_rng(zlib.crc32(event.encode())))
            variants = made if isinstance(made, list) else [made]
            names = [event.replace(".", "_") + (f"_{i}" if len(variants) > 1 else "") for i in range(1, len(variants) + 1)]
            for name, variant in zip(names, variants):
                encode(master(variant, peak), OUT / f"{name}.ogg", directory)
            sounds = []
            for name in names:
                entry = {"name": f"blockcraft:story/{name}"}
                if event in STREAMED:
                    entry["stream"] = True
                if event in DISTANT:
                    entry["attenuation_distance"] = DISTANT[event]
                sounds.append(entry if len(entry) > 1 else entry["name"])
            events[NAMESPACE + event] = {"sounds": sounds}
    path = ROOT / "sounds.json"
    entries = json.loads(path.read_text())
    entries = {key: value for key, value in entries.items() if not key.startswith(NAMESPACE) or key.startswith(VOICES)}
    entries.update(events)
    path.write_text(json.dumps(entries, indent=2) + "\n")
    print(f"Wrote {len(events)} story sounds to {OUT}")


if __name__ == "__main__":
    main()

import json
import math
from pathlib import Path
import struct
import subprocess
import tempfile
import wave

RATE = 44100
ROOT = Path(__file__).parent / "src/assets/blockcraft"
SOUNDS = {
    "click": [(0, 740, 0.055), (0, 1110, 0.035)],
    "open": [(0, 440, 0.075), (0.045, 660, 0.095)],
    "equip": [(0, 523.25, 0.07), (0.05, 783.99, 0.11)],
    "purchase": [(0, 523.25, 0.12), (0.08, 659.25, 0.12), (0.16, 783.99, 0.19)],
    "notify": [(0, 659.25, 0.11), (0.10, 880, 0.16)],
    "win": [(0, 523.25, 0.22), (0.12, 659.25, 0.22), (0.24, 783.99, 0.22), (0.4, 1046.5, 0.38)],
}


def render(notes):
    duration = max(start + length for start, _, length in notes)
    samples = [0.0] * (int(duration * RATE) + 1)
    for start, frequency, length in notes:
        for index in range(int(length * RATE)):
            time = index / RATE
            attack = min(time / 0.004, 1.0)
            release = (1.0 - time / length) ** 2
            tone = math.sin(2 * math.pi * frequency * time)
            tone += 0.18 * math.sin(4 * math.pi * frequency * time)
            samples[int(start * RATE) + index] += tone * attack * release
    peak = max(abs(sample) for sample in samples)
    return b"".join(struct.pack("<h", int(sample / peak * 0.32 * 32767)) for sample in samples)


(ROOT / "sounds/ui").mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory() as directory:
    for name, notes in SOUNDS.items():
        source = Path(directory) / f"{name}.wav"
        with wave.open(str(source), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(RATE)
            output.writeframes(render(notes))
        subprocess.run([
            "ffmpeg", "-loglevel", "error", "-y", "-i", str(source),
            "-c:a", "vorbis", "-strict", "experimental", "-ac", "2", "-q:a", "4", "-map_metadata", "-1",
            str(ROOT / f"sounds/ui/{name}.ogg"),
        ], check=True)
(ROOT / "sounds.json").write_text(json.dumps({
    f"ui.{name}": {"sounds": [f"blockcraft:ui/{name}"]} for name in SOUNDS
}, indent=2) + "\n")

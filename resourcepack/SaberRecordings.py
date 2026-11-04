"""Converts the checked-in lightsaber recordings to positional Minecraft sounds, without network access."""
import array
import hashlib
import json
import subprocess
import sys
from pathlib import Path

RATE = 44100
SOURCES = Path(__file__).parent / "audio/sternenfront"


def generate(root):
    manifest = json.loads((SOURCES / "sources.json").read_text())
    decoded = {}
    for name, source in manifest["sources"].items():
        path = SOURCES / name
        if hashlib.sha256(path.read_bytes()).hexdigest() != source["sha256"]:
            raise ValueError(f"Source recording checksum mismatch: {path}")
        result = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(RATE),
                                 "-f", "f32le", "-"], check=True, capture_output=True)
        pcm = array.array("f", result.stdout)
        if sys.byteorder != "little":
            pcm.byteswap()
        decoded[name] = pcm
    events = {}
    output = root / "sounds/sternenfront"
    output.mkdir(parents=True, exist_ok=True)
    for event, settings in manifest["events"].items():
        names = []
        for index, source in enumerate(settings["sources"], 1):
            pcm = decoded[source]
            peak = max(map(abs, pcm))
            if peak == 0:
                raise ValueError(f"Silent source recording: {source}")
            if event == "saber.hum":
                pcm = pcm[:RATE * 2]
            else:
                active = [index for index, value in enumerate(pcm) if abs(value) > peak * 0.003]
                pcm = pcm[max(0, active[0] - RATE // 200):min(len(pcm), active[-1] + RATE // 200 + 1)]
            gain = settings["peak"] / max(map(abs, pcm))
            pcm = array.array("f", (value * gain for value in pcm))
            fade = min(RATE // 500, len(pcm) // 2)
            for sample in range(fade):
                pcm[sample] *= sample / fade
                pcm[-sample - 1] *= sample / fade
            if sys.byteorder != "little":
                pcm.byteswap()
            name = event.replace(".", "_") + (f"_{index}" if len(settings["sources"]) > 1 else "")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-",
                            "-c:a", "libvorbis", "-q:a", "4", "-map_metadata", "-1", "-fflags", "+bitexact",
                            "-flags:a", "+bitexact", str(output / f"{name}.ogg")], input=pcm.tobytes(), check=True)
            names.append(f"blockcraft:sternenfront/{name}")
            print(f"{name}: {len(pcm) / RATE:.3f}s from {source}")
        events[f"sternenfront.{event}"] = {"sounds": names}
    return events


if __name__ == "__main__":
    root = Path(__file__).parent / "src/assets/blockcraft"
    definitions = root / "sounds.json"
    entries = json.loads(definitions.read_text())
    entries.update(generate(root))
    definitions.write_bytes((json.dumps(entries, indent=2) + "\n").encode())

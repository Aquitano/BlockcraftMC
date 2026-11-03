"""Imports the approved blaster recordings and credited music without network access."""
import hashlib
import json
import subprocess
from pathlib import Path

SOURCES = Path(__file__).parent / "audio/battlefront"


def generate(root):
    manifest = json.loads((SOURCES / "sources.json").read_text())
    for name, source in manifest["sources"].items():
        path = SOURCES / name
        if hashlib.sha256(path.read_bytes()).hexdigest() != source["sha256"]:
            raise ValueError(f"Source recording checksum mismatch: {path}")
    events = {}
    output = root / "sounds/sternenfront"
    output.mkdir(parents=True, exist_ok=True)
    for event, settings in manifest["events"].items():
        music = event.startswith("music.")
        duration = settings["seconds"]
        filters = [f"atrim=start={settings.get('start', 0)}:duration={duration}", "asetpts=PTS-STARTPTS"]
        if not music:
            filters.extend([f"asetrate={round(44100 * settings.get('pitch', 1))}", "aresample=44100"])
            duration /= settings.get("pitch", 1)
        filters.extend([f"volume={settings.get('gain_db', -3)}dB", f"afade=t=in:d={1 if music else 0.002}",
                        f"afade=t=out:st={duration - (2 if music else 0.08)}:d={2 if music else 0.08}"])
        name = event.replace(".", "_")
        source = manifest["sources"][settings["source"]]
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(SOURCES / settings["source"]), "-ac", "2" if music else "1",
                        "-ar", "44100", "-af", ",".join(filters), "-c:a", "libvorbis", "-q:a", "4",
                        "-map_metadata", "-1", "-metadata", f"artist={source['author']}",
                        "-metadata", f"comment={source['license']}; {source['source']}; edited excerpt with fades",
                        "-fflags", "+bitexact", "-flags:a", "+bitexact", str(output / f"{name}.ogg")], check=True)
        entry = {"name": f"blockcraft:sternenfront/{name}"}
        if music:
            entry["stream"] = True
        events[f"sternenfront.{event}"] = {"sounds": [entry]}
        print(f"{name}: {duration:.2f}s")
    return events


if __name__ == "__main__":
    root = Path(__file__).parent / "src/assets/blockcraft"
    definitions = root / "sounds.json"
    entries = json.loads(definitions.read_text())
    entries.update(generate(root))
    definitions.write_bytes((json.dumps(entries, indent=2) + "\n").encode())

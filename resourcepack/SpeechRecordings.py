"""Render checked-in faction speech; synthesis uses only an explicitly supplied local model."""
import argparse
import hashlib
import json
import math
import subprocess
import wave
from pathlib import Path

import yaml

ROOT = Path(__file__).parent / "src/assets/blockcraft"
MESSAGES = Path(__file__).parent.parent / "paper/sternenfront/src/main/resources/messages.yml"


def message(messages, key):
    for part in key.split("."):
        messages = messages[part]
    return messages


def generate(sources, group, root, budget, infantry=False):
    manifest = json.loads((sources / "sources.json").read_text())
    messages = yaml.safe_load(MESSAGES.read_text())
    output = root / "sounds/sternenfront"
    output.mkdir(parents=True, exist_ok=True)
    events = {}
    for name, source in manifest[group].items():
        path = sources / f"{name}.wav"
        if hashlib.sha256(path.read_bytes()).hexdigest() != source["sha256"]:
            raise ValueError(f"Speech source checksum mismatch: {path}")
        text = message(messages, source["message"])
        if hashlib.sha256(text.encode()).hexdigest() != source["text_sha256"]:
            raise ValueError(f"Speech text changed; synthesize a new recording: {source['message']}")
        with wave.open(str(path), "rb") as speech:
            duration = speech.getnframes() / speech.getframerate()
        if duration > budget or math.ceil(duration * 20) != source["duration_ticks"]:
            raise ValueError(f"Speech exceeds its timing budget or manifest duration: {path}")
        filters = ["highpass=f=300", "lowpass=f=3800"]
        if source["faction"] == "hegemonie":
            filters.extend(["asetrate=24475", "aresample=22050", "tremolo=f=70:d=0.35"])
        filters.extend(["acompressor=threshold=0.12:ratio=2.2:attack=4:release=60:makeup=1",
                        f"loudnorm=I={'-20' if infantry else '-18'}:TP=-3:LRA=7", "afade=t=in:d=0.012"])
        sound = source["sound"]
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(path), "-af", ",".join(filters),
                        "-ar", "22050", "-ac", "1", "-c:a", "libvorbis", "-q:a", "3",
                        "-map_metadata", "-1", "-metadata", "artist=BlockcraftMC; Thorsten-Voice (synthetic voice)",
                        "-metadata", "comment=Original German text; CC0 Thorsten voice; faction radio delivery",
                        "-fflags", "+bitexact", "-flags:a", "+bitexact", str(output / f"{sound}.ogg")], check=True)
        entry = {"name": f"blockcraft:sternenfront/{sound}"}
        if infantry:
            entry["attenuation_distance"] = 24
        events.setdefault(source["event"], {"sounds": []})["sounds"].append(entry)
        print(f"{sound}: {duration:.2f}s")
    return events


def synthesize(sources, group, model):
    from piper import PiperVoice, SynthesisConfig

    manifest = json.loads((sources / "sources.json").read_text())
    settings = manifest["voice"]
    voice = PiperVoice.load(str(model))
    messages = yaml.safe_load(MESSAGES.read_text())
    for name, source in manifest[group].items():
        delivery = settings["delivery"][source["faction"]]
        config = SynthesisConfig(length_scale=delivery["length_scale"], noise_scale=delivery["noise_scale"],
                                 noise_w_scale=delivery["noise_w_scale"])
        text = message(messages, source["message"])
        path = sources / f"{name}.wav"
        with wave.open(str(path), "wb") as speech:
            voice.synthesize_wav(text, speech, syn_config=config)
        with wave.open(str(path), "rb") as speech:
            source["duration_ticks"] = math.ceil(speech.getnframes() / speech.getframerate() * 20)
        source["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        source["text_sha256"] = hashlib.sha256(text.encode()).hexdigest()
    settings["model_sha256"] = hashlib.sha256(model.read_bytes()).hexdigest()
    settings["config_sha256"] = hashlib.sha256(Path(str(model) + ".json").read_bytes()).hexdigest()
    (sources / "sources.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")


def main(sources, group, budget, infantry=False):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path)
    args = parser.parse_args()
    if args.model:
        synthesize(sources, group, args.model)
    definitions = ROOT / "sounds.json"
    entries = json.loads(definitions.read_text())
    entries.update(generate(sources, group, ROOT, budget, infantry))
    definitions.write_text(json.dumps(entries, indent=2) + "\n")

# /// script
# requires-python = ">=3.11"
# dependencies = ["piper-tts==1.4.2", "pyyaml>=6"]
# ///
"""Imports the original German radio briefings without downloading a voice model.

To replace the checked-in source speech, pass --model /path/to/de_DE-thorsten-high.onnx.
The spoken text comes from Sternenfront's messages.yml. Piper and its model are build tools only.
"""
import argparse
import hashlib
import json
import subprocess
import wave
from pathlib import Path

SOURCES = Path(__file__).parent / "audio/intro"
MESSAGES = Path(__file__).parent.parent / "paper/sternenfront/src/main/resources/messages.yml"


def generate(root):
    manifest = json.loads((SOURCES / "sources.json").read_text())
    output = root / "sounds/sternenfront"
    output.mkdir(parents=True, exist_ok=True)
    events = {}
    for faction, source in manifest["briefings"].items():
        path = SOURCES / f"{faction}.wav"
        if hashlib.sha256(path.read_bytes()).hexdigest() != source["sha256"]:
            raise ValueError(f"Briefing source checksum mismatch: {path}")
        with wave.open(str(path), "rb") as speech:
            duration = speech.getnframes() / speech.getframerate()
        if duration > 4:
            raise ValueError(f"Briefing exceeds the four-second intro budget: {path}")
        name = f"intro_briefing_{faction}"
        filters = ["highpass=f=220", "lowpass=f=4400",
                   "acompressor=threshold=0.12:ratio=2.2:attack=4:release=60:makeup=1",
                   "loudnorm=I=-18:TP=-3:LRA=7", "afade=t=in:d=0.012",
                   f"afade=t=out:st={max(0, duration - 0.04)}:d=0.04"]
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(path), "-af", ",".join(filters),
                        "-ar", "22050", "-ac", "1", "-c:a", "libvorbis", "-q:a", "3",
                        "-map_metadata", "-1", "-metadata", "artist=BlockcraftMC; Thorsten-Voice (synthetic voice)",
                        "-metadata", "comment=Original German text; CC0 Thorsten voice; radio filtered",
                        "-fflags", "+bitexact", "-flags:a", "+bitexact", str(output / f"{name}.ogg")], check=True)
        events[f"sternenfront.intro.briefing.{faction}"] = {"sounds": [f"blockcraft:sternenfront/{name}"]}
        print(f"{name}: {duration:.2f}s")
    return events


def synthesize(model):
    import yaml
    from piper import PiperVoice, SynthesisConfig

    manifest = json.loads((SOURCES / "sources.json").read_text())
    settings = manifest["voice"]
    voice = PiperVoice.load(str(model))
    config = SynthesisConfig(length_scale=settings["length_scale"], noise_scale=settings["noise_scale"],
                             noise_w_scale=settings["noise_w_scale"])
    messages = yaml.safe_load(MESSAGES.read_text())
    for faction, source in manifest["briefings"].items():
        text = messages["intro"]["briefing"][faction]
        path = SOURCES / f"{faction}.wav"
        with wave.open(str(path), "wb") as speech:
            voice.synthesize_wav(text, speech, syn_config=config)
        source["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        source["text_sha256"] = hashlib.sha256(text.encode()).hexdigest()
    manifest["voice"]["model_sha256"] = hashlib.sha256(model.read_bytes()).hexdigest()
    manifest["voice"]["config_sha256"] = hashlib.sha256(Path(str(model) + ".json").read_bytes()).hexdigest()
    (SOURCES / "sources.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path)
    args = parser.parse_args()
    if args.model:
        synthesize(args.model)
    root = Path(__file__).parent / "src/assets/blockcraft"
    definitions = root / "sounds.json"
    entries = json.loads(definitions.read_text())
    entries.update(generate(root))
    definitions.write_bytes((json.dumps(entries, indent=2) + "\n").encode())

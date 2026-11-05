# /// script
# requires-python = ">=3.11"
# dependencies = ["piper-tts==1.4.2", "pyyaml>=6"]
# ///
"""Generate original infantry calls from checked-in WAV speech, or synthesize it with a local Piper model."""
import argparse
import hashlib
import json
import subprocess
import wave
from pathlib import Path

SOURCES = Path(__file__).parent / "audio/bots"
MESSAGES = Path(__file__).parent.parent / "paper/sternenfront/src/main/resources/messages.yml"


def generate(root):
    manifest = json.loads((SOURCES / "sources.json").read_text())
    output = root / "sounds/sternenfront"
    output.mkdir(parents=True, exist_ok=True)
    events = {}
    for cue, source in manifest["calls"].items():
        path = SOURCES / f"{cue}.wav"
        if hashlib.sha256(path.read_bytes()).hexdigest() != source["sha256"]:
            raise ValueError(f"Infantry call checksum mismatch: {path}")
        with wave.open(str(path), "rb") as speech:
            duration = speech.getnframes() / speech.getframerate()
        if duration > 2.5:
            raise ValueError(f"Infantry call exceeds 2.5 seconds: {path}")
        for faction in ("kolonien", "hegemonie"):
            name = f"bot_{faction}_{cue}"
            filters = ["highpass=f=300", "lowpass=f=3800"]
            if faction == "hegemonie":
                filters.extend(["asetrate=24475", "aresample=22050", "tremolo=f=70:d=0.35"])
            filters.extend(["acompressor=threshold=0.12:ratio=2.2:attack=4:release=60:makeup=1",
                            "loudnorm=I=-20:TP=-3:LRA=7", "afade=t=in:d=0.012"])
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(path), "-af", ",".join(filters),
                            "-ar", "22050", "-ac", "1", "-c:a", "libvorbis", "-q:a", "3",
                            "-map_metadata", "-1", "-metadata", "artist=BlockcraftMC; Thorsten-Voice (synthetic voice)",
                            "-metadata", "comment=Original German infantry call; CC0 Thorsten voice",
                            "-fflags", "+bitexact", "-flags:a", "+bitexact", str(output / f"{name}.ogg")], check=True)
            events[f"sternenfront.bot.{faction}.{cue}"] = {
                "sounds": [{"name": f"blockcraft:sternenfront/{name}", "attenuation_distance": 24}]
            }
        print(f"{cue}: {duration:.2f}s")
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
    for cue, source in manifest["calls"].items():
        text = messages["bot"]["voice"][cue]
        path = SOURCES / f"{cue}.wav"
        with wave.open(str(path), "wb") as speech:
            voice.synthesize_wav(text, speech, syn_config=config)
        source["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        source["text_sha256"] = hashlib.sha256(text.encode()).hexdigest()
    settings["model_sha256"] = hashlib.sha256(model.read_bytes()).hexdigest()
    settings["config_sha256"] = hashlib.sha256(Path(str(model) + ".json").read_bytes()).hexdigest()
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
    definitions.write_text(json.dumps(entries, indent=2) + "\n")

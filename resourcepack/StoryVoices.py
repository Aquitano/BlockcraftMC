# /// script
# requires-python = ">=3.11"
# dependencies = ["piper-tts==1.4.2", "pyyaml>=6"]
# ///
"""Builds the story's spoken lines into sounds/story/voice, one sound event per line.

Every line has a key: `vela.<option>` for an NPC option of the conversation `vela` in the BetonQuest package, and
`cutscene.<scene>.<n>` for a cutscene line in the Story plugin's messages.yml. A recording dropped in as
audio/story/recorded/<key>.<wav|ogg|flac|mp3> takes the place of the placeholder in audio/story/placeholder/<key>.wav.
The sound event is `story.voice.<key>` in lower case, as resource names must be, and paper/story's voices.properties gets each line's length in ticks, which the
subtitles type out at.

    uv run StoryVoices.py                        # build what is there, list what is missing or out of date
    uv run StoryVoices.py --model thorsten.onnx  # first synthesize placeholders for missing or changed lines
"""
import argparse
import hashlib
import json
import math
import subprocess
import tempfile
import wave
from pathlib import Path

import yaml

HERE = Path(__file__).parent
ROOT = HERE / "src/assets/blockcraft"
STORY = HERE.parent / "paper/story"
SOURCES = HERE / "audio/story"
RECORDED = SOURCES / "recorded"
PLACEHOLDER = SOURCES / "placeholder"
MANIFEST = SOURCES / "sources.json"
OUT = ROOT / "sounds/story/voice"
LENGTHS = STORY / "src/main/resources/voices.properties"
EVENT = "story.voice."
FORMATS = ("wav", "ogg", "flac", "mp3")
# Thorsten's delivery for each speaker; a radio speaker also goes through the faction radio's filter.
DELIVERY = {
    "vela": {"length_scale": 1.0, "noise_scale": 0.45, "noise_w_scale": 0.7, "radio": False},
    "funk": {"length_scale": 0.92, "noise_scale": 0.4, "noise_w_scale": 0.6, "radio": True},
}


def lines():
    """Every spoken line as {key: (speaker, text)}."""
    found = {}
    for path in sorted((STORY / "betonquest/QuestPackages").rglob("*.yml")):
        for name, conversation in (yaml.safe_load(path.read_text()) or {}).get("conversations", {}).items():
            for option, fields in conversation.get("NPC_options", {}).items():
                found[f"{name}.{option}"] = (name, fields["text"])
    messages = yaml.safe_load((STORY / "src/main/resources/messages.yml").read_text())["cutscene"]
    config = yaml.safe_load((STORY / "src/main/resources/config.yml").read_text())
    for scene, settings in config["cutscenes"]["scenes"].items():
        for line in settings.get("lines", []):
            *_, number = line["text"].split(".")
            found[line["text"]] = (line["speaker"], messages[scene][number])
    return found


def digest(text):
    return hashlib.sha256(text.encode()).hexdigest()


def recording(key):
    return next((path for ext in FORMATS if (path := RECORDED / f"{key}.{ext}").exists()), None)


def synthesize(model, wanted, manifest):
    from piper import PiperVoice, SynthesisConfig

    voice = PiperVoice.load(str(model))
    PLACEHOLDER.mkdir(parents=True, exist_ok=True)
    for key, (speaker, text) in wanted.items():
        known = manifest.get(key, {})
        path = PLACEHOLDER / f"{key}.wav"
        if recording(key) or path.exists() and known.get("text_sha256") == digest(text):
            continue
        delivery = DELIVERY[speaker]
        config = SynthesisConfig(length_scale=delivery["length_scale"], noise_scale=delivery["noise_scale"],
                                 noise_w_scale=delivery["noise_w_scale"])
        with wave.open(str(path), "wb") as speech:
            voice.synthesize_wav(text, speech, syn_config=config)
        manifest[key] = {"speaker": speaker, "text_sha256": digest(text), "model": model.name,
                         "model_sha256": hashlib.sha256(model.read_bytes()).hexdigest()}
        print(f"synthesized {key}")


def render(source, target, speaker, directory):
    radio = json.loads((HERE / "audio/radio-filter.json").read_text())
    filters = ["silenceremove=start_periods=1:start_threshold=-50dB"]
    if DELIVERY[speaker]["radio"]:
        filters.extend(radio["voice"])
    filters.extend(["loudnorm=I=-18:TP=-3:LRA=7", "aresample=22050"])
    graph = f"[0:a]{','.join(filters)}[voice]"
    if DELIVERY[speaker]["radio"]:
        graph += f";{','.join(radio['static'])}[static];[voice][static]amix=inputs=2:duration=first:normalize=0[out]"
    else:
        graph += ";[voice]anull[out]"
    flat = Path(directory) / "voice.wav"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(source), "-filter_complex", graph, "-map", "[out]",
                    "-ar", "22050", "-ac", "1", str(flat)], check=True)
    with wave.open(str(flat), "rb") as speech:
        seconds = speech.getnframes() / speech.getframerate()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(flat), "-af", f"afade=t=out:st={max(0, seconds - 0.03):.3f}:d=0.03",
                    "-c:a", "libvorbis", "-q:a", "3", "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
                    str(target)], check=True)
    return math.ceil(seconds * 20)


def build(wanted, manifest):
    OUT.mkdir(parents=True, exist_ok=True)
    for stale in OUT.glob("*.ogg"):
        if stale.stem not in {key.lower() for key in wanted}:
            stale.unlink()
    events, lengths = {}, {}
    with tempfile.TemporaryDirectory() as directory:
        for key, (speaker, text) in wanted.items():
            recorded = recording(key)
            source = recorded or PLACEHOLDER / f"{key}.wav"
            if not source.exists():
                print(f"missing  {key}: {text}")
                continue
            if not recorded and manifest.get(key, {}).get("text_sha256") != digest(text):
                print(f"outdated {key}: the text changed since its placeholder; run with --model")
            lengths[key] = render(source, OUT / f"{key.lower()}.ogg", speaker, directory)
            events[EVENT + key.lower()] = {"sounds": [{"name": f"blockcraft:story/voice/{key.lower()}", "stream": True}]}
            print(f"{'recorded' if recorded else 'placeholder'} {key}: {lengths[key]} ticks")
    path = ROOT / "sounds.json"
    entries = {key: value for key, value in json.loads(path.read_text()).items() if not key.startswith(EVENT)}
    entries.update(events)
    path.write_text(json.dumps(entries, indent=2) + "\n")
    LENGTHS.write_text("# Written by resourcepack/StoryVoices.py: each spoken line's recording, in ticks.\n"
                       + "".join(f"{key}={ticks}\n" for key, ticks in sorted(lengths.items())))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model", type=Path, help="a local Piper voice, de_DE-thorsten-high.onnx with its .json beside it")
    args = parser.parse_args()
    wanted = lines()
    manifest = json.loads(MANIFEST.read_text()) if MANIFEST.exists() else {}
    if args.model:
        synthesize(args.model, wanted, manifest)
        manifest = {key: value for key, value in manifest.items() if key in wanted}
        MANIFEST.parent.mkdir(parents=True, exist_ok=True)
        MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
    build(wanted, manifest)


if __name__ == "__main__":
    main()

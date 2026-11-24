# /// script
# requires-python = ">=3.11"
# dependencies = ["piper-tts==1.4.2", "pyyaml>=6"]
# ///
"""Regenerate the German intro from checked-in WAVs, or use --model for local Piper synthesis."""
from pathlib import Path

import SpeechRecordings

SOURCES = Path(__file__).parent / "audio/intro"


def generate(root):
    return SpeechRecordings.generate(SOURCES, "briefings", root, 4)


if __name__ == "__main__":
    SpeechRecordings.main(SOURCES, "briefings", 4)

# Battle audio

Attribution and licences are in [CREDITS.md](../CREDITS.md), which also ships inside the pack as `credits/CREDITS.md`.

The original source downloads and their SHA-256 checksums are in `audio/battlefront/sources.json`.
Blasters are shortened, pitch-adjusted by weapon, reduced by 4 dB, faded and converted to mono Vorbis.
Music is excerpted, reduced by 6 dB, faded and converted to stereo Vorbis. Its events set `stream: true`.

`uv run --no-project BattleRecordings.py` regenerates these eleven events from checked-in sources with FFmpeg.
`uv run SternenfrontSounds.py` regenerates all Sternenfront effects, including the imported recordings.
Neither command needs a network connection once its Python dependencies are installed.

Music uses Minecraft's Music category, including the cantina tune. The normal Music slider changes its volume
immediately. A value of zero mutes it independently of blasters and other combat effects in the Players category.

Infantry calls use the Voice category and play from the bot's position. `bots.voice` controls range, volume, and
cooldowns. Defaults allow one call every five seconds across all bots, with eighteen seconds between calls from one bot.
The original German lines live in `bot.voice` in Sternenfront's `messages.yml`. They cover contact, cover, advance, and healing.
`audio/bots/sources.json` records the local Thorsten-Voice synthesis and source hashes. Republic calls use a radio filter;
Separatist calls add a pitch shift and tremolo. Each checked-in source lasts less than a second.

`python3 resourcepack/BotVoices.py` regenerates all eight OGG files from the checked-in WAV sources with FFmpeg.
To change the speech, run `uv run --no-project resourcepack/BotVoices.py --model /path/to/de_DE-thorsten-high.onnx`.
The script uses a local model and never downloads one. The engine and model do not ship in the resource pack.

`uv run --no-project BattleArt.py` regenerates the projector, original faction emblems, shield grids and firing poses.
The six firing variants are registered in `PackModel`, so the normal `:paper:api:packOverrides` generator preserves them.
The generator disables the [client's hand-swap animation](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4)
on the six blaster carrier materials, so each shot does not lower and raise the weapon. Minecraft applies this flag
to the whole item definition, so vanilla hoes and other pack models on those materials also swap without that animation.
Their models and gameplay properties are unchanged.

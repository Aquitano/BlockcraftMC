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

`uv run --no-project BattleArt.py` regenerates the projector, original faction emblems, shield grids and firing poses.
The six firing variants are registered in `PackModel`, so the normal `:paper:api:packOverrides` generator preserves them.
The generator disables the [client's hand-swap animation](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4)
on the six blaster carrier materials, so each shot does not lower and raise the weapon. Minecraft applies this flag
to the whole item definition, so vanilla hoes and other pack models on those materials also swap without that animation.
Their models and gameplay properties are unchanged.

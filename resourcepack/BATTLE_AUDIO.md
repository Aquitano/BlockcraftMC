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
Each faction has eight original recordings, two variants for each of contact, cover, advance, and support.
The existing event names choose between variants. The German lines live under `bot.voice.kolonien` and
`bot.voice.hegemonie` in Sternenfront's `messages.yml`. The persisted faction keys remain unchanged.
Clone lines address fellow troops; droid support lines describe repairs.

The intro briefings name the Republic and Separatists and are enabled by default. Both source recordings fit the
48-tick speech window. The radio click closes after that window. Short intros omit speech when it would be cut off.
Captions use the recorded line from `intro.briefing`, and the scene scope stops audio on skip, disconnect, or cleanup.
An existing operator override of `intro.audio.briefing: false` still disables the briefing.

Objective radio announces the assault's priority post and the last stand. Post capture and loss reports have a shared
20-second cooldown and are dropped during speech or that cooldown. Act instructions replace pending post reports and
wait for any current infantry or radio line to finish. New infantry calls wait until radio speech ends.
`radio` in `config.yml` controls the events, speech, captions, volume, and timing. The timing floors cover the recordings.
Captions identify the post by letter and name, followed by the recorded German line from `radio` in `messages.yml`.
Only players in the round with the pack applied receive objective calls. Scenes and dialogues exclude their viewers.
Leaving the audience stops that player's owned Voice sounds. Ending the round also clears pending calls.

`audio/intro/sources.json`, `audio/bots/sources.json`, and `audio/radio/sources.json` record the local Thorsten-Voice
synthesis settings, recording durations, source hashes, and message hashes. Each faction has its own WAVs and text.
Republic delivery uses radio filtering; Separatist delivery uses steadier synthesis, pitch shift, and tremolo.
These synthetic lines are original project text. They do not use film or game performers' recordings.

From the repository root, these commands regenerate only the 26 speech OGG files and their sound events:

```sh
python3 resourcepack/IntroBriefings.py
python3 resourcepack/BotVoices.py
python3 resourcepack/RadioCalls.py
```

They need Python 3, PyYAML, and FFmpeg, and work offline from the checked-in WAVs. They reject a changed message hash
until its recording is regenerated. Vorbis encoding is deterministic with the same FFmpeg version.
To replace speech, append `--model /path/to/de_DE-thorsten-high.onnx` using a Python environment with `piper-tts==1.4.2`,
or run `uv run --no-project resourcepack/RadioCalls.py --model /path/to/de_DE-thorsten-high.onnx` for the objective calls.
The scripts require the adjacent model JSON and never download a model. Piper and the model do not ship in the pack.
Changing a line requires regenerating its source manifest and checking the configured speech window.

From the repository root, `./gradlew :resourcepack:generateBattle` regenerates the projector, original faction emblems,
shield grids and firing poses. It generates the base models and firing manifest before running `BattleArt.py`.
The eleven firing variants are registered in `PackModel`, so the normal `:paper:api:packOverrides` generator preserves them.
The generator disables the [client's hand-swap animation](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4)
on the six blaster carrier materials, so each shot does not lower and raise the weapon. Minecraft applies this flag
to the whole item definition, so vanilla hoes and other pack models on those materials also swap without that animation.
Their models and gameplay properties are unchanged.

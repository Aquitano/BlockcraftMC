# Lightsaber recordings

The saber effects use audio from [Star Wars by joe93barlow on Freesound](https://freesound.org/people/joe93barlow/packs/5968/),
published under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).
These are community recordings, not an official Lucasfilm sound library.

The downloaded high-quality MP3 previews are kept in `audio/sternenfront`. `sources.json` records each source page,
download URL, SHA-256 checksum, and its assigned sound events. The conversion changes channel count and volume,
trims silence, and adds short edge fades. The hum lasts two seconds to match its playback interval.

| Events | Source files |
| --- | --- |
| Swings | `swing2.mp3`, `swing4.mp3`, `swing7.mp3` |
| Impacts | `hit2.mp3`, `hit3.mp3`, `hit4.mp3` |
| Blade clash and deflection | `strike0.mp3`, `strike1.mp3` |
| Ignition, retraction and hum | `on0.mp3`, `off0.mp3`, `idle1.mp3` |
| Saber throw | `swing2.mp3` |

Run `uv run --no-project SaberRecordings.py` from `resourcepack` with FFmpeg installed to rebuild only the saber
effects. `generateSternenfrontSounds` also imports these recordings when rebuilding the game's other audio.
Neither operation downloads audio. Normal pack builds use the checked-in Ogg files.

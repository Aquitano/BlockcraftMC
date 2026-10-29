# BlockcraftReplay

BlockcraftReplay records rounds from the shared `Round` lifecycle and plays them in disposable worlds on a dedicated `replay` backend. It targets Paper 26.3 and Java 25.

## Recording scope

A recording contains participant positions, poses, visible equipment, nearby terrain, and supported Bukkit block-change events. Player actors use Paper's `Mannequin` API. Actor samples default to every two ticks.

Terrain capture covers loaded chunks near participants, up to the configured limit. A chunk's baseline reflects its state when captured. Terrain outside the captured area is unavailable during playback.

This version does not record projectiles, mobs, chat, sounds, particles, player camera input, private inventories, or block-entity contents. Direct block changes made by other plugins without Bukkit events are not captured. Eliminated players disappear from the actor roster. These limits make the recording incomplete for games whose main action depends on mobs or custom block changes.

Vanished players are excluded. Names and skins reflect the visible player profile. Equipment retains visual components, while custom data, container contents, book text, names, and lore are omitted.

## Access and controls

`/replay` opens the most recent recordings. Normal players can view rounds they participated in. `blockcraft.replay.staff` permits viewing all recorded rounds and defaults to operators. `blockcraft.replay.use` defaults to everyone.

Recordings enter the history after the round ends. A recording that reaches a duration, event, or terrain limit is marked as partial and stays private until that round ends. An overloaded writer queue aborts the recording instead of blocking the game thread.

| Command | Action |
| --- | --- |
| `/replay` | Open the history. |
| `/replay play <id>` | Open an accessible recording on the replay backend. |
| `/replay pause` | Toggle pause. |
| `/replay speed <speed>` | Select `0.5`, `1`, or `2`. |
| `/replay seek <seconds>` | Seek to an absolute time. |
| `/replay stop` | Cancel loading or leave playback. |

The viewer hotbar also provides pause, ten-second jumps, speed selection, and exit. An actionbar shows the position, duration, speed, and preparation state. Viewers use Adventure mode with flight and cannot interact with the recording. Leaving playback restores their inventory, cursor item, location, and movement settings.

## Deployment layout

The root Compose configuration adds BlockcraftReplay to the Paper backends and a separate `replay` service. Each backend mounts the shared `replays` volume at `/replays`. The `replay-storage` initializer assigns the volume to the image's default user and group, both `1000`, before Paper starts. `BLOCKCRAFT_REPLAY_DIRECTORY` overrides the local storage directory.

Playback must run without game plugins. Game listeners are not isolated by world, so playback on an active game backend could trigger game behavior. The dedicated service includes LuckPerms, BlockcraftAPI, BlockcraftCore, BlockcraftReplay, and ViaVersion.

All backends use the shared database for history, participant access, and short-lived viewing requests. Recording files stay on the shared volume. The plugin creates `bc_replays`, `bc_replay_players`, and `bc_replay_requests` at startup. The replay backend must remain running for retention and orphan recovery.

The build task is `./gradlew :paper:replay:build`. The network's `stagePlugins` task includes the replay jar automatically. This module has no BetterReplay or PacketEvents dependency.

## Resource limits

The [default configuration](src/main/resources/config.yml) sets these bounds.

| Resource | Default |
| --- | --- |
| Duration | 30 minutes |
| Participants | 32 |
| Captured chunks | 128 |
| Chunk snapshots | One per tick |
| Pending writer events | 32 |
| Compressed recording | 64 MiB |
| Decoded recording budget | 128 MiB |
| Simultaneous viewers per backend | Two |
| Playback block writes | 4,096 per tick across viewers |
| Playback block scans | 65,536 per tick across viewers |
| Storage admission limit | 8 GiB |
| Required free disk space | 512 MiB |
| Retention | Seven days, checked every 15 minutes |

Snapshots are captured on the server thread. Palette encoding, compression, file writes, archive reads, and history queries run off that thread. Playback pauses its clock while terrain work remains. Rewind rebuilds terrain under the same budgets and can take time on larger maps.

Storage admission reserves space for a recording's maximum size and counts incomplete files. Independent backends can race during admission, so the shared storage limit is not a transactional disk quota. Retention removes old completed files and incomplete files older than the configured duration plus one hour. Generated playback worlds are unloaded and deleted after their viewers leave.

These bounds limit retained work and data. They do not guarantee a tick-time target on every map or machine.

## Archive compatibility

Recordings use a versioned, compressed binary format with bounded decoding and an atomic final file rename. Unchanged equipment uses references to previous frames. Publication markers allow the replay backend to recover history entries after a database failure. Recovery runs on its own worker and decodes at most four unindexed archives per pass. A crash before the publication marker is saved can leave a recording unavailable until retention removes it.

Playback requires the recording's Minecraft version to match the backend exactly. This protects block-state and item deserialization across server upgrades. No archive migration is included. Client-version admission remains controlled by the existing proxy and ViaVersion configuration.

[BetterReplay](https://github.com/JustinDevB/BetterReplay) was inspected at commit `7d7d8a7ae3686bf3223262e737fbc23e231e3996` for design comparison. This implementation does not copy its GPL-licensed code.

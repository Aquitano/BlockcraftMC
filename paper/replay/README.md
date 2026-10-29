# BlockcraftReplay

BlockcraftReplay records rounds from the shared `Round` lifecycle and plays them in disposable worlds on a dedicated `replay` backend. It targets Paper 26.3 and Java 25.

## Recording scope

A recording contains participant positions, poses, visible equipment, nearby terrain, and supported Bukkit block-change events. It also contains the mobs, projectiles, and primed TNT in the captured chunks, along with swings, hits, deaths, and explosions. Player actors use Paper's `Mannequin` API. Samples default to every two ticks.

Each entity's data is stored once, as a Paper entity snapshot, when the recorder first sees it. That snapshot carries its type, variant, equipment, and name. Later samples store only its position and rotation. An entity snapshot over 16 KiB keeps only the entity type. Entities may use half of a recording's object budget, and a sample holds at most `max-entities` of them. A round that spends the entity budget records players and terrain only from then on. When a whole recording reaches its object or decoded-size budget, the writer ends the archive at the first event that does not fit and keeps it as partial.

Terrain capture covers loaded chunks near participants, up to the configured limit. A chunk's baseline reflects its state when captured. Terrain outside the captured area is unavailable during playback.

This version does not record dropped items, experience orbs, falling blocks, fishing hooks, riding passengers, chat, sounds, particles, player camera input, private inventories, or block-entity contents. A mob's appearance does not follow equipment changes after it was first seen. Direct block changes made by other plugins without Bukkit events are not captured. Eliminated players disappear from the actor roster. These limits make the recording incomplete for games whose main action depends on custom block changes.

Vanished players and their projectiles are excluded. Names and skins reflect the visible player profile. Equipment retains visual components, while custom data, container contents, book text, names, and lore are omitted.

## Access and controls

`/replay` opens the most recent recordings. Normal players can view rounds they participated in. `blockcraft.replay.staff` permits viewing all recorded rounds and defaults to operators. `blockcraft.replay.use` defaults to everyone.

Recordings enter the history after the round ends. A recording that reaches a duration, event, terrain, or object limit is marked as partial and stays private until that round ends. An overloaded writer queue aborts the recording instead of blocking the game thread.

| Command | Action |
| --- | --- |
| `/replay` | Open the history. |
| `/replay play <id>` | Open an accessible recording on the replay backend. |
| `/replay pause` | Toggle pause. |
| `/replay speed <speed>` | Select `0.5`, `1`, or `2`. |
| `/replay seek <seconds>` | Seek to an absolute time. |
| `/replay stop` | Cancel loading or leave playback. |

The viewer hotbar provides a participant picker, ten-second jumps around play and pause, speed selection, and exit. Its labels follow the playback state; play at the end of a recording starts it over. A boss bar shows the position, duration, speed, and loading state. The picker lists the participants on screen and moves the viewer behind the chosen one. Viewers start behind their own figure if they took part. They use Adventure mode with flight inside a world border around the captured chunks and cannot interact with the recording.

Leaving playback sends the viewer back to the server group they opened the recording from if that group can take them right now. Otherwise, or when the group is unknown, it sends them to the lobby. The recording stays on screen until the proxy moves them. If they are still on the replay backend after five seconds, playback ends there and restores their inventory, cursor item, location, and movement settings.

## Deployment layout

The root Compose configuration adds BlockcraftReplay to the Paper backends and a separate `replay` service. Each backend mounts the shared `replays` volume at `/replays`. The `replay-storage` initializer assigns the volume to the image's default user and group, both `1000`, before Paper starts. `BLOCKCRAFT_REPLAY_DIRECTORY` overrides the local storage directory.

Playback must run without game plugins. Game listeners are not isolated by world, so playback on an active game backend could trigger game behavior. The dedicated service includes LuckPerms, BlockcraftAPI, BlockcraftCore, BlockcraftReplay, and ViaVersion.

All backends use the shared database for history, participant access, and short-lived viewing requests. A request also stores the group the viewer came from. Recording files stay on the shared volume. The plugin creates `bc_replays`, `bc_replay_players`, and `bc_replay_requests` at startup. The replay backend must remain running for retention and orphan recovery.

The build task is `./gradlew :paper:replay:build`. The network's `stagePlugins` task includes the replay jar automatically. This module has no BetterReplay or PacketEvents dependency.

## Resource limits

The [default configuration](src/main/resources/config.yml) sets these bounds.

| Resource | Default |
| --- | --- |
| Duration | 30 minutes |
| Participants | 32 |
| Entities per sample | 64 |
| Captured chunks | 128 |
| Chunk snapshots | One per tick |
| Pending writer events | 32 |
| Compressed recording | 64 MiB |
| Decoded recording budget | 128 MiB |
| Simultaneous viewers per backend | Two |
| Playback block writes | 4,096 per tick across viewers |
| Storage admission limit | 8 GiB |
| Required free disk space | 512 MiB |
| Retention | Seven days, checked every 15 minutes |

Snapshots are captured on the server thread. Palette encoding, compression, file writes, archive reads, block-state parsing, and history queries run off that thread.

Recorded entities return as copies of their snapshots with no AI, gravity, owner, or fuse. The playback moves them to the recorded positions. Projectiles and TNT also receive the recorded motion as velocity, because clients predict their flight between server updates. Swings, hurt flashes, and deaths play while the recording runs, and so do explosion particles and sounds. A seek skips them. Undead do not burn in the replay world's daylight, and projectiles hit nothing.

Each playback world uses a chunk generator that builds the captured chunks from the recording. A chunk captured later in the round shows its captured state from the start. The server generates the chunks on its chunk workers and keeps them loaded with plugin tickets until the viewer leaves. Block events form a change log that stores each block's previous state. A seek in either direction writes only the blocks that differ between the two times. The clock waits while those writes exceed the per-tick budget. Player and entity positions are interpolated between samples, except across jumps of more than eight blocks.

Storage admission reserves space for a recording's maximum size and counts incomplete files. Independent backends can race during admission, so the shared storage limit is not a transactional disk quota. Retention removes old completed files and incomplete files older than the configured duration plus one hour. Generated playback worlds are unloaded and deleted after their viewers leave.

These bounds limit retained work and data. They do not guarantee a tick-time target on every map or machine.

## Archive compatibility

Recordings use a versioned, compressed binary format with bounded decoding and an atomic final file rename. Unchanged equipment uses references to previous frames. Publication markers allow the replay backend to recover history entries after a database failure. Recovery runs on its own worker and decodes at most four unindexed archives per pass. A crash before the publication marker is saved can leave a recording unavailable until retention removes it.

Playback requires the recording's Minecraft version to match the backend exactly. This protects block-state, item, and entity deserialization across server upgrades. Format version 3 added entities and animations to each sample; version 2 recordings still play, without them. No other archive migration is included. Client-version admission remains controlled by the existing proxy and ViaVersion configuration.

[BetterReplay](https://github.com/JustinDevB/BetterReplay) was inspected at commit `7d7d8a7ae3686bf3223262e737fbc23e231e3996` for design comparison. This implementation does not copy its GPL-licensed code.

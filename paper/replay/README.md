# BlockcraftReplay

BlockcraftReplay records rounds from the shared `Round` lifecycle and plays them in disposable worlds on a dedicated `replay` backend. It targets Paper 26.3 and Java 25. [config.yml](src/main/resources/config.yml) holds every limit named below.

`/replay` opens the most recent recordings. Normal players can view rounds they participated in. `blockcraft.replay.staff` permits viewing all recorded rounds and defaults to operators. `blockcraft.replay.use` defaults to everyone. `/replay play <id>` opens a recording on the replay backend. The viewer's hotbar and `/replay pause`, `speed`, `seek` and `stop` control playback. Viewers cannot interact with the recording.

## Run and test

The build task is `./gradlew :paper:replay:build`, which also runs the unit tests. The network's `stagePlugins` task includes the replay jar automatically. In a client, play a round on a game backend, then open it with `/replay` and check seeking, speed, the participant picker and leaving.

## Deployment layout

The root Compose configuration adds BlockcraftReplay to every Paper backend except Knockback FFA, and a separate `replay` service. Each of them mounts the shared `replays` volume at `/replays`. The `replay-storage` initializer assigns the volume to the image's default user and group, both `1000`, before Paper starts. `BLOCKCRAFT_REPLAY_DIRECTORY` overrides the local storage directory.

Playback must run without game plugins. Game listeners are not isolated by world, so playback on an active game backend could trigger game behavior. The `replay` service therefore carries only LuckPerms, BlockcraftAPI, BlockcraftCore, BlockcraftReplay, ViaVersion and GrimAC.

All backends use the shared database for history, participant access, and short-lived viewing requests. Recording files stay on the shared volume. The replay backend must remain running for retention and orphan recovery. This module has no BetterReplay or PacketEvents dependency.

## What a recording misses

Terrain outside the captured chunks is unavailable during playback. A chunk's baseline reflects its state when captured, so a chunk captured later in the round shows that state from the start of playback.

This version does not record experience orbs, falling blocks, fishing hooks, riding passengers, chat, sounds, particles, player camera input, private inventories, or block-entity contents. A mob's appearance does not follow equipment changes after it was first seen. Direct block changes made by other plugins are captured only if the plugin fires BlocksChangedEvent for them. Hitscan shots appear only if the game fires ShotEvent. Eliminated players disappear from the actor roster. These limits make the recording incomplete for games whose main action depends on custom block changes.

Vanished players and their projectiles are excluded. Names and skins reflect the visible player profile. Equipment retains visual components, while custom data, container contents, book text, names, and lore are omitted.

## When a recording hits a limit

Entities may use half of a recording's object budget, and a round that spends it records players and terrain only from then on. An entity snapshot over 16 KiB keeps only the entity type. When a whole recording reaches its object or decoded-size budget, the writer ends the archive at the first event that does not fit and keeps it as partial.

Recordings enter the history after the round ends. A recording that reaches a duration, event, terrain, or object limit is marked as partial and stays private until that round ends. An overloaded writer queue aborts the recording instead of blocking the game thread.

Storage admission reserves space for a recording's maximum size and counts incomplete files. Independent backends can race during admission, so the shared storage limit is not a transactional disk quota. Retention removes old completed files and incomplete files older than the configured duration plus one hour.

These bounds limit retained work and data. They do not guarantee a tick-time target on every map or machine.

## Playback decisions

- Playback worlds use Easy difficulty, because Minecraft refuses to create monsters in Peaceful.
- Recorded entities return as copies of their snapshots with no AI, gravity, owner, or fuse. Projectiles and TNT also receive the recorded motion as velocity, because clients predict their flight between server updates.
- A seek skips swings, hurt flashes, deaths and explosion effects. It writes only the blocks that differ between the two times, and the clock waits while those writes exceed the per-tick budget.
- A viewer who leaves goes back to the server group they opened the recording from if it can take them right now, otherwise to the lobby. The recording stays on screen until the proxy moves them. If they are still on the replay backend after five seconds, playback ends there and restores their inventory, cursor item, location, and movement settings.

## Archive compatibility

Recordings use a versioned, compressed binary format with bounded decoding and an atomic final file rename. Publication markers allow the replay backend to recover history entries after a database failure. A crash before the publication marker is saved can leave a recording unavailable until retention removes it.

Playback requires the recording's Minecraft version to match the backend exactly. This protects block-state, item, and entity deserialization across server upgrades. Format version 3 added entities and animations to each sample; version 4 added shots and the time of day. Version 2 and 3 recordings still play, without what they lack. No other archive migration is included. Client-version admission remains controlled by the existing proxy and ViaVersion configuration.

[BetterReplay](https://github.com/JustinDevB/BetterReplay) was inspected at commit `7d7d8a7ae3686bf3223262e737fbc23e231e3996` for design comparison. This implementation does not copy its GPL-licensed code.

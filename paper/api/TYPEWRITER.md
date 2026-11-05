# Typewriter and Blockcraft story games

Typewriter is the preferred candidate for story authoring. Its editor already provides dialogue, quest conditions,
entities, and cinematic sequences. Blockcraft's presentation prototype supplies the artwork and rendering functions
that a story entry can call. Building a separate quest editor would duplicate Typewriter's main benefit.

## Compatibility evidence

Checked on 2026-11-04 against the official repository at commit
[`d0e0d79`](https://github.com/gabber235/Typewriter/tree/d0e0d79f76bca6373120e0fed66f836bc6ecda69).

- The [published version metadata](https://api.modrinth.com/v2/project/typewriter/version) lists
  `0.9.0-beta-177`, published 2026-09-08, with support through Minecraft 26.2. No published version lists 26.3.
- The [engine build](https://github.com/gabber235/Typewriter/blob/d0e0d79f76bca6373120e0fed66f836bc6ecda69/engine/engine-paper/build.gradle.kts)
  compiles against Paper 1.21.11 and PacketEvents 2.14.0. The
  [release configuration](https://github.com/gabber235/Typewriter/blob/d0e0d79f76bca6373120e0fed66f836bc6ecda69/engine/gradle.properties)
  declares a Minecraft range ending at 26.2.
- The [installation guide](https://docs.typewritermc.com/docs/getting-started/installation) requires Paper,
  PacketEvents, and a matching BasicExtension. Its documentation version is 0.8.0, so source and release metadata
  provide the more precise evidence for current dependencies.

Blockcraft runs Paper 26.3. The evidence establishes a support gap, not a proven runtime failure. Typewriter has not
been installed or tested against this dev network. Compatibility needs a local server test before an adapter becomes
a required dependency.

## Division of responsibilities

Typewriter should own authored dialogue choices, quest conditions, event triggers, and cinematic editing. Blockcraft
should retain ownership of matchmaking, parties, sessions, rewards, unlocks, resource-pack readiness, and the shared HUD.
Story entries must call Blockcraft reward APIs rather than creating another store for owned items or player settings.

The source exposes these adapter points:

| Typewriter extension point | Blockcraft integration |
| --- | --- |
| `DialogueEntry.messenger` and `DialogueMessenger` | Display a portrait line through `Dialogues`; report completion or cancellation to Typewriter. Let Typewriter choose the next entry. |
| `CinematicEntry.create` and `CinematicAction` | Call `ScreenEffects` and actor actions while Typewriter owns the authored timeline. Use one camera controller at a time. |
| `ActionEntry.execute` | Call Blockcraft session and reward services after checking the player's current session. |

The interfaces are in
[`engine-paper/entry/entries`](https://github.com/gabber235/Typewriter/tree/d0e0d79f76bca6373120e0fed66f836bc6ecda69/engine/engine-paper/src/main/kotlin/com/typewritermc/engine/paper/entry/entries).
The [dialogue messenger lifecycle](https://github.com/gabber235/Typewriter/blob/d0e0d79f76bca6373120e0fed66f836bc6ecda69/engine/engine-paper/src/main/kotlin/com/typewritermc/engine/paper/entry/dialogue/MessengerFinder.kt)
has initialization, tick, completion, and disposal hooks. An adapter must stop its Blockcraft presentation on disposal
and distinguish a cancelled presentation from a completed entry. The prototype's completion callback alone does not
provide that whole adapter contract.

No Typewriter adapter is included in this change. The local dialogue graph exists to test the presentation without
installing a story engine. It adds no quest persistence or visual editor. Sternenfront's synchronized match intro
remains owned by the match because skipping must not change any participant's deployment time.

## Acceptance criteria for adopting Typewriter

A local Paper 26.3 test must load the engine, PacketEvents, BasicExtension, and the proposed Blockcraft extension.
It must exercise NPC interaction, dialogue choices, camera cancellation, reconnects, and the pack-loading sequence.
A story test must grant its reward once after completion and grant nothing after cancellation or a failed prerequisite.
The editor should run on the dev network, as recommended by the Typewriter installation guide.

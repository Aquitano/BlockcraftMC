# Test the presentation prototypes

Use a Java 26.3 client on the dev network with the matching Blockcraft pack and updated plugins. Grant `blockcraft.presentation` to the tester, or use an operator account. The Sternenfront camera
demo also needs `blockcraft.sternenfront.presentation`.

## Check the resource pack

1. Join through Velocity with server resource packs enabled. Wait for the pack to load. The proxy must finish
   configuration only after the client reports that it successfully applied the pack.
2. Rejoin with server resource packs disabled. The required pack must prevent gameplay.
3. On the dev network, make the pack URL unavailable and join again. A failed download or the configured timeout must
   disconnect the player. Restore the URL before continuing.
4. Rejoin with the pack enabled. Cached packs must still reach the applied state before configuration finishes.

Minecraft controls the first consent prompt. A required pack can refuse entry after a decline, but cannot override
the client's saved consent preference. The default load timeout is 120 seconds. An operator override retains its
configured value.

## Play the dialogue

1. Run `/presentation dialogue`. A panel with Mara's portrait appears above the hotbar.
2. Sneak while the text appears. The current page becomes fully visible. Sneak again to advance.
3. At K-7's question, scroll to select an answer. Sneak to confirm it. The hotbar selection must remain unchanged.
4. Choose the sensor-data answer. Read every page, then return to the choices.
5. Test the tunnel and main-gate branches on separate runs. The portrait changes expression, and the final line
   reports completion. This demonstration does not save a quest or grant a reward.
6. Restart the dialogue and swap hands. The panel disappears. Repeat with damage, an inventory opening, a world
   change, and a disconnect. The normal HUD must return.
7. Repeat at GUI scales 2, 3, and Auto, including a narrow window. Check the panel, portrait, body text, and controls
   for clipping or overlaps.

Command controls are `/presentation advance`, `/presentation next`, and `/presentation previous`.
`/presentation stop` closes a dialogue or visual demo. Dialogue layout currently uses a 300-pixel panel with three
body lines and up to three choices. Text is plain text after message resolution, with a bundled Latin glyph set.

## Inspect the effects

Run each command separately. Wait for the demo to finish, or use `/presentation stop` between demos.

| Command | Expected result |
| --- | --- |
| `/presentation hologram` | Three floating models use cyan scanlines and rotate around a point in front of you. |
| `/presentation plasma` | The same models use a moving violet material. |
| `/presentation pulse` | The same models pulse gold. |
| `/presentation text` | Five text displays demonstrate float, shake, brightness pulse, rainbow color, and shine. |
| `/presentation cinematic` | The screen fades from black, shows letterbox bars, then fades back before clearing. |
| `/presentation holoscreen` | The screen receives a cyan tint, scanlines, and slight distortion. |
| `/presentation storm` | The screen receives a cool tint and a darkened edge. |
| `/presentation portal` | The screen edge distorts and receives a purple tint. |
| `/presentation focus` | The screen edge loses color and darkens. |

The default demo lasts ten seconds. Visual models and text are visible only to the tester. With a second client,
check that the other player cannot see these displays. Confirm that ordinary item lighting, text, and enchantment
glint still work. Repeat with vanilla rendering before testing optional client shaders, which may override core
shaders from the pack.

During a demo, try to start a dialogue or `/sfintro`. The second presentation must be refused. Change worlds or
disconnect during a screen effect. Rejoin and confirm that the effect and its entities are gone.

## Check gameplay effects

1. Level up in any game. A focus effect fades in and out over 24 ticks. The level-up notification and HUD remain visible.
2. In Sternenfront, activate Siege Mode as a player. A holographic screen effect lasts 20 ticks after a successful activation.
3. Use Force Push. The caster receives a focus effect lasting 12 ticks. Other players' screens remain unchanged.
4. Trigger another event within two seconds. Effects must not stack or continually restart.
5. Die, leave the round, or change worlds during an effect. The effect must clear before the next life or lobby.

API `presentation.gameplay` configures the enable switch, peak intensity, fade duration, cooldown, and level-up duration.
The default intensity is 0.5. Sternenfront's `presentation.gameplay` configures Siege Mode and Force Push durations.
A duration of zero disables that event. Sternenfront's existing `presentation.effects` switch also disables its two hooks.
Gameplay effects do not take ownership of the HUD or block movement and input. A dialogue, demo, or cinematic takes precedence.

## Play the Sternenfront intro

1. In the Sternenfront lobby, run `/sfintro`. Watch the map overview and headquarters shots. The infantry actor
   performs an eased salute and head movement from `animations/briefing.animation.json`.
   Hear the faction's German radio briefing and the cues at the post and headquarters shots. Check the chat transcript.
   The final headquarters shot gains focus from 75% to 95% of the sequence. Check that no translucent armor stand
   appears at the camera. The camera uses an empty item display, with no item or shadow to render.
2. Sneak while the radio plays to skip the demo. Repeat with `/sfintro stop`. All intro sounds must stop immediately.
   Check that your position, game mode, flight, and vulnerability return to their previous values.
3. Start a real match with two testers. Let one watch the intro and let the other sneak immediately.
   Both must deploy at the same time. The round timer and bots must start after the shared intro deadline.
4. Disconnect one tester during the intro and rejoin before it ends. The player must wait with the match, then deploy.
5. Check the shots for each faction. Adjust `intro.shots` in the Sternenfront operator config if a camera enters
   terrain. Camera offsets are relative to the map's overview, headquarters, and control posts.

See the [Sternenfront intro reference](../sternenfront/README.md#match-intro) for animation limits and camera configuration.

## Test event presentations

### Battlefront deployment and captures

1. Start a match. The intro ends in black and teleports directly into deployment before fading in. The overview must
   not flash or leave the player falling. Join a running match and check the same immediate arrival behavior.
   A location title and radio subtitle identify the spawn. There is no additional camera move.
2. Die and choose a controlled post. Respawn fades out, waits two ticks at full black, teleports, then fades in.
   If the post becomes unavailable during the fade, deployment must fall back to headquarters and show that location.
3. Disconnect or end the round during the fade. The pending deployment must be cancelled.
4. Capture a neutral or enemy post. A ring expands around it in the capturing faction's color. An objective banner
   fades in and out. Existing capture rewards, sounds, and block recoloring still apply.
5. Run `/sfintro`, then skip. The actor, screen effects, and title must disappear. During a real match, the camera
   remains until the shared deadline, with only the waiting message remaining.

Sternenfront `presentation.deployment` controls fade duration and the briefing banner. Set `fade-ticks: 0` for
immediate deployment. `presentation.capture` controls the ring duration, radius, and particle count; `ticks: 0`
disables capture presentation. Without the pack, deployment is immediate and the native notifications remain readable.

### Battlefront outlines and combat HUD

1. With the matching pack applied, check ally and enemy outlines with each weapon without aiming or sneaking.
2. Put a solid wall between the viewer and another player. No covered outline should remain. Repeat with a closed
   trapdoor, a thin column, stairs, and a target partly visible around a corner. Check the custom heavy arms and weapon,
   not just the invisible actor's collision box.
3. Repeat as the relic champion. Its outline follows the same depth checks instead of enabling global glow.
4. Take damage and earn repeated kills or assists. Health, guard, damage direction, and cooldowns must remain visible
   beside compact feedback. Check a narrow window and larger GUI scales for clipping.
5. Check another game's ordinary glowing item or enemy. Its normal outline color and blur must remain unchanged.
   Test vanilla rendering first; optional client shader packs can replace the pack's outline shaders.

### Zombies outage and revive

1. Buy power and wait for an outage. Check the brief cool screen accent, low ambient sound, and generator smoke
   alongside the existing sparks and switched-off lights. The existing darkness mechanic remains unchanged.
2. Repair the generator by sneaking near it. The outage audio must stop. Repeat with a wave ending, a player being
   downed, and a player leaving the server.
3. With two players, let one become downed. Sneak nearby to revive them. A gray ring fills green with progress.
   Release sneak and verify that the progress resets. Finish the revive and check the expanding recovery ring.
4. End the round during an outage or recovery. No audio or screen effects should carry into the lobby.

For a deterministic dev test, use temporary Zombies operator overrides: `events.chance: 1`, `events.from-wave: 1`,
`events.power_outage.weight: 1`, and the other event weights set to zero. Buy power before the next wave. Restore
these overrides after testing. Zombies `presentation` controls effect durations, audio volume, and ring density.

### Story discovery demo

1. In a clear, flat area, run `/presentation discovery`. A private villager guide looks toward a floating artifact.
   The artifact is highlighted while a portrait dialogue presents two choices. Your camera remains under your control.
2. Use sneak to reveal or advance text and scroll to choose whether to inspect the artifact. Complete both branches
   on separate runs. This demo saves no progress and grants no rewards.
3. Swap hands or use `/presentation stop`. The guide, artifact, highlights, and dialogue must disappear together.
   Repeat by taking damage, walking more than 16 blocks from the artifact, changing worlds, and disconnecting.
4. Watch with a second client. That player must not see the guide, artifact, or highlight particles.

The guide uses a vanilla villager as a placeholder; the portrait and artifact use existing Blockcraft assets.
Configure the branches under API `presentation.discovery.demo`, with text under `presentation.discovery` in
`messages.yml`. Distances and highlight timing are also configurable.

## Reuse presentation ownership

Create a `PresentationScope(plugin)` for one presentation. Register cleanup with `scope.own(entity::remove)`,
`scope.own(task::cancel)`, or another cleanup action. Closing a scope releases resources once, in reverse order,
even if one cleanup fails. Registering a resource after closure releases it immediately. Use these APIs on the
server thread.

`Scenes.play(player, timeline, scope, completion)` and
`Dialogues.start(player, dialogue, messages, scope, completion)` take ownership of the supplied scope, including
when startup fails. Closing a dialogue scope ends that dialogue. Scene scopes own the visual resources; Scenes
owns the camera separately so a match-intro skip can remove visuals while retaining the camera until the deadline.
After a HOLD skip, frame callbacks must respect `skipped` and avoid creating new visual resources.

Use `ScreenEffects.frame(scope, player, fade, letterbox)`,
`ScreenEffects.atmosphere(scope, player, atmosphere)`, and
`BlockcraftApi.get().visuals().accent(scope, player, atmosphere, ticks)` for owned screen effects.
An older scope cannot remove a newer owner's effect when it closes. These handles control cleanup, not priority;
callers must still avoid starting a presentation while a higher-priority one is active.

`BlockcraftApi.get().discoveries().start(player, actor, target, dialogue, messages, scope, completion)` combines an
actor looking at a target, a private highlight, and dialogue. Supply a private actor for player-specific stories.
The service restores the actor's orientation on exit. Register temporary actors and props with the scope if they
should also be removed. Use the completion callback to hand the selected endpoint back to your game or story system.

## Build and validate locally

Use the Java 25 version from `mise.toml`.

```sh
./gradlew :resourcepack:generateDialogue :resourcepack:generatePresentation
./gradlew build
python3 resourcepack/verify_shaders.py --client /path/to/26.3.jar --glslang /path/to/glslangValidator
```

The pack is `resourcepack/build/blockcraft.zip`. Shader validation checks compilation and linking, not appearance
on a GPU. Automated tests cover dialogue sequencing, font metrics, camera interpolation, animation parsing, pack
readiness, and post-effect ownership. Client checks above remain necessary.

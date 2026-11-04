# Test the presentation prototypes

Use a Java 26.3 client on the dev network with the matching Blockcraft pack and updated plugins. These changes have
not been deployed. Grant `blockcraft.presentation` to the tester, or use an operator account. The Sternenfront camera
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
   performs a salute and head movement from `animations/briefing.animation.json`.
   The final headquarters shot gains focus from 75% to 95% of the sequence. Check that no translucent armor stand
   appears at the camera. The camera uses an empty item display, with no item or shadow to render.
2. Sneak to skip the demo. Repeat with `/sfintro stop`. Check that your position, game mode, flight, and vulnerability
   return to their previous values.
3. Start a real match with two testers. Let one watch the intro and let the other sneak immediately.
   Both must deploy at the same time. The round timer and bots must start after the shared intro deadline.
4. Disconnect one tester during the intro and rejoin before it ends. The player must wait with the match, then deploy.
5. Check the shots for each faction. Adjust `intro.shots` in the Sternenfront operator config if a camera enters
   terrain. Camera offsets are relative to the map's overview, headquarters, and control posts.

See the [Sternenfront intro reference](../sternenfront/README.md#match-intro) for animation limits and camera configuration.

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

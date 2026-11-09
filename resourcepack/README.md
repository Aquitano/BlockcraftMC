# Resource pack

How the proxy serves the pack is in the [root README](../README.md#resource-pack). This file is for changing what it draws.

The [presentation test guide](../paper/api/PRESENTATION.md) covers dialogue portraits, animated materials and text,
screen effects, and the Sternenfront intro.

## Constraints from the client

- **Plain fallback.** Shared code draws a vanilla version for a client without the pack, because a client can be in the
  world before it loads. `UiFont.overlaid` has no fallback, and Sternenfront's class menu uses it. A menu's own `screen()` falls back to its plain title.
- **Slot highlight.** The pack blanks `container/slot_highlight_back` and `_front`, because the highlight would mark one
  square of a card or toolbar button. The client has no per-screen switch, so this holds in every inventory. A menu
  item that needs a frame draws it in its own model, as Sternenfront's spawn markers do with `oversized_in_gui` so the
  frame can cover the slot's border.
- **Scope.** `HudArt` replaces `textures/misc/spyglass_scope.png` with the long rifle's reticle, so a spyglass in any
  game on the network looks through it.
- **Boss bars.** `HudArt` blanks the `pink_*` and `notched_20_*` boss bar sprites, because Sternenfront's objective
  line and minimap carry only text on a boss bar line. The client has no per-server switch, so on every server of the
  network a pink bar or one with twenty notches shows its text and no bar. Any other plugin's boss bar needs another
  colour and overlay.
- **Items other players can see.** A held or worn item or a display keeps its vanilla item and asks for the pack's model
  through custom_model_data. The pack overrides the vanilla item in `assets/minecraft/items/<item>.json` with a `select`
  on that string, whose fallback is the item's own vanilla definition. So a client without the pack draws the vanilla
  item. `item_model` set for one viewer is only for menus, which nobody else sees.
- **Title lettering.** `<display>` is only for titles, because `Notifications` turns it into bold text for a client
  without the pack. At title scale about thirteen letters fit a 1366x768 screen.
- **Counted text.** A player's own pack may replace `minecraft:font/ascii.png` or the default font with letters of other
  widths, and only the client knows them. Text whose width the server counts, such as the deploy screen's lines and
  the action bar beside the ability tiles, draws from `text.png`, the pack's copy of the vanilla sheet, in
  `blockcraft:text` or a deploy line font. `PackArt` writes the letters' advances to
  `paper/api/src/main/resources/text-glyphs.properties`, which `UiFont.width` counts with.
- **Crosshair marks.** The client centres a subtitle by the advances it measures from each glyph's last inked column, and
  draws a font pixel there two GUI pixels wide. `HudArt` therefore draws the marks, arcs, spread ticks and readiness
  marks at twice the resolution, centred between the middle texels, with a nearly clear texel in each cell's last
  column; `UiFontTest` checks that each still takes no room.
- **Sounds.** World sounds must be mono, because the client places only mono sounds in the world. Each script replaces
  only its own entries in `sounds.json`.
- **Portraits.** `SternenfrontArt.java` renders the role cards into `resourcepack/art/sternenfront` from the class
  models. `generateTextures` runs that generator before `PackArt` copies them into the menu tiles.

## Making a model

Sternenfront equipment and body models come from `SternenfrontArt.java`. Edit the generator for those assets.

The hand-made 3D models live in `assets/blockcraft/models/item/<set>/`, each named by its `PackModel`. They are cuboids
textured with vanilla block textures, which keeps the pack small and the look vanilla. Made in Blockbench, a model is
exported with "Java Block/Item".

- Elements must stay between -16 and 32 on every axis, so a larger set piece is scaled up by its display.
- Textures stay at 16 to 64 pixels, because every change makes every client download the whole pack again.
- A model that needs its own texture puts it under `textures/item/<set>/`, or under `textures/block/<set>/` when it also
  draws vanilla block textures, because a model draws from one atlas.
- A held model points along +z and borrows the Zombies guns' display transforms. Their `fixed` transform turns them
  side-on into the plane where a vanilla item sprite lies on an item display, so a display shows the same profile with or
  without the pack.
- A hat stands on the head's 0..16 cube and is worn on an item that is neither armour nor a skull, because the client
  draws a worn skull as a skull whatever its model.
- A tinted `PackModel` takes the colour of its `tintindex: 0` faces from the item's first custom_model_data colour, so
  one model draws every blade colour.
- The client sorts each face by the alpha of its own texture region: a face with partial alpha draws in the sorted
  translucent pass and the rest of the model stays solid. A lightsaber's white core is opaque and its two tinted
  shells fade across each face, which is as close to a glow as an item model gets without additive blending.
- Item icons use a native 16-pixel grid, transparent margins and upper-left lighting. HUD symbols use seven pixels of ink
  in each eight-pixel tile to align with the text baseline.

## Sternenfront first-person weapons

`SternenfrontArt.java` assigns seven `GunPose` families in the equipment list. The same definition supplies resting
transforms, a firing kick, a sprint pose, lowered and turned across the body, and an aim pose with its own smaller kick,
for both hands. The aim pose puts the rear of the top of the gun's centre line, its sight, half a block ahead of the eye
and just below it, so the barrel runs to the crosshair; a pair of pistols sits either side of it. The vanilla item
override draws `<model>_aim` only in the first-person hands, while the client's own use key (right click) is down.
A long rifle is a spyglass to the client, which draws it in its `head` transform at the eye while the player scopes. Vanilla mirrors the left hand's X translation, yaw and roll, so the authored
hand transforms are identical. Third-person, GUI, ground and fixed transforms retain their existing values.

Run `:resourcepack:generateBattle` to regenerate the equipment, firing, lowered and aim models. Java writes those transforms to
`build/sternenfront/gun-poses.json`; `BattleArt.py` consumes that manifest and inherits each gun's geometry and other
transforms. Running `BattleArt.py` directly requires a fresh Java generation first.
`:resourcepack:verifyWeaponPoses`, also part of `check`, checks that the guns and guarded blades `PackModel` registers
are the ones the generator writes, and their vanilla item routes, texture paths, both hands, firing and lowered
inheritance, family framing, recoil differences, the lowered muzzle, the sights under the crosshair and wrist bounds.

Each long gun has one feature that names it in profile. The carbine is short and boxy with a power cell standing out on
its left. The DC-15A is long, with a scope and a grip under the barrel. The E-5 is long and thin, with a ribbed barrel and
a rod stock offset low. The DC-15x has a large scope and a skeleton stock; the E-5s keeps the E-5's ribs, with a slim
scope along its left side and a forked muzzle. The Z-6 has its barrel cluster, a carry handle and a drum cell.
The grip of each sits where its pose family expects it, so the same poses frame the new shapes.

Pistols rise sharply when firing. Twin pistols sit farther outward with an inward cant. Carbines have a compact shoulder
pose; long rifles sit farther forward. The Z-6 sits lower with a larger scale. When firing it tilts up 5 degrees
and moves 0.3 up and 1.0 back.
The B2 wrist blaster includes a metal forearm, rear cuff and fist below its paired barrels. This is ordinary held-item
geometry and remains visible in inventory and third-person views. It does not replace a player's arm or body.
Twin-pistol models still represent one gun per equipped hand; the pose does not create a second hand or alternate shots.

The firing and lowered states are discrete model swaps, with no interpolation, barrel animation or change to shot mechanics.
Client checks must cover both dominant-hand settings, offhand pairs, firing, lowered and resting at normal and wide FOV,
and the wrist cuff's overlap with the vanilla hand. Automated model checks cannot establish screen clearance or clipping.

## Sturmwerft shipyard props

`YardArt.java` authors nine original props with separate 64-pixel atlases in
`models/item/sternenfront/yard/` and `textures/item/sternenfront/yard/`. The props use ivory enamel, blue-gray steel,
charcoal recesses, teal instruments, amber indicators and weathered copper. They reference no vanilla textures.
`:resourcepack:generateYardArt` regenerates only these assets and `resourcepack/build/yard/contact-sheet.png`.
The contact sheet uses the generated geometry and face UVs with ordinary directional shading.

The `PackModel` paths are `sternenfront/yard/console`, `power_coupler`, `engine_cradle`, `cargo_stack`, `repair_droid`,
`wall_panel`, `clone_tank`, `embryo_rack` and `gene_archive`, all under `sternenfront/yard/`.
They use `ItemDisplayTransform.NONE`, with no model display transforms.
The model's ground origin is `(8,8,8)`, and its authored front points along +Z. The runtime cancels the client's extra
half-turn, as it does for dropships. Do not add another corrective model rotation.

All element corners, including rotated corners, fit `x,z = -8..24` and `y = 8..32`. At scale 1, the item-centred
geometry therefore fits within two blocks horizontally and rises at most 1.5 blocks above the display position.
`YardArt` checks these bounds and its explicit UVs before writing each model. The props use ordinary world lighting.

The three cloning props use clean ivory collars, ice-blue interiors and warm skin tones. Their 64-pixel atlases use
only alpha 0 and 255. The capsule windows contain narrow opaque reflections and bubble marks, with the remaining
pixels fully clear. Each window is a single face.
The faceted collars use cutout caps and separate side faces to avoid overlapping cap faces.
The six shipyard atlases use their original opaque texture writer.

| Kind | Actual rotated bounds in model units | Intended scale | Visible size in blocks |
| --- | --- | --- | --- |
| `clone_tank` | `(0.2, 8, 0.2)` to `(15.8, 32, 15.9)` | 1.5 | 1.4625 × 2.25 × 1.471875 |
| `embryo_rack` | `(-4, 8, 1.6)` to `(20, 22.4, 14.4)` | 1 | 1.5 × 0.9 × 0.8 |
| `gene_archive` | `(2.5, 8, 2.5)` to `(13.5, 21, 13.5)` | 0.8 | 0.55 × 0.65 × 0.55 |

Bounds are rounded to four decimals. `YardArt` checks both the authoring boxes and rotated faces against each
cloning prop's tighter bounds. The adult clone spans model `y = 10.5..29.7`, or 1.8 blocks at scale 1.5.
The gene archive has three capped cartridges and an inventory panel inset behind its ivory bezel.

The visual references are Lucasfilm's [Kamino](https://www.starwars.com/databank/kamino),
[Tipoca City](https://www.starwars.com/databank/tipoca-city),
[medical capsules](https://www.starwars.com/databank/medical-capsules),
[Kamino history gallery](https://www.starwars.com/kamino-history-gallery) and
[ARC Troopers gallery](https://www.starwars.com/series/clone-wars/arc-troopers-episode-gallery).
The upright adult-filled capsule adapts those references for this desk. It is not an exact screen replica.
All geometry and pixels are authored locally. No source images are bundled.
Client verification must check the clone's face, window cutouts, lighting and clearance on the desk.
The software contact sheet does not reproduce Minecraft's item renderer.

## Presentation assets

`DialogueArt.java` generates the original portraits, panel, fonts, and matching Java glyph metrics.
`PresentationArt.java` generates three effect models and the named post-processing chains. Run
`:resourcepack:generateDialogue` and `:resourcepack:generatePresentation` to regenerate only those assets.

The item and text shader overrides preserve Minecraft 26.3's ordinary rendering branches. Exact color markers opt
into effects. Item markers use RGB `(233, mode, 197)`, where modes 1, 2, and 3 select hologram, plasma, and pulse.
Each affected model face needs `tintindex: 0`, and each element needs `shade: false` to preserve the marker bytes.
`VisualEffects.model` supplies that color through the first custom-model-data color. These models are viewer-only.
Their textures live under `textures/item/presentation`, which Minecraft 26.3 includes in its item atlas.

Text markers use RGB `(231, mode, 197)`. `TextMotion` selects float, shake, brightness pulse, rainbow, or shine.
Marked text shadows are suppressed.

GUI text can also be a HUD panel, which the text shader moves into a corner of the screen and colours from its tint
table. The table at the top of `hud_panel.glsl` lists every colour the text shader reserves, for panels and text effects
alike; ordinary text must avoid them and their darkened shadows. `HudPanelTest` checks that Sternenfront's `HudPanel`
writes the same corners and tints in the same order. A panel must take no width and start in its line's middle, where
the shader expects it. The shader finds a glyph's boss bar line from where the glyph lies in it, so every glyph of
`HudArt`'s `blockcraft:minimap` font hangs from the line's top and is at most 16 pixels tall; `MinimapLayoutTest` checks
both. The minimap uses the top-left corner and the kill feed the top-right, both on the minimap's boss bar line.
Sternenfront's ability tiles use the bottom-right corner with `HudArt`'s `blockcraft:abilities` font. The action bar
holds other text, so the server places that panel at the line's middle; `AbilityPanelTest` and `CombatFeedbackTest`
check the widths involved. It hangs from a corner a full column of four tiles above the screen's bottom, which
`AbilityPanelTest` checks against the shader. Its art has twice the texels of its GUI size, so it keeps detail from GUI scale 2 up; `HudArt`
writes every icon, ready and recharging, to `build/hud/ability-icons.png` for judging the set.

Sternenfront target outlines reserve scoreboard colors `DARK_AQUA` and `DARK_RED`. Its server sends those colors only
to combat viewers with the pack applied. The outline shader stores logarithmic reversed-Z depth in the RGB bytes and
uses alpha bytes 1 and 2 to identify the factions. The post-effect compares each silhouette pixel with the main scene's
depth, including the neighboring pixel receiving the outline. Covered pixels stay clear, and other glow colors retain
Minecraft's original outline and blur. Do not use the reserved colors for ordinary glowing entities.
The native outline buffer has no depth attachment. Exactly overlapping outlined front surfaces can suppress an
edge when a later surface overwrites its depth. The comparison stays conservative rather than exposing covered pixels.

`ScreenEffects` uses Paper 26.3's named post-effects API. Cinematic fades have 33 levels, with optional letterbox bars.
Atmospheric presets change screen color and distortion. They do not add world lighting, weather simulation, or video
playback. Cleanup removes only Blockcraft's presentation post-effects and preserves other namespaces.
Gameplay accents use eight intensity levels under `presentation/accent`. `VisualEffects.accent` controls their
duration, fade, and cooldown without suspending normal HUD updates. It clears them when a cinematic or dialogue starts.

`verify_shaders.py --client <26.3-client.jar> --glslang <validator>` compiles the core variants and post-effect pairs.
It needs Python 3 and glslangValidator. It does not download dependencies or change the pack. A new Minecraft version
needs validation against that version's vanilla shaders and a real client rendering check.

`verify_target_outlines.py --client <26.3-client.jar>` renders the actual outline shaders with ModernGL, EGL, and
OpenGL 4.5. It checks full and partial cover, thin cover at up to 256 blocks, edge clipping, a rotated cube, and preservation
of ordinary vanilla glow. The fixture uses Minecraft's reversed-Z range from zero to one. Software Mesa can run it on a
machine without a display. These fixtures do not replace a check with the player's client or shader mod.

# Resource pack

How the proxy serves the pack is in the [root README](../README.md#resource-pack). This file is for changing what it draws.

The [split pack prototype](SPLIT_PACK.md) documents the additional archives, client stack order, and rollout limits.

The [presentation test guide](../paper/api/PRESENTATION.md) covers dialogue portraits, animated materials and text,
screen effects, and the Sternenfront intro.

## Edit and validate assets

Finished imports are authoritative Minecraft files. Edit their models and textures in `src/assets`, their rigs in
`bettermodel`, and their furniture rules in `../nexo/items`. `catalogs` records their native item and rig identifiers.
The original GLBs and one-time converters are retired. They are not inputs to generation or packaging.
The weapons in `finished-weapons.txt` and their pose models are preserved when authored equipment is regenerated.

Render a finished native model without its original import file:

```sh
uv run --locked python assets.py preview blockcraft:item/props/imported/halloween_candle
```

`--yaw 217` shows the rear. The preview uses current native geometry, cutout alpha and the first animation frame.
Vanilla textures referenced from the client require Minecraft verification; they are not bundled for this renderer.

For an authored family, use the locked Python environment from this directory:

```sh
uv run --locked python assets.py list
uv run --locked python assets.py generate planet
```

The second command prints an isolated output directory under `build/assets`. It leaves accepted source files unchanged.
Inspect its `asset-report.json` and previews, then apply that exact result:

```sh
uv run --locked python assets.py apply /absolute/path/to/generated-workspace
uv run --locked python assets.py check
```

Application revalidates the result and rejects changed source inputs, modified generated files and concurrent edits.
It copies only changed accepted files and never deletes unrelated output. To generate and apply in one operation,
pass `--apply`. `--output` selects a new scratch directory. Existing directories are preserved.

Gradle's existing family tasks use this same workflow. `:resourcepack:regenerate` processes all authored visual families
once in dependency order. It preserves finished imports. Sound generation remains an explicit family selection.
`./gradlew build` validates and packages accepted assets without regenerating art or deploying. New backend `PackModel` registrations still use `:paper:api:packOverrides`
with `-PclientJar=/absolute/path/to/the/matching/client.jar`.

Shared model export uses `AssetFiles.py`. `NativeArt.py` supplies cuboid construction and software rendering;
`KitPixels.py` supplies material pixels. Generation validates geometry and creates previews, but the real client
must verify changed translucency, item framing and physical behavior. [AGENTS.md](AGENTS.md) gives the completion rules.

## Constraints from the client

- **Plain fallback.** Shared code draws a vanilla version for a client without the pack, because a client can be in the
  world before it loads. `UiFont.overlaid` has no fallback, and Sternenfront's class menu uses it. A menu's own `screen()` falls back to its plain title.
- **Slot highlight.** The pack blanks `container/slot_highlight_back` and `_front`, because the highlight would mark one
  square of a card or toolbar button. The client has no per-screen switch, so this holds in every inventory. A menu
  item that needs a frame draws it in its own model, as Sternenfront's spawn markers do with `oversized_in_gui` so the
  frame can cover the slot's border.
- **Scope.** `HudArt` draws the long rifle's reticle into `textures/misc/long_rifle_scope.png`, which a helmet shows as
  its camera overlay. The client stretches an overlay over the whole screen, so the reticle is drawn for 16:9 and is
  slightly oval on other screens.
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
- **Wide glyphs.** The client packs font glyphs into atlases 256 pixels across and draws a glyph wider than that as a
  missing-glyph box. `StoryArt` and `DialogueArt` therefore draw their dialogue's 300-pixel panels as left and right halves.
- **Crosshair marks.** The client centres a subtitle by the advances it measures from each glyph's last inked column, and
  draws a font pixel there two GUI pixels wide. `HudArt` therefore draws the marks, arcs, spread ticks and readiness
  marks at twice the resolution, centred between the middle texels, with a nearly clear texel in each cell's last
  column; `UiFontTest` checks that each still takes no room.
- **Sounds.** World sounds must be mono, because the client places only mono sounds in the world. Each script replaces
  only its own entries in `sounds.json`.
- **Portraits.** `SternenfrontArt.java` renders the role cards into `resourcepack/art/sternenfront` from the class
  models. `generateTextures` runs that generator before `PackArt` copies them into the menu tiles.

## Making a model

Authored Sternenfront equipment and body models come from `SternenfrontArt.java`.
Weapons listed in `finished-weapons.txt` are edited directly, including their pose variants.

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
A long rifle's aim models draw nothing, because the scope's reticle covers the view while it aims. Vanilla mirrors the
left hand's X translation, yaw and roll, so the authored hand transforms are identical. Third-person, GUI, ground and fixed transforms retain their existing values.

Run `:resourcepack:generateBattle` to regenerate the equipment, firing, lowered and aim models. Java writes those transforms to
`build/sternenfront/gun-poses.json`; `BattleArt.py` consumes that manifest and inherits each gun's geometry and other
transforms. Running `BattleArt.py` directly requires a fresh Java generation first.
`verify_weapon_poses.py`, run by `assets.py check`, checks that the guns and guarded blades `PackModel` registers
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

## Model generation and runtime

The art generators run during development. Imported sets are already stored as authoritative native Minecraft models. New building kits are authored as cuboids in Python. `KitPixels.py` paints their material tiles, and the generators assign face UVs at a consistent pixel density. `ModelSurfaces.py` clips hidden and competing face patches without moving their texture coordinates.

The output is ordinary model JSON, item routes, PNG textures, and Nexo definitions. Animated actors also have `.bbmodel` rigs with named bones and animation keyframes. The client pack already contains their native bone meshes. The build checks those meshes and pack dependencies before creating the ZIPs. No model generation runs during a match.

Nexo places and tracks furniture. The API's `FurnitureSolids` adds native backing blocks, coarse collision cubes, and world lights. It restores owned blocks when furniture is removed or a map is saved. BetterModel plays actor animations. Sternenfront owns combat and optional objective circuits. The proxy chooses and removes resource-pack layers during transfers.

`./gradlew :resourcepack:regenerate` explicitly regenerates art. `./gradlew build` validates and packages the current generated source. `stagePlugins` stages the packs, rigs, and plugin configuration. The [pack split guide](SPLIT_PACK.md) describes the client stacks and the [furniture guide](../nexo/README.md) describes physical placement.

## Objective model prototypes

`ObjectiveArt.py` generates the shield generator, power relay, and communications console with active, damaged,
and disabled states. Run `:resourcepack:generateObjectiveArt` to regenerate their nine native item routes under
`sternenfront/objectives`. The split builder includes these assets only in the Star Wars archive.
Generation validates geometry, texture references, UVs, and state differences. It writes a manifest with anchors,
footprints, interaction dimensions, and software renders to `build/objectives`.

The generated `nexo/items/blockcraft_starwars_objectives.yml` provides placeable versions with a one-block solid
barrier core in every state. Raw item displays remain non-solid. Place them through Nexo or the existing furniture
layout so placement and removal also manage collision. The shield generator is one block tall at its core.
`FurnitureSolids` mirrors these cores into real world blocks and restores the previous blocks on removal.
Star Wars block raycasts and bot line-of-sight checks include the smaller native colliders used by the building kits.
Sternenfront can connect these props to map-owned circuits for repair, sabotage, timed console overrides, and native shield fields.
Maps without [field-objectives.yml](../paper/sternenfront/README.md#field-objectives) retain their existing rules. The production lobby contains only its accepted ordinary scenery.
Operator instructions are generated in `build/objectives/README.md`.

## Planet building kit

`PlanetKitArt.py` generates 11 Kamino modules and 21 desert modules. Run `:resourcepack:generatePlanetKitArt`
to regenerate the source assets. The [builder guide](art/planet-kit/README.md) records the snap grid, openings,
connectors, support requirements and assembled examples. The manifest contains placement coordinates for each piece.
All kit assets belong to the Star Wars pack. Nexo definitions are in `nexo/items/blockcraft_starwars_planet_kit.yml`.
Nexo placements receive native structural backing and coarse equipment collision through `FurnitureSolids`. Raw displays remain visual previews.

`MilitaryKitArt.py` adds 23 shared military pieces and surface variants. `KitActors.py` adds six static actors and
six BetterModel rigs. Their Gradle tasks are `generateMilitaryKitArt` and `generateKitActors`; both belong to
`regenerate`. The [shared kit guide](art/shared-kit/README.md) covers the sliding door and collision cores.
The [actor guide](art/kit_actors/README.md) covers bone routes, clips and passive exhibition configuration.
All three kits belong to the Star Wars pack. The ordinary lobby does not load that pack.
Production profile delivery must be enabled separately; packaging does not activate it.

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
alike; ordinary text must avoid them and their darkened shadows. `HudPanelTest` checks that the api's `HudPanel`
writes the same corners and tints in the same order. A panel must take no width and start in its line's middle, where
the shader expects it. The shader finds a glyph's boss bar line from where the glyph lies in it, so every glyph of
`HudArt`'s `blockcraft:minimap` font hangs from the line's top and is at most 16 pixels tall; `MinimapLayoutTest` checks
both. The minimap uses the top-left corner and the kill feed the top-right, both on the minimap's boss bar line.
Sternenfront's ability tiles use the bottom-right corner with `HudArt`'s `blockcraft:abilities` font. The action bar
holds other text, so the server places that panel at the line's middle; `AbilityPanelTest` and `CombatFeedbackTest`
check the widths involved. The bottom-right corner lies `HudPanel.BOTTOM_RIGHT_HEIGHT` above the screen's bottom, a full
column of four tiles, which `HudPanelTest` checks against the shader and `AbilityPanelTest` against the column. Its art has twice the texels of its GUI size, so it keeps detail from GUI scale 2 up; `HudArt`
writes every icon, ready and recharging, to `build/hud/ability-icons.png` for judging the set.
Both dialogue panels use the bottom-centre space, which ends `HudPanel.BOTTOM_CENTRE_BOTTOM` above the screen's bottom,
just over the hearts. The shared panel from `DialogueArt` is 56 or 66 pixels tall, so at the smallest GUI, 240 pixels
tall, its top stays below the crosshair; `DialogueAssetsTest` checks that and the 256-pixel limit.
Zombies' weapon panel uses the same corner with `HudArt`'s `blockcraft:zombies/weapon` font, which the modes pack
carries. It stands on the corner's margin below the column's top, and `WeaponPanelTest` checks its glyphs against the
font. `HudArt` draws each gun's icon from its pack model in flat colours of the vanilla blocks the model uses, so a
changed gun model needs `assets.py generate hud`.
Zombies' station screens come from `StationArt`, family `stations`: a whole chest screen per layout in riveted olive
iron with sunk cards and a hazard-striped rail for the exit, and a line font for each height a card writes at. The cards'
guns are the weapon panel's side views at full size. Zombies' `StationScreen` lays the cards out from the same numbers,
which `StationScreenTest` compares, along with every glyph's width.

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

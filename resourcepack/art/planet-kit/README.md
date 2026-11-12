# Planet building kit prototype

The kit contains 32 native cuboid modules in ten selected families. Kamino has 11 modules. Desert has 21 modules. Utility droids, wildlife, weapons, and Geonosis factory parts are outside this kit.

## Files

`resourcepack/PlanetKitArt.py` owns generation. The pack source lives under `resourcepack/src/assets/blockcraft/{models/item,items,textures/item}/sternenfront/kits/{kamino,desert}/`. Nexo definitions live in `nexo/items/blockcraft_starwars_planet_kit.yml`.

[manifest.json](manifest.json) records every item route, occupied bounds, assembly offset, connector, and required supporting blocks. [sha256.json](sha256.json) records the assets, Nexo definitions, and PNG renders. [split-validation.json](split-validation.json) records the optional split check against the supplied reference source.

The PNGs render exported JSON elements with their resolved inherited model data and atlas UVs. They use a CPU depth buffer and directional shading. They are not Minecraft screenshots. Each individual render uses the same camera and automatic framing, so the contact sheet does not show relative scale.

## Placement reference

One block equals 16 model units. The ground origin is model coordinate `(8,0,8)`. The front faces `+Z`. The base snap grid is 16 units. Structural window, wall, doorway, roof, and shade spans are 32 units. Equipment, weather overlays, and poles use the smaller widths in the manifest. Scale is `(1,1,1)`.

The manifest expresses connectors relative to the ground origin. For an unrotated module, divide a connector by 16 and add the ground position to get its world position. To join modules, rotate their connectors around Y and make their world positions coincide. Straight structural modules repeat every 32 units along X. Corner return connectors end at `(14,0,16)`, so a rotated return starts on that station rather than a guessed bounding-box center. Roof terrace and dome connectors use a 32 by 32 footprint. Rim strips repeat along X. Equipment outlet ports have a center at `(8,4,0)` and face `+X`. Inlets are at `(-8,4,0)` and face `-X`. Both have a 2 by 2 unit cross-section. The assembled pump outlet meets the cistern inlet exactly.

The declared span is the reserved envelope. `extents_units` is the measured occupied size. Small lights and coping can extend beyond the nominal structural plane. `opening_xy_units` gives the clear rectangle in the frame plane. The mullion window has one intentional center rib through its opening. The deep doorway clears 22 by 30 units, or 1.375 by 1.875 blocks. The parked shutter clears 20 by 28 units and is unsuitable for an ordinary player passage beneath a solid lintel.

The native model origin for an `item_display` is `(8,8,8)`. Place a raw display half a block above the desired ground position to compensate for this kit's ground origin. Use `item_display: "none"`. Apply the existing yard half-turn cancellation once. Nexo uses `display_transform: NONE`, unit scale, and zero translation. The integrated gallery uses Nexo placements, verified in a Minecraft 26.3 client.

The generated Nexo entries declare `blockcraft_collision` recipes. `FurnitureSolids` supplies native wall, floor, roof and terrain backing, glass backing for ocean windows, and smaller native colliders for equipment and frame posts. Doorway apertures remain clear. Placement preserves existing solid supporting blocks and removal restores only blocks owned by that furniture. Thin surface details attach to solid backing; decorative water and canopy fabric remain passable. Raw item displays have no physical collision.

The canyon pieces are two-block-wide facings and cave trim for real block-built terrain. They do not replace terrain volumes. The dome is a small stepped roof accent. Repeat roof rims around native roof blocks rather than scaling the dome into a building.

## Family inventory

| Planet | Family | Modules |
| --- | --- | --- |
| Kamino | Ocean windows | Mullion, support rib, open corner |
| Kamino | Platform edges | Rim, corner, rail, light |
| Kamino | Storm wear | Drain, water streaks, leaking seam, worn panel |
| Desert | Roof and dome | Rim, corner, stepped terrace, stepped dome |
| Desert | Weathered plaster | Straight, return corner, repair patches |
| Desert | Recessed entrances | Deep reveal, outer frame, parked shutter |
| Desert | Shade and market | Awning, pole, canopy |
| Desert | Water infrastructure | Vaporator, cistern, pump |
| Desert | Power infrastructure | Local generator, distribution cabinet |
| Desert | Canyon and rock | Strata facing, ledge, cave edge |

There are two shared animated RGB atlases. Each PNG is 128 by 512 pixels and contains four 128 by 128 frames. Each material occupies one 32 by 32 tile per frame. Only the water tile changes between frames. The animation advances every four ticks and repeats after 0.8 seconds. The `.png.mcmeta` files must travel with the textures. It adds no particles, extra entities or liquid blocks.

`KitPixels.py` paints clustered color changes, plaster chips, masonry layers, cloth weave and metal recesses. Face UVs crop within their material tile at one texel per model unit. Thin ribs therefore sample a narrow material region instead of compressing a whole tile onto their edge. Window frames have stepped enamel trim, dark glazing gaskets and projecting sills. The glazing openings remain clear.

There is no transparent model geometry or baked text. Window openings receive native glass through their collision recipes. The repair wall inherits the straight wall's textures while replacing its element list. Small platform and equipment indicators emit at level 5 in the model. Generated Nexo definitions provide native world illumination through `FurnitureSolids`. The pack changes no vanilla textures, blockstates or shaders.

## Regenerate and validate

Run from the repository root with Python 3.12 or newer and the dependencies declared in the script:

```sh
uv run resourcepack/PlanetKitArt.py
```

The generator validates all 32 exported routes, atlas dimensions, animation metadata, native bounds, UV tile regions, declared envelopes, connectors and clear aperture rectangles. Six connector pairs must coincide exactly. It also verifies that every non-water material stays identical across animation frames. It renders all modules and four assemblies: `kamino_assembly`, `desert_assembly`, `storm_assembly` and `water_assembly`. `kamino_contact.png` and `desert_contact.png` are the contact sheets. The assembly transforms are in the manifest. Software renders show the first animation frame and omit supporting blocks.

For split validation, pass the directory containing a reference `split_pack.py` and its generated `src` directory:

```sh
uv run --project resourcepack --locked python resourcepack/assets.py generate planet
```

This copies the reference source into scratch output, overlays only the new assets, classifies each as `starwars`, and runs the reference split builder and dependency verifier. The source and helper stay read-only. The scratch source is removed after a successful check. No helper import uses a hardcoded parent path.

For determinism, save `sha256.json`, regenerate with the same arguments, and compare it byte for byte. Generation has no random or timestamp input.

## Integration example

The Nexo file supplies 32 new identifiers and routes without modifying `PackModel` or an existing generator. Run `:resourcepack:generatePlanetKitArt` to regenerate the source assets before packaging. The ordinary build packages the existing generated assets. `stageNexo` includes the definitions and kit assets.

A raw display example for a ground plane at Y=64 is:

```mcfunction
summon minecraft:item_display 0 64.5 0 {Tags:["planet_kit_preview"],item_display:"none",Rotation:[180f,0f],item:{id:"minecraft:paper",count:1,components:{"minecraft:item_model":"blockcraft:sternenfront/kits/desert/entrance_recess"}}}
```

For adjacent wall spans, place their ground origins two blocks apart on X. For the assembled examples, divide `ground_offset_units` by 16, add the selected ground location, and add 0.5 to display Y. Each manifest scene uses yaw zero in model space. The runtime half-turn cancels the item display convention rather than rotating the builder layout.

## Verification limits

The test gallery contains all 32 modules and four assembled examples. Kamino has native glass and a contained water basin behind its windows. Separate labeled displays show the storm panels and the vaporator, pump and cistern. Their locations are recorded in `run/imported-hub/selected-kit-gallery-positions.json` and the gallery builder. The animated leak and gauges are decorative. They do not repair, flood, supply water or affect a match.

The generator is available as `:resourcepack:generatePlanetKitArt` and is part of `:resourcepack:regenerate`. The prototype uses deliberately blocky stepped forms and repeated material tiles; it does not include rounded mesh geometry or translucent glass.

See [validation.json](validation.json) for the current generator and integration evidence. The full Java 25 build is recorded in `run/imported-hub/polish-build.log`. Animated wildlife movement and full navigation around assembled maps still need gameplay verification.

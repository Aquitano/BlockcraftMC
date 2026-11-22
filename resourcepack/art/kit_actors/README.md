# Utility droids and desert wildlife prototype

`KitActors.py` authors six original voxel actors without imported artwork. The utility family shares one opaque 64×64 atlas. The desert family shares another. All actors face north, along negative Z, with the root on the ground beneath the torso. One block equals 16 rig pixels.

`KitPixels.py` supplies their material tiles. Utility actors use enamel edges, panel recesses, vents and fasteners. Wildlife uses fur clusters, scale plates, horn bands and stitched leather. The texture pass keeps the existing geometry, 37 bone models, animation tracks and anchors.

| Actor | Height in blocks | Width × depth in blocks | Bones | Cuboids |
| --- | ---: | ---: | ---: | ---: |
| Astromech | 1.3125 | 1.125 × 0.64375 | 6 | 18 |
| Gonk | 1.3125 | 0.8125 × 0.70625 | 4 | 11 |
| Mouse droid | 0.5 | 0.7 × 0.90625 | 6 | 13 |
| Bantha | 2.5 | 2 × 3.1875 | 8 | 31 |
| Dewback | 1.6875 | 1.5 × 4.6875 | 9 | 24 |
| Womp rat | 0.9375 | 0.5625 × 2.40625 | 10 | 19 |

The dimensions include horns, ears, feet, and tails. They describe the rest geometry, not swept animation bounds. The bantha has a stepped curled horn pair and hanging fleece. The dewback has a broad torso and a four-step tail. Its blanket, seat, and pommel belong to a separate `saddle` bone. Omitting that bone's geometry produces an unsaddled variant without changing the body. The manifest lists its attachment anchor.

## Generated files

Run from the repository root with Python dependencies managed by uv:

```sh
uv run resourcepack/KitActors.py
```

The generator writes only its own actor paths. It does not require the other art generators. It validates its output on every run. `--no-renders` skips image rendering. If local `resourcepack/split_pack.py` exists, the generator also checks the combined local pack.

An isolated worktree can check a separate source pack without writing there:

```sh
uv run --project resourcepack --locked python resourcepack/assets.py generate actors
```


The outputs are:

- `resourcepack/src/assets/blockcraft/models/item/sternenfront/kits/actors/` contains six static models and 37 bone models.
- `resourcepack/src/assets/blockcraft/items/sternenfront/kits/actors/` contains their 43 native item definitions.
- `resourcepack/src/assets/blockcraft/textures/item/sternenfront/kits/actors/` contains `utility.png` and `desert.png`.
- `resourcepack/bettermodel/kit_*.bbmodel` contains six source rigs with embedded atlases.
- `nexo/items/blockcraft_starwars_kit_actors.yml` contains optional single-display furniture definitions.
- `resourcepack/build/kit_actors/manifest.json` records geometry bounds, scales, bones, pivots, anchors, animation timing, interaction dimensions, and every native part.
- `resourcepack/build/kit_actors/validation.json` records checks and SHA-256 hashes of the generated assets and previews.
- `resourcepack/src/assets/bettermodel/items/model/kit_*.json` contains the 37 optimized BetterModel item routes. The generator also writes a staging copy under `resourcepack/build/kit_actors/pack-overlay/`.
- `resourcepack/build/kit_actors/contact_sheet.png` shows all six actors at the same pixel scale with 1.8-block reference bars. Each actor also has `*_front.png`, `*_side.png`, `*_three.png`, and `*_walk.gif`.

The build directory is ignored by Git. Regenerate or explicitly preserve these review artifacts during handoff. The PNGs use a CPU orthographic texture rasterizer. Each GIF samples the actual `.bbmodel` animation tracks and applies the outliner hierarchy. These previews show geometry and authored motion, not the Minecraft renderer or entity AI.

## Native geometry and UVs

The static display scale is 1 for the droids and womp rat, and 2 for the bantha and dewback. Every native element remains within `[-16,32]`. Its ground origin sits at model coordinate `[8,8,8]` before item-display centering.

Each animated bone remains one native model with full cuboids. There is no conversion to one cuboid per quad. Empty root bones have no native item route. Every source element has all six face keys. This batch enables all faces; there are no transparent textures or hidden-face placeholders in native output.

The source atlases use top-origin UVs. Native UV coordinates are source pixel UVs divided by four because the atlases are 64 pixels wide. The validation checks geometry reconstruction and corresponding face UVs for every static and bone model. Existing furniture generators and their UV conventions are untouched.

## Runtime registration

The `StarWarsModels` plugin registers passive `kit_` actors through `scenery.exhibits` in its configuration. Each entry supplies `kind`, `x`, `y`, `z`, optional `yaw`, and `animation` (`idle` or `walk`). Its `/arsenal spawn` command remains for imported combat creatures. See the plugin README for a complete example.

For an animated exhibition, stage the six source rigs in `BetterModel/models` using names `kit_astromech`, `kit_gonk`, `kit_mouse`, `kit_bantha`, `kit_dewback`, and `kit_womp_rat`. The source pack already contains their optimized bone routes. `verifyBetterModel` checks the routes before packaging, so ordinary staging needs no server export merge. The scratch `pack-overlay` remains available when assembling external exports.

Bone export follows the native imported-rig convention. For each bone, its scale is `max(1, maximum absolute distance from its pivot / 16)`. Native coordinates equal `(source - pivot) / scale + 8`. The manifest records the exact scale and pivot. BetterModel must retain its corresponding bone transforms when loading these replacement item routes. A custom display adapter must restore both scale and pivot; native bone items alone are not positioned actor parts.

The utility droids expose `idle`, `walk`, and `turn`. Wildlife also exposes `attack`, `damage`, and `death`. Walk loops last 0.8 seconds, idle loops last 3 seconds, and turn clips last 0.6 seconds. Wildlife attack lasts 0.6 seconds, damage lasts 0.3 seconds, and death holds its final pose at 1 second. `turn` is a visual gesture and does not steer an entity. An attack clip applies no damage. Utility droids have no attack or defeat behavior.

A parent adapter can use the existing BetterModel tracker and replay registration conventions. It must choose a passive or stationary carrier for exhibitions. No Husk pursuit, hostile spawning, health, damage, drops, or balance values are supplied by this prototype. Player-facing registration text belongs in the runtime plugin's German `messages.yml`.

For a static exhibition, use the manifest's `static_item` with a single item display and its `display_scale`. The optional Nexo IDs are `blockcraft_kit_<id>`. Their furniture definitions follow the existing floor-placement translation of `0,-0.5,0`. They use `NONE` display transform. Directly spawned displays need their ground position set by the caller. The assets classify into the Star Wars split pack because they live under `sternenfront`.

## Collision and interaction conventions

Raw static item displays have no entity collision. The generated Nexo entries provide interaction regions and authored body collision recipes. `FurnitureSolids` supplies stationary native colliders for these furniture versions. Animated actors use their gameplay runtime instead.

The manifest's interaction width is the larger of full X and Z extent. It describes a square selection area that includes tails and horns. Its center offset includes the forward or rearward extent, while the Nexo interaction origin uses ground Y. This broad selection region is unsuitable as a damage hitbox. The listed body anchors provide narrower interaction points for a parent adapter.

Animated entity movement collision, path clearance, damage hitboxes, and mounting remain runtime responsibilities. The rigs deliberately omit a BetterModel `hitbox` bone. The full visual footprint and height are reference dimensions, not movement collision settings. The saddle anchor does not implement riding or passenger placement.

## Verification limits

Generator checks cover native bounds, exact cuboid reconstruction, UV correspondence, opaque textures, item/model/texture closure, bone ownership, animation references, finite values, clip timing, and loop closure. Wheel rotations close modulo 360 degrees. Combined pack classification is checked when the support splitter is available.

Parent integration loaded all six rigs in BetterModel 3.6.0 and displayed their walk loops in a Minecraft 26.3 test client. The static versions and animated versions share the gallery. The full Java 25 build passes. MariaDB integration tests require `BLOCKCRAFT_TEST_DB_PORT` and were not enabled. Nexo actor placement, hostile behavior, damage clips and riding remain unverified.

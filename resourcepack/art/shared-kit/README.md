# Shared military kit prototype

The kit contains 19 pieces and four additional surface variants. The 23 native item routes share three 64 × 64 opaque atlases. Floor panels and low cover have clean, worn, and scorched versions. The worn and scorched models inherit their clean parent geometry.

`KitPixels.py` paints panel recesses, highlights, fasteners, vent slats and hazard stripes. Worn and scorched variants use clustered chips and soot patches. Texture dimensions, item routes, collision cores and door behavior stay the same.

`manifest.json` records every route, Nexo ID, actual mesh bounds, scale, module pitch, ground anchor, connector, barrier core, and assembly placement. `asset-sha256.json` lists every generated asset. `changed-files.txt` lists the complete handoff.

## Generate the assets

Run from the repository root with Python 3.12 or newer and uv:

```sh
uv run resourcepack/MilitaryKitArt.py
```

For an isolated source tree, supply the resourcepack directory that contains `ObjectiveArt.py`:

```sh
uv run --project resourcepack --locked python resourcepack/assets.py generate shared
```

The default helper location is beside the generator. It reuses the objective geometry and CPU rasterizer. `ModelSurfaces.py` removes hidden and competing face patches while preserving their UV positions.

Use `--pack`, `--output`, and `--nexo` to regenerate into scratch directories. Generation validates native bounds, UV rectangles, texture sizes, parent resolution, route selection, Nexo ID count, and barrier containment in solid model cuboids.

## Assemble scenery

Place item displays with `item_display: none`. Model coordinates `(8,8,8)` are the ground anchor. Front is +Z. Apply the existing yard half-turn cancellation once. Apply the manifest scale explicitly. Frame and panel use scale 2. Other pieces use scale 1.

Floor pieces tile at one block. Put supporting vanilla full blocks immediately below their anchors. The overlays rise at most 0.011719 blocks above that surface. Their Nexo definitions have no barriers. They add no raised collision.

Cover joins at one-block intervals. The corner return occupies the preceding Z cell. Frame exterior dimensions are 3 × 3 × 1 blocks. Its opening is 1 block wide and 2 blocks high. Frame barrier cores occupy both jambs and the lintel. The two central passage cells remain clear.

Place the door panel and frame at exactly the same ground position and yaw. They are separate furniture entities. The frame's interaction box occupies one jamb so it cannot intercept clicks in the opening. The panel's selection box extends slightly beyond its barrier faces so Minecraft can target it from either side. `door_open` and `door_jammed` are static visual variants. The jammed state uses smaller native colliders inside its raised panel.

The engine has a two-block visual length. Machinery uses one full barrier core inside its solid housing. Hoses, clamps, upper pistons, and decorative projections have no additional collision. The manifest specifies each core. The wall, ceiling, and landing lamps and the warning beacon have interaction boxes without solid collision. Align the wall lamp's `wall_back` connector with a supporting wall. Align the ceiling lamp's `ceiling_mount` connector with the ceiling plane.

The three assembly PNGs render the generated elements with their face UVs. They are software renders, not Minecraft screenshots. Floor support blocks are placement requirements and are omitted from those images. `manifest.json` includes the exact piece placements used by each render.

## Use the native door mechanic

Install `nexo/items/blockcraft_starwars_shared_kit.yml` through the existing Nexo configuration workflow. Give and place `blockcraft_shared_blast_frame` and `blockcraft_shared_door_closed`. The closed panel definition contains Nexo's existing `Mechanics.furniture.door` configuration.

The door uses `is_sliding: true`, `toggle_hitbox_on_open: false`, and `delay_hitbox_toggle: false`. Open properties raise its ground position by two blocks and reduce its Y scale from 2 to 1. The visual panel retracts into the lintel. Only the panel moves. The frame keeps its own seven barrier cores.

The installed jar's `FurnitureDoor(ConfigurationSection)` reads these keys and clones the furniture properties before applying `open_properties`. Nexo retains its interaction box while `FurnitureSolids` opens and closes the native core. Packet-only barriers are disabled for this kit so they cannot retain a stale closed doorway. `FurnitureDoorListener` invokes `toggleState` from its interaction listener. A Minecraft 26.3 client verified closed collision, opening, closing and passage from both sides. The frame stays fixed while the panel moves. Preserving an open state across a tracking or chunk reload remains unverified; authored scenery starts closed after restart.

`FurnitureSolids` mirrors authored barrier cores into real world blocks, including the door's closed state. Smaller shapes use invisible, stationary native colliders. Star Wars rays include those colliders. The service restores owned blocks and removes colliders through the furniture lifecycle. Raw displays have no collision.

Light elements use native `light_emission` and `shade: false`. Generated Nexo light definitions also produce real world illumination through `FurnitureSolids`. Unlit model variants remove surface emission when power is off. The four small decorative light fixtures need no collision helpers.

## Integrate the generator

The integrated `generateMilitaryKitArt` task is part of `:resourcepack:regenerate`:

```kotlin
val generateMilitaryKitArt = tasks.register<Exec>("generateMilitaryKitArt") {
    workingDir(layout.projectDirectory)
    commandLine("uv", "run", "MilitaryKitArt.py")
}
```

The split builder assigns paths containing `sternenfront` to the Star Wars layer. No pack enum or vanilla item override is required.

See `validation.json` for targeted generator checks and scratch split-builder results. The integrated full Java 25 build passes. Runtime exhibition evidence is recorded under `run/imported-hub/selected-kit-*`. No production deployment, commit or push was made for this kit.

# Lobby furniture

The supplied market, Halloween, and quest-board models have their own [installation and interaction guide](IMPORTED_MODELS.md).

The item definitions in `items/blockcraft_props.yml` use the original models and textures in
`resourcepack/src/assets/blockcraft`. `resourcepack/PropArt.java` regenerates both with
`./gradlew :resourcepack:generateProps`.

The network resource pack contains the models. Nexo's pack generation and dispatch are disabled here so the
proxy remains responsible for the pack offered to players. Each item has an explicit `Components.item_model`.

## Stage the licensed plugin

Supply your compatible Nexo jar when staging plugins:

```powershell
./gradlew stagePlugins -PnexoJar=C:/path/to/Nexo.jar
```

`BLOCKCRAFT_NEXO_JAR` provides the same path when the Gradle property is absent. The jar stays outside source
control. `stageNexo` copies the jar, item definitions, settings, and model assets into `build/nexo`.
`stagePlugins` includes that directory in `build/docker/paper/nexo`. Local `:paper:runServer` uses it too.
Without a jar, staging includes the definitions and assets, but custom furniture requires Nexo at runtime.

## Place furniture

Every map reads `blockcraft/scenery/furniture.yml` within its world bundle. The shared waiting lobby also reads
`worlds/waiting-lobby/furniture.yml`. Positions identify the air block above the supporting floor.
Nexo centers the model there. `yaw` is the direction the model's front faces, as for a player: 0 south, 90
west, 180 north, 270 east. Turn it away from the wall behind it.

```yaml
placements:
  bench:
    item: blockcraft_timber_bench
    at: {x: -195, y: 47, z: -307}
    yaw: 180
    variants:
      winter: blockcraft_winter_bench
```

`during` limits a placement to calendar occasions. `groups` limits it to server groups. `variants` chooses the
first matching occasion, otherwise `item` remains. Occasions follow `/event` and the network calendar.

The stall and tree use real barrier hitboxes. Their placements also specify `fallback-block` and
`fallback-height` so a client without the pack sees solid vanilla scenery at those hitboxes. Other models use
their vanilla item material as a fallback. A client sees them only before its pack has loaded, because the proxy
kicks whoever declines or fails it. Both bench variants have seats.
Nexo's model remains tracked while its rendering is hidden for players without the pack, so the seats still work.
The definitions set `properties.cullable: false` because Nexo's culling would otherwise reset that visibility.

Placements are recreated on startup and when their season changes. The service removes its furniture before
`/map save` and restores it afterward. It prevents players from breaking these authored placements.
Edit the placement file and restart the affected server to apply position changes.

## Seasonal blocks

Each lobby's `seasons.yml` defines inclusive `min` and `max` coordinates under `snow`, `freeze`, and
`keep-clear`. Winter snow covers suitable exposed surfaces within `snow`. Only exposed source water within
`freeze` becomes ice. `keep-clear` excludes positions from every physical change, including authored decorations.

`decorations` places its `to` block data at `at` while any listed `during` occasion is active. Its `from` value
must be air. `snow-layers` is the depth of the deepest drift: the cover wanders between one layer and that many across
the ground, the same on every load. The waiting island also sets `snowfall-radius`; the main
lobby already controls falling snow through its existing season settings.

Original block states are journaled in the runtime dimension's `data/blockcraft-seasons` directory before edits.
Season changes, shutdown, and map publication restore them. Startup recovers interrupted changes. A block edited
by a builder is preserved when it no longer matches the seasonal replacement. Save aborts if restoration fails.

On a local test network, `/event winter advent` enables snow, ice, and Christmas decorations. `/event none`
restores the ordinary map, and `/event auto` returns to the calendar. Physical blocks do not depend on the resource
pack, so they are the same for every player.

## Physical scenery

`FurnitureSolids` follows Nexo placement and removal. Generated `blockcraft_collision` recipes give structural pieces native backing blocks or glazing. Smaller equipment and static actors use invisible closed shulkers with AI disabled. Empty display anchors keep these colliders at the authored coordinates; unattached shulkers would snap to the block grid.

The service retains the previous block data and shares ownership where furniture overlaps. It restores only blocks that still match its placement. Existing solid foundations can be preserved by the recipe. Saving a map clears runtime furniture and its collision before publishing the template, then recreates them afterwards.

The lobby's grain cart, both bench variants, departure clock, gift pile and skate rack use coarse body collision.
The imported food tables and market counter have standing surfaces. Thin decoration stays passable, and bench seats
retain Nexo's seating interaction. Authored recipes are generated alongside their item definitions. Finished imported recipes are edited directly
in `items/blockcraft_imported.yml` and validated against their native models.

The shared kit disables Nexo packet barriers. Its door uses Nexo's sliding visual state and native world blocks for closed collision. Closing is refused while a living entity occupies the doorway. Legacy furniture with authored Nexo barriers receives matching native cores automatically. Star Wars weapon and visibility rays also intersect the smaller native colliders.

The isolated native-pathfinder check routed all five imported creature carriers around a closed two-block door.
An occupied doorway refused closing, and a client could close it once clear. These carriers retain their native Husk
body dimensions; the full animated mesh does not define navigation clearance. Native mob pathfinding around the
smaller collision entities remains unverified. The protected lobby cancels hostile targeting.

Small equipment uses coarse collision. The shared wall, ceiling, and landing lamps and the warning beacon are passable decorations with world lighting and no collision helpers. Equipment retains its standing height, and roof terraces have separate steps. One of each of the 61 kit assets needs 73 anchored collision cubes, or 146 helper entities. Native block cores need no helper entities. Only doors need recurring collision checks.

## World lighting

Generated luminous furniture declares `Mechanics.furniture.lights.lights`. `FurnitureSolids` places matching native `LIGHT` blocks so every player receives real block lighting. A blocked emitter uses an adjacent air cell. Overlapping lights share the brightest requested level. Removal restores the previous block data and respects later builder edits.

The kit generators also write unlit model variants. `FurnitureSolids.power` changes both the Nexo model state and native illumination. Ordinary lobby lanterns and candles use the same native lighting service. Saving a map removes owned lights with the furniture and restores them after publication.

Gameplay stays outside this service. Sternenfront's optional [map circuits](../paper/sternenfront/README.md#field-objectives) connect authored lights and doors to objective props.

Place the generated Nexo items or use a furniture layout. A raw item display supplies no physics. Thin details and decorative water remain passable. Animated actors retain their separate gameplay collision and damage rules.

Map furniture belongs in `blockcraft/scenery/furniture.yml`. Real lobby placements remain in the lobby world bundle.
Temporary asset exhibitions are retired. Star Wars assets load only for their game and replay profiles.

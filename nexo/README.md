# Lobby furniture

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

The main lobby reads `worlds/lobby/blockcraft/scenery/furniture.yml`. Round servers read
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
`fallback-height` so clients without the pack see solid vanilla scenery at those hitboxes. Other models use
their vanilla item material as a fallback. Both bench variants have seats.
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
restores the ordinary map, and `/event auto` returns to the calendar. The shared resource pack is optional;
physical blocks remain the same for every player.

# Imported Vanade maps

The `VanadeSicherung` backup supplies four overworld templates:

- `lobby` contains `Lobby/Neue_Lobby`, named Neue Lobby.
- `duell` contains `Duell1/Bucht`, including the waiting lobby and the arenas in that world.
- `quicksg` contains `QSG1/world`, named Mall.
- `emeraldrush` contains `TW1/world`, named Mine. The old plugin was called TradeWars.

The backup does not identify the builders. Each `blockcraft/map.yml` leaves `builders` empty.
BedWars reuses a copy of EmeraldRush's waiting island. Its two arena templates remain in
`bedwars/blockcraft/BedWars/maps`.

Zombies ships the original Außenposten arena as a saved Paper 26.2 world template.
Its map-owned configuration includes four purchasable wings, seven stations, and eight barricades.
The plugin can also generate the arena in a new empty world. See [Zombies setup](../paper/zombies/README.md).

## Configuration

All active locations use the runtime world name `world` and Bukkit's location serialization.
The lobby has its spawn, Duell, QuickSG, jump, EmeraldRush, BedWars, shop, premium, and shop NPC locations.
The BedWars arrival is centered over the gold block at `(-900, 30, 1579)`, with feet at Y=31.
The arrival faces east toward a flat wooden board on the marked stone wall. The board measures five blocks
wide, three high, and one deep. Three west-facing join signs at `(-889, 31, 1578..1580)` add BedWars to the
eight imported status signs. Its title sign is at `(-889, 32, 1579)`. The 17 iron bars at the front of this
alcove are removed. The original arch and floor remain in place, and the board's former rear-wall position
is restored. Follow the existing steps across the room to reach the signs. The four old TradeWars labels
elsewhere in the lobby read EmeraldRush.
The old source at commit `13cc6d0`, in `Lobby/src/me/blockcraft/lobby/Lobby.java` and
`Lobby/src/me/blockcraft/lobby/commands/CommandSetholo.java`, establishes the key conversions:

- `syndicate` becomes `duell`.
- `superjump` becomes `qsg`.
- `skypvp` becomes `jump`.
- `trapsg` becomes `rush`.
- `hol` becomes `shop`.
- `blaze` becomes `shop-npc`.

The eight signs from `Lobby/plugins/TeleportSigns/TeleportSigns.db` are in
`lobby/blockcraft/StatusSigns/signs.yml`. Duell, QSG, and TW instance targets become the
`duell`, `quicksg`, and `emeraldrush` groups.

Duell has a lobby spawn, four 1vs1 arenas, two SG arenas, and five SuperJump arenas.
The plugin's default map names match the backup's numbered arenas. The third SG arena,
Bergwerk, points to `LobbyMap`, which is absent from the backup. Its two original locations
are retained in `duell/blockcraft/legacy/Duell/locations.yml`. They are not active spawns,
so the existing map vote excludes that arena.

QuickSG has its eight player spawns, lobby, spectator, and both world border centers.
The saved border settings match the current defaults: disabled, with radii of 30 and 60.
The backup has no `DM1` through `DM4` locations. The current QuickSG game does not use those keys.
EmeraldRush has all eleven locations, including its six deathmatch spawns and the search,
shopping, and enchanting locations. Its mine had lost all but one emerald ore, because the old plugin never reset
its world; the plugin now places the ore itself every round (`ores` in its `config.yml`).

The templates keep only the chunks a game uses, finished once on Paper 26.3, so no chunk is converted again at a
start. Each world's `world_gen_settings.dat` rebuilds the rest without structures: the old superflat ground for the
lobby, QuickSG, and EmeraldRush, and open sea at the old sea level for Duell. Duell keeps the waiting lobby's
surroundings, Bucht, every arena island, and the islands in view of one. EmeraldRush keeps its lobby, mine, and
deathmatch; the villages the old superflat generated are gone.
BedWars' waiting island and arena maps and the Zombies arena keep only the chunks that hold blocks; their
generators leave everything else empty, and Zombies stores no nether or end.

The old HolographicDisplays database is retained under
`lobby/blockcraft/legacy/HolographicDisplays/database.yml` for reference. Its welcome and
shop entries point to the absent `Lobby3` world. Its premium entry points to Neue Lobby.
The current plugin uses entity displays for holograms and has no importer for that database.
The `legacy` directory is reference data and is not loaded by the plugins.

## Editable team display

The lobby's team room at `(-974, 27, 1650)` has twelve existing head and sign pairs.
Their positions and roster are in [team-board.yml](lobby/blockcraft/Lobby/team-board.yml).
The roster contains Aquitano as Owner. The remaining eleven slots are vacant.

Edit `members` using the slot keys already listed under `slots`:

```yaml
members:
  slot-1:
    name: Aquitano
    role: OWNER
  slot-2:
    name: AnotherPlayer
    role: BUILDER
```

Use Minecraft account names for skins. A slot without a member uses `empty-skin` and a vacant-place sign.
The `slots` section controls the layout independently of the roster. Additional slots need existing player
heads and signs at their configured block positions. The title and sign formatting come from Lobby's
`messages.yml`, using the network address and palette.

Edit the template's `blockcraft/Lobby/team-board.yml`, then run `/teamboard check` and `/teamboard apply`.
Both require `blockcraft.lobby.setspawn` and work from the console. Check validates every configured position
and roster entry without changing blocks. Apply stops before editing if validation fails. It updates the
heads, front sign text, and central title, and copies the input file into the active map.
Run `/map save` afterward to keep the rendered blocks with the template.

On the local map preview, edit `run/map-review/preview-worlds/lobby/blockcraft/Lobby/team-board.yml`.
Its saves remain in that preview directory until copied back to `worlds/lobby` deliberately.

## Lobby activity setup

All nine hidden sign secrets are present in the imported lobby and match the current secret names.
The Konami code, chicken-pet eggs, sleeping-player effects, and shooting stars do not need map positions.
Their conditions and settings are described in [Easter eggs and lobby activities](../EASTER_EGGS.md).

The existing course starts on the oak plate at `(-667, 8, 1601)` and finishes on the added oak plate at
`(-717, 50, 1632)`. The adjacent sign identifies the finish on both faces. A top-ten display stands nearby.
`activity-settings.yml` overrides the plugin's activity settings for this map. This course uses zero checkpoints
because no checkpoint markers exist in the imported build. Completing a timed run records its best time and
grants the permanent "Im Ziel" achievement with 100 XP once. Clicking the sign alone grants nothing.

The pond and Easter hiding places still need configuration. Choose a fishable pond and register at least ten
distinct hiding places using the [setup commands](../EASTER_EGGS.md#configure-the-lobby-map).
`/lobbyset check` reports the remaining setup gaps. On the running preview, only the pond and missing Easter
hiding places remain. Use `/map save` after recording positions.

## BedWars waiting island

The waiting-world copy keeps 49 chunks around EmeraldRush's built island, with sections 2 through 7 and
all 44 block entities. Unrelated ground below the island and its two horses are excluded. New terrain generates
as void. Both root and Paper dimension spawn metadata point to `(-194, 47, -318)`.
`bedwars/blockcraft/BedWars/lobby.yml` places the arrival, CPS tester, four team selectors, and two member displays
per team. The selectors occupy four existing alcoves beside the interior path, facing that path.
All thirteen NPC positions have natural support and four blocks of clearance. Walking routes from spawn
reach every selector's front at Y=47. The CPS tester stands at `(-183.5, 47, -303.5)`, off the arrival path.
The old waiting area's jump locations are removed because this island has no configured BedWars course.
The nested arena pool is unchanged. Client inspection still needs to check the approach to the selectors,
joining teams, displayed player skins, CPS interactions, map voting, and round transitions.

## Inspect the maps in 3D

Run `uv run dev/map-inspector/map_inspector.py` from the repository root, with Java 25 available through
`JAVA_HOME` or `PATH`. Open <http://localhost:8100> to inspect saved terrain and configured-location markers.
The viewer reads templates without installing a Minecraft plugin or restarting a server. See
[local map inspection](../dev/map-inspector/README.md) for rendering the preview copies and other worlds.

## Importing an old 1.8 map

`map-import` turns a world folder from an old server into a template in this layout, one command per map. Run it
from the repository root with Java 25:

```sh
./gradlew importMap -Psource=/path/to/OldWorld -PdryRun
./gradlew importMap -Psource=/path/to/OldWorld -Ptarget=worlds/<group>
```

The source is the old world folder with its `level.dat`. The task reads the pre-26.1 layout, with `region/` next to
`level.dat` as in 1.8, and the 26.x layout. It never changes the source. It works in `run/map-import` in three steps:

1. It copies the overworld and the map item pictures, switches structures off in the copy, and records every stored
   chunk and every entity UUID.
2. It starts Paper 26.3 (the build `:paper:runServer` uses) with a helper plugin. The plugin freezes the game and
   holds each recorded chunk until it ticks. Paper then converts the chunk to status `full` with the world's own
   generator and applies the upgrade data and post processing, the shape fixes against neighbouring blocks. Nothing
   ticks while the game is frozen: water does not flow, sand does not fall, and mobs do not move. The server stops and
   saves the chunks.
3. It keeps the chunks the mode selects. Loose item entities are dropped. So is any entity whose UUID the old world
   did not store, such as a mob the generator spawned. Everything else stays, including item frames, armor stands,
   and the map's animals.

Without a target, or with `-PdryRun`, the task only reports. The report shows the superflat ground, the chunk counts,
and the kept chunks grouped into areas, each printed as a `-Pbounds` box. It also counts entities and chunks that
still hold scheduled block or fluid updates. The finished world is reused while the source is unchanged, so a dry
run and the real run convert only once. Delete `run/map-import/finished` to convert again. A failed conversion stops the task,
and so does a recorded chunk that is not `full`. Both leave the target unchanged.

| Mode   | Keeps                                                                 | New chunks generate as                  |
|--------|-----------------------------------------------------------------------|-----------------------------------------|
| `flat` | chunks that differ from the superflat ground, its biome, or hold a block entity or entity | the same superflat ground |
| `void` | chunks with any block, block entity or entity                         | void                                    |
| `all`  | every chunk                                                           | the old generator, without structures   |

`flat` is the default for a superflat world. Other worlds need an explicit mode. `void` suits maps built over
nothing, including those made with a void generator plugin. `all` suits natural terrain.
`-Pbounds=x1,z1,x2,z2;...` limits any mode to these block boxes, and `-Pmargin=<chunks>` widens them. Use bounds
when an old superflat generator built villages, because each village is a small separate area in the report. In
natural terrain, the bounds and margin decide how much original land stays around the build. The old generator
regenerates terrain beyond them.

A dimension (`-Playout=dimension`) always uses `void`, because the server loads it over void: a superflat chunk left
out would be a hole, not ground. Trim it with `-Pbounds` instead.

The target receives `region/`, `entities/`, `poi/`, and the generator in
`dimensions/minecraft/overworld/data/minecraft/world_gen_settings.dat`, with structures off. It receives Paper's
dimension metadata in `data/paper` only when the target lacks it, because Paper otherwise migrates the world at
every start. The same applies to `level.dat` and the map pictures in `data/minecraft/maps`. Game rules and other
data the finishing server wrote stay in `run/map-import`. When the target already holds a map, the task replaces
its chunks and generator and keeps everything else, such as `level.dat` with the spawn and the `blockcraft/`
directory.

A BedWars pool map is one dimension folder, `maps/<key>/world`. The key must use lowercase letters, digits, `_`,
`.`, or `-`:

```sh
./gradlew importMap -Psource=/path/to/Castle -Playout=dimension \
  -Ptarget=worlds/bedwars/blockcraft/BedWars/maps/castle/world
```

A dimension cannot hold map item pictures, so the report warns when the old world had them.

Afterwards:

- Look at the result before committing. A level layout template renders with
  `uv run dev/map-inspector/map_inspector.py --worlds run/map-import --maps template`. To walk it, run a server on
  the target as in the main README's "One server on the host", for example `./gradlew :paper:runServer -Pgame=<group>`.
- Check signs, heads, chests, item frames, and water that the report lists under scheduled updates.
  Paper's conversion also removes blocks that cannot stand where they are, such as an unsupported torch, and their
  drops do not enter the map.
- Old plugin data is not imported. Place spawns and locations again with the game's `/setup`, name the map in
  `blockcraft/map.yml`, and publish it with `/map save`. A BedWars pool map needs its `map.yml` before it can be
  voted. Use `/setup map <key> edit` to load the imported world and create an empty `map.yml`. Then use `spawn`,
  `bed`, `shop`, `spawner`, `spectator`, `border`, `name`, and `icon`. `info` lists missing settings, and `save`
  stores the world in the pool. After that, run `/map save`.
- Region files go through Git LFS, as every template's do.

## Storage and validation

All 103 source overworld region files and their 48,070 chunks are retained. The initial
import occupies about 197 MiB. No area of a build was trimmed.
The old shop villager and its two title armor stands were removed from the imported lobby.
The current Lobby plugin creates their replacements at the saved `shop-npc` location.
Every other source entity is retained.
The templates use the Paper 26.2 layout under `dimensions/minecraft/overworld`, with world
data and `level.dat` at the template root. Region, entity, and POI files use the repository's
existing Git LFS rule.

The temporary copies were force-upgraded with the cached Mojang 26.2 server, then loaded
and stopped cleanly with cached Paper 26.2 build 133. Paper's `--forceUpgrade` option reports
that world upgrades are unimplemented in this build. The compose files currently pin build 134.
Every source chunk coordinate was checked against the imported overworld, and the original
files were checked against their SHA-256 hashes after conversion. All 54 active locations
resolve to retained chunks, and all eight imported sign positions contain sign blocks.
An independent review decoded all chunks and checked entity positions, container contents,
and the conversion of old flower-pot and note-block records. A separate JVM check loaded
all 54 locations and eight signs through the current plugin classes with a stub Bukkit world.
The initial Paper checks loaded no Blockcraft plugins. A subsequent local Docker preview loaded the
actual plugin stack on pinned Paper build 134, registered all five backend groups, and loaded all eight
status signs. Multiplayer gameplay still needs the checks below.

Player records, statistics, session locks, legacy `uid.dat` files, old level backups, the Nether,
and the End are excluded. Newly generated Paper world UUID metadata remains in the templates.
Plugin jars, logs, permissions, customer records, and database credentials
from the backup are excluded. The original backup is unchanged.

## Accepted layout and gameplay checks

The imported layouts and the final lobby board placement have been accepted on the local preview.
The pond and Easter hiding places remain unset. Acceptance of the layout does not replace the multiplayer
round checks below.

- Check the lobby warps, signs, shop NPC, and the jump landing. The saved jump point at Y=7.5 stands
  on bottom stone-brick slabs at Y=7. Its headroom is clear.
- Confirm that the shop has one villager and one current name card after startup and a
  periodic NPC respawn.
- Inspect the gold-block BedWars navigator arrival. Configure the pond and Easter hiding places.
- Complete the lobby course from its start plate. Check the finish sign, best time, leaderboard, and
  first-completion achievement. Repeat it to confirm the achievement pays only once.
- Outside `/build`, right-click both sides of an ordinary sign. The editor must stay closed in survival and
  creative mode. Secret signs and status signs must still respond to their normal clicks.
- Check for old branding and legacy armor stand holograms in the build. Place the current
  game and leaderboard holograms with `/setholo` where needed. `/teamboard apply` replaces the legacy
  title and link on the sign at `(-974, 27, 1650)`.
- Choose new firework-pad positions if that interaction is wanted. The default `fireworks`
  coordinates in the Lobby plugin configuration belong to another lobby.
- Supply `LobbyMap` to restore Duell's third SG arena, or keep it unavailable.
- Add the builders to each `blockcraft/map.yml` when their identities are known.
- Use `/map save` after map or setup changes to publish them to the template.

These checks did not start or change a production server.

## Zeitkapsel exhibits

The `archive` template holds an empty overworld: BedWars' `level.dat` and world generation settings without any
chunks, so the server restores the bundle at every start and generates nothing but void. The entrance hall is built
in code. The exhibits live apart from it, one directory each under `archive-exhibits/<key>/`, which the server reads
in place and copies a world out of only when someone visits it:

```
exhibits/neue_lobby/
  exhibit.yml
  world/          the old map as a dimension folder (region/, entities/, poi/, data/), like a BedWars pool map
```

```yaml
name: Neue Lobby
origin: Vanade                # the old server; the guide groups exhibits by it
year: 2018                    # optional; eras and exhibits sort oldest first
description: Die Lobby, mit der Vanade öffnete.
icon: grass_block             # the item the guide shows; paper when unknown
arrival: {x: -900.5, y: 31.0, z: 1579.5, yaw: -90.0}    # optional; else the world's spawn
checkpoints:
  - name: Teamraum
    description: Hier hingen die Köpfe des Teams.
    at: {x: -974.5, y: 27.0, z: 1645.5, yaw: 180.0, pitch: 10.0}
```

To add an exhibit after importing an old map:

1. Import the map as a dimension folder with `./gradlew importMap -Psource=<old world> -Playout=dimension
   -Ptarget=worlds/archive-exhibits/<key>/world` (see "Importing an old 1.8 map"). The key may only hold a-z, 0-9,
   `_`, `.` and `-`.
2. Write `archive-exhibits/<key>/exhibit.yml` with at least `origin`. The guide leaves out a directory without
   `exhibit.yml` or `world/`; one with an invalid key or without `origin` is left out and named in the server log.
3. Start the archive server, travel to the exhibit and set its arrival and checkpoints with `/setup`; they land
   in `exhibit.yml` at once. Everything beyond the build stays void.
4. Commit the template; its region files go through git-lfs.

Neither a start nor `/map save` copies the exhibits, so many old maps cost nothing until they are visited. Block
edits inside an exhibit are not saved. The text in `exhibit.yml` shows as written, without MiniMessage tags.

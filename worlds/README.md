# World templates

Every server group runs a map from `worlds/<group>`. A start replaces the world folder with a copy of the template,
so a restart also resets the map. `/map save` publishes the running world back into the template.
[Maps](../README.md#maps) in the main README describes `map.yml` and how a save works.

A level template keeps `level.dat` and shared world data at its root, and its overworld under
`dimensions/minecraft/overworld`. BedWars and TTT pool maps and archive exhibits store one dimension directly in their
`world/` directory. Paper's dimension metadata stays with each template, so the server can load it without repeating
a world migration. Region, entity and POI files go through Git LFS, so run `git lfs install` once before committing a
map.

Authors, sources and licences of the maps are in [CREDITS.md](../CREDITS.md#maps-and-worlds).

## Change a map

1. Run the server on its template: `./gradlew :paper:runServer -Pgame=<group>`, without `-Pgame` for the lobby
   ([One server on the host](../README.md#one-server-on-the-host)). The archive needs Compose, which mounts its
   exhibits.
2. Run `/build`, which lifts the block protection for you, and edit the map.
3. Place positions with the game's `/setup`, or edit the files under the template's `blockcraft/` where a game has no
   command for them.
4. Run `/map save`. An edit without it is gone at the next restart. As a player standing in the shared waiting world,
   the command publishes [the waiting island](#shared-waiting-island) instead.
5. Look at the result in the [3D viewer](../dev/map-inspector/README.md) and in a client. See
   [Gameplay checks](#gameplay-checks) for the lobby.
6. Commit the template like any other change.

## Add a map

- **A new server group:** import a world into `worlds/<group>` as below, or start from a template without a
  `level.dat`, which leaves the server's own world alone until the first `/map save`. The group also needs a service
  in `docker-compose.yml` that mounts the template.
- **A BedWars pool map:** see the end of [Importing an old 1.8 map](#importing-an-old-18-map).
- **A TTT pool map:** see [The maps](../paper/ttt/README.md#the-maps) in the TTT README.
- **A Zeitkapsel exhibit:** see [Zeitkapsel exhibits](#zeitkapsel-exhibits).

Then add the map's source and author to [CREDITS.md](../CREDITS.md#maps-and-worlds), or mark them as unknown.

## Importing an old 1.8 map

`map-import` turns a world folder from an old server into a template in this layout, one command per map. Run it
from the repository root with Java 25:

```sh
./gradlew importMap -Psource=/path/to/OldWorld -PdryRun
./gradlew importMap -Psource=/path/to/OldWorld -Ptarget=worlds/<group>
```

The source is the old world folder with its `level.dat`. Both the pre-26.1 layout, with `region/` next to `level.dat`
as in 1.8, and the 26.x layout work. The task never changes the source. It works in `run/map-import` in three steps:

1. It copies the overworld and the map item pictures and switches structures off in the copy, so the finishing server
   adds no villages or temples.
2. It starts Paper 26.3 with a helper plugin that freezes the game and holds each stored chunk until Paper has
   converted it to status `full`. Nothing ticks while the game is frozen: water does not flow, sand does not fall, and
   mobs do not move.
3. It keeps the chunks the mode selects. Loose item entities are dropped. So is any entity the old world did not
   store, such as a mob the generator spawned. Everything else stays, including item frames, armor stands, and the
   map's animals.

`-PdryRun` only reports, and a run without it needs `-Ptarget`. The report prints the kept chunks grouped into areas,
each as a `-Pbounds` box, and the chunks that still hold scheduled block or fluid updates. The finished world is
reused while the source is unchanged, so a dry run and the real run convert only once. Delete `run/map-import/finished`
to convert again. A failed conversion leaves the target unchanged.

| Mode   | Keeps                                                                 | New chunks generate as                  |
|--------|-----------------------------------------------------------------------|-----------------------------------------|
| `flat` | chunks that differ from the superflat ground, its biome, or hold a block entity or entity | the same superflat ground |
| `void` | chunks with any block, block entity or entity                         | void                                    |
| `all`  | every chunk                                                           | the old generator, without structures   |

`flat` is the default for a superflat world. Other worlds need an explicit `-Pmode`. `void` suits maps built over
nothing, including those made with a void generator plugin. `all` suits natural terrain.
`-Pbounds=x1,z1,x2,z2;...` limits any mode to these block boxes, and `-Pmargin=<chunks>` widens them. Use bounds
when an old superflat generator built villages, because each village is a small separate area in the report. In
natural terrain, the bounds and margin decide how much original land stays around the build, and the old generator
regenerates terrain beyond them.

A dimension (`-Playout=dimension`) always uses `void`, because the server loads it over void: a superflat chunk left
out would be a hole, not ground. Trim it with `-Pbounds` instead.

When the target already holds a map, the task replaces its chunks and generator and keeps everything else, such as
`level.dat` with the spawn and the `blockcraft/` directory. A new target also receives `level.dat` and the map pictures.
A target without Paper's `data/paper` receives it, because Paper otherwise migrates the world at every start. Game
rules and other data the finishing server wrote stay in `run/map-import`.

A BedWars pool map is one dimension folder, `maps/<key>/world`. The key must use lowercase letters, digits, `_`,
`.`, or `-`:

```sh
./gradlew importMap -Psource=/path/to/Castle -Playout=dimension \
  -Ptarget=worlds/bedwars/blockcraft/BedWars/maps/castle/world
```

A dimension cannot hold map item pictures, so the report warns when the old world had them.

Afterwards:

- Look at the result before committing. A level template renders with
  `uv run dev/map-inspector/map_inspector.py --worlds run/map-import --maps template`. To walk it, run a server on
  the target, for example `./gradlew :paper:runServer -Pgame=<group>`.
- Check signs, heads, chests, item frames, and water that the report lists under scheduled updates.
  Paper's conversion also removes blocks that cannot stand where they are, such as an unsupported torch, and their
  drops do not enter the map. [The GommeHD import](#gommehdnet-star-wars-hypixel-and-twerion) lists what else a legacy
  conversion lost.
- Old plugin data is not imported. Place spawns and locations again with the game's `/setup`, name the map in
  `blockcraft/map.yml`, and publish it with `/map save`. A BedWars pool map needs its `map.yml` before it can be
  voted. Use `/setup map <key> edit` to load the imported world and create an empty `map.yml`. Then use `spawn`,
  `bed`, `shop`, `spawner`, `spectator`, `border`, `name`, and `icon`. `info` lists missing settings, and `save`
  stores the world in the pool. After that, run `/map save`.

## Gameplay checks

Run these checks after changing the imported maps or their setup data.

- Check the lobby warps, signs, shop NPC, and the jump landing. The saved jump point at Y=7.5 stands
  on bottom stone-brick slabs at Y=7. Its headroom is clear.
- Confirm that the shop has one villager and one current name card after startup and a
  periodic NPC respawn.
- Inspect the gold-block BedWars navigator arrival. Configure the pond.
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

## Lobby

### Editable team display

The lobby's team room has twelve existing head and sign pairs. Their positions and roster are in
[team-board.yml](lobby/blockcraft/Lobby/team-board.yml). Use Minecraft account names for skins. Additional slots need
existing player heads and signs at their configured block positions.

1. Edit the template's `blockcraft/Lobby/team-board.yml`.
2. Run `/teamboard check`, which validates the file without changing blocks.
3. Run `/teamboard apply`, which stops before editing if validation fails, and copies the file into the active map.
4. Run `/map save` to keep the rendered blocks with the template.

### Lobby activity setup

The hidden sign secrets are present in the imported lobby and match the current secret names.
The Konami code, chicken-pet eggs, sleeping-player effects, and shooting stars do not need map positions
([Easter eggs and lobby activities](../EASTER_EGGS.md)).

The existing course starts on an oak plate of the imported build and finishes on an added oak plate, with an adjacent
sign. It uses zero checkpoints because no checkpoint markers exist in the imported build. Clicking the sign alone
grants nothing.

The pond still needs configuration. Choose a fishable pond and register it with the
[setup commands](../EASTER_EGGS.md#configure-the-lobby-map). `/lobbyset check` reports the remaining setup gaps.

## Shared waiting island

`waiting-lobby/world` is the single editable source for every round-based game. KnockbackFFA has no waiting phase.
Each server copies the island into its own `blockcraft:waiting_lobby` dimension at startup. Players stay in their
game's queue, while the map and its decoration files are shared.

Compose and the local Paper task set `BLOCKCRAFT_WAITING_TEMPLATE`. Without it, the games retain their
existing waiting locations. `waiting-lobby/lobby.yml` holds the shared arrival, the CPS tester, the team selectors and
their member displays, and the `leaderboard` point. Edit that file for shared positions and restart the game servers,
which read it at startup. BedWars hides its `/setup
lobby` commands while the shared template is active.

Run `/map save` as a player standing in the shared waiting world to publish that world back to the shared
template. From the main game world or console, the command still publishes the game's map. Restart the other
game servers to load the changed template. Publish from one builder server at a time.

`waiting-lobby/furniture.yml` and `lobby/blockcraft/scenery/furniture.yml` place the Nexo models. Their adjacent
`seasons.yml` files define real snow, ice, and seasonal block decorations. The original blocks return before map
publication and when the occasion ends. See [furniture setup](../nexo/README.md) for the licensed plugin staging and
model definitions.

The island comes from EmeraldRush's map. The copy keeps 49 chunks around the built island, with sections 2 through 7.
Unrelated ground below the island and its two horses are excluded. New terrain generates as void. Both root and Paper
dimension spawn metadata point to `(-194, 47, -318)`. The imported template omits Paper's copied world identity so it
can load alongside the original BedWars world. The old waiting area's jump locations are removed because this island
has no configured BedWars course.

The selectors occupy four existing alcoves beside the interior path, facing that path. The CPS tester stands off the
arrival path. The thirteen NPC positions were checked for natural support and four blocks of clearance, and walking
routes from the spawn reach every selector's front at Y=47. Client inspection still needs to check the approach to the
selectors, joining teams, displayed player skins, CPS interactions, map voting, and round transitions.

## Imported Vanade maps

The `VanadeSicherung` backup supplies four overworld templates and the waiting island:

- `lobby` contains `Lobby/Neue_Lobby`, named Neue Lobby.
- `duell` contains `Duell1/Bucht`, including the waiting lobby and the arenas in that world.
- `quicksg` contains `QSG1/world`, named Mall.
- `emeraldrush` contains `TW1/world`, named Mine. The old plugin was called TradeWars.

The backup does not identify the builders, so each `blockcraft/map.yml` leaves `builders` empty. Player records,
statistics, session locks, legacy `uid.dat` files, old level backups, the Nether, and the End are excluded. So are the
backup's plugin jars, logs, permissions, customer records, and database credentials. The original backup is unchanged.

### Trimming

The templates keep only the chunks a game uses, finished once on Paper 26.3, so no chunk is converted again at a
start. Each world's `world_gen_settings.dat` rebuilds the rest without structures: the old superflat ground for the
lobby, QuickSG, and EmeraldRush, and open sea at the old sea level for Duell. Duell keeps the waiting lobby's
surroundings, Bucht, every arena island, and the islands in view of one. EmeraldRush keeps its lobby, mine, and
deathmatch; the villages the old superflat generated are gone.

### Converted configuration

All locations use the runtime world name `world` and Bukkit's location serialization. The old source at commit
`13cc6d0`, in `Lobby/src/me/blockcraft/lobby/Lobby.java` and `Lobby/src/me/blockcraft/lobby/commands/CommandSetholo.java`,
establishes the key conversions for the lobby's `locations.yml`:

- `syndicate` becomes `duell`.
- `superjump` becomes `qsg`.
- `skypvp` becomes `jump`.
- `trapsg` becomes `rush`.
- `hol` becomes `shop`.
- `blaze` becomes `shop-npc`.

Eight of the status signs come from `Lobby/plugins/TeleportSigns/TeleportSigns.db`. Duell, QSG, and TW instance
targets became the `duell`, `quicksg`, and `emeraldrush` groups.

The BedWars arrival is centered over the gold block at `(-900, 30, 1579)` and faces east toward a board on the marked
stone wall, with its signs at x=-889. The 17 iron bars at the front of this alcove are removed. The original arch and
floor remain, and an earlier board's rear-wall position is restored. Follow the existing steps across the room to reach
the signs.

The third Duell SG arena, Bergwerk, points to `LobbyMap`, which is absent from the backup. Its two original locations
are retained in `duell/blockcraft/legacy/Duell/locations.yml`. They are not active spawns, so the arena stays out of
the vote.

The backup has no `DM1` through `DM4` locations for QuickSG. `/setup` still offers those keys, but the round does not
read them.

EmeraldRush's mine had lost all but one emerald ore, because the old plugin never reset its world. The plugin now
places the ore itself every round (`ores` in its `config.yml`).

### Old plugin data

`lobby/blockcraft/legacy/HolographicDisplays/database.yml` is the old HolographicDisplays database, kept for reference.
Its welcome and shop entries point to the absent `Lobby3` world, and its premium entry to Neue Lobby. The current
plugin uses entity displays for holograms and has no importer for that database. The `legacy` directories are reference
data and are not loaded by the plugins.

## Zombies and Sternenfront

Zombies ships the original Außenposten arena as a saved template and stores no nether or end. The plugin can also
generate the arena in a new empty world ([Zombies setup](../paper/zombies/README.md)).

Sternenfront ships Wrack von Tessar as a saved template with no nether or end
([the map section](../paper/sternenfront/README.md#the-map-wrack-von-tessar)). Like the maps below it keeps only the
chunks that hold blocks, including the far mesas behind the canyon walls, which are part of the view. Its world
generates air beyond them.

BedWars' waiting island and arena maps and the Zombies arena keep only the chunks that hold blocks, and their
generators leave everything else empty.

## Zeitkapsel exhibits

The `archive` template holds an empty overworld: BedWars' `level.dat` and world generation settings without any
chunks, so the server restores the bundle at every start and generates nothing but void. The entrance hall is built
in code. The exhibits live apart from it, one directory each under `archive-exhibits/<key>/`. The server reads them in
place and copies a world out only when someone visits it. Neither a start nor `/map save` copies them, so old maps cost
nothing until they are visited. [The Zeitkapsel README](../paper/archive/README.md) describes the guide and notes.

```
archive-exhibits/neue_lobby/
  exhibit.yml
  world/          the old map as a dimension folder (region/, entities/, poi/, data/), like a BedWars pool map
```

```yaml
name: Neue Lobby
origin: Vanade                # the old server
year: 2018                    # optional; maps sort oldest first, undated last
category: Lobbys              # optional; defaults to Sonstiges in the guide
description: Die Lobby, mit der Vanade öffnete.
icon: grass_block             # the item the guide shows; paper when unknown
arrival: {x: -900.5, y: 31.0, z: 1579.5, yaw: -90.0}    # optional; else the world's spawn
checkpoints:
  - name: Teamraum
    description: Hier hingen die Köpfe des Teams.
    at: {x: -974.5, y: 27.0, z: 1645.5, yaw: 180.0, pitch: 10.0}
figures:                      # optional; who stood in the map, without what they did
  - name: Händler
    at: {x: 67.23, y: 63.0, z: 47.52, yaw: -127.27}
    skin: ewogICJ0aW1l...     # the signed textures property of the old profile
    signature: kEpcT3Br...
notes:                        # optional; notes that come with the map instead of the database
  - text: 'Der Händler kaufte und verkaufte Baublöcke (/shop blocks).'
    author: Aquitano
    date: 2026-10-31
    at: {x: 69.5, y: 63.0, z: 49.5, yaw: 156.1}    # where the reader stands and looks
```

To add an exhibit after importing an old map:

1. Import the map as a dimension folder with `./gradlew importMap -Psource=<old world> -Playout=dimension
   -Ptarget=worlds/archive-exhibits/<key>/world` (see "Importing an old 1.8 map"). The key may only hold a-z, 0-9,
   `_`, `.` and `-`.
2. Write `archive-exhibits/<key>/exhibit.yml` with at least `origin`. The guide leaves out a directory without
   `exhibit.yml` or `world/`; one with an invalid key or without `origin` is left out and named in the server log.
3. Start the archive server with `docker compose up -d archive`, because `exhibits.directory` is the `/exhibits` mount
   Compose provides. Travel to the exhibit and set its arrival and checkpoints with `/setup spawn` and
   `/setup point add <name>`. They land in `exhibit.yml` at once, and everything beyond the build stays void.
4. Commit the template.

Block edits inside an exhibit are not saved. The text in `exhibit.yml` shows as written, without MiniMessage tags.

A figure stands where `at` puts it, in the old skin and under the old name, while its exhibit is loaded. It does not
move, talk or open a shop, so notes tell what it once did. Citizens kept an NPC's skin in `plugins/Citizens/saves.yml`
as `textureRaw` and `signature`; those two are `skin` and `signature`.

A note in `exhibit.yml` hangs 1.5 blocks ahead of its `at` at eye height, so leave about two blocks of air above that
point for the text. The exhibit's menu lists these notes first and offers no delete for them. They change only in the
file, and a new server or an emptied database has them at once.

### AQ Survival

`aq_survival` and `novigrad` come from AQ Survival, a Paper 1.16.4 survival server of 2020 and 2021:

- `aq_survival` holds two boxes of its overworld: the town around the old spawn with its harbor
  (`-80,-208,399,191`) and Aquitano's base in a desert village (`-912,-624,-577,-289`). The seven Citizens
  shopkeepers stand in it as figures, and its notes tell what they did and what the old welcome and farm world signs
  said.
- `novigrad` holds the island of Novigrad, Elysium Fire's recreation of the city from The Witcher 3, which the server
  loaded as a world of its own (`96,256,1903,1855`).

### ClayMC

The three ClayMC lobbies come from `Blockcraft Server/2/ClayMC-Lobbys`, imported on 2026-10-31 with
Paper 26.3 build 234. Their exhibit names and year follow the supplied folder names. All three `level.dat` files
have a `LastPlayed` date of 2020-11-15, so that timestamp does not establish when the lobbies were built or used.
The season of `Unknown-2019` remains unknown.

- `claymc_summer_2019` comes from `Sommer-2019` (`clay123`). It keeps every nonempty chunk, including the
  connected islands, the separate island copy to the east, and the small build fragment to the southeast.
- `claymc_winter_2019` comes from `Winter-2019` (`alobby`). It keeps the built chunks within
  `-96,-128,127,95`. The two detached areas of untouched superflat ground are excluded.
- `claymc_unknown_2019` comes from `Unknown-2019` (`sdasdsad`). It keeps the chunks containing blocks above
  the original superflat ground within `-384,-368,255,159`, except chunk `(-19,9)`, a clipped generated village
  house. The round lobby, detached trees, building samples, and glass surface remain. A scratch copy of these
  original chunks was passed to `importMap`; empty chunks, unchanged superflat ground, and generated villages
  were left out before conversion.

All original armor stands and item frames in the selected builds remain, with their stored equipment, names, and
items. Loose item drops, player records, village data, and unused dimensions are excluded. The Downloads backups remain
unchanged.

An isolated Paper run checked discovery through `ExhibitPool`, loading through `TemplateDimensions`, support
and headroom at all arrivals and checkpoints, and void generation beyond each build. Client inspection is still needed
for skins, signs, armor-stand poses, and lighting. Scheduled block and fluid updates remain as reported by `importMap`.

`claymc_lobby_2020` is a fourth, separate lobby, imported on 2026-10-31 from `Maps/Lobby World` in
`Blockcraft Server/2/ClayMC-7.0-7.1` (`ClayMCLobby`). None of its chunks matches one of the 2019 lobbies. It dates
itself: a hologram over the team figures reads "Stand: 08.04.2020", a sign reads "Covid-19 / Ich war dabei", and
`LastPlayed` is 2020-04-21. Its name follows the backup's folder, and a sign credits the ClayMC-Bauteam. It keeps
the built chunks of its round island (`-160,-96,159,207`). All armor stands and item frames remain, and 72 loose items
are gone. Its arrival is the old world spawn. Five secret signs, numbered 1 to 4 and 6, are left for visitors to
find. A Paper run checked the exhibit's discovery, its loading, the support and headroom at the arrival and every
checkpoint, and void beyond the build.

### GommeHD.net, Star Wars, Hypixel, and Twerion

Nine exhibits were imported on 2026-11-03 with Paper 26.3 build 235.
[CREDITS.md](../CREDITS.md#gommehdnet-star-wars-hypixel-and-twerion) names each source archive and its source and kept
chunk counts. The archives lie in `Downloads/blockcraft/`, except the two world downloads,
`.minecraft/saves/hypixel zombies-2026-10-24_(2).zip` and `.minecraft/saves/twerion lobby-2026-10-22_(2).zip`. The
source ZIPs remain unchanged.

`run/capsule-20261103`, ignored by Git, is the import workspace, including the ZIP hashes, chunk audits, and smoke
results. The complete pre-cleanup Star Wars world is retained under
`run/capsule-20261103/before-cleanup`, with SHA-256 inventories.

**What was cut.** Empty chunks are omitted everywhere. The cleanup also removes 321 plain flat-ground chunks from Star
Wars and 1,089 from Gommunity. Star Wars and Gommunity keep a chunk
of plain ground beside their non-flat content. The other earlier exhibits already contain no empty
or plain-flat chunks. New chunks beyond each exhibit generate as void. Player data, unused dimensions, and loose item
drops are excluded.

**Dates.** The dates in the Hypixel and Twerion descriptions are download dates. The construction years are unknown, so
none of these nine exhibits sets `year`. Gommunity's saved level name mentions 2017, while the supplied archive names it
Normal 2020. Its display name follows the archive.

**Conversion.**

- GommeHD.net and the five additional GommeHD archives use the pre-1.9 numeric block format.
- Star Wars mixes that format with 1.14.4 chunks. Its working copy received deterministic UUIDs for 18 old entities
  without them and the default priority for 73 old scheduled block updates. All seven villagers survive.
- The Hypixel and Twerion downloads already use 26.3. Their original generator metadata is at the level root, so the
  working copies also placed it under the overworld dimension for Paper.

**What legacy conversion lost and was restored.** The import restores `Silent` on 144 armor stands in the five
additional GommeHD maps after legacy conversion omitted it. Paper's upgrade removed 20 blank wall signs and twelve wall
banners in the waiting lobbies because their old attachments use air, plants, or beds. Those decorations are restored in
the finished chunks using matching surviving decorations with identical source text or banner patterns. Only those 32
block positions and their block entities differ from the finished terrain, and no supporting blocks were added.

**Map pictures.** A dimension cannot hold pictures, so they live in the archive level, in
`archive/data/minecraft/maps`. Restore this level data with the archive template when starting the backend; adding only
the exhibit folders does not install the pictures into an already running level. All 77 picture files retain their
source bytes, and their frame references were remapped to free IDs:

| IDs | Holds |
| --- | --- |
| up to 194 | referenced by older exhibits, with no picture file in the level |
| 195 to 714 | reserved for an exhibit kept outside this repository |
| 715 | Star Wars' unused picture, which none of its retained items references |
| 716 to 791 | the 2020 GommeHD lobby's 76 pictures, with 72 frame references |
| 11060 to 11063 | four frames of the 2020 GommeHD lobby, whose picture files are absent from the ZIP |

Those four frames remain without pictures. The otherwise unused source pictures 12 through 15 contain no colored
pixels. The saved allocator reserves all of these IDs, so the next allocated map is 11064 and cannot populate a missing
frame with unrelated artwork.

**Checks.** Independent checks read all 14,135 source chunks after conversion. Every chunk is `minecraft:full` at
DataVersion 5023, has valid block and biome palettes, and has no pending neighbour upgrades. Every retained terrain
chunk matches the finished copy apart from the 32 restored decorations. The checks also compare source block-entity
positions and types. Legacy flower pots, note blocks, and beds now store their data as block states and were checked
there. Armor stands, item frames, and paintings were compared per map, including poses, equipment, names, frame
contents, leather colors, and custom-head textures; the audits are in the workspace. Legacy hanging-entity coordinates and directions are checked through their attachment positions and
equivalent modern directions rather than by comparing the changed storage formats.

A throwaway Paper server loaded all nine through `ExhibitPool` and `TemplateDimensions`. It checked support and headroom
at all arrivals and checkpoints, void outside the templates, the 32 restored decorations, picture loading, and
allocation of map ID 11064 without occupying the older map IDs. Client appearance, lighting, and normal ticking still
need a visit. Scheduled block and fluid updates remain.

## TTT

The TTT server's template is `worlds/ttt`: ClayMC's waiting island as its overworld and three imported maps in
`blockcraft/TTT/maps`, each with a `map.yml` ([TTT](../paper/ttt/README.md#the-maps)). The round-based servers use
the [shared waiting island](#shared-waiting-island) when `BLOCKCRAFT_WAITING_TEMPLATE` is set, as Compose and the local
Paper task do, so the ClayMC island is the lobby only without it. All four come from
`Blockcraft Server/2/ClayMC-7.0-7.1/Maps` and were imported on 2026-10-31 with Paper 26.3 build 234.

- The lobby comes from `TTT World`. It was saved as the level `Wartelobby`, using WorldDownloader on ClayMc.net in 2017
  (`WorldDownloader.txt`). All built chunks of the round waiting island remain (`-144,16,-33,127`). The lobby
  spawn in `blockcraft/TTT/locations.yml` is the old world spawn, turned toward the middle of the island. The six signs
  at `(-97, 47..49, 77..81)` are blank.
- `vineyard` comes from `Vineyard` (`Welten//TTT-Vineyard`): a valley ringed by stone mountains. The tester stands in
  the building at its middle. A sign there says "Build by ClayMC". All built chunks remain.
- `minecraft` comes from `TTTMinecraft` (`Welten//TTT-Minecraft`): an island in a sea with a beach, a pond, rails and
  a nether portal, inside a wall of barriers. All built chunks and its two minecarts remain.
- `raid` comes from `Raid`: a villa with a pool, a basketball court and a fountain in a roundabout, inside a ring of
  barriers. Signs in a cave below it credit youtube.com/NiicKNumber1. The import keeps the 324 chunks of its raised
  terrain (`-1120,-1184,-833,-897`) and drops the 496 chunks of flat grass around it.

Vineyard and Minecraft come with ClayMC's tester: a 3x3 iron room with a doorway in front, a glass lamp on each side
of the doorway and a button on the back wall. Below the floor is the pit of ClayMC's traitor trap: three blocks of
air over lava. Its hidden button is a stone button on an iron block in Vineyard and a wooden button on a quartz block in
Minecraft. Raid had no tester and no ender chest. Its lawn is a single layer of grass over a hollow. A tester was built
the same way, pit included, on the west lawn at `(-1063..-1059, 41..50, -1059..-1055)`. Its trap button is on the outside
of the back wall. The chest at `(-996, 48, -1026)` became the ender chest.

Each `map.yml` holds the positions. Spawns are all in the open: in a ring round Vineyard's tester building, near
Minecraft's old world spawn and round Raid's fountain. Every chest of a map is a loot chest.

Player records, statistics, village data, 80 loose items in Vineyard and the unused dimensions are excluded. The
Downloads backups remain unchanged.

A throwaway Paper run loaded every map through `TownMaps` and `TemplateDimensions`. For all spawns it checked the floor
and headroom, and for each spectator spot open air. It also checked each tester's button, lamps, doorway, room and floor,
each trap's button and its pit with lava, each map's border, and its chests and ender chests. A dev server with
BlockcraftAPI, core and TTT then started on the template and copied the three maps into its level. Client checks are
still needed for the vote, a round on each map, the tester, the trap and the lobby.

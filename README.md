# BlockcraftMC Plugins

Paper and Velocity plugins for the BlockcraftMC minigame network, originally written for Spigot 1.8.8 / BungeeCord in
2018-2019 and ported to Paper 26.3 / Velocity 4 in 2026.

See [Easter eggs and lobby activities](EASTER_EGGS.md) for rewards, calendar events, map setup, and gameplay checks.
See [Zombies setup](paper/zombies/README.md) to run the solo and cooperative survival mode.
See [EnderGames](paper/endergames/README.md), [TTT](paper/ttt/README.md) and [SkyWars](paper/skywars/README.md) for those modes, after GommeHD's.

## Layout

```
common/                  DB access (HikariCP), YAML config, MiniMessage messages; shared by Paper and Velocity plugins
paper/api                BlockcraftAPI: library plugin owning the database pool, coins, menus, items, shared helpers
paper/bedwars            BedWars, a port of rewi-bw (see "BedWars")
paper/core               BlockcraftCore: staff/utility commands, chat moderation enforcement
paper/lobby              Lobby: join items, server compass, cosmetics, premium shop, secrets
paper/duell              Duell: single games and three-mode tours (vs, sg, superjump, sumo, mlgrush)
paper/emeraldrush        EmeraldRush minigame
paper/mlgrush            MLG Rush: two players, knockback sticks, beds, first to five
paper/quicksg            QuickSG survival games
paper/zombies            Solo or co-op Zombies survival
paper/cores              Cores: red against blue, two beacons each, after GommeHD's mode
paper/endergames         EnderGames: kit survival games in a fresh random world, after GommeHD's mode
paper/ttt                TTT: Traitors, Detectives and Innocents in a town built in code, after GommeHD's mode
paper/skywars            SkyWars: everyone on their own island over the void, after GommeHD's mode
paper/archive            Archive: the Zeitkapsel, old maps of earlier servers to walk through (see "Zeitkapsel")
paper/reports            ReportsMenu: staff GUI over the reports table
paper/statussigns        StatusSigns: clickable server-status signs
velocity/system          BlockcraftSystem: reports, moderation, parties, friends, routing and staff tools
resourcepack/            The client-side look: menu panels, HUD icons and button textures (see "Resource pack")
waker/                   Starts and stops game servers for the proxy (see "Game servers on demand")
map-import/              Turns an old world into a map template (worlds/README.md, "Importing an old 1.8 map")
dev/                     Config templates for the local dev network (see below)
worlds/                  One map template per server group, restored at every start (see "Maps")
docker-compose.yml       The local dev network: MariaDB, Velocity and one Paper server per game mode
docker-compose.prod.yml  The same network on a public host, with prod/ and .env (see "Deploy")
```

Ranks and permissions are handled by [LuckPerms](https://luckperms.net) on both platforms; the plugins check
`group.<rank>` permissions and assign groups through the LuckPerms API. The proxy and every Paper server must share
one LuckPerms database, otherwise a rank set on one side is unknown on the other.

## Ranks

`luckperms/ranks.json` is a LuckPerms export with the groups the plugins expect. Chat and the tab list of every
Paper server render the group's prefix in front of the name and sort the tab list by group weight; the prefix
carries the name colour, so it ends in a colour code. Each group's `badge` meta is a glyph of the resource pack's font
(U+E300 onwards) that replaces the prefix for a player who wears the pack.

| Group      | Prefix             | Grants                                                                            |
|------------|--------------------|-----------------------------------------------------------------------------------|
| `default`  | `&7SPIELER &8● &7` | party invite and kick, `/msg` and `/r`, `/friend`, `/seen`, `/playtime`, `/discord` |
| `premium`  | `&6PREMIUM &8● &6` | default plus premium lobby area, double jump, join when full, `/joinme`, chat colours, own `/ptime` kept across servers |
| `youtuber` | `&5YT &8● &5`      | premium plus `/joinme` without delay, `/bc`, `/nick`                              |
| `supporter`| `&eSUP &8● &e`     | premium plus team chat, reports and support requests, mute, warn, kick, check and history, clear chat, chatting during a global mute, seeing through vanish and nicks |
| `moderator`| `&cMOD &8● &c`     | supporter plus ban, strikes, warn lists, alts, `/goto`, global mute and slow chat, freeze, vanish, staff mode, invsee (read only), teleport |
| `dev`      | `&bDEV &8● &b`     | premium plus every `blockcraft.*` and `velocity.command.*` permission except `/rank` |
| `admin`    | `&4ADMIN &8● &4`   | dev plus `luckperms.*` and `/rank`                                                |
| `owner`    | `&bOWNER &8● &b`   | admin plus `*`                                                                    |

`default` negates `minecraft.command.msg`, `minecraft.command.teammsg` and `minecraft.command.me`. Paper
grants these vanilla chat commands, with their `tell`, `w` and `tm` aliases, to every player, and they reach the
chat without passing the mute check or the chat filter. Every other group inherits `default` and loses them as well; the
proxy's `/msg`, `/tell` and `/w` remain. LuckPerms lets such an exact node win over any wildcard, so `dev` grants
`velocity.command.server` by name: `default` closes `/server` to players, and `velocity.command.*` alone would not
reopen it for staff.

Import it into every LuckPerms database, and again after every change to it (`gzip -k luckperms/ranks.json`, copy the
`.gz` into LuckPerms' data folder, `/lp import ranks`), then assign players with `/rank <name> <group>`.
The import replaces each group's permissions with the file's and leaves players' groups alone, so a permission set
by hand in game is gone after the next import unless it is in the file.

`/rank <name>` shows a player's rank on the proxy; `/rank <name> <group>` replaces every group they had, like
`/lp user <name> parent set <group>`, except `premium` bought in the lobby shop when the new group lacks it, and
`/rank <name> <group> 30d` gives a rank on time on top of the one for good,
which LuckPerms takes away again when it runs out. Staff change only players below them and only to groups below
their own, by group weight; the console may do anything, so the first owner is still set from a console. Every change
lands in `/stafflog`.

Every plugin ships a `messages.yml` (MiniMessage, German by default) and a `config.yml`; both are copied to the plugin's
data folder on first start and can be edited there. Keys the edited file lacks come from the bundled default, so a
plugin update never runs on missing settings.

Two sets of tags resolve in every message. The network addresses (`<ip>`, `<website>`, `<shop>`, `<teamspeak>`,
`<discord>`) come from `network` in `BlockcraftAPI/config.yml` on the Paper side and from `network` in
BlockcraftSystem's `config.yml` on the proxy, so the server address is written once. The colour palette
(`<primary>`, `<accent>`, `<muted>`, `<dark>`, `<success>`, `<error>`, plus `<bullet>` and `<arrow>` for the two
list shapes, and `<premium>` for everything Premium) comes from `palette` in `BlockcraftAPI/messages.yml` and from
the same section in the proxy plugins' `messages.yml`; recolouring the network means editing that one section. On
Paper the palette also holds the click hints of menu lore, `<left>`, `<right>` and `<click>`, which open a line such as
`<left>Jetzt spielen`.

## Build

Requires JDK 25 (`mise install` picks it up from `mise.toml`; the Gradle toolchain downloads one otherwise).

```
./gradlew build
```

Deployable jars land in `<module>/build/libs/<module>-<version>.jar` (the `-slim` jar is a build artefact).
`BlockcraftAPI` shades HikariCP and the MariaDB driver; the other Paper plugins reach the pool through it. Velocity
plugins shade them too.

Versions live in `gradle/libs.versions.toml` (`paper-api`, `velocity-api`, `minecraft`, `viaversion`).

### Client versions

The servers speak `minecraft`; every Paper server also runs [ViaVersion](https://github.com/ViaVersion/ViaVersion),
so clients newer than that join as well, as far as the proxy knows their protocol (Velocity 4.2.0 knows 26.3).
Older clients would need ViaBackwards on top. A new client version therefore needs a ViaVersion release that
supports it, `viaversion` bumped in `gradle/libs.versions.toml` and `./gradlew stagePlugins` again, and, when the proxy does not
know it yet, a newer `VELOCITY_VERSION` and `velocity-api`.

## Run locally

For repeatable server and client measurements, see [Measure Minecraft performance](dev/performance/README.md).

### The whole network in Docker

```
./gradlew stagePlugins   # plugin jars into build/docker/
docker compose up -d     # MariaDB, Velocity on 25565, Paper servers lobby, duell, emeraldrush, quicksg, bedwars and mlgrush
```

Connect to `localhost:25565`. Every Paper server runs BlockcraftAPI, BlockcraftCore and its game plugin (the lobby
also StatusSigns and ReportsMenu); `docker-compose.yml` is the place that says which server runs what. Plugin data
lives in named volumes: `docker compose down` keeps it, `docker compose down -v` starts over. The worlds come from
`worlds/<group>` (see "Maps") and are thrown away at every start. After a code change, `./gradlew stagePlugins` and
`docker compose restart <server>` pick up the new jars.

In the lobby, `/createsign quicksg` while looking at a sign makes a status sign for that group, `/setspawn qsg` and
friends place the navigator warps, `/map save` keeps all of that, and `/play quicksg` joins a game from anywhere.
Use `/navigator` (aliases `/nav`, `/spiele`) or the compass to open the game menu in the lobby.
Left-click a game to play or right-click to visit its lobby area. Cards and status signs share
availability labels and group player counts; the proxy checks availability again when clicked. Unconfigured
lobby areas explain that they are not ready.
To run a second instance of a game, copy its service under a new name with its own `/data` volume. Avoid
`--scale`: the copies would share one volume, and with it one world folder that every start overwrites.

`docker compose logs -f lobby` follows a server and `docker compose exec lobby rcon-cli <command>` runs a command.
LuckPerms answers only on the real console, so on a fresh database, and the first two lines again after every change
to `luckperms/ranks.json`, run

```
docker compose exec -u 1000 lobby mc-send-to-console lp import ranks
docker compose exec -u 1000 lobby mc-send-to-console lp networksync
docker compose exec -u 1000 lobby mc-send-to-console lp user <name> parent set owner
```

and read the replies in the log. The import does not notify the other servers, so without the `networksync` the proxy
keeps the empty groups it loaded at startup and every permission check there fails, which also hides all tab
completion. A player who is online while their rank changes has to rejoin before the client shows completions again.

### One server on the host

```
docker compose up -d mariadb    # MariaDB on localhost:3306, database/user/password "blockcraft"
./gradlew :paper:runServer      # Paper 26.3 on 25566 with all Paper plugins + LuckPerms, announced as "lobby"
./gradlew :velocity:runVelocity # Velocity on 25565 with all Velocity plugins + LuckPerms
```

`-Pplugins=core,lobby` limits either run task to a subset (`BlockcraftAPI` is always included). The run directories
under `run/` are seeded from `dev/` (proxy forwarding, ports, dev secret, LuckPerms on the compose MariaDB so proxy
and backend share one permission database) and are git-ignored. The dev run tasks accept the Minecraft EULA on your
behalf and drop `ranks.json.gz` next to LuckPerms; on a fresh database run `lp import ranks` and
`lp user <name> parent set owner` in either console once, and `lp import ranks` again after a change to `ranks.json`.

Both flavours share the database, announce a server called `lobby` and run the map in `worlds/lobby`, so run one of
them at a time.

## Maps

A map is one folder: the world plus a `blockcraft/` directory inside it, where every plugin keeps what belongs to
the map (`blockcraft/<Plugin>/locations.yml` for spawns and warps, `blockcraft/StatusSigns/signs.yml`) and
`blockcraft/map.yml` names it:

```yaml
name: Mondbasis
builders: [Kevos, Aquitano]
```

Name and builders show in the title every server greets a player with (`arrival` in BlockcraftAPI's
`messages.yml`) and in the games' sidebars. Without a `map.yml` the map is called after its template directory.

Every server group has a template directory, `worlds/<group>` in this repository, mounted into the container and
named by `BLOCKCRAFT_WORLD_TEMPLATE` (or `world.template` in `plugins/BlockcraftAPI/config.yml`). BlockcraftAPI
replaces the world folder with a copy of the template before Paper loads it, so a restart also resets the map:
nothing a round, a gadget or a builder left behind survives. Duell and MLG Rush reset their gameplay between matches
without restarting the server. A template
without a `level.dat` is left alone and the server keeps whatever world it has, which is how a map is made in the
first place; no template at all means a plain persistent world.

Changing a map: join the server, `/build` (`blockcraft.build`) lifts the block protection and the inventory lock
for you until you leave, edit, place spawns with the plugin's setup command, then `/map save` (`blockcraft.map`).
That flushes the world and copies it into the template without player data, and every start from then on boots
with the new state; `/map` shows what the server is running. The copy goes to `.staging` inside the template
first and only a finished copy replaces the old contents, so a save that fails halfway leaves the template as it
was; the next start or save puts a swap that was cut short back together. `.staging` and `.replaced` are ignored by
git. Commit the template like any other change and revert
it like one. Region files go through git-lfs (`.gitattributes`), so `git lfs install` once before committing a map.
A map edited without `/map save` is gone at the next restart, and so is a `/setup` on such a server, which is the
point. The running server publishes its own copy of `map.yml` too, so edit that file in the template and restart
rather than while the server runs.

Templates from a server that predates this layout: `./gradlew importMap -Psource=<old world> -Ptarget=worlds/<group>`
converts the world once on Paper 26.3 and keeps only the chunks that matter ([Importing an old 1.8
map](worlds/README.md#importing-an-old-18-map)). Then set the spawns again, since the plugin files used to live in
`plugins/<Plugin>` and pointed at the old world name. On a server that already runs this layout without a template, those files move into the world's
`blockcraft/` directory on the first start by themselves.

The [Vanade map import](worlds/README.md) records the imported worlds, converted configuration, and remaining in-game checks.

## MLG Rush

`paper/mlgrush` runs human 1v1 matches in the `mlgrush` group. Each player gets a knockback stick, refillable
terracotta, and a wooden pickaxe. Two players start the five-second countdown. Later arrivals watch.
Breaking the enemy bed scores once and resets both players and the arena. Falls respawn immediately.
The first player to reach `score-target` wins. Leaving an active match forfeits it.

### Run a local MLG Rush network

Build the plugins, then start the four local services:

```sh
./gradlew :paper:mlgrush:build :paper:lobby:build stagePlugins
docker compose --env-file /dev/null -f docker-compose.yml up -d mariadb velocity lobby mlgrush
```

Connect two clients to `localhost:25565`. In the lobby, `/navigator`, `/nav`, `/spiele`, or the compass opens the
same game menu. Left-click MLG Rush to play, or use `/play mlgrush` directly from any backend. `/hub` or `/lobby`
returns from MLG Rush to the lobby, where `/navigator` works again. The waiting and ending hotbars also have a hub
item. The active kit stays locked to its gameplay slots. No lobby warp or arena editing is required.

`stagePlugins` writes `build/docker/paper/mlgrush/mlgrush.jar` with `config.yml`, `maps.yml`, and `messages.yml`
bundled inside it. The service installs BlockcraftAPI, BlockcraftCore, MLGRush, LuckPerms, and ViaVersion.
Its hostname is `mlgrush`, and `BLOCKCRAFT_SERVER_GROUP=mlgrush`. BlockcraftAPI announces two participant places
and the game phase to `bc_servers`, with the shared five-second heartbeat. The Paper connection limit remains 200
so spectators can connect. After a match, both players can request a rematch through the recap's play-again button
or the first hotbar item. Once both agree, the arena resets, a map is selected, and the next match starts after
`countdown.start` seconds, three by default. Scores, kits, and round summaries start fresh without a backend restart.
When an opponent leaves, the server returns to its waiting phase and fills open seats from non-vanished spectators
in arrival order. An empty backend still stops after `idle-restart-minutes`.

Duell also offers MLG Rush as a native mode in singles and tours. Both hosts use `paper/api/game/rush` for maps,
equipment, knockback and scoring. The standalone `mlgrush` group keeps its existing entry point and `mlgrush_stats`;
native Duell matches contribute only to `duell_stats`. Existing statistics are not moved or counted twice.
Native gameplay settings live under `mlgrush` in Duell's config, with its procedural maps in
`plugins/Duell/rush/maps.yml`. An explicit Duell map vote takes priority over that file's standalone selection preset.

### Select and configure maps

On first start, MLGRush creates `plugins/MLGRush/maps.yml`. Its six enabled procedural maps are:

- `sandstone`, Sandsteg: a 25 by 1 bridge and 7 by 7 bases, with sandstone and red/blue concrete.
- `courtyard`, Burghof: a 17 by 3 bridge and 9 by 9 bases, with stone bricks, red sandstone, and prismarine bricks.
- `quartz`, Quarzlinie: a 37 by 1 bridge and 11 by 11 bases, with quartz and white/light gray concrete.
- `tightrope`, Drahtseil: a 29 by 1 bridge with one-block-wide bases.
- `divide`, Schlucht: a 25 by 3 bridge with a five-block central gap and one-block-wide bases.
- `stepping`, Sprunginseln: a 31 by 3 route with three-block segments, two-block gaps, and three-block-wide bases.

Spawns face each other and retain solid support, including on narrow bases. Both bed halves also have support.
The generated void dimension starts as `mlgrush:<key>` and stays loaded when another map replaces its layout.
Point resets restore the selected map's materials and paired
beds after both players teleport to protected spawn spaces. Only player-placed blocks and the enemy bed can break.
Combat applies knockback without health damage. Wins, losses, falls, and coins use `mlgrush_stats`.

`selection: random` chooses one enabled map at backend start and between matches. For repeatable testing, set `selection: courtyard`
to an enabled key, then recreate or restart the backend. The chosen name appears on arrival, at match start, and
in the sidebar; the log records its key. `maps.<key>.enabled` controls the random pool. Each definition must name
its geometry and all three materials. Only the full solid blocks listed in the bundled pool are accepted.
Empty pools, unknown or disabled selections, unsafe materials, and invalid geometry fail startup with the map key.

`plugins/MLGRush/config.yml` controls the score target, countdowns, build margin/height, floor/void heights, kit,
and rewards. A base radius is half its side length rounded down. Bridge widths must be 1, 3, or 5. Bounds are shared
by all maps, and the stick's horizontal velocity is its level times `kit.horizontal-per-level`.

Optional map keys control the new layouts. `base-width` is an odd width within the base's full diameter and defaults
to that diameter. `center-gap` defaults to zero and removes a centered stretch of bridge. `segment-length` and
`segment-gap` default to zero; set both to positive lengths for separated bridge segments. Existing definitions
without these keys keep their full platforms and continuous bridges.

For an existing installation, stage the updated jars and restart the backend. A missing `maps.yml` is created
without replacing `config.yml` or `messages.yml`. Move any old `arena.bridge-length`, `arena.bridge-width`, and
`arena.base-radius` customizations into a named entry in `maps.yml`; those three old config keys are no longer read.
Pin that entry with `selection` if needed. New message keys fall back to bundled defaults.
Missing bundled map definitions are added to the loaded pool, preserving existing definitions, disabled flags,
and the selected map. Set a bundled map's `enabled` flag to false to exclude it.

### Run MLG Rush with production forwarding

Fill `.env` as described under "Deploy", then use both Compose files:

```sh
./gradlew stagePlugins
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml up -d mariadb velocity lobby mlgrush
```

MLG Rush is released by default. Its production override uses `prod/paper-global.yml`, the same forwarding secret
as Velocity, the production database passwords and network addresses, and its own `mlgrush` data volume.
For updates, stage the jars, run the same `up -d` command to apply Compose changes, then run
`docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml restart velocity lobby mlgrush`
to load changed jars and resource-pack assets. Persistent plugin files stay in the named data volume.

## Duell Sumo

Sumo appears alongside VS, SG, and SuperJump in Duell's mode vote. `/forcemap sumo` forces it for the next duel.
Its circular stone ring is generated in `duell:sumo`, so it needs no `/setup` locations or imported map.
Both players fight with empty hands. Punches apply knockback without health damage. Falling below the ring's
loss height gives the opponent one point, resets both players, and starts a three-second freeze before play resumes.
The first player to three points wins. At the round time limit, the player with more points wins, or equal scores draw.

Duell's `sumo` config section controls the radius, floor and loss heights, spawn distance, target score, reset
duration, knockback strength, and hit delay. Sumo uses Duell's stats, win rewards, rating choice, and rematch flow.
Each knockout counts as a kill for stats and network XP. The damage-free win achievement excludes Sumo.

### Single game and three-mode tour

Duell's vote menu offers `Einzelspiel` and `3-Modi-Tour`. Single game is the default. A tour requires both
participants to select it before the lobby countdown finishes. It schedules three distinct playable modes from
VS, SG, SuperJump, Sumo, and MLG Rush. Each player's mode vote nominates that mode; remaining places are drawn randomly,
and the order is shuffled and announced before play. Map votes still apply within their modes. Without nominations,
all three modes are random. A forced mode takes one tour place. Fewer than three playable modes falls back to a
single game with an explanation. Format tabs sit above the mode choices; map voting follows the selected mode.

Every tour plays all three games, including at 2:0. A drawn game consumes its place; equal game wins after the third
game draw the tour. Games retain their normal point rules, reset equipment and checkpoints, alternate starting
slots, and continue on the same backend. `tour.intermission-seconds` controls the next game's freeze, three seconds
by default. Leaving or vanishing during a tour forfeits the whole series, including during transitions.

Kills and deaths accumulate across the tour. The final result grants win coins, win XP, participation XP, the
account-level win or loss once. One combined recap follows the tour. Both players can accept a local rematch without
restarting the backend. The same pair keeps its format and mode preferences; a new opponent starts with fresh choices.
SG loot, equipment, scores and round accounting reset between matches.

Format and rating are independent. `rating.enabled` defaults to `false`, so both single games and tours are
unrated while still earning normal XP, coins and statistics. Historical Elo is preserved. Enabling the flag exposes
a separate rating choice; a series is rated only when both players explicitly choose it. A decided rated series
settles Elo once, including forfeits. Rematches reset rating consent.

## BedWars

`paper/bedwars` is a port of [rewi-bw](https://github.com/Marfien/rewi-bw), the Rewinside BedWars clone, under its
Apache 2.0 licence (`paper/bedwars/NOTICE`). It plays like the original: four teams of two, bronze, iron and gold
from the map's spawners, the original shop and prices (the `shop` list of its `config.yml`), team chests, rescue
platforms, parachutes, teleporters and mobile shops, and a lobby with team stands, a map vote, a CPS tester, a jump
and run and a game of catch. Stats, coins, XP, achievements and spectating are the network's own, as in every game.

The lobby is the `worlds/bedwars` template like any other map. The maps a round is played on travel inside it, one
directory per map under `blockcraft/BedWars/maps/<key>/`: `map.yml` with its name, border, spectator spot, shops,
spawners, team spawns and beds, and `world/`, the map's dimension folder. A round copies the voted map to
`dimensions/bedwars/<key>` of the running level and loads it as the world `bedwars:<key>`; the restart after the round
throws it away again.

`/setup lobby ...` places the lobby's spots, kept with `/map save` like every plugin's. `/setup map <key> edit`
loads a map of the pool, or an empty one for a new key, and takes the builder there; standing in it, `spawn`, `bed`,
`shop`, `spawner`, `spectator`, `border`, `name` and `icon` change its `map.yml` at once, `info` names what is still
missing, and `save` copies the world back into the pool. `/map save` then publishes the lobby with its pool. A map is
in the vote once nothing is missing for the teams `config.yml` plays with. `era: og` in a `map.yml` marks a map of
the old server: the vote draws only from those while the OG occasion is on (`/event og` on the proxy) and only from
the others otherwise, unless that set has no playable map. `/forcemap` takes any map.

Opening a map for editing keeps its loaded dimension out of votes and forced choices. Save changes you want to
keep, then restart the backend to use that map in rounds. Editing a chosen map before the round starts cancels
that start instead of replacing files beneath the loaded world.

## Cores

`paper/cores` plays GommeHD's Cores in the `cores` group: red against blue, `teams.players-per-team` each (2x4 by
default). Players pick a team with the hotbar's team item; the rest are seated on the smaller side when the lobby
countdown runs out, and a full lobby jumps to `countdown.full` seconds. Each team defends two beacons, left and
right in its base. A core sits in a 3x3x3 cube that nobody can change, so it is only reached from above; an enemy
within `cores.fatigue-radius` mines with Mining Fatigue, which makes a beacon take 15 seconds. An enemy within
`cores.alarm-radius` alerts the team in chat and with a repeating sound, and the sidebar marks the core. The first
team to break both enemy cores wins; after `countdown.round` seconds (45 minutes) the round is a draw.

Respawns are unlimited, with the `kit` and leather armour in the team's colour, and players cannot be hurt by other
players for `respawn.protection-seconds`. There is no shop: each base holds iron blocks to mine, workbenches, an
enchanting table with shelves (lapis is provided), four anvils and a chest of experience bottles; the middle holds
diamond blocks and the four side islands loot chests and gold. Base blocks other than the iron do not break; the
rest of the map, and everything players place, does. A bow kill pays `bow-kill-arrows` arrows. Combat is 1.8-style.
Team chat is the default during a round; a message that starts with `@` goes to everyone.

The map, Himmelsfeste, is built by `SkyMap` into an empty world at every start, so the service needs no world
template. The production compose file keeps the service under the `unreleased` profile until it is released.

## Zeitkapsel

`paper/archive` runs the `archive` group, the Zeitkapsel: old maps of earlier servers to walk and fly through, with no
game. Everyone arrives in a small entrance hall that `EntranceHall` builds into an empty world at every start, in
adventure mode with flight; nobody is hurt or hungry, and nothing is built or dropped outside `/build`. The pocket
watch in the first hotbar slot opens the guide, grouped by server, year and category. Sections sort oldest year
first, then by category, with undated exhibits last. A single section opens its exhibit list directly. Left-click
an exhibit to travel to its arrival; right-click to open its checkpoints and notes. The sections screen also leads
back to the entrance hall, and the hotbar's last slot back to the lobby.

Exhibits live in their own template folder beside the archive's map bundle, one directory per exhibit under
`worlds/archive-exhibits/<key>/` with its `exhibit.yml` and `world/` ([layout](worlds/README.md#zeitkapsel-exhibits)),
mounted at `exhibits.directory`; neither a start nor `/map save` copies them.
An exhibit's world is copied out and loaded as `exhibit:<key>` when its first visitor travels there, and dropped again
once nobody has been in it for `exhibits.unload-after-seconds`. Standing in an exhibit, `/setup spawn` sets its
arrival, `/setup point add <name>` places a checkpoint and asks for its description in a dialog (the same name again
moves and rewrites it), and `/setup point remove <name>` takes one away. Each lands in the exhibit's
`exhibit.yml` at once.

Staff with `blockcraft.archive.note` leave a note where they stand with `/archive note` or by left- or right-clicking
the note item next to the pocket watch in the hotbar. Both open the note dialog inside an exhibit. Notes are rows of
`bc_archive_notes` (exhibit, where the author stood, author UUID and name, text, time). While their exhibit is loaded
they hang as text displays in front of where the author stood; the exhibit's menu lists them for everyone, and staff
delete one there with a right-click and a confirmation.

Production runs the Zeitkapsel like a released game, and `archive` is one of the `on-demand.groups` the waker starts
at the first visit and stops when it stays empty.

## Server discovery and groups

The proxy keeps no server list. Every Paper server writes a heartbeat row (name, group, address, state, player
count) to `bc_servers` through BlockcraftAPI, and BlockcraftSystem on the proxy registers each server with a fresh
heartbeat and drops those whose heartbeat went stale (`discovery` in its `config.yml`). `[servers]` in
`velocity.toml` stays empty; servers listed there anyway keep their static address.

Servers of one game form a group, and players join groups, not servers: `/play <group>`, a status sign or a game's
"back to the lobby" item ask the proxy, which picks the instance. Games fill the fullest open instance, lobbies
(`lobby-group`) take the emptiest. A server is open while its state is `LOBBY` and it has room; the game plugins
switch the state to `INGAME` and `RESTART` as rounds run, and the lobby's status signs and navigator show the group's
state and player count from the same table.

The heartbeat lags a few seconds behind, so the proxy counts a server's players as the larger of the heartbeat and
the players it has connected there itself, plus a seat for everyone it is still sending there. A seat ends when its
player arrives, when the connection fails, or after `discovery.seat-seconds`. A party leader who joins a game takes
seats for every online member at once, and is told when no instance has room for the whole party. A member who
follows a leader onto a game instance any other way needs a free seat there, or stays where they are; lobbies and
running rounds, which members only watch, take them as before. A friend jump and a JoinMe click take a seat on that
one server too, or let the player watch its running round, and are turned away when it has neither; `/goto` takes a
seat when there is one and goes anyway. The proxy writes its count back into the row's `occupancy` column whenever a
seat comes or goes and on every poll, and status signs, the navigator and the lobby list count the higher of that and
the heartbeat, so they stop inviting players the moment the last seat is taken.

A server announces itself as `BLOCKCRAFT_SERVER_NAME`, falling back to `HOSTNAME` (the container or pod name), in
group `BLOCKCRAFT_SERVER_GROUP`, falling back to its name, at `BLOCKCRAFT_SERVER_HOST`, falling back to the address
of the interface that reaches the database, on its `server-port`. Set the variables when several servers share a
host or the proxy reaches them through another address. The same works unchanged on Kubernetes: one Deployment per
group, the pod name becomes the server name and the pod IP its address.

A server writes its first heartbeat on `ServerLoadEvent`, once it takes connections, so the proxy never sends anyone
into a server that is still starting.

A Paper server that cannot reach the database at startup shuts itself down instead of running without its plugins,
so the container's restart policy tries again; the proxy does the same.

### Game servers on demand

Off by default. With `on-demand.enabled` in BlockcraftSystem's `config.yml`, only the proxy, MariaDB and the lobby
need to run around the clock; the game groups in `on-demand.groups` run only while someone plays.

- A player who wants into a group with no running server (navigator, sign, `/play`) starts it. They are told so and
  sent once its server is up, about a minute later; everyone who asks meanwhile waits in order. Whoever waited past
  `start-timeout-seconds` is told it failed.
- A group whose servers sat in their lobby with nobody on them or on the way for `idle-stop-minutes` is closed to new
  seats and stopped. A round in progress is never stopped, and a click while it stops starts it again.
- A server restarting after its round counts as starting, so whoever clicks, or asks for a rematch, waits for it.
- `max-awake` caps how many of these groups run or start at once (0 for no cap).

The navigator and the status signs show such a game as sleeping or starting (`bc_server_groups`), and both invite the
click.

The proxy decides, because only it sees seats, parties and rounds; the `waker` service carries it out. The waker is
the only service with the Docker socket, which is root on the host. So it publishes no port, wants the bearer token
`WAKER_TOKEN` and starts or stops nothing but the compose services in `WAKER_SERVICES`, a group's service being named
like the group. To enable it on the host:

1. Put `WAKER_TOKEN=$(openssl rand -hex 24)` into `.env`; the proxy reads it as `BLOCKCRAFT_WAKER_TOKEN`.
2. Run `./gradlew stagePlugins`, then `docker compose --profile ondemand up -d waker`.
3. Set `on-demand.enabled: true` in the proxy's `config.yml` in its data volume and recreate the proxy with
   `docker compose up -d velocity`; a `restart` keeps the environment it started with, without the token.

A deploy's `docker compose up -d` or `restart` starts every game again; the proxy stops each after
`idle-stop-minutes`. `docker compose stop <game>` puts one to sleep by hand, and the next click wakes it.

When a discovery poll finds no joinable server left in the lobby group, BlockcraftSystem posts the `alerts.no-lobby`
text to the Discord webhook in `alerts.webhook-url`, and `alerts.lobby-back` once a lobby is joinable again. Each
change is announced once and at most one alert goes out per `alerts.min-interval-seconds`; an empty URL disables it.
Every heartbeat also carries the server's one-minute TPS and its average milliseconds per tick; a server above
`alerts.lag-mspt` for `alerts.lag-polls` polls in a row is posted as `alerts.lag`, and `alerts.lag-over` follows once
it drops back, under the same once-per-interval limit kept apart from the lobby alert's.

## Network XP and levels

Every kill and win in a game awards XP through BlockcraftAPI (`xp.rewards` in its `config.yml`), stored in `bc_xp`
as one total per player. The level follows a fixed curve: each level costs `xp.level-step` more than the one before,
so with the default of 100 level 2 is 100 XP, level 3 is 300 and level 10 is 4500. Awards arriving together show as
one XP total in the action bar; level-ups report the highest reached level in chat. `/level` opens the milestone
browser with current progress, the next reward, and owned cosmetics that can be equipped there. `/level <player>`
needs `blockcraft.level.others`, and chat prefixes every message with the level (`chat.level` in core's `messages.yml`).
The lobby experience bar shows this network level and progress. Its boss bar labels coins separately and refreshes
from the database every `hud.refresh-seconds`. Purchases and secret rewards report the new balance in chat.
Every timed phase of a game shows its time on a boss bar; `timer-bar: false` in a game's `config.yml` moves it to the
sidebar. Levels bought or collected inside a game are labelled as enchantment levels and do not change network XP.

The default XP rewards are 10 for a kill, 50 for a win, and 15 for staying through the round summary. XP is never
spent and grants no combat advantage. Network level achievements automatically unlock these cosmetics:

| Level | Reward |
| --- | --- |
| 3 | Konfettisieger win effect |
| 5 | Aufsteiger chat tag |
| 10 | Veteran chat tag |
| 15 | Sternenstaub kill effect |
| 25 | Elite chat tag |
| 40 | Nordlicht win effect |

Rewards and thresholds live under `achievements.network` in BlockcraftAPI's config. Earned cosmetics require no
coins or Premium. Existing players receive missing rewards after their XP loads, including when they already own
the achievement. Unlocking a reward leaves the current cosmetic choice intact.

The profile gives levels and coins separate tiles. Coins are the spendable balance for permanent purchases; buying
something never lowers the player's level. The lobby shop links heads, shirts, kill effects, win effects and Premium,
and shows the current coin balance. Effect catalogues distinguish coin purchases from level rewards. Existing
balances, purchases, Premium access and gameplay unlocks remain valid.

BedWars, QuickSG, EmeraldRush, every Duell variant, and MLG Rush contribute to this same total. SuperJump and
MLG Rush award win and participation XP without kill awards. MLG Rush bed points, BedWars bed breaks, EmeraldRush
mining, and SuperJump checkpoints award no XP directly. Lobby activities can grant one-time achievement XP.

`BlockcraftApi.notifications()` coordinates action bars, titles, and reward sounds. Direct feedback takes priority
over routine status updates and XP notices. Round titles take priority over achievement titles; interrupted titles
do not replay. The `notifications` section in BlockcraftAPI's config controls display times, XP batching, and sound
spacing. Queued notices expire, and the shared scheduler stops when no notifications remain.

## Nick and vanish across the network

Both states are set on a Paper server (`/nick`, `/v`, `/staff`) and stored in `bc_nicks` and `bc_vanished` so every
server re-applies them on join. Others see a nicked player with made-up stats, rating and level drawn from the nick's name
(`nick-stats` in BlockcraftAPI's `config.yml`): `/stats`, the stats menus, the Duell ladder and the chat level all show
those, while the account's own rows keep counting underneath. A player with `blockcraft.nick` who turns `auto-nick` on is nicked by the server
itself when they arrive for a round, and arrives in the lobby under their own name again unless they also turned
`keep-nick` on; which servers count as a lobby is `lobby-group` in BlockcraftAPI's `config.yml`. The proxy keeps its
own view: it reads both tables when a player logs in and the Paper servers report every change over the
`blockcraft:main` plugin channel. Player counts (`/online`, `/list`, the
tab header, the server list ping), `/find`, `/goto`, `/msg`, `/seen` and party invites leave vanished players out and
address nicked players by their nick; `blockcraft.core.vanish.see` and `blockcraft.nick.see` look through either on
both sides, and the moderation commands resolve a nick to the account behind it. `velocity.command.glist` is not
granted to players because Velocity's own list would show real names and vanished staff.

The same channel lets a Paper plugin run a proxy command as the player, with the player's permissions: the report
menu's "go to" buttons run `/goto`. Reports and punishments do not travel as commands but as typed requests on
`blockcraft:operations` (see "Shared modules and upgrades"). The `/strafe` catalogue itself stays in the proxy's
`config.yml`; BlockcraftSystem mirrors it into `bc_punishment_catalog` at every start so the menu can list the
numbered punishments without a copy of the file.

Staff who see through vanish get a `[V]` marker behind vanished names in the tab list (`tab.vanished-suffix` in
BlockcraftAPI's `messages.yml`); nobody else lists them at all.

## Moderation on the proxy

`/tempban` and `/tempmute` take the duration as one word, the same shape the `/strafe` catalogue uses: `16d`, `12h`,
`30m`, at most five years, or less once 2038 is nearer than that (the end is stored as a `TIMESTAMP`); anything longer
is refused, and a permanent punishment comes from the `/strafe` catalogue. `/support list` shows the open support requests, waiting ones first and clickable to accept, and staff who
log in while requests are waiting are told so. When a player with open reports or a punishment from the last
`watch.recent-days` logs in, everyone with `blockcraft.system.team` gets a line that opens `/whois` on click, from
one query per login. A message the chat filter dropped reaches the staff with `[Goto]`, `[Mute]` and `[Strafe]`
behind it, which run `/goto` or pre-fill `/tempmute` and `/strafe` for that player.
`/msg`, `/r` and `/pc` pass the same word and advertisement filter on the proxy, which reads its own copy of
`chat-filter.yml` (the lists ship in `common`), and allow one line per `private-chat.cooldown-seconds`; a filtered
private line reaches the staff with `[Goto]` and `[Whois]`, and an advertisement lands in `/werbung` like one in chat.

On the Paper side `/team help` lists every command registered on that server through `CommandRegistry` that the
player may run, with its `commands.<name>.description`, so there is no hand-written list to go stale. `/invsee`
shows another player's inventory read-only unless the viewer holds `blockcraft.core.invsee.edit`.

Core's `/tp`, `/kill`, `/clear`, `/xp` and `/enchant` take the vanilla argument forms (coordinates, selectors,
item filters, enchantments) and add the rank guard; `/time` is vanilla's, with `/day` and `/night` as shortcuts. The
console and `rcon-cli` run every core command that names a target. Every staff action on a player (gamemode,
teleports, invsee, sudo, clear, kill, heal, fly, speed, vanish, freeze, staff mode, nick, `/build`, `/coins give|take|set`)
is one row in
`bc_staff_actions`, written through `BlockcraftApi.get().audit()`, and the proxy adds its `/rank` changes; the actions under `audit.notify` in
BlockcraftAPI's `config.yml` also reach everyone with `blockcraft.audit.notify`. `/stafflog <player> [page]`
(`blockcraft.system.stafflog`) lists what was done by or to a player, newest first.

Every game says the shared moments the same way, from `arena` in BlockcraftAPI's `messages.yml`: the arrival card
with game and map, who joined and left the round with the player count, the lobby countdown, the trackers, the last
minutes and the restart countdown. Each game holds a `Round` for what they share: it seats joiners while the lobby
has room and makes everyone else a spectator, vanished staff included, who are neither announced nor counted; it
reports the phase to the proxy and counts leaving a running round as a death and a loss. Spectators fly unseen in
adventure mode with a hotbar to pick a player, step to the previous or next one and leave; the round keeps them from
touching, taking or opening anything, and while it runs their chat reaches only the other spectators. A round's kills,
deaths, coins and XP are counted in one place (`RoundSummary`), which announces kill
streaks at the lengths `streaks.announce` in BlockcraftAPI's `config.yml` names, writes every participant's stats
once when the round ends (a loss for everyone knocked out or beaten by a winner), prints a compact result and
reward line with actions, and stores the recap in `bc_round_recaps`. The Details action shows the full breakdown.
The round also shows a brief result title. Winners see `Du hast gewonnen!`; everyone else sees who won, with
plural wording for team wins. Dead players receive it after respawning. Before an automatic lobby transfer,
titles count down 3, 2, 1. The restart delay is at least seven seconds so the result and countdown fit before the
final shutdown grace second. MLG Rush shows the result without an automatic transfer countdown during rematches.
The lobby offers a Details link after arrival and keeps the recap accessible through the profile during that
session, with a button back into the same game. The `auto-recap` setting opts into opening the lobby dialog
automatically while the arrival screen is still current; it defaults to off. Recaps older than
`recap.max-age-minutes` are discarded. The `quicksg`,
`emeraldrush`, `duell` and `bedwars` palette tags give the games distinct colours across menus,
signs and sidebars. Their arrival sounds respect the player's sound setting. Kit and perk menus distinguish
selected, unlocked and purchasable items; coins buy permanent unlocks, while EmeraldRush emeralds buy round items.
A game's own `messages.yml` holds its introduction and game-specific wording, including the `menu.stats` and
`menu.global-stats` texts of the shared `StatsMenus` and `/top`. `MessageKeysTest` in `common`
checks that every key a plugin asks its messages for exists in its `messages.yml` or BlockcraftAPI's.

The rest of a game's lobby and round comes from the same pieces of BlockcraftAPI's `game` package, so every game reads
alike. Every game's `config.yml` shares `players.min` (the lobby counts down by itself from there), `countdown.lobby`,
`countdown.start` (what `/start` shortens the lobby to), `countdown.restart`, `idle-restart-minutes`, `timer-bar` and
`global-stats-head`. `/start` (`blockcraft.game.start`) never starts a round with fewer than two players, and every
round announces its start the same way. The lobby hotbar has the game's own choice in its first slot (BedWars has a
second one, its vote, next to it), stats in the middle, `/start` for staff in slot 7 and the way back to the hub at
the end; the lobby keeps everyone unhurt and its blocks and items in place. The sidebar shows the players with how
many watch, the time when no boss bar does and the map, then the game's own lines, and the winners at the end. A
lobby vote (BedWars' maps, Duell's mode and then its map) marks the voter's choice, offers to leave the map to
chance, confirms the vote to the voter alone and can be decided with `/forcemap` (`blockcraft.game.forcemap`; in
Duell it decides the mode). `/setup` (`blockcraft.game.setup`) places a game's spots, its lobby spot always as
`lobby`.

`/globalmute` and `/chatslow <seconds>|off` (`blockcraft.system.chatslow`) are stored in `bc_settings`. Every Paper
server re-reads both every `chat.network-refresh-seconds` of core's `config.yml` and keeps the last known values while
the database cannot answer. A chat slow longer than core's `chat.cooldown-seconds` replaces that cooldown, and
`blockcraft.core.chat.spam.bypass` skips both.

`/history <player>` shows bans and mutes, including revoked entries, with the display length set by
`history.limit`. It requires `blockcraft.system.history`; `/whois` also shows the totals, along with ping, client
version, coins and how many accounts share an address with the player.

Every login records a keyed hash of its address in `bc_player_addresses` (never the address itself; IPv6 by its /64
prefix), kept for `alts.retention-days` and purged daily. `/alts <player>` (`blockcraft.system.alts`) lists the
accounts that shared one of those addresses, with last login and whether they are banned or muted now. When a
joining account shares an address with a banned one, holders of that permission are told from the same per-login
query; `alts.block-banned-alts` refuses such a login instead.

A player who leaves while frozen and is not back within `freeze.quit-grace-seconds` gets the `/strafe` number
`freeze.quit-punishment` in the name of whoever froze them; 0, the default, only tells the team.

`/report <player>` opens a reason dialog on the backend. `/reports` lists the open reports; each opens as a dialog in
which staff claim it, edit its status and choose catalogue or custom timed punishments. Claims identify the staff account even while it is nicked. "Erledigt"
closes a report instead of deleting it: the row keeps who closed it and when, leaves the lists and the open counts,
and lets the same player report that account again.

`/party promote <player>` transfers leadership to a member. On disconnect, leadership passes to the first remaining
member in join order. Pending invitations follow the new leader and expire when the party disbands.

## Shared modules and upgrades

`common` holds what both platforms use: storage and migrations, purchases, player references and the operation
payloads. `paper/api` holds what every backend shares: menus, dialogs, player tasks and requests to the proxy.
`paper/core` is the backend's commands and enforcement; the games keep their own match rules on top of the shared
countdowns, rewards and menus.

Reports run inside BlockcraftSystem on its database pool and player identity rules. Remove the old BlockcraftReport
jar when deploying this version; the proxy refuses to start while it is loaded. On first start the old plugin's
`config.yml`, `messages.yml` and `ignored-staff.yml` are copied from `plugins/blockcraft-report` into
`plugins/blockcraft-system/reports` where no file exists yet, so messages, cooldown and muted staff survive. The old
plugin's own database section is gone; if it pointed at a different database, move its `Reports` table first.
The staff listed in `ignored-staff.yml` get the `report-notifications` setting turned off on that start, and the
file is renamed to `ignored-staff.yml.imported`; from then on `/report notify` and the settings dialog change it.

Deploy the Paper and Velocity jars together: reports and punishments from a backend travel on `blockcraft:operations`
as versioned payloads with a request id, and the proxy answers with a result. The proxy accepts a request only from
the server the player is on, checks permission and rank itself, and tells the player what happened, a failed database
write included; only an unanswered request times out with an unconfirmed-result message on the backend. Nothing is
retried automatically, because the original request may already have reached storage. Every `blockcraft:main`
message (`ProxyMessage`) starts with a protocol version byte, and a side drops and logs a message whose version
differs from its own, so a Paper jar and a proxy jar from different builds ignore each other on that channel.

Shared tables come from ordered migrations recorded in `bc_schema_migrations`. A start takes a MariaDB lock before
applying what is pending, so servers starting together take turns; a checksum rejects an edited migration. Every
statement is safe to run again because MariaDB commits DDL on its own. Existing report rows and name columns stay as
they are. New reports, claims, punishments, strikes, warns and freezes also record the account UUID of everyone involved, so
a renamed account or a nicked staff member still resolves to who did what. The games' stats tables keep their own setup.

Database work behind a menu goes through `PlayerTasks`: a read that builds a screen is dropped when the player moved
on, opened another screen or disconnected before it finished; a write such as a purchase or a claim always reports
its outcome. Reads that take longer than a few ticks show a loading screen. A failed read keeps Back and Retry actions
available. Failed writes report their outcome. A purchase commits the unlock and the payment
together, so a database failure rolls back both.

## Tests

`./gradlew build` runs the unit tests: player visibility, the operation protocol, the proxy's handling of backend
requests, stale requests, support assignment, party handover and the report configuration import. With
`BLOCKCRAFT_TEST_DB_PORT` set it also runs the MariaDB integration tests: legacy upgrade, concurrent migration
startup, checksum enforcement, report identity and transactional purchases. CI sets it; locally, point it at a
disposable MariaDB:

```sh
BLOCKCRAFT_TEST_DB_PORT=3307 BLOCKCRAFT_TEST_DB_PASSWORD=blockcraft-test ./gradlew build
```

The tests connect as root, create a fresh `bc_test_*` schema per test and drop only those. Dialogs and inventories
still need an in-game smoke test.

## Server list

The ping answer carries the MOTD edited by `/motd`, the cap set by `/setmax`, the icon from `server-icon.png` in
BlockcraftSystem's data folder (a default is copied there on first start; replace it with any 64x64 PNG) and, where
the client would list who is online, the `motd.sample` lines from its `messages.yml`. The second line of the MOTD
names the network's birthday (`calendar.founded`), else greets the account last seen logging in from the pinging
address (the alt detection's address hash) if that player enabled `server-list-greeting`, else is a random
`motd.random` line. Personal greetings default to off because everyone sharing an address can see them.
Friend counts additionally require `server-list.show-friends`, which also defaults to false.

## Player settings

Every remembered choice a player makes is one row in `bc_player_settings`, read by both platforms through
`common`'s `PlayerSettingRepository`. Cosmetic settings store the chosen key for tags, effects, trails, gadgets,
pets, heads, and shirts. The toggles store `on` or `off` and count as their own default while no row exists, so nothing has to be seeded:
`private-messages`, `party-invites`, `friend-requests`, `player-visibility`, `sounds`, `mentions`, and `effects` default to on.
The nick toggles (`auto-nick`, `keep-nick`), `server-list-greeting`, and `auto-recap` default to off.
`report-notifications` defaults to on for staff with `blockcraft.report.notify`. A toggle may name a permission, and then it is only shown to, and only
read for, the players who hold it.

Paper caches a player's settings for as long as they are online (`BlockcraftApi.get().settings()`) and fires
`PlayerSettingsLoadedEvent` once the cache is filled; the lobby waits for it before re-applying a stored "players
hidden". The settings screen is `SettingsDialog` in BlockcraftAPI, opened from the lobby's hotbar, the pause menu or
the profile card (`ProfileMenu`, rank, level, coins and the per-game stats listed in `profile.games` of BlockcraftAPI's
`config.yml`). `Sounds.play` and proxy notifications stay silent for players who turned sounds off. Settings apply immediately,
and saving reports success after the database write completes. Failed writes restore the stored choice when it
can be read. The `effects` toggle controls lobby particles and cosmetic previews.

Right-click a head, shirt, pet, trail, or effect to preview it. Head and shirt previews use a mannequin; preview
entities and particles are visible only to the viewer. Previews leave saved equipment and unlocks unchanged and
end on navigation, teleport, disconnect, or after the lobby's `effects.preview-ticks` duration. Already-equipped
controls and empty remove actions are inactive. Cosmetic selections update the open inventory in place.

The proxy keeps its own view: it reads the table at login and every change reaches it over the `Setting`
subchannel of `blockcraft:main`, the same way nick and vanish do. `/msg` to a player with private messages off is
refused unless the sender holds `blockcraft.system.msg.bypass`, and a party invite to a player with invites off
never arrives.

## Friends

`bc_friends` holds one row per pair, the player who asked first in `requester` and a `status` of `PENDING` or
`ACCEPTED`; the names come from `bc_players`, so a rename follows by itself. An unanswered request expires after
`friends.request-minutes` and a player may have `friends.max` friends (both in BlockcraftSystem's `config.yml`).
`/friend add|accept|deny|remove|list|jump <name>` and `/friends` need `blockcraft.system.friends`; `add` respects
the target's `friend-requests` setting, and a friend who is vanished counts as offline everywhere.

`/friend jump` also works while the friend's round is running: a game server takes no new players then, so the
proxy sends the player over as a spectator, names the friend on the `Spectate` subchannel once they arrive and the
game puts them next to them. Only a game server is watched this way; a lobby and a server that is restarting stay
unavailable.

The lobby's friends item opens `FriendsMenu`, which is drawn from the proxy's answer rather than from the
database: only the proxy knows who is online and on which server, so the Paper side asks over the `Friends`
subchannel and the proxy writes the list back, including whether the friend's round is running. Its toolbar switches
between friends and requests and opens the dialog that sends a request; the list pages like every `ListMenu` and
reloads after accepting or denying a request. Pages and refreshes update the open inventory in place. Typed requests
carry a request ID. Late replies cannot replace newer screens, and failures show a retry action.
`friends.timeout-ticks` in the lobby config controls how long the menu waits.

## Discord linking

BlockcraftSystem owns the table `bc_discord_links` (`player_uuid`, `code`, `code_expires_at`, `discord_id`,
`linked_at`), the Discord bot lives outside this repository and shares the table with it. `/discord link` writes a
fresh six-character `code` with a `code_expires_at` ten minutes out (`discord-code-minutes`) and replaces whatever
code the row held. The bot looks the code up (`code = ? AND code_expires_at > NOW()`), and on a match sets
`discord_id` and `linked_at` and clears `code` and `code_expires_at`; `discord_id` is unique, so one Discord account
links to one player. `/discord unlink` clears `discord_id` and `linked_at` again. Nothing else writes the table, and
`/discord` reads it to tell a player where they stand.

## Resource pack

`resourcepack/src` is the pack the proxy offers at login, zipped by `./gradlew :resourcepack:pack` and staged next to
the proxy plugin by `stagePlugins`. The proxy hashes `plugins/blockcraft-system/blockcraft.zip` at startup, serves it
on `resource-pack.port` (8080 in the compose network) and offers `resource-pack.url` to every client while it is being
configured. The pack is required (`required: true`): a client whose server list entry turns packs off would decline
an optional pack without asking, while a required one is prompted for and declining it kicks. A proxy whose
`config.yml` predates this still says `required: false` and needs the edit by hand. Only the proxy hears the client's
answer, so it tells each backend on arrival (the `Pack` subchannel), and BlockcraftAPI keeps that in `resourcePack()`.
A joining player stays on the loading screen until the client has loaded, declined or failed the pack, at most
`resource-pack.wait-seconds` (15): chat lines that reach a client while it reloads its resources stay garbled, and the
lobby greets every arrival in chat. Only the first offer of a session waits.

Everything the pack changes has a plain fallback, so a player without it sees vanilla menus and text symbols:

- Menu panels are glyphs of the `blockcraft:ui` font, drawn from the inventory title and moved back into the corner
  with negative-width spaces (`UiFont`); `Menu` picks the panel by viewer, skips the pane filler and draws the exit
  and the page buttons with the pack's item models. A menu with a `toolbar` gets the variant whose first row is three
  wide coloured buttons; the icon item sits in the middle slot and the outer slots hold invisible items, so the
  whole bar answers the click. An item built from a `MenuIcon` carries its icon, so every menu draws it with the
  pack's `items/<icon>.json` for pack wearers and as the vanilla item for everyone else; the lobby's and the games'
  hotbars carry the same icons through the pack's item overrides (below), so they need no restyling. A hotbar item is a
  `HotbarItem`, recognised by a tag in its data rather than its material. Every item in a menu gets the
  pack's tooltip (`tooltip_style`, the `gui/sprites/tooltip/menu_*` nine-slices), so the item names read on the same
  dark card as the panel.
- HUD icons are `<icon:name>` in a message (`Icon`): a font glyph with the pack, the symbol under `icons.<name>` in
  BlockcraftAPI's `messages.yml` without. The lobby's and every game's sidebar headings carry them, and the red error
  icon, drawn in its own colours, leads core's answer to a command the server cannot run.
- Rank badges are glyphs of `blockcraft:ui` drawn in each rank's colour (`badges.png`); `RankPrefix` shows a
  viewer the badge with the pack and the LuckPerms prefix text without, in chat, the tab list, above heads, on the lobby
  sidebar and in the profile. `UiFontTest` fails when a badge in `ranks.json` has no glyph.
- Title lettering is `<display>` in a message (`DisplayFont`, `font/display.json`): outlined capitals with two-pixel
  strokes, upper and lower case drawn alike, any other character from the vanilla font. `Notifications` turns it into
  bold text for a client without the pack, so only titles carry it; at title scale about thirteen letters fit a
  1366x768 screen. The countdowns, the round result and BedWars' lost bed use it.
- Vanilla inventory labels remain intact. The pack does not override container translations.
- Short UI sounds live in `assets/blockcraft/sounds/ui`. `FeedbackSound` selects custom sounds when the pack is
  active and vanilla sounds otherwise. `python3 resourcepack/SoundArt.py` regenerates the Ogg Vorbis files with
  Python and FFmpeg. `FeedbackSoundTest` checks that every custom sound has a definition and an audio asset.

- Anything other players can see, such as a held or worn item or a display, keeps its vanilla item and asks for the
  pack's model through custom_model_data: a `MenuIcon` as `blockcraft:icon/<key>`, a 3D model as a `PackModel`
  (`blockcraft:<set>/<name>`). The pack overrides the vanilla item in `assets/minecraft/items/<item>.json` with a
  `select` on that string, whose fallback is the item's own vanilla definition. A client without the pack draws the
  vanilla item, so nobody ever sees a missing model, and no item depends on who holds it. `item_model` set for one
  viewer (`MenuIcon.style`) is only for menus, which nobody else sees. The overrides are written from `MenuIcon` and
  `PackModel` by `./gradlew :paper:api:packOverrides -PclientJar=<minecraft client jar>`, which copies each item's
  vanilla definition out of the client jar (the map inspector downloads one to `run/map-inspector/data`); run it after
  adding an icon or a model, and again for a new Minecraft version. `PackOverridesTest` fails when an override, a case,
  a model or one of its `blockcraft:` textures is missing, or an override is left over.

The textures are drawn by `java resourcepack/PackArt.java resourcepack/src` (panel colours, slots and the pixel
icons live in that file). `PackArt` draws the UI art only. The hand-made 3D models live in
`assets/blockcraft/models/item/<set>/` (`zombies`, `ttt`, `endergames`, `skywars`, `hats`, `gadgets`, `props`), each
named by its `PackModel`. They are cuboids textured with vanilla block textures, which keeps the pack small and the
look vanilla; a model that needs its own texture puts it under `textures/item/<set>/`. Made in Blockbench, a model is
exported with "Java Block/Item"; its elements must stay between -16 and 32 on every axis, so a larger set piece is
scaled up by its display rather than in the model, and textures stay at 16 to 64 pixels, because every change makes
every client download the whole pack again. A held model points along +z and borrows the Zombies guns' display
transforms. The guns' `fixed` transform turns them side-on into the plane where a vanilla item sprite lies on an
item display, so a display shows the same profile with or without the pack. A hat stands on the head's 0..16 cube, worn on an item that is neither armour nor a skull, since the
client draws a worn skull as a skull whatever its model. Item icons use a native 16-pixel grid, transparent margins, and upper-left lighting.
HUD symbols use seven pixels of ink in each eight-pixel tile to align with the text baseline.
A redrawn pack needs `stagePlugins` and a proxy restart, and the client re-downloads it
because the hash changed. `UiFontTest` keeps the code's glyph table in step with `ui.json`. Open menus restyle when the pack becomes active
without losing their page or selection. Settings show the pack status and offer retry after a failed download
or a declined offer.

## Deploy

Copy the jars from `build/libs` plus `LuckPerms` into `plugins/` of the respective server, configure the `database`
section of `plugins/BlockcraftAPI/config.yml` (Paper) and of each Velocity plugin's `config.yml`, put
`blockcraft.zip` next to the proxy plugin's config with `resource-pack.url` reachable from the internet, point LuckPerms on
every server at the same database (`storage-method` and `data` in its `config.yml`), import the ranks (see above),
and point the Paper servers at the proxy with modern forwarding. The servers register themselves with the proxy (see
above).

In containers, environment variables replace the config edits: `BLOCKCRAFT_DB_HOST`, `BLOCKCRAFT_DB_PORT`,
`BLOCKCRAFT_DB_NAME`, `BLOCKCRAFT_DB_USER` and `BLOCKCRAFT_DB_PASSWORD` override the `database` section of every
Blockcraft plugin, `BLOCKCRAFT_NETWORK_<KEY>` an entry of every `network` section (`BLOCKCRAFT_NETWORK_IP` for
`<ip>`), `BLOCKCRAFT_WORLD_TEMPLATE` the map template (see "Maps"), `BLOCKCRAFT_RESOURCE_PACK_URL` the proxy's
`resource-pack.url`, `BLOCKCRAFT_WAKER_TOKEN` its `on-demand.token` (see "Game servers on demand"), and LuckPerms reads `LUCKPERMS_STORAGE_METHOD`, `LUCKPERMS_DATA_ADDRESS` and friends
itself; `docker-compose.yml` shows the full set.

### One host with Docker

`docker-compose.prod.yml` turns the dev network into the public one: MariaDB publishes no port, the resource pack
is served on loopback for a web server in front of it, the passwords, the forwarding secret, the pack address and the
network addresses come from `.env`, and the games under `profiles: [unreleased]` stay off.
Lobby, BedWars, MLG Rush, Duell, Zombies, EnderGames and TTT are enabled by default. Players still connect to
the proxy's port 25565 directly: Minecraft is not HTTP, so neither a web server nor Cloudflare's proxy can carry it,
and the DNS record players use has to point at the host itself. On the host, with Docker, git-lfs (otherwise the maps arrive as pointer files) and JDK 25 (`mise install`):

```sh
git lfs install
git clone https://github.com/Aquitano/BlockcraftMC && cd BlockcraftMC
cp .env.example .env     # fill in the secrets
./gradlew stagePlugins
docker compose up -d
```

To update, pull, run `./gradlew stagePlugins` and restart the servers (`docker compose restart velocity lobby
bedwars mlgrush duell zombies endergames ttt emeraldrush`, plus `waker` when on-demand runs; see "Game servers on demand"). A plugin's `config.yml` and `messages.yml` in its data folder are copies of the bundled defaults, made on
its first start: new keys arrive through the bundled default, but the copy keeps its own value for every key it
already has, so changed texts and renamed config keys do not reach a server by themselves. Move a copy nobody edited
aside before the restart (`docker compose exec bedwars mv /data/plugins/BedWars/messages.yml
/data/plugins/BedWars/messages.yml.bak`) and the plugin writes the current default; carry the edits of an edited one
over by hand. A changed `luckperms/ranks.json` reaches the network only through `lp import ranks` and
`lp networksync` (see "Run locally"); `stagePlugins` already put the new `ranks.json.gz` next to LuckPerms.

With Caddy in front, the pack needs one site block (`RESOURCE_PACK_PORT` 8081, `RESOURCE_PACK_URL`
`https://mc.example.net/blockcraft.zip`):

```
mc.example.net {
	reverse_proxy 127.0.0.1:8081
}
```

`.env` sets `COMPOSE_FILE`, so every `docker compose` command in that directory, the rank import from "Run locally"
included, works on the production setup. An update is `git pull`, `./gradlew stagePlugins`, `docker compose up -d`
and `docker compose restart`, plus the rank import when `ranks.json` changed. Releasing a game means removing its `profiles` line
and, with on-demand on, adding it to `on-demand.groups`. `/map save` writes into the
checkout's `worlds/`, so a map edited on the host is committed from there.

Alt detection stores login addresses only as an HMAC-SHA256 under a secret the proxy generates once into
`plugins/blockcraft-system/address-secret`. Several proxies sharing one database must hash alike, so give them all the
same `BLOCKCRAFT_ADDRESS_SECRET`, which then replaces the file. Changing the secret unlinks every stored hash.

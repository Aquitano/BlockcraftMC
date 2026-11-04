# BlockcraftMC Plugins

Paper and Velocity plugins for the BlockcraftMC minigame network, originally written for Spigot 1.8.8 / BungeeCord in
2018-2019 and ported to Paper 26.3 / Velocity 4 in 2026.

Start with [Build](#build), [Run locally](#run-locally) or [Deploy](#deploy). Other guides: [lobby activities](EASTER_EGGS.md),
[world templates](worlds/README.md), [furniture](nexo/README.md), [performance](dev/performance/README.md),
[map inspector](dev/map-inspector/README.md) and [credits](CREDITS.md).

## Layout

| Path | What it is |
| --- | --- |
| `common/` | Database access, YAML config and messages, shared by Paper and Velocity plugins |
| `paper/api` | BlockcraftAPI: library plugin that owns the database pool, coins, menus and the shared game pieces |
| `paper/core` | BlockcraftCore: staff and utility commands, chat moderation enforcement |
| `paper/lobby` | Lobby: join items, navigator, cosmetics, premium shop, secrets |
| `paper/reports` | ReportsMenu: staff GUI over the reports table |
| `paper/statussigns` | StatusSigns: clickable server-status signs |
| [`paper/replay`](paper/replay/README.md) | BlockcraftReplay: bounded match recording and isolated playback |
| [`paper/archive`](paper/archive/README.md) | Zeitkapsel: old maps of earlier servers to walk through |
| [`paper/bedwars`](paper/bedwars/README.md) | BedWars, a port of rewi-bw |
| [`paper/cores`](paper/cores/README.md) | Cores: red against blue, two beacons each, after GommeHD's mode |
| [`paper/duell`](paper/duell/README.md) | Duell: single games, three-mode tours, ranked queue and direct challenges |
| `paper/emeraldrush` | EmeraldRush minigame |
| [`paper/endergames`](paper/endergames/README.md) | EnderGames: kit survival games in a fresh random world, after GommeHD's mode |
| [`paper/knockbackffa`](paper/knockbackffa/README.md) | Knockback FFA: continuous arena, instant respawns and a shared rotating kit |
| [`paper/mlgrush`](paper/mlgrush/README.md) | MLG Rush: two players, knockback sticks, beds, first to five |
| `paper/quicksg` | QuickSG survival games |
| [`paper/skywars`](paper/skywars/README.md) | SkyWars: everyone on their own island over the void, after GommeHD's mode |
| [`paper/sternenfront`](paper/sternenfront/README.md) | Sternenfront: sci-fi team shooter; in the navigator only with `blockcraft.lobby.preview` while it is tested |
| [`paper/ttt`](paper/ttt/README.md) | TTT, and TTT 2 (group `ttt2`) with chores and sabotage; in the navigator only with `blockcraft.lobby.preview` while it is tested |
| [`paper/zombies`](paper/zombies/README.md) | Solo or co-op Zombies survival |
| `velocity/system` | BlockcraftSystem: reports, moderation, parties, friends, routing and staff tools |
| `resourcepack/` | The client-side look; see [Resource pack](#resource-pack) |
| `waker/` | Starts and stops game servers for the proxy; see [Game servers on demand](#game-servers-on-demand) |
| `map-import/` | Turns an old world into a map template; see [Importing an old 1.8 map](worlds/README.md#importing-an-old-18-map) |
| `worlds/` | One map template per server group, restored at every start; see [Maps](#maps) |
| `dev/`, `prod/` | Config templates for the local dev network and the public host |
| `docker-compose.yml` | The local dev network: MariaDB, Velocity and one Paper server per game mode |
| `docker-compose.prod.yml` | The same network on a public host, layered over the first; see [Deploy](#deploy) |

## Build

Requires JDK 25. `mise install` picks it up from `mise.toml`; the Gradle toolchain downloads one otherwise.

```
./gradlew build
```

Deployable jars land in `<module>/build/libs/<module>-<version>.jar`. The `-slim` jar is a build artefact. Versions live
in `gradle/libs.versions.toml`. `stagePlugins` writes the server versions to `build/docker/runtime.env`, which Compose
loads for Paper and Velocity.

### Client versions

The proxy rejects clients older than `minecraft` in the catalog before login. Every Paper server runs
[ViaVersion](https://github.com/ViaVersion/ViaVersion), so newer clients join as well, as far as the proxy knows their
protocol (Velocity 4.2.0 knows 26.3). A new client version needs a ViaVersion release that supports it, `viaversion`
bumped in the catalog and `./gradlew stagePlugins` again. When the proxy does not know the version yet, it also needs
newer `velocity-api` and `velocity-build` values.

Older clients would need ViaBackwards, compatible resource packs, dialog fallbacks and a lower client minimum after
testing those features. The pack's vanilla fallback does not provide older-client support.

## Run locally

### The whole network in Docker

```
./gradlew stagePlugins   # plugin jars into build/docker/
docker compose up -d     # MariaDB, Velocity on 25565, the waker and every Paper server
docker compose up -d mariadb velocity lobby <game>   # or only what you need
```

Connect to `localhost:25565`. Every Paper server takes 1G of heap or more, so prefer the subset on a small machine.

- `docker compose down` keeps the plugin data in its named volumes, and `docker compose down -v` starts over.
- After a code change, `./gradlew stagePlugins` and `docker compose restart <server>` pick up the new jars.
- `docker compose logs -f lobby` follows a server, and `docker compose exec lobby rcon-cli <command>` runs a command.
- On a fresh database, [import the ranks](#importing-the-ranks).
- In the lobby, `/createsign quicksg` while looking at a sign makes a status sign for that group, `/setspawn qsg` and
  friends place the navigator warps, and `/map save` keeps all of that.

To run a second instance of a game, copy its service under a new name with its own `/data` volume. Avoid `--scale`:
the copies would share one volume, and with it one world folder that every start overwrites.

### One server on the host

```
./gradlew stagePlugins          # stages the runtime settings Compose requires
docker compose up -d mariadb    # MariaDB on localhost:3306, database/user/password "blockcraft"
./gradlew :paper:runServer      # Paper on 25566 with all Paper plugins + LuckPerms, announced as "lobby"
./gradlew :velocity:runVelocity # Velocity on 25565 with all Velocity plugins + LuckPerms
```

- `-Pgame=zombies` runs a game server instead of the lobby, with the map from `worlds/zombies` and its own run directory
  `run/paper-zombies`. A game server stops itself after every round, which also ends the Gradle task.
- `-Pplugins=core,lobby` limits either run task to a subset. `BlockcraftAPI` is always included.
- The run directories under `run/` are seeded from `dev/`. The run tasks accept the Minecraft EULA on your behalf and drop
  `ranks.json.gz` next to LuckPerms. On a fresh database, run `lp import ranks` and `lp user <name> parent set owner` in
  either console once, and `lp import ranks` again after a change to `ranks.json`.

The Docker network and the lobby run task share the database, announce a server called `lobby` and run the map in
`worlds/lobby`, so run one of them at a time.

## Deploy

### One host with Docker

`docker-compose.prod.yml` turns the dev network into the public one. MariaDB publishes no port, and the resource pack is
served on loopback for a web server in front of it. Players connect to the proxy's port 25565 directly. Minecraft is not
HTTP, so neither a web server nor Cloudflare's proxy can carry it, and the DNS record players use has to point at the
host itself.

The host needs Docker, JDK 25 (`mise install`) and git-lfs. Without git-lfs, the maps arrive as pointer files.

```sh
git lfs install
git clone https://github.com/Aquitano/BlockcraftMC && cd BlockcraftMC
cp .env.example .env     # fill in the secrets
./gradlew stagePlugins
docker compose up -d
```

`.env` holds `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `FORWARDING_SECRET`, `WAKER_TOKEN`, `RESOURCE_PACK_PORT`,
`RESOURCE_PACK_URL` and `NETWORK_*`; `.env.example` explains each. It also sets `COMPOSE_FILE`, so every `docker compose`
command in that directory works on the production setup, which is why `.env` never belongs on a dev machine.

After the first start, [import the ranks](#importing-the-ranks) and set the first owner. Every game runs around the clock
until you enable [game servers on demand](#game-servers-on-demand).

The lobby furniture needs the licensed Nexo jar, which stays outside git. Put its path in `BLOCKCRAFT_NEXO_JAR` or pass
`-PnexoJar=...` before every `stagePlugins`, or the servers start without furniture ([Nexo setup](nexo/README.md)).

With Caddy in front, the pack needs one site block (`RESOURCE_PACK_PORT` 8081, `RESOURCE_PACK_URL`
`https://mc.example.net/blockcraft.zip`):

```
mc.example.net {
	reverse_proxy 127.0.0.1:8081
}
```

### Updating

```sh
git pull
./gradlew stagePlugins
docker compose up -d     # applies Compose changes
docker compose restart velocity lobby replay bedwars mlgrush duell zombies endergames ttt ttt2 emeraldrush archive quicksg cores sternenfront skywars knockbackffa
```

- Add `waker` to the restart when on-demand runs. Both commands start every game again, and the proxy stops each after
  `idle-stop-minutes`.
- New and changed plugin defaults arrive with the jar. A renamed key needs its local override renamed by hand.
- A changed `luckperms/ranks.json` reaches the network only through [the rank import](#importing-the-ranks).
  `stagePlugins` already put the new `ranks.json.gz` next to LuckPerms.
- `/map save` writes into the checkout's `worlds/`, so a map edited on the host is committed from there.

Adding a game means a service in `docker-compose.yml` named like its group, an entry in `docker-compose.prod.yml`, and
the group in `x-on-demand-groups`.

### Alt detection secret

Alt detection stores login addresses only as an HMAC-SHA256 under a secret the proxy generates once into
`plugins/blockcraft-system/address-secret`. Several proxies sharing one database must hash alike, so give them all the
same `BLOCKCRAFT_ADDRESS_SECRET`, which then replaces the file. Changing the secret unlinks every stored hash.

### Without containers

1. Copy the jars from `build/libs` plus LuckPerms into `plugins/` of the respective server.
2. Configure the `database` section of `plugins/BlockcraftAPI/config.yml` (Paper) and of each Velocity plugin's
   `config.yml`.
3. Put `blockcraft.zip` next to the proxy plugin's config, with `resource-pack.url` reachable from the internet.
4. Point LuckPerms on every server at the same database (`storage-method` and `data` in its `config.yml`).
5. [Import the ranks](#importing-the-ranks).
6. Point the Paper servers at the proxy with modern forwarding. They register themselves with it.

### Environment variables

In containers, these replace the config edits.

| Variable | Replaces |
| --- | --- |
| `BLOCKCRAFT_DB_HOST`, `_PORT`, `_NAME`, `_USER`, `_PASSWORD` | The `database` section of every Blockcraft plugin |
| `BLOCKCRAFT_NETWORK_<KEY>` | An entry of every `network` section, such as `BLOCKCRAFT_NETWORK_IP` for `<ip>` |
| `BLOCKCRAFT_SERVER_NAME`, `_GROUP`, `_HOST` | What a server announces; see [Server discovery and groups](#server-discovery-and-groups) |
| `BLOCKCRAFT_WORLD_TEMPLATE` | `world.template`; see [Maps](#maps) |
| `BLOCKCRAFT_WAITING_TEMPLATE` | `waiting-lobby.template`, the [shared waiting island](worlds/README.md#shared-waiting-island) |
| `BLOCKCRAFT_REPLAY_DIRECTORY` | Where recordings are stored |
| `BLOCKCRAFT_TTT_MODE` | `tasks` makes a TTT server play TTT 2 |
| `BLOCKCRAFT_RESOURCE_PACK_URL` | The proxy's `resource-pack.url` |
| `BLOCKCRAFT_WAKER_TOKEN` | The proxy's `on-demand.token` |
| `BLOCKCRAFT_ON_DEMAND_GROUPS` | Names the groups the proxy starts on demand; it has no config key |
| `BLOCKCRAFT_ALERTS_WEBHOOK_URL` | The proxy's `alerts.webhook-url`; the compose files do not pass it |
| `BLOCKCRAFT_ADDRESS_SECRET` | The proxy's [`address-secret` file](#alt-detection-secret) |

LuckPerms reads `LUCKPERMS_STORAGE_METHOD`, `LUCKPERMS_DATA_ADDRESS` and friends itself; `docker-compose.yml` shows the
full set. At build time, `BLOCKCRAFT_BUILD` appends to the plugin version, so `/serverinfo plugins` identifies a CI build
by its Git commit.

## Maps

A map is one folder: the world plus a `blockcraft/` directory inside it, where every plugin keeps what belongs to the
map (`blockcraft/<Plugin>/locations.yml`, `blockcraft/StatusSigns/signs.yml`). `blockcraft/map.yml` holds its `name` and
`builders`; without one, the map is called after its template directory.

Every server group has a template directory, `worlds/<group>`, mounted into the container and named by
`BLOCKCRAFT_WORLD_TEMPLATE` or `world.template` in `plugins/BlockcraftAPI/config.yml`. BlockcraftAPI replaces the world
folder with a copy of the template before Paper loads it, so a restart resets the map. A template without a `level.dat`
is left alone and the server keeps whatever world it has, which is how a map is made in the first place. No template at
all means a plain persistent world.

[Change a map](worlds/README.md#change-a-map) and [importing an old 1.8 map](worlds/README.md#importing-an-old-18-map) have
the steps. `/build` needs `blockcraft.build`, and `/map save` needs `blockcraft.map`.

- A `/setup` on a server without `/map save` is gone at the next restart, like any map edit. That is the point.
- The running server publishes its own copy of `map.yml` too, so edit that file in the template and restart rather than
  while the server runs.
- The save goes to `.staging` inside the template first, and only a finished copy replaces the old contents. A save that
  fails halfway leaves the template as it was, and the next start or save puts a swap that was cut short back together.
- Plugin files in `plugins/<Plugin>` from before this layout move into the world's `blockcraft/` directory on the first
  start by themselves.

## Ranks

Ranks and permissions are handled by [LuckPerms](https://luckperms.net) on both platforms. The proxy and every Paper
server must share one LuckPerms database, otherwise a rank set on one side is unknown on the other.
`luckperms/ranks.json` is a LuckPerms export with the groups the plugins expect. A group's prefix carries the name
colour, so it ends in a colour code. Its `badge` meta is a glyph of the resource pack's font from U+E300 onwards.

`premium` inherits `default`. `youtuber`, `supporter` and `dev` inherit `premium`, `moderator` inherits `supporter`, `admin`
inherits `dev`, and `owner` inherits `admin`. `dev` holds every `blockcraft.*` and `velocity.command.*` permission except
`/rank`, `admin` adds `luckperms.*` and `/rank`, and `owner` adds `*`. The file lists the rest.

Two groups hold nodes that look odd on purpose:

- `default` negates `minecraft.command.msg`, `minecraft.command.teammsg` and `minecraft.command.me`. Paper grants these
  vanilla chat commands, with their `tell`, `w` and `tm` aliases, to every player, and they reach the chat without
  passing the mute check or the chat filter. Every other group inherits the negation. The proxy's `/msg`, `/tell` and
  `/w` remain.
- `dev` grants `velocity.command.server` by name. `default` closes `/server` to players, and LuckPerms lets such an
  exact node win over any wildcard, so `velocity.command.*` alone would not reopen it for staff.

`/rank <name> <group>` replaces every group a player had, except `premium` bought in the lobby shop when the new group
lacks it. Staff change only players and groups below them by group weight, so the first owner is set from a console.

### Importing the ranks

Import the file into every LuckPerms database, and again after every change to it. In Docker, LuckPerms answers only on
the real console, so run this and read the replies in the log. The third line is only for a fresh database.

```
docker compose exec -u 1000 lobby mc-send-to-console lp import ranks
docker compose exec -u 1000 lobby mc-send-to-console lp networksync
docker compose exec -u 1000 lobby mc-send-to-console lp user <name> parent set owner
```

Without containers, run `gzip -k luckperms/ranks.json`, copy the `.gz` into LuckPerms' data folder and run
`/lp import ranks`.

- The import does not notify the other servers. Without `networksync`, the proxy keeps the groups it loaded at startup.
  On a fresh database those are empty, so every permission check there fails, which also hides all tab completion.
- A player who is online while their rank changes has to rejoin before the client shows completions again.
- The import replaces each group's permissions with the file's and leaves players' groups alone. A permission set by
  hand in game is gone after the next import unless it is in the file.

## Config and messages

Every plugin ships a `messages.yml` (MiniMessage, German by default) and a `config.yml` inside its jar, and never writes
either to its data folder. A file of the same name there holds only the keys an operator changed. Every other key
follows the bundled default of the running version, so an update brings its new texts and settings along.

The `network` addresses and the colour `palette` exist on both sides: in BlockcraftAPI and in the proxy plugins. Changing
either means editing both.

## Server discovery and groups

The proxy keeps no server list. Every Paper server writes a heartbeat row to `bc_servers`, and BlockcraftSystem registers
each server with a fresh heartbeat and drops those that went stale (`discovery` in its `config.yml`). `[servers]` in
`velocity.toml` stays empty, and servers listed there anyway keep their static address.

A server announces itself as `BLOCKCRAFT_SERVER_NAME`, falling back to `HOSTNAME` (the container or pod name), in group
`BLOCKCRAFT_SERVER_GROUP`, falling back to its name, at `BLOCKCRAFT_SERVER_HOST`, falling back to the address of the
interface that reaches the database, on its `server-port`. Set the variables when several servers share a host or the
proxy reaches them through another address. The same works unchanged on Kubernetes: one Deployment per group, where the
pod name becomes the server name and the pod IP its address.

When something fails:

- A Paper server that cannot reach the database at startup shuts itself down instead of running without its plugins, so
  the container's restart policy tries again. The proxy does the same.
- If discovery cannot refresh its data for `discovery.max-snapshot-age-seconds`, the proxy stops new joins to discovered
  servers and stops treating them as idle. Current connections stay in place, and a successful poll opens routing
  again. Static servers keep their usual admission rules.

Players join groups, not servers: games fill the fullest open instance, and lobbies take the emptiest. The heartbeat lags
a few seconds, so the proxy also counts a seat for everyone on the way and writes the count to the row's `occupancy`
column. That is what makes status signs and the navigator stop inviting players when the last seat is taken.

BlockcraftSystem posts to the Discord webhook in `alerts.webhook-url` when no lobby is joinable or a server's milliseconds
per tick stay above `alerts.lag-mspt`, and again when each recovers. An empty URL disables it.

### Game servers on demand

Off by default. With `on-demand.enabled`, only the proxy, MariaDB and the lobby need to run around the clock. A player
who wants into a sleeping group starts it and is sent once its server is up, about a minute later. A round in progress is
never stopped.

`docker-compose.yml` lists the groups once, in `x-on-demand-groups`, and hands the list to the proxy as
`BLOCKCRAFT_ON_DEMAND_GROUPS` and to the waker as `WAKER_SERVICES`. Set `ON_DEMAND_GROUPS` in `.env` to choose a
different comma-separated list, then recreate the proxy and the waker.

The proxy decides, because only it sees seats, parties and rounds. The `waker` service carries it out. It is the only
service with the Docker socket, which is root on the host. So it publishes no port, wants the bearer token `WAKER_TOKEN`
and starts or stops nothing but the on-demand groups' compose services. It only starts existing containers, which
`docker compose up -d` creates. It finds them through the Compose project `COMPOSE_PROJECT_NAME`, by default
`blockcraftmc`, so set that when the checkout's directory has another name.

To enable it on the host:

1. Generate a token with `openssl rand -hex 24` and put it into `.env` as `WAKER_TOKEN`. The proxy reads it as
   `BLOCKCRAFT_WAKER_TOKEN`. An empty token keeps the waker from starting at all.
2. Run `./gradlew stagePlugins`, then `docker compose --profile ondemand up -d waker`.
3. Set `on-demand.enabled: true` in the proxy's `config.yml` in its data volume and recreate the proxy with
   `docker compose up -d velocity`. A `restart` keeps the environment it started with, without the token.

`docker compose stop <game>` puts a game to sleep by hand, and the next click wakes it.

### Rejoining a running round

BedWars, Cores and Sternenfront keep the place of a player who drops out of a running round for `rejoin-seconds`. The
server writes the place to `bc_rejoins`, and the proxy starts the returning player on that server instead of the lobby.
The database's clock decides when a place runs out.

## Network XP and levels

Kills, wins and staying through the round summary award XP (`xp.rewards` in BlockcraftAPI's `config.yml`), stored in
`bc_xp` as one total per player. XP is never spent and grants no combat advantage. Coins are the separate spendable
balance, so buying something never lowers a level. Level rewards live under `achievements.network`.

## Daily reward

`/daily` pays coins once a day in the lobby, and `/lobbyset daily` places an optional courier figure for it. A claim locks
the player's row in `bc_daily_rewards`, moves the streak and credits `VCOINS` in one transaction, so two lobbies cannot pay
the same day twice.

## Nick and vanish across the network

Both states are stored in `bc_nicks` and `bc_vanished`, so every server re-applies them on join. The proxy reads both
tables at login, and the Paper servers report every change over the `blockcraft:main` plugin channel.
`velocity.command.glist` is not granted to players, because Velocity's own list would show real names and vanished staff.

The `/strafe` catalogue stays in the proxy's `config.yml`. BlockcraftSystem mirrors it into `bc_punishment_catalog` at
every start, so the Paper menu can list the numbered punishments without a copy of the file.

## Moderation on the proxy

`/team help` lists every command on that server that the player may run. The console and `rcon-cli` run every core command
that names a target. These notes are what that list does not say.

- **Durations.** `/tempban` and `/tempmute` take one word (`16d`, `12h`, `30m`). The maximum is five years, or less once
  2038 is nearer than that, because the end is stored as a `TIMESTAMP`. A permanent punishment comes from `/strafe`.
- **Chat filter.** `/msg`, `/r` and `/pc` pass the same word and advertisement filter on the proxy. Core and the proxy
  each read their own `chat-filter.yml`, written from the lists in `common` on the first start, so a local edit applies
  to one side only.
- **Global mute and chat slow.** Both are stored in `bc_settings`. Every Paper server re-reads them and keeps the last
  known values while the database cannot answer.
- **Alts.** Every login records a keyed hash of its address in `bc_player_addresses`, never the address itself. The secret
  is under [Alt detection secret](#alt-detection-secret).
- **Reports.** "Erledigt" closes a report instead of deleting it, so the row keeps who closed it and when.
- **Troll tools.** `/troll` has no match or progression integration, so using it during a game does not stop the game's
  stats or rewards.

## Anticheat

[GrimAC](https://grim.ac) runs on every Paper server and not on the proxy. Its punishments only alert, so no violation
kicks or bans anyone, and staff decide with the usual tools. Grim still pulls a player back from a move it rejects,
cancels hits past its own limit, and disconnects a client that stops answering it or floods packets.

`grim/punishments.yml` is staged into `plugins/GrimAC` at every start, so an edit in a container is gone after the next
one. BlockcraftCore turns its `[staff] %vl%` lines into one staff notice on the whole network for everyone with
`blockcraft.core.anticheat.notify`. The notice's violation count is the check's, while the threshold counts the whole
punishment group, so the count can be lower. If Grim's API changes under core, core logs it and runs on without the
notices. If the database stalls and the write queue fills, new `/anticheat history` entries are dropped with a warning.

`owner` negates `grim.alerts`, `grim.exempt`, `grim.disabled`, `grim.nosetback` and `grim.nomodifypacket`, because `*`
would grant them. Those denials cover only the global permissions. The owner's `*` still grants `grim.exempt.<check>`,
`grim.nosetback.<check>` and `grim.nomodifypacket.<check>`, so the owner currently bypasses individual checks. Admins and
the other ranks have no such bypass and can be flagged.

The network's own movement needs no exemptions, because Grim follows what the server sends the client. Plugin knockback,
flight for spectators and staff mode, `/speed` and teleports are all predicted.

To update Grim, change both `grimac` in `gradle/libs.versions.toml` and the Modrinth version id in `build.gradle.kts`'s
`downloadGrimAC`, then run `./gradlew stagePlugins` and restart the game servers. Core compiles against the API classes
of that same jar, because Grim publishes no API artifact for its builds, so a change in Grim's API shows up as a build
error.

After adding Grim to a service's `COPY_PLUGINS_SRC`, recreate that container with
`docker compose up -d --no-deps <service>` before relying on on-demand starts. A restart keeps the old plugin paths, and
the waker only starts existing containers.

## Shared modules and upgrades

**Deploy the Paper and Velocity jars together.** Reports and punishments from a backend travel on
`blockcraft:operations` as versioned payloads with a request id. Every `blockcraft:main` message starts with a protocol
version byte, and a side drops and logs a message whose version differs from its own. So a Paper jar and a proxy jar from
different builds ignore each other on that channel.

The proxy accepts an operation only from the server the player is on and checks permission and rank itself. An unanswered
request times out with an unconfirmed-result message on the backend. Nothing is retried automatically, because the
original request may already have reached storage.

**Migrations.** Shared tables come from ordered migrations recorded in `bc_schema_migrations`.

- A start takes a MariaDB lock before applying what is pending, so servers starting together take turns.
- A checksum rejects an edited migration, so new ones are only ever appended to `SharedSchema`.
- Every statement is safe to run again, because MariaDB commits DDL on its own.
- Existing report rows and name columns stay as they are. New rows also record the account UUID of everyone involved, so
  a renamed account or a nicked staff member still resolves to who did what.

A game's `countdown.restart` below seven seconds is raised to seven, so the result and the return countdown fit before
the final shutdown grace second. Report config and messages live in `plugins/blockcraft-system/reports`.

## Tests

`./gradlew build` runs the unit tests. With `BLOCKCRAFT_TEST_DB_PORT` set it also runs the MariaDB integration tests. CI
sets it; locally, point it at a disposable MariaDB:

```sh
BLOCKCRAFT_TEST_DB_PORT=3307 BLOCKCRAFT_TEST_DB_PASSWORD=blockcraft-test ./gradlew build
```

The tests connect as root, create a fresh `bc_test_*` schema per test and drop only those. Dialogs and inventories still
need an in-game smoke test.

## Server list

The icon is `server-icon.png` in BlockcraftSystem's data folder. A default is copied there on first start, and any 64x64
PNG replaces it. The MOTD's second line can greet the account last seen logging in from the pinging address. That needs
the player's `server-list-greeting` setting and defaults to off, because everyone sharing an address can see it. Friend
counts also need `server-list.show-friends`.

## Player settings

Every remembered choice is one row in `bc_player_settings`. A toggle counts as its own default while no row exists, so
nothing has to be seeded; the defaults are in `PlayerSetting`. SkyWars, EnderGames and QuickSG also remember the last
selected kit there. The proxy reads the table at login, and every change reaches it over the `Setting` subchannel of
`blockcraft:main`.

## Friends

`bc_friends` holds one row per pair, and names come from `bc_players`, so a rename follows by itself. The lobby's friends
menu is drawn from the proxy's answer on the `Friends` subchannel rather than from the database, because only the proxy
knows who is online and where.

## Discord linking

The Discord bot lives outside this repository and shares the table `bc_discord_links` (`player_uuid`, `code`,
`code_expires_at`, `discord_id`, `linked_at`) with BlockcraftSystem. Nothing else writes the table.

- `/discord link` writes a fresh six-character `code` with a `code_expires_at` ten minutes out (`discord-code-minutes`)
  and replaces whatever code the row held.
- The bot looks the code up (`code = ? AND code_expires_at > NOW()`). On a match it sets `discord_id` and `linked_at` and
  clears `code` and `code_expires_at`.
- `discord_id` is unique, so one Discord account links to one player.
- `/discord unlink` clears `discord_id` and `linked_at` again.

## Resource pack

`resourcepack/src` is the pack the proxy offers at login, zipped by `./gradlew :resourcepack:pack` and staged next to the
proxy plugin by `stagePlugins`. The proxy hashes `plugins/blockcraft-system/blockcraft.zip` at startup, serves it on
`resource-pack.port` and offers `resource-pack.url` to every client. A redrawn pack needs `stagePlugins` and a proxy
restart, and the client re-downloads it because the hash changed.

The pack is required. A client whose server list entry turns packs off would decline an optional pack without asking,
while a required one is prompted for and declining it kicks. A proxy whose `config.yml` still says `required: false`
needs the edit by hand.

A joining player stays on the loading screen until the client reports that the required pack was successfully applied.
Downloading alone does not release the player. Declining, failing, discarding, or exceeding `resource-pack.wait-seconds`
disconnects the client with reconnect instructions. The bundled deadline is 120 seconds; existing operator overrides
still apply. A missing required ZIP or a failed HTTP listener also blocks entry. With `required: false`, failures and
timeouts allow entry, and only the session's first offer waits. Only the proxy hears the client's answer, so it tells
each backend on arrival over the `Pack` subchannel.

To regenerate the assets after changing the art generators, adding an icon or model, or updating Minecraft, install
Python 3, uv and FFmpeg and run the command below. Normal builds package the checked-in assets. Use a client jar matching
`minecraft` in `gradle/libs.versions.toml`; the map inspector caches one in `run/map-inspector/data`.

```sh
./gradlew :resourcepack:regenerate -PclientJar=/path/to/minecraft-client.jar
```

[`resourcepack/README.md`](resourcepack/README.md) has the client's constraints and how to make a model.

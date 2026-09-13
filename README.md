# BlockcraftMC Plugins

Paper and Velocity plugins for the BlockcraftMC minigame network, originally written for Spigot 1.8.8 / BungeeCord in
2018-2019 and ported to Paper 26.2 / Velocity 4 in 2026.

## Layout

```
common/                  DB access (HikariCP), YAML config, MiniMessage messages; shared by Paper and Velocity plugins
paper/api                BlockcraftAPI: library plugin owning the database pool, coins, menus, items, shared helpers
paper/core               BlockcraftCore: staff/utility commands, chat moderation enforcement
paper/lobby              Lobby: join items, server compass, cosmetics, premium shop, secrets
paper/duell              Duell: 1v1 duels (vs, sg, superjump)
paper/emeraldrush        EmeraldRush minigame
paper/quicksg            QuickSG survival games
paper/reports            ReportsMenu: staff GUI over the reports table
paper/statussigns        StatusSigns: clickable server-status signs
velocity/system          BlockcraftSystem: bans, mutes, strikes, warns, party, hub, MOTD, staff tools
velocity/report          BlockcraftReport: /report on the proxy
dev/                     Config templates for the local dev network (see below)
docker-compose.yml       The local dev network: MariaDB, Velocity and one Paper server per game mode
```

Ranks and permissions are handled by [LuckPerms](https://luckperms.net) on both platforms; the plugins check
`group.<rank>` permissions and assign groups through the LuckPerms API. The proxy and every Paper server must share
one LuckPerms database, otherwise a rank set on one side is unknown on the other.

## Ranks

`luckperms/ranks.json` is a LuckPerms export with the groups the plugins expect. Chat and the lobby tab list render
the group's prefix in front of the name; the prefix carries the name colour, so it ends in a colour code.

| Group      | Prefix             | Grants                                                                            |
|------------|--------------------|-----------------------------------------------------------------------------------|
| `default`  | `&7SPIELER &8● &7` | party invite and kick, `/msg` and `/r`, `/seen`, `/playtime`, `/server`, `/glist` |
| `premium`  | `&6PREMIUM &8● &6` | premium lobby area, double jump, join when full, `/joinme`, chat colours          |
| `youtuber` | `&5YT &8● &5`      | premium plus `/joinme` without delay, `/bc`, `/send`, `/nick`                     |
| `dev`      | `&bDEV &8● &b`     | premium plus every `blockcraft.*` and `velocity.command.*` permission             |
| `admin`    | `&4ADMIN &8● &4`   | dev plus `luckperms.*`                                                            |
| `owner`    | `&bOWNER &8● &b`   | admin plus `*`                                                                    |

Import it once per LuckPerms database (`gzip -k luckperms/ranks.json`, copy the `.gz` into LuckPerms' data folder,
`/lp import ranks`), then assign players with `/lp user <name> parent set <group>`.

Every plugin ships a `messages.yml` (MiniMessage, German by default) and a `config.yml`; both are copied to the plugin's
data folder on first start and can be edited there.

## Build

Requires JDK 25 (`mise install` picks it up from `mise.toml`; the Gradle toolchain downloads one otherwise).

```
./gradlew build
```

Deployable jars land in `<module>/build/libs/<module>-<version>.jar` (the `-slim` jar is a build artefact).
Paper plugins declare HikariCP and the MariaDB driver in `plugin.yml` `libraries`, so only `BlockcraftAPI` needs them
and they are downloaded by the server. Velocity plugins shade them.

Versions live in `gradle/libs.versions.toml` (`paper-api`, `velocity-api`, `minecraft`).

## Run locally

### The whole network in Docker

```
./gradlew stagePlugins   # plugin jars into build/docker/
docker compose up -d     # MariaDB, Velocity on 25565, Paper servers lobby, duell, emeraldrush and quicksg
```

Connect to `localhost:25565`. Every Paper server runs BlockcraftAPI, BlockcraftCore and its game plugin (the lobby
also StatusSigns and ReportsMenu); `docker-compose.yml` is the place that says which server runs what. Worlds and
plugin data live in named volumes: `docker compose down` keeps them, `docker compose down -v` starts over. After a
code change, `./gradlew stagePlugins` and `docker compose restart <server>` pick up the new jars.

In the lobby, `/createsign quicksg` while looking at a sign makes a status sign for that group, `/setspawn qsg` and
friends place the navigator warps, and `/play quicksg` joins a game from anywhere. To run several instances of a
game, remove that service's `hostname:` line (instances then announce themselves under their container id) and
`docker compose up -d --scale quicksg=3`.

`docker compose logs -f lobby` follows a server and `docker compose exec lobby rcon-cli <command>` runs a command.
LuckPerms answers only on the real console, so on a fresh database run

```
docker compose exec -u 1000 lobby mc-send-to-console lp import ranks
docker compose exec -u 1000 lobby mc-send-to-console lp user <name> parent set owner
```

and read the replies in the log.

### One server on the host

```
docker compose up -d mariadb    # MariaDB on localhost:3306, database/user/password "blockcraft"
./gradlew :paper:runServer      # Paper 26.2 on 25566 with all Paper plugins + LuckPerms, announced as "lobby"
./gradlew :velocity:runVelocity # Velocity on 25565 with all Velocity plugins + LuckPerms
```

`-Pplugins=core,lobby` limits either run task to a subset (`BlockcraftAPI` is always included). The run directories
under `run/` are seeded from `dev/` (proxy forwarding, ports, dev secret, LuckPerms on the compose MariaDB so proxy
and backend share one permission database) and are git-ignored. The dev run tasks accept the Minecraft EULA on your
behalf and drop `ranks.json.gz` next to LuckPerms; on a fresh database run `lp import ranks` and
`lp user <name> parent set owner` in either console once.

Both flavours share the database and announce a server called `lobby`, so run one of them at a time.

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

A server announces itself as `BLOCKCRAFT_SERVER_NAME`, falling back to `HOSTNAME` (the container or pod name), in
group `BLOCKCRAFT_SERVER_GROUP`, falling back to its name, at `BLOCKCRAFT_SERVER_HOST`, falling back to the address
of the interface that reaches the database, on its `server-port`. Set the variables when several servers share a
host or the proxy reaches them through another address. The same works unchanged on Kubernetes: one Deployment per
group, the pod name becomes the server name and the pod IP its address.

## Deploy

Copy the jars from `build/libs` plus `LuckPerms` into `plugins/` of the respective server, configure the `database`
section of `plugins/BlockcraftAPI/config.yml` (Paper) and of each Velocity plugin's `config.yml`, point LuckPerms on
every server at the same database (`storage-method` and `data` in its `config.yml`), import the ranks (see above),
and point the Paper servers at the proxy with modern forwarding. The servers register themselves with the proxy (see
above).

In containers, environment variables replace the config edits: `BLOCKCRAFT_DB_HOST`, `BLOCKCRAFT_DB_PORT`,
`BLOCKCRAFT_DB_NAME`, `BLOCKCRAFT_DB_USER` and `BLOCKCRAFT_DB_PASSWORD` override the `database` section of every
Blockcraft plugin, and LuckPerms reads `LUCKPERMS_STORAGE_METHOD`, `LUCKPERMS_DATA_ADDRESS` and friends itself;
`docker-compose.yml` shows the full set.

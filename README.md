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
velocity/system          BlockcraftSystem: reports, moderation, parties, friends, routing and staff tools
dev/                     Config templates for the local dev network (see below)
docker-compose.yml       The local dev network: MariaDB, Velocity and one Paper server per game mode
```

Ranks and permissions are handled by [LuckPerms](https://luckperms.net) on both platforms; the plugins check
`group.<rank>` permissions and assign groups through the LuckPerms API. The proxy and every Paper server must share
one LuckPerms database, otherwise a rank set on one side is unknown on the other.

## Ranks

`luckperms/ranks.json` is a LuckPerms export with the groups the plugins expect. Chat and the tab list of every
Paper server render the group's prefix in front of the name and sort the tab list by group weight; the prefix
carries the name colour, so it ends in a colour code.

| Group      | Prefix             | Grants                                                                            |
|------------|--------------------|-----------------------------------------------------------------------------------|
| `default`  | `&7SPIELER &8● &7` | party invite and kick, `/msg` and `/r`, `/friend`, `/seen`, `/playtime`, `/discord`, `/server` |
| `premium`  | `&6PREMIUM &8● &6` | premium lobby area, double jump, join when full, `/joinme`, chat colours          |
| `youtuber` | `&5YT &8● &5`      | premium plus `/joinme` without delay, `/bc`, `/send`, `/nick`                     |
| `dev`      | `&bDEV &8● &b`     | premium plus every `blockcraft.*` and `velocity.command.*` permission             |
| `admin`    | `&4ADMIN &8● &4`   | dev plus `luckperms.*`                                                            |
| `owner`    | `&bOWNER &8● &b`   | admin plus `*`                                                                    |

Import it once per LuckPerms database (`gzip -k luckperms/ranks.json`, copy the `.gz` into LuckPerms' data folder,
`/lp import ranks`), then assign players with `/lp user <name> parent set <group>`.

Every plugin ships a `messages.yml` (MiniMessage, German by default) and a `config.yml`; both are copied to the plugin's
data folder on first start and can be edited there. Keys the edited file lacks come from the bundled default, so a
plugin update never runs on missing settings.

Two sets of tags resolve in every message. The network addresses (`<ip>`, `<website>`, `<shop>`, `<teamspeak>`,
`<discord>`) come from `network` in `BlockcraftAPI/config.yml` on the Paper side and from `network` in
BlockcraftSystem's `config.yml` on the proxy, so the server address is written once. The colour palette
(`<primary>`, `<accent>`, `<muted>`, `<dark>`, `<success>`, `<error>`, plus `<bullet>` and `<arrow>` for the two
list shapes) comes from `palette` in `BlockcraftAPI/messages.yml` and from the same section in the proxy plugins'
`messages.yml`; recolouring the network means editing that one section.

## Build

Requires JDK 25 (`mise install` picks it up from `mise.toml`; the Gradle toolchain downloads one otherwise).

```
./gradlew build
```

Deployable jars land in `<module>/build/libs/<module>-<version>.jar` (the `-slim` jar is a build artefact).
`BlockcraftAPI` shades HikariCP and the MariaDB driver; the other Paper plugins reach the pool through it. Velocity
plugins shade them too.

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

When a discovery poll finds no joinable server left in the lobby group, BlockcraftSystem posts the `alerts.no-lobby`
text to the Discord webhook in `alerts.webhook-url`, and `alerts.lobby-back` once a lobby is joinable again. Each
change is announced once and at most one alert goes out per `alerts.min-interval-seconds`; an empty URL disables it.

## Network XP and levels

Every kill and win in a game awards XP through BlockcraftAPI (`xp.rewards` in its `config.yml`), stored in `bc_xp`
as one total per player. The level follows a fixed curve: each level costs `xp.level-step` more than the one before,
so with the default of 100 level 2 is 100 XP, level 3 is 300 and level 10 is 4500. Awards show in the action bar,
a level-up in chat with the level-up sound; `/level` shows the own progress, `/level <player>` needs
`blockcraft.level.others`, and chat prefixes every message with the level (`chat.level` in core's `messages.yml`).

## Nick and vanish across the network

Both states are set on a Paper server (`/nick`, `/v`, `/staff`) and stored in `bc_nicks` and `bc_vanished` so every
server re-applies them on join. The proxy keeps its own view: it reads both tables when a player logs in and the
Paper servers report every change over the `blockcraft:main` plugin channel. Player counts (`/online`, `/list`, the
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
`30m`. `/support list` shows the open support requests, waiting ones first and clickable to accept, and staff who
log in while requests are waiting are told so. When a player with open reports or a punishment from the last
`watch.recent-days` logs in, everyone with `blockcraft.system.team` gets a line that opens `/whois` on click, from
one query per login. A message the chat filter dropped reaches the staff with `[Goto]`, `[Mute]` and `[Strafe]`
behind it, which run `/goto` or pre-fill `/tempmute` and `/strafe` for that player.

On the Paper side `/team help` lists every command registered on that server through `CommandRegistry` that the
player may run, with its `commands.<name>.description`, so there is no hand-written list to go stale. `/invsee`
shows another player's inventory read-only unless the viewer holds `blockcraft.core.invsee.edit`.

`/history <player>` shows bans and mutes, including revoked entries, with the display length set by
`history.limit`. It requires `blockcraft.system.history`; `/whois` also shows the totals.

`/report <player>` opens a reason dialog on the backend. Staff can claim reports, edit their status and choose
catalogue or custom timed punishments. Claims identify the staff account even while it is nicked.

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

Deploy the Paper and Velocity jars together: reports and punishments from a backend travel on `blockcraft:operations`
as versioned payloads with a request id, and the proxy answers with a result. The proxy accepts a request only from
the server the player is on, checks permission and rank itself, and tells the player what happened, a failed database
write included; only an unanswered request times out with an unconfirmed-result message on the backend. Nothing is
retried automatically, because the original request may already have reached storage.

Shared tables come from ordered migrations recorded in `bc_schema_migrations`. A start takes a MariaDB lock before
applying what is pending, so servers starting together take turns; a checksum rejects an edited migration. Every
statement is safe to run again because MariaDB commits DDL on its own. Existing report rows and name columns stay as
they are. New reports and claims also record the account UUID of everyone involved, so a renamed account
still resolves to who did what. The games' stats tables keep their own setup.

Database work behind a menu goes through `PlayerTasks`: a read that builds a screen is dropped when the player moved
on, opened another screen or disconnected before it finished; a write such as a purchase or a claim always reports
its outcome. A failed query is logged and answered with one message. A purchase commits the unlock and the payment
together, so a database failure rolls back both.

## Tests

`./gradlew build` runs the unit tests: player visibility, the operation protocol, the proxy's handling of backend
requests, stale requests and the report configuration import. With `BLOCKCRAFT_TEST_DB_PORT` set it also runs the
MariaDB integration tests: legacy upgrade, concurrent migration startup, checksum enforcement, report identity and
transactional purchases. CI sets it; locally, point it at a disposable MariaDB:

```sh
BLOCKCRAFT_TEST_DB_PORT=3307 BLOCKCRAFT_TEST_DB_PASSWORD=blockcraft-test ./gradlew build
```

The tests connect as root, create a fresh `bc_test_*` schema per test and drop only those. Dialogs and inventories
still need an in-game smoke test.

## Server list

The ping answer carries the MOTD edited by `/motd`, the cap set by `/setmax`, the icon from `server-icon.png` in
BlockcraftSystem's data folder (a default is copied there on first start; replace it with any 64x64 PNG) and, where
the client would list who is online, the `motd.sample` lines from its `messages.yml`.

## Player settings

Every remembered choice a player makes is one row in `bc_player_settings`, read by both platforms through
`common`'s `PlayerSettingRepository`. The cosmetic ones store the chosen key (chat tag, kill and win effect); the
five toggles (`private-messages`, `party-invites`, `friend-requests`, `player-visibility`, `sounds`) store `on` or
`off` and count as on while no row exists, so nothing has to be seeded.

Paper caches a player's settings for as long as they are online (`BlockcraftApi.get().settings()`) and fires
`PlayerSettingsLoadedEvent` once the cache is filled; the lobby waits for it before re-applying a stored "players
hidden". The settings screen is `SettingsDialog` in BlockcraftAPI, opened from the lobby's hotbar, the pause menu or
the profile card (`ProfileMenu`, rank, level, coins and the per-game stats listed in `profile.games` of BlockcraftAPI's
`config.yml`). `Sounds.play` stays silent for a player who turned the sounds off.

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

The lobby's friends item opens `FriendsMenu`, which is drawn from the proxy's answer rather than from the
database: only the proxy knows who is online and on which server, so the Paper side asks over the `Friends`
subchannel and the proxy writes the list back. Clicking a friend, or accepting and denying a request, runs the
matching `/friend` command through the `Command` subchannel.

## Discord linking

BlockcraftSystem owns the table `bc_discord_links` (`player_uuid`, `code`, `code_expires_at`, `discord_id`,
`linked_at`), the Discord bot lives outside this repository and shares the table with it. `/discord link` writes a
fresh six-character `code` with a `code_expires_at` ten minutes out (`discord-code-minutes`) and replaces whatever
code the row held. The bot looks the code up (`code = ? AND code_expires_at > NOW()`), and on a match sets
`discord_id` and `linked_at` and clears `code` and `code_expires_at`; `discord_id` is unique, so one Discord account
links to one player. `/discord unlink` clears `discord_id` and `linked_at` again. Nothing else writes the table, and
`/discord` reads it to tell a player where they stand.

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

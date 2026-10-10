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
resourcepack/            The client-side look: menu panels, HUD icons and button textures (see "Resource pack")
dev/                     Config templates for the local dev network (see below)
worlds/                  One map template per server group, restored at every start (see "Maps")
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
| `premium`  | `&6PREMIUM &8● &6` | default plus premium lobby area, double jump, join when full, `/joinme`, chat colours, own `/ptime` kept across servers |
| `youtuber` | `&5YT &8● &5`      | premium plus `/joinme` without delay, `/bc`, `/send`, `/nick`                     |
| `dev`      | `&bDEV &8● &b`     | premium plus every `blockcraft.*` and `velocity.command.*` permission             |
| `admin`    | `&4ADMIN &8● &4`   | dev plus `luckperms.*`                                                            |
| `owner`    | `&bOWNER &8● &b`   | admin plus `*`                                                                    |

`default` negates `minecraft.command.msg`, `minecraft.command.teammsg` and `minecraft.command.me`. Paper
grants these vanilla chat commands, with their `tell`, `w` and `tm` aliases, to every player, and they reach the
chat without passing the mute check or the chat filter. Every other group inherits `default` and loses them as well; the
proxy's `/msg`, `/tell` and `/w` remain.

Import it once per LuckPerms database (`gzip -k luckperms/ranks.json`, copy the `.gz` into LuckPerms' data folder,
`/lp import ranks`), then assign players with `/lp user <name> parent set <group>`.

Every plugin ships a `messages.yml` (MiniMessage, German by default) and a `config.yml`; both are copied to the plugin's
data folder on first start and can be edited there. Keys the edited file lacks come from the bundled default, so a
plugin update never runs on missing settings.

Two sets of tags resolve in every message. The network addresses (`<ip>`, `<website>`, `<shop>`, `<teamspeak>`,
`<discord>`) come from `network` in `BlockcraftAPI/config.yml` on the Paper side and from `network` in
BlockcraftSystem's `config.yml` on the proxy, so the server address is written once. The colour palette
(`<primary>`, `<accent>`, `<muted>`, `<dark>`, `<success>`, `<error>`, plus `<bullet>` and `<arrow>` for the two
list shapes, and `<premium>` for everything Premium) comes from `palette` in `BlockcraftAPI/messages.yml` and from
the same section in the proxy plugins' `messages.yml`; recolouring the network means editing that one section.

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
also StatusSigns and ReportsMenu); `docker-compose.yml` is the place that says which server runs what. Plugin data
lives in named volumes: `docker compose down` keeps it, `docker compose down -v` starts over. The worlds come from
`worlds/<group>` (see "Maps") and are thrown away at every start. After a code change, `./gradlew stagePlugins` and
`docker compose restart <server>` pick up the new jars.

In the lobby, `/createsign quicksg` while looking at a sign makes a status sign for that group, `/setspawn qsg` and
friends place the navigator warps, `/map save` keeps all of that, and `/play quicksg` joins a game from anywhere.
In the compass, left-click a game to play or right-click to visit its lobby area. Cards and status signs share
availability labels and group player counts; the proxy checks availability again when clicked. Unconfigured
lobby areas explain that they are not ready.
To run several instances of a game, remove that service's `hostname:` line (instances then announce themselves
under their container id) and `docker compose up -d --scale quicksg=3`.

`docker compose logs -f lobby` follows a server and `docker compose exec lobby rcon-cli <command>` runs a command.
LuckPerms answers only on the real console, so on a fresh database run

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
./gradlew :paper:runServer      # Paper 26.2 on 25566 with all Paper plugins + LuckPerms, announced as "lobby"
./gradlew :velocity:runVelocity # Velocity on 25565 with all Velocity plugins + LuckPerms
```

`-Pplugins=core,lobby` limits either run task to a subset (`BlockcraftAPI` is always included). The run directories
under `run/` are seeded from `dev/` (proxy forwarding, ports, dev secret, LuckPerms on the compose MariaDB so proxy
and backend share one permission database) and are git-ignored. The dev run tasks accept the Minecraft EULA on your
behalf and drop `ranks.json.gz` next to LuckPerms; on a fresh database run `lp import ranks` and
`lp user <name> parent set owner` in either console once.

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
replaces the world folder with a copy of the template before Paper loads it, so a restart, which every game round
ends in anyway, also resets the map: nothing a round, a gadget or a builder left behind survives. A template
without a `level.dat` is left alone and the server keeps whatever world it has, which is how a map is made in the
first place; no template at all means a plain persistent world.

Changing a map: join the server, `/build` (`blockcraft.build`) lifts the block protection and the inventory lock
for you until you leave, edit, place spawns with the plugin's setup command, then `/map save` (`blockcraft.map`).
That flushes the world and copies it into the template without player data, and every start from then on boots
with the new state; `/map` shows what the server is running. Commit the template like any other change and revert
it like one. Region files go through git-lfs (`.gitattributes`), so `git lfs install` once before committing a map.
A map edited without `/map save` is gone at the next restart, and so is a `/setspawn` on such a server, which is the
point. The running server publishes its own copy of `map.yml` too, so edit that file in the template and restart
rather than while the server runs.

Templates from a server that predates this layout: copy its world folder into `worlds/<group>`, start the server
and set the spawns again, since the plugin files used to live in `plugins/<Plugin>` and pointed at the old world
name. On a server that already runs this layout without a template, those files move into the world's
`blockcraft/` directory on the first start by themselves.

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
one XP total in the action bar; level-ups report the highest reached level in chat. `/level` shows the own progress, `/level <player>` needs
`blockcraft.level.others`, and chat prefixes every message with the level (`chat.level` in core's `messages.yml`).
The lobby experience bar shows this network level and progress. Its boss bar labels coins separately and refreshes
from the database every `hud.refresh-seconds`. Purchases and secret rewards report the new balance in chat.
Game countdowns use boss bars, and Duell displays its kill tally in the sidebar. Levels bought or collected inside
a game are labelled as enchantment levels and do not change network XP.

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
`30m`. `/support list` shows the open support requests, waiting ones first and clickable to accept, and staff who
log in while requests are waiting are told so. When a player with open reports or a punishment from the last
`watch.recent-days` logs in, everyone with `blockcraft.system.team` gets a line that opens `/whois` on click, from
one query per login. A message the chat filter dropped reaches the staff with `[Goto]`, `[Mute]` and `[Strafe]`
behind it, which run `/goto` or pre-fill `/tempmute` and `/strafe` for that player.

On the Paper side `/team help` lists every command registered on that server through `CommandRegistry` that the
player may run, with its `commands.<name>.description`, so there is no hand-written list to go stale. `/invsee`
shows another player's inventory read-only unless the viewer holds `blockcraft.core.invsee.edit`.

Core's `/tp`, `/kill`, `/clear`, `/xp` and `/enchant` take the vanilla argument forms (coordinates, selectors,
item filters, enchantments) and add the rank guard; `/time` is vanilla's, with `/day` and `/night` as shortcuts. The
console and `rcon-cli` run every core command that names a target. Every staff action on a player (gamemode,
teleports, invsee, sudo, clear, kill, heal, fly, speed, vanish, freeze, staff mode, nick, `/build`, `/coins give|take|set`)
is one row in
`bc_staff_actions`, written through `BlockcraftApi.get().audit()`; the actions under `audit.notify` in
BlockcraftAPI's `config.yml` also reach everyone with `blockcraft.audit.notify`. `/stafflog <player> [page]`
(`blockcraft.system.stafflog`) lists what was done by or to a player, newest first.

Every game says the shared moments the same way, from `arena` in BlockcraftAPI's `messages.yml`: the arrival card
with game and map, who joined and left the round with the player count, the lobby countdown, the trackers, the last
minutes and the restart countdown. Each game holds a `Round` for what they share: it seats joiners while the lobby
has room and makes everyone else a spectator, vanished staff included, who are neither announced nor counted; it
reports the phase to the proxy and counts leaving a running round as a death and a loss. Spectators fly unseen in
adventure mode with a hotbar to pick a player, step to the previous or next one and leave; the round keeps them from
touching, taking or opening anything, and while it runs their chat reaches only the other spectators. A round's kills, deaths,
coins and XP are counted in one place (`RoundSummary`), which announces kill
streaks at the lengths `streaks.announce` in BlockcraftAPI's `config.yml` names, writes every participant's stats
once when the round ends (a loss for everyone knocked out or beaten by a winner), prints the recap in chat and
stores it in `bc_round_recaps`; the lobby opens that recap as a dialog after arrival if the player has not opened another screen. The profile
keeps the recap accessible during that lobby session, with a button back into the same game. Recaps older than
`recap.max-age-minutes` are discarded. The `quicksg`,
`emeraldrush`, and `duell` palette tags give the games distinct colours across menus,
signs and sidebars. Their arrival sounds respect the player's sound setting. Kit and perk menus distinguish
selected, unlocked and purchasable items; coins buy permanent unlocks, while EmeraldRush emeralds buy round items.
A game's own `messages.yml` holds its introduction and game-specific wording, including the `menu.stats` and
`menu.global-stats` texts of the shared `StatsMenus` and `/top`. `MessageKeysTest` in `common`
checks that every key a plugin asks its messages for exists in its `messages.yml` or BlockcraftAPI's.

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
the client would list who is online, the `motd.sample` lines from its `messages.yml`.

## Player settings

Every remembered choice a player makes is one row in `bc_player_settings`, read by both platforms through
`common`'s `PlayerSettingRepository`. Cosmetic settings store the chosen key for tags, effects, trails, gadgets,
pets, heads, and shirts. The toggles store `on` or `off` and count as their own default while no row exists, so nothing has to be seeded:
`private-messages`, `party-invites`, `friend-requests`, `player-visibility`, `sounds`, and `effects` default to on, the two nick
toggles (`auto-nick`, `keep-nick`) to off, and `report-notifications` (staff with `blockcraft.report.notify`) to on. A toggle may name a permission, and then it is only shown to, and only
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
subchannel and the proxy writes the list back, including whether the friend's round is running. The menu paginates friends
and requests, accepts names through a dialog, and refreshes after accepting or denying a request. Pages and refreshes
update the open inventory in place. Typed requests
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
configured; `required: true` kicks whoever declines. Only the proxy hears the client's answer, so it tells each
backend on arrival (the `Pack` subchannel), and BlockcraftAPI keeps that in `resourcePack()`.

Everything the pack changes has a plain fallback, so a player without it sees vanilla menus and text symbols:

- Menu panels are glyphs of the `blockcraft:ui` font, drawn from the inventory title and moved back into the corner
  with negative-width spaces (`UiFont`); `Menu` picks the panel by viewer, skips the pane filler and draws the back
  and close buttons with the pack's item models. A menu with a `toolbar` gets the variant whose first row is three
  wide coloured buttons; the icon item sits in the middle slot and the outer slots hold invisible items, so the
  whole bar answers the click. An item built from a `MenuIcon` carries its icon, so every menu draws it with the
  pack's `items/<icon>.json` for pack wearers and as the vanilla item for everyone else; the lobby's and the games'
  hotbars carry the same icons, restyled the moment the proxy's answer about the pack arrives. A hotbar item is a
  `HotbarItem`, recognised by a tag in its data rather than its material. Every item in a menu gets the
  pack's tooltip (`tooltip_style`, the `gui/sprites/tooltip/menu_*` nine-slices), so the item names read on the same
  dark card as the panel.
- HUD icons are `<icon:name>` in a message (`Icon`): a font glyph with the pack, the symbol under `icons.<name>` in
  BlockcraftAPI's `messages.yml` without. The lobby's and every game's sidebar headings carry them.
- Vanilla inventory labels remain intact. The pack does not override container translations.
- Short UI sounds live in `assets/blockcraft/sounds/ui`. `FeedbackSound` selects custom sounds when the pack is
  active and vanilla sounds otherwise. `python3 resourcepack/SoundArt.py` regenerates the Ogg Vorbis files with
  Python and FFmpeg. `FeedbackSoundTest` checks that every custom sound has a definition and an audio asset.

The textures are drawn by `java resourcepack/PackArt.java resourcepack/src` (panel colours, slots and the pixel
icons live in that file); a redrawn pack needs `stagePlugins` and a proxy restart, and the client re-downloads it
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
Blockcraft plugin, `BLOCKCRAFT_WORLD_TEMPLATE` the map template (see "Maps"), and LuckPerms reads
`LUCKPERMS_STORAGE_METHOD`, `LUCKPERMS_DATA_ADDRESS` and friends itself; `docker-compose.yml` shows the full set.

Alt detection stores login addresses only as an HMAC-SHA256 under a secret the proxy generates once into
`plugins/blockcraft-system/address-secret`. Several proxies sharing one database must hash alike, so give them all the
same `BLOCKCRAFT_ADDRESS_SECRET`, which then replaces the file. Changing the secret unlinks every stored hash.

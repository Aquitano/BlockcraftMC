# BlockcraftMC

Paper 26.2 / Velocity 4 plugin monorepo (Java 25, Gradle Kotlin DSL). Layout, build and dev-network instructions are in
README.md; this file only holds what the code does not say by itself.

## Commands

- `./gradlew build` compiles everything with `-Xlint:deprecation`; keep it warning-free.
- `./gradlew stagePlugins && docker compose up -d` boots the whole dev network (proxy plus one Paper server per game
  mode). `docker compose up -d mariadb`, then `./gradlew :paper:runServer` / `./gradlew :velocity:runVelocity` run one
  server on the host instead; `-Pplugins=core,lobby` limits the plugin set. The build runs the unit tests; with
  `BLOCKCRAFT_TEST_DB_PORT` set it also runs the MariaDB integration tests, as CI does. Gameplay changes still need a
  client smoke test on the dev network.

## Conventions

- Commands are Brigadier (`Commands.literal` on Paper, `BrigadierCommand` on Velocity). No `plugin.yml` command entries.
- Every player-facing string lives in the module's `messages.yml` (MiniMessage, German). Tunables live in
  `config.yml`. Never hard-code either; `<prefix>` is available in every message, and so are the colour palette
  (`<primary>`, `<accent>`, `<muted>`, `<dark>`, `<success>`, `<error>`, `<bullet>`, `<arrow>`) and the network
  addresses (`<ip>`, `<website>`, `<shop>`, `<teamspeak>`, `<discord>`). Paper plugins get both from
  `BlockcraftApi.get().tags()` when they load their messages; the proxy plugins build them from their own config.
  Write colours as palette tags, never the raw ones, and the brand only as `<ip>`. A click hint in a menu is
  `<left>`, `<right>` or `<click>` before the action (`<left>Jetzt spielen`), and a menu or dialog title is
  `<arrow><colour>Name`, a detail after `<dark>· `, a game's menus in the game's colour (`<arrow><bedwars>BedWars
  <dark>· <muted>Shop`); `MessageStyleTest` holds both.
- Command descriptions are messages too: `commands.<name>.description`, registered through `CommandRegistry`; core's
  `/team help` is generated from that registry, so never write a command list by hand.
- Shared database tables are initialized once per runtime by `Database.migrate()`. Append to `SharedSchema`; never
  edit a deployed migration. Tables one plugin owns alone (game stats, staff inventories, friends) keep their own
  `createTable`.
- Database access goes through `common`'s `Database` with prepared statements. Table and column names of the legacy
  tables (`VCOINS`, `*_stats`, `*_kits`, `Reports`) stay as they are; new tables are `bc_*`.
- Anything a player unlocks with coins or by finding it (cosmetics, kits, perks, secrets) is one row in `bc_unlocks`
  through `BlockcraftApi.unlocks()`, bought via `BlockcraftApi.shop()`; the legacy `*_kits` tables are only read once for
  the import. No per-plugin unlock tables or YAML stores.
- Anything the network remembers about a player's own choices (cosmetics, on/off toggles) is one row in
  `bc_player_settings` through `common`'s `PlayerSetting`; Paper reads it from `BlockcraftApi.get().settings()` and
  reports every change to the proxy over the `Setting` subchannel. No per-plugin settings store.
- Paper plugins get the one Hikari pool from `BlockcraftApi.get().database()`; they never open their own. Blocking
  work (DB, pings) on hot paths runs through `Tasks.async`; database work behind a menu goes through
  `BlockcraftApi.get().playerTasks()`: `latest` for reads that build a screen, `write` for changes the player must
  hear about. Chat listeners use `AsyncChatEvent` and may block.
- A chest `Menu` is for picking among things with a picture; reading, forms and confirmations are dialogs, one
  `*Dialog` class each, built with `Dialogs.create` and shown through `Dialogs.show`, whose back, close and cancel
  buttons use BlockcraftAPI's `menu.*` messages. Typed text is read with `Dialogs.text`; a form rejected on submit
  reopens with what was entered.
- GUIs extend `Menu` (holder-based, never title matching). Open and close other inventories only via `Menu.open` /
  `Menu.close`; both defer a tick because Bukkit forbids doing it inside `InventoryClickEvent`. `Menu` fills the
  empty slots, plays the click sound and owns the exit in the last slot: `backButton` when another menu leads there,
  `closeButton` otherwise; a coin purchase goes through `ConfirmDialog` first. A list of entries is a `ListMenu`,
  which pages from the middle of the last row and shows loading, failure and an empty list in the middle of the page;
  no menu draws its own pager or loading state.
- A game plugin builds its lobby and round from `paper/api`'s `game` package: `GameConfig` for the shared
  config keys, `Round` (with `clock` for every timed phase), `LobbyCountdown`, `RoundStart`, `LobbyHotbar`,
  `LobbyProtection`, `GameSidebar` sections under the shared frame, `VoteMenu`/`Ballot`/`ForceMapCommand` for a lobby
  vote, and `StartCommand`, `SetupCommand`, `StatsCommand`, `TopCommand`. No game writes its own lobby hotbar,
  /start, sidebar frame or lobby rules, and a game with more to set up than named spots hangs it off
  `SetupCommand.root()`.
- The client-side look lives in `resourcepack/` (README, "Resource pack"): menu panels and HUD icons are glyphs of
  the `blockcraft:ui` font, addressed only through `UiFont` and `<icon:name>`, and never assumed: whatever the pack
  draws must also read without it, because `BlockcraftApi.get().resourcePack().applied(player)` may be false.
- A menu's own controls (exit, pager, toolbar, list notices), every tile that leads to another screen and every
  hotbar item show a `MenuIcon`: the pack model and the vanilla fallback are decided there, never at the call site,
  and `MenuIconTest` fails when the pack lacks one. What a menu lists, sells or lets the player choose (kits, perks,
  cosmetics, maps, heads, achievements) and the buttons that act on it show the real item. `Menu` styles its items for the viewer;
  an item handed out elsewhere goes through `MenuIcon.style` with the viewer's pack status. Top-level menus
  (navigator, profile, extras, shop) put close on the left of the `toolbar` (`closeButton(ToolbarButton.LEFT)`) and
  their two main destinations next to it; list menus keep the corner `closeButton`, because the toolbar takes their
  first row. Sidebar headings carry an `<icon:...>`, resolved with `resourcePack().icons(player)`, and redraw on
  `PlayerResourcePackStatusEvent`.
- Feedback sounds come from `Sounds` in `paper/api` (`Sounds.MENU_CLICK.play(player)`), never from a hand-written
  volume and pitch; only sounds that belong to a world effect stay at their call site.
- Ranks come from LuckPerms (`group.<rank>` permissions, `LuckPermsProvider` API). No rank tables, no rank names in
  code; chat and tab show the group prefix via `Ranks.prefix`, and the groups themselves are seeded from
  `luckperms/ranks.json`. A nick hides the rank too: `Ranks.prefix` and `TabTeams` show the group the nick wears
  (`BlockcraftApi.nickRank`, one of core's `nick.ranks`), permissions stay the player's own.
- Moderation state (bans, mutes, strikes, warns, maintenance, global mute) is in MySQL `bc_*` tables written by the
  proxy; Paper `core` enforces chat-side consequences because Velocity cannot cancel chat for 1.19.1+ clients.
- Backends announce themselves in `bc_servers` (BlockcraftAPI heartbeat with group, state and player count) and the
  proxy registers them at runtime; `velocity.toml` lists no servers. Players join groups, never server names: Paper
  code uses `ProxyMessaging.play`/`hub`, the proxy's `ServerDirectory` picks the instance. Game plugins report their
  phase through `BlockcraftApi.presence()`. Server identity and database settings can come from `BLOCKCRAFT_*`
  environment variables (README, "Server discovery and groups").
- Anything that concerns the whole network (private messages, staff notices, parties, moderation) lives on the
  proxy; Paper plugins only do what needs the world.
- No NMS, no legacy `§` codes in code. Legacy `&` is only parsed for text humans typed (MOTD, chat, LuckPerms prefixes, report statuses).
- Names and structure should carry the meaning; comments only for a non-obvious why.
- Reports live in `velocity/system/report`, using the system pool and visibility-aware player lookup. The retired
  `velocity/report` jar must not run.
- Whoever a row is about or by (reporter, reported, claimer, issuer) is a `PlayerReference`: the account UUID next to
  the name it had at the time; the console has only a name. Names stay for legacy rows and display.
- Reports and punishments from Paper use `BlockcraftApi.operations()` and the common versioned operation codec.
  Commands and plugin messages call the same proxy implementations, which also tell the player the outcome; Paper only
  speaks when no answer arrived. Generic command forwarding remains for other commands.
- Purchases use `common`'s `Shop`: the unlock and coin payment commit in one transaction.

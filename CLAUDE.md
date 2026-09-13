# BlockcraftMC

Paper 26.2 / Velocity 4 plugin monorepo (Java 25, Gradle Kotlin DSL). Layout, build and dev-network instructions are in
README.md; this file only holds what the code does not say by itself.

## Commands

- `./gradlew build` compiles everything with `-Xlint:deprecation`; keep it warning-free.
- `./gradlew stagePlugins && docker compose up -d` boots the whole dev network (proxy plus one Paper server per game
  mode). `docker compose up -d mariadb`, then `./gradlew :paper:runServer` / `./gradlew :velocity:runVelocity` run one
  server on the host instead; `-Pplugins=core,lobby` limits the plugin set. There are no unit tests; a change is
  verified by building and, where it touches gameplay, by clicking through it on the dev network.

## Conventions

- Commands are Brigadier (`Commands.literal` on Paper, `BrigadierCommand` on Velocity). No `plugin.yml` command entries.
- Every player-facing string lives in the module's `messages.yml` (MiniMessage, German). Tunables live in
  `config.yml`. Never hard-code either; `<prefix>` is available in every message.
- Database access goes through `common`'s `Database` with prepared statements. Table and column names of the legacy
  tables (`VCOINS`, `*_stats`, `*_kits`, `Reports`) stay as they are; new tables are `bc_*`.
- Anything a player unlocks with coins or by finding it (cosmetics, kits, perks, secrets) is one row in `bc_unlocks`
  through `BlockcraftApi.unlocks()`, bought via `BlockcraftApi.shop()`; the legacy `*_kits` tables are only read once for
  the import. No per-plugin unlock tables or YAML stores.
- Paper plugins get the one Hikari pool from `BlockcraftApi.get().database()`; they never open their own. Blocking
  work (DB, pings) on hot paths runs through `Tasks.async`, chat listeners use `AsyncChatEvent` and may block.
- GUIs extend `Menu` (holder-based, never title matching). Open and close other inventories only via `Menu.open` /
  `Menu.close`; both defer a tick because Bukkit forbids doing it inside `InventoryClickEvent`.
- Ranks come from LuckPerms (`group.<rank>` permissions, `LuckPermsProvider` API). No rank tables, no rank names in
  code; chat and tab show the group prefix via `Ranks.prefix`, and the groups themselves are seeded from
  `luckperms/ranks.json`.
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

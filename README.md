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
```

Ranks and permissions are handled by [LuckPerms](https://luckperms.net) on both platforms; the plugins check
`group.<rank>` permissions and assign groups through the LuckPerms API.

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

```
docker compose up -d            # MariaDB on localhost:3306, database/user/password "blockcraft"
./gradlew :paper:runServer      # Paper 26.2 on 25566 with all Paper plugins + LuckPerms
./gradlew :velocity:runVelocity # Velocity on 25565 with all Velocity plugins + LuckPerms, forwarding to 25566
```

`-Pplugins=core,lobby` limits either run task to a subset (`BlockcraftAPI` is always included). The run directories
under `run/` are seeded from `dev/` (proxy forwarding, ports, dev secret) and are git-ignored. The dev run tasks accept
the Minecraft EULA on your behalf.

## Deploy

Copy the jars from `build/libs` plus `LuckPerms` into `plugins/` of the respective server, configure the `database`
section of `plugins/BlockcraftAPI/config.yml` (Paper) and of each Velocity plugin's `config.yml`, and point the
Paper servers at the proxy with modern forwarding.

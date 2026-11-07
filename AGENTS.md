# BlockcraftMC

Paper 26.3 / Velocity 4 plugins. This file holds only what the code does not say; read a neighbouring plugin for the rest.

## Checking your work

- Gradle needs Java 25 (`mise.toml`), and a shell's `JAVA_HOME` may point at an older one.
- `./gradlew build` must pass warning-free. `BLOCKCRAFT_TEST_DB_PORT` adds the MariaDB integration tests that CI runs.
- Tests cannot see gameplay. A gameplay change needs a client on the dev network; without one, say what stayed unseen.

## Rules the code cannot enforce

- Before writing a lobby, round, menu or protected world, look in `paper/api`, which already has them.
- A Classic ([README](README.md#games)) keeps its rules. A new idea for one becomes a new group, as TTT 2 does for TTT.
- Player text goes in `messages.yml` (German) and tunables in `config.yml`. A data folder holds only operator overrides, and
  `BundledDefaults.apply(this)` runs before `getConfig()` because Bukkit's `getInt(path, fallback)` ignores bundled defaults.
- `SharedSchema` is append-only. New tables are `bc_*`; legacy table and column names stay.
- `bc_unlocks` and `bc_player_settings` are the only stores for what a player owns and chooses. Ranks live in LuckPerms.
- Database work behind a menu goes through `playerTasks()`.
- Whatever concerns the whole network lives on the proxy. Paper `core` enforces chat punishments only because Velocity
  cannot cancel chat for 1.19.1+ clients.
- Players join groups through `ProxyMessaging`. Only the `waker` service touches Docker.
- Replays see only what Bukkit announces: fire `BlocksChangedEvent` for blocks set directly and `ShotEvent` for hitscan guns,
  and set `Round.playArea` for a fixed arena.
- The proxy requires the resource pack, though a client can be in the world before it loads. Sternenfront assumes the pack;
  shared code and the other games stay readable without it.

# BlockcraftMC

Paper 26.3 / Velocity 4 plugin monorepo (Java 25, Gradle). README.md covers layout, build and the dev network. Everything
else here is what the code does not say by itself; look the rest up in the code, starting with a neighbouring plugin.

## Done means

- `./gradlew build` passes warning-free. With `BLOCKCRAFT_TEST_DB_PORT` set it also runs the MariaDB integration tests.
- A gameplay change is smoke-tested in a client on the dev network.

## Conventions

- Player-facing text lives in the module's `messages.yml` (German, MiniMessage, palette tags) and tunables in `config.yml`.
  `MessageStyleTest` enforces the style.
- The bundled file is the default, and a file in the data folder holds only an operator's overrides. Call
  `BundledDefaults.apply(this)` before `getConfig()`, because Bukkit's `getInt(path, fallback)` ignores bundled defaults.
- Reuse `paper/api` first: the `game` and `kit` packages for lobbies and rounds, `Menu`, `ListMenu` and `Dialogs` for
  screens, `WorldProtection` for worlds players must leave as built, `Sounds` and `MenuIcon` for feedback and controls.
- Blocking work on hot paths runs through `Tasks.async`, and database work behind a menu through `playerTasks()`.
- `SharedSchema` is append-only. New tables are `bc_*`; legacy table and column names stay.
- `bc_unlocks` (`BlockcraftApi.unlocks()`) and `bc_player_settings` (`PlayerSetting`) are the only stores for what a player
  owns and chooses. Ranks live in LuckPerms alone.
- Plugins use the Bukkit and Paper API without NMS. Legacy `&` is parsed only for text humans typed.
- Names and structure carry the meaning; a comment is for a non-obvious why.

## Constraints from outside the code

- **Proxy or Paper:** whatever concerns the whole network lives on the proxy, and Paper does only what needs the world.
  Paper `core` enforces chat-side punishments because Velocity cannot cancel chat for 1.19.1+ clients.
- **Servers:** players join groups through `ProxyMessaging`, never server names. Only the `waker` service touches Docker.
- **Resource pack:** the proxy requires it, but a client can be in the world before it finishes loading. Sternenfront
  assumes the pack; shared code and the other games keep a readable version without it.
- **Replays** only see what Bukkit announces: fire `BlocksChangedEvent` for blocks set directly and `ShotEvent` for hitscan
  guns, and set `Round.playArea` for a fixed arena.

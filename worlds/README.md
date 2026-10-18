# Imported Vanade maps

The `VanadeSicherung` backup supplies four overworld templates:

- `lobby` contains `Lobby/Neue_Lobby`, named Neue Lobby.
- `duell` contains `Duell1/Bucht`, including the waiting lobby and the arenas in that world.
- `quicksg` contains `QSG1/world`, named Mall.
- `emeraldrush` contains `TW1/world`, named Mine. The old plugin was called TradeWars.

The backup does not identify the builders. Each `blockcraft/map.yml` leaves `builders` empty.
The existing BedWars template comes from a separate import.

## Configuration

All active locations use the runtime world name `world` and Bukkit's location serialization.
The lobby has its spawn, Duell, QuickSG, jump, EmeraldRush, shop, premium, and shop NPC locations.
The old source at commit `13cc6d0`, in `Lobby/src/me/blockcraft/lobby/Lobby.java` and
`Lobby/src/me/blockcraft/lobby/commands/CommandSetholo.java`, establishes the key conversions:

- `syndicate` becomes `duell`.
- `superjump` becomes `qsg`.
- `skypvp` becomes `jump`.
- `trapsg` becomes `rush`.
- `hol` becomes `shop`.
- `blaze` becomes `shop-npc`.

The eight signs from `Lobby/plugins/TeleportSigns/TeleportSigns.db` are in
`lobby/blockcraft/StatusSigns/signs.yml`. Duell, QSG, and TW instance targets become the
`duell`, `quicksg`, and `emeraldrush` groups.

Duell has a lobby spawn, four 1vs1 arenas, two SG arenas, and five SuperJump arenas.
The plugin's default map names match the backup's numbered arenas. The third SG arena,
Bergwerk, points to `LobbyMap`, which is absent from the backup. Its two original locations
are retained in `duell/blockcraft/legacy/Duell/locations.yml`. They are not active spawns,
so the existing map vote excludes that arena.

QuickSG has its eight player spawns, lobby, spectator, and both world border centers.
The saved border settings match the current defaults: disabled, with radii of 30 and 60.
The backup has no `DM1` through `DM4` locations. The current QuickSG game does not use those keys.
EmeraldRush has all eleven locations, including its six deathmatch spawns and the search,
shopping, and enchanting locations.

The old HolographicDisplays database is retained under
`lobby/blockcraft/legacy/HolographicDisplays/database.yml` for reference. Its welcome and
shop entries point to the absent `Lobby3` world. Its premium entry points to Neue Lobby.
The current plugin uses entity displays for holograms and has no importer for that database.
The `legacy` directory is reference data and is not loaded by the plugins.

## Storage and validation

All 103 source overworld region files and their 48,070 chunks are retained. The initial
import occupies about 197 MiB. No area of a build was trimmed.
The old shop villager and its two title armor stands were removed from the imported lobby.
The current Lobby plugin creates their replacements at the saved `shop-npc` location.
Every other source entity is retained.
The templates use the Paper 26.2 layout under `dimensions/minecraft/overworld`, with world
data and `level.dat` at the template root. Region, entity, and POI files use the repository's
existing Git LFS rule.

The temporary copies were force-upgraded with the cached Mojang 26.2 server, then loaded
and stopped cleanly with cached Paper 26.2 build 133. Paper's `--forceUpgrade` option reports
that world upgrades are unimplemented in this build. The compose files currently pin build 134.
Every source chunk coordinate was checked against the imported overworld, and the original
files were checked against their SHA-256 hashes after conversion. All 54 active locations
resolve to retained chunks, and all eight imported sign positions contain sign blocks.
An independent review decoded all chunks and checked entity positions, container contents,
and the conversion of old flower-pot and note-block records. A separate JVM check loaded
all 54 locations and eight signs through the current plugin classes with a stub Bukkit world.
The Paper startup checks loaded no Blockcraft plugins. Full plugin startup and multiplayer
gameplay still need the in-game review below.

Player records, statistics, session locks, legacy `uid.dat` files, old level backups, the Nether,
and the End are excluded. Newly generated Paper world UUID metadata remains in the templates.
Plugin jars, logs, permissions, customer records, and database credentials
from the backup are excluded. The original backup is unchanged.

## In-game review

The maps still need a Minecraft client review before deployment:

- Check the lobby warps, signs, shop NPC, and the jump landing. The saved jump location has
  air immediately below it, so its intended landing needs a visual check.
- Confirm that the shop has one villager and one current name card after startup and a
  periodic NPC respawn.
- Place the BedWars navigator warp and the new lobby activities with their setup commands.
  The backup predates those activities.
- Check for old branding and legacy armor stand holograms in the build. Place the current
  game and leaderboard holograms with `/setholo` where needed. The sign at `(-974, 27, 1650)`
  still links to `Vanade.EU`.
- Choose new firework-pad positions if that interaction is wanted. The default `fireworks`
  coordinates in the Lobby plugin configuration belong to another lobby.
- Supply `LobbyMap` to restore Duell's third SG arena, or keep it unavailable.
- Add the builders to each `blockcraft/map.yml` when their identities are known.
- Use `/map save` after map or setup changes to publish them to the template.

These checks did not start or change a production server.

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

## Editable team display

The lobby's team room at `(-974, 27, 1650)` has twelve existing head and sign pairs.
Their positions and roster are in [team-board.yml](lobby/blockcraft/Lobby/team-board.yml).
The initial roster preserves the seven historical names and roles from the imported map.
Replace them with the current team before publishing the display.

Edit `members` using the slot keys already listed under `slots`:

```yaml
members:
  slot-1:
    name: Aquitano
    role: OWNER
  slot-2:
    name: AnotherPlayer
    role: BUILDER
```

Use Minecraft account names for skins. A slot without a member uses `empty-skin` and a vacant-place sign.
The `slots` section controls the layout independently of the roster. Additional slots need existing player
heads and signs at their configured block positions. The title and sign formatting come from Lobby's
`messages.yml`, using the network address and palette.

Edit the template's `blockcraft/Lobby/team-board.yml`, then run `/teamboard check` and `/teamboard apply`.
Both require `blockcraft.lobby.setspawn` and work from the console. Check validates every configured position
and roster entry without changing blocks. Apply stops before editing if validation fails. It updates the
heads, front sign text, and central title, and copies the input file into the active map.
Run `/map save` afterward to keep the rendered blocks with the template.
On an existing server, add `teamboard` to Lobby's `allowed-commands` list if setup staff lack the command bypass.

On the local map preview, edit `run/map-review/preview-worlds/lobby/blockcraft/Lobby/team-board.yml`.
Its saves remain in that preview directory until copied back to `worlds/lobby` deliberately.

## Lobby activity setup

All nine hidden sign secrets are present in the imported lobby and match the current secret names.
The Konami code, chicken-pet eggs, sleeping-player effects, and shooting stars do not need map positions.
Their conditions and settings are described in [Easter eggs and lobby activities](../EASTER_EGGS.md).

The imported map has no pond, parkour, or Easter hiding-place configuration. With the default settings,
`/lobbyset check` reports twelve problems: the pond, course start and finish, leaderboard, seven checkpoints,
and insufficient Easter hiding places. Choose a fishable pond, build or select a playable pressure-plate
course, and register at least ten distinct hiding places using the [setup commands](../EASTER_EGGS.md#configure-the-lobby-map).
The commands record positions; they do not build the activities.

Stand at the desired BedWars navigator arrival and run `/setspawn bedwars`. The BedWars backend is separate
from this lobby warp and can be reached with `/play bedwars` while the warp is unset.
Use `/map save` after recording any of these positions.

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
The initial Paper checks loaded no Blockcraft plugins. A subsequent local Docker preview loaded the
actual plugin stack on pinned Paper build 134, registered all five backend groups, and loaded all eight
status signs. Client appearance and multiplayer gameplay still need the in-game review below.

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
  game and leaderboard holograms with `/setholo` where needed. `/teamboard apply` replaces the legacy
  title and link on the sign at `(-974, 27, 1650)`.
- Choose new firework-pad positions if that interaction is wanted. The default `fireworks`
  coordinates in the Lobby plugin configuration belong to another lobby.
- Supply `LobbyMap` to restore Duell's third SG arena, or keep it unavailable.
- Add the builders to each `blockcraft/map.yml` when their identities are known.
- Use `/map save` after map or setup changes to publish them to the template.

These checks did not start or change a production server.

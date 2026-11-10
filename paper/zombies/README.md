# Zombies

Zombies is wave survival for one to four players on an original starter arena called Außenposten. Players earn gold in
the match, buy doors, power, perks and guns, and hold out until the final wave. Bleeding out removes perks, retains
weapons, and returns the player at the next intermission. In solo play, Quick Revive is consumed to survive one lethal
hit. Losing the last fighter or exceeding a wave's time limit ends the match.

`/zombies` opens the player guide. Right-click fires, left-click reloads, and the third slot holds the knife. Sneak near
a broken window to repair it or near a downed teammate to revive them.

## Run it

From the repository root, build the plugins and start the local services:

```powershell
.\gradlew.bat stagePlugins
docker compose up -d mariadb velocity lobby zombies
```

Join through Velocity and run `/play zombies`. One player starts the lobby countdown, and staff can shorten it with
`/start`. A player who joins after the match starts watches that match.

For the host run task, start the local database first, then run:

```powershell
docker compose up -d mariadb velocity
.\gradlew.bat :paper:runServer -Pgame=zombies -Pplugins=core,zombies
```

Both commands announce the `zombies` server group.

## Configure the map

The saved [Außenposten template](../../worlds/zombies) includes the arena and its map-owned `blockcraft/Zombies/arena.yml`.
Both local launch options restore that template before starting the game.
For a new world without an arena definition, the plugin copies the bundled [arena definition](src/main/resources/arena.yml).
Its generator (`StarterArena`) uses an empty 127 by 127 region above Y=95 and refuses to replace existing blocks.
A fixed seed makes every generated copy identical.
Change `origin` before generation if that region is occupied. Set `starter.build: false` for a hand-built arena.

Edit `rooms`, `windows`, `stations`, and `notes` in the map-owned file to change the layout:

- Window, station and note locations must lie in their named room.
- The `yard` room starts unlocked. Each other room has a gold price and a door box.
- Keep at least one window in `yard`, so the first wave can spawn before players buy a door.
- Door and window block data define the blocks the plugin resets at startup and shutdown.
- A `PERKS` station with a `perk` sells that one; without one it sells them all. A station added to the file after the
  map was saved gets its block from the starter at the next start.
- All eight windows have enclosed spawn rooms, with iron bars above the opening. Mobs approach the boards inside those
  rooms, break them, and walk through the opening. Players cannot enter spawn rooms, even when the adjacent wings are
  open. The courtyard windows lead into locked storerooms, and each outer window has a barred gate in the perimeter
  fence.
- Every waxed copper bulb inside a room is a power light. The plugin switches them off at startup, and they light up in
  order of distance from the power station when the power is bought.
- Each note is a lectern with its text in `messages.yml` under `note.<id>`. The first read unlocks `zombies.note.<id>`
  and pays `rewards.note` network coins.

Use `/setup lobby`, `/setup spawn`, and `/setup spectator` while standing at each desired position.
Run `/setup check` to check the layout and spawn, then `/map save` to publish the map template.
A map save writes the intact doors and barricades, even during a match. It restores the live match state before returning.

## Tune the match

The bundled [config.yml](src/main/resources/config.yml) holds the waves, events, weapons, enemies, perks, prices and
gold. To change a value, put only that key into the server's `plugins/Zombies/config.yml` and restart the game server.

The squad size is sampled at the start of each wave, and temporary deaths do not reduce it. `rewards` pays network coins
through the shared round summary, while gold stays in the match.

## Validate changes

```powershell
.\gradlew.bat :paper:zombies:test
.\gradlew.bat build
```

For MariaDB integration tests, set `BLOCKCRAFT_TEST_DB_PORT` and `BLOCKCRAFT_TEST_DB_PASSWORD` for an isolated test database.
In a Minecraft client, check a solo start, gun controls, a room purchase, power and upgrades, and a cooperative revive.
Check the weapon models and the enemies' headgear with the resource pack both applied and absent. The proxy's
`resource-pack.required` kicks whoever declines the pack, so turn it off in BlockcraftSystem's config for the run
without it.
The [arena playtest checklist](TESTING.md) covers every station, barricade, and player state.

To inspect a saved arena in the local 3D viewer, use the [map inspector](../../dev/map-inspector/README.md):

```powershell
uv run dev/map-inspector/map_inspector.py --maps zombies
```

After editing the running arena, run `/map save` first. The inspector reads `worlds/zombies`, not the running server's world.

The implementation references are recorded in [SOURCES.md](SOURCES.md).

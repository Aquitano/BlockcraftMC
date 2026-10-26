# Run Zombies locally

Zombies supports one to four players and ships an original starter arena called Außenposten.
It uses BlockcraftAPI for its lobby, countdowns, sidebar, setup commands, stats, rewards, and match recap.

From the repository root, build the plugins and start the local services:

```powershell
.\gradlew.bat stagePlugins
docker compose up -d mariadb velocity lobby zombies
```

Join through Velocity and run `/play zombies`. One player starts the 30-second lobby countdown.
Staff can shorten it with `/start`. A player who joins after the match starts watches that match.
The resource pack adds weapon models and a navigator icon. Controls, names, and menus also work without the pack.

For the host run task, start the local database first, then run:

```powershell
docker compose up -d mariadb velocity
.\gradlew.bat :paper:runServer -Pgame=zombies -Pplugins=core,zombies
```

Both commands announce the `zombies` server group.
The production overlay enables Zombies by default.

## Configure the map

The saved [Außenposten template](../../worlds/zombies) includes the arena and its map-owned `blockcraft/Zombies/arena.yml`.
Both local launch options restore that template before starting the game.
For a new world without an arena definition, the plugin copies the bundled [arena definition](src/main/resources/arena.yml).
Its generator uses an empty 127 by 127 region above Y=95 and refuses to replace existing blocks.
A fixed seed makes every generated copy identical.
The courtyard has a watchtower, a radio shelter, a collapsed shelter, a campfire, and the two supply kiosks.
Each wing is furnished for its job: depot racks and a forklift, a workshop jeep on a lift, the generator, and the clinic ward.
The yards between the wings hold a motor pool, a tank farm, a graveyard, and a helipad. A forest surrounds the fence.
Change `origin` before generation if that region is occupied. Set `starter.build: false` for a hand-built arena.

Edit `rooms`, `windows`, `stations`, and `notes` in the map-owned file to change the layout.
Window entries must lie in their named room. Station and note locations must also lie in their named room.
The `yard` room starts unlocked. Each other room has a gold price and a door box.
Door and window block data define the blocks the plugin resets at startup and shutdown.
All eight windows have enclosed spawn rooms. Mobs approach the boards inside those rooms, break them, and walk through the opening.
Iron bars sit above the opening. Players cannot enter spawn rooms, even when the adjacent wings are open.
The courtyard windows lead into locked storerooms. Each outer window has a barred gate in the perimeter fence.

Every waxed copper bulb inside a room is a power light. The plugin switches them off at startup.
When the power is bought, they light up in order of distance from the power station.
Each note is a lectern. Its text lives in `messages.yml` under `note.<id>`.
The first read of a note unlocks `zombies.note.<id>` and pays `rewards.note` network coins.
Keep at least one window in `yard`, so the first wave can spawn before players buy a door.

Use `/setup lobby`, `/setup spawn`, and `/setup spectator` while standing at each desired position.
Run `/setup check` to check the layout and spawn, then `/map save` to publish the map template.
A map save writes the intact doors and barricades, even during a match. It restores the live match state before returning.

## Tune the match

Edit [config.yml](src/main/resources/config.yml) in the plugin's data directory and restart the game server.
`waves` controls the final wave, squad scaling, spawn pacing, health growth, and boss interval.
The squad size is sampled at the start of each wave. Temporary deaths do not reduce the squad size.
`weapons`, `enemies`, `perks`, `prices`, and `gold` hold combat and economy values.
`rewards` pays network coins through the shared round summary. Gold stays in the match.

Open `/zombies` for the player guide. Right-click fires, left-click reloads, and the third slot holds the knife.
Sneak near a broken window to repair it or near a downed teammate to revive them.
Bleeding out removes perks, retains weapons, and returns the player at the next intermission.
In solo play, Quick Revive is consumed to survive one lethal hit. Losing the last fighter or exceeding a wave's time limit ends the match.

## Validate changes

```powershell
.\gradlew.bat :paper:zombies:test
.\gradlew.bat build
```

For MariaDB integration tests, set `BLOCKCRAFT_TEST_DB_PORT` and `BLOCKCRAFT_TEST_DB_PASSWORD` for an isolated test database.
In a Minecraft client, check a solo start, gun controls, a room purchase, power and upgrades, and a cooperative revive.
Check the weapon models with the resource pack both enabled and declined.
The [arena playtest checklist](TESTING.md) covers every station, barricade, and player state.

To inspect a saved arena in the local 3D viewer, use the [map inspector](../../dev/map-inspector/README.md):

```powershell
uv run dev/map-inspector/map_inspector.py --maps zombies
```

After editing the running arena, run `/map save` first. The inspector reads `worlds/zombies`, not the running server's world.

The implementation references are recorded in [SOURCES.md](SOURCES.md).

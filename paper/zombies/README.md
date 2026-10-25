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
The production overlay keeps Zombies behind the `unreleased` profile.

## Configure the map

The saved [Außenposten template](../../worlds/zombies) includes the arena and its map-owned `blockcraft/Zombies/arena.yml`.
Both local launch options restore that template before starting the game.
For a new world without an arena definition, the plugin copies the bundled [arena definition](src/main/resources/arena.yml).
Its generator uses an empty 59 by 59 region above Y=95 and refuses to replace existing blocks.
Change `origin` before generation if that region is occupied. Set `starter.build: false` for a hand-built arena.

Edit `rooms`, `windows`, and `stations` in the map-owned file to change the layout.
Window entries must lie in their named room. Station locations must also lie in their named room.
The `yard` room starts unlocked. Each other room has a gold price and a door box.
Door and window block data define the blocks the plugin resets at startup and shutdown.
The four courtyard windows have enclosed spawn bays with permanent iron bars behind their repairable boards.
Enemies enter through the courtyard windows after breaking the boards, even when the adjacent wings are open.
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
uv run dev/map-inspector/inspect.py --maps zombies
```

After editing the running arena, run `/map save` first. The inspector reads `worlds/zombies`, not the running server's world.

The implementation references are recorded in [SOURCES.md](SOURCES.md).

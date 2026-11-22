# Silo 7 generator reference

Silo 7 is the second Zombies map: a research outpost on a rocky island, built over the sealed silo its creatures came
out of. `python3 -I dev/mapgen/silo7.py` checks the layout and writes the map's functions to `build/mapgen/silo7`, with
the world's `blockcraft/` files under `files/`. `dev/verify/verify.py map silo7` runs them on a bare server over the flat
sea and saves the world to `worlds/silo7`. Coordinates here are relative to the arena's origin `(0, 64, 0)`: players
stand at y 0 on the surface and at y -24 on the silo floor.

## Layout

The yard (Hof) sits in the middle, 41 by 41 and open to the sky. Six zones surround it on a 105-block square inside the
compound wall. Each zone has its own materials, light and threat.

| Room | Id | Area | What is there |
| --- | --- | --- | --- |
| Hof | `yard` | x -20..20, z -20..20 | The cracked blast lid over the silo, armory and armor kiosks, containers, a truck, a guard booth |
| Werkstatt | `workshop` | x -20..20, z -52..-21 | Vehicle hall: car lift with a truck, gantry crane, workbenches, parts cage, the freight lift to the silo |
| Funkturm | `radio_tower` | x 21..52, z -52..-21 | Fenced compound around a station block whose roof is a deck at y 7, reached by one three-wide stairway; mast and dish on the deck |
| Gewächshaus | `greenhouse` | x 21..52, z -20..52 | Glasshouse with raised beds of tainted plants, then an outdoor test field with plots, hedges, a tractor and the quarantine tent |
| Kühlhaus | `cold_store` | x -52..-21, z -52..20 | Low, dark cold store with racks, hanging sacks and frost, lit by a few soul lanterns; an outdoor loading dock with a reefer truck |
| Generatorraum | `power` | x -52..20, z 21..52 | Generator hall with two sets and the switchboard, the blockhouse over the silo stairs; an outdoor tank yard |
| Silo | `silo` | x -20..20, y -26..-1, z -36..38 | The round shaft under the yard: a gallery ring at y -12, two stairs to the floor, the lift foot, the sealed chamber |

## Doors and loops

All doors are 3 wide and 3 tall. They sit in `doors`, each joining two rooms. Buying one opens it and unlocks whichever
of its rooms was still closed, so a zone reached one way can later be joined to its neighbour.

| Door | Rooms | Price |
| --- | --- | ---: |
| `yard-workshop` | Hof, Werkstatt | 750 |
| `yard-greenhouse` | Hof, Gewächshaus | 750 |
| `yard-cold-store` | Hof, Kühlhaus | 1,000 |
| `workshop-radio-tower` | Werkstatt, Funkturm | 1,000 |
| `workshop-cold-store` | Werkstatt, Kühlhaus | 750 |
| `radio-tower-greenhouse` | Funkturm, Gewächshaus | 1,000 |
| `greenhouse-power` | Gewächshaus, Generatorraum | 1,250 |
| `cold-store-power` | Kühlhaus, Generatorraum | 1,250 |
| `silo-gate` | Generatorraum, Silo | 2,500 |

Three doors lead out of the yard, so the first gold decides the direction. The yard has no door to the Generatorraum:
power is two doors deep either way. The doors close these loops:

- Hof, Werkstatt, Kühlhaus, Hof.
- Hof, Werkstatt, Funkturm, Gewächshaus, Hof.
- Hof, Gewächshaus, Generatorraum, Kühlhaus, Hof.
- The outer ring: Werkstatt, Funkturm, Gewächshaus, Generatorraum, Kühlhaus, Werkstatt.

The silo has its own loop: the gallery ring and the floor are joined by two stairs, east (x 9..11, descending south)
and south (z 9..11, descending west).

## Windows

Twenty windows, each 2 wide and 2 tall with bars above, in front of an enclosed spawn bay 8 by 8 and 5 high. The yard's
four bays are closed storerooms inside the neighbouring buildings, set in a pinwheel near the yard's corners. Outer bays
stand outside the compound wall with a barred gate and a partly barred roof. The silo's three bays are cut into the rock;
their openings run through the curved wall, widened to four blocks on the room side.

| Room | Windows |
| --- | --- |
| Hof | `yard-north`, `yard-east`, `yard-south`, `yard-west` |
| Werkstatt | `workshop-west`, `workshop-east` |
| Funkturm | `radio-tower-east`, `radio-tower-north` |
| Gewächshaus | `greenhouse-glass`, `greenhouse-field`, `greenhouse-south` |
| Kühlhaus | `cold-store-hall`, `cold-store-dock`, `cold-store-north` |
| Generatorraum | `power-hall`, `power-tanks`, `power-south` |
| Silo | `silo-east`, `silo-south` on the floor, `silo-gallery` on the ring |

## Stations and notes

| Room | Stations |
| --- | --- |
| Hof | Armory `(-4, 0, 17)`, armor `(5, 0, 17)` |
| Werkstatt | Armory `(18, 0, -46)`, Quick Reload `(-18, 0, -24)` |
| Funkturm | Mystery box on the deck `(44, 7, -32)` |
| Gewächshaus | Extra Health `(44, 0, 3)`, team revive in the tent `(28, 0, 44)` |
| Kühlhaus | Frozen Bullets `(-50, 0, -10)` |
| Generatorraum | Power `(0, 0, 49)`, Quick Revive `(18, 0, 25)` |
| Silo | Upgrade on the gallery `(0, -12, -14)`, armory on the floor `(6, -24, -13)` |

Seven notes, one per room, ids `silo_yard`, `silo_workshop`, `silo_tower`, `silo_greenhouse`, `silo_cold`,
`silo_power` and `silo_lab`. Their text is in the Zombies `messages.yml` under `note.<id>`. They tell what was kept in
the silo and point at the lift and the gate.

## Quest spots

The arena file carries a `quest` section for the optional main quest and its boss. `ArenaMap` reads only the keys it
knows (Bukkit's `YamlConfiguration` is not strict), so the section loads with the current plugin and survives a
`/setup` save.

| Key | Value |
| --- | --- |
| `quest.lift.shaft` | Shaft `x -3..3, y -25..-1, z -34..-28`, under the Werkstatt |
| `quest.lift.grate` | The copper grate over the shaft at y -1; the quest removes it or runs the platform |
| `quest.lift.top`, `quest.lift.bottom` | Platform centres `(0.5, 0, -30.5)` and `(0.5, -24, -30.5)` |
| `quest.lift.controls` | The lever post `(5, 0, -27)` beside the shaft |
| `quest.stairs` | The playable way down behind `silo-gate`: top `(-4.5, -1, 34.5)`, foot on the gallery `(-4.5, -12, 15.5)` |
| `quest.boss.rig` | `sw_rancor` |
| `quest.boss.gate` | The wall the boss smashes: `x -17..-16, y -24..-17, z -3..3`, 7 wide and 8 tall, reinforced deepslate |
| `quest.boss.chamber` | The sealed chamber behind it: `x -27..-18, y -24..-15, z -5..5` |
| `quest.boss.spawn` | `(-22.5, -24, 0.5)`, facing east toward the gate |
| `quest.boss.arena` | Centre `(0, -24, 0)`, radius 14, ceiling y -3 |

The lift foot opens through a 5-wide tunnel onto the silo floor. The gallery is 4 wide and 11 blocks above the floor,
so the floor under it stays clear for the boss.

## Checks

The generator stops on any failed check. Cells count as passable when a player walks through them (air, plants, snow
layers, levers, carpets); partial blocks such as slabs and stairs count as full blocks. Fences, walls, bars and panes
cannot be stood on.

- With every door shut, the spawn reaches only the yard.
- With every door open, every reachable place lies in a room and outside every spawn bay. No reachable place stands
  above y 2 except on the radio tower's deck and stairway. No reachable surface place stands on a half block at y -1,
  which would put a player below the room.
- A player crosses from one room into another only through a door, and each door touches exactly the two rooms it names.
- Each bay holds its enemies while the boards stand, and with the boards gone they reach the window's entry and the spawn.
- A body 2 by 2 blocks and 2 tall reaches every room and every window from the spawn, so doors and corridors are at least
  two wide.
- Every station and note is its block in its room and reachable within 4.5 blocks; every room has a power light.
- With the gate gone, a body 4 by 4 and 7 tall walks from the chamber onto at least 300 floor places of the silo.

The checks do not simulate jumping timing, enemy pathfinding, lighting levels or what a client renders.

## Not used

Nexo furniture can be placed from a map's `blockcraft/scenery/furniture.yml`, but the Zombies map uses none. Each piece
places an interaction hitbox: a right click on it reaches the furniture instead of the gun, the storage crates open a
menu, and the hitboxes stand in the way of shots.

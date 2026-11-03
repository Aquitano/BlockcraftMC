# TTT

`paper/ttt` plays GommeHD's TTT (Trouble in Terrorist Town) in the `ttt` group, and TTT 2, the same game with chores
and sabotage, in the `ttt2` group (see "TTT 2" below): up to `players.max` (12) players on
one map, a few of them traitors. The rules follow GommeHD's mode and Garry's Mod's original, checked against two
open Minecraft clones ([MTTT](https://github.com/phiph-s/MTTT), Apache 2.0, and
[caseif/TTT](https://github.com/caseif/TTT), MIT); no code was taken from either.

## A round

1. The lobby waits on ClayMC's old TTT waiting island until `players.min` (4) are there; a round never starts with
   fewer than `players.round-minimum` (3). Meanwhile everyone votes for the map with the hotbar's vote item, or
   leaves it to chance; staff decide it with `/forcemap <map>`.
2. Preparation, `countdown.preparation` seconds (30): everyone starts at the map's spawns and loots. Nobody can be
   hurt, and nobody knows a role yet.
3. The roles are drawn: `roles.traitor-ratio` of the players are traitors (rounded to the nearest, at least one, so
   two from six players on), from `roles.detective-min-players` (6) on `roles.detective-ratio` are detectives (rounded
   down, at least one), the rest innocents. Each learns their role by title; traitors also learn each other (chat, and
   a sidebar section only they see), and everyone learns who the detectives are, whose names turn blue. How many
   traitors there are is never announced.
4. The round runs `countdown.round` seconds (10 minutes). The innocents and detectives win when no traitor is left
   or the time runs out; the traitors win when nobody else is left. The whole side wins, dead members included.

Nobody hears who killed whom: the round keeps its kills secret (`RoundSummary.keepKillsSecret`), so no streak is
announced, no kill effect plays, the victim's title names nobody and nobody pays respects with an F. The dead lie where they fell as a figure in their skin, named "Unbekannte Leiche";
the first player to right-click it identifies it for everyone, which reveals the role. Holding the DNA scanner,
a click also tells who the killer was. Dead players watch; their chat reaches only other spectators. The sidebar counts them among the living until their corpse is identified.

A traitor's chat message that starts with `#` reaches only the living traitors; `/tc <Nachricht>` writes one.

A map may have **traitor doors**: iron doors into passages that open for a living traitor's right-click for three
seconds and stay shut for everyone else.

## The maps

On every map, a right-click on any chest removes it and gives one item from `chests.loot`, picked by weight. Every
ender chest holds the round's one iron sword, given once and only after the preparation. Nothing can be broken, and
nothing placed but the shop's landmines, fake chests and heal stations. The **traitor tester** works the same way on
every map. Pressing its button from inside closes the doorway with glass. After `tester.delay-seconds`, the lamps
turn red if a traitor stands inside, lime otherwise, for `tester.show-seconds`. Then the lamps and doorway return to
the map's own blocks. Everyone is told who pressed it; the tester then rests `tester.cooldown-seconds`.

The imported maps also have ClayMC's **traitor trap**: a pit with lava under the tester's floor and a hidden button
elsewhere. Only a living traitor can press it, once per round. The floor drops away for `tester.trap-seconds` (3),
then closes over the pit. Lava and fire deaths inside the pit count as the presser's kills unless combat credits
another player. Escaping the pit leaves no trap attribution. Eichenhain's tester has no trap.

**Eichenhain** is a village `TownBuilder` puts into an empty world at every start; the decoration draws from a fixed
seed, so it looks the same every time. Two streets cross at a plaza with a fountain, where the round starts, and
benches. Around it stand eight furnished houses with gable roofs (two with an upper floor and a ladder), an inn with
a bar below and rooms above, an open smithy, a barn with a loft and a paddock, and a market with a well. The two
western houses have cellars, joined by a tunnel under the street. A pond with a jetty and a footbridge lies in the
north-east, fenced vegetable gardens behind the houses, a graveyard with an open grave beside the chapel, and a
forest between the village and the hedge, inside the world border. About 30 chests hide in the houses, cellars,
lofts, the tunnel, on the jetty and in the open grave; the ender chest stands in the chapel at the south end, the
tester at the north end.

**Vineyard**, **Minecraft** and **Raid** are imported maps from ClayMC (see [the import](../../worlds/README.md#ttt)).
They live in the TTT map bundle, `worlds/ttt/blockcraft/TTT/maps/<key>/`, each as a `map.yml` and the dimension
folder of its world, `world/`. At startup, the server copies every imported map into place and adds it to the vote;
a round loads only the chosen one. A map whose `map.yml` lacks something a round needs is skipped and named in the
log. Invalid world borders are rejected before voting. If the chosen world fails to load at round start, the
round uses Eichenhain and announces it.

Each `map.yml` uses this format:

```yaml
name: Vineyard
builders: [ClayMC]
icon: vine                               # what the vote shows
border: {x: 6.5, z: 8.5, size: 128}      # the world border, a square round x/z
spectator: {x: 6.5, y: 105.0, z: 45.5, yaw: 180.0, pitch: 35.0}
spawns:                                  # one per player; more players than spawns share them
  - {x: 0.5, y: 71.0, z: -12.5, yaw: 0.0}
tester:
  zone: {x1: -1, y1: 75, z1: -2, x2: 1, y2: 77, z2: 0}      # who stands here is tested
  button: {x: 0, y: 76, z: -2}
  entrance: {x1: -1, y1: 75, z1: 1, x2: 1, y2: 76, z2: 1}   # shut with glass during a test
  lamps:                                                    # any blocks; red or lime while the result shows
    - {x: -2, y: 77, z: 1}
  trap:                                                     # optional
    button: {x: 0, y: 73, z: 12}
    floor: {x1: -1, y1: 74, z1: -2, x2: 1, y2: 74, z2: 0}   # opens over the pit
```

Chests and ender chests need no entry. To add a map, import it with `./gradlew importMap -Psource=<old world>
-Playout=dimension -Pmode=void -Ptarget=worlds/ttt/blockcraft/TTT/maps/<key>/world` and write its `map.yml`.

The lobby template is `worlds/ttt`. `/setup lobby` moves its spawn, and `/map save` keeps it.

## Shops and points

Traitors and detectives get a shop item in the last hotbar slot and `shop.start-points` (2) points. A traitor earns
a point for every innocent or detective they kill, a detective for every traitor. Prices are under `shop.prices`.

| Shop | Item | Effect |
|---|---|---|
| Traitor | Medipack | heals `shop.medipack` health points |
| Traitor, Detective | Radar | a compass pointing at the nearest target for `shop.radar-seconds`, distance in the action bar; a traitor's radar skips traitors |
| Traitor | Creeper-Pfeile | `shop.creeper-arrows` arrows (and a bow if there is none) that leave a lit creeper where they land |
| Traitor | Landmine | a pressure plate that explodes under anyone but a traitor |
| Traitor | Tester-Spoofer | the next test shows its traitor as innocent |
| Traitor | Fake-Kiste | a chest that explodes when anyone but a traitor opens it |
| Traitor | Messer | a stab in the back kills at once and uses it up; from the front it is shears |
| Traitor | Maske | on a corpse: wear its skin and name for `shop.mask-seconds`; the figure is left faceless and unidentified |
| Traitor | Feuerzeug | on a corpse: burns it, so nobody identifies or scans it, and the sidebar keeps counting the dead among the living |
| Traitor | Zeitbombe | set on the floor, ticks down `shop.time-bomb.seconds` with a timer above it and explodes with its `power`; anyone can cut one of its `wires`, and only the right one, which the owner is told, defuses it |
| Traitor | Schalldämpfer-Pistole | hitscan, `shop.silenced-pistol.shots` shots of `damage` up to `range` blocks, almost silent |
| Traitor | Bärenfalle | set on the floor; whoever but a traitor walks in is held for `shop.bear-trap.seconds` and hurt by `damage`, loudly |
| Traitor | Gift | on a heal station: from then on it hurts instead of healing, and looks the same |
| Traitor | Störsender | for `shop.jammer.seconds`, radars, motion sensors, mine detectors, crime cameras and the tester within `radius` of the holder show only static |
| Detective | Heilstation | a block that heals everyone within `shop.heal-station.radius` for `shop.heal-station.seconds` |
| Detective | DNA-Scanner | names the killer of a corpse it touches |
| Detective | Goldenes Schwert | one blow kills a traitor; striking anyone else kills the wielder; used up either way |
| Detective | Bewegungsmelder | set on the floor; notes everyone within `shop.motion-sensor-radius`, and a right-click shows who passed and how long ago |
| Detective | Taser | hitscan, `shop.taser.charges` shots up to `range` blocks; whoever it hits cannot move, strike or shoot for `seconds` |
| Detective | Defibrillator | at the corpse of an innocent or detective dead at most `shop.defibrillator.window-seconds`: after `hold-seconds` beside it they live again with `health` |
| Detective | Schutzweste | every hit deals `shop.vest` less, for the rest of the round |
| Detective | Minensucher | held, marks landmines, fake chests, bear traps and bombs within `shop.mine-detector-radius` with particles only the holder sees |
| Detective | Tatortkamera | at a corpse, replays `shop.crime-camera-seconds` before the death and a third of that after it with faceless figures only the holder sees, the dead one glowing |

The time bomb, bear trap and motion sensor lie on an item display with the pack's model, and the bomb and sensor
answer clicks through an interaction hitbox. The lighter and the mask work on a corpse without identifying it; the
other goods identify it first. A mask, vest, stun, radar or jammer ends when its holder dies or leaves.

Explosions use `shop.explosion-power` (the time bomb its own), break no blocks and count as the owner's kill, even once
the owner is dead; so do the bear trap's and a poisoned heal station's hurt.

## TTT 2

A server whose `BLOCKCRAFT_TTT_MODE` is `tasks` plays TTT 2. The compose service `ttt2` does, in its own group, so the
two modes fill their own rounds; both share the stats, karma and the lobby. TTT 2 plays only maps whose `Town` has
a `TaskLayout`, which today is the map `TownBuilder` builds. The settings are under `tasks` in config.yml.

- The chests stay shut. Everyone starts with `tasks.start-items` and gets `tasks.per-player` chores when the roles
  are drawn, listed on the sidebar with where they are. A traitor's list looks the same, and doing them shows nothing.
- A chore starts with a right-click on its station. A hold chore takes `tasks.hold-seconds` within `tasks.reach`
  blocks; a hit or walking off breaks it off. A fetch chore hands over a load at its station that must be
  right-clicked onto its destination. A puzzle chore opens nine jumbled tiles to click from one to nine.
- Each real chore pays one pick from `tasks.loot`. A visible chore also shows at its station, a bell rings or a fire
  lights, which only an innocent's chore does.
- A boss bar shows how far the innocents are. A dead innocent's open chores drop out, so the bar can still fill.
  When every living innocent has done theirs, the innocents win.
- Traitors sabotage from a row in their shop, free, one at a time and `tasks.sabotage.cooldown-seconds` apart: a
  blackout puts out the map's lights and darkens everyone else until someone mends the generator
  (`repair-seconds` beside it) or `blackout-seconds` pass; locked doors shut every door of the map for
  `doors-seconds`; a broken tester tests nobody until it is mended at its button or `tester-seconds` pass.

## Karma, health, rewards

Karma lives in `bc_ttt_karma` and starts at `karma.start` (1000, also the maximum). Killing your own side costs
`karma.team-kill`; an innocent or detective killing a traitor earns `karma.enemy-kill`; a round without a team kill
earns `karma.clean-round`. Every blow deals `karma / karma.max` of its damage, never less than `karma.damage-floor`.
The sidebar shows it.

There is no hunger, and without `natural-regeneration` only medipacks and heal stations heal. Combat is 1.8-style
(`pvp.legacy-combat`). A correct kill pays `rewards.kill` coins and a win `rewards.win`; a team kill pays nothing.
Stats go to `bc_ttt_stats`.

## Testing

`./gradlew :paper:ttt:build` runs the unit tests (role counts, the win check, shop points, karma, reading a
`map.yml`, TTT 2's chore progress). A round needs a client smoke test with at least three accounts (`/start` shortens the lobby, `/forcemap`
picks the map): the vote, roles and titles, a chest and the ender chest, the tester on every map with and without a
traitor and with a spoofer, the traitor trap, a traitor door, a corpse identified and scanned, every shop item (the
mask, lighter, defibrillator and crime camera on corpses, the bomb defused and set off, the jammer next to a radar and
the tester), a team kill's karma, and both endings. TTT 2 needs the same on the `ttt2` server, plus each chore kind, a
traitor's fake chore, the win by chores and each sabotage with its repair.

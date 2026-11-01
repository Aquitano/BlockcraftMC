# TTT

`paper/ttt` plays GommeHD's TTT (Trouble in Terrorist Town) in the `ttt` group: up to `players.max` (12) players on
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
a click also tells who the killer was. Dead players watch; their chat reaches only other spectators.

A traitor's chat message that starts with `#` reaches only the living traitors; `/tc <Nachricht>` writes one.

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
| Detective | Heilstation | a block that heals everyone within `shop.heal-station.radius` for `shop.heal-station.seconds` |
| Detective | DNA-Scanner | names the killer of a corpse it touches |
| Detective | Goldenes Schwert | one blow kills a traitor; striking anyone else kills the wielder; used up either way |

Explosions use `shop.explosion-power`, break no blocks and count as the owner's kill, even once the owner is dead.

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
`map.yml`). A round needs a client smoke test with at least three accounts (`/start` shortens the lobby, `/forcemap`
picks the map): the vote, roles and titles, a chest and the ender chest, the tester on every map with and without a
traitor and with a spoofer, the traitor trap, a corpse identified and scanned, every shop item, a team kill's karma, and both endings.

# TTT

`paper/ttt` plays GommeHD's TTT (Trouble in Terrorist Town) in the `ttt` group: up to `players.max` (12) players in
one town, a few of them traitors. The rules follow GommeHD's mode and Garry's Mod's original, checked against two
open Minecraft clones ([MTTT](https://github.com/phiph-s/MTTT), Apache 2.0, and
[caseif/TTT](https://github.com/caseif/TTT), MIT); no code was taken from either.

## A round

1. The lobby waits on a platform above the town until `players.min` (4) are there; a round never starts with fewer
   than `players.round-minimum` (3).
2. Preparation, `countdown.preparation` seconds (30): everyone starts around the fountain and loots. Nobody can be
   hurt, and nobody knows a role yet.
3. The roles are drawn: `roles.traitor-ratio` of the players are traitors (at least one), from
   `roles.detective-min-players` (6) on `roles.detective-ratio` are detectives (at least one), the rest innocents. Each
   learns their role by title; traitors also learn each other (chat, and a sidebar section only they see), and
   everyone learns who the detectives are, whose names turn blue. The number of traitors is announced.
4. The round runs `countdown.round` seconds (10 minutes). The innocents and detectives win when no traitor is left
   or the time runs out; the traitors win when nobody else is left. The whole side wins, dead members included.

Nobody hears who killed whom. The dead lie where they fell as a figure in their skin, named "Unbekannte Leiche";
the first player to right-click it identifies it for everyone, which reveals the role. Holding the DNA scanner,
a click also tells who the killer was. Dead players watch; their chat reaches only other spectators.

A traitor's chat message that starts with `#` reaches only the living traitors; `/tc <Nachricht>` writes one.

## The town

Eichenhain is built by `TownBuilder` into an empty world at every start, so the service needs no world template:
a plaza with a fountain, two streets crossing it, two houses per quarter (doors, windows, some with an upper floor
and a ladder to the roof), sheds, gardens and trees, a barn in the east and a market in the west, all inside a
hedge and the world border. Nothing can be broken, and nothing placed but the shop's landmines, fake chests and heal
stations.

- **Chests** (about 25): a right-click removes the chest and gives one item from `chests.loot`, picked by weight.
- **Ender chest** in the chapel at the south end: an iron sword, once per round, only after the preparation.
- **Traitor tester** at the north end: pressing the button inside closes the doorway with glass and, after
  `tester.delay-seconds`, the lamps over the doorway and on the back wall turn red if a traitor stands inside, lime
  otherwise, for `tester.show-seconds`. Everyone is told who pressed it; the tester then rests `tester.cooldown-seconds`.

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

Explosions use `shop.explosion-power`, break no blocks and count as the owner's kill.

## Karma, health, rewards

Karma lives in `bc_ttt_karma` and starts at `karma.start` (1000, also the maximum). Killing your own side costs
`karma.team-kill`; an innocent or detective killing a traitor earns `karma.enemy-kill`; a round without a team kill
earns `karma.clean-round`. Every blow deals `karma / karma.max` of its damage, never less than `karma.damage-floor`.
The sidebar shows it.

There is no hunger, and without `natural-regeneration` only medipacks and heal stations heal. Combat is 1.8-style
(`pvp.legacy-combat`). A correct kill pays `rewards.kill` coins and a win `rewards.win`; a team kill pays nothing.
Stats go to `bc_ttt_stats`.

## Testing

`./gradlew :paper:ttt:build` runs the unit tests (role counts, the win check, shop points, karma). A round needs a
client smoke test with at least three accounts (`/start` shortens the lobby): roles and titles, a chest and the
ender chest, the tester with and without a traitor and with a spoofer, a corpse identified and scanned, every shop
item, a team kill's karma, and both endings.

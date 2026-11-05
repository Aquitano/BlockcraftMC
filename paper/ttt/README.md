# TTT

`paper/ttt` plays GommeHD's TTT (Trouble in Terrorist Town) in the `ttt` group, and TTT 2, the same game with chores
and sabotage, in the `ttt2` group (see "TTT 2" below): up to `players.max` players on one map, a few of them traitors.
The rules follow GommeHD's mode and Garry's Mod's original, checked against two open Minecraft clones
([MTTT](https://github.com/phiph-s/MTTT), Apache 2.0, and [caseif/TTT](https://github.com/caseif/TTT), MIT); no code was
taken from either.

Every number named below as a key is in [config.yml](src/main/resources/config.yml), most with a comment.

## A round

1. The lobby waits on the [shared waiting island](../../worlds/README.md#shared-waiting-island) until `players.min` are
   there; a round never starts with fewer than `players.round-minimum`. Meanwhile everyone votes for the map with the hotbar's vote item, or leaves it to
   chance; staff decide it with `/forcemap <map>`.
2. Preparation: everyone starts at the map's spawns and loots. Nobody can be hurt, and nobody knows a role yet.
3. The roles are drawn: traitors, from `roles.detective-min-players` on detectives, the rest innocents. Each learns
   their role by title. Traitors also learn each other, and everyone learns who the detectives are, whose names turn
   blue. How many traitors there are is never announced.
4. The innocents and detectives win when no traitor is left or the time runs out; the traitors win when nobody else is
   left. The whole side wins, dead members included.

Nobody hears who killed whom: the round keeps its kills secret (`RoundSummary.keepKillsSecret`), so no streak is
announced, no kill effect plays, the victim's title names nobody and nobody pays respects with an F. The dead lie where
they fell as a figure in their skin, named "Unbekannte Leiche".
Right-clicking a corpse privately reveals its name and role to the finder. Its public label stays unknown, and each
player's sidebar counts the victim among the living until that player inspects the body. The DNA scanner reports
whether foreign DNA is present and estimates time since death in 15-second intervals. It never names the killer.
Dead players watch; their chat reaches only other spectators.

A traitor's chat message that starts with `#` reaches only the living traitors; `/tc <Nachricht>` writes one.

A map may have **traitor doors**: iron doors into passages that open for a living traitor's right-click for three
seconds and stay shut for everyone else.

## The maps

On every map, a right-click on any chest removes it and gives one item from `chests.loot`, picked by weight. Every
ender chest holds the round's one iron sword, given once and only after the preparation. Nothing can be broken, and
nothing placed but the shop's landmines, fake chests and heal stations.

The **traitor tester** works the same way on every map. Pressing its button from inside closes the doorway with glass.
After a delay the lamps turn red if a traitor stands inside, lime otherwise, and then the lamps and doorway return to the
map's own blocks. Everyone is told who pressed it.

The imported maps also have ClayMC's **traitor trap**: a pit with lava under the tester's floor and a hidden button
elsewhere. Only a living traitor can press it, once per round. Lava and fire deaths inside the pit count as the
presser's kills unless combat credits another player. Escaping the pit leaves no trap attribution.

**Schattenbach** is an abbey village `TownBuilder` puts into an empty world at every start (vote and `/forcemap` key
`schattenbach`); the decoration draws from a fixed seed, so it looks the same every time. `Abbey`, `Village`, `Mill` and
`Outskirts` hold the buildings. Barriers fill the strip between the hedge and the world border, so nobody drops out of
the village. The tester stands in the town hall, over a lava pit that shows through a grate in the Ratskeller below,
where the trap's button hides behind the kegs. Two passages open only for living traitors, behind iron doors at both
ends: one from the mausoleum to the crypt, one from the inn's cellar to the mill's gear room. Anyone can find their
hatches, at the bottom of the open grave (which has a ladder back up) and among the crates in the lane beside the mill,
and walk the passages, but only a traitor opens their iron doors.

**Vineyard**, **Minecraft** and **Raid** are imported maps from ClayMC (see [the import](../../worlds/README.md#ttt)).
They live in the TTT map bundle, `worlds/ttt/blockcraft/TTT/maps/<key>/`, each as a `map.yml` and the dimension
folder of its world, `world/`. At startup, the server copies every imported map into place and adds it to the vote;
a round loads only the chosen one. A map whose `map.yml` lacks something a round needs is skipped and named in the
log. Invalid world borders are rejected before voting. If the chosen world fails to load at round start, the
round uses Schattenbach and announces it.

To add a map, import it with `./gradlew importMap -Psource=<old world> -Playout=dimension -Pmode=void
-Ptarget=worlds/ttt/blockcraft/TTT/maps/<key>/world` and write its `map.yml`, using
[Vineyard's](../../worlds/ttt/blockcraft/TTT/maps/vineyard/map.yml) as the model. Chests and ender chests need no entry.
What its keys mean:

- `border` is the world border, a square round x/z. `icon` is what the vote shows.
- `spawns` holds one spot per player; more players than spawns share them.
- `tester.zone` is the box whose occupants are tested, and `tester.entrance` is shut with glass during a test.
  `tester.lamps` can be any blocks; they turn red or lime while the result shows.
- `tester.trap` is optional: its `button`, and the `floor` that opens over the pit.

The server's template is `worlds/ttt`. Its overworld, ClayMC's old TTT waiting island, is the lobby only when
`BLOCKCRAFT_WAITING_TEMPLATE` is unset, as Compose and `:paper:runServer` both set it. `/setup lobby` moves that
island's spawn, and `/map save` keeps it.

## Shops and points

Traitors and detectives get a shop item in the last hotbar slot and earn points for kills across sides. `ShopItem` says
who can buy what, `shop.items` in `messages.yml` says what each good does for the player, and the comment on `shop` in
`config.yml` gives its numbers.

Rules that cross items: the lighter and the mask work on a corpse without identifying it, and the other goods identify
it privately first. A mask, vest, stun, radar or jammer ends when its holder dies or leaves. Explosions break no blocks and count
as the owner's kill, even once the owner is dead; so do the bear trap's and a poisoned heal station's hurt.

## TTT 2

A server whose `BLOCKCRAFT_TTT_MODE` is `tasks` plays TTT 2. The compose service `ttt2` does, in its own group, so the
two modes fill their own rounds; both share the stats, karma and the lobby. TTT 2 plays only maps whose `Town` has
a `TaskLayout`, which today is the map `TownBuilder` builds. The settings are under `tasks` in config.yml.

- Each innocent and detective receives a suggested route of chores. Those assignments form a fixed team pool that
  any living innocent or detective can finish. Deaths, disconnects, and revivals leave the target and progress unchanged.
- The navigation book in hotbar slot eight selects a task. Traitors can find stations but cannot complete tasks.
  After a completion, innocents receive another target, with carried deliveries and their own route taking priority.
- The selected target has a private label with its destination, distance, and relative height. Other unfinished tasks
  on the route have smaller symbols. A repairable sabotage temporarily selects its station, then restores the task.
  Traitors also see their passage doors and the tester's trap button until it is used.
- Schattenbach has 16 chores of three kinds: held at a station, played as a game (`task/game`), and errands that carry a
  load across the map. A hit or walking off breaks a held chore off. Closing a game early leaves the chore open.
  Each successful completion earns credit and one pick from `tasks.loot` once. Finishing the pool wins the round.
- The sidebar shows the selected objective and personal contribution. Karma stays on the lobby sidebar.
  Task progress and active sabotage share one boss bar.
- Traitors sabotage from a row in their shop, free, one at a time: a blackout puts out the map's lights and darkens
  everyone else until someone mends the generator in the mill's gear room, locked doors shut every door of the map, and
  a broken tester tests nobody until it is mended at its button.

## Karma, health, rewards

Karma lives in `bc_ttt_karma` and stats in `bc_ttt_stats`. Every blow scales with the attacker's karma, which the lobby
sidebar shows. There is no hunger, and without `natural-regeneration` only medipacks, heal stations and a traitor's kills
heal. Combat is 1.8-style (`pvp.legacy-combat`). A team kill pays nothing.

## Testing

`./gradlew :paper:ttt:build` runs the unit tests (role counts, the win check, shop points, karma, reading a
`map.yml`, TTT 2's chore progress). A round needs a client smoke test with at least three accounts (`/start` shortens the lobby, `/forcemap`
picks the map): the vote, roles and titles, a chest and the ender chest, the tester on every map with and without a
traitor and with a spoofer, the traitor trap, a traitor door, a corpse identified and scanned, every shop item (the
mask, lighter, defibrillator and crime camera on corpses, the bomb defused and set off, the jammer next to a radar and
the tester), a team kill's karma, and both endings. TTT 2 needs the same on the `ttt2` server, plus each chore kind,
each of the six chore games, the win by chores and each sabotage with its repair.

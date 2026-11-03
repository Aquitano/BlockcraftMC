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
another player. Escaping the pit leaves no trap attribution.

**Schattenbach** is an abbey village `TownBuilder` puts into an empty world at every start (vote and `/forcemap`
key `schattenbach`); the decoration draws from a fixed seed, so it looks the same every time. The round starts round
the fountain on the market. The abbey stands on a terrace in the north-west, reached by the stairs above the market,
narrow stairs in the west and stairs down to the farm in the east: a church with pews, an organ gallery and a bell
tower whose belfry overlooks the map, the crypt below it, a cloister whose roof can be walked from the library, the
east range with refectory, library and abbot's room, the graveyard with a mausoleum, and the herb garden. Around the
market stand the town hall, the inn "Zum Goldenen Krug" with guest rooms and an attic, the bakery, the smithy, the
apothecary, the cobbler's house, the weaver's house, the well square and the old gate tower; the town hall's balcony
bridges the lane onto the smithy's flat roof. A brook crosses the south under a stone bridge, a footbridge and a ford
of stepping stones, past the water mill, whose wheel a flume feeds from the mill pond. Beyond the brook lie the quarry
pit with its crane, the orchard with a wayside chapel, the charcoal burner's clearing and a hunting stand; the red
barn, the fields and the pigsty lie under the terrace in the north-east.

Underneath, the town hall, the inn and the cobbler's house have cellars, a dry well in the cloister drops into the
crypt, and a miners' tunnel runs from the cobbler's cellar under the brook to the quarry floor. Two passages open only
for living traitors, behind iron doors at both ends: one from the mausoleum to the crypt, one from the inn's cellar to
the mill's gear room. Anyone can find their hatches, at the bottom of the open grave (which has a ladder back up) and
among the crates in the lane beside the mill, and walk the passages, but only a traitor opens their iron doors.
Barriers fill the strip between the hedge and the world border, so nobody drops out of the village. The tester stands in the town hall behind the hall's north wall.
Its floor is the trap's, over a pit of lava that shows through a grate in the Ratskeller below, where the trap's
button hides behind the kegs. About 30 chests hide in the houses, cellars, lofts, the tunnels, the crypt, the bell
tower and the mausoleum; the ender chest stands in a niche in the crypt's north wall.

For TTT 2, Schattenbach has 16 chores, named in `tasks.chores` in messages.yml. Five are held at a station: ringing
the bell in the belfry, the vigil at the brazier in the crypt, baking at the bakery's oven (those three show everyone
nearby), drawing water at the well square and turning hay in the barn loft. Six are played, each its own game in
`task/game`: repeating the organ's tune, sorting the library's chronicle by swapping volumes, striking the wrong sums
in the council chamber's ledger, brewing a remedy at the apothecary's stand by its recipe (which shows), timing the
mill's millstone gears, and keeping the smithy's forge at heat with the bellows (which shows). Five are errands across the
map: grain from the barn to the mill's sack floor, a charter from the town hall to the abbot's lectern, herbs from the
herb garden to the inn's soup kettle, stone from the quarry's stonecutter to the cloister's broken wall, and
horseshoes from the smithy's anvil to the stable gate in the barn. A sabotage locks every wooden door, a blackout
puts out the lanterns, torches, candles, fires and lamps, and the generator that mends it stands in the mill's gear
room.

**Vineyard**, **Minecraft** and **Raid** are imported maps from ClayMC (see [the import](../../worlds/README.md#ttt)).
They live in the TTT map bundle, `worlds/ttt/blockcraft/TTT/maps/<key>/`, each as a `map.yml` and the dimension
folder of its world, `world/`. At startup, the server copies every imported map into place and adds it to the vote;
a round loads only the chosen one. A map whose `map.yml` lacks something a round needs is skipped and named in the
log. Invalid world borders are rejected before voting. If the chosen world fails to load at round start, the
round uses Schattenbach and announces it.

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

- The chests are looted as in TTT. Everyone gets `tasks.per-player` chores when the roles are drawn, at least
  `tasks.spread` blocks apart where the map allows, listed on the sidebar. The traitors get none; they have the
  sabotage.
- Each player sees a `!` over every spot their chores send them to next, through walls and only for them, with
  sparks around the block once they are close; for a load they carry, a `?` marks its destination instead. While a
  sabotage can be mended, a red `!` marks the generator or the tester's button for every living innocent and detective.
  A mark grows with the distance, so it reads about as large across the town as next door. A label over the tester's
  doorway shows everyone, in either mode, where the tester is. A traitor sees a `☠` over each door to the passages and a
  `⚠` over the tester's trap button until it is used. Next to any mark, the action bar says what a right-click does. What a click on a
  chore answers goes to the action bar, a sabotage starts as a title, and the chat keeps what concerns everyone.
- A chore starts with a right-click on its station. A hold chore takes `tasks.hold-seconds` with the player's eyes within
  `tasks.reach` blocks; a hit or walking off breaks it off. A fetch chore hands over a load at its station that must be
  right-clicked onto its destination. A played chore opens its game, its title saying what to do, and closing it early
  leaves the chore open.
- Each chore pays one pick from `tasks.loot`. A visible chore also shows at its station: a bell rings or a fire lights.
- A boss bar shows how far the innocents are. A dead innocent's open chores drop out, so the bar can still fill.
  When a chore done leaves no living innocent with an open one, the innocents win; a death alone never wins by chores.
- Traitors sabotage from a row in their shop, free, one at a time and `tasks.sabotage.cooldown-seconds` apart: a
  blackout puts out the map's lights and darkens everyone else until someone mends the generator
  (`repair-seconds` beside it) or `blackout-seconds` pass; locked doors shut every door of the map for
  `doors-seconds`; a broken tester tests nobody until it is mended at its button or `tester-seconds` pass. A
  sabotage starts as a title and runs as a red boss bar until it ends.

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
each of the six chore games, the win by chores and each sabotage with its repair.

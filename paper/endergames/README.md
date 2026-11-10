# EnderGames

GommeHD's EnderGames for the `endergames` group: survival games in a world nobody has seen before, with kits that
have abilities. Up to `players.max` play; `players.min` start the lobby countdown. Every number named below as a key is
in [config.yml](src/main/resources/config.yml).

## The world

At every server start the plugin throws away the last round's world and generates a vanilla overworld as the
dimension `endergames:runde`, so the service needs no world template. The seed is one of `world.seeds`, picked at
random; each comes with the centre the border goes round. The eight bundled ones were chosen from 360 centres of 40
random seeds for land (at least 92%), tree cover and moderate relief, then by eye for variety. While the lobby waits,
the plugin puts the border `world.radius` blocks around the centre and loads every chunk inside. The sidebar shows
whether the world is ready; a countdown that runs out before it is starts again. Time and weather stand still at day,
phantoms, patrols and traders do not spawn, other mobs do, and portals lead nowhere.

## The round

Everyone starts together on the start island, an island of end stone floating over the highest ground at the centre.
They stand on a ring for `countdown.freeze` seconds with chests of starter loot, then grab what they can and jump down
into the world. In front of each player stands an ender chest with better loot (`start-island.ender-chests`), which
vanishes once someone empties it. Nobody is hurt for `countdown.protection` seconds, so the jump is safe. When it ends
the island crumbles with whatever is left in its chests, and whoever is still on it floats down with Slow Falling. Then
the fight runs for `countdown.round`; the last player alive wins, otherwise it is a draw. Every player gets a compass
`countdown.tracker-after` seconds into the fight. `world.shrink-after` seconds into the fight the border starts closing
to `world.final-radius` by the end of the round.

- Ender chests wander the map, each marked by a beam of particles, with fresh loot at each new spot (`enderchests`). They
  cannot be broken or blown up; potions in it heal, splash potions harm. Ender chests players place drop nothing.
- Every `swap.min-seconds` to `swap.max-seconds` of the fight two random players trade places, with a second of
  blindness, and everyone is told.
- Placed TNT lights itself as in GommeHD's round, enchanting tables need no lapis, and a dead player drops all their
  experience. Combat is 1.8-style (`pvp.legacy-combat`).

## Kits

Picked with the hotbar's kit item in the lobby; the price of each is `kits.<key>` in coins, 0 is free, and a purchase
is a `bc_unlocks` row `endergames.kit.<key>`. A player who picks nothing plays Barbar. `kit/Kit` says what each starts
with, `kit/KitAbilities` what it does, `kit.<key>` in `messages.yml` what the player reads and `abilities` in the config
the numbers, with health in half hearts.

## Test it

`./gradlew :paper:endergames:build` runs the unit tests (swap pairs, reach).
`./gradlew stagePlugins`, then restart the `endergames` service and `/play endergames`. Things to check in a client:
the world finishes loading before the first countdown runs out on its 1.5 GB; everyone starts on the start island, its
chests and its ender chest per player open only after the freeze, an emptied ender chest vanishes, the jump down does
no harm and the island crumbles at the end of the protection with Slow Falling for whoever stayed; ender chests appear, open, move after being opened and are visible from afar; swaps teleport both
players; each kit's ability; the border shrink; the win and the recap.

# SkyWars

GommeHD's solo SkyWars for the `skywars` group: everyone on their own island over the void, the last one standing
wins. Up to `players.max` (12) play; `players.min` (4) start the lobby countdown, a full lobby jumps to
`countdown.full` seconds. The waiting lobby is a grassy island among clouds, built into an empty world; barriers at
its edge keep everyone on it.

## The maps

Three maps, built in code into the empty world `skywars:inseln` when the round starts (about half a second), so the
service needs no world template. The lobby votes with the hotbar's vote item, or leaves it to chance; staff decide
with `/forcemap <map>`. Each map draws its shapes from its own seed, so it is the same at every start.

- **Wolkenhain**: grass and dirt over stone with coal and iron, oaks, flowers, clouds round the outside.
- **Frostspitze**: snow over packed ice, spruces under snow, ice spikes on the semi-middles.
- **Wüstenwind**: sand over sandstone and terracotta with gold, palms, cacti and dead bushes.

All three share one layout: twelve spawn islands in a ring 40 blocks round the middle, four semi-middles 21 blocks
out between them, and the middle with a raised plateau and a shrine of four pillars round an enchanting table, which
brings its own lapis. A spawn island has a tree, a little hill, a glass cage on the player's spawn and three chests
behind it; a semi-middle two chests and a crafting table; the middle four chests at the shrine's sides. Islands
taper to a point below and are made of the theme's top block, soil, and stone with veins.

## A round

1. When the countdown runs out, the vote decides the map, it is built, and the players are spread evenly round the
   ring with their kit, each in their cage. They wait `countdown.freeze` (5) seconds, in which nobody is hurt and
   nothing is used, thrown or placed; then the cages are gone.
2. `countdown.refill-after` (300) seconds later every chest that still stands is filled anew, announced 60 and 10
   seconds before.
3. From `countdown.border-after` (600) seconds the border closes from 54 blocks round the middle to
   `arena.final-radius` (12) by the end of the round.
4. After `countdown.round` (900) seconds the round ends in a draw; otherwise the last player alive wins.

Below `arena.void-height` (y 40) a player dies at once, the kill credited to whoever hit them last. Players build
and break anywhere inside the border up to `arena.build-height` (125). Combat is 1.8-style (`pvp.legacy-combat`).
A kill pays `rewards.kill` (5) coins, a win `rewards.win` (20); stats go to `bc_skywars_stats`.

## Chests

A spawn island's three chests each roll from their own table, so nobody starts without armour, a weapon or blocks:
`chests.armour`, `chests.weapons` and `chests.utility`. The semi-middles roll from `chests.semi-middle`, the middle
from `chests.middle`. Each table has `min-items`, `max-items` and weighted `loot`; potions in a chest heal.

## Kits

Item kits as on GommeHD, chosen in the lobby with the hotbar's kit item. A kit's price in coins is `kits.<key>`;
a kit at 0 is free for everyone, the others are bought once through the confirm dialog and kept in `bc_unlocks` as
`skywars.kit.<key>`. Whoever picks nothing plays Standard.

| Kit | Price | Items |
|---|---|---|
| Standard | free | wooden sword, wooden pickaxe |
| Bauarbeiter | 300 | stone pickaxe, 64 bricks |
| Bogenschütze | 400 | bow, 12 arrows |
| Ritter | 500 | chainmail chestplate and leggings |
| Enderman | 500 | 2 ender pearls |
| Bomber | 400 | 3 TNT, flint and steel |
| Fischer | 300 | fishing rod, 16 snowballs |
| Heiler | 500 | 2 splash potions of healing, golden apple |

## Testing

`./gradlew :paper:skywars:build` runs the unit tests (the island ring and the kits' unlock keys). In a client, with
`/start` to shorten the lobby: the vote and `/forcemap`, kit choice and purchase, the cages opening, the chests'
loot by category, the refill, the border, a void death crediting the last hitter, and the win.

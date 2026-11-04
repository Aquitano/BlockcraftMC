# SkyWars

GommeHD's solo SkyWars for the `skywars` group: everyone on their own island over the void, the last one standing
wins. Up to `players.max` play; `players.min` start the lobby countdown. Every number named below as a key is in
[config.yml](src/main/resources/config.yml).

## The maps

Three maps, **Wolkenhain**, **Frostspitze** and **Wüstenwind** (`map/Theme`), built in code into the empty world
`skywars:inseln` when the round starts, so the service needs no world template. The lobby votes with the hotbar's vote
item, or leaves it to chance; staff decide with `/forcemap <map>`. Each map draws its shapes from its own seed, so it is
the same at every start.

All three share one layout (`map/ArenaBuilder`): twelve spawn islands in a ring, each with a glass cage on the player's
spawn and three chests behind it, four semi-middles between them, and the middle with a shrine round an enchanting
table, which brings its own lapis.

## A round

1. When the countdown runs out, the vote decides the map, it is built, and the players are spread evenly round the
   ring with their kit, each in their cage. They wait `countdown.freeze` seconds, in which nobody is hurt and
   nothing is used, thrown or placed; then the cages are gone.
2. `countdown.refill-after` seconds later every chest that still stands is filled anew.
3. From `countdown.border-after` seconds the border closes on the middle to `arena.final-radius` by the end of the
   round.
4. After `countdown.round` seconds the round ends in a draw; otherwise the last player alive wins.

Below `arena.void-height` a player dies at once, the kill credited to whoever hit them last. Players build and break
anywhere inside the border up to `arena.build-height`. Combat is 1.8-style (`pvp.legacy-combat`). Stats go to
`bc_skywars_stats`.

## Chests

A spawn island's three chests each roll from their own table, so nobody starts without armour, a weapon or blocks. The
comment on `chests` in the config covers the tables.

## Kits

Item kits as on GommeHD, chosen in the lobby with the hotbar's kit item. `kit/Kit` says what each holds and `kits.<key>`
in the config its price; a kit at 0 is free for everyone, the others are bought once through the confirm dialog and kept
in `bc_unlocks` as `skywars.kit.<key>`. Whoever picks nothing plays Standard.

## Testing

The module has no unit tests. In a client, with
`/start` to shorten the lobby: the vote and `/forcemap`, kit choice and purchase, the cages opening, the chests'
loot by category, the refill, the border, a void death crediting the last hitter, and the win.

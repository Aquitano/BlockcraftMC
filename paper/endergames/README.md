# EnderGames

GommeHD's EnderGames for the `endergames` group: survival games in a world nobody has seen before, with kits that
have abilities. Up to `players.max` (24) play; `players.min` (4) start the lobby countdown, a full lobby jumps to
`countdown.full` seconds.

## The world

At every server start the plugin throws away the last round's world and generates a vanilla overworld as the
dimension `endergames:runde`, so the service needs no world template. The seed is one of `world.seeds`, picked at
random; each comes with the centre the border goes round. The eight bundled ones were chosen from 360 centres of 40
random seeds for land (at least 92%), tree cover and moderate relief, then by eye for variety: a giant spruce taiga,
a forest round a lake, a bamboo jungle, a dark forest, a meadow below snowy slopes, a snowy taiga, a savanna plateau
and a birch forest. While the lobby waits, the plugin puts the border `world.radius` blocks around the centre and
loads every chunk inside, sixteen at a time. The sidebar shows whether the world is ready; a countdown that runs out before
it is starts again. Time and weather stand still at day, phantoms, patrols and traders do not spawn, other mobs do,
and portals lead nowhere. The waiting lobby is a floating island of end stone built into an empty world, with an end portal frame at its
heart and obsidian pillars; barriers at its edge keep everyone on it.

## The round

Players start evenly spread on a ring at `world.spawn-ring` of the radius, each moved inward until it stands on dry
ground, and stand still for `countdown.freeze` seconds. Nobody is hurt for `countdown.protection` (90) seconds, then
the fight runs for `countdown.round` (30 minutes); the last player alive wins, otherwise it is a draw. Every player
gets a compass `countdown.tracker-after` seconds into the fight. `world.shrink-after` seconds into the fight the border
starts closing to `world.final-radius` by the end of the round.

- Ender chests wander the map: `enderchests.per-player` for every player, between `min` and `max`, each marked by a
  beam of particles. One stands `relocate-seconds`, or `after-open-seconds` once it was opened, or until the border
  passes it, then appears elsewhere with fresh loot from `enderchests.loot`. They cannot be broken or blown up;
  potions in it heal, splash potions harm. Ender chests players place drop nothing.
- Every `swap.min-seconds` to `swap.max-seconds` of the fight two random players trade places, with a second of
  blindness, and everyone is told.
- Placed TNT lights itself and goes off after 1.5 seconds, enchanting tables need no lapis, and a dead player drops all
  their experience. Combat is 1.8-style (`pvp.legacy-combat`).

## Kits

Picked with the hotbar's kit item in the lobby; the price of each is `kits.<key>` in coins, 0 is free, and a purchase
is a `bc_unlocks` row `endergames.kit.<key>`. A player who picks nothing plays Barbar. Ability numbers live under
`abilities`, health in half hearts.

| Kit | Starts with | Ability |
|---|---|---|
| Barbar (free) | wooden sword, leather armour | none |
| Bogenschütze (free) | bow, 16 arrows | an arrow at head height into a player without helmet deals `headshot-multiplier` damage |
| Holzfäller (free) | wooden axe with Sharpness I | breaking a log fells up to `lumberjack-max-logs` touching logs; crafted axes get Sharpness I |
| Jäger (free) | stone sword | animals he kills drop cooked meat |
| Enderman | 5 ender pearls | no pearl damage; `enderman-pearls-per-kill` per player kill |
| Bomber | 5 TNT, flint and steel | immune to explosions |
| Vampir | nothing | a player kill heals fully, a mob kill `vampire-mob-heal` |
| Känguru | nothing | double jump with the jump key, not below `kangaroo-min-health` |
| Spinne | 16 cobwebs | `spider-cobwebs-per-kill` per player kill |
| Stomper | nothing | players within `stomper-radius` of his landing take his fall damage; his own is capped at `stomper-max-fall-damage` |
| Skorpion | green leather chestplate | melee poisons for `scorpion-melee-seconds`, arrows for `scorpion-arrow-seconds`; immune to poison |
| Thor | Thors Hammer (golden axe) | right-click strikes lightning at the block he looks at, up to `thor-range`, every `thor-cooldown-seconds`; immune to lightning |
| Teleporter | 2 ender pearls | after a swap: full health and food, invulnerable for `teleporter-invulnerable-seconds` |
| Berserker | nothing | Resistance for `berserker-seconds` after every kill, Resistance II after kills in a row |
| Angsthase | wooden sword with Knockback II | Speed II for `scaredy-seconds` when hit |
| Suppenmeister | 6 mushroom stews | a stew heals `soup-heal` at once when he is hurt |
| Tracker | Tracker compass | the compass points to the nearest player, however far, and names their kit |

## Test it

`./gradlew stagePlugins`, then start the `endergames` service (the production overlay keeps it under the
`unreleased` profile) and `/play endergames`. Things to check in a client: the world finishes loading before the first
countdown runs out on a 2 GB server; spawns are on land; ender chests appear, open, move after being opened and are
visible from afar; swaps teleport both players; each kit's ability; the border shrink; the win and the recap.

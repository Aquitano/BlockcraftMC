# BedWars

`paper/bedwars` is a port of [rewi-bw](https://github.com/Marfien/rewi-bw), the Rewinside BedWars clone, under its Apache
2.0 licence (`NOTICE`). It plays like the original: four teams of two, bronze, iron and gold from the map's spawners, and
the original shop and prices (`shop` in its `config.yml`). Stats, coins, XP, achievements and spectating are the
network's own.

## Maps

The lobby is the `worlds/bedwars` template like any other [map](../../README.md#maps). The maps a round is played on
travel inside it, one directory per map under `blockcraft/BedWars/maps/<key>/`:

- `map.yml` holds its name, border, spectator spot, shops, spawners, team spawns and beds.
- `world/` is the map's dimension folder.

A round copies the voted map to `dimensions/bedwars/<key>` of the running level and loads it as the world
`bedwars:<key>`. The restart after the round throws it away again.

## Editing

`/setup lobby ...` places the lobby's spots, kept with `/map save` like every plugin's.

`/setup map <key> edit` loads a map of the pool, or an empty one for a new key, and takes the builder there. Standing in
it, `spawn`, `bed`, `shop`, `spawner`, `spectator`, `border`, `name` and `icon` change its `map.yml` at once, and `info`
names what is still missing. `save` copies the world back into the pool, and `/map save` then publishes the lobby with
its pool. A map is in the vote once nothing is missing for the teams `config.yml` plays with.

Opening a map for editing keeps its loaded dimension out of votes and forced choices. Save the changes you want to keep,
then restart the backend to use that map in rounds. Editing a chosen map before the round starts cancels that start
instead of replacing files beneath the loaded world.

## Which maps are in the vote

- `era: og` in a `map.yml` marks a map of the old server. The vote draws only from those while the OG occasion is on
  (`/event og` on the proxy), and only from the others otherwise, unless that set has no playable map.
- `always: true` puts a map into both votes, which is how Castle, Guardians and Ruins stay in the everyday rotation.
- `/forcemap` takes any map.

## Knockback-stick appearances

BedWars, MLG Rush, Duell's MLG Rush mode and Knockback FFA share one appearance, chosen in the lobby's Extras menu or
BedWars' stick menu. It is stored under `bedwars-stick`, the key from when only BedWars had it. All appearances use a
vanilla stick with the same combat properties, and their pictures require the resource pack.

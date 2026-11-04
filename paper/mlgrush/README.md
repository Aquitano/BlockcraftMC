# MLG Rush

`paper/mlgrush` runs 1v1 matches in the `mlgrush` group. Each player gets a knockback stick, refillable terracotta and a
wooden pickaxe. Two players start the countdown, and later arrivals watch. Breaking the enemy bed scores once and resets
both players and the arena. The first player to reach `score-target` wins, and leaving an active match forfeits it.

Hits knock back as in vanilla, with any item, and deal no health damage. `kit.knockback-level` is the stick's Knockback
enchantment. Stats are in `mlgrush_stats`.

BlockcraftAPI announces two participant places, but the Paper connection limit stays at 200 so spectators can connect.
After a match both players can request a rematch, and the arena resets without a backend restart. When an opponent
leaves, the server fills open seats from non-vanished spectators in arrival order. An empty backend still stops after
`idle-restart-minutes`.

## Run locally

```sh
./gradlew :paper:mlgrush:build :paper:lobby:build stagePlugins
docker compose up -d mariadb velocity lobby mlgrush
```

Connect two clients to `localhost:25565` and use `/play mlgrush`. The service runs without a world template, so no lobby
warp or arena editing is required.

## Run with production forwarding

Fill `.env` as described under [Deploy](../../README.md#one-host-with-docker), then:

```sh
./gradlew stagePlugins
docker compose up -d mariadb velocity lobby mlgrush
```

For updates, stage the jars and run the same `up -d` to apply Compose changes. Then run
`docker compose restart velocity lobby mlgrush` to load changed jars and resource-pack assets. Persistent plugin files
stay in the named data volume.

## Maps

On first start, MLG Rush writes `plugins/MLGRush/maps.yml` with six procedural maps. Unlike `config.yml`, this file stays
in the data folder. Bundled maps missing from it are added to the loaded pool, and existing definitions, disabled flags
and the selection are kept. Set a bundled map's `enabled` to false to exclude it.

`selection: random` chooses one enabled map at backend start and between matches. For repeatable testing, set `selection`
to an enabled key and restart the backend. The log records the chosen key.

The generated void dimension starts as `mlgrush:<key>` and stays loaded when another map replaces its layout.

A definition must name its geometry and all three materials. Empty pools, unknown or disabled selections, unsafe materials
and invalid geometry fail startup with the map key.

- `bridge-length` is 3 to 100, `bridge-width` is 1, 3 or 5, and `base-radius` is 3 to 8. A base radius is half its side
  length rounded down.
- Materials must be full solid blocks from the bundled pool.
- `base-width` is an odd width within the base's full diameter, and defaults to that diameter.
- `center-gap` defaults to zero and removes a centered stretch of bridge.
- `segment-length` and `segment-gap` default to zero. Set both to positive lengths for separated bridge segments.

Build and void bounds in `config.yml` are shared by all maps.

## Upgrading an old installation

Move any old `arena.bridge-length`, `arena.bridge-width` and `arena.base-radius` customizations into a named entry in
`maps.yml`, and pin that entry with `selection` if needed. Those three config keys are no longer read.

## In Duell

Duell also offers MLG Rush as a native mode; see [Duell](../duell/README.md). Both hosts use `paper/api`'s `game/rush` for
maps, equipment, knockback and scoring.

- Native matches count only in `duell_stats`, and standalone matches only in `mlgrush_stats`. Neither is moved or counted
  twice.
- Native settings live under `mlgrush` in Duell's config, with its maps in `plugins/Duell/rush/maps.yml`. An explicit
  Duell map vote takes priority over that file's `selection`.
- The hotbar order a player picks in the waiting hotbar's layout menu is a player setting, so it also holds in Duell. There,
  a right click on the mode's tile in the vote menu opens the same menu.

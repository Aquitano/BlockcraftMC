# Imported furniture

The three supplied scenes were converted into 52 finished Nexo furniture items: 30 market props, 21 Halloween props, and one quest board.
The item IDs and German names are in [blockcraft_imported.yml](items/blockcraft_imported.yml).

## Use the furniture

Stage a compatible licensed Nexo jar using the [existing setup](README.md#stage-the-licensed-plugin).
The network pack includes these models, and `stageNexo` includes their definitions and textures.
No MythicMobs or client mod is required. The models use Minecraft's multi-axis element rotations and target this
repository's Minecraft 26.3 client.

As an operator, give yourself a prop:

```text
/nexo give blockcraft_market_market_stall 1 <player>
/nexo give blockcraft_halloween_ghost 1 <player>
/nexo give blockcraft_quest_board 1 <player>
```

Place it with right click. Nexo's placement preview is enabled. Furniture uses four rotation directions.
Every prop has a targeting hitbox, sized from its geometry. Tables and the covered stall also have coarse solid
bodies through `FurnitureSolids`. Halloween fences, grave markers, cauldrons, pumpkins and street-lamp bases have
solid cores. Leaves, candle flames and hanging projections remain passable. The other imported props retain their
interaction hitboxes and do not acquire collision automatically.

The six chest, crate, and cart items open persistent three-row storage. Lanterns and candle-bearing props emit
level-12 light, including the candelabra and decorated grave markers. The ghost and scarecrow are decorations, with no AI or combat behavior.
The quest board prints the lobby's configured quests and their progress in chat. A finished quest's button uses the
same progress checks and reward transaction as its quest giver. This interaction is available on the lobby server.

For authored scenery, use an item ID in the existing `furniture.yml` placement format. Do not use the scenery lifecycle
for storage containing valuables: scenery is removed and recreated during map saves and seasonal refreshes.
Manual Nexo storage also follows the map lifecycle: this network restores its world template on startup.
Only the contents captured by `/map save` survive that reset. Use a persistent world for player storage.

## Lobby placement

The village layout is in `worlds/lobby/blockcraft/scenery/furniture.yml`. Its earlier 13 placements cover the mill,
market stall, arrival clock, pond bench, lanterns and seasonal variants. The produce table remains a full table in
every season. The cosmetics counter retains its head displays. The quest board and outdoor candle holders have
no permanent lobby placements.

One half-size candle sits on the raised furnace beside the bed in the furnished upstairs room at
`(-987.5, 54, 1634.5)`. Its Nexo definition is in `items/blockcraft_interior.yml`; it reuses the imported candle model
and emits native level-12 light. It needs no additional resource-pack assets.

The real-client collision pass checked walking obstruction and standing height for 14 furniture types, including
the tables, cart, market counter, bench and Halloween graveyard pieces. A normal jump onto the cart and seating
on both bench variants passed. Winter, Halloween and map publication recreated the expected furniture and
collision counts. The candle's native light measured level 12 and did not remain in the saved world template.

Another 15 placements appear only during Halloween: pumpkins, lanterns and a small graveyard beside the
witch's house. They use the existing October 17–31 calendar and lobby theme. `/event halloween` forces that occasion
across the network; `/event auto` restores the calendar.

## Regenerate and inspect

The converted Minecraft models, textures and rigs are the authoritative source. Edit those files directly and keep
`resourcepack/catalogs/furniture.json` consistent with the Nexo identifiers. The original GLBs and one-time
converters are retired. The local pre-retirement archive is `run/asset-refactor/original-imports.tar.gz`, and the
originals remain recoverable from Git history before this cleanup.

Run `./gradlew :resourcepack:verifyAssets :resourcepack:pack` to validate and package accepted assets.
The checks use the locked art environment and validate native models, textures, item routes and furniture definitions.
Candle wax and representative solid creature faces are checked against the finished PNGs. No GLB is required.

The converted files already contain their corrected per-material UV orientation. Keep those UVs when editing geometry.
Nexo's display scale restores each object's size, and its half-block translation cancels the placement offset.
Each prop uses one item display. Thin front and back faces remain for paper, leaves and flames.
Software previews do not replace Minecraft rendering and movement checks.

## Client verification

Tested with the running lobby's Nexo 1.28 jar on an isolated Paper 26.3 lobby and a real Minecraft 26.3 client.
All 52 items loaded. The client exposed and confirmed the fixes for UV boundary baking failures and missing
particle textures. The quest board opened the three configured quests. A chest accepted three diamonds and retained
them after closing and reopening in builder mode. Adventure-mode inventory transfers remain blocked by lobby rules.

The private test hub uses `100.64.195.51:25572` over Tailscale with Minecraft Java 26.3 and is stopped when idle.
Visitors arrive at the gallery at `(-969.5, 100, 1586.5)`. All 52 labelled placements and the platform survived a restart.
Its separate world template, compose overlay, and startup instructions are in `run/imported-hub`.

The live lobby and proxy were deployed on 2026-11-11. The market stall is at `(-948, 23, 1603)`, the bread table at
`(-937, 24, 1619)`, and the carrot table at `(-940, 24, 1620)`. The produce table stays a full table in every season.
The public pack at `https://mc.aquitano.me/blockcraft.zip` matches the tested build. Live entity metadata confirms
the imported item IDs. Visual and interaction checks used the isolated client, not a live authenticated account.
Rollback copies of the previous live pack, plugin jars, and furniture layout are in `run/imported-hub/live-backup`.

Screenshots are in `dev/verify/client-mod/run/screenshots/imported/`: `gallery-final.png`, `market-tested.png`,
`mill-final.png`, `quest-menu.png`, and `chest-storage.png`.

Reward claiming, storage across a world-template reset and break drops were not verified by this run.
The lobby restores its template on restart, so do not use this test as evidence of persistent player storage.

The surface tests check candle wax and exported crates, pennants, gravestones, the scarecrow, the quest board,
coins, and the cloth tray for opaque texture regions:

```sh
uv run --project resourcepack --locked python resourcepack/test_imported_props.py
```

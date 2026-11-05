# Sturmwerft generator reference

`python3 -I dev/mapgen/sturmwerft.py` validates the geometry and writes functions and arena metadata to
`build/mapgen/sturmwerft`. It does not start a server or update a saved world. `build()` returns the combat and scenery
builders for checks in memory. The scenery function includes the occupied expansion and runs after the combat function.

## Circulation

The original headquarters, post order, capture cylinders, six landing grounds and moving crane remain in place.
The play area is `[-100, 50, -150, 100, 144, 145]`, the world border is 384 blocks and `death-y` is 60.
The sea is at 56. The replay area retains its bottom at 50.

The northern road extensions join the promenade at deck level 64. Five-wide quartz stairs at x=-32 and x=32 rise
from floor 65 at z=-80 to floor 82 at z=-97. Two inner stairs at x=-12 and x=12 return to the northern post.
The ship contains an engine room, an assembly room and a connecting passage at floor 82.

The southern workshop, control hall and cargo room have floor 80 and occupied space through y=88.
The skybridge crosses z=110. Five-wide stairs at x=-32, x=0 and x=32 rise from floor 65 at z=64 to floor 80 at z=79.
The side stairs also connect to the outer rooms through their northern entrances. The central stair enters the control hall.
Each faction has a route through its nearest outer room before returning by the central stair toward the southern post.

The eastern glass gallery joins the northern and middle posts. The western open service catwalk joins the middle and
southern posts. Each has machinery, lower cover and openings through the adjoining rails. The middle post has faction
routes through these passages. All posts retain both direct approaches.

## Controls

| Control | Switch blocks | Affected blocks or volume | Permanent alternate passage |
| --- | --- | --- | --- |
| Bulkhead | `-4 84 -114`, `4 84 -114` | x=0, y=83..86, z=-112..-108, initially air | x=-12..12, z=-122..-118, floor 82 |
| Vent | `-71 82 100`, `-60 82 100` | Center `-65.5 81 103.5`, runtime volume extends 3 along x, 2 along z and y=80..84 | Workshop passage at z=110, floor 80 |

All four switches face south and have solid supports on their north sides. The generator checks a reachable stance
within five blocks and a clear ray to each switch center with the bulkhead closed and vent volume excluded.
Authored spawns and route endpoints avoid both hazards.

## Secret locations

The receiver is the note block at `-58 84 -128`, inside the ship's maintenance alcove.
Its entrance opens south into the engine room. The standing position `-57.5 83 -125.5` faces the receiver and its copper
cabinet, antenna and glass indicators. The generator provides no player-facing text or tune.

The inspection course runs west to east under a fixed maintenance crane north of the ship passage.
Its metadata is:

```yaml
receiver: "-58 84 -128"
inspection-course:
  lane: [-27.0, 77.0, -134.0, 23.0, 91.0, -129.0]
  pit-y: 80.0
  gates: [-21.0, -16.0, -11.0, -6.0, -1.0, 4.0, 9.0, 14.0, 19.0]
  board: "-24 84 -127"
```

The board stands beside the western approach. From the ship's northern bypass, take the western spur to
`-23.5 83 -131.5`. Face east along z=-131.5 and cross the fixed quartz platforms in order. The gaps are two blocks wide.
The platforms change height by at most one block. The finish joins the eastern return deck near `21.5 83 -131.5`.
The pit has floor 77. Its two stairs at x=-18 and x=15 rise south from z=-129 to z=-125 and return to the ship at z=-124.
The course is separate from normal combat circulation and the bulkhead bypass.

## Validation limits

The generator checks standing positions at all playable heights, one-block movement clearance, ordinary floor and stair
access to rooms and fighter positions, stair width and headroom, and both control bypasses while the hazards are active.
It also checks interaction reach and support, capture positions, cover and peek rays, fixed course landings, landing
cylinders to the sky, headquarters vehicle approaches, environment emitters and the moving crane's swept volume.

Collision checks conservatively treat partial solid blocks as full blocks. Buttons and levers are passable.
The generator does not simulate player movement, jumping timing, bot navigation, rendered models or round events.
World regeneration and a real client check are still required after the runtime changes are integrated.

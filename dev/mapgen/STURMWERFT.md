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

## Architecture and lighting

The ocean towers have tapered lower bodies, dark horizontal collars, and layered overhangs below their occupied floors.
Four curved buttresses rise around each tower into a broad ribbed roof. Dark window bands and spaced lights separate
the pale walls from the roof rims. The offshore towers have elevated connecting bridges.
Two offshore domes carry dark breaching craft with raised central bodies and four curved drilling arms each.
The arms grip the outer roof rims beyond the combat area.

The engine room has covered copper service trenches at z=-121 and z=-99, ribbed machinery bays, and copper work shelves
at the existing tall cabinets. The assembly room keeps its central component cradle and adds suspended support rails
and local task lights. Both northern entrances have quartz frames, cyan lintels, and lit outer jambs.

The western dome has a copper service run, warm work lights, and a repair bench in its existing western cargo pocket.
The control hall has a shallow ceiling ring, four radial supports, and a glass-topped central table under a task light.
The freight hall has an overhead handling rail and cargo with metal bindings and ochre markings.
Its small cargo stacks sit on four low pallets under task lights. Two tall freight containers retain cover between aisles.
The workshop roof carries an exhaust cluster, the control roof a forked antenna, and the freight roof two service housings.
Support collars and buttresses sit below the occupied floors.

The southern stairs have lights in both side supports every four steps. The five stair columns remain clear.
All three northern dome entrances have illuminated jambs outside their five-wide passages.
Occupied domes use spaced perimeter lights rather than continuous lantern rings. Sea lanterns, shroomlights, and
ochre froglights provide world light. The generator does not add fullbright displays or invisible light blocks.

`fleets.py` builds the ships above both maps. Sturmwerft has a Republic carrier, two Separatist frigates, and three escorts.
Tessar has a carrier, a frigate, and three escorts, with sandstone hull panels and terracotta markings above the desert.
The ships have blue engine exhaust, underside lights, and separate bridge structures. Their footprints leave all six
landing cylinders open to the sky. They are saved blocks, so they add no display entities or recurring tasks.

Sturmwerft includes its fleet when regenerated. `python3 -I dev/mapgen/fleets.py` also writes both formations as a datapack
under `build/mapgen/fleets`, with `fleets:sturmwerft` and `fleets:tessar`, chunk-loading lists, and a bounds manifest.
Apply the Tessar function to a copy of its existing world to retain the hand-built terrain.

Floor wear follows paired wheel tracks with short connected repair patches. Drainage covers and transverse seams
break up the southern skybridge, and the ship passage has long edge seams. Floor treatments retain their original height.
Pilasters above the skybridge openings start at y=86, leaving the centerline clear at walking height.

## Equipment props

`environment.props` contains 42 placements from `yard_props()`. Each entry has a snake-case `kind`, an `at` string
containing `x y z yaw`, and a `scale` between 0.5 and 1.5. The default scale is 1.0.
The y coordinate is the visible base, including half-block slab tops. Yaw 0 faces south along +Z, yaw 90 faces west,
yaw 180 faces north, and yaw 270 faces east.

| Area | Count | Equipment groups |
| --- | --- | --- |
| Engine room | 8 | Couplers and cabinet panel at the copper work shelves, engine on the low service bench, three terminals, spare cargo |
| Assembly | 7 | Engine cradle at `41 85 -116`, terminals, coupler, staged cargo, repair droid |
| Workshop | 6 | Repair station at `-77 82 109.5`, warm-lit terminals, coupler, cabinet panel, spare cargo |
| Control hall | 14 | Four perimeter desks, cloning equipment at the north desk, two consoles at the central table, cabinet panels, service terminals |
| Freight hall | 7 | Six cargo groups on four lit pallets, with fronts toward the working aisles, and one container panel |

Northern console work surfaces end at y=84. Southern perimeter consoles and the repair station stand at y=82.
These one-block-high supports leave the equipment visible from the adjacent walking floor. The central control table
retains its raised surface at y=83. Cargo stacks use scale 0.9 or 1.0 in the freight hall and remain within their pallet footprints.

Models use `sternenfront/yard/<kind>` with `ItemDisplayTransform.NONE` and no model display transforms.
Their ground origin is model coordinate `(8,8,8)`. All elements fit the shared 2 by 1.5 by 2 block envelope at scale 1.0.
`YARD_PROP_BOUNDS` records bounds relative to that origin, including element rotations.
The six original equipment kinds match the yard asset generator's `bounds.json` from commit `903db8ef`.
The three cloning models use the conservative authored limits below. Their exported meshes may be smaller.
The cargo model has depth -0.6875..0.125 before yaw. The wall panel has depth -0.09375..0.075.
Panel placements account for this depth so their backs meet solid cabinet faces.

### North control-hall cloning desk

The Kamino-inspired desk pairs an upright adult clone tank with three incubation vessels in one rack and a stepped DNA sample pedestal.
The upright adult tank is an original adaptation, not an exact screen replica. The existing console remains the terminal.
The composition occupies the existing bench at x=6..13, z=94..95, with its top at y=82.
All four fronts face south along +Z. Sea lanterns replace quartz bench blocks at `8 81 95` and `12 81 95` below the models.
These task lights use world lighting without a display brightness override.

| Kind | `at` | Scale | Maximum authored bounds in model units | Relative bounds at scale 1 |
| --- | --- | --- | --- | --- |
| `clone_tank` | `7.4 82 95 0` | 1.5 | `[0,8,0]..[16,32,16]` | `[-0.5,0,-0.5]..[0.5,1.5,0.5]` |
| `gene_archive` | `9 82 95 0` | 0.8 | `[2.4,8,2.4]..[13.6,21,13.6]` | `[-0.35,0,-0.35]..[0.35,0.8125,0.35]` |
| `console` | `10.5 82 95 0` | 0.8 | Existing exported bounds | `YARD_PROP_BOUNDS["console"]` |
| `embryo_rack` | `12.5 82 95 0` | 1.0 | `[-4,8,1.6]..[20,22.4,14.4]` | `[-0.75,0,-0.4]..[0.75,0.9,0.4]` |

### Prop validation

All standing equipment rests on solid work surfaces. Panels have solid backing across their width and height.
The generator rejects model intersections, overlapping props, unsupported bases, and bounds that enter a player's body
at any standing position reachable by ordinary floors and stairs. It then treats the rotated bounds as solid blocks
for the existing circulation, interaction, board, cover, landing, and crane checks. These barriers exist only in validation.

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
  board: "-23.5 87 -126.5"
```

The board floats above its plinth beside the western approach. From the ship's northern bypass, take the western spur to
`-23.5 83 -131.5`. Face east along z=-131.5 and cross the fixed quartz platforms in order. The gaps are two blocks wide.
The platforms change height by at most one block. The finish joins the eastern return deck near `21.5 83 -131.5`.
The pit has floor 77. Its two stairs at x=-18 and x=15 rise south from z=-129 to z=-125 and return to the ship at z=-124.
The course is separate from normal combat circulation and the bulkhead bypass.

## Validation limits

The generator checks standing positions at all playable heights, one-block movement clearance, ordinary floor and stair
access to rooms and fighter positions, stair width and headroom, and both control bypasses while the hazards are active.
At least 23,000 standing positions must be reachable by ordinary floors and stairs.
It also checks interaction reach and support, board visibility, capture positions, cover and peek rays, fixed course landings, landing
cylinders to the sky, headquarters vehicle approaches, environment emitters and the moving crane's swept volume.

Circulation checks conservatively treat partial solid blocks as full blocks. Buttons and levers are passable.
The generator does not simulate player movement, jumping timing, bot navigation, rendered textures or round events.
The prop checks use exported bounding boxes and conservative block volumes rather than rendering the model meshes.
World regeneration and a real client check are still required after the runtime changes are integrated.

#!/usr/bin/env python3
"""Fits TTT 2 into the harbour of Novigrad, a TTT pool map cut from the Zeitkapsel exhibit with importMap.

`python3 -I dev/mapgen/novigrad.py` writes the fittings as a function and the map's map.yml. With `--apply` it then runs
the function on a bare Paper server over a copy of the template and writes the result back to the template, so run it
once on a fresh import (worlds/README.md, "Importing an old 1.8 map"); every fitting is set block by block, so a second
run changes nothing. The map's streets lie at y 6 to 12 and its sea at y 5 over sand, with bedrock at y 0.

Every coordinate below was chosen by walking the saved blocks: the stations stand against a wall of a room players
reach from the fish market, the two traitor passages run at y 1 to 4 where only earth, sand and sea lie, and the
torches hang on walls along the lanes, which the exhibit left almost dark.
"""
import argparse
import glob
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = ROOT / "worlds/ttt/blockcraft/TTT/maps/novigrad"
OUT = ROOT / "build/mapgen/novigrad"
BORDER = (944, 1420, 208)

SPAWNS = ((926, 8, 1481), (928, 8, 1478), (928, 8, 1484), (922, 6, 1481), (925, 8, 1476), (925, 8, 1486), (921, 6, 1477),
          (931, 8, 1476), (930, 8, 1487), (918, 5, 1480), (919, 10, 1485), (919, 6, 1474), (935, 9, 1486), (933, 8, 1473))
MARKET = (926, 1481)

TORCHES = (
    (841, 10, 1387, "east"), (842, 16, 1333, "south"), (844, 10, 1440, "east"), (845, 10, 1495, "west"), (848, 10, 1395, "north"),
    (848, 13, 1419, "north"), (848, 16, 1374, "west"), (854, 16, 1381, "west"), (855, 12, 1364, "north"), (856, 6, 1519, "west"),
    (856, 12, 1413, "north"), (859, 10, 1437, "south"), (861, 9, 1506, "north"), (865, 8, 1521, "east"), (865, 15, 1350, "east"),
    (865, 16, 1377, "east"), (866, 10, 1408, "north"), (869, 10, 1392, "east"), (871, 10, 1433, "south"), (872, 10, 1421, "east"),
    (875, 10, 1406, "south"), (876, 6, 1522, "south"), (880, 13, 1372, "south"), (881, 10, 1437, "east"), (884, 9, 1502, "north"),
    (885, 6, 1520, "south"), (885, 14, 1356, "north"), (886, 17, 1332, "east"), (889, 10, 1376, "east"), (891, 6, 1508, "east"),
    (891, 12, 1458, "south"), (893, 9, 1436, "south"), (898, 16, 1325, "east"), (899, 6, 1521, "south"), (901, 9, 1442, "south"),
    (901, 9, 1457, "north"), (907, 9, 1449, "west"), (907, 15, 1325, "west"), (909, 8, 1468, "east"), (911, 6, 1511, "west"),
    (913, 7, 1478, "east"), (913, 8, 1457, "south"), (914, 9, 1392, "east"), (916, 12, 1330, "south"), (917, 6, 1503, "west"),
    (919, 6, 1516, "south"), (919, 8, 1465, "south"), (919, 9, 1356, "west"), (922, 7, 1476, "west"), (930, 9, 1352, "north"),
    (934, 9, 1473, "west"), (934, 9, 1493, "west"), (935, 10, 1482, "west"), (944, 9, 1483, "south"), (945, 9, 1492, "east"),
    (953, 6, 1366, "east"), (959, 7, 1343, "north"), (960, 6, 1397, "east"), (967, 6, 1495, "south"), (967, 11, 1324, "west"),
    (978, 6, 1487, "east"), (981, 9, 1329, "south"), (986, 9, 1454, "west"), (990, 6, 1467, "east"), (995, 6, 1450, "east"),
    (995, 9, 1339, "north"), (997, 6, 1438, "east"), (1015, 11, 1343, "east"), (1037, 6, 1382, "south"), (1046, 6, 1385, "west"),
)

# The customs house stands on stilts in the basin east of the quay; its floor is level with the quay at y 7.
HOUSE = (1001, 1431, 1011, 1441)
FLOOR = 7
CHAMBER = (1004, 1433, 1008, 1437)

# Each passage: a hatch in the street, a ladder down to y 2, an iron door, a tunnel at y 2 and 3, a door and a ladder up.
PASSAGES = (((937, 9, 1467), (857, 11, 1362)), ((993, 8, 1360), (994, 8, 1438)))
TUNNEL_FLOOR = 1
TUNNEL_TOP = 4

GENERATOR = (952, 5, 1339, "observer[facing=west]")
SWORD = (943, 10, 1373, "ender_chest[facing=east]")

# key, kind, station (x, y, z, block), and for a fetch the destination and the cargo; puzzles name their game.
CHORES = (
    ("anker", "puzzle", "forge", (887, 10, 1451, "blast_furnace[facing=south,lit=false]"), True),
    ("trank", "puzzle", "potion", (979, 11, 1357, "brewing_stand"), True),
    ("spielwerk", "puzzle", "organ", (986, 8, 1351, "note_block"), False),
    ("seekarten", "puzzle", "chronicle", (872, 10, 1370, "chiseled_bookshelf[facing=west]"), False),
    ("zollbuch", "puzzle", "ledger", (1003, 8, 1439, "lectern[facing=east]"), False),
    ("kran", "puzzle", "millwork", (961, 8, 1486, "grindstone[face=floor,facing=north]"), False),
    ("leuchtfeuer", "hold", None, (894, 12, 1512, "campfire[lit=false,facing=west]"), True),
    ("glocke", "hold", None, (854, 12, 1358, "bell[attachment=floor,facing=north]"), True),
    ("netze", "hold", None, (990, 8, 1447, "loom[facing=west]"), False),
    ("kerzen", "hold", None, (860, 10, 1373, "candle[candles=4,lit=false]"), True),
    ("raeuchern", "hold", None, (873, 8, 1477, "smoker[facing=south,lit=false]"), False),
    ("fracht", "fetch", (966, 8, 1484, "barrel[facing=up]", "barrel"), (1020, 8, 1454, "barrel[facing=up]"), False),
    ("fang", "fetch", (875, 8, 1482, "barrel[facing=up]", "cod"), (1010, 8, 1452, "dried_kelp_block"), False),
    ("brief", "fetch", (910, 8, 1357, "lectern[facing=south]", "paper"), (943, 8, 1392, "cartography_table"), False),
    ("wein", "fetch", (906, 8, 1491, "barrel[facing=up]", "glass_bottle"), (955, 6, 1339, "barrel[facing=up]"), False),
    ("seil", "fetch", (992, 8, 1425, "spruce_fence", "lead"), (918, 8, 1377, "stripped_spruce_log"), False),
)

LOOSE = ("sand", "red_sand", "gravel", "water", "suspicious_sand", "suspicious_gravel")
STEADY = {"sand": "sandstone", "red_sand": "red_sandstone", "gravel": "cobblestone", "water": "stone_bricks",
          "suspicious_sand": "sandstone", "suspicious_gravel": "cobblestone"}


class Fittings:
    def __init__(self):
        self.commands = []

    def set(self, x, y, z, block):
        self.commands.append(f"setblock {x} {y} {z} {block}")

    def fill(self, x1, y1, z1, x2, y2, z2, block, mode=""):
        low = (min(x1, x2), min(y1, y2), min(z1, z2))
        high = (max(x1, x2), max(y1, y2), max(z1, z2))
        self.commands.append(f"fill {low[0]} {low[1]} {low[2]} {high[0]} {high[1]} {high[2]} {block} {mode}".rstrip())

    def steady(self, x1, y1, z1, x2, y2, z2):
        """Turns what would fall or flow into a hollowed box into stone of the same look."""
        for loose in LOOSE:
            self.fill(x1, y1, z1, x2, y2, z2, STEADY[loose], f"replace {loose}")


def customs_house(f):
    """The tester stands in the customs house, a stone and timber hall on stilts with its lava pit dug into the sand."""
    x1, z1, x2, z2 = HOUSE
    mx, mz = (x1 + x2) // 2, (z1 + z2) // 2
    for x in (x1, mx, x2):
        for z in (z1, mz, z2):
            if (x, z) != (mx, mz):
                f.fill(x, 1, z, x, FLOOR - 1, z, "spruce_log")
    f.fill(x1 - 1, FLOOR, z1 - 1, x2 + 1, FLOOR, z2 + 1, "spruce_planks")
    f.fill(997, FLOOR, mz - 2, x1 - 1, FLOOR, mz + 2, "spruce_planks")
    for z in (mz - 2, mz + 2):
        f.fill(997, FLOOR + 1, z, x1 - 2, FLOOR + 1, z, "spruce_fence")
    f.fill(x1, FLOOR, z1, x2, FLOOR, z2, "stone_bricks")
    f.fill(x1 + 1, FLOOR, z1 + 1, x2 - 1, FLOOR, z2 - 1, "spruce_planks")
    f.fill(x1, FLOOR + 1, z1, x2, FLOOR + 1, z2, "stone_bricks", "outline")
    f.fill(x1, FLOOR + 2, z1, x2, FLOOR + 4, z2, "spruce_planks", "outline")
    f.fill(x1 + 1, FLOOR + 1, z1 + 1, x2 - 1, FLOOR + 4, z2 - 1, "air")
    for x in (x1, mx, x2):
        for z in (z1, mz, z2):
            if (x in (x1, x2)) or (z in (z1, z2)):
                f.fill(x, FLOOR + 1, z, x, FLOOR + 4, z, "stripped_dark_oak_log")
    for x in (x1 + 2, x2 - 2):
        for z in (z1, z2):
            f.fill(x, FLOOR + 2, z, x, FLOOR + 3, z, "glass_pane")
    for z in (z1 + 2, z2 - 2):
        f.fill(x2, FLOOR + 2, z, x2, FLOOR + 3, z, "glass_pane")
    f.fill(x1, FLOOR + 1, mz - 1, x1, FLOOR + 2, mz, "air")
    for step in range(0, 7):
        y = FLOOR + 5 + step
        f.fill(x1 - 1, y, z1 - 1 + step, x2 + 1, y, z1 - 1 + step, "dark_oak_stairs[facing=south]")
        f.fill(x1 - 1, y, z2 + 1 - step, x2 + 1, y, z2 + 1 - step, "dark_oak_stairs[facing=north]")
        if z1 + step <= z2 - step:
            for x in (x1, x2):
                f.fill(x, y, z1 + step, x, y, z2 - step, "spruce_planks")
    f.fill(x1 - 1, FLOOR + 11, mz, x2 + 1, FLOOR + 11, mz, "dark_oak_slab[type=bottom]")

    cx1, cz1, cx2, cz2 = CHAMBER
    f.fill(cx1, 1, cz1, cx2, FLOOR - 1, cz2, "stone_bricks")
    f.fill(cx1 + 1, 1, cz1 + 1, cx2 - 1, 2, cz2 - 1, "lava")
    f.fill(cx1 + 1, 3, cz1 + 1, cx2 - 1, FLOOR - 1, cz2 - 1, "air")
    f.fill(cx1 + 1, FLOOR, cz1 + 1, cx2 - 1, FLOOR, cz2 - 1, "polished_andesite")
    f.fill(cx1, FLOOR + 1, cz1, cx2, FLOOR + 4, cz2, "polished_andesite", "outline")
    f.fill(cx1 + 1, FLOOR + 1, cz1 + 1, cx2 - 1, FLOOR + 3, cz2 - 1, "air")
    f.fill(cx1 + 1, FLOOR + 4, cz1 + 1, cx2 - 1, FLOOR + 4, cz2 - 1, "polished_andesite")
    door_x = (cx1 + cx2) // 2
    f.fill(door_x, FLOOR + 1, cz2, door_x, FLOOR + 2, cz2, "air")
    for x in range(cx1 + 1, cx2):
        f.set(x, FLOOR + 3, cz2, "redstone_lamp")
        f.set(x, FLOOR + 3, cz1, "redstone_lamp")
    f.set(door_x, FLOOR + 2, cz1 + 1, "stone_button[face=wall,facing=south]")
    f.set(x2 + 1, FLOOR + 1, mz + 2, "stone_button[face=wall,facing=east]")

    f.set(x2 - 1, FLOOR + 1, z2 - 1, "barrel[facing=up]")
    f.set(x2 - 1, FLOOR + 2, z2 - 1, "barrel[facing=up]")
    f.set(x2 - 2, FLOOR + 1, z2 - 1, "barrel[facing=up]")
    f.set(x2 - 1, FLOOR + 3, z2 - 1, "lantern")
    f.set(x1 + 1, FLOOR + 1, z1 + 1, "barrel[facing=up]")
    f.set(x1 + 1, FLOOR + 2, z1 + 1, "lantern")
    f.set(x1 + 3, FLOOR + 3, z2 - 1, "wall_torch[facing=north]")
    f.set(x2 - 3, FLOOR + 3, z1 + 1, "wall_torch[facing=south]")
    for x in (997, x1 - 2):
        for z in (mz - 2, mz + 2):
            f.set(x, FLOOR + 2, z, "lantern")
    return {
        "zone": (cx1 + 1, FLOOR + 1, cz1 + 1, cx2 - 1, FLOOR + 3, cz2 - 1),
        "button": (door_x, FLOOR + 2, cz1 + 1),
        "entrance": (door_x, FLOOR + 1, cz2, door_x, FLOOR + 2, cz2),
        "lamps": [(x, FLOOR + 3, z) for z in (cz2, cz1) for x in range(cx1 + 1, cx2)],
        "trap": {"button": (x2 + 1, FLOOR + 1, mz + 2), "floor": (cx1 + 1, FLOOR, cz1 + 1, cx2 - 1, FLOOR, cz2 - 1)},
    }


def run(a, b):
    """The straight steps from a to b along x, then along z."""
    (ax, az), (bx, bz) = a, b
    sx = 1 if bx >= ax else -1
    sz = 1 if bz >= az else -1
    return [(x, az) for x in range(ax, bx + sx, sx)] + [(bx, z) for z in range(az + sz, bz + sz, sz)]


def passage(f, hatch_a, hatch_b):
    """Returns the lower halves of the passage's two iron doors."""
    path = run((hatch_a[0], hatch_a[2]), (hatch_b[0], hatch_b[2]))
    for (x, z) in path:
        f.steady(x - 1, TUNNEL_FLOOR, z - 1, x + 1, TUNNEL_TOP, z + 1)
    for (x, z) in path:
        f.fill(x, TUNNEL_FLOOR + 1, z, x, TUNNEL_TOP - 1, z, "air")
    doors = []
    for hatch, inner in ((hatch_a, path[1]), (hatch_b, path[-2])):
        x, y, z = hatch
        f.steady(x - 1, TUNNEL_TOP, z - 1, x + 1, y - 2, z + 1)
        f.fill(x, TUNNEL_FLOOR + 1, z, x, y - 2, z, "air")
        dx, dz = inner[0] - x, inner[1] - z
        back = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(dx, dz)]
        wall = (x - dx, z - dz)
        f.fill(wall[0], TUNNEL_FLOOR + 1, wall[1], wall[0], y - 2, wall[1], "stone_bricks")
        f.fill(x, TUNNEL_FLOOR + 1, z, x, y - 2, z, f"ladder[facing={back}]")
        f.set(x, y - 1, z, f"spruce_trapdoor[half=top,open=false,facing={back}]")
        f.set(inner[0], TUNNEL_FLOOR + 1, inner[1], f"iron_door[half=lower,facing={back}]")
        f.set(inner[0], TUNNEL_FLOOR + 2, inner[1], f"iron_door[half=upper,facing={back}]")
        doors.append((inner[0], TUNNEL_FLOOR + 1, inner[1]))
    for (x, z) in path[3:-3:12]:
        f.set(x, TUNNEL_TOP, z, "glowstone")
    return doors


def build():
    f = Fittings()
    tester = customs_house(f)
    traitor_doors = [door for a, b in PASSAGES for door in passage(f, a, b)]
    for x, y, z, facing in TORCHES:
        f.set(x, y, z, f"wall_torch[facing={facing}]")
    x, y, z, block = GENERATOR
    f.set(x, y, z, block)
    f.set(x, y + 1, z, "lantern")
    x, y, z, block = SWORD
    f.set(x, y, z, block)
    chores = []
    for key, kind, extra, station, visible in CHORES:
        x, y, z, block = station
        f.set(x, y, z, block)
        entry = {"key": key, kind: {"x": x, "y": y, "z": z}}
        if kind == "puzzle":
            entry["game"] = extra
        if kind == "fetch":
            dx, dy, dz, dblock, cargo = extra
            f.set(dx, dy, dz, dblock)
            entry["to"] = {"x": dx, "y": dy, "z": dz}
            entry["cargo"] = cargo
        if visible:
            entry["visible"] = True
        chores.append(entry)
    return f, tester, traitor_doors, chores


def spot(values):
    return "{" + ", ".join(f"{axis}: {value}" for axis, value in zip("xyz", values)) + "}"


def box(values):
    return "{" + ", ".join(f"{axis}: {value}" for axis, value in zip(("x1", "y1", "z1", "x2", "y2", "z2"), values)) + "}"


def map_yml(tester, traitor_doors, chores):
    import math
    lines = ["# Generated by dev/mapgen/novigrad.py; change that and run it again.", "name: Novigrad",
             "builders: [Elysium Fire, BlockcraftMC]", "icon: oak_boat",
             f"border: {{x: {BORDER[0]}, z: {BORDER[1]}, size: {BORDER[2]}}}",
             "spectator: {x: 926.5, y: 48.0, z: 1515.5, yaw: 180.0, pitch: 40.0}", "spawns:"]
    for x, y, z in SPAWNS:
        yaw = math.degrees(math.atan2(-(MARKET[0] - x), MARKET[1] - z)) if (x, z) != MARKET else 0.0
        lines.append(f"  - {{x: {x + 0.5}, y: {float(y)}, z: {z + 0.5}, yaw: {round(yaw, 1)}}}")
    lines += ["tester:", f"  zone: {box(tester['zone'])}", f"  button: {spot(tester['button'])}",
              f"  entrance: {box(tester['entrance'])}", "  lamps:"]
    lines += [f"    - {spot(lamp)}" for lamp in tester["lamps"]]
    lines += ["  trap:", f"    button: {spot(tester['trap']['button'])}", f"    floor: {box(tester['trap']['floor'])}",
              "traitor-doors:"]
    lines += [f"  - {spot(door)}" for door in traitor_doors]
    lines += ["tasks:", f"  generator: {spot(GENERATOR[:3])}", "  chores:"]
    for chore in chores:
        parts = [f"key: {chore['key']}"]
        for name in ("hold", "puzzle", "fetch"):
            if name in chore:
                parts.append(f"{name}: {spot(chore[name].values())}")
        if "game" in chore:
            parts.append(f"game: {chore['game']}")
        if "to" in chore:
            parts.append(f"to: {spot(chore['to'].values())}")
            parts.append(f"cargo: {chore['cargo']}")
        if chore.get("visible"):
            parts.append("visible: true")
        lines.append("    - {" + ", ".join(parts) + "}")
    return "\n".join(lines) + "\n"


def paper_jar():
    jars = sorted(glob.glob(str(Path.home() / ".gradle/caches/run-task-jars/paper/jars/26.3/*.jar")),
                  key=lambda path: int(Path(path).stem))
    if not jars:
        sys.exit("No Paper 26.3 jar in Gradle's run-paper cache; run ./gradlew importMap or :paper:runServer once first.")
    return jars[-1]


def apply(function):
    """Runs the function on a bare server over a copy of the template and copies the changed dimension back."""
    server = OUT / "server"
    shutil.rmtree(server, ignore_errors=True)
    dimension = server / "world/dimensions/minecraft/overworld"
    shutil.copytree(TEMPLATE / "world", dimension)
    pack = server / "world/datapacks/novigrad"
    (pack / "data/novigrad/function").mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 121, "min_format": 121, "max_format": 121,
                                                           "description": "Novigrad"}}))
    (pack / "data/novigrad/function/build.mcfunction").write_text(function)
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text("\n".join([
        "level-type=minecraft\\:flat", 'generator-settings={"layers"\\:[],"biome"\\:"minecraft\\:the_void"}',
        "online-mode=false", "server-port=25599", "view-distance=2", "simulation-distance=2", "spawn-protection=0",
        "generate-structures=false", "enable-command-block=false"]) + "\n")
    process = subprocess.Popen(["java", "-Xmx3G", "-jar", paper_jar(), "--nogui"], cwd=server, stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)

    def wait_for(text, seconds):
        deadline = time.monotonic() + seconds
        for line in process.stdout:
            print(line, end="")
            if text in line:
                return
            if time.monotonic() > deadline:
                break
        process.kill()
        sys.exit(f"The server never printed {text!r}")

    def say(command):
        process.stdin.write(command + "\n")
        process.stdin.flush()

    wait_for("Done (", 300)
    x, z, size = BORDER
    say(f"forceload add {x - size // 2} {z - size // 2} {x + size // 2} {z + size // 2}")
    say("datapack enable \"file/novigrad\"")
    time.sleep(15)
    say("function novigrad:build")
    say("say novigrad built")
    wait_for("novigrad built", 120)
    say("forceload remove all")
    say("save-all flush")
    time.sleep(10)
    say("stop")
    process.wait(120)
    for part in ("region", "entities", "poi"):
        shutil.rmtree(TEMPLATE / "world" / part, ignore_errors=True)
        if (dimension / part).exists():
            shutil.copytree(dimension / part, TEMPLATE / "world" / part)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="build the fittings into the template")
    args = parser.parse_args()
    f, tester, traitor_doors, chores = build()
    OUT.mkdir(parents=True, exist_ok=True)
    function = "\n".join(f.commands) + "\n"
    (OUT / "build.mcfunction").write_text(function)
    (TEMPLATE / "map.yml").write_text(map_yml(tester, traitor_doors, chores))
    print(f"{len(f.commands)} commands, {len(chores)} chores, {OUT}")
    if args.apply:
        apply(function)


if __name__ == "__main__":
    sys.exit(main())

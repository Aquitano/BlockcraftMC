#!/usr/bin/env python3
"""Generates Silo 7, a Zombies map: a coastal research outpost built over the sealed silo its creatures escaped from.

Run through `dev/verify/verify.py map silo7`, which starts a bare server over an endless flat sea, runs the functions and
saves the result to worlds/silo7. Coordinates below are relative to ORIGIN, as in the arena file: players stand at y 0
on the surface and the silo floor is at y -24. SILO7.md describes the layout and what the checks cover.
"""
import json
import math
import random
import sys
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/mapgen/silo7"
ORIGIN = (0, 64, 0)
SEA = -8
MOST_PER_FILL = 32768
COMMANDS_PER_FUNCTION = 30000
SURFACE_TOP = 16
EDGE = 52
BAY_DEPTH = 7
BULB = "waxed_copper_bulb[lit=false,powered=false]"
DOOR = "waxed_cut_copper"
BOARD = "oak_slab[type=top,waterlogged=false]"
SILO_RADIUS = 15.5
SILO_FLOOR = -24
GALLERY = -12
RING_INNER = 11.0
STAIRS = (("x", (9, 11), -7, 1), ("z", (9, 11), 6, -1))

ROOMS = {
    "yard": (-20, 0, -20, 20, SURFACE_TOP, 20),
    "workshop": (-20, 0, -52, 20, SURFACE_TOP, -21),
    "radio_tower": (21, 0, -52, 52, SURFACE_TOP, -21),
    "greenhouse": (21, 0, -20, 52, SURFACE_TOP, 52),
    "cold_store": (-52, 0, -52, -21, SURFACE_TOP, 20),
    "power": (-52, 0, 21, 20, SURFACE_TOP, 52),
    "silo": (-20, -26, -36, 20, -1, 38),
}

DOORS = {
    "yard-workshop": (("yard", "workshop"), 750, (-1, 0, -20, 1, 2, -20)),
    "yard-greenhouse": (("yard", "greenhouse"), 750, (20, 0, -1, 20, 2, 1)),
    "yard-cold-store": (("yard", "cold_store"), 1000, (-20, 0, -1, -20, 2, 1)),
    "workshop-radio-tower": (("workshop", "radio_tower"), 1000, (20, 0, -38, 20, 2, -36)),
    "workshop-cold-store": (("workshop", "cold_store"), 750, (-20, 0, -38, -20, 2, -36)),
    "radio-tower-greenhouse": (("radio_tower", "greenhouse"), 1000, (34, 0, -20, 36, 2, -20)),
    "greenhouse-power": (("greenhouse", "power"), 1250, (20, 0, 36, 20, 2, 38)),
    "cold-store-power": (("cold_store", "power"), 1250, (-38, 0, 20, -36, 2, 20)),
    "silo-gate": (("power", "silo"), 2500, (-6, 0, 35, -4, 2, 35)),
}

# Each window: room, the wall it sits in ("x" or "z" and the plane), the first of its two board columns, which way the bay
# lies from the wall, and the floor the boards stand on.
WINDOWS = {
    "yard-north": ("yard", "z", -20, 15, -1, 0),
    "yard-east": ("yard", "x", 20, 15, 1, 0),
    "yard-south": ("yard", "z", 20, -16, 1, 0),
    "yard-west": ("yard", "x", -20, -16, -1, 0),
    "workshop-west": ("workshop", "z", -52, -6, -1, 0),
    "workshop-east": ("workshop", "z", -52, 9, -1, 0),
    "radio-tower-east": ("radio_tower", "x", 52, -40, 1, 0),
    "radio-tower-north": ("radio_tower", "z", -52, 37, -1, 0),
    "greenhouse-glass": ("greenhouse", "x", 52, -2, 1, 0),
    "greenhouse-field": ("greenhouse", "x", 52, 30, 1, 0),
    "greenhouse-south": ("greenhouse", "z", 52, 36, 1, 0),
    "cold-store-hall": ("cold_store", "x", -52, 4, -1, 0),
    "cold-store-dock": ("cold_store", "x", -52, -37, -1, 0),
    "cold-store-north": ("cold_store", "z", -52, -38, -1, 0),
    "power-hall": ("power", "z", 52, 15, 1, 0),
    "power-tanks": ("power", "x", -52, 36, -1, 0),
    "power-south": ("power", "z", 52, -37, 1, 0),
    "silo-east": ("silo", "x", 17, -1, 1, SILO_FLOOR),
    "silo-south": ("silo", "z", 17, 8, 1, SILO_FLOOR),
    "silo-gallery": ("silo", "x", -17, 9, -1, GALLERY),
}

STATIONS = {
    "yard-armory": ("yard", "ARMORY", None, (-4, 0, 17)),
    "yard-armor": ("yard", "ARMOR", None, (5, 0, 17)),
    "workshop-armory": ("workshop", "ARMORY", None, (18, 0, -46)),
    "workshop-quick-reload": ("workshop", "PERKS", "QUICK_RELOAD", (-18, 0, -24)),
    "radio-tower-mystery": ("radio_tower", "MYSTERY", None, (44, 7, -32)),
    "greenhouse-extra-health": ("greenhouse", "PERKS", "EXTRA_HEALTH", (44, 0, 3)),
    "greenhouse-revive": ("greenhouse", "TEAM_REVIVE", None, (28, 0, 44)),
    "cold-store-frozen-bullets": ("cold_store", "PERKS", "FROZEN_BULLETS", (-50, 0, -10)),
    "generator": ("power", "POWER", None, (0, 0, 49)),
    "power-quick-revive": ("power", "PERKS", "QUICK_REVIVE", (18, 0, 25)),
    "silo-upgrade": ("silo", "UPGRADE", None, (0, GALLERY, -14)),
    "silo-armory": ("silo", "ARMORY", None, (6, SILO_FLOOR, -13)),
}
STATION_BLOCKS = {"ARMORY": "smithing_table", "ARMOR": "blast_furnace[facing=north]", "PERKS": "brewing_stand",
                  "POWER": "lodestone", "UPGRADE": "enchanting_table", "MYSTERY": "ender_chest[facing=north]",
                  "TEAM_REVIVE": "beacon"}

NOTES = {
    "silo_yard": ("yard", (14, 0, 9)),
    "silo_workshop": ("workshop", (-17, 0, -42)),
    "silo_tower": ("radio_tower", (32, 0, -44)),
    "silo_greenhouse": ("greenhouse", (49, 0, -17)),
    "silo_cold": ("cold_store", (-49, 0, 14)),
    "silo_power": ("power", (-9, 0, 50)),
    "silo_lab": ("silo", (-9, GALLERY, -11)),
}

SPAWN = (0.5, 0, 12.5, 180)
SPECTATOR = (0.5, 14, 0.5)
HIGH_GROUND = (26, 0, -47, 47, 8, -29)
LIFT_SHAFT = (-3, -25, -34, 3, -1, -28)
BOSS_GATE = (-17, SILO_FLOOR, -3, -16, -17, 3)
BOSS_CHAMBER = (-27, SILO_FLOOR, -5, -18, -15, 5)
BOSS_SPAWN = (-22.5, SILO_FLOOR, 0.5, 270)
BOSS_BODY = (4, 7)


def runs(values):
    values = sorted(values)
    if not values:
        return
    start = previous = values[0]
    for value in values[1:]:
        if value != previous + 1:
            yield start, previous
            start = value
        previous = value
    yield start, previous


class Builder:
    """Fills in arena coordinates. Later fills win, as in the game."""

    def __init__(self, seed):
        self.fills = []
        self.rng = random.Random(seed)

    def box(self, x1, y1, z1, x2, y2, z2, block):
        x1, x2, y1, y2, z1, z2 = min(x1, x2), max(x1, x2), min(y1, y2), max(y1, y2), min(z1, z2), max(z1, z2)
        self.fills.append((x1, y1, z1, x2, y2, z2, block))

    def put(self, x, y, z, block):
        self.fills.append((x, y, z, x, y, z, block))

    def column(self, x, z, y1, y2, block):
        self.box(x, y1, z, x, y2, z, block)

    def chance(self, p):
        return self.rng.random() < p

    def pick(self, *choices):
        return self.rng.choice(choices)

    def floor(self, x1, z1, x2, z2, palette, y=-1):
        """A floor from a weighted palette, a fill per run of equal blocks."""
        blocks, weights = zip(*palette)
        for z in range(min(z1, z2), max(z1, z2) + 1):
            row = [self.rng.choices(blocks, weights)[0] for _ in range(min(x1, x2), max(x1, x2) + 1)]
            start = 0
            for index in range(1, len(row) + 1):
                if index == len(row) or row[index] != row[start]:
                    self.box(min(x1, x2) + start, y, z, min(x1, x2) + index - 1, y, z, row[start])
                    start = index

    def stripes(self, x1, z1, x2, z2, y=-1, length=2):
        along_x = abs(x2 - x1) >= abs(z2 - z1)
        start, end = (min(x1, x2), max(x1, x2)) if along_x else (min(z1, z2), max(z1, z2))
        for index, at in enumerate(range(start, end + 1, length)):
            stop = min(at + length - 1, end)
            block = "yellow_concrete" if index % 2 == 0 else "black_concrete"
            if along_x:
                self.box(at, y, z1, stop, y, z2, block)
            else:
                self.box(x1, y, at, x2, y, stop, block)

    def disc(self, cx, cz, radius, y1, y2, block, inner=-1.0):
        """Blocks whose centres lie within radius of (cx, cz) and beyond inner, a fill per row."""
        for z in range(math.floor(cz - radius) - 1, math.ceil(cz + radius) + 1):
            xs = [x for x in range(math.floor(cx - radius) - 1, math.ceil(cx + radius) + 1)
                  if inner < math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= radius]
            for start, end in runs(xs):
                self.box(start, y1, z, end, y2, z, block)

    def hanging_lamp(self, x, top, z, bottom, lamp="lantern[hanging=true]"):
        if top > bottom:
            self.box(x, bottom + 1, z, x, top, z, "iron_chain[axis=y]")
        self.put(x, bottom, z, lamp)

    def crates(self, x1, z1, x2, z2, height=2, y=0):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                for level in range(self.rng.randint(1, height)):
                    self.put(x, y + level, z, self.pick("barrel[facing=up]", "spruce_planks", "stripped_spruce_wood",
                                                        "barrel[facing=north]"))

    def barrels(self, x, z, count, y=0):
        for index in range(count):
            dx, dz = index % 2, index // 2
            self.put(x + dx, y, z + dz, self.pick("barrel[facing=up]", "barrel[facing=up]", "waxed_copper_block"))

    def floodlight(self, x, z, height, toward_x, toward_z, y=0):
        self.column(x, z, y, y + height - 1, "polished_deepslate")
        self.put(x, y + height, z, "iron_block")
        self.put(x + toward_x, y + height, z + toward_z, BULB)
        self.put(x - toward_x, y + height, z - toward_z, "lantern")
        self.put(x, y + height + 1, z, "lightning_rod[facing=up]")

    def sandbags(self, x1, z1, x2, z2, y=0):
        self.box(x1, y, z1, x2, y, z2, "packed_mud")
        self.box(x1, y + 1, z1, x2, y + 1, z2, "mud_brick_slab[type=bottom]")

    def truck(self, x, z, length, facing, body, cover, y=0):
        """A three-wide truck: cab at the facing end, wheels on both sides."""
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
        sx, sz = abs(dz), abs(dx)
        for step in range(length):
            cx, cz = x + dx * step, z + dz * step
            cab = step >= length - 3
            for side in (-1, 0, 1):
                bx, bz = cx + sx * side, cz + sz * side
                self.put(bx, y, bz, "black_concrete")
                self.put(bx, y + 1, bz, body)
                if cab:
                    self.put(bx, y + 2, bz, "gray_stained_glass" if step == length - 2 and side == 0 else body)
                    if step < length - 1:
                        self.put(bx, y + 3, bz, body)
                else:
                    self.put(bx, y + 2, bz, cover)
                    self.put(bx, y + 3, bz, cover if side == 0 or step % 3 else "air")
            if step in (1, 2, length - 2):
                for side in (-2, 2):
                    self.put(cx + sx * side, y, cz + sz * side, "polished_blackstone")
        front = x + dx * length, z + dz * length
        self.put(front[0], y + 1, front[1], "iron_trapdoor[facing=" + facing + ",half=bottom,open=true]")
        for side in (-1, 1):
            self.put(front[0] + sx * side, y + 1, front[1] + sz * side, "sea_lantern")

    def container(self, x1, z1, x2, z2, colour, y=0, height=3):
        """A shipping container with ribbed sides and dark corner posts; the end facing the longer axis has doors."""
        self.box(x1, y, z1, x2, y + height - 1, z2, f"{colour}_concrete")
        along_x = x2 - x1 >= z2 - z1
        for at in range(x1 if along_x else z1, (x2 if along_x else z2) + 1, 2):
            if along_x:
                self.box(at, y, z1, at, y + height - 1, z1, f"{colour}_terracotta")
                self.box(at, y, z2, at, y + height - 1, z2, f"{colour}_terracotta")
            else:
                self.box(x1, y, at, x1, y + height - 1, at, f"{colour}_terracotta")
                self.box(x2, y, at, x2, y + height - 1, at, f"{colour}_terracotta")
        for x in (x1, x2):
            for z in (z1, z2):
                self.column(x, z, y, y + height - 1, "polished_deepslate")
        if along_x:
            self.box(x2, y, z1 + 1, x2, y + height - 2, z2 - 1, "iron_block")
        else:
            self.box(x1 + 1, y, z2, x2 - 1, y + height - 2, z2, "iron_block")

    def tank(self, cx, cz, radius, height, body, band, y=0):
        self.disc(cx, cz, radius, y, y + height - 1, body)
        self.disc(cx, cz, radius, y + height // 2, y + height // 2, band)
        self.disc(cx, cz, radius - 1, y + height, y + height, "smooth_stone_slab[type=bottom]")
        self.put(math.floor(cx), y + height, math.floor(cz), "lightning_rod[facing=up]")


def wall_box(b, x1, z1, x2, z2, y1, y2, block):
    """The four walls of a box, without its inside."""
    b.box(x1, y1, z1, x2, y2, z1, block)
    b.box(x1, y1, z2, x2, y2, z2, block)
    b.box(x1, y1, z1, x1, y2, z2, block)
    b.box(x2, y1, z1, x2, y2, z2, block)


def noise(seed):
    rng = random.Random(seed)
    lattice = {}

    def at(x, z, scale):
        gx, gz = x / scale, z / scale
        x0, z0 = math.floor(gx), math.floor(gz)
        tx, tz = gx - x0, gz - z0
        tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)

        def corner(cx, cz):
            key = (cx, cz, scale)
            if key not in lattice:
                lattice[key] = rng.random() * 2 - 1
            return lattice[key]

        top = corner(x0, z0) * (1 - tx) + corner(x0 + 1, z0) * tx
        bottom = corner(x0, z0 + 1) * (1 - tx) + corner(x0 + 1, z0 + 1) * tx
        return top * (1 - tz) + bottom * tz

    return at


def terrain(b):
    """An island plateau over the sea: rock to y -2, soil on top, forest beyond the fence, cliffs to the water."""
    shape = noise(77)
    tops = {}
    for x in range(-100, 101):
        for z in range(-100, 101):
            edge = max(abs(x), abs(z)) + 6 * shape(x, z, 23) + 3 * shape(x, z, 7)
            if edge < 84:
                tops[x, z] = -1
            elif edge < 88:
                tops[x, z] = -3
            elif edge < 93:
                tops[x, z] = -6
            elif edge < 97:
                tops[x, z] = SEA - 2
    for z in range(-100, 101):
        by_height = {}
        for x in range(-100, 101):
            if (x, z) in tops:
                by_height.setdefault(tops[x, z], []).append(x)
        for top, xs in by_height.items():
            for start, end in runs(xs):
                b.box(start, -19, z, end, top - 1, z, "stone")
                surface = "gravel" if top <= SEA else "stone" if top <= -6 else "coarse_dirt" if top == -3 else "grass_block"
                b.box(start, top, z, end, top, z, surface)
    for x in range(-96, 97, 3):
        for z in range(-96, 97, 3):
            top = tops.get((x, z))
            if top is None or (abs(x) < 62 and abs(z) < 62):
                continue
            roll = b.rng.random()
            if top == -1 and roll < 0.09:
                b.box(x, top, z, x + 1, top, z + 1, b.pick("podzol", "coarse_dirt", "moss_block"))
            elif top in (-3, -6) and roll < 0.25:
                b.box(x, top + 1, z, x + b.rng.randint(0, 1), top + b.rng.randint(1, 2), z, b.pick("cobblestone", "andesite", "mossy_cobblestone", "tuff"))
    trees = []
    for x in range(-90, 91, 5):
        for z in range(-90, 91, 5):
            tx, tz = x + b.rng.randint(-2, 2), z + b.rng.randint(-2, 2)
            if tops.get((tx, tz)) != -1 or max(abs(tx), abs(tz)) < 64 or b.chance(0.25):
                continue
            if abs(tx) < 6 and tz > 60:
                continue
            trees.append((tx, tz))
    for tx, tz in trees:
        spruce(b, tx, tz, b.rng.randint(8, 14), dead=b.chance(0.15))
    for x in range(-84, 85, 2):
        for z in range(-84, 85, 2):
            if tops.get((x, z)) == -1 and max(abs(x), abs(z)) > 61 and (x, z) not in trees and b.chance(0.12):
                b.put(x, 0, z, b.pick("short_grass", "fern", "short_grass"))
    road = [(x, z) for z in range(53, 90) for x in range(-3, 4)]
    for x, z in road:
        if (x, z) in tops:
            b.put(x, tops[x, z], z, "gravel" if abs(x) == 3 else b.pick("dirt_path", "coarse_dirt", "dirt_path"))
    return tops


def spruce(b, x, z, height, dead=False):
    b.column(x, z, 0, height - 1, "spruce_log[axis=y]")
    if dead:
        b.put(x + b.pick(-1, 1), height - 3, z, "spruce_log[axis=x]")
        return
    for y in range(3, height + 1):
        radius = max(0, (height - y) // 3 + (1 if y % 2 == 0 else 0))
        if y == height:
            radius = 0
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                if abs(dx) + abs(dz) <= radius + 1 and (dx, dz) != (0, 0) or y >= height - 1:
                    b.put(x + dx, y, z + dz, "spruce_leaves[persistent=true]")
    b.put(x, height, z, "spruce_leaves[persistent=true]")


def perimeter(b):
    """The compound wall around the outdoor zones, with wire on top, and the ground inside it."""
    b.box(-EDGE, -6, -EDGE, EDGE, -2, EDGE, "stone")
    for x1, z1, x2, z2 in ((-EDGE, -EDGE, EDGE, -EDGE), (-EDGE, EDGE, EDGE, EDGE), (-EDGE, -EDGE, -EDGE, EDGE), (EDGE, -EDGE, EDGE, EDGE)):
        b.box(x1, 0, z1, x2, 3, z2, "polished_andesite")
        b.box(x1, 0, z1, x2, 0, z2, "andesite")
        b.box(x1, 4, z1, x2, 4, z2, "polished_andesite_slab[type=bottom]")
        b.box(x1, -1, z1, x2, -1, z2, "stone_bricks")
    for at in range(-EDGE, EDGE + 1, 6):
        for x, z in ((at, -EDGE), (at, EDGE), (-EDGE, at), (EDGE, at)):
            b.column(x, z, 0, 4, "polished_deepslate")
            b.put(x, 5, z, "iron_bars")
    for x in (-EDGE, EDGE):
        for z in (-EDGE, EDGE):
            b.column(x, z, 0, 6, "polished_deepslate")
            b.put(x, 7, z, "lantern")
    b.box(-4, 0, EDGE, 4, 5, EDGE, "polished_deepslate")
    b.box(-3, 0, EDGE + 1, 3, 3, EDGE + 1, "iron_bars")
    b.box(-4, 6, EDGE, 4, 6, EDGE, "smooth_stone_slab[type=bottom]")
    for x in (-5, 5):
        b.column(x, EDGE, 0, 7, "polished_deepslate")
        b.put(x, 8, EDGE, "lantern")
    b.stripes(-3, EDGE + 2, 3, EDGE + 2)


def yard(b):
    b.floor(-19, -19, 19, 19, [("stone_bricks", 10), ("cracked_stone_bricks", 2), ("andesite", 3), ("tuff", 1),
                               ("polished_andesite", 2), ("gravel", 1)])
    for z1, z2, x1, x2 in ((-19, 19, -1, 1), (-1, 1, -19, 19)):
        b.box(x1, -1, z1, x2, -1, z2, "polished_andesite")
    b.box(-19, -1, -1, -19, -1, 1, "yellow_concrete")
    b.box(19, -1, -1, 19, -1, 1, "yellow_concrete")
    b.box(-1, -1, -19, 1, -1, -19, "yellow_concrete")
    lid(b)
    b.truck(-15, 4, 8, "south", "green_terracotta", "green_wool")
    b.put(-13, 0, 3, "barrel[facing=up]")
    b.sandbags(-6, 7, -2, 7)
    b.sandbags(3, 7, 7, 7)
    b.sandbags(-11, -9, -11, -5)
    b.sandbags(11, 5, 11, 9)
    b.container(9, -16, 15, -14, "orange")
    b.container(-15, -16, -13, -10, "blue")
    b.box(-8, 0, -18, -6, 2, -16, "spruce_planks")
    b.box(-8, 1, -18, -6, 1, -16, "glass")
    b.box(-7, 1, -18, -7, 1, -16, "spruce_planks")
    b.box(-9, 3, -19, -5, 3, -15, "spruce_slab[type=bottom]")
    b.put(-7, 2, -16, "lantern")
    for x, z in ((-10, 6), (9, -8), (-6, -10), (10, 9), (-9, -6), (5, 10), (11, -3), (-4, 11)):
        b.put(x, 0, z, b.pick("cracked_deepslate_tiles", "polished_deepslate", "smooth_stone"))
    b.box(1, -1, 8, 1, -1, 18, "waxed_copper_grate")
    b.box(-1, -1, -18, -1, -1, -8, "waxed_copper_grate")
    for x in (6, 8):
        b.column(x, -12, 0, 3, "iron_block")
    b.box(6, 4, -12, 8, 4, -12, "iron_block")
    b.box(6, 3, -12, 8, 3, -12, "air")
    b.put(7, 3, -12, "end_rod[facing=down]")
    b.box(6, -1, -13, 8, -1, -11, "waxed_copper_grate")
    b.barrels(16, 4, 3)
    b.barrels(-17, -6, 4)
    for x, z, tx, tz in ((-17, -17, 1, 1), (17, 17, -1, -1), (17, -17, -1, 1), (-17, 17, 1, -1)):
        b.floodlight(x, z, 7, tx, tz)
    for x, z in ((-12, 0), (12, 0), (0, -12)):
        b.column(x, z, 0, 3, "polished_deepslate")
        b.put(x, 4, z, BULB)
        b.put(x, 5, z, "lightning_rod[facing=up]")
    kiosk(b, -4, "spruce", "dark_oak")
    kiosk(b, 5, "deepslate_tile", "polished_blackstone")
    b.box(-1, -1, 12, 2, -1, 13, "polished_andesite")
    b.put(14, 0, 10, "spruce_planks")
    b.put(15, 0, 10, "spruce_planks")
    b.put(15, 0, 9, "spruce_stairs[facing=west]")


def lid(b):
    """The cracked blast lid over the silo, flush with the yard so it can be crossed."""
    b.disc(0, 0, 7.5, -1, -1, "smooth_stone")
    b.disc(0, 0, 7.5, -1, -1, "polished_deepslate", inner=6.4)
    b.box(-7, -1, 0, 7, -1, 0, "polished_blackstone")
    b.box(0, -1, -7, 0, -1, 7, "polished_blackstone")
    seven = ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."]
    for row, line in enumerate(seven):
        for column, cell in enumerate(line):
            if cell == "#":
                b.put(-3 + column, -1, -4 + row + (1 if row >= 4 else 0), "yellow_concrete")
    crack = [(-5, -2), (-4, -2), (-4, -1), (-3, -1), (-3, 0), (-2, 0), (-2, 1), (-1, 1), (0, 2), (1, 2), (1, 3), (2, 3), (3, 4), (4, 4)]
    for x, z in crack:
        b.put(x, -1, z, "black_stained_glass")
        b.put(x, -2, z, "air")
    for x, z in ((-5, -3), (-2, -1), (0, 1), (2, 4), (3, 3), (-4, 0)):
        b.put(x, -1, z, "cracked_deepslate_tiles")
    for x, z in ((-9, 0), (8, 0), (0, -9), (0, 8)):
        b.box(x, 0, z, x + (1 if x else 0), 1, z + (0 if x else 1), "polished_deepslate")
        b.box(x, 2, z, x + (1 if x else 0), 2, z + (0 if x else 1), "polished_blackstone_slab[type=bottom]")
    for angle in range(0, 360, 45):
        x, z = round(7 * math.cos(math.radians(angle + 22))), round(7 * math.sin(math.radians(angle + 22)))
        b.put(x, -1, z, "yellow_concrete")


def kiosk(b, x, wood, trim):
    b.box(x - 3, 0, 19, x + 3, 2, 19, f"{wood}s" if wood == "deepslate_tile" else f"{wood}_planks")
    b.box(x - 3, 3, 15, x + 3, 3, 19, f"{trim}_slab[type=bottom]")
    for px in (x - 3, x + 3):
        b.column(px, 16, 0, 2, "stripped_dark_oak_log[axis=y]" if trim == "dark_oak" else "polished_blackstone")
    b.put(x - 2, 0, 18, "barrel[facing=up]")
    b.put(x + 2, 0, 18, "barrel[facing=up]")
    b.put(x - 2, 1, 18, "iron_trapdoor[facing=north,half=bottom,open=false]")
    b.put(x + 2, 1, 18, "iron_trapdoor[facing=north,half=bottom,open=false]")
    b.put(x, 2, 18, "lantern[hanging=true]")
    b.put(x, 2, 19, "iron_block")


def hof_facades(b):
    """The four buildings around the yard show their own face to it."""
    b.box(-20, 0, -20, 20, 9, -20, "light_gray_concrete")
    b.box(-20, 0, -20, 20, 1, -20, "polished_andesite")
    b.box(-20, 10, -20, 20, 10, -20, "gray_concrete")
    for x in range(-20, 21, 5):
        b.column(x, -20, 0, 10, "polished_deepslate")
    b.box(20, 0, -20, 20, 0, 20, "white_concrete")
    b.box(20, 1, -19, 20, 6, 19, "glass")
    for z in range(-20, 21, 5):
        b.column(20, z, 0, 7, "white_concrete")
    b.box(20, 7, -20, 20, 7, 20, "white_concrete")
    b.box(-20, 0, 20, 20, 9, 20, "deepslate_tiles")
    b.box(-20, 4, 20, 20, 4, 20, "polished_blackstone")
    for x in range(-18, 19, 6):
        b.box(x, 6, 20, x + 1, 7, 20, "iron_bars")
    b.box(-20, 0, -20, -20, 6, 20, "white_concrete")
    b.box(-20, 3, -20, -20, 3, 20, "light_blue_terracotta")
    b.box(-20, 7, -20, -20, 7, 20, "smooth_quartz")


def workshop(b):
    x1, z1, x2, z2 = -19, -51, 19, -21
    b.floor(x1, z1, x2, z2, [("smooth_stone", 12), ("polished_andesite", 4), ("andesite", 2), ("gray_concrete", 1)])
    for x in (-8, 8):
        b.box(x, -1, z1, x, -1, z2, "yellow_concrete")
    for _ in range(14):
        x, z = b.rng.randint(x1 + 2, x2 - 2), b.rng.randint(z1 + 2, z2 - 2)
        b.box(x, -1, z, x + 1, -1, z + b.rng.randint(0, 1), b.pick("black_concrete", "gray_concrete", "coal_block"))
    b.box(-20, 0, -52, 20, 9, -52, "light_gray_concrete")
    b.box(-20, 0, -52, -20, 9, -21, "light_gray_concrete")
    b.box(20, 0, -52, 20, 9, -21, "light_gray_concrete")
    b.box(-20, 0, -52, 20, 1, -52, "polished_andesite")
    b.box(-20, 0, -52, -20, 1, -21, "polished_andesite")
    b.box(20, 0, -52, 20, 1, -21, "polished_andesite")
    for z in range(-52, -20, 6):
        for x in (-20, 20):
            b.column(x, z, 0, 10, "polished_deepslate")
    for x in range(-20, 21, 5):
        b.column(x, -52, 0, 10, "polished_deepslate")
    b.box(-20, 10, -52, 20, 10, -20, "gray_concrete")
    for z in range(-49, -22, 6):
        b.box(-17, 10, z, 17, 10, z + 1, "glass")
        b.box(-18, 11, z - 1, 18, 11, z - 1, "gray_concrete")
        b.box(-18, 11, z, 18, 11, z + 1, "glass")
    for x in range(-20, 21, 8):
        b.box(x, 3, -52, x + 3, 4, -52, "glass")
    for x in (-10, 10):
        b.box(x, 8, z1, x, 8, z2, "polished_blackstone")
    b.box(-10, 8, -40, 10, 8, -40, "iron_block")
    b.box(-1, 8, -40, 1, 8, -40, "yellow_concrete")
    b.hanging_lamp(0, 7, -40, 4, "anvil[facing=east]")
    for x in (-14, -5, 5, 14):
        for z in (-46, -36, -26):
            b.hanging_lamp(x, 9, z, 7, BULB if (x + z) % 2 == 0 else "lantern[hanging=true]")
    # The car lift with a truck on it, east bay.
    b.stripes(7, -47, 13, -47)
    b.stripes(7, -38, 13, -38)
    b.box(8, -1, -46, 12, -1, -39, "iron_block")
    b.truck(10, -46, 7, "south", "orange_terracotta", "brown_wool")
    for x in (7, 13):
        b.column(x, -46, 0, 3, "yellow_concrete")
        b.column(x, -39, 0, 3, "yellow_concrete")
    # Workbenches along the west wall.
    for z in (-50, -46, -42, -32):
        b.box(-19, 0, z, -19, 0, z + 2, "spruce_planks")
        b.box(-19, 1, z, -19, 1, z + 2, "spruce_slab[type=bottom]")
        b.put(-19, 1, z + 1, b.pick("grindstone[face=floor,facing=east]", "anvil[facing=north]", "stonecutter[facing=east]"))
        b.box(-19, 2, z, -19, 3, z + 2, "dark_oak_planks")
        b.put(-19, 3, z + 1, "lantern")
    b.put(-18, 0, -34, "crafting_table")
    # The parts cage in the north-west corner.
    b.box(-19, 0, -51, -14, 2, -46, "iron_bars")
    b.box(-18, 0, -50, -15, 2, -47, "air")
    b.crates(-18, -50, -15, -49, 2)
    b.box(-19, 3, -51, -14, 3, -46, "smooth_stone_slab[type=bottom]")
    # Tyres and drums by the east wall.
    for z in (-30, -28):
        b.put(18, 0, z, "black_concrete")
        b.put(18, 1, z, "polished_blackstone_slab[type=bottom]")
    b.barrels(17, -34, 3)
    b.crates(-6, -30, -5, -29)
    for z in (-48, -44):
        b.box(-12, 0, z, -8, 0, z + 1, "dark_oak_planks")
        b.box(-12, 1, z, -8, 2, z + 1, "air")
        b.box(-12, 1, z, -12, 2, z + 1, "polished_blackstone")
        b.box(-8, 1, z, -8, 2, z + 1, "polished_blackstone")
        b.box(-12, 3, z, -8, 3, z + 1, "dark_oak_slab[type=bottom]")
        for x in range(-11, -8):
            b.put(x, 1, z, b.pick("barrel[facing=north]", "chiseled_bookshelf[facing=north]", "air"))
            b.put(x, 2, z + 1, b.pick("barrel[facing=south]", "air", "iron_block"))
    b.box(-11, 0, -27, -7, 0, -25, "polished_blackstone")
    b.box(-11, 1, -27, -7, 2, -25, "green_terracotta")
    b.box(-9, 3, -27, -8, 3, -25, "gray_stained_glass")
    for x in (-11, -7):
        for z in (-28, -24):
            b.put(x, 0, z, "stone_brick_wall[up=true]")
    b.put(-5, 0, -26, "anvil[facing=east]")
    b.put(-5, 1, -26, "heavy_core")
    lift_top(b)


def lift_top(b):
    """The freight lift down to the silo: a grate over its shaft, posts and a hoist. The quest repairs it."""
    x1, _, z1, x2, _, z2 = LIFT_SHAFT
    b.stripes(x1 - 1, z1 - 1, x2 + 1, z1 - 1)
    b.stripes(x1 - 1, z2 + 1, x2 + 1, z2 + 1)
    b.stripes(x1 - 1, z1, x1 - 1, z2)
    b.stripes(x2 + 1, z1, x2 + 1, z2)
    b.box(x1, -1, z1, x2, -1, z2, "waxed_copper_grate")
    for x in (x1 - 1, x2 + 1):
        for z in (z1 - 1, z2 + 1):
            b.column(x, z, 0, 7, "iron_block")
    b.box(x1 - 1, 8, z1 - 1, x2 + 1, 8, z1 - 1, "polished_blackstone")
    b.box(x1 - 1, 8, z2 + 1, x2 + 1, 8, z2 + 1, "polished_blackstone")
    b.box(0, 8, z1, 0, 8, z2, "polished_blackstone")
    b.put(0, 9, (z1 + z2) // 2, "grindstone[face=floor,facing=north]")
    b.box(0, 3, (z1 + z2) // 2, 0, 7, (z1 + z2) // 2, "iron_chain[axis=y]")
    b.put(0, 2, (z1 + z2) // 2, "heavy_core")
    b.put(x2 + 2, 0, z2 + 1, "polished_blackstone")
    b.put(x2 + 2, 1, z2 + 1, "lever[face=floor,facing=east,powered=false]")


def radio_tower(b):
    b.floor(21, -51, 51, -21, [("gravel", 3), ("coarse_dirt", 4), ("andesite", 2), ("stone", 2), ("cobblestone", 1)])
    b.box(37, -1, -29, 39, -1, -21, "polished_andesite")
    b.box(21, -1, -38, 29, -1, -36, "polished_andesite")
    # The station block, its roof the deck.
    x1, z1, x2, z2 = 30, -46, 46, -30
    wall_box(b, x1, z1, x2, z2, 0, 5, "white_concrete")
    b.box(x1, 0, z1, x2, 0, z2, "light_gray_concrete")
    b.box(x1, 3, z1, x2, 3, z1, "cyan_terracotta")
    b.box(x1, 3, z2, x2, 3, z2, "cyan_terracotta")
    b.box(x1, 3, z1, x1, 3, z2, "cyan_terracotta")
    b.box(x2, 3, z1, x2, 3, z2, "cyan_terracotta")
    b.box(x1 + 1, -1, z1 + 1, x2 - 1, -1, z2 - 1, "polished_andesite")
    b.box(x1, 6, z1, x2, 6, z2, "smooth_stone")
    b.box(x1 + 1, 6, z1 + 1, x2 - 1, 6, z2 - 1, "polished_andesite")
    for x, z in ((37, z2), (37, z1)):
        b.box(x, 0, z, x + 2, 2, z, "air")
    b.box(x2, 0, -39, x2, 2, -37, "air")
    for x in range(x1 + 3, x2 - 1, 4):
        b.box(x, 1, z2, x + 1, 2, z2, "glass_pane")
        b.box(x, 1, z1, x + 1, 2, z1, "glass_pane")
    for x in range(x1 + 2, x2 - 1, 3):
        if 35 <= x <= 40:
            continue
        b.put(x, 0, z1 + 1, "polished_blackstone")
        b.put(x, 1, z1 + 1, "observer[facing=south]" if x % 2 else "target")
        b.put(x + 1, 0, z1 + 1, "polished_blackstone_slab[type=bottom]")
    b.box(36, 0, -40, 40, 0, -36, "spruce_planks")
    b.box(37, 1, -39, 39, 1, -37, "spruce_slab[type=bottom]")
    b.put(38, 1, -38, "lantern")
    for z in (-34, -42):
        b.box(x2 - 1, 0, z - 1, x2 - 1, 2, z + 1, "iron_block")
        b.put(x2 - 1, 1, z, "cyan_stained_glass")
    b.box(x1 + 1, 0, -35, x1 + 1, 0, -32, "red_bed[part=foot,facing=south]")
    b.put(x1 + 1, 0, -32, "air")
    b.put(x1 + 1, 0, -34, "red_bed[part=head,facing=north]")
    b.put(x1 + 1, 0, -35, "red_bed[part=foot,facing=north]")
    b.put(x1 + 1, 0, -33, "air")
    for x in (34, 42):
        for z in (-42, -34):
            b.hanging_lamp(x, 5, z, 4, BULB if x == 34 else "lantern[hanging=true]")
    # The one stairway to the deck, up the west side.
    for step in range(6):
        z = -31 - step
        b.box(27, -1, z, 29, step - 1, z, "stone_bricks")
        b.box(27, step, z, 29, step, z, "stone_brick_stairs[facing=north,half=bottom]")
    b.box(26, -1, -41, 29, 5, -37, "stone_bricks")
    b.box(27, 6, -41, 29, 6, -37, "polished_andesite")
    b.box(26, 0, -41, 26, 7, -30, "polished_andesite")
    b.box(26, 6, -41, 29, 6, -41, "polished_andesite")
    b.box(27, 7, -41, 29, 7, -41, "iron_bars")
    b.box(26, 8, -41, 26, 8, -30, "polished_andesite_slab[type=bottom]")
    b.put(26, 8, -37, "lantern")
    b.put(26, 8, -31, "lantern")
    # The deck: railing, mast, dish, cover.
    for x in range(x1, x2 + 1):
        b.put(x, 7, z1, "iron_bars")
        b.put(x, 7, z2, "iron_bars")
    for z in range(z1, z2 + 1):
        b.put(x2, 7, z, "iron_bars")
        if not -41 <= z <= -30:
            b.put(x1, 7, z, "iron_bars")
    for x, z in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        b.put(x, 7, z, "polished_deepslate")
        b.put(x, 8, z, "lantern")
    b.box(37, 6, -39, 39, 6, -37, "iron_block")
    for y in range(7, 31):
        for x, z in ((37, -39), (39, -39), (37, -37), (39, -37)):
            b.put(x, y, z, "iron_bars" if y % 4 else "polished_deepslate")
        if y % 4 == 0:
            b.box(38, y, -39, 38, y, -37, "iron_bars")
            b.box(37, y, -38, 39, y, -38, "iron_bars")
    b.box(37, 7, -38, 39, 7, -38, "iron_block")
    b.box(38, 7, -39, 38, 7, -37, "iron_block")
    b.put(38, 8, -38, BULB)
    b.box(38, 31, -38, 38, 33, -38, "iron_bars")
    b.put(38, 34, -38, "redstone_lamp[lit=true]")
    b.put(38, 35, -38, "lightning_rod[facing=up]")
    for y in (18, 26):
        for x, z in ((36, -38), (40, -38), (38, -40), (38, -36)):
            b.put(x, y, z, "redstone_lamp[lit=true]")
    b.column(42, -44, 7, 8, "polished_deepslate")
    b.disc(42, -44.5, 2.6, 9, 13, "air")
    for y in range(9, 14):
        for x in range(40, 45):
            if math.hypot(x - 42, y - 11) <= 2.5:
                b.put(x, y, -45, "white_concrete")
    b.put(42, 11, -44, "iron_block")
    b.put(42, 11, -43, "end_rod[facing=south]")
    b.sandbags(42, -34, 45, -34)
    b.sandbags(41, -33, 41, -31)
    b.crates(32, -44, 33, -43)
    b.barrels(32, -33, 2)
    # Grounds: the fuel tank, cable drums, a fallen dish panel and floodlights.
    b.box(47, 0, -26, 50, 0, -23, "polished_blackstone")
    b.box(47, 1, -26, 50, 3, -23, "white_terracotta")
    b.box(47, 2, -26, 50, 2, -23, "red_terracotta")
    for x, z in ((23, -48), (25, -48), (23, -46)):
        b.put(x, 0, z, "stripped_spruce_wood")
        b.put(x, 1, z, "spruce_trapdoor[half=bottom,facing=north]")
    b.box(23, 0, -27, 25, 0, -25, "white_concrete")
    b.box(23, 1, -27, 24, 1, -26, "smooth_quartz_slab[type=bottom]")
    b.container(22, -51, 28, -49, "green")
    b.box(47, 0, -51, 51, 1, -50, "gray_concrete")
    b.box(47, 0, -50, 51, 1, -50, "yellow_concrete")
    b.box(48, 2, -51, 50, 2, -51, "waxed_copper_grate")
    b.box(50, 2, -51, 50, 4, -51, "polished_blackstone")
    b.put(49, 2, -50, "lantern")
    b.box(31, -1, -29, 45, -1, -29, "polished_blackstone")
    for x in range(31, 46, 3):
        b.put(x, -1, -29, "iron_trapdoor[facing=north,half=top,open=false]")
    b.floodlight(22, -22, 6, 1, -1)
    b.floodlight(50, -29, 6, -1, 0)


def greenhouse(b):
    b.floor(21, -19, 51, 19, [("mud_bricks", 6), ("packed_mud", 3), ("moss_block", 1), ("cobblestone", 1)])
    b.box(21, -1, -1, 51, -1, 1, "polished_tuff")
    b.box(34, -1, -19, 36, -1, 19, "polished_tuff")
    # Glass shell: frame every five blocks, a stepped glass roof with its ridge over z 0.
    b.box(21, 0, -20, 52, 0, -20, "white_concrete")
    b.box(21, 1, -20, 52, 6, -20, "glass")
    b.box(52, 0, -20, 52, 0, 20, "white_concrete")
    b.box(52, 1, -20, 52, 6, 20, "glass")
    b.box(21, 0, 20, 52, 6, 20, "glass")
    b.box(21, 0, 20, 52, 0, 20, "white_concrete")
    for x in range(20, 53, 5):
        b.column(x, -20, 0, 7, "white_concrete")
        b.column(x, 20, 0, 7, "white_concrete")
    for z in range(-20, 21, 5):
        b.column(52, z, 0, 7, "white_concrete")
    for x, z1 in ((27, 20), (44, 20)):
        b.box(x, 0, z1, x + 4, 3, z1, "air")
        b.box(x, 4, z1, x + 4, 4, z1, "white_concrete")
    for z in range(-20, 21):
        height = 7 + (20 - abs(z)) // 3
        b.box(20, height, z, 52, height, z, "glass")
        if abs(z) % 3 == 2 or z == 0:
            b.box(20, height, z, 52, height, z, "white_concrete" if z == 0 else "glass")
    for x in range(20, 53, 5):
        for z in range(-20, 21):
            b.put(x, 7 + (20 - abs(z)) // 3, z, "white_concrete")
    for z in range(-20, 21):
        top = 7 + (20 - abs(z)) // 3
        b.box(20, 7, z, 20, top - 1, z, "glass")
        b.box(52, 7, z, 52, top - 1, z, "glass")
    # Planting beds with plants that should not grow here.
    for bz1, bz2 in ((-17, -15), (-11, -8), (8, 11), (15, 17)):
        for bx1, bx2 in ((23, 32), (38, 49)):
            b.box(bx1, 0, bz1, bx2, 0, bz2, "spruce_planks")
            b.box(bx1 + 1, 0, bz1 + (1 if bz2 - bz1 > 2 else 0), bx2 - 1, 0, bz2 - (1 if bz2 - bz1 > 2 else 0),
                  b.pick("moss_block", "rooted_dirt", "moss_block"))
            for x in range(bx1 + 1, bx2):
                for z in range(bz1, bz2 + 1):
                    if bz2 - bz1 > 2 and z in (bz1, bz2):
                        continue
                    roll = b.rng.random()
                    if roll < 0.2:
                        b.put(x, 1, z, "large_fern[half=lower]")
                        b.put(x, 2, z, "large_fern[half=upper]")
                    elif roll < 0.32:
                        b.put(x, 1, z, "azalea")
                    elif roll < 0.4:
                        b.put(x, 1, z, "flowering_azalea")
                    elif roll < 0.5:
                        b.put(x, 1, z, "torchflower")
                    elif roll < 0.58:
                        b.put(x, 0, z, "sculk")
                        b.put(x, 1, z, "sculk_sensor[sculk_sensor_phase=inactive]" if b.chance(0.1) else "air")
                    elif roll < 0.7:
                        b.put(x, 1, z, "fern")
                    elif roll < 0.75:
                        b.put(x, 1, z, "pitcher_plant[half=lower]")
                        b.put(x, 2, z, "pitcher_plant[half=upper]")
    for x in range(24, 50, 4):
        for z in (-13, -5, 5, 13):
            top = 7 + (20 - abs(z)) // 3
            b.put(x, top - 1, z, "spore_blossom" if b.chance(0.5) else "air")
        b.box(x, 11, -3, x, 13, -3, "cave_vines[berries=true,age=25]")
        b.put(x, 14, -3, "cave_vines_plant[berries=false]")
        b.box(x, 11, 3, x, 13, 3, "cave_vines[berries=false,age=25]")
        b.put(x, 14, 3, "cave_vines_plant[berries=true]")
    for x in range(24, 50, 4):
        for z in (-3, 3):
            top = 7 + (20 - abs(z)) // 3
            b.box(x, 11, z, x, top - 1, z, "cave_vines_plant[berries=true]")
            b.put(x, 10, z, "cave_vines[berries=true,age=25]")
    # Research bench, irrigation and lights.
    b.box(41, 0, 2, 47, 0, 2, "smooth_quartz")
    b.box(41, 1, 2, 47, 1, 2, "smooth_quartz_slab[type=bottom]")
    b.put(42, 1, 2, "flower_pot")
    b.put(46, 1, 2, "decorated_pot")
    for z in (-12, 12):
        b.box(22, 2, z, 51, 2, z, "waxed_lightning_rod[facing=east]")
    for x in (25, 33, 41, 49):
        for z in (-6, 6):
            b.column(x, z, 0, 2, "white_concrete")
            b.put(x, 3, z, BULB)
            b.put(x, 4, z, "lantern")
    b.crates(48, -19, 51, -18)
    test_field(b)


def test_field(b):
    b.floor(21, 21, 51, 51, [("grass_block", 6), ("coarse_dirt", 3), ("podzol", 1), ("rooted_dirt", 1)])
    b.box(21, -1, 36, 51, -1, 38, "dirt_path")
    b.box(34, -1, 21, 36, -1, 51, "dirt_path")
    for x1, z1 in ((23, 23), (40, 23), (40, 41)):
        b.box(x1, 0, z1, x1 + 8, 0, z1 + 9, "spruce_slab[type=top]")
        b.box(x1 + 1, 0, z1 + 1, x1 + 7, 0, z1 + 8, "moss_block")
        for x in range(x1 + 1, x1 + 8):
            for z in range(z1 + 1, z1 + 9):
                if (x - x1) % 2 == 1:
                    b.put(x, 1, z, b.pick("short_grass", "fern", "bush", "pumpkin"))
                else:
                    b.put(x, 1, z, "hay_block[axis=x]" if b.chance(0.12) else "air")
    for x1, z1, x2, z2 in ((23, 34, 31, 34), (40, 34, 49, 34), (33, 40, 33, 48)):
        b.box(x1, 0, z1, x2, 1, z2, "azalea_leaves[persistent=true]")
    for x, z in ((27, 28), (44, 28), (44, 46)):
        b.column(x, z, 0, 3, "stripped_spruce_log[axis=y]")
        b.box(x - 1, 3, z, x + 1, 3, z, "stripped_spruce_log[axis=x]")
        b.put(x, 4, z, "carved_pumpkin[facing=south]")
        b.put(x, 5, z, "lightning_rod[facing=up]")
    # The quarantine tent with the team revive.
    b.box(24, 0, 41, 32, 0, 47, "white_wool")
    b.box(25, 0, 42, 31, 0, 46, "air")
    b.box(27, 0, 41, 29, 1, 41, "air")
    b.box(24, 1, 41, 24, 2, 47, "white_wool")
    b.box(32, 1, 41, 32, 2, 47, "white_wool")
    b.box(24, 1, 47, 32, 2, 47, "white_wool")
    b.box(24, 1, 41, 26, 2, 41, "white_wool")
    b.box(30, 1, 41, 32, 2, 41, "white_wool")
    b.box(24, 3, 41, 32, 3, 47, "white_wool")
    b.box(25, 4, 42, 31, 4, 46, "white_wool")
    b.box(26, 5, 43, 30, 5, 45, "red_wool")
    b.box(25, -1, 42, 31, -1, 46, "white_concrete")
    b.put(25, 0, 46, "white_bed[part=foot,facing=north]")
    b.put(25, 0, 45, "white_bed[part=head,facing=north]")
    b.put(31, 0, 46, "white_bed[part=foot,facing=north]")
    b.put(31, 0, 45, "white_bed[part=head,facing=north]")
    b.hanging_lamp(28, 2, 44, 2, "lantern[hanging=true]")
    b.put(28, 3, 42, BULB)
    # Tractor and compost.
    b.box(42, 0, 36, 45, 2, 38, "green_concrete")
    b.box(46, 0, 36, 46, 1, 38, "green_concrete")
    b.box(43, 3, 36, 44, 3, 38, "gray_stained_glass")
    b.box(43, 4, 36, 44, 4, 38, "green_concrete")
    b.put(47, 1, 37, "iron_trapdoor[facing=east,half=bottom,open=true]")
    for z in (35, 39):
        b.box(42, 0, z, 43, 1, z, "polished_blackstone")
        b.box(45, 0, z, 45, 1, z, "polished_blackstone")
    b.box(22, 0, 49, 25, 0, 51, "composter[level=7]")
    b.floodlight(50, 50, 6, -1, -1)
    b.floodlight(22, 22, 6, 1, 1)
    b.floodlight(50, 22, 6, -1, 1)


def power(b):
    b.floor(-19, 21, 19, 51, [("deepslate_tiles", 8), ("polished_deepslate", 4), ("cracked_deepslate_tiles", 1),
                               ("polished_basalt[axis=y]", 1)])
    b.box(-19, -1, 36, 19, -1, 38, "polished_blackstone")
    wall_box(b, -20, 20, 20, 52, 0, 9, "deepslate_tiles")
    b.box(-20, 4, 20, 20, 4, 52, "polished_blackstone")
    b.box(-19, 4, 21, 19, 4, 51, "air")
    for x in range(-20, 21, 5):
        b.column(x, 52, 0, 10, "polished_deepslate")
    for z in range(20, 53, 6):
        b.column(20, z, 0, 10, "polished_deepslate")
        b.column(-20, z, 0, 10, "polished_deepslate")
    b.box(-20, 10, 20, 20, 10, 52, "polished_deepslate")
    for x, z in ((-12, 30), (8, 44), (12, 28)):
        b.box(x, 10, z, x + 1, 11, z + 1, "iron_bars")
    b.box(-20, 11, 20, 20, 11, 20, "polished_deepslate_slab[type=bottom]")
    b.box(-20, 11, 52, 20, 11, 52, "polished_deepslate_slab[type=bottom]")
    b.box(-20, 11, 20, -20, 11, 52, "polished_deepslate_slab[type=bottom]")
    b.box(20, 11, 20, 20, 11, 52, "polished_deepslate_slab[type=bottom]")
    for x, z in ((-14, 24), (-2, 24), (10, 24), (-14, 33), (10, 33)):
        b.box(x, 11, z, x + 3, 12, z + 2, "gray_concrete")
        b.box(x + 1, 12, z + 1, x + 2, 12, z + 1, "waxed_copper_grate")
    b.box(-18, 11, 38, 18, 11, 38, "waxed_cut_copper_slab[type=bottom]")
    for gx in (-16, 4):
        for x in (gx + 3, gx + 6):
            b.box(x, 10, 45, x, 14, 45, "polished_blackstone")
            b.put(x, 15, 45, "polished_blackstone_slab[type=bottom]")
    for z1 in (26, 42):
        b.box(-20, 0, z1, -20, 3, z1 + 3, "air")
        b.box(-20, 4, z1 - 1, -20, 4, z1 + 4, "yellow_concrete")
    # Two generator sets and the switchboard on the south wall with the power station.
    for gx in (-16, 4):
        b.box(gx, 0, 43, gx + 9, 0, 47, "polished_blackstone")
        b.box(gx + 1, 1, 44, gx + 7, 2, 46, "iron_block")
        b.box(gx + 1, 3, 44, gx + 7, 3, 46, "polished_blackstone_slab[type=bottom]")
        b.box(gx + 8, 1, 44, gx + 8, 3, 46, "gray_concrete")
        b.box(gx + 1, 1, 43, gx + 7, 2, 43, "yellow_concrete")
        b.box(gx + 1, 1, 47, gx + 7, 2, 47, "black_concrete")
        b.put(gx + 4, 2, 43, "red_concrete")
        b.box(gx + 3, 3, 45, gx + 3, 9, 45, "polished_blackstone_wall[up=true]")
        b.box(gx + 6, 3, 45, gx + 6, 9, 45, "polished_blackstone_wall[up=true]")
        b.box(gx + 1, 8, 44, gx + 7, 8, 46, "air")
        b.box(gx, 1, 42, gx + 9, 1, 42, "air")
        b.put(gx + 9, 1, 44, "lever[face=wall,facing=east,powered=false]")
    b.box(-6, 0, 51, 6, 3, 51, "iron_block")
    for x in range(-6, 7):
        b.put(x, 1, 50, "lever[face=wall,facing=north,powered=true]" if x % 3 == 0 else "air")
        b.put(x, 3, 50, "redstone_lamp[lit=true]" if x % 4 == 0 else "air")
    b.box(-6, 4, 51, 6, 4, 51, "polished_blackstone")
    b.box(-1, -1, 47, 1, -1, 50, "polished_blackstone")
    b.stripes(-2, 46, 2, 46)
    for x in (-18, -9, 0, 9, 18):
        b.box(x, 7, 21, x, 7, 51, "iron_bars")
    b.box(-19, 7, 30, 19, 7, 30, "waxed_cut_copper_slab[type=top]")
    b.box(-19, 7, 40, 19, 7, 40, "waxed_cut_copper_slab[type=top]")
    for x in (-13, -4, 5, 14):
        for z in (25, 34, 40):
            b.hanging_lamp(x, 9, z, 6, BULB)
    for x, z in ((-19, 33), (19, 33), (-19, 48), (19, 48)):
        b.put(x, 3, z, "shroomlight")
    b.barrels(14, 22, 4)
    b.crates(-18, 49, -16, 51)
    schleuse(b)
    tank_yard(b)


def schleuse(b):
    """The blockhouse over the silo stairs. Its gate is the silo's door."""
    b.box(-7, 0, 29, -3, 4, 35, "polished_deepslate")
    b.box(-6, 0, 30, -4, 1, 31, "polished_blackstone")
    b.box(-6, 0, 32, -4, 3, 34, "air")
    b.box(-6, 0, 35, -4, 2, 35, DOOR)
    b.box(-8, 0, 35, -8, 4, 35, "yellow_concrete")
    b.box(-2, 0, 35, -2, 4, 35, "yellow_concrete")
    b.box(-8, 5, 35, -2, 5, 35, "black_concrete")
    for x in (-8, -6, -4, -2):
        b.put(x, 5, 35, "yellow_concrete")
    b.put(-5, 4, 36, "redstone_lamp[lit=true]")
    b.box(-7, 5, 29, -3, 5, 34, "polished_deepslate_slab[type=bottom]")
    b.stripes(-7, 36, -3, 36)
    b.box(-6, -2, 34, -4, -2, 34, "polished_blackstone")
    b.box(-6, -1, 32, -4, -1, 34, "air")
    for k in range(1, 12):
        z = 34 - k
        b.box(-6, -2 - k, z, -4, -2 - k, z, "polished_deepslate_stairs[facing=south,half=bottom]")
        b.box(-6, -1 - k, z, -4, min(-1, 2 - k), z, "air")
        b.box(-7, -2 - k, z, -7, min(-1, 2 - k), z, "light_gray_concrete")
        b.box(-3, -2 - k, z, -3, min(-1, 2 - k), z, "light_gray_concrete")
        if k % 4 == 2:
            b.put(-7, -k, z, "sea_lantern")
    b.box(-6, -13, 15, -4, -13, 22, "polished_deepslate")
    b.box(-6, -12, 14, -4, -9, 22, "air")
    b.box(-7, -12, 15, -7, -9, 22, "light_gray_concrete")
    b.box(-3, -12, 15, -3, -9, 22, "light_gray_concrete")
    b.box(-7, -8, 15, -3, -8, 22, "polished_deepslate")
    for z in (17, 21):
        b.put(-7, -10, z, "sea_lantern")
    b.stripes(-6, 16, -4, 16, y=-13)


def tank_yard(b):
    b.floor(-51, 21, -21, 51, [("gravel", 4), ("coarse_dirt", 4), ("stone", 2), ("andesite", 2)])
    b.box(-51, -1, 36, -21, -1, 38, "stone_bricks")
    b.box(-38, -1, 21, -36, -1, 51, "stone_bricks")
    for cx, cz in ((-45.5, 28.5), (-45.5, 45.5), (-27.5, 46.5)):
        b.tank(cx, cz, 3.6, 7, "white_concrete", "red_concrete")
    for cx, cz in ((-45, 28), (-45, 45), (-28, 46)):
        b.box(cx - 4, -1, cz - 4, cx + 4, -1, cz + 4, "polished_andesite")
    b.box(-41, 4, 28, -21, 4, 28, "waxed_copper_block")
    b.box(-41, 4, 45, -21, 4, 45, "waxed_copper_block")
    for x in (-41, -33, -25):
        for z in (28, 45):
            b.column(x, z, 0, 3, "polished_blackstone")
    b.box(-33, 0, 23, -25, 1, 31, "iron_bars")
    b.box(-32, 0, 24, -26, 1, 30, "air")
    for x, z in ((-31, 25), (-27, 25), (-31, 29), (-27, 29)):
        b.box(x, 0, z, x + 1, 1, z + 1, "iron_block")
        b.put(x, 2, z, "lightning_rod[facing=up]")
        b.put(x + 1, 2, z + 1, "lightning_rod[facing=up]")
    b.stripes(-33, 32, -25, 32)
    b.truck(-46, 33, 6, "east", "black_concrete", "coal_block")
    b.barrels(-25, 39, 4)
    b.crates(-50, 40, -49, 41)
    b.floodlight(-50, 22, 6, 1, 1)
    b.floodlight(-22, 50, 6, -1, -1)
    b.floodlight(-50, 50, 6, 1, -1)


def cold_store(b):
    x1, z1, x2, z2 = -51, -19, -21, 19
    b.floor(x1, z1, x2, z2, [("polished_diorite", 6), ("white_concrete", 3), ("calcite", 2), ("snow_block", 1)])
    for _ in range(40):
        x, z = b.rng.randint(x1, x2), b.rng.randint(z1, z2)
        b.put(x, 0, z, "snow[layers=1]")
    wall_box(b, -52, -20, -20, 20, 0, 6, "white_concrete")
    b.box(-52, 2, -20, -20, 2, 20, "light_blue_terracotta")
    b.box(x1, 0, z1, x1, 5, z2, "packed_ice")
    b.box(x1, 2, z1, x1, 2, z2, "light_blue_terracotta")
    b.box(-52, 7, -20, -20, 7, 20, "smooth_quartz")
    for z in (-14, -2, 10):
        b.box(-44, 8, z, -40, 9, z + 3, "iron_block")
        b.box(-44, 9, z + 1, -40, 9, z + 2, "waxed_copper_grate")
        b.box(-34, 8, z, -30, 9, z + 3, "light_gray_concrete")
        b.put(-32, 10, z + 1, "lightning_rod[facing=up]")
    for xa in (-46, -30):
        b.box(xa, 0, -20, xa + 3, 3, -20, "air")
        b.box(xa - 1, 4, -20, xa + 4, 4, -20, "light_blue_concrete")
    # Racks of frozen stock in rows, aisles three wide.
    for rx in (-48, -42, -36, -30):
        for rz1, rz2 in ((-17, -6), (6, 17)):
            b.box(rx, 0, rz1, rx + 1, 0, rz2, "stripped_spruce_log[axis=z]")
            b.box(rx, 2, rz1, rx + 1, 2, rz2, "spruce_slab[type=bottom]")
            b.box(rx, 1, rz1, rx + 1, 1, rz2, "air")
            for z in range(rz1, rz2 + 1):
                for level in (1, 3):
                    if b.chance(0.7):
                        b.put(rx + b.rng.randint(0, 1), level, z, b.pick("white_wool", "snow_block", "packed_ice", "barrel[facing=up]",
                                                                    "light_gray_wool"))
            for z in (rz1, rz2):
                b.column(rx, z, 0, 4, "iron_block")
                b.column(rx + 1, z, 0, 4, "iron_block")
    for x, z in ((-44, 0), (-38, -2), (-32, 1), (-26, -1), (-40, 3)):
        b.box(x, 3, z, x, 6, z, "iron_chain[axis=y]")
        b.box(x, 1, z, x, 2, z, "white_wool")
    for z in (-15, -5, 5, 15):
        b.box(-22, 3, z - 1, -21, 5, z + 1, "iron_block")
        b.put(-22, 4, z, "waxed_copper_grate")
        b.box(-25, 6, z, -21, 6, z, "waxed_lightning_rod[facing=east]")
    for x, z in ((-46, -1), (-34, -10), (-34, 10), (-24, 3), (-48, 18), (-48, -18)):
        b.put(x, 6, z, BULB)
    for x, z in ((-40, -12), (-26, 12), (-44, 12)):
        b.hanging_lamp(x, 6, z, 5, "soul_lantern[hanging=true]")
    for _ in range(30):
        x, z = b.rng.randint(-51, -21), b.rng.randint(-19, 19)
        b.put(x, 6, z, b.pick("packed_ice", "blue_ice", "ice"))
    loading_dock(b)


def loading_dock(b):
    b.floor(-51, -51, -21, -21, [("stone", 4), ("andesite", 3), ("snow_block", 2), ("gravel", 1), ("cobblestone", 1)])
    b.box(-51, -1, -38, -21, -1, -36, "polished_andesite")
    b.box(-50, 0, -24, -22, 0, -21, "smooth_stone")
    b.box(-50, 0, -25, -22, 0, -25, "yellow_concrete")
    for x in range(-50, -21, 4):
        b.put(x, 0, -25, "black_concrete")
    for x in (-50, -22):
        b.box(x, 0, -24, x, 0, -21, "stone_brick_stairs[facing=" + ("east" if x == -50 else "west") + "]")
    b.box(-44, 1, -22, -44, 3, -21, "air")
    b.truck(-38, -28, 9, "north", "white_concrete", "light_blue_concrete")
    b.box(-39, 4, -31, -37, 4, -29, "white_concrete")
    for x in (-28, -48):
        b.box(x, 0, -32, x + 2, 0, -30, "spruce_slab[type=bottom]")
        b.box(x, 1, -32, x + 2, 2, -31, "barrel[facing=up]")
        b.box(x + 1, 1, -32, x + 1, 2, -31, "spruce_planks")
    b.box(-31, 0, -46, -29, 0, -44, "orange_concrete")
    b.box(-31, 1, -46, -31, 2, -44, "orange_concrete")
    b.box(-29, 1, -46, -29, 3, -46, "iron_bars")
    b.box(-28, 0, -46, -28, 0, -44, "iron_trapdoor[facing=east,half=bottom,open=false]")
    for _ in range(50):
        x, z = b.rng.randint(-51, -21), b.rng.randint(-51, -26)
        b.put(x, 0, z, "snow[layers=1]")
    b.floodlight(-50, -50, 6, 1, 1)
    b.floodlight(-22, -50, 6, -1, 1)
    b.floodlight(-50, -28, 6, 1, 0)


def silo(b):
    """The shaft under the yard: a gallery ring, two stairs to the floor, the lift tunnel and the sealed chamber."""
    top = -3
    b.disc(0, 0, SILO_RADIUS + 2.2, SILO_FLOOR - 1, -2, "light_gray_concrete")
    b.disc(0, 0, SILO_RADIUS, SILO_FLOOR, top, "air")
    for angle in range(0, 360, 30):
        x = math.floor((SILO_RADIUS + 0.7) * math.cos(math.radians(angle)))
        z = math.floor((SILO_RADIUS + 0.7) * math.sin(math.radians(angle)))
        b.box(x, SILO_FLOOR, z, x, top, z, "polished_deepslate")
    b.disc(0, 0, SILO_RADIUS + 1.5, -15, -15, "gray_concrete", inner=SILO_RADIUS)
    b.disc(0, 0, SILO_RADIUS + 1.5, -6, -6, "gray_concrete", inner=SILO_RADIUS)
    for y in (SILO_FLOOR, SILO_FLOOR + 1):
        b.disc(0, 0, SILO_RADIUS + 1.5, y, y, "yellow_concrete" if y == SILO_FLOOR else "black_concrete", inner=SILO_RADIUS)
    b.disc(0, 0, SILO_RADIUS, SILO_FLOOR - 1, SILO_FLOOR - 1, "polished_deepslate")
    b.disc(0, 0, 13, SILO_FLOOR - 1, SILO_FLOOR - 1, "deepslate_tiles", inner=12)
    for angle in range(0, 360, 20):
        x, z = math.floor(12.5 * math.cos(math.radians(angle))), math.floor(12.5 * math.sin(math.radians(angle)))
        b.put(x, SILO_FLOOR - 1, z, "yellow_concrete")
    seven = ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."]
    for row, line in enumerate(seven):
        for column, cell in enumerate(line):
            if cell == "#":
                b.box(-5 + column * 2, SILO_FLOOR - 1, -7 + row * 2, -4 + column * 2, SILO_FLOOR - 1, -6 + row * 2, "white_concrete")
    for x, z in ((-9, -9), (8, -9), (-9, 8), (8, 8)):
        b.put(x, SILO_FLOOR - 1, z, "waxed_copper_grate")
    b.disc(0, 0, 7.5, -2, -2, "iron_block")
    b.disc(0, 0, 7.5, -2, -2, "polished_blackstone", inner=6.5)
    for angle in range(0, 360, 30):
        x, z = math.floor(10.5 * math.cos(math.radians(angle))), math.floor(10.5 * math.sin(math.radians(angle)))
        b.put(x, -2, z, "sea_lantern")
    for angle in range(15, 360, 60):
        x, z = math.floor(9 * math.cos(math.radians(angle))), math.floor(9 * math.sin(math.radians(angle)))
        b.hanging_lamp(x, -3, z, -9, "lantern[hanging=true]")
    # Gallery ring.
    b.disc(0, 0, SILO_RADIUS, GALLERY - 1, GALLERY - 1, "waxed_exposed_copper_grate", inner=RING_INNER)
    for x, z in railing():
        b.put(x, GALLERY - 1, z, "polished_blackstone")
        b.put(x, GALLERY, z, "iron_bars")
    for angle in range(0, 360, 30):
        x, z = math.floor((RING_INNER + 1.5) * math.cos(math.radians(angle + 15))), math.floor((RING_INNER + 1.5) * math.sin(math.radians(angle + 15)))
        b.put(x, GALLERY - 2, z, "polished_blackstone")
    for angle in range(0, 360, 45):
        x = math.floor((SILO_RADIUS + 0.6) * math.cos(math.radians(angle + 10)))
        z = math.floor((SILO_RADIUS + 0.6) * math.sin(math.radians(angle + 10)))
        b.put(x, GALLERY + 1, z, BULB)
        b.put(x, SILO_FLOOR + 2, z, "sea_lantern" if angle % 90 else BULB)
    # The two stairs from the gallery to the floor, each three wide against the wall, away from the boss gate.
    for axis, (low, high), start, step in STAIRS:
        def cells(along, y1, y2, block):
            if axis == "x":
                b.box(low, y1, along, high, y2, along, block)
            else:
                b.box(along, y1, low, along, y2, high, block)
        facing = {("x", 1): "north", ("x", -1): "south", ("z", 1): "west", ("z", -1): "east"}[axis, step]
        for k in range(1, GALLERY - SILO_FLOOR + 1):
            along = start + step * k
            y = GALLERY - 1 - k
            cells(along, SILO_FLOOR - 1, y - 1, "polished_deepslate")
            cells(along, y, y, f"polished_deepslate_stairs[facing={facing},half=bottom]")
            cells(along, y + 1, GALLERY + 1, "air")
        for k in range(-1, 5):
            cells(start + step * k, GALLERY, GALLERY, "air")
        cells(start, GALLERY - 1, GALLERY - 1, "polished_blackstone")
    # Lift shaft and the tunnel from its foot to the floor.
    x1, y1, z1, x2, y2, z2 = LIFT_SHAFT
    b.box(x1 - 1, SILO_FLOOR - 1, z1 - 1, x2 + 1, -2, z2 + 1, "light_gray_concrete")
    b.box(x1, SILO_FLOOR, z1, x2, -2, z2, "air")
    for y in range(SILO_FLOOR + 3, -2, 4):
        b.box(x1 - 1, y, z1 - 1, x2 + 1, y, z1 - 1, "yellow_concrete")
    b.box(x1, SILO_FLOOR - 1, z1, x2, SILO_FLOOR - 1, z2, "iron_block")
    b.stripes(x1, z1, x2, z1, y=SILO_FLOOR - 1)
    for x in (x1, x2):
        for z in (z1, z2):
            b.column(x, z, SILO_FLOOR, SILO_FLOOR + 1, "yellow_concrete")
    b.box(-2, SILO_FLOOR - 1, z2 + 1, 2, SILO_FLOOR - 1, -15, "polished_deepslate")
    b.box(-2, SILO_FLOOR, z2 + 1, 2, SILO_FLOOR + 3, -15, "air")
    b.box(-3, SILO_FLOOR, z2 + 1, -3, SILO_FLOOR + 3, -17, "light_gray_concrete")
    b.box(3, SILO_FLOOR, z2 + 1, 3, SILO_FLOOR + 3, -17, "light_gray_concrete")
    b.box(-3, SILO_FLOOR + 4, z2 + 1, 3, SILO_FLOOR + 4, -17, "polished_deepslate")
    for z in (-25, -20):
        b.put(-3, SILO_FLOOR + 2, z, "sea_lantern")
        b.put(3, SILO_FLOOR + 2, z, BULB)
    b.box(4, SILO_FLOOR, -25, 4, SILO_FLOOR + 1, -25, "polished_blackstone")
    b.box(-4, SILO_FLOOR, -25, -4, SILO_FLOOR + 1, -25, "polished_blackstone")
    # Laboratory on the gallery around the upgrade station.
    b.box(-3, GALLERY, -15, -1, GALLERY, -15, "smooth_quartz")
    b.box(1, GALLERY, -15, 3, GALLERY, -15, "smooth_quartz")
    b.put(-2, GALLERY + 1, -15, "flower_pot")
    b.put(2, GALLERY + 1, -15, "brown_stained_glass")
    b.put(0, GALLERY + 2, -15, "sea_lantern")
    b.put(-6, GALLERY, -13, "smooth_quartz")
    b.put(-6, GALLERY + 1, -13, "lantern")
    boss_chamber(b)
    b.box(5, SILO_FLOOR, -14, 7, SILO_FLOOR, -14, "spruce_planks")
    b.put(5, SILO_FLOOR + 1, -14, "barrel[facing=up]")


def railing():
    """Gallery cells touching the open shaft, corners included, so the bars join with no gap to slip through."""
    def ring(x, z):
        return RING_INNER < math.hypot(x + 0.5, z + 0.5) <= SILO_RADIUS
    edge = math.ceil(SILO_RADIUS) + 1
    return [(x, z) for x in range(-edge, edge) for z in range(-edge, edge) if ring(x, z)
            and any(math.hypot(x + dx + 0.5, z + dz + 0.5) <= RING_INNER for dx in (-1, 0, 1) for dz in (-1, 0, 1))]


def boss_chamber(b):
    """Behind the gate wall: the chamber the creature waits in, its window above the gate."""
    x1, y1, z1, x2, y2, z2 = BOSS_CHAMBER
    b.box(x1 - 1, y1 - 1, z1 - 1, x2 + 1, y2 + 1, z2 + 1, "deepslate_bricks")
    b.box(x1, y1, z1, x2, y2, z2, "air")
    b.box(x1, y1 - 1, z1, x2, y1 - 1, z2, "deepslate_tiles")
    for _ in range(16):
        b.put(b.rng.randint(x1, x2), y1 - 1, b.rng.randint(z1, z2), b.pick("sculk", "cracked_deepslate_tiles", "sculk"))
    for x, z in ((x1 + 1, z1 + 1), (x1 + 1, z2 - 1), (x2 - 2, z1 + 1), (x2 - 2, z2 - 1)):
        b.box(x, y1 + 6, z, x, y2, z, "iron_chain[axis=y]")
    b.box(x1 + 2, y1, -1, x1 + 3, y1 + 2, 1, "glass")
    b.box(x1 + 2, y1, 0, x1 + 3, y1 + 1, 0, "air")
    b.put(x1 + 1, y2, 0, "soul_lantern[hanging=false]")
    gx1, gy1, gz1, gx2, gy2, gz2 = BOSS_GATE
    b.box(gx1, gy1, gz1, gx2, gy2, gz2, "reinforced_deepslate")
    for y in range(gy1, gy2 + 1):
        for z in range(gz1, gz2 + 1):
            b.put(gx2, y, z, "yellow_concrete" if (y + z) % 4 < 2 else "black_concrete")
    b.box(gx2, gy1, gz1 - 1, gx2, gy2 + 1, gz1 - 1, "polished_blackstone_bricks")
    b.box(gx2, gy1, gz2 + 1, gx2, gy2 + 1, gz2 + 1, "polished_blackstone_bricks")
    b.box(gx2, gy2 + 1, gz1, gx2, gy2 + 1, gz2, "polished_blackstone_bricks")
    b.box(gx1, gy2 + 2, -2, gx2, gy2 + 3, 2, "tinted_glass")
    b.box(gx1 - 1, gy2 + 2, -2, gx1 - 1, gy2 + 3, 2, "air")
    for x, y, z in ((gx2, gy1 + 3, gz1 + 1), (gx2, gy1 + 5, gz2 - 2), (gx2, gy1 + 1, 0)):
        b.put(x, y, z, "cracked_deepslate_bricks")
    b.put(gx2 + 1, gy2 + 1, gz1 - 2, "redstone_lamp[lit=true]")
    b.put(gx2 + 1, gy2 + 1, gz2 + 2, "redstone_lamp[lit=true]")


def bay(b, window_id, spec):
    """An enclosed spawn bay behind a window: boards in the wall, bars above, walls and a partly barred roof."""
    room, axis, plane, first, outward, floor = spec
    box = bay_box(spec)
    x1, y1, z1, x2, y2, z2 = box
    surface = floor == 0
    walls = {"yard": "spruce_planks", "workshop": "light_gray_concrete", "radio_tower": "polished_andesite",
             "greenhouse": "white_concrete", "cold_store": "white_concrete", "power": "deepslate_tiles",
             "silo": "light_gray_concrete"}[room]
    if room == "yard":
        walls = {"yard-north": "light_gray_concrete", "yard-east": "spruce_planks", "yard-south": "deepslate_tiles",
                 "yard-west": "white_concrete"}[window_id]
    outer = surface and room != "yard"
    shell = "polished_andesite" if outer else walls
    b.box(x1, y1 - 1, z1, x2, y1 - 1, z2, "cobblestone" if outer else "stone_bricks")
    wall_box(b, x1, z1, x2, z2, y1, y2, shell)
    b.box(x1, y2, z1, x2, y2, z2, shell)
    if axis == "x":
        inner_x = plane + outward
        inside = (min(inner_x, x2 - 1 if outward > 0 else x1 + 1), z1 + 1, max(inner_x, x2 - 1 if outward > 0 else x1 + 1), z2 - 1)
    else:
        inner_z = plane + outward
        inside = (x1 + 1, min(inner_z, z2 - 1 if outward > 0 else z1 + 1), x2 - 1, max(inner_z, z2 - 1 if outward > 0 else z1 + 1))
    b.box(inside[0], y1, inside[1], inside[2], y2 - 1, inside[3], "air")
    if outer:
        for x in range(inside[0], inside[2] + 1):
            for z in range(inside[1], inside[3] + 1):
                if (x + z) % 3 == 0:
                    b.put(x, y2, z, "iron_bars")
        far = (x2 if outward > 0 else x1) if axis == "x" else (z2 if outward > 0 else z1)
        if axis == "x":
            b.box(far, y1, z1 + 2, far, y1 + 3, z2 - 2, "iron_bars")
        else:
            b.box(x1 + 2, y1, far, x2 - 2, y1 + 3, far, "iron_bars")
    for _ in range(3):
        x, z = b.rng.randint(inside[0], inside[2]), b.rng.randint(inside[1], inside[3])
        b.put(x, y1 - 1, z, "coarse_dirt" if surface else "cracked_deepslate_tiles")
    bx1, by1, bz1, bx2, by2, bz2 = boards_box(spec)
    b.box(bx1, by1, bz1, bx2, by2, bz2, BOARD)
    bars = "iron_bars[east=true,west=true]" if axis == "z" else "iron_bars[north=true,south=true]"
    if axis == "z":
        b.box(bx1 - 1, by2 + 1, bz1, bx2 + 1, by2 + 1, bz1, bars)
        b.box(bx1 - 1, by1, bz1, bx1 - 1, by2, bz1, "stripped_spruce_log[axis=y]")
        b.box(bx2 + 1, by1, bz1, bx2 + 1, by2, bz1, "stripped_spruce_log[axis=y]")
    else:
        b.box(bx1, by2 + 1, bz1 - 1, bx1, by2 + 1, bz2 + 1, bars)
        b.box(bx1, by1, bz1 - 1, bx1, by2, bz1 - 1, "stripped_spruce_log[axis=y]")
        b.box(bx1, by1, bz2 + 1, bx1, by2, bz2 + 1, "stripped_spruce_log[axis=y]")
    reveal(b, spec)


def reveal(b, spec):
    """Clears the cells between the boards and the room where a thick or curved wall stands in between."""
    room, axis, plane, first, outward, floor = spec
    for step in range(1, 6 if room == "silo" else 2):
        at = plane - outward * step
        low, high, top = (first, first + 1, floor + 1) if step == 1 else (first - 1, first + 2, floor + 2)
        if axis == "x":
            b.box(at, floor, low, at, top, high, "air")
            if room == "silo":
                b.box(at, floor - 1, low, at, floor - 1, high, "polished_deepslate")
        else:
            b.box(low, floor, at, high, top, at, "air")
            if room == "silo":
                b.box(low, floor - 1, at, high, floor - 1, at, "polished_deepslate")


def bay_box(spec):
    room, axis, plane, first, outward, floor = spec
    far = plane + outward * BAY_DEPTH
    if axis == "x":
        return (min(plane, far), floor, first - 3, max(plane, far), floor + 5, first + 4)
    return (first - 3, floor, min(plane, far), first + 4, floor + 5, max(plane, far))


def boards_box(spec):
    room, axis, plane, first, outward, floor = spec
    if axis == "x":
        return (plane, floor, first, plane, floor + 1, first + 1)
    return (first, floor, plane, first + 1, floor + 1, plane)


def window_points(spec):
    room, axis, plane, first, outward, floor = spec
    middle = first + 1.0
    if axis == "x":
        spawn = (plane + outward * (BAY_DEPTH - 1.5), floor, middle)
        entry = (plane - outward * 2.5 + 0.5, floor, middle)
    else:
        spawn = (middle, floor, plane + outward * (BAY_DEPTH - 1.5))
        entry = (middle, floor, plane - outward * 2.5 + 0.5)
    return spawn, entry


def doors(b):
    for (rooms, price, box) in DOORS.values():
        x1, y1, z1, x2, y2, z2 = box
        b.box(x1, y1, z1, x2, y2, z2, DOOR)
        b.box(x1, y1 - 1, z1, x2, y1 - 1, z2, "polished_blackstone")
        if x1 == x2:
            b.box(x1, y1, z1 - 1, x1, y2 + 1, z1 - 1, "yellow_concrete")
            b.box(x1, y1, z2 + 1, x1, y2 + 1, z2 + 1, "yellow_concrete")
            b.box(x1, y2 + 1, z1, x1, y2 + 1, z2, "black_concrete")
            b.put(x1, y2 + 1, (z1 + z2) // 2, "yellow_concrete")
        elif z1 == z2:
            b.box(x1 - 1, y1, z1, x1 - 1, y2 + 1, z1, "yellow_concrete")
            b.box(x2 + 1, y1, z1, x2 + 1, y2 + 1, z1, "yellow_concrete")
            b.box(x1, y2 + 1, z1, x2, y2 + 1, z1, "black_concrete")
            b.put((x1 + x2) // 2, y2 + 1, z1, "yellow_concrete")


def stations(b):
    for station_id, (room, kind, perk, (x, y, z)) in STATIONS.items():
        b.put(x, y - 1, z, "waxed_cut_copper")
        b.put(x, y, z, STATION_BLOCKS[kind])
        if room != "silo" and room != "yard" and kind != "MYSTERY":
            b.put(x, y + 3, z, "lantern[hanging=true]")
            b.box(x, y + 4, z, x, y + 6, z, "iron_chain[axis=y]")
    for note_id, (room, (x, y, z)) in NOTES.items():
        rx1, _, rz1, rx2, _, rz2 = ROOMS[room]
        dx, dz = (rx1 + rx2 + 1) / 2 - x, (rz1 + rz2 + 1) / 2 - z
        if room == "silo":
            dx, dz = -x, -z
        facing = ("east" if dx > 0 else "west") if abs(dx) >= abs(dz) else ("south" if dz > 0 else "north")
        b.put(x, y - 1, z, "polished_andesite")
        b.put(x, y, z, f"lectern[facing={facing},has_book=true]")


def build():
    scenery = Builder(7)
    terrain(scenery)
    b = Builder(17)
    perimeter(b)
    silo(b)
    yard(b)
    hof_facades(b)
    workshop(b)
    radio_tower(b)
    greenhouse(b)
    power(b)
    cold_store(b)
    for window_id, spec in WINDOWS.items():
        bay(b, window_id, spec)
    doors(b)
    stations(b)
    return scenery, b


# ----- checks -----

PASSABLE = ("air", "cave_air", "light", "short_grass", "fern", "large_fern", "tall_grass", "dead_bush", "bush",
            "short_dry_grass", "tall_dry_grass", "snow", "lever", "torchflower", "redstone_wire", "spore_blossom",
            "cave_vines", "cave_vines_plant", "pitcher_plant", "glow_lichen", "sculk_vein", "vine", "rail", "tripwire")
TALL = ("_fence", "_wall", "iron_bars", "_pane")


def base(block):
    return block.split("[")[0]


def passable(block):
    name = base(block)
    if name == "snow":
        return "layers=1" in block or "[" not in block
    return name in PASSABLE or name.endswith("_carpet") or name.endswith("_button") or name.endswith("_sign") \
        or name.endswith("torch") or name.endswith("_pressure_plate")


class Grid:
    def __init__(self, fills, region):
        self.region = region
        x1, y1, z1, x2, y2, z2 = region
        self.size = (x2 - x1 + 1, y2 - y1 + 1, z2 - z1 + 1)
        self.palette = ["air"]
        self.index = {"air": 0}
        self.cells = bytearray(self.size[0] * self.size[1] * self.size[2] * 2)
        for fx1, fy1, fz1, fx2, fy2, fz2, block in fills:
            cx1, cy1, cz1 = max(fx1, x1), max(fy1, y1), max(fz1, z1)
            cx2, cy2, cz2 = min(fx2, x2), min(fy2, y2), min(fz2, z2)
            if cx1 > cx2 or cy1 > cy2 or cz1 > cz2:
                continue
            code = self.code(block)
            low, high = code & 0xFF, code >> 8
            for x in range(cx1, cx2 + 1):
                for y in range(cy1, cy2 + 1):
                    start = self.offset(x, y, cz1)
                    count = cz2 - cz1 + 1
                    self.cells[start:start + count * 2:2] = bytes([low]) * count
                    self.cells[start + 1:start + count * 2:2] = bytes([high]) * count
        self.passable = [passable(block) for block in self.palette]

    def code(self, block):
        if block not in self.index:
            self.index[block] = len(self.palette)
            self.palette.append(block)
        return self.index[block]

    def offset(self, x, y, z):
        x1, y1, z1 = self.region[:3]
        return (((x - x1) * self.size[1] + (y - y1)) * self.size[2] + (z - z1)) * 2

    def at(self, x, y, z):
        x1, y1, z1, x2, y2, z2 = self.region
        if not (x1 <= x <= x2 and y1 <= y <= y2 and z1 <= z <= z2):
            return "stone"
        start = self.offset(x, y, z)
        return self.palette[self.cells[start] | self.cells[start + 1] << 8]

    def open(self, x, y, z):
        x1, y1, z1, x2, y2, z2 = self.region
        if not (x1 <= x <= x2 and y1 <= y <= y2 and z1 <= z <= z2):
            return False
        start = self.offset(x, y, z)
        return self.passable[self.cells[start] | self.cells[start + 1] << 8]

    def stand(self, x, y, z):
        if not (self.open(x, y, z) and self.open(x, y + 1, z)) or self.open(x, y - 1, z):
            return False
        below = base(self.at(x, y - 1, z))
        return below != "water" and not any(below.endswith(tall) for tall in TALL)


def walk(grid, starts, allowed=lambda cell: True):
    """Where a player or a mob can walk from the starts: steps up one block, drops up to three."""
    reached = {start for start in starts if grid.stand(*start) and allowed(start)}
    pending = deque(reached)
    while pending:
        x, y, z = pending.popleft()
        for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            for ny in (y, y + 1, y - 1, y - 2, y - 3):
                cell = nx, ny, nz
                if cell in reached:
                    break
                if ny > y and not grid.open(x, y + 2, z):
                    continue
                if ny < y and not all(grid.open(nx, level, nz) for level in range(ny, y + 2)):
                    continue
                if ny > y and not grid.open(nx, ny + 1, nz):
                    continue
                if grid.stand(*cell) and allowed(cell):
                    reached.add(cell)
                    pending.append(cell)
                    break
    return reached


def body_walk(grid, cells, width, height):
    """Cells where a body width blocks square and height tall fits; its columns may stand a block apart, as on stairs."""
    fits = set()
    for x, y, z in cells:
        columns = []
        for dx in range(width):
            for dz in range(width):
                feet = next((y + dy for dy in (0, 1, -1) if (x + dx, y + dy, z + dz) in cells), None)
                if feet is None:
                    break
                columns.append((x + dx, feet, z + dz))
            else:
                continue
            break
        else:
            top = max(feet for _, feet, _ in columns) + height
            if all(grid.open(cx, level, cz) for cx, feet, cz in columns for level in range(feet, top)):
                fits.add((x, y, z))
    return fits


def connected(cells, start):
    if start not in cells:
        return set()
    reached = {start}
    pending = deque([start])
    while pending:
        x, y, z = pending.popleft()
        for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            for ny in (y, y + 1, y - 1, y - 2, y - 3):
                cell = nx, ny, nz
                if cell in cells and cell not in reached:
                    reached.add(cell)
                    pending.append(cell)
    return reached


def inside(box, point):
    x1, y1, z1, x2, y2, z2 = box
    x, y, z = point[:3]
    return x1 <= x < x2 + 1 and y1 <= y < y2 + 1 and z1 <= z < z2 + 1


def room_of(cell):
    for room, box in ROOMS.items():
        if inside(box, cell):
            return room
    return None


def validate(fills):
    problems = []
    grid = Grid(fills, (-72, -34, -72, 72, 40, 72))
    door_cells = {cell for (_, _, box) in DOORS.values() for cell in box_cells(box)}
    bays = [bay_box(spec) for spec in WINDOWS.values()]
    open_doors = fills + [(*box, "air") for (_, _, box) in DOORS.values()]
    grid_open = Grid(open_doors, grid.region)
    spawn = tuple(math.floor(value) for value in SPAWN[:3])
    if not grid.stand(*spawn) or room_of(spawn) != "yard":
        problems.append(f"Spawn {SPAWN} is not a standing place in the yard")
    closed = walk(grid, [spawn])
    strays = sorted(cell for cell in closed if room_of(cell) != "yard")
    if strays:
        problems.append(f"With every door shut, players leave the yard at {strays[:5]}")
    reach = walk(grid_open, [spawn])
    outside = sorted(cell for cell in reach if room_of(cell) is None or any(inside(bay, cell) for bay in bays))
    if outside:
        problems.append(f"{len(outside)} reachable places lie outside every room or inside a spawn bay, e.g. {outside[:6]}")
    high = sorted(cell for cell in reach if cell[1] > 2 and not inside(HIGH_GROUND, cell))
    if high:
        problems.append(f"{len(high)} reachable places stand higher than two blocks outside the radio tower, e.g. {high[:6]}")
    for (x, y, z) in reach:
        here = room_of((x, y, z))
        if y == 0 and here != "silo" and (base(grid_open.at(x, -1, z)).endswith("_slab") or base(grid_open.at(x, -1, z)).endswith("_stairs")):
            if "type=bottom" in grid_open.at(x, -1, z) or base(grid_open.at(x, -1, z)).endswith("_stairs"):
                problems.append(f"Half block under the surface floor at {(x, -1, z)}: a player there stands below the room")
        for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            for ny in (y, y + 1, y - 1, y - 2, y - 3):
                other = (nx, ny, nz)
                if other in reach:
                    there = room_of(other)
                    if there != here and (x, y, z) not in door_cells and other not in door_cells \
                            and not any((x, level, z) in door_cells or (nx, level, nz) in door_cells for level in (y - 1, y, y + 1)):
                        problems.append(f"Open passage between {here} and {there} at {(x, y, z)} -> {other}")
                    break
    rooms_reached = {room_of(cell) for cell in reach}
    for room in ROOMS:
        if room not in rooms_reached:
            problems.append(f"No reachable place in {room}")
    for door_id, (rooms, price, box) in DOORS.items():
        x1, y1, z1, x2, y2, z2 = box
        if max(x2 - x1, z2 - z1) + 1 < 3 or y2 - y1 + 1 < 3:
            problems.append(f"Door {door_id} is narrower than three by three")
        sides = set()
        for (x, y, z) in box_cells(box):
            for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                for ny in (y, y - 1):
                    if (nx, ny, nz) in reach and (nx, ny, nz) not in door_cells:
                        sides.add(room_of((nx, ny, nz)))
        if sides != set(rooms):
            problems.append(f"Door {door_id} joins {sorted(map(str, sides))}, not {sorted(rooms)}")
    for window_id, spec in WINDOWS.items():
        bx = bay_box(spec)
        boards = boards_box(spec)
        spawn_point, entry = window_points(spec)
        spawn_cell = tuple(math.floor(value) for value in spawn_point)
        entry_cell = tuple(math.floor(value) for value in entry)
        if not inside(bx, spawn_point) or inside(bx, entry) or room_of(entry) != spec[0]:
            problems.append(f"Window {window_id}: spawn outside its bay or entry outside its room")
        if not inside(bx, approach(spec)) or inside(boards, approach(spec)):
            problems.append(f"Window {window_id}: enemies would approach the boards from outside the bay")
        if not grid.stand(*spawn_cell):
            problems.append(f"Window {window_id}: no floor at the spawn {spawn_point}")
        shut = walk(grid_open, [spawn_cell])
        if any(not inside(bx, cell) for cell in shut):
            problems.append(f"Window {window_id}: its bay is open while the boards stand")
        broken = Grid(open_doors + [(*boards, "air")], grid.region)
        through = walk(broken, [spawn_cell])
        if entry_cell not in through:
            problems.append(f"Window {window_id}: enemies cannot walk from the bay to {entry}")
        if spawn not in through:
            problems.append(f"Window {window_id}: enemies from it cannot reach the spawn")
    for station_id, (room, kind, perk, (x, y, z)) in STATIONS.items():
        if base(grid.at(x, y, z)) != base(STATION_BLOCKS[kind]) or room_of((x, y, z)) != room:
            problems.append(f"Station {station_id} is not a {kind} block in {room}")
        if not any(math.dist((cx + 0.5, cy, cz + 0.5), (x, y, z)) < 4.5 for cx, cy, cz in reach):
            problems.append(f"Station {station_id} cannot be reached")
    for note_id, (room, (x, y, z)) in NOTES.items():
        if base(grid.at(x, y, z)) != "lectern" or room_of((x, y, z)) != room:
            problems.append(f"Note {note_id} is not a lectern in {room}")
        if not any(math.dist((cx + 0.5, cy, cz + 0.5), (x + 0.5, y, z + 0.5)) < 4.5 for cx, cy, cz in reach):
            problems.append(f"Note {note_id} cannot be reached")
    for room in ROOMS:
        if not any(base(grid.at(x, y, z)) == "waxed_copper_bulb" for (x, y, z) in box_cells(ROOMS[room])):
            problems.append(f"No power light in {room}")
    mobs = body_walk(grid_open, reach, 2, 2)
    mob_area = connected(mobs, spawn)
    for room in ROOMS:
        if not any(room_of(cell) == room for cell in mob_area):
            problems.append(f"A two-wide enemy cannot reach {room} from the spawn")
    for window_id, spec in WINDOWS.items():
        entry_cell = tuple(math.floor(value) for value in window_points(spec)[1])
        if not any(math.dist(cell, entry_cell) <= 1.5 for cell in mob_area):
            problems.append(f"A two-wide enemy cannot reach the window {window_id}")
    boss = Grid(open_doors + [(*BOSS_GATE, "air")], grid.region)
    boss_spawn = tuple(math.floor(value) for value in BOSS_SPAWN[:3])
    boss_cells = walk(boss, [boss_spawn], allowed=lambda cell: inside(ROOMS["silo"], cell) or inside(BOSS_CHAMBER, cell))
    width, height = BOSS_BODY
    roomy = body_walk(boss, boss_cells, width, height)
    boss_area = connected(roomy, boss_spawn)
    floor_area = sum(1 for cell in boss_area if cell[1] == SILO_FLOOR and math.hypot(cell[0] + 0.5, cell[2] + 0.5) <= SILO_RADIUS)
    if boss_spawn not in roomy or floor_area < 300:
        problems.append(f"The silo boss ({width} wide, {height} tall) has {floor_area} floor places past its gate")
    problems.extend(f"No German text for {key} in messages.yml" for key in missing_messages())
    return problems, len(reach), len(mob_area), floor_area


def approach(spec):
    """Where ArenaMap.Window.approach() sends enemies: 1.5 blocks from the boards' middle toward the spawn."""
    x1, y1, z1, x2, y2, z2 = boards_box(spec)
    (sx, _, sz), (ex, _, ez) = window_points(spec)
    length = math.hypot(sx - ex, sz - ez)
    return ((x1 + x2 + 1) / 2 + (sx - ex) / length * 1.5, y1, (z1 + z2 + 1) / 2 + (sz - ez) / length * 1.5)


def missing_messages():
    """Room names and notes the map uses but the Zombies messages do not have."""
    text = (ROOT / "paper/zombies/src/main/resources/messages.yml").read_text(encoding="utf8")

    def section(name):
        start = text.index(f"\n{name}:\n")
        following = [index for index in (text.find(f"\n{key}", start + 1) for key in "abcdefghijklmnopqrstuvwxyz") if index > 0]
        return text[start:min(following, default=len(text))]

    rooms = [f"room.{room}" for room in ROOMS if f"\n  {room}: " not in section("room")]
    notes = [f"note.{note}" for note in NOTES if f"\n  {note}:\n    name: " not in section("note")]
    return rooms + notes


def box_cells(box):
    x1, y1, z1, x2, y2, z2 = box
    return [(x, y, z) for x in range(x1, x2 + 1) for y in range(y1, y2 + 1) for z in range(z1, z2 + 1)]


# ----- output -----

PANE_LIKE = ("iron_bars", "glass_pane")


def connections(fills):
    """Bars and panes placed by a fill stand alone until a neighbour updates; this gives them their sides up front."""
    grid = Grid(fills, (-72, -34, -72, 72, 40, 72))
    fixes = []
    seen = set()
    for x1, y1, z1, x2, y2, z2, block in fills:
        if not base(block).endswith(PANE_LIKE):
            continue
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    if (x, y, z) in seen or base(grid.at(x, y, z)) != base(block):
                        continue
                    seen.add((x, y, z))
                    sides = []
                    for name, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
                        neighbour = grid.at(x + dx, y, z + dz)
                        attaches = base(neighbour).endswith(PANE_LIKE) or (not passable(neighbour) and full_block(neighbour))
                        sides.append(f"{name}={'true' if attaches else 'false'}")
                    fixes.append((x, y, z, x, y, z, f"{base(grid.at(x, y, z))}[{','.join(sides)},waterlogged=false]"))
    return fixes


def full_block(block):
    name = base(block)
    partial = ("_slab", "_stairs", "_trapdoor", "_door", "lantern", "iron_chain", "lightning_rod", "_bed", "lectern",
               "brewing_stand", "enchanting_table", "ender_chest", "anvil", "grindstone", "stonecutter", "flower_pot",
               "decorated_pot", "end_rod", "lever", "heavy_core", "composter", "_wall", "_fence", "carpet", "snow",
               "spore_blossom", "cave_vines", "azalea", "fern", "grass", "torchflower", "pitcher", "bush", "_pane",
               "iron_bars", "sculk_sensor", "beacon")
    return not any(part in name for part in partial) and name != "air"


def commands(fills):
    for x1, y1, z1, x2, y2, z2, block in fills:
        x1, x2 = x1 + ORIGIN[0], x2 + ORIGIN[0]
        y1, y2 = y1 + ORIGIN[1], y2 + ORIGIN[1]
        z1, z2 = z1 + ORIGIN[2], z2 + ORIGIN[2]
        if (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1) <= MOST_PER_FILL:
            yield f"fill {x1} {y1} {z1} {x2} {y2} {z2} {block}"
            continue
        for x in range(x1, x2 + 1, 32):
            for y in range(y1, y2 + 1, 32):
                for z in range(z1, z2 + 1, 32):
                    yield f"fill {x} {y} {z} {min(x + 31, x2)} {min(y + 31, y2)} {min(z + 31, z2)} {block}"


def forceload_areas():
    """A row of chunks at a time: the command refuses more than 256 chunks, and a fill only reaches loaded ones."""
    low, high = (-104 + ORIGIN[0]) >> 4, (104 + ORIGIN[0]) >> 4
    return [f"{low * 16} {cz * 16} {high * 16} {cz * 16}" for cz in range((-104 + ORIGIN[2]) >> 4, ((104 + ORIGIN[2]) >> 4) + 1)]


def biomes():
    y1, y2 = ORIGIN[1] - 40, ORIGIN[1] + 40
    lines = []
    for x in range(-104, 105, 32):
        for z in range(-104, 105, 32):
            biome = "minecraft:old_growth_spruce_taiga"
            lines.append(f"fillbiome {x + ORIGIN[0]} {y1} {z + ORIGIN[2]} {min(x + 31, 104) + ORIGIN[0]} {y2} {min(z + 31, 104) + ORIGIN[2]} {biome}")
    return lines


def point(values, yaw=None):
    x, y, z = values[:3]
    result = {"x": x, "y": y, "z": z}
    if yaw is not None:
        result["yaw"] = yaw
    return result


def box_yaml(box):
    x1, y1, z1, x2, y2, z2 = box
    return {"x1": x1, "y1": y1, "z1": z1, "x2": x2, "y2": y2, "z2": z2}


def arena():
    plan = {
        "name": "Silo 7",
        "difficulty": "NORMAL",
        "time": 14000,
        "door-block-data": f"minecraft:{DOOR}",
        "window-block-data": f"minecraft:{BOARD}",
        "origin": point(ORIGIN),
        "starter": {"build": False, "built": True},
        "lobby": point(SPAWN, SPAWN[3]),
        "spawn": point(SPAWN, SPAWN[3]),
        "spectator": point(SPECTATOR),
        "rooms": {room: {"bounds": box_yaml(box)} for room, box in ROOMS.items()},
        "doors": {door_id: {"rooms": list(rooms), "price": price, "box": box_yaml(box)} for door_id, (rooms, price, box) in DOORS.items()},
        "windows": {},
        "stations": {},
        "notes": {note_id: {"room": room, "location": point(at)} for note_id, (room, at) in NOTES.items()},
        "quest": quest(),
    }
    for window_id, spec in WINDOWS.items():
        spawn, entry = window_points(spec)
        plan["windows"][window_id] = {"room": spec[0], "bay": box_yaml(bay_box(spec)), "boards": box_yaml(boards_box(spec)),
                                      "spawn": point(spawn), "entry": point(entry)}
    for station_id, (room, kind, perk, at) in STATIONS.items():
        entry = {"room": room, "kind": kind}
        if perk:
            entry["perk"] = perk
        entry["location"] = point(at)
        plan["stations"][station_id] = entry
    return plan


def quest():
    """Spots the later silo quest uses. ArenaMap ignores keys it does not know, so they ride along in the arena file."""
    x1, y1, z1, x2, y2, z2 = LIFT_SHAFT
    centre_x, centre_z = (x1 + x2 + 1) / 2, (z1 + z2 + 1) / 2
    return {
        "lift": {"shaft": box_yaml(LIFT_SHAFT), "grate": box_yaml((x1, -1, z1, x2, -1, z2)),
                 "top": point((centre_x, 0, centre_z)), "bottom": point((centre_x, SILO_FLOOR, centre_z)),
                 "controls": point((x2 + 2, 0, z2 + 1))},
        "stairs": {"gate": "silo-gate", "top": point((-4.5, -1, 34.5)), "bottom": point((-4.5, GALLERY, 15.5))},
        "boss": {"rig": "sw_rancor", "gate": box_yaml(BOSS_GATE), "chamber": box_yaml(BOSS_CHAMBER),
                 "spawn": point(BOSS_SPAWN, BOSS_SPAWN[3]),
                 "arena": {"center": point((0, SILO_FLOOR, 0)), "radius": 14, "ceiling": -3}},
    }


def yaml_scalar(value):
    return json.dumps(value, ensure_ascii=False) if isinstance(value, (str, bool)) else repr(value)


def to_yaml(value, indent=0):
    pad = " " * indent
    lines = []
    if isinstance(value, dict):
        flat = all(not isinstance(item, (dict, list)) for item in value.values())
        if flat and indent and len(value) <= 6:
            return [pad + "{" + ", ".join(f"{key}: {yaml_scalar(item)}" for key, item in value.items()) + "}"]
        for key, item in value.items():
            if isinstance(item, dict) and item and all(not isinstance(each, (dict, list)) for each in item.values()) and len(item) <= 6:
                lines.append(f"{pad}{key}: " + "{" + ", ".join(f"{k}: {yaml_scalar(v)}" for k, v in item.items()) + "}")
            elif isinstance(item, list) and all(not isinstance(each, (dict, list)) for each in item):
                lines.append(f"{pad}{key}: [{', '.join(yaml_scalar(each) for each in item)}]")
            elif isinstance(item, (dict, list)) and item:
                lines.append(f"{pad}{key}:")
                lines.extend(to_yaml(item, indent + 2))
            else:
                lines.append(f"{pad}{key}: {'{}' if item == {} else yaml_scalar(item)}")
    return lines


def main():
    scenery, b = build()
    problems, reachable, mob_places, boss_floor = validate(scenery.fills + b.fills)
    if problems:
        for problem in problems[:40]:
            print(problem)
        sys.exit(f"{len(problems)} problems in Silo 7")
    fixes = connections(scenery.fills + b.fills)
    lines = list(commands(scenery.fills)) + list(commands(b.fills)) + list(commands(fixes))
    functions = OUT / "datapack/data/silo7/function"
    if functions.exists():
        for stale in functions.glob("*.mcfunction"):
            stale.unlink()
    functions.mkdir(parents=True, exist_ok=True)
    (OUT / "datapack/pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 121, "min_format": 121, "max_format": 121, "description": "Silo 7"}}))
    sequence = []
    lines = biomes() + lines
    for index, start in enumerate(range(0, len(lines), COMMANDS_PER_FUNCTION)):
        name = f"build_{index + 1}"
        (functions / f"{name}.mcfunction").write_text("\n".join(lines[start:start + COMMANDS_PER_FUNCTION]) + "\n")
        sequence.append(f"silo7:{name}")
    (OUT / "functions.txt").write_text("\n".join(sequence) + "\n")
    files = OUT / "files/blockcraft"
    (files / "Zombies").mkdir(parents=True, exist_ok=True)
    (files / "Zombies/arena.yml").write_text("# Generated by dev/mapgen/silo7.py; change that and rebuild the map.\n" + "\n".join(to_yaml(arena())) + "\n")
    (files / "map.yml").write_text("name: Silo 7\nbuilders: [BlockcraftMC]\n")
    (OUT / "forceload.txt").write_text("\n".join(forceload_areas()) + "\n")
    print(f"{len(lines)} commands in {len(sequence)} functions, {reachable} places to stand, {mob_places} for two-wide enemies, "
          f"{boss_floor} silo floor places for the boss, {OUT}")


if __name__ == "__main__":
    sys.exit(main())

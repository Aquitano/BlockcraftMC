#!/usr/bin/env python3
"""Generates Sturmwerft, an offshore shipyard in a storm at the end of the day: its blocks as Minecraft functions and its arena.yml.

Run through `dev/verify/verify.py map`, which starts a bare server over an endless flat sea, runs the functions and saves the
result to worlds/sturmwerft. Everything stands on one deck at y 64, with the headquarters and posts where Sternenfront's
rules expect them: the posts on the north-south centre line with landing grounds at x -22 and 22, and the headquarters at
x -82 and 82.
"""
import json
import math
import random
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/mapgen/sturmwerft"
DECK = 64
SEA = 56
SKY = 65
ROADS = (-32, 32)
BRIDGES = (-25, 0, 25)
POSTS = {"trockendock": -44, "kranbruecke": 0, "turbinenhalle": 44}
DECK_BLOCK = "smooth_stone"
PLATE = "polished_andesite"
STRIPE = "yellow_concrete"
STEEL = "iron_block"
RUST = "waxed_oxidized_copper"
FRAME = "waxed_copper_block"
DARK = "polished_deepslate"
LIGHT = "ochre_froglight"
COLD_LIGHT = "sea_lantern"
HULL = "gray_terracotta"
ANTIFOULING = "red_terracotta"
STAINS = ("gray_concrete", "andesite", "tuff", "cyan_terracotta", "brown_terracotta", "waxed_weathered_copper")
CONTAINERS = ("orange", "blue", "cyan", "red", "yellow", "green", "gray")
RAIL = "iron_bars"
MOST_PER_FILL = 32768


def runs(values):
    """Consecutive stretches of whole numbers, so that a round shape takes a fill per row instead of one per block."""
    values = sorted(values)
    start = previous = values[0]
    for value in values[1:]:
        if value != previous + 1:
            yield start, previous
            start = value
        previous = value
    yield start, previous


def disc(radius, hole=0.0):
    return [(a, b) for a in range(-radius, radius + 1) for b in range(-radius, radius + 1)
            if hole <= math.hypot(a, b) <= radius + 0.3]


def spot(x, z, y=SKY):
    """The middle of a block. A hand-written "-5.5" is the block after -5, on the far side of whatever stands at -5."""
    return f"{x + 0.5} {y} {z + 0.5}"


class Builder:
    def __init__(self):
        self.commands = []
        self.fills = []

    def box(self, x1, y1, z1, x2, y2, z2, block):
        x1, x2, y1, y2, z1, z2 = min(x1, x2), max(x1, x2), min(y1, y2), max(y1, y2), min(z1, z2), max(z1, z2)
        if (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1) <= MOST_PER_FILL:
            self.commands.append(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {block}")
            self.fills.append((x1, y1, z1, x2, y2, z2, block))
            return
        for x in range(x1, x2 + 1, 32):
            for y in range(y1, y2 + 1, 32):
                for z in range(z1, z2 + 1, 32):
                    self.box(x, y, z, min(x + 31, x2), min(y + 31, y2), min(z + 31, z2), block)

    def deck(self, x1, z1, x2, z2, block=DECK_BLOCK):
        self.box(x1, DECK - 2, z1, x2, DECK - 1, z2, DARK)
        self.box(x1, DECK, z1, x2, DECK, z2, block)

    def legs(self, x1, z1, x2, z2, every=12):
        for x in range(x1 + 2, x2, every):
            for z in range(z1 + 2, z2, every):
                self.box(x, SEA - 4, z, x + 1, DECK - 3, z + 1, RUST)

    def rail(self, x1, z1, x2, z2, block=RAIL):
        self.box(x1, SKY, z1, x2, SKY, z2, block)

    def stripes(self, x1, z1, x2, z2, y=DECK, length=2):
        """Hazard paint along the longer side."""
        along_x = abs(x2 - x1) >= abs(z2 - z1)
        start, end = (min(x1, x2), max(x1, x2)) if along_x else (min(z1, z2), max(z1, z2))
        for index, at in enumerate(range(start, end + 1, length)):
            stop = min(at + length - 1, end)
            block = "yellow_concrete" if index % 2 == 0 else "black_concrete"
            if along_x:
                self.box(at, y, z1, stop, y, z2, block)
            else:
                self.box(x1, y, at, x2, y, stop, block)

    def wear(self, x1, z1, x2, z2, patches, rng):
        for _ in range(patches):
            width, depth = rng.randint(0, 3), rng.randint(0, 3)
            x, z = rng.randint(x1, x2 - width), rng.randint(z1, z2 - depth)
            self.box(x, DECK, z, x + width, DECK, z + depth, rng.choice(STAINS))

    def warning_light(self, x, y, z):
        self.box(x, y, z, x, y, z, "redstone_block")
        self.box(x, y + 1, z, x, y + 1, z, "redstone_lamp[lit=true]")

    def mast(self, x, z, height, facing_x):
        """A floodlight on a pole, its head leaning over the deck."""
        self.box(x, SKY, z, x, SKY + height, z, DARK)
        self.box(x, SKY + height + 1, z, x + facing_x * 2, SKY + height + 1, z, FRAME)
        self.box(x + facing_x, SKY + height, z, x + facing_x * 2, SKY + height, z, LIGHT)
        self.warning_light(x, SKY + height + 2, z)

    def crate(self, x, z, width, depth, height, colour):
        self.box(x, SKY, z, x + width - 1, SKY + height - 1, z + depth - 1, f"{colour}_concrete")
        self.box(x, SKY + height - 1, z, x + width - 1, SKY + height - 1, z + depth - 1, f"{colour}_terracotta")

    def wall(self, x1, z1, x2, z2, height=2, block=DARK):
        self.box(x1, SKY, z1, x2, SKY + height - 1, z2, block)


def headquarters(b, sign):
    near, far = 63 * sign, 97 * sign
    b.deck(near, -30, far, 31)
    b.legs(min(near, far), -30, max(near, far), 31)
    outer = 86 * sign
    b.box(outer, SKY, -30, far, SKY + 8, 31, DARK)
    b.box(outer, SKY + 1, -22, outer, SKY + 5, 23, "air")
    b.box(outer - sign * 12, SKY + 8, -30, far, SKY + 9, 31, FRAME)
    for z in range(-28, 30, 14):
        b.box(outer - sign * 12, SKY, z, outer - sign * 12, SKY + 8, z, RUST)
    b.rail(near, -30, far, -30)
    b.rail(near, 31, far, 31)
    edges = sorted(z for bridge in BRIDGES for z in (bridge - 3, bridge + 3))
    previous = -30
    for index in range(0, len(edges), 2):
        b.rail(near, previous, near, edges[index])
        previous = edges[index + 1]
    b.rail(near, previous, near, 31)
    for z in (-14, 14):
        b.box(70 * sign, DECK, z - 8, 70 * sign, DECK, z + 8, LIGHT)
    colour = "red" if sign > 0 else "light_blue"
    b.box(outer, SKY + 6, -30, outer, SKY + 7, 31, f"{colour}_concrete")
    b.stripes(outer, -22, outer, 23, SKY + 5)
    tower = 92 * sign
    b.box(tower - 4, SKY + 10, -7, tower + 4, SKY + 22, 7, DARK)
    b.box(tower - 4, SKY + 18, -7, tower + 4, SKY + 19, 7, "gray_stained_glass")
    b.box(tower - 3, SKY + 18, -6, tower + 3, SKY + 19, 6, LIGHT)
    b.box(tower - 5, SKY + 20, -8, tower + 5, SKY + 20, 8, f"{colour}_concrete")
    b.box(tower - 4, SKY + 23, -7, tower + 4, SKY + 23, 7, FRAME)
    b.box(tower, SKY + 24, 0, tower, SKY + 33, 0, RAIL)
    b.warning_light(tower, SKY + 34, 0)
    b.crate(min(68 * sign, 73 * sign), -27, 6, 3, 2, "red" if sign > 0 else "light_blue")
    b.crate(min(68 * sign, 73 * sign), 25, 6, 3, 2, "red" if sign > 0 else "light_blue")


def bridges(b):
    for sign in (-1, 1):
        road = ROADS[0] if sign < 0 else ROADS[1]
        for z in BRIDGES:
            near, far = (road + 4 * sign), 62 * sign
            b.deck(near, z - 2, far, z + 2, PLATE)
            b.box(near, DECK, z, far, DECK, z, STRIPE)
            b.rail(near, z - 3, far, z - 3)
            b.rail(near, z + 3, far, z + 3)
            for x in range(min(near, far), max(near, far), 10):
                b.box(x, SEA - 4, z - 1, x + 1, DECK - 3, z, RUST)
            b.stripes(near, z - 2, near, z + 2)
            b.stripes(far, z - 2, far, z + 2)
            for x in range(40, 61, 10):
                gate = x * sign
                b.box(gate, SKY, z - 3, gate, SKY + 6, z - 3, FRAME)
                b.box(gate, SKY, z + 3, gate, SKY + 6, z + 3, FRAME)
                b.box(gate, SKY + 7, z - 3, gate, SKY + 7, z + 3, FRAME)
                b.box(gate, SKY + 6, z, gate, SKY + 6, z, LIGHT)


def roads(b):
    for road in ROADS:
        b.deck(road - 4, -62, road + 4, 62, PLATE)
        b.legs(road - 4, -62, road + 4, 62)
        for z in range(-60, 61, 12):
            b.box(road, DECK, z, road, DECK, z + 1, LIGHT)
        sign = -1 if road < 0 else 1
        edge = road + 4 * sign
        cuts = sorted(z for bridge in BRIDGES for z in (bridge - 3, bridge + 3))
        previous = -62
        for index in range(0, len(cuts), 2):
            b.rail(edge, previous, edge, cuts[index])
            previous = cuts[index + 1]
        b.rail(edge, previous, edge, 62)
        inner = road - 4 * sign
        for z1, z2 in ((-30, -14), (14, 30)):
            b.rail(inner, z1, inner, z2)
        b.rail(road - 4, -62, road + 4, -62)
        b.rail(road - 4, 62, road + 4, 62)
        for z in range(-56, 57, 16):
            if all(abs(z - bridge) > 4 for bridge in BRIDGES):
                b.box(edge, SKY, z, edge, SKY + 6, z, DARK)
                b.box(edge - sign, SKY + 6, z, edge, SKY + 6, z, FRAME)
                b.box(edge - sign, SKY + 5, z, edge - sign, SKY + 5, z, LIGHT)


def post_deck(b, z):
    b.deck(-28, z - 14, 28, z + 14)
    b.legs(-28, z - 14, 28, z + 14)
    for x1, x2 in ((-28, -4), (4, 28)):
        b.rail(x1, z - 14, x2, z - 14)
        b.rail(x1, z + 14, x2, z + 14)
        b.stripes(x1, z - 13, x2, z - 13)
        b.stripes(x1, z + 13, x2, z + 13)
    b.wear(-27, z - 12, 27, z + 12, 26, random.Random(z))
    for sign in (-1, 1):
        for edge in (z - 13, z + 13):
            b.mast(27 * sign, edge, 14, -sign)


def causeways(b):
    for z1, z2 in ((-29, -15), (15, 29)):
        b.deck(-3, z1, 3, z2, PLATE)
        b.box(0, DECK, z1, 0, DECK, z2, STRIPE)
        b.rail(-4, z1, -4, z2)
        b.rail(4, z1, 4, z2)
        b.box(-1, SEA - 4, (z1 + z2) // 2, 1, DECK - 3, (z1 + z2) // 2, RUST)


def relic_dais(b):
    b.deck(-8, -70, 8, -59)
    b.legs(-8, -70, 8, -59, 8)
    b.rail(-8, -70, 8, -70)
    b.rail(-8, -70, -8, -59)
    b.rail(8, -70, 8, -59)
    for step in range(5):
        b.box(-4, SKY, -59 - step, 4, SKY + step, -59 - step, DARK)
    b.box(-4, SKY, -68, 4, SKY + 4, -64, DARK)
    b.box(0, SKY + 4, -65, 0, SKY + 4, -65, LIGHT)
    for x in (-4, 4):
        b.box(x, SKY + 5, -68, x, SKY + 10, -68, RUST)
    b.box(-4, SKY + 10, -68, 4, SKY + 10, -68, RUST)


def trockendock(b, z):
    """Open air and long sightlines: a half-built hull's ribs, with cover near the post and a clear walk around it."""
    for rib_z in (z - 13, z + 13):
        for x in (-16, 16):
            b.box(x, SKY, rib_z, x, SKY + 12, rib_z, FRAME)
        b.box(-16, SKY + 13, rib_z, 16, SKY + 13, rib_z, FRAME)
        for x, y in ((-14, 12), (-11, 13), (11, 13), (14, 12)):
            b.box(x, SKY + y, rib_z, x, SKY + y, rib_z, RUST)
    b.crate(-14, z - 7, 5, 3, 2, "orange")
    b.crate(9, z + 4, 5, 3, 2, "orange")
    b.crate(9, z - 8, 3, 3, 3, "gray")
    b.crate(-12, z + 5, 3, 3, 3, "gray")
    b.wall(-5, z - 11, 5, z - 11, 2)
    b.wall(-5, z + 11, 5, z + 11, 2)
    b.box(-13, SKY, z - 1, -11, SKY + 1, z + 1, STEEL)
    b.box(11, SKY, z - 1, 13, SKY + 1, z + 1, STEEL)
    for x in (-16, 16):
        b.box(x, SKY + 8, z - 12, x, SKY + 12, z + 12, HULL)
        b.box(x, SKY + 7, z - 12, x, SKY + 7, z + 12, ANTIFOULING)
        b.box(x, SKY + 10, z - 4, x, SKY + 11, z + 3, "waxed_weathered_copper")


def kranbruecke(b, z):
    """The middle ground: a gantry crane overhead and a broken line of cover across the deck."""
    for x in (-15, 15):
        for leg_z in (z - 11, z + 11):
            b.box(x, SKY, leg_z, x + 1, SKY + 15, leg_z + 1, RUST)
        b.box(x, SKY + 16, z - 11, x + 1, SKY + 17, z + 12, FRAME)
    for beam in (z - 3, z + 3):
        b.box(-15, SKY + 18, beam, 16, SKY + 19, beam + 1, FRAME)
    b.box(-1, SKY + 12, z - 1, 2, SKY + 17, z + 2, "iron_chain")
    b.box(-3, SKY + 8, z - 3, 4, SKY + 11, z + 4, "yellow_concrete")
    b.box(-3, SKY + 8, z - 3, 4, SKY + 8, z + 4, "black_concrete")
    for x, depth in ((-12, 3), (9, 3)):
        b.crate(x, z - 11, 4, depth, 2, "blue")
        b.crate(x, z + 9, 4, depth, 2, "blue")
    b.wall(-11, z - 2, -9, z + 2, 2, STEEL)
    b.wall(9, z - 2, 11, z + 2, 2, STEEL)
    for x in (-15, 15):
        for step in range(1, 6):
            b.box(x, SKY + 10 + step, z - 11 + step, x, SKY + 10 + step, z - 11 + step, FRAME)
            b.box(x, SKY + 10 + step, z + 12 - step, x, SKY + 10 + step, z + 12 - step, FRAME)
    for beam in (z - 3, z + 3):
        b.stripes(-15, beam, 16, beam, SKY + 20)
        b.box(-15, SKY + 21, beam, 16, SKY + 21, beam, RAIL)
    b.box(-13, SKY + 14, z + 4, -9, SKY + 17, z + 8, "yellow_concrete")
    b.box(-13, SKY + 15, z + 4, -9, SKY + 16, z + 8, "gray_stained_glass")
    b.box(-12, SKY + 15, z + 5, -10, SKY + 16, z + 7, LIGHT)
    b.warning_light(-15, SKY + 18, z + 12)
    b.warning_light(16, SKY + 18, z - 11)


def turbinenhalle(b, z):
    """Close quarters: a roofed hall with wide doors on every side, so the post is fought over indoors."""
    west, east, north, south = -14, 14, z - 9, z + 9
    b.box(west, SKY, north, east, SKY + 7, south, DARK)
    b.box(west + 1, SKY, north + 1, east - 1, SKY + 6, south - 1, "air")
    b.box(west, SKY + 7, north, east, SKY + 7, south, FRAME)
    for x in (west, east):
        b.box(x, SKY, z - 3, x, SKY + 3, z + 3, "air")
    for door_z in (north, south):
        b.box(-3, SKY, door_z, 3, SKY + 3, door_z, "air")
        b.box(-13, SKY + 3, door_z, -6, SKY + 4, door_z, "gray_stained_glass")
        b.box(6, SKY + 3, door_z, 13, SKY + 4, door_z, "gray_stained_glass")
    for x in (-9, 9):
        b.box(x - 1, SKY, z - 6, x + 1, SKY + 2, z - 4, RUST)
        b.box(x - 1, SKY, z + 4, x + 1, SKY + 2, z + 6, RUST)
    for x in range(-11, 12, 6):
        for lamp in (z - 4, z + 4):
            b.box(x, SKY + 6, lamp, x, SKY + 6, lamp, COLD_LIGHT)
    for door_z in (north, south):
        b.stripes(-14, door_z, 14, door_z, SKY + 5)
    for x in (-12, -4, 4, 12):
        b.box(x - 2, SKY + 8, north + 1, x + 2, SKY + 8, south - 1, DARK)
        b.box(x, SKY + 9, north + 1, x, SKY + 9, south - 1, "gray_stained_glass")
    for x, offset in ((-10, -5), (10, 5)):
        b.box(x - 1, SKY + 9, z + offset - 1, x + 1, SKY + 17, z + offset + 1, RUST)
        b.box(x - 1, SKY + 18, z + offset - 1, x + 1, SKY + 18, z + offset + 1, LIGHT)
    b.crate(-25, z - 12, 4, 3, 2, "gray")
    b.crate(21, z + 9, 4, 3, 2, "gray")


def ship(b, rng):
    """A freighter under construction north of the yard, its midships still bare frames around working lights."""
    z0, stern, bow, top = -102, -80, 80, SEA + 30
    profile = defaultdict(list)
    for x in range(stern, bow + 1):
        half = max(1, 17 - max(0, x - 44) * 16 // 36 - max(0, stern + 5 - x))
        keel = SEA - 3 + max(0, x - 60) // 2
        profile[half, keel].append(x)
    framed = range(-24, 29)
    for (half, keel), xs in profile.items():
        for x1, x2 in runs([x for x in xs if x not in framed]):
            b.box(x1, keel, z0 - half, x2, SEA + 6, z0 + half, ANTIFOULING)
            b.box(x1, SEA + 7, z0 - half, x2, top - 1, z0 + half, HULL)
            b.box(x1, top, z0 - half, x2, top, z0 + half, "gray_concrete")
    b.box(framed[0], SEA - 3, z0 - 17, framed[-1], SEA + 4, z0 + 17, DARK)
    for x in range(framed[0], framed[-1] + 1, 5):
        for side in (-17, 17):
            b.box(x, SEA + 5, z0 + side, x, top, z0 + side, FRAME)
        b.box(x, top, z0 - 17, x, top, z0 + 17, FRAME)
        b.box(x, SEA + 5, z0, x, SEA + 5, z0, LIGHT)
        b.box(x, SEA + 17, z0 + 16, x, SEA + 17, z0 + 16, LIGHT)
    for y in (SEA + 12, SEA + 20):
        for side in (-17, 17):
            b.box(framed[0], y, z0 + side, framed[-1], y, z0 + side, RUST)
    for _ in range(30):
        x = rng.choice([value for value in range(stern + 6, 60) if value not in framed])
        y = rng.randint(SEA + 8, top - 8)
        b.box(x, y, z0 + 17, x + rng.randint(0, 3), y + rng.randint(2, 7), z0 + 17, rng.choice(STAINS))
    for x in range(stern + 8, 44, 8):
        if x not in framed:
            b.box(x, top + 1, z0 + 17, x, top + 1, z0 + 17, LIGHT)
    b.box(-76, top + 1, z0 - 13, -52, top + 16, z0 + 13, "light_gray_terracotta")
    for y in (top + 4, top + 8, top + 12):
        for x in range(-75, -52, 2):
            b.box(x, y, z0 + 13, x, y, z0 + 13, LIGHT)
    b.box(-68, top + 17, z0 - 3, -62, top + 30, z0 + 3, "black_concrete")
    b.box(-68, top + 24, z0 - 3, -62, top + 26, z0 + 3, "red_concrete")
    b.warning_light(-65, top + 31, z0)


def goliath(b, x):
    """A portal crane straddling the ship, the tallest thing in sight."""
    z0, top = -102, 122
    for z in (-130, -76):
        b.box(x, SEA - 4, z, x + 3, top, z + 3, "yellow_concrete")
        for y in range(SEA, SEA + 24, 6):
            b.box(x, y, z, x + 3, y + 2, z + 3, "black_concrete")
    b.box(x - 1, top + 1, -132, x + 4, top + 6, -73, "yellow_concrete")
    b.box(x - 1, top + 1, -132, x + 4, top + 1, -73, "black_concrete")
    b.box(x - 2, top - 3, z0 - 5, x + 5, top, z0 + 5, DARK)
    b.box(x + 1, SEA + 40, z0 - 1, x + 2, top - 4, z0 + 1, "iron_chain")
    b.box(x - 4, SEA + 34, z0 - 3, x + 7, SEA + 39, z0 + 3, "orange_concrete")
    for z in (-132, -73):
        b.warning_light(x + 1, top + 7, z)


def tidal_wheel(b, cx, z, radius):
    """A wheel in the sea that feeds the power station, half under water."""
    cy = SEA + 8
    rows = defaultdict(list)
    for dx, dy in disc(radius, radius - 2.0):
        rows[dy].append(dx)
    for dy, xs in rows.items():
        for x1, x2 in runs(xs):
            b.box(cx + x1, cy + dy, z, cx + x2, cy + dy, z + 2, RUST)
    for angle in range(0, 360, 45):
        for r in range(3, radius - 1):
            x = cx + round(r * math.cos(math.radians(angle)))
            y = cy + round(r * math.sin(math.radians(angle)))
            b.box(x, y, z + 1, x, y, z + 1, FRAME)
    b.box(cx - 2, cy - 2, z - 1, cx + 2, cy + 2, z + 3, DARK)
    b.box(cx, cy, z - 2, cx, cy, z + 4, LIGHT)
    for side in (-1, 1):
        b.box(cx + side * (radius + 2), SEA - 4, z - 3, cx + side * (radius + 5), cy + radius + 2, z + 5, DARK)
    b.box(cx - radius - 5, cy + radius + 3, z - 3, cx + radius + 5, cy + radius + 4, z + 5, FRAME)
    b.warning_light(cx, cy + radius + 5, z + 1)


def smokestack(b, x, z, radius, height):
    base = SEA + 18
    rows = defaultdict(list)
    for dx, dz in disc(radius):
        rows[dz].append(dx)

    def band(y1, y2, block):
        for dz, xs in rows.items():
            for x1, x2 in runs(xs):
                b.box(x + x1, y1, z + dz, x + x2, y2, z + dz, block)

    band(base, base + height, DARK)
    for index in range(3):
        top = base + height - 2 - index * 8
        band(top - 3, top, "white_terracotta" if index % 2 else ANTIFOULING)
    band(base + height + 1, base + height + 1, LIGHT)
    b.warning_light(x, base + height + 2, z)


def power_station(b):
    b.box(-44, SEA - 2, 92, 44, SEA + 18, 122, DARK)
    b.box(-45, SEA + 19, 91, 45, SEA + 19, 123, FRAME)
    for y in (SEA + 6, SEA + 12):
        for x in range(-42, 43, 3):
            b.box(x, y, 92, x + 1, y + 1, 92, LIGHT)
    smokestack(b, -22, 104, 6, 46)
    smokestack(b, 0, 112, 7, 58)
    smokestack(b, 22, 104, 6, 46)
    tidal_wheel(b, -72, 95, 22)
    tidal_wheel(b, 72, 95, 22)


def barge(b, x, z, rng):
    b.box(x - 11, SEA - 1, z - 22, x + 11, SEA + 2, z + 22, DARK)
    for column in (-9, -3, 3):
        for row in range(-20, 14, 7):
            for tier in range(rng.randint(1, 3)):
                y = SEA + 3 + tier * 3
                b.box(x + column, y, z + row, x + column + 4, y + 2, z + row + 5, f"{rng.choice(CONTAINERS)}_concrete")
    b.warning_light(x, SEA + 3, z + 21)


def breakwaters(b):
    """Harbour walls with a light every so often, so the storm's dark has an edge."""
    for x in (-132, 132):
        b.box(x - 3, SEA - 4, -156, x + 3, SEA + 4, 156, "tuff")
        for z in range(-150, 151, 30):
            b.box(x, SEA + 5, z, x, SEA + 10, z, DARK)
            b.box(x, SEA + 11, z, x, SEA + 11, z, LIGHT)
    for z in (-156, 156):
        for x1, x2 in ((-132, -28), (28, 132)):
            b.box(x1, SEA - 4, z - 3, x2, SEA + 4, z + 3, "tuff")
            for x in range(x1 + 12, x2, 30):
                b.box(x, SEA + 5, z, x, SEA + 10, z, DARK)
                b.box(x, SEA + 11, z, x, SEA + 11, z, LIGHT)


def scenery(b):
    """Outside the play area and never reached, so the horizon holds a working yard instead of open water."""
    rng = random.Random(7)
    ship(b, rng)
    goliath(b, -36)
    goliath(b, 40)
    power_station(b)
    barge(b, -116, -58, rng)
    barge(b, 116, 58, rng)
    breakwaters(b)
    for x, z, height in ((-150, -90, 60), (150, 95, 74), (150, -130, 50), (-150, 120, 66)):
        b.box(x - 7, SEA, z - 7, x + 7, SEA + 8, z + 7, DARK)
        b.box(x - 2, SEA + 9, z - 2, x + 2, SEA + height, z + 2, RUST)
        b.box(x - 3, SEA + height, z - 3, x + 3, SEA + height + 1, z + 3, LIGHT)


def forceload_areas(fills):
    """A row of chunks at a time: the command refuses more than 256 chunks, and a fill only reaches loaded ones."""
    rows = defaultdict(set)
    for x1, _, z1, x2, _, z2, _ in fills:
        for cz in range(z1 >> 4, (z2 >> 4) + 1):
            rows[cz].update(range(x1 >> 4, (x2 >> 4) + 1))
    return [f"{start * 16} {cz * 16} {end * 16} {cz * 16}" for cz, row in sorted(rows.items()) for start, end in runs(row)]


def ring(z, radius=7.0):
    """The ring of colour blocks at a post, which the round recolours for whoever holds it."""
    cells = []
    for x in range(-8, 9):
        for dz in range(-8, 9):
            distance = (x ** 2 + dz ** 2) ** 0.5
            if radius - 0.6 <= distance < radius + 0.4:
                cells.append((x, z + dz))
    return cells


def route(faction, z, lane, inward):
    sign = -1 if faction == "kolonien" else 1
    bridge = min(BRIDGES, key=lambda each: abs(each - z))
    road = 32 * sign
    points = [spot(50 * sign, bridge), spot(road, bridge)]
    if bridge != z:
        points.append(spot(road, z + lane))
    points.append(spot(inward * sign, z + lane))
    return dict(approach=points, cover=spot((inward + 5) * sign, z + lane), peek=points[-1],
                retreat=spot(road, z + lane), capture=spot(4 * sign, z + lane // 2))


def arena():
    posts = []
    for key, z in POSTS.items():
        cells = ring(z)
        posts.append(dict(key=key, center=spot(0, z), radius=6.5, label=spot(0, z, SKY + (5 if key != "turbinenhalle" else 4)),
                          spawns=[spot(-5, z - 10), spot(5, z - 10), f"{spot(-5, z + 10)} 180 0", f"{spot(5, z + 10)} 180 0"],
                          indicators=[f"{x} {DECK} {cz}" for x, cz in cells],
                          routes={faction: [route(faction, z, lane, inward) for lane, inward in ((-6, 15), (6, 15))]
                                  for faction in ("kolonien", "hegemonie")}))
    spawns = [-5.5, -1.5, 2.5, 6.5]
    return {"name": "Sturmwerft", "weather": "rain", "time": 12000,
            "play-area": [-97.0, SEA - 6.0, -70.0, 97.0, 128.0, 71.0],
            "relic": f"0.5 {SKY + 5}.2 -65.5", "overview": "0.5 112 62.5 180 40", "lobby": "0.5 90 0.5", "spectator": "24.5 92 30.5 145 35",
            "headquarters": {
                "kolonien": dict(spawns=[f"-81.5 65 {z} 270 0" for z in spawns], zone=[-97.0, 60.0, -30.0, -63.0, 104.0, 31.0]),
                "hegemonie": dict(spawns=[f"82.5 65 {z} 90 0" for z in spawns], zone=[63.0, 60.0, -30.0, 97.0, 104.0, 31.0])},
            "posts": posts, "controls": []}


def solid_at(fills, x, y, z):
    for x1, y1, z1, x2, y2, z2, block in reversed(fills):
        if x1 <= x <= x2 and y1 <= y <= y2 and z1 <= z <= z2:
            return block != "air"
    return False


def blocked_spots(fills, plan):
    """Every place the arena puts a fighter that a wall would catch them in, at the feet or the head."""
    spots = [*(each for hq in plan["headquarters"].values() for each in hq["spawns"])]
    for post in plan["posts"]:
        spots.extend(post["spawns"])
        for routes in post["routes"].values():
            for each in routes:
                spots.extend([*each["approach"], each["cover"], each["retreat"], each["capture"]])
    blocked = []
    for each in dict.fromkeys(spots):
        x, y, z = (math.floor(float(part)) for part in each.split()[:3])
        if solid_at(fills, x, y, z) or solid_at(fills, x, y + 1, z):
            blocked.append(each)
    return blocked


def yaml_scalar(value):
    return json.dumps(value, ensure_ascii=False) if isinstance(value, (str, bool)) else repr(value)


def to_yaml(value, indent=0):
    pad = " " * indent
    lines = []
    if isinstance(value, dict):
        for key, item in value.items():
            if isinstance(item, (dict, list)) and item:
                lines.append(f"{pad}{key}:")
                lines.extend(to_yaml(item, indent + 2))
            else:
                lines.append(f"{pad}{key}: {'[]' if item == [] else yaml_scalar(item)}")
    else:
        for item in value:
            if isinstance(item, dict):
                nested = to_yaml(item, indent + 2)
                lines.append(f"{pad}- {nested[0].strip()}")
                lines.extend(nested[1:])
            else:
                lines.append(f"{pad}- {yaml_scalar(item)}")
    return lines


def main():
    b = Builder()
    for sign in (-1, 1):
        headquarters(b, sign)
    roads(b)
    bridges(b)
    for key, z in POSTS.items():
        post_deck(b, z)
    causeways(b)
    relic_dais(b)
    trockendock(b, POSTS["trockendock"])
    kranbruecke(b, POSTS["kranbruecke"])
    turbinenhalle(b, POSTS["turbinenhalle"])
    for z in POSTS.values():
        for x, cz in ring(z):
            b.box(x, DECK, cz, x, DECK, cz, "white_concrete")
        for x in (-22, 22):
            for edge in (z - 4, z + 4):
                b.stripes(x - 4, edge, x + 4, edge)
            for edge in (x - 4, x + 4):
                b.stripes(edge, z - 3, edge, z + 3)
            b.box(x - 3, DECK, z - 3, x + 3, DECK, z + 3, STEEL)
            b.box(x, DECK, z, x, DECK, z, LIGHT)
    deck_commands = b.commands
    plan = arena()
    blocked = blocked_spots(b.fills, plan)
    if blocked:
        sys.exit(f"Inside a block: {blocked}")
    far = Builder()
    scenery(far)
    functions = OUT / "datapack/data/sturmwerft/function"
    functions.mkdir(parents=True, exist_ok=True)
    (OUT / "datapack/pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 121, "min_format": 121, "max_format": 121, "description": "Sturmwerft"}}))
    (functions / "build.mcfunction").write_text("\n".join(deck_commands) + "\n")
    (functions / "scenery.mcfunction").write_text("\n".join(far.commands) + "\n")
    (OUT / "arena.yml").write_text("# Generated by dev/mapgen/sturmwerft.py; change that and rebuild the map.\n" + "\n".join(to_yaml(plan)) + "\n")
    (OUT / "map.yml").write_text('name: Sturmwerft\nbuilders: [BlockcraftMC]\n')
    (OUT / "forceload.txt").write_text("\n".join(forceload_areas(b.fills + far.fills)) + "\n")
    print(f"{len(deck_commands)} deck commands, {len(far.commands)} scenery commands, {OUT}")


if __name__ == "__main__":
    sys.exit(main())

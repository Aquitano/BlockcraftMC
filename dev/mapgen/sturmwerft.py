#!/usr/bin/env python3
"""Generates Sturmwerft, a stormbound ocean shipyard with elevated domes and spacecraft assembly docks.

Run through `dev/verify/verify.py map`, which starts a bare server over an endless flat sea, runs the functions and saves the
result to worlds/sturmwerft. Everything stands on one deck at y 64, with the headquarters and posts where Sternenfront's
rules expect them: the posts on the north-south centre line with landing grounds at x -22 and 22, and the headquarters at
x -82 and 82.
"""
import json
import math
import random
import sys
from collections import defaultdict, deque
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
PLATE = "light_gray_concrete"
STRIPE = "cyan_concrete"
STEEL = "iron_block"
RUST = "waxed_weathered_copper"
FRAME = "white_concrete"
DARK = "polished_deepslate"
LIGHT = "sea_lantern"
COLD_LIGHT = "sea_lantern"
HULL = "light_gray_concrete"
STAINS = ("andesite", "light_gray_concrete", "gray_concrete", "cyan_terracotta")
RAIL = "iron_bars"
MOST_PER_FILL = 32768


def runs(values):
    """Consecutive stretches of whole numbers, so that a round shape takes a fill per row instead of one per block."""
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

    def ellipse(self, cx, cz, rx, rz, y1, y2, block, hollow=False):
        for dz in range(-rz, rz + 1):
            half = round(rx * math.sqrt(max(0, 1 - (dz / (rz + 0.3)) ** 2)))
            if hollow and abs(dz) < rz:
                for side in (-1, 1):
                    self.box(cx + side * half, y1, cz + dz, cx + side * half, y2, cz + dz, block)
            else:
                self.box(cx - half, y1, cz + dz, cx + half, y2, cz + dz, block)

    def dome(self, cx, cz, rx, rz, base, height):
        for dy in range(height + 1):
            scale = math.sqrt(max(0, 1 - (dy / (height + 0.4)) ** 2))
            self.ellipse(cx, cz, max(1, round(rx * scale)), max(1, round(rz * scale)), base + dy, base + dy, FRAME)

    def rounded_deck(self, x1, z1, x2, z2, radius=5):
        for z in range(z1, z2 + 1):
            dz = max(z1 + radius - z, z - (z2 - radius), 0)
            inset = radius - round(math.sqrt(max(0, radius * radius - dz * dz)))
            self.deck(x1 + inset, z, x2 - inset, z)

    def wear(self, paths, rng):
        floor = layer(self.fills, DECK) - layer(self.fills, SKY)
        painted = {}
        for x1, z1, x2, z2 in paths:
            distance = max(abs(x2 - x1), abs(z2 - z1))
            drift = 0
            for step in range(distance + 1):
                x = round(x1 + (x2 - x1) * step / max(1, distance))
                z = round(z1 + (z2 - z1) * step / max(1, distance))
                drift = max(-2, min(2, drift + rng.choice((-1, 0, 0, 1))))
                for side in range(-2, 3):
                    dx, dz = (0, drift + side) if abs(x2 - x1) > abs(z2 - z1) else (drift + side, 0)
                    cell = x + dx, z + dz
                    if cell in floor and rng.random() < (0.7 if side == 0 else 0.32):
                        painted[cell] = rng.choices(STAINS, (6, 7, 2, 1))[0]
        for (x, z), block in sorted(painted.items()):
            self.box(x, DECK, z, x, DECK, z, block)

    def service_unit(self, x, z, facing):
        self.box(x - 1, SKY, z - 1, x + 1, SKY + 1, z + 1, "gray_concrete")
        self.box(x - 1, SKY + 2, z - 1, x + 1, SKY + 2, z + 1, "smooth_quartz_slab[type=bottom]")
        self.box(x + facing, SKY + 1, z, x + facing, SKY + 1, z, "cyan_stained_glass")
        self.box(x, SKY, z - 2, x, SKY, z - 2, "waxed_oxidized_copper")
        self.box(x, SKY + 1, z - 2, x, SKY + 1, z - 2, "lightning_rod[facing=up]")

    def maintenance_droid(self, x, z):
        self.box(x - 1, SKY, z, x + 1, SKY, z, "polished_blackstone_slab[type=bottom]")
        self.box(x, SKY, z, x, SKY + 1, z, "smooth_quartz")
        self.box(x, SKY + 2, z, x, SKY + 2, z, "cyan_stained_glass")
        self.box(x - 1, SKY + 1, z, x - 1, SKY + 1, z, "end_rod[facing=west]")

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
    b.rounded_deck(min(near, far), -30, max(near, far), 31, 4)
    for z in BRIDGES:
        b.deck(near, z - 3, 69 * sign, z + 3)
    b.legs(min(near, far), -30, max(near, far), 31)
    cx = 83 * sign
    b.ellipse(cx, 0, 14, 27, SKY, SKY + 8, FRAME, hollow=True)
    b.ellipse(cx, 0, 14, 27, SKY + 5, SKY + 6, "cyan_stained_glass", hollow=True)
    b.dome(cx, 0, 15, 28, SKY + 9, 9)
    b.box(67 * sign, SKY, -21, 85 * sign, SKY + 7, 21, "air")
    for z in (-22, 22):
        b.box(69 * sign, SKY, z, 72 * sign, SKY + 7, z, FRAME)
        b.box(70 * sign, SKY + 2, z - 1, 70 * sign, SKY + 6, z + 1, COLD_LIGHT)
    colour = "red" if sign > 0 else "light_blue"
    b.box(69 * sign, SKY + 8, -21, 72 * sign, SKY + 8, 21, "smooth_quartz")
    b.box(69 * sign, SKY + 8, -7, 69 * sign, SKY + 8, 7, f"{colour}_concrete")
    b.ellipse(cx, 0, 7, 8, SKY + 18, SKY + 21, "smooth_quartz")
    b.ellipse(cx, 0, 7, 8, SKY + 19, SKY + 20, "cyan_stained_glass", hollow=True)
    b.dome(cx, 0, 8, 9, SKY + 22, 5)
    b.box(cx, SKY + 27, 0, cx, SKY + 38, 0, "end_rod")
    for z in (-24, 24):
        b.service_unit(75 * sign, z, -sign)
    b.maintenance_droid(74 * sign, -17)
    b.crate(88 * sign, 15, 3, 5, 2, "gray")
    for z in (-29, 30):
        b.rail(68 * sign, z, 92 * sign, z)
    edges = sorted(z for bridge in BRIDGES for z in (bridge - 3, bridge + 3))
    previous = -27
    for index in range(0, len(edges), 2):
        b.rail(near, previous, near, edges[index])
        previous = edges[index + 1]
    b.rail(near, previous, near, 28)


def bridges(b):
    for sign in (-1, 1):
        road = ROADS[0] if sign < 0 else ROADS[1]
        for z in BRIDGES:
            near, far = (road + 4 * sign), 62 * sign
            b.deck(near, z - 2, far, z + 2, PLATE)
            b.box(near, DECK, z - 2, far, DECK, z - 2, STRIPE)
            b.box(near, DECK, z + 2, far, DECK, z + 2, STRIPE)
            b.rail(near, z - 3, far, z - 3)
            b.rail(near, z + 3, far, z + 3)
            for x in range(min(near, far), max(near, far), 10):
                b.box(x, SEA - 4, z - 1, x + 1, DECK - 3, z, RUST)
            b.stripes(near, z - 2, near, z + 2)
            b.stripes(far, z - 2, far, z + 2)
            for x in (42, 58):
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
    b.rounded_deck(-28, z - 14, 28, z + 14)
    b.legs(-28, z - 14, 28, z + 14)
    for x1, x2 in ((-23, -4), (4, 23)):
        b.rail(x1, z - 14, x2, z - 14)
        b.rail(x1, z + 14, x2, z + 14)
        b.stripes(x1, z - 13, x2, z - 13)
        b.stripes(x1, z + 13, x2, z + 13)
    for sign in (-1, 1):
        for edge in (z - 13, z + 13):
            b.mast(23 * sign, edge, 8, -sign)


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
    for rib_z in (z - 12, z + 12):
        for x in range(-14, 15):
            height = round(8 + 6 * math.sqrt(max(0, 1 - (x / 14) ** 2)))
            b.box(x, SKY + height, rib_z, x, SKY + height + 1, rib_z, FRAME)
        for x in (-14, 14):
            b.box(x, SKY, rib_z, x, SKY + 8, rib_z, FRAME)
        b.box(-8, SKY + 13, rib_z, 8, SKY + 13, rib_z, COLD_LIGHT)
    for x, dz in ((-11, -6), (11, 6), (-11, 6), (11, -6)):
        b.service_unit(x, z + dz, -1 if x < 0 else 1)
    b.wall(-4, z - 12, 4, z - 12, 2, "smooth_quartz")
    b.wall(-4, z + 12, 4, z + 12, 2, "smooth_quartz")
    for x in (-14, 14):
        b.box(x, SKY + 9, z - 11, x, SKY + 10, z + 11, "gray_concrete")
        b.box(x, SKY + 10, z - 2, x, SKY + 11, z + 2, "cyan_stained_glass")
    for x in (-7, 7):
        b.box(x, SKY, z - 10, x + 1, SKY + 1, z - 9, "waxed_exposed_copper")


def kranbruecke(b, z):
    for x in (-14, 14):
        for leg_z in (z - 11, z + 11):
            b.box(x, SKY, leg_z, x + 1, SKY + 15, leg_z + 1, FRAME)
            b.box(x, SKY + 3, leg_z, x + 1, SKY + 7, leg_z, "cyan_concrete")
        b.box(x, SKY + 16, z - 11, x + 1, SKY + 17, z + 12, "smooth_quartz")
    for beam in (z - 3, z + 3):
        b.box(-14, SKY + 20, beam, 15, SKY + 21, beam + 1, FRAME)
        b.box(-13, SKY + 19, beam, 14, SKY + 19, beam, DARK)
        b.box(-13, SKY + 22, beam, 14, SKY + 22, beam, "cyan_concrete")
    b.box(-13, SKY + 20, z, 14, SKY + 20, z, DARK)
    for x in (-13, 14):
        b.box(x, SKY + 20, z - 3, x, SKY + 20, z + 3, DARK)
    for x, dz in ((-11, -6), (11, 6), (-11, 6), (11, -6)):
        b.service_unit(x, z + dz, -1 if x < 0 else 1)
    b.box(-13, SKY + 13, z + 7, -9, SKY + 16, z + 10, "smooth_quartz")
    b.box(-13, SKY + 14, z + 7, -9, SKY + 15, z + 10, "cyan_stained_glass")
    b.box(-12, SKY + 14, z + 8, -10, SKY + 14, z + 9, COLD_LIGHT)
    b.warning_light(-14, SKY + 18, z + 12)
    b.warning_light(15, SKY + 18, z - 11)


def turbinenhalle(b, z):
    b.ellipse(0, z, 14, 9, SKY, SKY + 7, "smooth_quartz", hollow=True)
    b.ellipse(0, z, 14, 9, SKY + 4, SKY + 5, "cyan_stained_glass", hollow=True)
    b.dome(0, z, 14, 10, SKY + 8, 7)
    b.box(-15, SKY, z - 3, 15, SKY + 4, z + 3, "air")
    b.box(-6, SKY, z - 10, 6, SKY + 4, z + 10, "air")
    for x, dz in ((-11, -6), (11, 6), (-11, 6), (11, -6)):
        b.service_unit(x, z + dz, -1 if x < 0 else 1)
    for x in (-7, 7):
        for dz in (-3, 3):
            b.box(x, SKY + 7, z + dz, x, SKY + 7, z + dz, COLD_LIGHT)
    b.ellipse(0, z, 3, 3, SKY + 15, SKY + 18, "smooth_quartz")
    b.box(0, SKY + 19, z, 0, SKY + 27, z, "end_rod")


def ship(b):
    z0, stern, bow = -110, -77, 81
    for x in range(stern, bow + 1):
        half = max(1, round(29 * (bow - x) / (bow - stern)))
        depth = max(2, round(8 * (bow - x) / (bow - stern)))
        if -20 <= x <= 22:
            if x % 5 == 0:
                b.box(x, 78 - depth, z0 - half, x, 79, z0 - half, FRAME)
                b.box(x, 78 - depth, z0 + half, x, 79, z0 + half, FRAME)
                b.box(x, 79, z0 - half, x, 79, z0 + half, FRAME)
            b.box(x, 78 - depth, z0 - half, x, 78 - depth, z0 + half, DARK)
            b.box(x, 76, z0 - 2, x, 78, z0 + 2, "cyan_concrete")
        else:
            b.box(x, 78 - depth, z0 - half, x, 78, z0 + half, HULL)
            b.box(x, 79, z0 - half, x, 80, z0 + half, "smooth_stone")
            if half > 4:
                b.box(x, 81, z0 - half + 4, x, 82, z0 + half - 4, HULL)
            if x % 11 < 2:
                b.box(x, 79, z0 - half, x, 79, z0 - half, "cyan_concrete")
                b.box(x, 79, z0 + half, x, 79, z0 + half, "cyan_concrete")
    b.box(-65, 83, z0 - 13, -32, 87, z0 + 13, HULL)
    b.box(-59, 88, z0 - 9, -39, 91, z0 + 9, "smooth_quartz")
    b.box(-56, 92, z0 - 5, -48, 98, z0 + 5, HULL)
    b.box(-61, 99, z0 - 16, -44, 101, z0 + 16, "smooth_quartz")
    b.box(-61, 100, z0 + 16, -44, 100, z0 + 16, "cyan_stained_glass")
    for z in (z0 - 18, z0, z0 + 18):
        b.box(stern - 5, 71, z - 4, stern - 1, 80, z + 4, DARK)
        b.box(stern - 6, 73, z - 3, stern - 6, 78, z + 3, "cyan_concrete")
        b.box(stern - 7, 74, z - 2, stern - 7, 77, z + 2, COLD_LIGHT)
    for x in (-62, -28, 28, 57):
        b.box(x - 2, SEA - 4, z0 - 22, x + 2, 66, z0 + 22, FRAME)
        b.box(x - 3, 67, z0 - 22, x + 3, 69, z0 + 22, DARK)
    b.box(-82, 62, -79, 82, 64, -74, FRAME)
    b.box(-82, 65, -79, 82, 65, -79, RAIL)
    b.box(-82, 65, -74, 82, 65, -74, RAIL)
    for x in (-61, -18, 34, 67):
        b.box(x, 65, -77, x + 3, 67, -75, "gray_concrete")
        b.box(x, 68, -77, x + 3, 68, -75, "waxed_weathered_copper")


def goliath(b, x):
    top = 116
    for z in (-145, -76):
        b.box(x, SEA - 4, z, x + 3, top, z + 3, FRAME)
        b.box(x, 72, z, x + 3, 80, z + 3, "cyan_concrete")
        b.box(x, 95, z, x, 108, z, COLD_LIGHT)
    b.box(x - 1, top + 1, -147, x + 4, top + 5, -73, "smooth_quartz")
    b.box(x - 1, top + 1, -147, x + 4, top + 1, -73, DARK)
    b.box(x - 2, top - 3, -115, x + 5, top, -105, DARK)
    b.box(x + 1, 99, -111, x + 2, top - 4, -109, "iron_chain")
    b.box(x - 4, 94, -113, x + 7, 98, -107, "gray_concrete")
    for z in (-147, -73):
        b.warning_light(x + 1, top + 6, z)


def ocean_tower(b, x, z, radius, floor, height):
    b.ellipse(x, z, 4, 4, SEA - 5, floor - 5, "smooth_quartz")
    for dy in range(4):
        b.ellipse(x, z, radius - 6 + dy * 2, radius - 6 + dy * 2, floor - 4 + dy, floor - 4 + dy, FRAME)
    b.ellipse(x, z, radius + 2, radius + 2, floor, floor + 1, "smooth_quartz")
    b.ellipse(x, z, radius, radius, floor + 2, floor + height, FRAME)
    b.ellipse(x, z, radius, radius, floor + 3, floor + 5, "cyan_stained_glass", hollow=True)
    b.ellipse(x, z, radius - 1, radius - 1, floor + 4, floor + 4, COLD_LIGHT, hollow=True)
    b.dome(x, z, radius + 1, radius + 1, floor + height + 1, max(4, radius // 2))
    top = floor + height + max(4, radius // 2) + 2
    b.ellipse(x, z, 3, 3, top, top + 3, FRAME)
    b.box(x, top + 4, z, x, top + 17, z, "end_rod")


def skyline(b):
    for x, z, radius, floor, height in ((0, 110, 25, 79, 12), (-66, 103, 18, 72, 9),
                                       (68, 109, 20, 77, 10), (-137, 42, 20, 80, 10),
                                       (139, -20, 18, 88, 9), (-141, -46, 15, 93, 8),
                                       (142, 115, 15, 101, 8), (-104, 148, 17, 95, 10)):
        ocean_tower(b, x, z, radius, floor, height)
    for x1, x2, z, y in ((-67, -24, 106, 80), (24, 68, 110, 80)):
        b.box(x1, y, z - 2, x2, y + 1, z + 2, FRAME)
        b.box(x1, y + 2, z - 3, x2, y + 2, z - 3, RAIL)
        b.box(x1, y + 2, z + 3, x2, y + 2, z + 3, RAIL)
        b.box(x1, y, z, x2, y, z, COLD_LIGHT)


def shuttle(b, x, z, sign):
    b.ellipse(x, z, 18, 22, 63, 65, "smooth_quartz")
    b.ellipse(x, z, 4, 4, SEA - 5, 62, FRAME)
    b.ellipse(x, z, 16, 20, 66, 66, "cyan_concrete", hollow=True)
    for dz in (-5, 5):
        b.box(x - 7, 67, z + dz, x + 7, 67, z + dz, DARK)
    for dx in range(-10, 11):
        half = max(1, 4 - max(0, dx * sign - 4) // 2)
        b.box(x + dx, 69, z - half, x + dx, 73, z + half, "smooth_quartz")
    b.box(x + sign * 7, 72, z - 2, x + sign * 10, 73, z + 2, "black_stained_glass")
    b.box(x - 5, 71, z - 11, x + 1, 72, z + 11, "light_gray_concrete")
    b.box(x - sign * 6, 74, z, x - sign * 3, 82, z, "smooth_quartz")
    for side in (-1, 1):
        b.box(x - sign * 7, 70, z + side * 9, x + sign * 3, 73, z + side * 9, DARK)
        b.box(x - sign * 8, 71, z + side * 9, x - sign * 8, 72, z + side * 9, COLD_LIGHT)
    for dz in (-16, 16):
        b.box(x - 11, 67, z + dz, x - 8, 69, z + dz + 2, "gray_concrete")
        b.box(x - 10, 70, z + dz, x - 9, 70, z + dz + 2, "waxed_weathered_copper")


def scenery(b):
    ship(b)
    goliath(b, -31)
    goliath(b, 35)
    skyline(b)
    shuttle(b, -118, -84, 1)
    shuttle(b, 118, 60, -1)
    for x, z in ((-165, -120), (165, -140), (-164, 130), (160, 155)):
        b.ellipse(x, z, 5, 5, SEA - 3, SEA + 1, DARK)
        b.box(x, SEA + 2, z, x, SEA + 15, z, "smooth_quartz")
        b.box(x, SEA + 16, z, x, SEA + 17, z, COLD_LIGHT)


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


def route(faction, z, lane):
    sign = -1 if faction == "kolonien" else 1
    bridge = min(BRIDGES, key=lambda each: abs(each - z))
    road = 32 * sign
    points = [spot(50 * sign, bridge), spot(road, bridge)]
    if bridge != z:
        points.append(spot(road, z + lane))
    points.append(spot(14 * sign, z + lane))
    return dict(approach=points, cover=points[-1], peek=spot(14 * sign, z + lane // 2),
                retreat=spot(15 * sign, z + lane), capture=spot(4 * sign, z + lane // 2))


def arena():
    posts = []
    for key, z in POSTS.items():
        cells = ring(z)
        posts.append(dict(key=key, center=spot(0, z), radius=6.5, label=spot(0, z, SKY + (5 if key != "turbinenhalle" else 4)),
                          spawns=[spot(-5, z - 10), spot(5, z - 10), f"{spot(-5, z + 10)} 180 0", f"{spot(5, z + 10)} 180 0"],
                          indicators=[f"{x} {DECK} {cz}" for x, cz in cells],
                          routes={faction: [route(faction, z, lane) for lane in (-6, 6)]
                                  for faction in ("kolonien", "hegemonie")}))
    spawns = [-5.5, -1.5, 2.5, 6.5]
    return {"name": "Sturmwerft", "weather": "rain", "time": 13000,
            "play-area": [-97.0, SEA - 6.0, -70.0, 97.0, 128.0, 71.0],
            "world-border-size": 384.0,
            "relic": f"0.5 {SKY + 5}.2 -65.5", "overview": "0.5 112 62.5 180 40", "lobby": "0.5 90 0.5", "spectator": "24.5 92 30.5 145 35",
            "headquarters": {
                "kolonien": dict(spawns=[f"-81.5 65 {z} 270 0" for z in spawns], zone=[-97.0, 60.0, -30.0, -63.0, 104.0, 31.0]),
                "hegemonie": dict(spawns=[f"82.5 65 {z} 90 0" for z in spawns], zone=[63.0, 60.0, -30.0, 97.0, 104.0, 31.0])},
            "posts": posts, "controls": [],
            "environment": {
                "steam": [spot(x, z - 8, SKY + 2) for z in POSTS.values() for x in (-11, 11)],
                "sparks": [spot(x, z - 6, SKY + 1.5) for z in POSTS.values() for x in (-9, 13)],
                "crane": {"from": "-10.5 84 0.5", "to": "10.5 84 0.5"}}}


def solid_at(fills, x, y, z):
    for x1, y1, z1, x2, y2, z2, block in reversed(fills):
        if x1 <= x <= x2 and y1 <= y <= y2 and z1 <= z <= z2:
            return block != "air"
    return False


def layer(fills, y):
    cells = set()
    for x1, y1, z1, x2, y2, z2, block in fills:
        if y1 <= y <= y2:
            changed = {(x, z) for x in range(x1, x2 + 1) for z in range(z1, z2 + 1)}
            if block == "air":
                cells.difference_update(changed)
            else:
                cells.update(changed)
    return cells


def reachable(cells, start):
    reached = {start} if start in cells else set()
    pending = deque(reached)
    while pending:
        x, z = pending.popleft()
        for cell in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
            if cell in cells and cell not in reached:
                reached.add(cell)
                pending.append(cell)
    return reached


def validate(fills, plan):
    blocked = blocked_spots(fills, plan)
    if blocked:
        sys.exit(f"Unsupported or obstructed fighter positions: {blocked}")
    floor = layer(fills, DECK)
    walkable = floor - layer(fills, SKY) - layer(fills, SKY + 1)
    connected = reachable(walkable, (-82, -6))
    for post in plan["posts"]:
        positions = [post["center"], *post["spawns"]]
        for routes in post["routes"].values():
            for route in routes:
                positions.extend([*route["approach"], route["cover"], route["peek"], route["retreat"], route["capture"]])
        for position in positions:
            x, _, z = (math.floor(float(part)) for part in position.split()[:3])
            if (x, z) not in connected:
                sys.exit(f"Unreachable fighter position: {position}")
    for headquarters in plan["headquarters"].values():
        for position in headquarters["spawns"]:
            x, _, z = (math.floor(float(part)) for part in position.split()[:3])
            if (x, z) not in connected:
                sys.exit(f"Unreachable headquarters spawn: {position}")
    driveable = {cell for cell in walkable if all((cell[0] + dx, cell[1] + dz) in walkable
                 for dx in (-1, 0, 1) for dz in (-1, 0, 1))}
    for sign in (-1, 1):
        driven = reachable(driveable, (82 * sign, 10))
        for z in (10, 16, 22):
            if (82 * sign, z) not in driven:
                sys.exit(f"Vehicle headquarters pad obstructed: {(82 * sign, z)}")
        for x in (-22, 22):
            for z in POSTS.values():
                if (x, z) not in driven:
                    sys.exit(f"Vehicle cannot reach landing ground {(x, z)} from headquarters {sign}")
    for x in (-22, 22):
        for z in POSTS.values():
            if any((x + dx, z + dz) not in floor for dx in range(-3, 4) for dz in range(-3, 4)):
                sys.exit(f"Incomplete landing ground: {(x, z)}")
            for x1, y1, z1, x2, y2, z2, block in fills:
                if y2 < SKY or block == "air":
                    continue
                dx = max(x1 - (x + 0.5), (x + 0.5) - (x2 + 1), 0)
                dz = max(z1 - (z + 0.5), (z + 0.5) - (z2 + 1), 0)
                if dx * dx + dz * dz < 6.5 ** 2:
                    for bx in range(max(x1, x - 7), min(x2, x + 7) + 1):
                        for bz in range(max(z1, z - 7), min(z2, z + 7) + 1):
                            dx = max(bx - x - 0.5, x - bx - 0.5, 0)
                            dz = max(bz - z - 0.5, z - bz - 0.5, 0)
                            if dx * dx + dz * dz < 6.5 ** 2:
                                for y in range(max(SKY, y1), y2 + 1):
                                    if solid_at(fills, bx, y, bz):
                                        sys.exit(f"Landing clearance blocked at {(bx, y, bz)} near {(x, z)}")
    for kind in ("steam", "sparks"):
        for position in plan["environment"][kind]:
            x, y, z = (math.floor(float(part)) for part in position.split()[:3])
            if solid_at(fills, x, y, z):
                sys.exit(f"Buried {kind} emitter: {position}")
    endpoints = [[float(part) for part in plan["environment"]["crane"][key].split()] for key in ("from", "to")]
    for x in range(math.floor(min(point[0] for point in endpoints) - 1.2), math.ceil(max(point[0] for point in endpoints) + 1.2)):
        for z in range(math.floor(min(point[2] for point in endpoints) - 0.8), math.ceil(max(point[2] for point in endpoints) + 0.8)):
            for y in range(math.floor(min(point[1] for point in endpoints) - 9.62), math.ceil(max(point[1] for point in endpoints) + 0.65)):
                if solid_at(fills, x, y, z):
                    sys.exit(f"Crane swept volume blocked: {(x, y, z)}")
    print(f"Validated {len(connected)} connected walkable cells, both vehicle approaches and six clear landing cylinders")


def blocked_spots(fills, plan):
    """Fighter positions need a supporting block and room for the feet and head."""
    spots = [*(each for hq in plan["headquarters"].values() for each in hq["spawns"])]
    for post in plan["posts"]:
        spots.extend(post["spawns"])
        for routes in post["routes"].values():
            for each in routes:
                spots.extend([*each["approach"], each["cover"], each["peek"], each["retreat"], each["capture"]])
    blocked = []
    for each in dict.fromkeys(spots):
        x, y, z = (math.floor(float(part)) for part in each.split()[:3])
        if not solid_at(fills, x, y - 1, z) or solid_at(fills, x, y, z) or solid_at(fills, x, y + 1, z):
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
    paths = [(road, -60, road, 60) for road in ROADS]
    paths.extend((sign * 32, z, sign * 83, z) for sign in (-1, 1) for z in BRIDGES)
    paths.extend((-27, z + lane, 27, z + lane) for z in POSTS.values() for lane in (-6, 0, 6))
    paths.extend((0, -57, 0, 57) for _ in range(2))
    b.wear(paths, random.Random(731))
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
    far = Builder()
    scenery(far)
    validate(b.fills + far.fills, plan)
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

#!/usr/bin/env python3
"""Generates Sturmwerft, a stormbound ocean shipyard with elevated domes and spacecraft assembly docks.

Run through `dev/verify/verify.py map`, which starts a bare server over an endless flat sea, runs the functions and saves the
result to worlds/sturmwerft. The combat deck stands at y 64, the occupied southern domes at y 80 and the northern ship
at y 82. The headquarters, posts and six open landing grounds retain their original positions.
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
RAIL = "iron_bars"
MOST_PER_FILL = 32768
YARD_PROP_BOUNDS = {
    "console": (-0.6875, 0, -0.4375, 0.6875, 1.3897287161694392, 0.6203725392145505),
    "power_coupler": (-0.5, 0, -0.4375, 0.5, 1.375, 0.75),
    "engine_cradle": (-0.7625, 0, -0.875, 0.7125, 1.375, 0.875),
    "cargo_stack": (-0.8125, 0, -0.6875, 0.8125, 1.39375, 0.125),
    "repair_droid": (-0.6875, 0, -0.3125, 0.58125, 1.41875, 0.625),
    "wall_panel": (-0.6875, 0, -0.09375, 0.6875, 1.375, 0.075),
}


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

    def wear(self, paths, rng, y=DECK):
        floor = layer(self.fills, y) - layer(self.fills, y + 1)
        painted = {}
        for x1, z1, x2, z2 in paths:
            distance = max(abs(x2 - x1), abs(z2 - z1))
            along_x = abs(x2 - x1) > abs(z2 - z1)
            repair = rng.randrange(3, max(4, distance - 3))
            for step in range(distance + 1):
                x = round(x1 + (x2 - x1) * step / max(1, distance))
                z = round(z1 + (z2 - z1) * step / max(1, distance))
                for side in (-1, 1):
                    dx, dz = (0, side) if along_x else (side, 0)
                    cell = x + dx, z + dz
                    if cell in floor and step % 13 not in (0, 1, 2):
                        painted[cell] = "andesite" if step % 13 < 8 else "light_gray_concrete"
                if repair <= step < repair + 3:
                    for side in range(-2, 3):
                        dx, dz = (0, side) if along_x else (side, 0)
                        cell = x + dx, z + dz
                        if cell in floor:
                            painted[cell] = "polished_andesite" if side in (-2, 2) else "smooth_stone"
        for (x, z), block in sorted(painted.items()):
            self.box(x, y, z, x, y, z, block)

    def service_unit(self, x, z, facing, floor=DECK):
        y = floor + 1
        self.box(x - 1, y, z - 1, x + 1, y + 1, z + 1, "gray_concrete")
        self.box(x - 1, y + 2, z - 1, x + 1, y + 2, z + 1, "smooth_quartz_slab[type=bottom]")
        self.box(x + facing, y + 1, z, x + facing, y + 1, z, "cyan_stained_glass")
        self.box(x, y, z - 2, x, y, z - 2, "waxed_oxidized_copper")
        self.box(x, y + 1, z - 2, x, y + 1, z - 2, "lightning_rod[facing=up]")

    def workbench(self, x, z, floor):
        self.box(x - 1, floor + 1, z - 1, x + 1, floor + 1, z + 1, "smooth_quartz")
        self.box(x, floor + 1, z, x, floor + 1, z, COLD_LIGHT)

    def maintenance_droid(self, x, z, floor=DECK):
        y = floor + 1
        self.box(x - 1, y, z, x + 1, y, z, "polished_blackstone_slab[type=bottom]")
        self.box(x, y, z, x, y + 1, z, "smooth_quartz")
        self.box(x, y + 2, z, x, y + 2, z, "cyan_stained_glass")
        self.box(x - 1, y + 1, z, x - 1, y + 1, z, "end_rod[facing=west]")

    def warning_light(self, x, y, z):
        self.box(x, y, z, x, y, z, "redstone_block")
        self.box(x, y + 1, z, x, y + 1, z, "redstone_lamp[lit=true]")

    def mast(self, x, z, height, facing_x):
        """A floodlight on a pole, its head leaning over the deck."""
        self.box(x, SKY, z, x, SKY + height, z, DARK)
        self.box(x, SKY + height + 1, z, x + facing_x * 2, SKY + height + 1, z, FRAME)
        self.box(x + facing_x, SKY + height, z, x + facing_x * 2, SKY + height, z, LIGHT)
        self.warning_light(x, SKY + height + 2, z)

    def crate(self, x, z, width, depth, height, colour, floor=DECK):
        self.box(x, floor + 1, z, x + width - 1, floor + height, z + depth - 1, f"{colour}_concrete")
        self.box(x, floor + height, z, x + width - 1, floor + height, z + depth - 1, f"{colour}_terracotta")
        for edge in (x, x + width - 1):
            self.box(edge, floor + 1, z, edge, floor + height, z + depth - 1, DARK)
        if width > 2:
            self.box(x + 1, floor + 1, z, x + width - 2, floor + 1, z, "waxed_weathered_cut_copper")
            self.box(x + 1, floor + height, z, x + width - 2, floor + height, z, "iron_block")

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


def ocean_tower(b, x, z, radius, floor, height, occupied=False):
    b.ellipse(x, z, 4, 4, SEA - 5, floor - 5, "smooth_quartz")
    for dy in range(4):
        b.ellipse(x, z, radius - 6 + dy * 2, radius - 6 + dy * 2, floor - 4 + dy, floor - 4 + dy, FRAME)
    b.ellipse(x, z, radius + 2, radius + 2, floor, floor if occupied else floor + 1, "smooth_quartz")
    b.ellipse(x, z, radius, radius, floor + (1 if occupied else 2), floor + height, FRAME, hollow=occupied)
    b.ellipse(x, z, radius, radius, floor + 3, floor + 5, "cyan_stained_glass", hollow=True)
    light_y = floor + (9 if occupied else 4)
    b.ellipse(x, z, radius - 1, radius - 1, light_y, light_y, DARK if occupied else COLD_LIGHT, hollow=True)
    if occupied:
        for dz in range(-radius + 4, radius - 3, 7):
            half = round((radius - 1) * math.sqrt(1 - (dz / (radius - 0.7)) ** 2))
            for sign in (-1, 1):
                b.box(x + sign * half, light_y, z + dz, x + sign * half, light_y, z + dz + 1,
                      "shroomlight" if x < 0 else COLD_LIGHT)
    b.dome(x, z, radius + 1, radius + 1, floor + height + 1, max(4, radius // 2))
    top = floor + height + max(4, radius // 2) + 2
    b.ellipse(x, z, 3, 3, top, top + 3, FRAME)
    b.box(x, top + 4, z, x, top + 17, z, "end_rod")


def skyline(b):
    for x, z, radius, floor, height in ((0, 110, 25, 80, 10), (-66, 103, 18, 80, 10),
                                       (68, 109, 20, 80, 10), (-137, 42, 20, 80, 10),
                                       (139, -20, 18, 88, 9), (-141, -46, 15, 93, 8),
                                       (142, 115, 15, 101, 8), (-104, 148, 17, 95, 10)):
        ocean_tower(b, x, z, radius, floor, height, occupied=(x, z) in {(-66, 103), (0, 110), (68, 109)})


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


def upper_deck(b, x1, z1, x2, z2, floor):
    b.box(x1, floor - 2, z1, x2, floor - 1, z2, FRAME)
    b.box(x1, floor, z1, x2, floor, z2, PLATE)


def stairway(b, x, z, floor, rise, direction):
    facing = "south" if direction > 0 else "north"
    for step in range(rise):
        y, at = floor + step, z + direction * step
        b.box(x - 2, y + 1, at, x + 2, y + 4, at, "air")
        b.box(x - 2, floor - 2, at, x + 2, y - 1, at, FRAME)
        b.box(x - 2, y, at, x + 2, y, at, f"smooth_quartz_stairs[facing={facing},half=bottom,shape=straight]")
        for edge in (x - 3, x + 3):
            b.box(edge, y - 1, at, edge, y, at, DARK)
            b.box(edge, y + 1, at, edge, y + 1, at, RAIL)
            if floor == 65 and direction > 0 and step % 4 == 1:
                b.box(edge, y, at, edge, y, at, COLD_LIGHT)
    for at, y in ((z - direction, floor - 1), (z + direction * rise, floor + rise - 1)):
        upper_deck(b, x - 2, at, x + 2, at, y)
        b.box(x - 2, y + 1, at, x + 2, y + 4, at, "air")


def ship_room(b, x1, z1, x2, z2):
    upper_deck(b, x1, z1, x2, z2, 82)
    b.box(x1, 83, z1, x2, 90, z2, FRAME)
    b.box(x1, 85, z1, x2, 87, z2, "cyan_stained_glass")
    b.box(x1 + 1, 83, z1 + 1, x2 - 1, 89, z2 - 1, "air")
    for x in range(x1 + 4, x2 - 2, 8):
        b.box(x, 89, z1 + 2, x, 89, z2 - 2, DARK)
        b.box(x, 89, z1 + 3, x, 89, z1 + 5, COLD_LIGHT)
        b.box(x, 89, z2 - 5, x, 89, z2 - 3, COLD_LIGHT)


def northern_dock(b):
    for x in ROADS:
        b.deck(x - 4, -78, x + 4, -61, PLATE)
        b.box(x - 2, 65, -79, x + 2, 68, -61, "air")
    for x1, z1, x2, z2 in ((-72, -130, -27, -92), (-33, -124, 25, -96), (20, -124, 66, -96)):
        upper_deck(b, x1, z1, x2, z2, 82)
        b.box(x1, 83, z1, x2, 88, z2, "air")
    ship_room(b, -64, -124, -32, -96)
    ship_room(b, 20, -120, 62, -100)
    ship_room(b, -33, -114, 21, -106)
    for x1, x2, z1, z2 in ((-62, 60, -113, -107), (-35, -29, -108, -92),
                            (29, 35, -108, -96), (-14, -10, -122, -96), (10, 14, -122, -96),
                            (-12, 12, -122, -118)):
        upper_deck(b, x1, z1, x2, z2, 82)
        b.box(x1, 83, z1, x2, 87, z2, "air")
    for x in (-12, 12):
        b.deck(x - 2, -79, x + 2, -58, PLATE)
        b.box(x - 2, 65, -79, x + 2, 68, -58, "air")
    for x in (-72, 66):
        b.box(x, 83, -124, x, 83, -97, RAIL)
    b.box(-71, 83, -130, -28, 83, -130, RAIL)
    b.box(-71, 83, -92, -36, 83, -92, RAIL)
    b.box(20, 83, -124, 65, 83, -124, RAIL)
    b.box(39, 83, -96, 65, 83, -96, RAIL)
    for x in (-69, 64):
        b.warning_light(x, 84, -122)
    for x, z in ((-57, -117), (-43, -117), (-54, -102), (29, -116), (48, -115), (47, -104)):
        b.workbench(x, z, 82)
    for x, z, width, depth, height in ((-49, -120, 3, 3, 1), (-61, -104, 3, 2, 1),
                                      (35, -104, 5, 2, 1), (54, -116, 3, 4, 1), (22, -104, 3, 2, 2)):
        b.crate(x, z, width, depth, height, "gray", 82)
    for x in (-59, -45):
        b.box(x, 83, -123, x + 1, 88, -122, DARK)
        b.box(x, 83, -97, x + 1, 88, -98, DARK)
    for x, z in ((-51, -105), (-43, -120)):
        b.box(x, 83, z, x + 3, 88, z + 2, DARK)
        b.box(x, 84, z, x + 3, 86, z, "waxed_weathered_copper")
        b.box(x + 1, 85, z, x + 2, 87, z, "cyan_stained_glass")
        b.box(x + 1, 88, z, x + 2, 88, z + 2, COLD_LIGHT)
    for x in (38, 43):
        b.box(x, 83, -118, x, 88, -115, FRAME)
        b.stripes(x, -118, x, -115, 83)
    b.box(38, 89, -118, 43, 89, -115, DARK)
    b.crate(40, -117, 2, 2, 2, "light_gray", 82)
    b.box(0, 83, -117, 0, 86, -104, DARK)
    b.box(0, 83, -112, 0, 86, -108, "air")
    b.box(0, 87, -117, 0, 87, -104, FRAME)
    for x in (-4, 4):
        b.box(x, 83, -115, x, 85, -115, "smooth_quartz")
        b.box(x, 84, -114, x, 84, -114, "polished_blackstone_button[face=wall,facing=south]")
        b.warning_light(x, 88, -115)
    b.stripes(-2, -112, -2, -108, 82)
    b.stripes(2, -112, 2, -108, 82)
    b.maintenance_droid(-67, -112, 82)
    b.wear([(-62, -110, 60, -110), (-12, -120, 12, -120), (-32, -98, -32, -110),
            (32, -98, 32, -110)], random.Random(732), 82)
    for x in (*ROADS, -12, 12):
        stairway(b, x, -80, 65, 18, -1)


def southern_halls(b):
    for x in (*ROADS, 0):
        b.deck(x - 2, 58, x + 2, 63, PLATE)
        b.box(x - 2, 65, 58, x + 2, 68, 63, "air")
        stairway(b, x, 64, 65, 16, 1)
        upper_deck(b, x - 3, 80, x + 3, 110, 80)
        for edge in (x - 3, x + 3):
            b.box(edge, 81, 80, edge, 81, 104, RAIL)
    upper_deck(b, -66, 107, 68, 113, 80)
    b.box(-66, 81, 107, 68, 85, 113, "air")
    for x1, x2 in ((-47, -26), (26, 47)):
        for z in (106, 114):
            b.box(x1, 81, z, x2, 81, z, RAIL)
            b.box(x1, 80, z, x2, 80, z, FRAME)
        for x in range(x1 + 3, x2, 9):
            b.box(x, 80, 110, x + 1, 80, 110, COLD_LIGHT)
    for sign in (-1, 1):
        upper_deck(b, min(30 * sign, 64 * sign), 81, max(30 * sign, 64 * sign), 87, 80)
        upper_deck(b, 62 * sign - 2, 84, 62 * sign + 2, 98, 80)
        b.box(62 * sign - 2, 81, 84, 62 * sign + 2, 88, 98, "air")
        b.box(32 * sign - 4, 81, 82, 32 * sign + 4, 84, 86, "air")
        for z in (80, 88):
            b.box(min(37 * sign, 58 * sign), 81, z, max(37 * sign, 58 * sign), 81, z, RAIL)
    b.box(-2, 81, 80, 2, 88, 108, "air")
    b.box(-66, 81, 96, -66, 87, 107, DARK)
    b.box(-76, 81, 102, -67, 87, 102, FRAME)
    b.box(-70, 81, 102, -68, 84, 102, "air")
    for x, z in ((-77, 96), (-72, 91)):
        b.workbench(x, z, 80)
    for x, z in ((-57, 96), (-57, 115)):
        b.service_unit(x, z, 1, 80)
    b.crate(-79, 108, 4, 3, 1, "gray", 80)
    b.crate(-61, 118, 3, 2, 1, "light_gray", 80)
    b.box(-69, 80, 102, -63, 80, 105, "waxed_oxidized_copper")
    for x in (-71, -60):
        b.box(x, 81, 99, x, 83, 99, "smooth_quartz")
        b.box(x, 82, 100, x, 82, 100, "lever[face=wall,facing=south]")
    for x, z, dx, dz in ((-15, 100, 1, 7), (13, 114, 1, 7), (-10, 125, 6, 1), (6, 94, 7, 1)):
        b.box(x, 81, z, x + dx, 81, z + dz, "smooth_quartz")
    for x, z in ((18, 104), (8, 91)):
        b.workbench(x, z, 80)
    for x, z in ((-19, 113), (-6, 129)):
        b.service_unit(x, z, -1 if x > 0 else 1, 80)
    b.ellipse(0, 116, 4, 3, 81, 81, DARK)
    b.box(-3, 82, 115, 3, 82, 117, "cyan_stained_glass")
    b.box(-1, 83, 116, 1, 83, 116, COLD_LIGHT)
    for x, z, width, depth, height in ((55, 98, 4, 5, 1), (65, 95, 5, 3, 1),
                                      (74, 101, 5, 5, 4), (55, 117, 6, 3, 1),
                                      (67, 115, 4, 6, 4), (78, 113, 3, 4, 1)):
        b.crate(x, z, width, depth, height, "gray" if height > 2 else "light_gray", 80)
        b.stripes(x, z - 1, x + width - 1, z - 1, 80)
    b.maintenance_droid(72, 124, 80)
    b.wear([(-65, 110, 67, 110), (0, 87, 0, 127), (-77, 106, -55, 106),
            (61, 96, 61, 122), (72, 97, 72, 122)], random.Random(733), 80)


def side_routes(b):
    b.deck(8, -30, 16, -14, PLATE)
    b.box(8, 65, -30, 16, 68, -14, "air")
    for x in (8, 16):
        b.box(x, 65, -29, x, 69, -15, FRAME)
        b.box(x, 67, -28, x, 68, -16, "cyan_stained_glass")
    b.box(8, 70, -29, 16, 70, -15, FRAME)
    b.box(10, 69, -23, 14, 69, -23, COLD_LIGHT)
    b.box(14, 65, -28, 15, 69, -26, DARK)
    b.box(10, 65, -21, 12, 65, -21, "smooth_quartz")
    b.deck(-16, 14, -8, 30, PLATE)
    b.box(-16, 65, 14, -8, 68, 30, "air")
    for x in (-16, -8):
        b.rail(x, 15, x, 29)
    b.box(-16, 65, 20, -14, 69, 22, DARK)
    b.box(-11, 65, 25, -9, 65, 25, "smooth_quartz")
    b.box(-15, 70, 21, -15, 70, 21, "waxed_weathered_copper")


def inspection_course(b):
    upper_deck(b, -28, -136, 23, -126, 77)
    b.box(-28, 78, -136, 23, 91, -126, "air")
    for x in (-28, 23):
        b.box(x, 78, -136, x, 82, -126, FRAME)
    for z in (-136, -126):
        b.box(-28, 78, z, 23, 82, z, FRAME)
        b.box(-28, 83, z, 23, 83, z, RAIL)
    upper_deck(b, -27, -128, -22, -118, 82)
    upper_deck(b, 18, -134, 22, -118, 82)
    b.box(-27, 83, -128, -22, 87, -118, "air")
    b.box(18, 83, -134, 22, 87, -118, "air")
    upper_deck(b, -27, -134, -21, -129, 82)
    for index, x in enumerate(range(-18, 18, 5)):
        y = 82 + (index % 3 == 1)
        b.box(x, 78, -133, x + 2, y - 1, -130, DARK)
        b.box(x, y, -133, x + 2, y, -130, "smooth_quartz")
        b.box(x + 1, y, -131, x + 1, y, -131, "yellow_concrete")
    for x in (-18, 15):
        stairway(b, x, -129, 78, 5, 1)
    for x in (-27, 22):
        b.box(x, 83, -135, x, 103, -135, FRAME)
        b.warning_light(x, 104, -135)
    b.box(-27, 103, -135, 22, 104, -129, FRAME)
    b.box(-27, 103, -133, 22, 103, -131, DARK)
    b.box(-5, 95, -132, -4, 102, -132, "iron_chain")
    b.box(-7, 93, -134, -2, 94, -130, "gray_concrete")
    b.box(-24, 83, -127, -24, 84, -127, "chiseled_quartz_block")
    b.box(-24, 85, -127, -24, 85, -127, "cyan_stained_glass")
    ship_room(b, -62, -130, -53, -121)
    b.box(-60, 83, -123, -55, 87, -121, "air")
    b.box(-59, 83, -128, -57, 83, -128, "waxed_weathered_copper")
    b.box(-58, 84, -128, -58, 84, -128, "note_block")
    b.box(-57, 84, -128, -57, 87, -128, "lightning_rod[facing=up]")
    b.box(-60, 84, -128, -60, 85, -128, "cyan_stained_glass")


def northern_details(b):
    b.box(-62, 82, -113, 60, 82, -113, "polished_andesite")
    b.box(-62, 82, -107, 60, 82, -107, "polished_andesite")
    for z in (-121, -99):
        b.box(-62, 81, z, -34, 81, z, "waxed_oxidized_cut_copper")
        b.box(-62, 82, z, -34, 82, z, "iron_trapdoor[half=top,open=false,powered=false]")
        for x in (-60, -46, -36):
            b.box(x, 81, z, x, 81, z, COLD_LIGHT)
    for x in (-59, -45):
        for z in (-123, -98):
            b.box(x, 84, z, x + 1, 87, z, "chiseled_deepslate")
            b.box(x, 85, z, x + 1, 85, z, "waxed_weathered_cut_copper")
    for x in range(-61, -34, 4):
        b.box(x, 88, -123, x, 88, -115, "polished_deepslate_slab[type=top]")
        b.box(x, 88, -105, x, 88, -97, "polished_deepslate_slab[type=top]")
    for x, z in ((-51, -105), (-43, -120)):
        b.box(x, 83, z, x + 3, 83, z + 2, "polished_blackstone")
        for rib in (x, x + 3):
            b.box(rib, 84, z, rib, 87, z, "waxed_exposed_cut_copper")
        b.box(x + 1, 84, z, x + 2, 84, z, "copper_grate")
        b.box(x + 1, 85, z, x + 2, 86, z, "waxed_oxidized_copper")
        b.box(x + 1, 87, z, x + 2, 87, z, "copper_grate")
        b.box(x + 1, 88, z, x + 2, 88, z, "shroomlight")
    b.box(-51, 87, -102, -48, 87, -102, "waxed_weathered_cut_copper")
    b.box(-50, 86, -102, -49, 86, -102, "ochre_froglight[axis=x]")
    for x, z in ((-51, -102), (-43, -122)):
        b.box(x, 83, z, x + 3, 83, z + 1, "waxed_weathered_cut_copper")
    b.box(-62, 87, -101, -54, 87, -101, "waxed_oxidized_cut_copper")
    for x in (-61, -57):
        b.box(x, 88, -101, x, 89, -101, "iron_chain")
    b.box(-61, 86, -103, -59, 86, -103, COLD_LIGHT)
    b.box(-60, 87, -103, -60, 89, -103, "iron_chain")
    for x in (38, 43):
        b.box(x, 84, -118, x, 88, -118, "chiseled_quartz_block")
        b.box(x, 88, -116, x, 88, -115, "waxed_weathered_cut_copper")
    b.box(38, 89, -118, 43, 89, -115, "polished_deepslate")
    b.box(39, 88, -116, 42, 88, -116, "iron_bars")
    for x in (39, 42):
        b.box(x, 87, -117, x, 87, -117, COLD_LIGHT)
    for x in (26, 50, 58):
        b.box(x, 88, -119, x + 1, 88, -101, "polished_deepslate_slab[type=top]")
        b.box(x, 88, -117, x + 1, 88, -117, COLD_LIGHT)
        b.box(x, 88, -103, x + 1, 88, -103, COLD_LIGHT)
        for z in (-118, -102):
            b.box(x, 89, z, x, 89, z, "iron_chain")
    b.box(23, 87, -119, 59, 87, -119, "waxed_oxidized_cut_copper")
    for x, z in ((-32, -96), (32, -100)):
        for edge in (x - 4, x + 4):
            b.box(edge, 83, z, edge, 88, z, "chiseled_quartz_block")
            b.box(edge, 84, z + 1, edge, 86, z + 1, COLD_LIGHT)
        b.box(x - 4, 89, z, x + 4, 89, z, DARK)
        b.box(x - 3, 88, z, x + 3, 88, z, "cyan_concrete")
        b.box(x - 2, 82, z - 2, x + 2, 82, z - 2, "polished_andesite")
    for x, z in ((-48, -119), (55, -114)):
        b.box(x, 86, z, x, 86, z, "ochre_froglight[axis=y]")
        b.box(x, 87, z, x, 89, z, "iron_chain")
    for x in (-62, -28, 28, 57):
        b.box(x - 4, 67, -132, x + 4, 68, -88, "waxed_oxidized_cut_copper")
        for z in (-128, -92):
            b.box(x - 3, 69, z, x + 3, 72, z, "chiseled_quartz_block")


def southern_details(b):
    for cx, cz, radius in ((-66, 103, 18), (0, 110, 25), (68, 109, 20)):
        b.ellipse(cx, cz, 6, 6, 70, 71, "waxed_oxidized_cut_copper", hollow=True)
        b.ellipse(cx, cz, 7, 7, 75, 76, DARK, hollow=True)
        for sign in (-1, 1):
            for step in range(4):
                b.box(cx + sign * (5 + step), 72 + step, cz - 1,
                      cx + sign * (6 + step), 73 + step, cz + 1, FRAME)
        for dz in (-10, 0, 10):
            half = round(radius * math.sqrt(1 - (dz / (radius + 0.3)) ** 2))
            for sign in (-1, 1):
                x = cx + sign * half
                bottom = 86 if 107 <= cz + dz <= 113 else 81
                b.box(x, bottom, cz + dz, x, 88, cz + dz, "quartz_pillar[axis=y]")
                b.box(x, bottom + 1, cz + dz, x, bottom + 1, cz + dz, "waxed_oxidized_cut_copper")
    for x, z, accent in ((-62, 92, "waxed_exposed_cut_copper"), (0, 85, "cyan_concrete"),
                          (62, 92, "yellow_terracotta")):
        for edge in (x - 3, x + 3):
            b.box(edge, 81, z, edge, 86, z, "chiseled_quartz_block")
            b.box(edge, 82, z - 1, edge, 85, z - 1, COLD_LIGHT)
        b.box(x - 3, 87, z, x + 3, 87, z, DARK)
        b.box(x - 2, 86, z, x + 2, 86, z, accent)
        b.box(x - 2, 80, z - 1, x + 2, 80, z - 1, "polished_andesite")
    b.box(-46, 79, 109, 46, 79, 109, DARK)
    b.box(-46, 80, 109, 46, 80, 109, "iron_trapdoor[half=top,open=false,powered=false]")
    for x in (-40, -30, -20, 20, 30, 40):
        b.box(x, 80, 107, x, 80, 113, "polished_andesite")
    b.box(-78, 88, 98, -56, 88, 98, "waxed_weathered_cut_copper")
    for x in (-76, -64, -56):
        b.box(x, 89, 98, x, 90, 98, "iron_chain")
        b.box(x, 87, 98, x + 1, 87, 98, "ochre_froglight[axis=x]")
    b.box(-79, 81, 108, -76, 81, 110, "waxed_weathered_cut_copper")
    b.box(-79, 82, 108, -79, 84, 108, "iron_bars")
    b.box(-76, 82, 108, -76, 84, 108, "iron_bars")
    b.box(-79, 85, 108, -76, 85, 108, DARK)
    b.box(-78, 84, 108, -77, 84, 108, "ochre_froglight[axis=x]")
    b.ellipse(0, 110, 9, 9, 90, 90, "smooth_quartz_slab[type=top]", hollow=True)
    for x, z in ((-9, 110), (9, 110), (0, 101), (0, 119)):
        b.box(x, 89, z, x, 89, z, COLD_LIGHT)
    b.box(-21, 90, 110, 21, 90, 110, "quartz_pillar[axis=x]")
    b.box(0, 90, 90, 0, 90, 130, "quartz_pillar[axis=z]")
    b.box(-3, 82, 115, 3, 82, 117, "smooth_quartz")
    b.box(-2, 82, 115, 2, 82, 117, "cyan_stained_glass")
    b.box(-1, 81, 116, 1, 81, 116, COLD_LIGHT)
    b.box(-1, 83, 116, 1, 83, 116, "air")
    b.box(-3, 88, 116, 3, 88, 116, DARK)
    for x in (-3, 3):
        b.box(x, 89, 116, x, 90, 116, "iron_chain")
        b.box(x, 87, 116, x, 87, 116, COLD_LIGHT)
    for x in (57, 76):
        b.box(x, 88, 97, x, 88, 123, "polished_deepslate_slab[type=top]")
        for z in (99, 119):
            b.box(x, 89, z, x, 90, z, "iron_chain")
    b.box(57, 88, 122, 76, 88, 122, DARK)
    b.box(70, 87, 122, 74, 87, 122, "waxed_weathered_cut_copper")
    for x in (58, 75):
        b.box(x, 87, 121, x + 1, 87, 121, "ochre_froglight[axis=x]")
    for x, z, width in ((74, 101, 5), (67, 115, 4)):
        b.box(x + 1, 82, z, x + width - 2, 82, z, "yellow_terracotta")
        b.box(x + 1, 83, z, x + width - 2, 83, z, "iron_block")
    for x, z in ((57, 100), (67, 96), (58, 118), (79, 115)):
        b.box(x, 84, z, x, 84, z, "ochre_froglight[axis=y]")
        b.box(x, 85, z, x, 90, z, "iron_chain")
    for x, z, top in ((-72, 103, 100), (-69, 105, 102), (-65, 102, 101)):
        b.box(x - 1, 98, z - 1, x + 1, 98, z + 1, DARK)
        b.box(x, 99, z, x, top, z, "waxed_weathered_cut_copper")
        b.box(x - 1, top + 1, z - 1, x + 1, top + 1, z + 1, "smooth_quartz_slab[type=bottom]")
    b.box(-5, 106, 110, 5, 106, 110, DARK)
    for x in (-5, 5):
        b.box(x, 107, 110, x, 109, 110, FRAME)
        b.box(x, 110, 110, x, 110, 110, COLD_LIGHT)
    b.box(-4, 109, 110, 4, 109, 110, "iron_bars")
    for x, z in ((61, 109), (73, 114)):
        b.box(x - 2, 101, z - 2, x + 2, 102, z + 2, "smooth_quartz")
        b.box(x - 1, 102, z - 2, x + 1, 102, z - 2, "waxed_oxidized_cut_copper")
        b.box(x - 2, 103, z - 2, x + 2, 103, z + 2, "smooth_quartz_slab[type=bottom]")


def expansion(b):
    northern_dock(b)
    southern_halls(b)
    side_routes(b)
    inspection_course(b)
    northern_details(b)
    southern_details(b)


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


def expansion_route(faction, z):
    sign = -1 if faction == "kolonien" else 1
    if z < 0:
        points = [spot(50 * sign, -25), spot(32 * sign, -25), spot(32 * sign, -76),
                  spot(32 * sign, -79), spot(32 * sign, -98, 83), spot(32 * sign, -110, 83),
                  spot(12 * sign, -110, 83), spot(12 * sign, -98, 83), spot(12 * sign, -79),
                  spot(12 * sign, -60)]
    else:
        points = [spot(50 * sign, 25), spot(32 * sign, 25), spot(32 * sign, 63),
                  spot(32 * sign, 80, 81), spot(32 * sign, 84, 81), spot(62 * sign, 84, 81),
                  spot(62 * sign, 100, 81), spot(62 * sign, 110, 81), spot(32 * sign, 110, 81), spot(0, 110, 81),
                  spot(0, 83, 81), spot(0, 80, 81), spot(0, 63), spot(0, 58)]
    result = route(faction, z, -6 if z < 0 else 6)
    result["approach"] = points + [result["cover"]]
    return result


def side_route(faction):
    if faction == "kolonien":
        points = [spot(-50, 25), spot(-32, 25), spot(-32, 33), spot(-12, 33), spot(-12, 29),
                  spot(-12, 16), spot(-10, 14), spot(-10, 10)]
        lane = 6
    else:
        points = [spot(50, -25), spot(32, -25), spot(32, -33), spot(12, -33), spot(12, -29),
                  spot(12, -24), spot(13, -23), spot(13, -19), spot(12, -15)]
        lane = -6
    result = route(faction, 0, lane)
    result["approach"] = points + [result["cover"]]
    return result


def yard_props():
    return [dict(kind=kind, at=f"{x} {y} {z} {yaw}", scale=scale) for kind, x, y, z, yaw, scale in (
        ("power_coupler", -49, 84, -101, 0, 0.8),
        ("wall_panel", -50.5, 84, -101.953125, 0, 0.5),
        ("power_coupler", -41.5, 84, -121, 180, 0.75),
        ("console", -57, 84, -117, 0, 0.8),
        ("console", -43, 84, -116.5, 0, 0.8),
        ("console", -54, 84, -102, 180, 0.8),
        ("cargo_stack", -47.5, 84, -118.5, 0, 1.0),
        ("engine_cradle", -59.5, 84, -103, 0, 0.9),

        ("engine_cradle", 41, 85, -116, 0, 0.9),
        ("console", 29, 84, -116, 0, 0.8),
        ("console", 48, 84, -115, 0, 0.8),
        ("power_coupler", 47, 84, -104, 180, 0.8),
        ("cargo_stack", 55.5, 84, -114, 0, 1.0),
        ("cargo_stack", 37, 84, -103, 180, 0.75),
        ("repair_droid", 23.5, 85, -103, 180, 0.5),

        ("repair_droid", -77, 82, 109.5, 0, 0.65),
        ("console", -78.3, 82, 109.5, 0, 0.5),
        ("console", -72, 82, 91, 0, 0.8),
        ("power_coupler", -77, 82, 96, 270, 0.8),
        ("wall_panel", -54.925, 81.5, 115, 270, 0.8),
        ("cargo_stack", -59.5, 82, 119, 180, 0.65),

        ("console", -14, 82, 103.5, 270, 0.8),
        ("console", 14, 82, 117.5, 90, 0.8),
        ("console", -6.5, 82, 126, 180, 0.8),
        ("console", 10.5, 82, 95, 0, 0.8),
        ("wall_panel", -16.925, 81.5, 113, 270, 0.8),
        ("console", 18, 82, 104, 90, 0.8),
        ("wall_panel", -7.075, 81.5, 129, 90, 0.8),
        ("console", 8, 82, 91, 0, 0.8),
        ("console", -1.8, 83, 116.5, 0, 0.6),
        ("console", 1.8, 83, 116.5, 180, 0.6),
        ("wall_panel", 0, 81, 120.0609375, 0, 0.65),

        ("cargo_stack", 57, 82, 100.5, 270, 1.0),
        ("cargo_stack", 66.25, 82, 96.5, 0, 0.9),
        ("cargo_stack", 68.5, 82, 96.5, 0, 0.9),
        ("cargo_stack", 56.5, 82, 118.5, 180, 0.9),
        ("cargo_stack", 59.5, 82, 118.5, 180, 0.9),
        ("cargo_stack", 79.5, 82, 115, 90, 0.9),
        ("wall_panel", 76.5, 81.5, 106.0703125, 0, 0.75),
    )]


def arena():
    posts = []
    for key, z in POSTS.items():
        cells = ring(z)
        posts.append(dict(key=key, center=spot(0, z), radius=6.5, label=spot(0, z, SKY + (5 if key != "turbinenhalle" else 4)),
                          spawns=[spot(-5, z - 10), spot(5, z - 10), f"{spot(-5, z + 10)} 180 0", f"{spot(5, z + 10)} 180 0"],
                          indicators=[f"{x} {DECK} {cz}" for x, cz in cells],
                          routes={faction: [route(faction, z, lane) for lane in (-6, 6)] +
                                  [expansion_route(faction, z) if z else side_route(faction)]
                                  for faction in ("kolonien", "hegemonie")}))
    spawns = [-5.5, -1.5, 2.5, 6.5]
    return {"name": "Sturmwerft", "weather": "rain", "time": 13000,
            "play-area": [-100.0, 50.0, -150.0, 100.0, 144.0, 145.0],
            "world-border-size": 384.0, "death-y": 60.0,
            "relic": f"0.5 {SKY + 5}.2 -65.5", "overview": "0.5 112 62.5 180 40", "lobby": "0.5 90 0.5", "spectator": "24.5 92 30.5 145 35",
            "headquarters": {
                "kolonien": dict(spawns=[f"-81.5 65 {z} 270 0" for z in spawns], zone=[-97.0, 60.0, -30.0, -63.0, 104.0, 31.0]),
                "hegemonie": dict(spawns=[f"82.5 65 {z} 90 0" for z in spawns], zone=[63.0, 60.0, -30.0, 97.0, 104.0, 31.0])},
            "posts": posts,
            "controls": [dict(control="bulkhead", switches=["-4 84 -114", "4 84 -114"],
                              panels=[f"0 {y} {z}" for y in range(83, 87) for z in range(-112, -107)],
                              center="0.5 83 -109.5"),
                         dict(control="vent", switches=["-71 82 100", "-60 82 100"], panels=[], center="-65.5 81 103.5")],
            "secrets": {"receiver": "-58 84 -128", "inspection-course": {
                "lane": [-27.0, 77.0, -134.0, 23.0, 91.0, -129.0], "pit-y": 80.0,
                "gates": [-21.0, -16.0, -11.0, -6.0, -1.0, 4.0, 9.0, 14.0, 19.0], "board": "-23.5 87 -126.5"}},
            "environment": {
                "props": yard_props(),
                "steam": [spot(x, z - 8, SKY + 2) for z in POSTS.values() for x in (-11, 11)],
                "sparks": [spot(x, z - 6, SKY + 1.5) for z in POSTS.values() for x in (-9, 13)],
                "crane": {"from": "-10.5 84 0.5", "to": "10.5 84 0.5"}}}


def block_at(fills, x, y, z):
    for x1, y1, z1, x2, y2, z2, block in reversed(fills):
        if x1 <= x <= x2 and y1 <= y <= y2 and z1 <= z <= z2:
            return block
    return "air"


def solid_block(block):
    return block.split("[")[0] not in ("air", "polished_blackstone_button", "lever")


def solid_at(fills, x, y, z):
    return solid_block(block_at(fills, x, y, z))


def layer(fills, y):
    cells = set()
    for x1, y1, z1, x2, y2, z2, block in fills:
        if y1 <= y <= y2:
            changed = {(x, z) for x in range(x1, x2 + 1) for z in range(z1, z2 + 1)}
            if not solid_block(block):
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


def standing_positions(levels, area, death_y):
    x1, y1, z1, x2, y2, z2 = area
    return {(x, y, z) for y in range(math.ceil(max(death_y, y1)), math.floor(y2) + 1)
            for x, z in levels[y - 1] - levels[y] - levels[y + 1]
            if x1 <= x + 0.5 <= x2 and z1 <= z + 0.5 <= z2}


def walk_reachable(cells, start, levels, stairs=None):
    reached = {start} if start in cells else set()
    pending = deque(reached)
    while pending:
        x, y, z = pending.popleft()
        for nx, nz in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
            for ny in (y, y + 1, y - 1):
                cell = nx, ny, nz
                if cell not in cells or cell in reached:
                    continue
                if ny != y and stairs is not None and (x, y, z) not in stairs and cell not in stairs:
                    continue
                if (ny > y and (x, z) in levels[y + 2]) or (ny < y and (nx, nz) in levels[y + 1]):
                    continue
                reached.add(cell)
                pending.append(cell)
    return reached


def fighter_positions(plan):
    positions = [each for headquarters in plan["headquarters"].values() for each in headquarters["spawns"]]
    for post in plan["posts"]:
        positions.extend([post["center"], *post["spawns"]])
        for faction in ("kolonien", "hegemonie"):
            if not post["routes"].get(faction):
                sys.exit(f"Missing {faction} routes for {post['key']}")
            for route in post["routes"][faction]:
                positions.extend([*route["approach"], route["cover"], route["peek"], route["retreat"], route["capture"]])
                x, _, z = map(float, route["capture"].split()[:3])
                cx, _, cz = map(float, post["center"].split())
                if (x - cx) ** 2 + (z - cz) ** 2 > post["radius"] ** 2:
                    sys.exit(f"Capture outside {post['key']}: {route['capture']}")
    return positions


def require_reachable(positions, connected, area):
    for position in positions:
        values = [float(part) for part in position.split()[:3]]
        if any(not area[i] <= values[i] <= area[i + 3] for i in range(3)):
            sys.exit(f"Fighter position outside play area: {position}")
        if tuple(map(math.floor, values)) not in connected:
            sys.exit(f"Unreachable fighter position: {position}")


def clear_ray(levels, start, end, ignore_target=False):
    samples = max(1, math.ceil(math.dist(start, end) * 10))
    target = tuple(map(math.floor, end))
    for step in range(samples + 1):
        x, y, z = (math.floor(a + (b - a) * step / samples) for a, b in zip(start, end))
        if (x, z) in levels[y] and not (ignore_target and (x, y, z) == target):
            return False
    return True


def prop_bounds(prop):
    x, y, z, yaw = map(float, prop["at"].split())
    scale = prop.get("scale", 1.0)
    x1, y1, z1, x2, y2, z2 = YARD_PROP_BOUNDS[prop["kind"]]
    cosine, sine = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    corners = [(x + (dx * cosine - dz * sine) * scale, z + (dx * sine + dz * cosine) * scale)
               for dx in (x1, x2) for dz in (z1, z2)]
    return tuple(round(value, 9) for value in (min(point[0] for point in corners), y + y1 * scale,
                 min(point[1] for point in corners), max(point[0] for point in corners), y + y2 * scale,
                 max(point[1] for point in corners)))


def volume_cells(bounds):
    spans = [range(math.floor(low + 1e-7), math.ceil(high - 1e-7)) for low, high in zip(bounds[:3], bounds[3:])]
    return ((x, y, z) for x in spans[0] for y in spans[1] for z in spans[2])


def block_top(block, y):
    return y + (0.5 if block.split("[")[0].endswith("_slab") and "type=bottom" in block else 1)


def validate_props(fills, plan, walking):
    occupied = []
    visuals = Builder()
    for prop in plan["environment"].get("props", []):
        if prop["kind"] not in YARD_PROP_BOUNDS or not 0.5 <= prop.get("scale", 1.0) <= 1.5:
            sys.exit(f"Invalid yard prop: {prop}")
        bounds = prop_bounds(prop)
        x1, y1, z1, x2, y2, z2 = bounds
        area = plan["play-area"]
        if any(bounds[index] < area[index] or bounds[index + 3] > area[index + 3] for index in range(3)):
            sys.exit(f"Yard prop outside play area: {prop}")
        for x, y, z in volume_cells((x1 - 0.8, y1 - 1.8, z1 - 0.8, x2 - 0.2, y2, z2 - 0.2)):
            body = x + 0.2, y, z + 0.2, x + 0.8, y + 1.8, z + 0.8
            if (x, y, z) in walking and all(bounds[index] < body[index + 3] - 1e-7 and
                    bounds[index + 3] > body[index] + 1e-7 for index in range(3)):
                sys.exit(f"Yard prop obscures an ordinary standing position at {(x, y, z)}: {prop}")
        for previous in occupied:
            if all(bounds[index] < previous[index + 3] - 1e-7 and bounds[index + 3] > previous[index] + 1e-7
                   for index in range(3)):
                sys.exit(f"Yard props overlap: {prop}")
        occupied.append(bounds)
        for x, y, z in volume_cells(bounds):
            block = block_at(fills, x, y, z)
            bottom = y + (0.5 if block.split("[")[0].endswith("_slab") and "type=top" in block else 0)
            if solid_block(block) and bottom < y2 - 1e-7 and block_top(block, y) > y1 + 1e-7:
                sys.exit(f"Yard prop intersects {block} at {(x, y, z)}: {prop}")
        if prop["kind"] == "wall_panel":
            yaw = float(prop["at"].split()[3]) % 360
            if yaw not in (0, 90, 180, 270):
                sys.exit(f"Wall panel needs a cardinal backing face: {prop}")
            for x, y, z in volume_cells(bounds):
                sx = max(x1 + 1e-5, min(x + 0.5, x2 - 1e-5))
                sy = max(y1 + 1e-5, min(y + 0.5, y2 - 1e-5))
                sz = max(z1 + 1e-5, min(z + 0.5, z2 - 1e-5))
                backing = {0: (sx, sy, z1 - 0.001), 90: (x2 + 0.001, sy, sz),
                           180: (sx, sy, z2 + 0.001), 270: (x1 - 0.001, sy, sz)}[yaw]
                if not solid_at(fills, *map(math.floor, backing)):
                    sys.exit(f"Wall panel has no solid backing: {prop}")
        else:
            for x, y, z in volume_cells((x1, y1 - 0.01, z1, x2, y1, z2)):
                block = block_at(fills, x, y, z)
                if not solid_block(block) or not math.isclose(block_top(block, y), y1, abs_tol=1e-7):
                    sys.exit(f"Yard prop needs a supported work surface at {(x, y, z)}: {prop}")
        visuals.box(math.floor(x1 + 1e-7), math.floor(y1 + 1e-7), math.floor(z1 + 1e-7),
                    math.ceil(x2 - 1e-7) - 1, math.ceil(y2 - 1e-7) - 1, math.ceil(z2 - 1e-7) - 1, "barrier")
    print(f"Validated {len(occupied)} scaled yard props, backing and work surfaces")
    return visuals.fills


def validate_expansion(fills, plan, levels, cells, connected, stairs):
    rooms = [spot(-60, -120, 83), spot(-35, -99, 83), spot(24, -117, 83), spot(58, -103, 83),
             spot(-69, 96, 81), spot(-61, 114, 81), spot(0, 100, 81), spot(0, 128, 81),
             spot(61, 100, 81), spot(73, 122, 81), spot(-58, -126, 83),
             spot(-24, -131, 83), spot(21, -132, 83), spot(-20, -132, 78)]
    require_reachable(rooms, connected, plan["play-area"])
    walking = walk_reachable(cells, (-82, 65, -6), levels, stairs)
    require_reachable([*fighter_positions(plan), *rooms], walking, plan["play-area"])
    for x in range(-66, 69):
        if (x, 81, 110) not in walking:
            sys.exit(f"Skybridge centerline is obstructed at {(x, 81, 110)}")
    for x, z, floor, rise, direction in [(x, -80, 65, 18, -1) for x in (*ROADS, -12, 12)] + [
            (x, 64, 65, 16, 1) for x in (*ROADS, 0)]:
        for step in range(rise):
            at, y = z + direction * step, floor + step
            for dx in range(-2, 3):
                if "stairs[" not in block_at(fills, x + dx, y, at):
                    sys.exit(f"Missing stair: {(x + dx, y, at)}")
                if any(solid_at(fills, x + dx, y + dy, at) for dy in (1, 2, 3)):
                    sys.exit(f"Low stair headroom: {(x + dx, y, at)}")
    shut = {y: set(row) for y, row in levels.items()}
    panels = set()
    for control in plan["controls"]:
        for panel in control["panels"]:
            x, y, z = map(int, panel.split())
            if block_at(fills, x, y, z) != "air":
                sys.exit(f"Control panel must start as air: {panel}")
            panels.add((x, y, z))
            shut[y].add((x, z))
    safe = standing_positions(shut, plan["play-area"], plan["death-y"])
    vent = next(control for control in plan["controls"] if control["control"] == "vent")
    vx, vy, vz = map(float, vent["center"].split())
    hazard = {cell for cell in safe if abs(cell[0] + 0.5 - vx) <= 3 and abs(cell[2] + 0.5 - vz) <= 2
              and vy - 1 <= cell[1] <= vy + 3}
    safe -= hazard
    closed_connected = walk_reachable(safe, (-82, 65, -6), shut, stairs)
    require_reachable([*fighter_positions(plan), *rooms], closed_connected, plan["play-area"])
    bypass = {cell for cell in safe if -16 <= cell[0] <= 16 and -123 <= cell[2] <= -104 and cell[1] == 83}
    if (8, 83, -110) not in walk_reachable(bypass, (-8, 83, -110), shut):
        sys.exit("Bulkhead has no permanent northern bypass")
    workshop = {cell for cell in safe if -82 <= cell[0] <= -50 and 88 <= cell[2] <= 119 and cell[1] == 81}
    if (-59, 81, 103) not in walk_reachable(workshop, (-73, 81, 103), shut):
        sys.exit("Workshop vent has no southern bypass")
    for position in fighter_positions(plan):
        x, y, z = (math.floor(float(part)) for part in position.split()[:3])
        if (x, y, z) in hazard or (x, y, z) in panels or (x, y + 1, z) in panels:
            sys.exit(f"Fighter position overlaps control hazard: {position}")
    switches = []
    for control in plan["controls"]:
        for switch in control["switches"]:
            x, y, z = map(int, switch.split())
            block = block_at(fills, x, y, z)
            if not ("facing=south" in block and ("button[" in block or "lever[" in block)
                    and solid_at(fills, x, y, z - 1)):
                sys.exit(f"Unsupported control switch: {switch}")
            if abs(x + 0.5 - vx) <= 3 and abs(z + 0.5 - vz) <= 2 and vy - 1 <= y <= vy + 3:
                sys.exit(f"Control switch overlaps vent hazard: {switch}")
            switches.append(switch)
    secrets = plan["secrets"]
    for position in [*switches, secrets["receiver"]]:
        x, y, z = map(int, position.split())
        if block_at(fills, x, y, z) == "air":
            sys.exit(f"Missing interaction block: {position}")
        if position not in switches and not solid_at(fills, x, y - 1, z):
            sys.exit(f"Unsupported interaction block: {position}")
        target = x + 0.5, y + 0.5, z + 0.5
        usable = False
        for dx in range(-2, 3):
            for dz in range(1, 4):
                stance = x + dx, y - 1, z + dz
                if stance not in closed_connected:
                    continue
                eye = stance[0] + 0.5, stance[1] + 1.62, stance[2] + 0.5
                distance = math.dist(eye, target)
                if distance <= 5 and clear_ray(shut, eye, target, ignore_target=True):
                    usable = True
        if not usable:
            sys.exit(f"Interaction cannot be reached and seen: {position}")
    course = plan["secrets"]["inspection-course"]
    board = tuple(map(float, course["board"].split()))
    bx, by, bz = map(math.floor, board)
    text = [(board[0] + dx, board[1] + dy, board[2]) for dx in (-2.75, 0, 2.75) for dy in (0, 1.5, 3)]
    if any(solid_at(fills, *map(math.floor, point)) for point in text):
        sys.exit("Inspection board intersects a solid block")
    if not any(solid_at(fills, bx, by - dy, bz) for dy in range(1, 4)):
        sys.exit("Inspection board has no nearby support")
    readable = False
    for x in range(bx - 2, bx + 3):
        for z in range(bz + 2, bz + 7):
            for y in range(by - 6, by):
                if (x, y, z) not in closed_connected:
                    continue
                eye = x + 0.5, y + 1.62, z + 0.5
                if math.dist(eye, board) <= 8 and all(clear_ray(shut, eye, point) for point in text):
                    readable = True
    if not readable:
        sys.exit("Inspection board cannot be read from its southern approach")
    if course["gates"] != sorted(set(course["gates"])):
        sys.exit("Inspection gates must run west to east")
    for gate in course["gates"]:
        if not any((math.floor(gate), y, -132) in cells for y in (83, 84)):
            sys.exit(f"Inspection gate has no fixed landing: {gate}")
    lane = course["lane"]
    landings = [(x, y) for x in range(math.ceil(lane[0]), math.floor(lane[3]))
                for y in (83, 84) if (x, y, -132) in cells]
    for (x1, y1), (x2, y2) in zip(landings, landings[1:]):
        if x2 - x1 > 3 or abs(y2 - y1) > 1:
            sys.exit(f"Inspection jump too long or high: {(x1, y1)} to {(x2, y2)}")
    for post in plan["posts"]:
        target = [float(part) for part in post["center"].split()]
        target[1] += 1.62
        for routes in post["routes"].values():
            for route in routes:
                eyes = [[float(part) for part in route[key].split()] for key in ("cover", "peek")]
                for eye in eyes:
                    eye[1] += 1.62
                if clear_ray(levels, eyes[0], target) or not clear_ray(levels, eyes[1], target):
                    sys.exit(f"Cover or peek has no useful geometry at {post['key']}: {route['cover']}")
    if len(walking) < 23000:
        sys.exit(f"Expansion too small: {len(walking)} standing positions reachable without jumping")
    print(f"Validated {len(walking)} standing positions reachable by floor and stairs")


def validate(fills, plan):
    levels = {y: layer(fills, y) for y in range(math.floor(plan["play-area"][1]) - 1, math.floor(plan["play-area"][4]) + 3)}
    cells = standing_positions(levels, plan["play-area"], plan["death-y"])
    stairs = {(x, y1 + 1, z) for x1, y1, z1, x2, _, z2, block in fills if "_stairs[" in block
              for x in range(x1, x2 + 1) for z in range(z1, z2 + 1)
              if "_stairs[" in block_at(fills, x, y1, z)}
    visuals = validate_props(fills, plan, walk_reachable(cells, (-82, 65, -6), levels, stairs))
    fills = fills + visuals
    for x1, y1, z1, x2, y2, z2, _ in visuals:
        for y in range(y1, y2 + 1):
            levels[y].update((x, z) for x in range(x1, x2 + 1) for z in range(z1, z2 + 1))
    blocked = blocked_spots(fills, plan)
    if blocked:
        sys.exit(f"Unsupported or obstructed fighter positions: {blocked}")
    floor = levels[DECK]
    walkable = floor - levels[SKY] - levels[SKY + 1]
    cells = standing_positions(levels, plan["play-area"], plan["death-y"])
    connected = walk_reachable(cells, (-82, 65, -6), levels)
    require_reachable(fighter_positions(plan), connected, plan["play-area"])
    validate_expansion(fills, plan, levels, cells, connected, stairs)
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
    print(f"Validated {len(connected)} connected standing positions, occupied rooms, stairs and control bypasses")
    print("Validated both vehicle approaches, six clear landing cylinders, crane sweep and emitters")


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


def build():
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
    far = Builder()
    scenery(far)
    expansion(far)
    return b, far


def main():
    b, far = build()
    deck_commands = b.commands
    plan = arena()
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

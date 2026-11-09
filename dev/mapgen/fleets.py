"""Block-built fleets for the ocean shipyard and the desert battlefield."""

import json
import runpy
from pathlib import Path


class _Frame:
    def __init__(self, builder, origin, turn):
        self.builder = builder
        self.origin = origin
        self.turn = turn % 4
        self.bounds = None

    def box(self, x1, y1, z1, x2, y2, z2, block):
        corners = []
        for x, y, z in ((x1, y1, z1), (x2, y2, z2)):
            for _ in range(self.turn):
                x, z = -z, x
            corners.append((x + self.origin[0], y + self.origin[1], z + self.origin[2]))
        low = tuple(min(a, b) for a, b in zip(*corners))
        high = tuple(max(a, b) for a, b in zip(*corners))
        self.builder.box(*low, *high, block)
        if self.bounds is None:
            self.bounds = low + high
        else:
            self.bounds = tuple(min(a, b) for a, b in zip(self.bounds[:3], low)) + tuple(
                max(a, b) for a, b in zip(self.bounds[3:], high))


def _carrier(builder, origin, turn, warm):
    ship = _Frame(builder, origin, turn)
    hull = "smooth_sandstone" if warm else "light_gray_concrete"
    edge = "cut_sandstone" if warm else "smooth_stone"
    stripe = "red_terracotta" if warm else "red_concrete"
    dark = "polished_deepslate"
    for z in range(-36, 58, 3):
        end = min(z + 2, 57)
        half = max(1, round(30 * (57 - z) / 93))
        belly = max(1, half - 4)
        ship.box(-belly, -3, z, belly, -1, end, dark)
        ship.box(-half, 0, z, half, 2, end, hull)
        ship.box(-max(1, half - 2), 3, z, max(1, half - 2), 3, end, edge)
        paint = min(3, belly)
        ship.box(-paint, -3, z, paint, -3, end, stripe)
        ship.box(-min(3, half), 3, z, min(3, half), 3, end, stripe)
        if half > 10 and z % 9 == 0:
            for side in (-1, 1):
                ship.box(side * (half - 3), 0, z, side * (half - 1), 0, z, "sea_lantern")
    ship.box(-17, 4, -34, 17, 5, -9, hull)
    ship.box(-12, 6, -32, 12, 7, -17, edge)
    ship.box(-2, 4, -35, 2, 4, 37, stripe)
    ship.box(-5, -4, -32, 5, -4, -4, "black_concrete")
    for side in (-1, 1):
        ship.box(side * 6, -4, -32, side * 6, -4, -4, "sea_lantern")
        ship.box(side * 9 - 1, 8, -29, side * 9 + 1, 13, -25, hull)
        ship.box(side * 9 - 5, 14, -32, side * 9 + 5, 15, -22, hull)
        ship.box(side * 9 - 5, 14, -21, side * 9 + 5, 14, -21, "cyan_stained_glass")
        ship.box(side * 9 - 2, 16, -31, side * 9 + 2, 16, -25, stripe)
        ship.box(side * 9, 17, -28, side * 9, 19, -28, "iron_block")
        ship.box(side * 9, 20, -28, side * 9, 20, -28, "sea_lantern")
        for z in (-22, -10, 2):
            half = round(30 * (57 - z) / 93)
            ship.box(side * (half - 5), 4, z, side * (half - 3), 5, z + 2, dark)
            ship.box(side * (half - 4), 5, z + 3, side * (half - 4), 5, z + 6, "iron_block")
        ship.box(side * 15, -3, -30, side * 21, -3, -21, stripe)
        ship.box(side * 24, 1, -35, side * 30, 1, -35, "sea_lantern")
    for x in (-22, -11, 0, 11, 22):
        ship.box(x - 3, -3, -42, x + 3, 3, -35, dark)
        ship.box(x - 2, -2, -43, x + 2, 2, -43, "sea_lantern")
        ship.box(x - 2, -2, -45, x + 2, 2, -44, "light_blue_stained_glass")
        ship.box(x - 1, -3, -43, x + 1, -3, -36, "sea_lantern")
        ship.box(x - 1, -1, -47, x + 1, 1, -46, "blue_stained_glass")
    return ship.bounds


def _frigate(builder, origin, turn, warm):
    ship = _Frame(builder, origin, turn)
    hull = "brown_terracotta" if warm else "gray_concrete"
    plate = "light_gray_terracotta" if warm else "polished_andesite"
    dark = "polished_blackstone"
    ship.box(-4, -3, -30, 4, 3, 19, dark)
    ship.box(-2, -4, -24, 2, -4, 12, "cyan_concrete")
    ship.box(-5, 4, -19, 5, 6, 8, hull)
    ship.box(-3, 7, -9, 3, 10, 3, plate)
    ship.box(-4, 9, -1, 4, 9, 4, "cyan_stained_glass")
    for z, half in ((0, 10), (3, 16), (6, 20), (9, 22), (12, 22), (15, 20), (18, 18),
                    (21, 16), (24, 13), (27, 10), (30, 7), (33, 5)):
        for side in (-1, 1):
            inside = 5 if z >= 18 else 3
            ship.box(side * inside, -2, z, side * half, 3, z + 2, hull)
            ship.box(side * inside, 4, z, side * max(inside, half - 2), 5, z + 2, plate)
            ship.box(side * max(inside, half - 2), -3, z, side * half, -3, z + 2, dark)
            if z in (6, 15, 24):
                ship.box(side * (half - 1), -3, z, side * (half - 1), -3, z + 1, "sea_lantern")
    for side in (-1, 1):
        ship.box(side * 11, -1, -27, side * 16, 2, -8, hull)
        ship.box(side * 8, 0, -20, side * 14, 0, -18, dark)
        ship.box(side * 8, 0, -12, side * 14, 0, -10, dark)
        ship.box(side * 13, -2, -28, side * 15, 2, -28, "sea_lantern")
        ship.box(side * 13, -2, -31, side * 15, 2, -29, "light_blue_stained_glass")
        ship.box(side * 9, 6, 9, side * 13, 7, 12, dark)
        ship.box(side * 11, 7, 13, side * 11, 7, 17, "iron_block")
        ship.box(side * 5, -3, -12, side * 5, -3, 9, "cyan_stained_glass")
    ship.box(-2, -4, 18, 2, -2, 23, "sea_lantern")
    ship.box(-2, -4, 24, 2, -2, 28, "cyan_stained_glass")
    ship.box(-2, -2, -32, 2, 2, -32, "sea_lantern")
    ship.box(-1, -1, -35, 1, 1, -33, "blue_stained_glass")
    ship.box(0, 11, -5, 0, 15, -5, "iron_block")
    return ship.bounds


def _escort(builder, origin, turn, enemy, warm):
    ship = _Frame(builder, origin, turn)
    hull = "gray_concrete" if enemy else ("smooth_sandstone" if warm else "light_gray_concrete")
    stripe = "cyan_terracotta" if enemy else "red_terracotta"
    dark = "polished_deepslate"
    for z in range(-11, 18, 3):
        half = max(1, round(7 * (17 - z) / 28))
        ship.box(-half, -1, z, half, 1, min(z + 2, 17), hull)
        ship.box(-1, -2, z, 1, -2, min(z + 2, 17), stripe)
    ship.box(-2, 2, -8, 2, 3, -2, dark)
    ship.box(-2, 3, -1, 2, 3, -1, "cyan_stained_glass")
    for side in (-1, 1):
        ship.box(side * 4, -1, -13, side * 6, 1, -7, dark)
        ship.box(side * 4, -1, -14, side * 6, 1, -14, "sea_lantern")
        ship.box(side * 4, -1, -16, side * 6, 1, -15, "light_blue_stained_glass")
        if enemy:
            ship.box(side * 7, -4, -4, side * 7, 4, 4, hull)
            ship.box(side * 7, -4, 5, side * 7, 4, 5, stripe)
    return ship.bounds


def sturmwerft_fleet(builder):
    return [
        {"kind": "carrier", "bounds": _carrier(builder, (0, 140, -108), 1, False)},
        {"kind": "frigate", "bounds": _frigate(builder, (124, 146, -18), 0, False)},
        {"kind": "frigate", "bounds": _frigate(builder, (-24, 148, 143), 3, False)},
        {"kind": "escort", "bounds": _escort(builder, (-92, 133, -97), 1, False, False)},
        {"kind": "escort", "bounds": _escort(builder, (36, 148, -153), 1, False, False)},
        {"kind": "escort", "bounds": _escort(builder, (104, 137, 48), 0, True, False)},
    ]


def tessar_fleet(builder):
    return [
        {"kind": "carrier", "bounds": _carrier(builder, (-64, 140, -15), 0, True)},
        {"kind": "frigate", "bounds": _frigate(builder, (62, 145, 22), 2, True)},
        {"kind": "escort", "bounds": _escort(builder, (4, 132, -82), 1, False, True)},
        {"kind": "escort", "bounds": _escort(builder, (4, 144, 72), 1, False, True)},
        {"kind": "escort", "bounds": _escort(builder, (58, 136, -72), 0, True, True)},
    ]


def main():
    yard = runpy.run_path(str(Path(__file__).with_name("sturmwerft.py")))
    out = Path(__file__).resolve().parents[2] / "build/mapgen/fleets"
    functions = out / "datapack/data/fleets/function"
    functions.mkdir(parents=True, exist_ok=True)
    (out / "datapack/pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 121, "min_format": 121, "max_format": 121, "description": "Sternenfront fleets"}}) + "\n")
    manifest = {}
    for name, formation in (("sturmwerft", sturmwerft_fleet), ("tessar", tessar_fleet)):
        builder = yard["Builder"]()
        ships = formation(builder)
        (functions / f"{name}.mcfunction").write_text("\n".join(builder.commands) + "\n")
        (out / f"{name}-forceload.txt").write_text("\n".join(yard["forceload_areas"](builder.fills)) + "\n")
        manifest[name] = {"ships": ships, "commands": len(builder.commands)}
        print(f"{name}: {len(ships)} ships, {len(builder.commands)} commands")
    (out / "fleets.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(out)


if __name__ == "__main__":
    main()

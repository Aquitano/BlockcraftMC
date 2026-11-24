"""Source-derived Minecraft starships; model credits and transformations are in models/sources.json."""

import gzip
import json
import runpy
from functools import cache
from pathlib import Path


@cache
def _rotate_block(block, turn):
    name, separator, suffix = block.partition("[")
    if not separator or turn % 4 == 0:
        return block
    directions = ("north", "east", "south", "west")
    rotated = {direction: directions[(index + turn) % 4] for index, direction in enumerate(directions)}
    properties = {}
    for property_ in suffix[:-1].split(","):
        key, value = property_.split("=", 1)
        original_key = key
        key = rotated.get(key, key)
        if original_key == "facing":
            value = rotated.get(value, value)
        elif original_key == "axis" and turn % 2:
            value = {"x": "z", "z": "x"}.get(value, value)
        elif original_key == "rotation":
            value = str((int(value) + 4 * turn) % 16)
        elif original_key == "shape" and "rail" in name:
            parts = [rotated.get(part, part) for part in value.split("_")]
            if "ascending" not in parts:
                parts.sort(key=("north", "south", "east", "west").index)
            value = "_".join(parts)
        elif original_key == "orientation":
            value = "_".join(rotated.get(part, part) for part in value.split("_"))
        properties[key] = value
    return name + "[" + ",".join(f"{key}={value}" for key, value in sorted(properties.items())) + "]"


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
        self.builder.box(*low, *high, _rotate_block(block, self.turn))
        if self.bounds is None:
            self.bounds = low + high
        else:
            self.bounds = tuple(min(a, b) for a, b in zip(self.bounds[:3], low)) + tuple(
                max(a, b) for a, b in zip(self.bounds[3:], high))


@cache
def _model(name):
    data = json.loads(gzip.decompress(Path(__file__).with_name("models").joinpath(f"{name}.json.gz").read_bytes()))
    palette = []
    for block in data["palette"]:
        if block.endswith("_concrete_powder"):
            block = block.removesuffix("_powder")
        elif block.startswith("chain["):
            block = "iron_" + block
        palette.append(_rotate_block(block, data["rotation"]))
    return data["cuboids"], palette


def _place(builder, model, kind, origin, turn):
    ship = _Frame(builder, origin, turn)
    cuboids, palette = _model(model)
    for x1, y1, z1, x2, y2, z2, block in cuboids:
        ship.box(x1, y1, z1, x2, y2, z2, palette[block])
    return {"kind": kind, "model": model, "bounds": ship.bounds}


def sturmwerft_fleet(builder):
    return [
        _place(builder, "venator", "carrier", (0, 178, -139), 1),
        _place(builder, "munificent", "frigate", (162, 187, 18), 0),
        _place(builder, "munificent", "frigate", (-20, 193, 195), 3),
        _place(builder, "venator-distant", "escort", (-159, 186, -133), 1),
        _place(builder, "venator-distant", "escort", (58, 198, -220), 1),
        _place(builder, "munificent-distant", "escort", (171, 188, 122), 0),
    ]


def tessar_fleet(builder):
    return [
        _place(builder, "venator", "carrier", (-132, 178, -40), 0),
        _place(builder, "munificent", "frigate", (130, 189, 36), 2),
        _place(builder, "venator-distant", "escort", (-14, 179, -151), 1),
        _place(builder, "venator-distant", "escort", (-7, 193, 133), 1),
        _place(builder, "munificent-distant", "escort", (115, 181, -105), 0),
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

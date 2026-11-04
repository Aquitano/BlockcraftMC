"""Builds original command-post projectors, faction holograms and shield grids."""
import json
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).parent / "src/assets/blockcraft"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(value, indent=2) + "\n").encode())


def png(path, pixels, size=32):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    rows = b"".join(b"\0" + bytes(sum(pixels[y * size:(y + 1) * size], ())) for y in range(size))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", size, size, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def box(start, end, texture):
    return {"from": start, "to": end, "faces": {face: {"texture": "#" + texture, "uv": [0, 0, 16, 16]}
            for face in ("north", "south", "east", "west", "up", "down")}}


def model(name, elements, textures):
    write(ROOT / f"models/item/battle/{name}.json", {"textures": textures, "elements": elements})
    write(ROOT / f"items/battle/{name}.json", {"model": {"type": "minecraft:model", "model": f"blockcraft:item/battle/{name}"}})


def generate():
    palette = {"neutral": (176, 198, 214), "kolonien": (255, 151, 49), "hegemonie": (69, 214, 255)}
    for faction, color in palette.items():
        for kind in ("emblem", "grid", "glow"):
            pixels = []
            for y in range(32):
                for x in range(32):
                    dx, dy = x - 15.5, y - 15.5
                    radius = math.hypot(dx, dy)
                    if kind == "grid":
                        ink = x in (0, 1, 15, 16, 30, 31) or y in (0, 1, 15, 16, 30, 31)
                    elif kind == "glow":
                        ink = True
                    elif faction == "kolonien":
                        ink = 11 < radius < 13 or abs(dx) < 2 and abs(dy) < 10 or abs(dy + abs(dx) * .55 - 3) < 1.5 and abs(dx) < 10
                    elif faction == "hegemonie":
                        angle = math.atan2(dy, dx)
                        ink = 11 < radius < 13 or radius < 4 or 5 < radius < 10 and abs(math.sin(angle * 3)) < .27
                    else:
                        ink = 11 < radius < 13 or abs(abs(dx) + abs(dy) - 7) < 1.5
                    pixels.append((*color, 255 if ink else 0))
            png(ROOT / f"textures/item/battle/{kind}_{faction}.png", pixels)
            if kind != "glow":
                model(f"{kind}_{faction}", [box([0, 0, 7.98], [16, 16, 8.02], "image")],
                      {"image": f"blockcraft:item/battle/{kind}_{faction}"})
        model(f"segment_{faction}", [box([5, 7.6, 7.3], [11, 8.4, 8.7], "image")],
              {"image": f"blockcraft:item/battle/glow_{faction}"})
    textures = {"body": "minecraft:block/polished_blackstone", "metal": "minecraft:block/iron_block",
                "panel": "minecraft:block/gray_concrete", "glow": "minecraft:block/light_blue_concrete"}
    parts = [box([0, 0, 0], [16, 2, 16], "body"), box([2, 2, 2], [14, 5, 14], "panel"),
             box([4, 5, 4], [12, 6, 12], "metal"), box([5, 6, 5], [11, 6.5, 11], "glow")]
    for x in (1, 12):
        for z in (1, 12):
            parts.extend([box([x, 2, z], [x + 3, 6, z + 3], "body"), box([x + 1, 6, z + 1], [x + 2, 7, z + 2], "glow")])
    for z in (3, 6, 9, 12):
        parts.append(box([-.1, 2.5, z], [.2, 4, z + 1], "metal"))
        parts.append(box([15.8, 2.5, z], [16.1, 4, z + 1], "metal"))
    model("projector", parts, textures)
    for weapon in ("carbine", "repeater", "long_rifle", "pistol", "twin_pistol", "heavy_rifle"):
        original = json.loads((ROOT / f"models/item/sternenfront/{weapon}.json").read_text())
        display = {}
        for hand in ("firstperson_righthand", "firstperson_lefthand"):
            pose = original["display"][hand]
            pose["translation"][1] -= .35
            pose["translation"][2] += 1.1
            pose["rotation"][0] -= 3
            display[hand] = pose
        write(ROOT / f"models/item/sternenfront/{weapon}_firing.json",
              {"parent": f"blockcraft:item/sternenfront/{weapon}", "display": display})
    print("Generated battle projectors, original faction emblems, shield grids and firing poses")


if __name__ == "__main__":
    generate()

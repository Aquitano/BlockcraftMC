"""Builds command-post projectors, Republic and Separatist holograms, shield grids and firing poses."""
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


def projector_textures():
    textures = {"glow": "blockcraft:item/battle/glow_neutral"}
    for name, base in (("body", (41, 48, 54)), ("panel", (94, 107, 115)), ("metal", (152, 164, 170))):
        pixels = []
        for y in range(32):
            for x in range(32):
                grain = (x * 17 + y * 11 + x * y * 3) % 7 - 3
                color = tuple(channel + grain for channel in base)
                if x in (3, 27) or y in (4, 26):
                    color = (28, 34, 39)
                if (x < 2 or x > 29 or y > 29) and (x * 13 + y * 7) % 11 < 4:
                    color = (154, 158, 150)
                if 6 <= x <= 15 and y == 11 + x // 5 or 19 <= x <= 26 and y == 22:
                    color = (123, 129, 128)
                if 22 <= x <= 25 and 6 <= y <= 20:
                    color = tuple(int(channel * (.45 + (y - 6) / 32)) for channel in color)
                if name == "panel" and 7 <= x <= 16 and 15 <= y <= 23:
                    color = (111, 122, 126) if x not in (7, 16) and y not in (15, 23) else (42, 49, 53)
                if (x, y) in ((5, 6), (25, 6), (5, 24), (25, 24)):
                    color = (179, 186, 181)
                pixels.append((*color, 255))
        png(ROOT / f"textures/item/battle/projector_{name}.png", pixels)
        textures[name] = f"blockcraft:item/battle/projector_{name}"
    return textures


def generate():
    palette = {"neutral": (176, 198, 214), "kolonien": (61, 139, 255), "hegemonie": (255, 52, 52)}
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
                        angle = math.atan2(dy, dx)
                        ink = 11 < radius < 13 or radius < 3 or 4 < radius < 11 and abs(math.sin(angle * 4)) < .35
                    elif faction == "hegemonie":
                        angle = math.atan2(dy, dx)
                        edge = 11 / math.cos((angle + math.pi / 6) % (math.pi / 3) - math.pi / 6)
                        ink = abs(radius - edge) < 1.2 or radius < 3 or 4 < radius < 9 and abs(math.sin(angle * 3)) < .3
                    else:
                        ink = 11 < radius < 13 or abs(abs(dx) + abs(dy) - 7) < 1.5
                    pixels.append((*color, 255 if ink else 0))
            png(ROOT / f"textures/item/battle/{kind}_{faction}.png", pixels)
            if kind != "glow":
                model(f"{kind}_{faction}", [box([0, 0, 7.98], [16, 16, 8.02], "image")],
                      {"image": f"blockcraft:item/battle/{kind}_{faction}"})
        model(f"segment_{faction}", [box([5, 7.6, 7.3], [11, 8.4, 8.7], "image")],
              {"image": f"blockcraft:item/battle/glow_{faction}"})
    textures = projector_textures()
    parts = [box([0, 0, 0], [16, 2, 16], "body"), box([2, 2, 2], [14, 5, 14], "panel"),
             box([4, 5, 4], [12, 6, 12], "metal"), box([5, 6, 5], [11, 6.5, 11], "glow")]
    for x in (1, 12):
        for z in (1, 12):
            parts.extend([box([x, 2, z], [x + 3, 6, z + 3], "body"), box([x + 1, 6, z + 1], [x + 2, 7, z + 2], "glow")])
    for z in (3, 6, 9, 12):
        parts.append(box([-.1, 2.5, z], [.2, 4, z + 1], "metal"))
        parts.append(box([15.8, 2.5, z], [16.1, 4, z + 1], "metal"))
    parts.extend([box([5, 2.5, 1.7], [10, 4.5, 2], "metal"),
                  box([6, 3, 1.5], [9, 3.6, 1.7], "body"),
                  box([10.5, 2, 13.9], [12, 4, 14.3], "body")])
    model("projector", parts, textures)
    for weapon in ("carbine", "repeater", "long_rifle", "pistol", "twin_pistol", "heavy_rifle", "e5", "e5s", "wrist_blaster", "se14", "westar_pistols"):
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
    print("Generated battle projectors, Clone Wars faction emblems, shield grids and firing poses")


if __name__ == "__main__":
    generate()

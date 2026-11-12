"""Original faction-neutral objective props and CPU renders. Use assets.py generate objectives."""
import argparse
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from SceneryLights import configure as scenery_lights
from NativeArt import MATERIALS, COLORS, DIRECTIONS, box, remove_hidden, corners, resolve, render
from AssetFiles import write_json

ROOT = Path(__file__).resolve().parent
PREFIX = "sternenfront/objectives"
STATES = ("active", "damaged", "disabled")
KINDS = ("shield_generator", "power_relay", "communications_console")
NAMES = {"shield_generator": "Schildgenerator", "power_relay": "Energierelais", "communications_console": "Kommunikationskonsole"}
STATE_NAMES = {"active": "Aktiv", "damaged": "Beschädigt", "disabled": "Abgeschaltet"}


def atlas(state):
    image = Image.new("RGB", (64, 64))
    draw = ImageDraw.Draw(image)
    for i, (name, color) in enumerate(zip(MATERIALS, COLORS)):
        x, y = i % 4 * 16, i // 4 * 16
        rgb = tuple(bytes.fromhex(color))
        if state == "disabled" and name in ("cyan", "amber", "screen", "keys"):
            rgb = (37, 52, 59)
        if state == "damaged" and name == "cyan":
            rgb = (204, 135, 72)
        for v in range(16):
            for u in range(16):
                noise = -7 if (u * 17 + v * 31 + i * 11) % 29 == 0 else 0
                edge = 13 if u == 0 or v == 0 else -17 if u == 15 or v == 15 else 0
                image.putpixel((x + u, y + v), tuple(max(0, min(255, c + noise + edge)) for c in rgb))
        def line(points, fill, width=1):
            draw.line([(x + a, y + b) for a, b in points], fill=fill, width=width)
        if name in ("steel", "ivory", "service"):
            for u, v in ((2, 2), (13, 2), (2, 13), (13, 13)):
                draw.point((x + u, y + v), fill="#78888a")
        if name == "service":
            draw.rectangle((x + 2, y + 2, x + 13, y + 13), outline="#5a7078")
            draw.rectangle((x + 9, y + 7, x + 12, y + 8), fill="#344650")
        if name in ("vent", "coil"):
            for v in range(2, 15, 3):
                line([(2, v), (13, v)], "#172730")
                line([(2, v + 1), (13, v + 1)], "#83999a")
        if name == "screen" and state != "disabled":
            for u in range(3, 14, 4):
                line([(u, 3), (u, 12)], "#285e66")
            for v in range(4, 14, 4):
                line([(2, v), (13, v)], "#285e66")
            line([(3, 10), (6, 10), (6, 6), (11, 6), (11, 3)], "#83ddd3")
            draw.rectangle((x + 3, y + 3, x + 5, y + 4), fill="#edb76a")
        if name == "keys":
            for v in (3, 7, 11):
                for u in (2, 6, 10):
                    draw.rectangle((x + u, y + v, x + u + 2, y + v + 1), fill="#748e91" if state == "disabled" else "#b6d4cc")
        if name == "hazard":
            for u in range(16):
                for v in range(16):
                    if (u + v) % 8 < 4:
                        draw.point((x + u, y + v), fill="#33424a")
        if name == "socket":
            draw.rectangle((x + 3, y + 3, x + 12, y + 12), outline="#91a5a5", width=2)
        if name == "scar" or (state == "damaged" and name in ("service", "screen", "ivory")):
            line([(3, 1), (8, 6), (6, 9), (13, 14)], "#1b282e", 2)
            line([(2, 1), (7, 6), (5, 9)], "#a08570")
    return image


def geometry(kind, state):
    e = []
    def add(*args, **kwargs):
        e.append(box(*args, **kwargs))
    broken = state == "damaged"
    on = state != "disabled"
    if kind == "shield_generator":
        add("foundation", (-6, 8, -3), (22, 10, 19), "dark", front="hazard")
        add("armored tray", (-5, 10, -2), (21, 12, 18), "steel", front="service")
        add("core housing", (2, 12, 2), (14, 15, 14), "dark")
        add("energy core", (4, 15, 4), (12, 19, 12), "cyan", glow=on)
        add("core crown", (2, 19, 2), (14, 20, 14), "ivory", top="service")
        for x in (-3, 16):
            add("coil pedestal", (x - 1, 12, 1), (x + 3, 14, 15), "steel")
            for z in (3, 6, 9, 12):
                add("protected coil", (x, 14, z), (x + 2, 18, z + 1), "copper", top="coil")
            add("guard rail", (x - 1, 18, 0), (x + 3, 19, 16), "ivory")
        for x in (-5, 19):
            for z in (-1, 16):
                if broken and x == 19 and z == 16:
                    continue
                add("corner armor", (x, 12, z), (x + 2, 19, z + 2), "ivory")
        add("service hatch", (1, 12, 15), (15, 17, 16), "ivory", front="service",
            turn=([1, 12, 15], "x", 22.5) if broken else None)
        add("status strip", (3, 10.5, 18), (13, 11.25, 18.25), "amber", glow=on)
        if broken:
            add("exposed severed lead", (14, 12, 12), (15, 16, 13), "copper")
            add("loose armor shard", (16, 10, 16), (20, 10.75, 19), "scar", turn=([18, 10, 17], "y", 22.5))
    elif kind == "power_relay":
        add("relay plinth", (-2, 8, 1), (18, 10, 15), "dark", front="hazard")
        add("relay column", (4, 10, 3), (12, 24, 13), "steel", front="service")
        add("ivory hood", (2, 24, 2), (14, 26, 14), "ivory", top="vent")
        for x in (0, 14):
            add("coupler boot", (x - 2, 10, 5), (x + 4, 12, 11), "steel")
            add("power conduit", (x, 12, 6), (x + 2, 20 if broken and x == 14 else 23, 10), "copper", front="coil")
            if not (broken and x == 14):
                add("upper elbow", (min(x, 8), 22, 6), (max(x + 2, 8), 24, 10), "copper")
            add("coupler collar", (x - 1, 12, 5), (x + 3, 13, 11), "ivory")
            add("conduit tracer", (x + .5, 14, 10), (x + 1.5, 19, 10.25), "cyan", glow=on)
        add("distribution panel", (3, 15, 13), (13, 22, 14), "ivory", front="socket",
            turn=([3, 15, 13], "z", -22.5) if broken else None)
        add("status lamp", (6, 23, 13), (10, 23.75, 13.25), "amber", glow=on)
        if broken:
            add("snapped conductor", (14, 20, 7), (15, 22, 8), "scar", turn=([14, 20, 7], "z", -22.5))
    else:
        add("console foot", (0, 8, 2), (16, 10, 14), "dark", front="hazard")
        add("console pedestal", (3, 10, 4), (13, 18, 12), "steel", front="vent")
        add("service cover", (4, 11, 12), (12, 16, 12.5), "ivory", front="service",
            turn=([4, 11, 12], "x", 22.5) if broken else None)
        turn = ([8, 19, 8], "x", 22.5)
        add("sloping instrument case", (-1, 18, 1), (17, 21, 15), "ivory", turn=turn)
        add("display bezel", (0, 21, 2), (10, 21.5, 10), "dark", turn=turn)
        add("tactical display", (1, 21.5, 3), (9, 21.6, 9), "screen", turn=turn, glow=on)
        add("key bed", (11, 21, 3), (16, 21.5, 12), "steel", top="keys", turn=turn)
        add("control row", (1, 21, 11), (9, 21.5, 13), "dark", top="keys", turn=turn)
        add("warning lamp", (11, 21.5, 2), (15, 21.75, 2.75), "amber", turn=turn, glow=on)
        add("antenna mount", (14, 17, 3), (17, 20, 6), "steel")
        add("antenna stalk", (15, 20, 4), (16, 26 if broken else 30, 5), "steel")
        if not broken:
            add("antenna crosspiece", (13, 28, 4), (18, 29, 5), "ivory")
            add("antenna cap", (15, 30, 4), (16, 31, 5), "amber", glow=on)
        else:
            add("hanging antenna tip", (16, 23, 4), (17, 28, 5), "scar", turn=([16, 23, 4], "z", 45))
            add("loose control cable", (12, 15, 13), (12.5, 19, 13.5), "copper")
    if kind == "shield_generator":
        for element in e:
            for key in ("from", "to"):
                element[key][1] = 8 + (element[key][1] - 8) * 4 / 3
            if rotation := element.get("rotation"):
                rotation["origin"][1] = 8 + (rotation["origin"][1] - 8) * 4 / 3
    return remove_hidden(e)


def validate(model):
    assert model["textures"]["atlas"].startswith("blockcraft:item/" + PREFIX)
    points = []
    for e in model["elements"]:
        assert all(-16 <= a < b <= 32 for a, b in zip(e["from"], e["to"])), e["name"]
        assert 0 <= e.get("light_emission", 0) <= 15
        if r := e.get("rotation"):
            assert r["angle"] in (-45, -22.5, 0, 22.5, 45)
            assert r["axis"] in "xyz" and not r["rescale"]
        for side, face in e["faces"].items():
            assert face["texture"] == "#atlas"
            assert all(0 <= v <= 16 for v in face["uv"])
            assert face["uv"][0] < face["uv"][2] and face["uv"][1] < face["uv"][3]
            p = corners(e, side)
            assert np.all(p >= [-8, 8, -8]) and np.all(p <= [24, 32, 24]), (e["name"], p)
            points.extend(p.tolist())
    p = np.array(points)
    return {"min": p.min(0).round(5).tolist(), "max": p.max(0).round(5).tolist()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", type=Path, default=ROOT / "src")
    parser.add_argument("--output", type=Path, default=ROOT / "build/objectives")
    parser.add_argument("--nexo", type=Path, default=ROOT.parent / "nexo/items/blockcraft_starwars_objectives.yml")
    args = parser.parse_args()
    assets, out = args.pack / "assets/blockcraft", args.output
    models = assets / "models/item" / PREFIX
    textures = assets / "textures/item" / PREFIX
    items = assets / "items" / PREFIX
    for directory in (models, textures, items, out):
        directory.mkdir(parents=True, exist_ok=True)
    atlases = {state: atlas(state) for state in STATES}
    for state, image in atlases.items():
        image.save(textures / f"{state}.png")
    manifest = {"pack_layer": "starwars", "anchor_model_units": [8,8,8], "units_per_block": 16,
        "front": "+Z", "display_transform": "NONE", "model_display_transforms": {},
        "runtime_yaw": "Use the existing yard display half-turn cancellation once.",
        "collision": "FurnitureSolids mirrors the Nexo barrier core into real world blocks. Raw item displays have no collision.", "objects": {}}
    routes = []
    furniture = []
    for kind in KINDS:
        bounds = []
        states = {}
        for state in STATES:
            name = f"{kind}_{state}"
            elements = geometry(kind, state)
            if state == "active":
                data = {"ambientocclusion": True, "textures": {"atlas": f"blockcraft:item/{PREFIX}/active", "particle": "#atlas"}, "elements": elements}
            else:
                data = {"parent": f"blockcraft:item/{PREFIX}/{kind}_active", "textures": {"atlas": f"blockcraft:item/{PREFIX}/{state}"}, "elements": elements}
            write_json(models / f"{name}.json", data)
            route = {"model": {"type": "minecraft:model", "model": f"blockcraft:item/{PREFIX}/{name}"}}
            write_json(items / f"{name}.json", route)
            resolved = resolve(models / f"{name}.json", models)
            saved_route = json.loads((items / f"{name}.json").read_text())["model"]
            assert saved_route["type"] == "minecraft:model"
            assert saved_route["model"] == f"blockcraft:item/{PREFIX}/{name}"
            texture_path = args.pack / "assets" / (resolved["textures"]["atlas"].replace(":", "/textures/", 1) + ".png")
            loaded_texture = Image.open(texture_path)
            assert loaded_texture.size == (64, 64) and loaded_texture.mode == "RGB"
            assert resolved["textures"]["particle"] == "#atlas"
            assert not resolved.get("display")
            bound = validate(resolved)
            bounds.append(bound)
            if state == "disabled":
                assert not any(e.get("light_emission", 0) for e in elements)
                active = geometry(kind, "active")
                for a, b in zip(active, elements):
                    assert a["from"] == b["from"] and a["to"] == b["to"]
            states[state] = {"item_model": f"blockcraft:{PREFIX}/{name}", "geometry_bounds": bound,
                "nexo_item": f"blockcraft_objective_{name}",
                "elements": len(elements), "faces": sum(len(e["faces"]) for e in elements)}
            render(resolved, loaded_texture, out / f"{name}.png")
            render(resolved, loaded_texture, out / f"{name}_rear.png", az=math.pi + .65)
            routes.append(name)
        low = np.array([b["min"] for b in bounds]).min(0)
        high = np.array([b["max"] for b in bounds]).max(0)
        footprint = ((high-low)/16).round(5).tolist()
        manifest["objects"][kind] = {"states": states, "union_bounds_blocks_from_anchor": {
            "min": ((low-8)/16).round(5).tolist(), "max": ((high-8)/16).round(5).tolist()},
            "footprint_xz_blocks": [footprint[0],footprint[2]], "height_blocks": footprint[1],
            "interaction": {"offset": [0,0,0], "width": round(max(abs(low[0]-8),abs(high[0]-8),abs(low[2]-8),abs(high[2]-8))*2/16,5), "height": round((high[1]-8)/16,5)},
            "collision": {"type": "barrier", "blocks": [[0,0,0]]}}
        interaction = manifest["objects"][kind]["interaction"]
        for state in STATES:
            name = f"{kind}_{state}"
            furniture.extend([
                f"blockcraft_objective_{name}:", f"  itemname: '<white>{NAMES[kind]} · {STATE_NAMES[state]}'",
                "  material: PAPER", "  Components:", f"    item_model: blockcraft:{PREFIX}/{name}",
                "  Pack:", f"    model: blockcraft:item/{PREFIX}/{name}", "  Mechanics:", "    furniture:",
                "      properties:", "        display_transform: NONE", "        cullable: false",
                "        translation: 0,-0.5,0", "      restricted_rotation: VERY_STRICT",
                "      preview_placement: true", "      hitbox:",
                f"        interaction: 0,0,0 {interaction['width']},{interaction['height']}",
                "        barriers:", "          - 0,0,0", "",
            ])
            light,_=scenery_lights(args.pack/'assets/blockcraft',PREFIX+'/'+name,resolve(models/f'{name}.json',models),blocked=((0,0,0),))
            if light:
                furniture.extend(['      lights:','        toggleable: true',f'        lights: {json.dumps(light["lights"])}',
                                  f'        toggled_item_model: {light["toggled_item_model"]}',''])
        assert geometry(kind, "damaged") != geometry(kind, "active")
    args.nexo.parent.mkdir(parents=True, exist_ok=True)
    args.nexo.write_text("\n".join(furniture))
    sheet = Image.new("RGB", (1800, 1800), "#18222b")
    draw = ImageDraw.Draw(sheet)
    for row, kind in enumerate(KINDS):
        for col, state in enumerate(STATES):
            image = Image.open(out / f"{kind}_{state}.png").resize((550,550), Image.Resampling.LANCZOS)
            sheet.paste(image, (col*600+25,row*590+25))
            draw.text((col*600+24,row*590+32), kind.replace("_", " ").upper(), fill="#dce2dc", font_size=21)
            draw.text((col*600+24,row*590+60), state.upper(), fill="#edb76a", font_size=20)
    draw.text((24,1770), "SOFTWARE RENDERS OF GENERATED MESHES / NOT MINECRAFT SCREENSHOTS", fill="#a8babc", font_size=18)
    sheet.save(out / "contact-sheet.png")
    write_json(out / "manifest.json", manifest)
    docs(out, manifest)
    hashes = {str(p.relative_to(args.pack)): hashlib.sha256(p.read_bytes()).hexdigest()
              for directory in (models,items,textures) for p in sorted(directory.iterdir())}
    write_json(out / "asset-sha256.json", hashes)
    print(f"Validated {len(routes)} routes, native bounds/rotations/UVs, state geometry and dependencies. {out / 'contact-sheet.png'}")


def docs(out, manifest):
    lines = ["# Objective asset prototype", "", "Original faction-neutral kit. This subtree belongs to the Star Wars pack.",
        "The contact sheet and individual front/rear PNGs are CPU renders of the resolved generated model elements and atlas UVs. They are not Minecraft screenshots.", "",
        "## Generate", "", "From resourcepack: `uv run --locked python assets.py generate objectives`.",
        "`--pack` and `--output` support isolated output. Generation validates every native element, rotated corner, UV rectangle and state route.",
        "The three 64x64 RGB atlases are fully opaque. Their UV origin is top-left for every source and render; no source-dependent flip is applied.", "",
        "## Controller hooks", "", "Use the nine item_model routes in manifest.json for visual state swaps. Raw item displays have no collision.",
        "For solid scenery, install nexo/items/blockcraft_starwars_objectives.yml and place the matching nexo_item from the manifest through Nexo or the existing furniture layout.",
        "Each Nexo placement has one barrier block at local (0,0,0). Decorative projections and the console antenna are outside this core.",
        "FurnitureSolids mirrors the authored barrier core into a real world block. Native block rays and navigation see that core.",
        "Nexo owns the furniture lifecycle. FurnitureSolids owns its physical backing and restoration. Do not replace a Nexo entity's model route with another furniture item without its lifecycle.",
        "Place an item_display with item_display none at the desired ground position. Its local model anchor is (8,8,8), and front is +Z.",
        "Use the existing yard half-turn cancellation once. No scale is required. All dimensions and conservative interaction bounds are in manifest.json.",
        "Native block-model light_emission is 12 on powered elements only. This provides unshaded energy/instrument surfaces, not world light, bloom or a shield bubble.",
        "Minecraft block_display cannot directly reference item model routes. Use item_display, as existing yard props do, with native block-model cuboid geometry.", "",
        "## Isolated operator preview", "", "Run the following manually in a disposable development world after loading the Star Wars pack. These commands do not install tick functions.",
        "Choose a clear location in that disposable world. The preview does not alter tracked map placements.", "",
        '```mcfunction',
        'summon minecraft:item_display ~ ~ ~ {Tags:["bc_objective_preview"],item_display:"none",Rotation:[180f,0f],item:{id:"minecraft:paper",count:1,components:{"minecraft:item_model":"blockcraft:sternenfront/objectives/shield_generator_active"}}}',
        'item replace entity @e[type=minecraft:item_display,tag=bc_objective_preview,sort=nearest,limit=1,distance=..8] contents with minecraft:paper[minecraft:item_model="blockcraft:sternenfront/objectives/shield_generator_damaged"]',
        'item replace entity @e[type=minecraft:item_display,tag=bc_objective_preview,sort=nearest,limit=1,distance=..8] contents with minecraft:paper[minecraft:item_model="blockcraft:sternenfront/objectives/shield_generator_disabled"]',
        'kill @e[type=minecraft:item_display,tag=bc_objective_preview,distance=..8]', '```', "",
        "Substitute power_relay or communications_console for the model kind. Explicit state selection is intentional. No click handler, interaction state cycle or gameplay rule is installed.", "",
        "## Asset workflow", "", "Use resourcepack/assets.py generate objectives to create a validated scratch result.",
        "Review the previews and asset-report.json, then apply that result with assets.py apply.",
        "No vanilla route override or PackModel registration is necessary for direct item_model selection. Public shared equipment can instead be registered by the parent using its existing routing policy.", "",
        "## Limits", "", "Solid collision comes from Nexo's one-block barrier core, not the resource pack. Click boxes include the full damaged extents.",
        "Damage is static: opened service covers, missing armor, severed conduit or antenna, and scarred instruments. Active and disabled share intact geometry, with lighting and atlas state changes.",
        "Hidden and overlapping regions of unrotated cuboid faces are clipped while retaining their UV mapping. Rotated panels retain their faces. No global shader, atlas override, tick function, live-world command or match rule is added.",
        "Sternenfront's optional field-objectives.yml connects these props to map-owned power, console overrides, and shield fields. Lobby furniture stays decorative. Software renders cannot establish client collision or lighting."]
    (out / "README.md").write_text("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()

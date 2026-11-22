"""Shared native scenery kit. Geometry and raster helpers come from NativeArt.py."""
import argparse
import copy
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
import yaml
import NativeArt as h
from KitPixels import tile
from ModelSurfaces import exposed, overlaps
from FurnitureCollision import shared as physical_shared
from SceneryLights import configure as scenery_lights

ROOT = Path(__file__).resolve().parent
PREFIX = "sternenfront/kits/shared"
CONDITIONS = ("clean", "worn", "scorched")
LABELS = {
    "floor_panel": "Bodenplatte", "floor_grate": "Bodengitter", "service_strip": "Wartungsstreifen",
    "damaged_tile": "Beschädigte Bodenplatte", "blast_frame": "Schleusenrahmen",
    "door_closed": "Schott geschlossen", "door_open": "Schott offen", "door_jammed": "Schott verklemmt",
    "cover_low": "Deckung niedrig", "cover_high": "Deckung hoch", "cover_corner": "Deckung Ecke",
    "wall_lamp": "Wandleuchte", "ceiling_lamp": "Deckenleuchte", "landing_lamp": "Landefeldleuchte",
    "warning_beacon": "Warnbake", "engine_module": "Triebwerksmodul", "service_stand": "Wartungsständer",
    "refuelling_hose": "Betankungsschlauch", "landing_strut": "Landestütze",
}


def geometry(h, kind):
    e = []
    def b(name, low, high, material="steel", **kwargs):
        e.append(h.box(name, low, high, material, density=2 if kind.startswith('door_') or kind=='blast_frame' else 1, **kwargs))
    if kind in ("floor_panel", "floor_grate", "service_strip", "damaged_tile"):
        b("flush bed", (0,8,0), (16,8.0625,16), "dark")
        if kind == "floor_grate":
            for x in range(1,16,2):
                b("grating rib", (x,8.0625,1), (x+1,8.125,15), "steel")
        elif kind == "service_strip":
            b("service channel", (1,8.0625,1), (15,8.125,15), "service")
            b("guide stripe", (7,8.125,0), (9,8.1875,16), "amber")
        else:
            b("enamel plate", (1,8.0625,1), (15,8.125,15), "ivory", top="scar" if kind == "damaged_tile" else "service")
            if kind == "damaged_tile":
                b("exposed patch", (4,8.125,7), (10,8.1875,12), "scar")
        for x in (0,15):
            b("edge rail", (x,8.0625,0), (x+1,8.125,16), "steel")
    elif kind == "blast_frame":
        for x in (-4,12):
            b("fixed jamb", (x,8,4), (x+8,32,12), "steel", front="service")
            b("jamb armor", (x+.5,8,12), (x+7.5,30,12.5), "ivory")
            b("jamb guide", (x+3,9,12.5), (x+4,23,12.75), "cyan", glow=True)
        b("fixed lintel", (4,24,4), (12,32,12), "ivory", front="hazard")
        b("header indicator", (5,25,12), (11,26,12.25), "amber", glow=True)
    elif kind.startswith("door_"):
        shift = 16 if kind == "door_open" else 5 if kind == "door_jammed" else 0
        b("sliding panel", (4,8+shift,4), (12,24+shift,12), "dark")
        for x in (4.25,8.25):
            b("panel leaf", (x,8.25+shift,12), (x+3.5,23.75+shift,12.25), "ivory", front="service")
        b("lock seam", (7.75,9+shift,12), (8.25,23+shift,12.375), "steel")
        b("panel warning", (4.5,9+shift,12), (11.5,10+shift,12.125), "hazard")
    elif kind.startswith("cover_"):
        height = 24 if kind != "cover_high" else 31.75
        b("ballast core", (0,8,0), (16,height,16), "dark", front="vent")
        b("armor face", (0,9,14), (16,height-1,16.25), "ivory", front="service")
        b("top cap", (-.25,height,-.25), (16.25,height+.25,16.25), "steel", top="hazard")
        for x in (0,14):
            b("armor post", (x-.125,8,-.125), (x+2.125,height+.125,16.5), "steel")
        b("identifier strip", (3,11,16.25), (13,12,16.5), "amber")
        if kind == "cover_corner":
            b("return armor", (0,8,-16), (4,24,0), "ivory", front="service")
            b("return foot", (0,8,-16), (16,10,0), "dark")
            b("return cap", (-.125,23,-16), (4.125,24.125,0), "steel")
    elif kind in ("wall_lamp", "ceiling_lamp", "landing_lamp", "warning_beacon"):
        if kind == "wall_lamp":
            b("wall bracket", (2,8,0), (14,20,2), "steel")
            b("light hood", (1,17,1), (15,21,7), "ivory")
            b("luminous face", (3,12,2), (13,18,4), "cyan", glow=True)
            for x in (2,13):
                b("guard rail", (x,11,3), (x+1,19,5), "steel")
        elif kind == "ceiling_lamp":
            b("ceiling mount", (0,14,0), (16,16,16), "steel")
            b("diffuser", (2,12,2), (14,14,14), "cyan", glow=True)
            for z in (0,14):
                b("hood", (-.125,11,z), (16.125,15,z+2), "ivory")
        else:
            b("weighted base", (2,8,2), (14,10,14), "dark", top="hazard")
            b("lamp pedestal", (5,10,5), (11,16 if kind == "warning_beacon" else 12,11), "ivory")
            if kind == "warning_beacon":
                b("beacon lens", (5,16,5), (11,21,11), "amber", glow=True)
                b("beacon cap", (4,21,4), (12,22,12), "steel")
            else:
                b("landing diffuser", (4,12,4), (12,13,12), "cyan", glow=True)
    elif kind == "engine_module":
        b("engine bed", (-8,8,-.5), (24,11,16.5), "dark")
        b("reactor housing", (-5,8,0), (20,24,16), "steel", front="vent")
        for x in (-4,4,12):
            b("engine collar", (x,10,-.25), (x+2,24.5,16.25), "ivory")
        b("exhaust shroud", (20,12,3), (24,21,13), "dark")
        b("exhaust glow", (24,14,5), (24.25,19,11), "cyan", glow=True)
        b("service hatch", (1,24,4), (11,24.25,12), "service")
    elif kind == "service_stand":
        b("stand foot", (-.25,8,-.25), (16.25,10,16.25), "dark", top="hazard")
        b("lift cabinet", (0,8,0), (16,24,16), "steel", front="coil")
        b("clamp crossbeam", (-.125,23,2.875), (16.125,25,13.125), "ivory")
        for x in (0,13):
            b("clamp jaw", (x,25,3), (x+3,29,13), "steel")
            b("clamp pad", (x+.5,28,4), (x+2.5,29.5,12), "dark")
    elif kind == "refuelling_hose":
        b("pump base", (-.25,8,-.25), (16.25,10,16.25), "dark")
        b("pump", (0,8,0), (16,24,16), "ivory", front="service")
        for low, high in (((8,9,3),(23,11,5)), ((21,9,3),(23,11,17)), ((10,9,15),(23,11,17)), ((10,9,15),(12,15,17))):
            b("hose segment", low, high, "dark")
        b("fuel nozzle", (9,14,14), (13,19,18), "copper")
        b("status gauge", (2,17,16), (6,20,16.25), "cyan", glow=True)
    elif kind == "landing_strut":
        b("landing shoe", (-.25,8,-.25), (16.25,11,16.25), "dark", top="hazard")
        b("hydraulic pedestal", (0,8,0), (16,24,16), "ivory", front="service")
        b("hydraulic piston", (5,24,5), (11,29,11), "steel", front="coil")
        b("piston collar", (-.125,22,-.125), (16.125,24.5,16.125), "ivory")
        b("ship coupling", (1,28,2), (15,32,14), "steel", front="socket")
    if kind == "door_open":
        for element in e:
            for key in ("from", "to"):
                element[key][1] = 24 + (element[key][1] - 24) / 2
    return e


def texture(h, condition):
    image = Image.new('RGB', (64,64))
    roles = ('enamel', 'steel', 'dark', 'steel', 'vent', 'panel', 'screen', 'keys',
             'light', 'light', 'hazard', 'scar', 'steel', 'coil', 'socket', 'vent')
    for i, color in enumerate(h.COLORS):
        image.paste(tile(color, roles[i], seed=i+43), (i%4*16, i//4*16))
    if condition != "clean":
        for y in range(64):
            for x in range(64):
                tile_index = (y//16)*4+x//16
                if tile_index in (8,9):
                    continue
                scratch = ((x//2)*19+(y//2)*37)%23 < 2
                scorch = condition == "scorched" and ((x%16-9)**2+(y%16-8)**2 < 45+(x//3%3)*9)
                color = image.getpixel((x,y))
                image.putpixel((x,y), tuple(int(c * (.42 if scorch else .77 if scratch else .94)) for c in color))
    return image


def scene(h, entries, models, atlas, path):
    elements = []
    for name, position, yaw in entries:
        record = models[name]
        for original in record["model"]["elements"]:
            element = copy.deepcopy(original)
            a, b = (np.array(element[key], dtype=float) for key in ("from", "to"))
            points = np.array([a,b]) - 8
            points *= record["scale"]
            theta = math.radians(yaw)
            rotation = np.array([[math.cos(theta),0,-math.sin(theta)],[0,1,0],[math.sin(theta),0,math.cos(theta)]])
            points = points @ rotation + np.array(position)*16
            element["from"], element["to"] = points.min(0).tolist(), points.max(0).tolist()
            elements.append(element)
    low = np.array([e["from"] for e in elements]).min(0)
    high = np.array([e["to"] for e in elements]).max(0)
    factor = 26/max(high-low)
    center = (low+high)/2
    for e in elements:
        for key in ("from", "to"):
            e[key] = ((np.array(e[key])-center)*factor+[8,18,8]).tolist()
    h.render({"elements": elements}, atlas, path, size=1400)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", type=Path, default=ROOT/"src")
    parser.add_argument("--output", type=Path, default=ROOT/"art/shared-kit")
    parser.add_argument("--nexo", type=Path, default=ROOT.parent/"nexo/items/blockcraft_starwars_shared_kit.yml")
    args = parser.parse_args()
    out = args.output
    out.mkdir(parents=True, exist_ok=True)
    dirs = {kind: args.pack/"assets/blockcraft"/kind/PREFIX for kind in ("models/item", "items", "textures/item")}
    for directory in dirs.values():
        directory.mkdir(parents=True, exist_ok=True)
    atlases = {c:texture(h,c) for c in CONDITIONS}
    for c, atlas in atlases.items():
        atlas.save(dirs["textures/item"]/f"{c}.png")
    manifest = {"units_per_block":16, "ground_anchor_model_units":[8,8,8], "front":"+Z", "snap_grid_blocks":.125,
                "yaw_degrees":[0,90,180,270], "runtime_yaw":"Cancel the vanilla item display half-turn once, as yard props do.",
                "display_transform":"NONE", "collision":"FurnitureSolids supplies native world cores and bounded smaller colliders", "objects":{}}
    definitions = {}
    records = {}
    for kind in LABELS:
        for condition in CONDITIONS if kind in ("floor_panel", "cover_low") else ("clean",):
            name = kind if condition == "clean" else f"{kind}_{condition}"
            scale = 2 if kind.startswith("door_") or kind == "blast_frame" else 1
            solid_elements = geometry(h,kind)
            elements,_ = exposed(solid_elements)
            assert not overlaps(elements),(kind,'coplanar surface overlap')
            data = {"ambientocclusion":True, "textures":{"atlas":f"blockcraft:item/{PREFIX}/{condition}", "particle":"#atlas"}, "elements":elements}
            if condition != "clean":
                data = {"parent":f"blockcraft:item/{PREFIX}/{kind}", "textures":{"atlas":f"blockcraft:item/{PREFIX}/{condition}"}}
            h.write_json(dirs["models/item"]/f"{name}.json", data)
            route = f"blockcraft:{PREFIX}/{name}"
            h.write_json(dirs["items"]/f"{name}.json", {"model":{"type":"minecraft:model","model":f"blockcraft:item/{PREFIX}/{name}"}})
            resolved = h.resolve(dirs["models/item"]/f"{name}.json", dirs["models/item"])
            for e in resolved["elements"]:
                assert all(-16<=a<b<=32 for a,b in zip(e["from"],e["to"])), (name,e)
                for face in e["faces"].values():
                    assert face["texture"] == "#atlas"
                    u,v,U,V = face["uv"]
                    assert 0<=u<U<=16 and 0<=v<V<=16
            low = np.array([e["from"] for e in elements]).min(0)
            high = np.array([e["to"] for e in elements]).max(0)
            cores = [[0,0,0]] if kind.startswith("cover_") or kind in ("engine_module","service_stand","refuelling_hose","landing_strut","door_closed") else []
            if kind == "door_closed":
                cores += [[0,1,0]]
            if kind == "blast_frame":
                cores = [[x,y,0] for x in (-1,1) for y in range(3)]+[[0,2,0]]
                assert not any(x==0 and y<2 for x,y,z in cores)
            for cx,cy,cz in cores:
                core_low = np.array([cx-.5,cy,cz-.5])*16/scale+8
                core_high = np.array([cx+.5,cy+1,cz+.5])*16/scale+8
                assert any(np.all(core_low >= part["from"]) and np.all(core_high <= part["to"]) for part in solid_elements), (kind, "barrier exceeds solid core")
            floor = kind in ("floor_panel","floor_grate","service_strip","damaged_tile")
            dims = ((high-low)*scale/16).round(6).tolist()
            connectors = {"ground":[0,0,0]}
            if floor:
                connectors |= {"west":[-.5,0,0],"east":[.5,0,0],"north":[0,0,-.5],"south":[0,0,.5]}
            elif kind.startswith("cover_"):
                connectors |= {"west":[-.5,0,0],"east":[.5,0,0]}
            elif kind == "blast_frame":
                connectors |= {"panel_anchor":[0,0,0],"wall_west":[-1.5,0,0],"wall_east":[1.5,0,0]}
            elif kind.startswith("door_"):
                connectors["frame_anchor"] = [0,0,0]
            elif kind == "landing_strut":
                connectors["ship_mount"] = [0,1.5,0]
            elif kind == "wall_lamp":
                connectors["wall_back"] = [0,0,-.5]
            elif kind == "ceiling_lamp":
                connectors["ceiling_mount"] = [0,.5,0]
            else:
                connectors["service_axis"] = [.5,0,0]
            record = {"item_model":route,"nexo_id":f"blockcraft_shared_{name}","scale":[scale]*3,"dimensions_blocks":dims,
                      "bounds_model_units":{"min":low.tolist(),"max":high.tolist()},"connectors_blocks":connectors,
                      "barrier_cores":cores,"condition":condition, "ground_anchor_model_units":[8,8,8], "yaw_degrees":[0,90,180,270],
                      "module_pitch_blocks":[1,0,1] if floor else [3,0,1] if kind == "blast_frame" else [1,0,1],
                      "placement":"Overlay on supporting vanilla full blocks. No barrier." if floor else "Place through Nexo for client hitbox lifecycle; raw displays are non-solid.",
                      "interaction":"Nexo sliding door with FurnitureSolids world collision" if kind == "door_closed" else "Static state",
                      "elements":len(elements)}
            manifest["objects"][name] = record
            records[name] = {"model":resolved,"scale":scale}
            props = {"display_transform":"NONE", "cullable":False,"translation":"0,-0.5,0", "scale":f"{scale},{scale},{scale}"}
            offset = ((low+high)/2-8)*scale/16
            offset[1] = (low[1]-8)*scale/16
            width = max(dims[0],dims[2])
            if kind == "blast_frame":
                offset = np.array([-1.0,0.0,0.0])
                width = 1.0
            elif kind == "door_closed":
                offset = np.array([0.0,0.0,0.0])
                width = 1.125
            record["interaction_offset_blocks"] = offset.round(6).tolist()
            record["interaction_width_height"] = [width,dims[1]]
            hitbox = {"interaction":f"{','.join(str(round(v,6)) for v in offset)} {width},{dims[1]}"}
            hitbox["barriers"] = []
            furniture = {"properties":props,"restricted_rotation":"VERY_STRICT","preview_placement":True,"hitbox":hitbox}
            furniture['blockcraft_collision']=physical_shared(kind,cores)
            light,_=scenery_lights(args.pack/'assets/blockcraft',PREFIX+'/'+name,resolved,scale)
            if light:furniture['lights']=light
            record['physical_collision']=furniture['blockcraft_collision']
            if kind == "door_closed":
                furniture["door"] = {"is_sliding":True,"toggle_hitbox_on_open":False,"delay_hitbox_toggle":False,
                    "open_properties":{"translation":"0,1.5,0", "scale":"2,1,2"}}
            definitions[record["nexo_id"]] = {"itemname":f"<white>{LABELS[kind]} · {dict(clean='Sauber',worn='Abgenutzt',scorched='Versengt')[condition]}","material":"PAPER", "Components":{"item_model":route},"Pack":{"model":f"blockcraft:item/{PREFIX}/{name}"},"Mechanics":{"furniture":furniture}}
            h.render(resolved,atlases[condition],out/f"{name}.png",size=500)
    args.nexo.parent.mkdir(parents=True,exist_ok=True)
    args.nexo.write_text(yaml.safe_dump(definitions,allow_unicode=True,sort_keys=False))
    saved = yaml.safe_load(args.nexo.read_text())
    assert len(saved)==23 and len(set(saved))==23
    for name,record in manifest["objects"].items():
        assert saved[record["nexo_id"]]["Components"]["item_model"]==record["item_model"]
        route = json.loads((dirs["items"]/f"{name}.json").read_text())["model"]
        assert route["type"] == "minecraft:model" and route["model"].endswith('/'+name)
        assert Image.open(dirs["textures/item"]/f"{record['condition']}.png").size == (64,64)
    sheet = Image.new("RGB",(2000,1800),"#18222b")
    draw = ImageDraw.Draw(sheet)
    for i,name in enumerate(records):
        x,y = i%5*400,i//5*350
        sheet.paste(Image.open(out/f"{name}.png").resize((350,310)),(x+25,y+35))
        draw.text((x+15,y+12),name,fill="#edb76a",font_size=17)
    draw.text((20,1770),"GENERATED MESHES / SOFTWARE RENDERS / NOT MINECRAFT SCREENSHOTS",fill="#dce2dc",font_size=19)
    sheet.save(out/"contact-sheet.png")
    scenes = {
        "assembly-airlock": [("floor_panel",(x,0,z),0) for x in range(-2,3) for z in range(-1,3)]+[("blast_frame",(0,0,0),0),("door_closed",(0,0,0),0),("wall_lamp",(-1,1.5,.6),0),("warning_beacon",(2,0,1),0)],
        "assembly-open-airlock": [("floor_grate",(x,0,z),0) for x in range(-2,3) for z in range(-1,3)]+[("blast_frame",(0,0,0),0),("door_open",(0,0,0),0),("cover_low",(-2,0,1.5),0),("landing_lamp",(2,0,1),0)],
        "assembly-service-bay": [("service_strip",(x,0,z),0) for x in range(-2,3) for z in range(-2,3)]+[("engine_module",(0,0,0),0),("service_stand",(0,0,-1.2),0),("refuelling_hose",(1.4,0,0),0),("landing_strut",(-1.5,0,-1),0),("landing_lamp",(-2,0,1.5),0),("cover_high",(-2,0,-2),0)],
    }
    for name,entries in scenes.items():
        scene(h,entries,records,atlases["clean"],out/f"{name}.png")
    manifest["door_mechanic"] = {"item":"blockcraft_shared_door_closed", "fixed_frame":"blockcraft_shared_blast_frame",
        "closed_scale":[2,2,2], "open_scale":[2,1,2], "open_ground_offset_blocks":[0,2,0],
        "closed_barriers":[[0,0,0],[0,1,0]], "open_barriers":[], "client_test":"Pending parent-controlled testing",
        "jar_classes":["com.nexomc.nexo.mechanics.furniture.door.FurnitureDoor", "com.nexomc.nexo.mechanics.furniture.door.FurnitureDoorListener"]}
    manifest["support_sha256"] = hashlib.sha256(Path(h.__file__).read_bytes()).hexdigest()
    manifest["assemblies"] = {name:[{"piece":n,"ground_blocks":p,"yaw":y} for n,p,y in entries] for name,entries in scenes.items()}
    h.write_json(out/"manifest.json",manifest)
    hashes = {str(p.relative_to(args.pack)):hashlib.sha256(p.read_bytes()).hexdigest() for d in dirs.values() for p in sorted(d.iterdir())}
    h.write_json(out/"asset-sha256.json",hashes)
    print(f"Validated {len(saved)} Nexo IDs/routes, resolved parents, 3 atlases, UVs, native bounds, and open frame cores. {out}")


if __name__ == "__main__":
    main()

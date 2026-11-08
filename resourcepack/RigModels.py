"""Turns the generated body and blaster models into animated BetterModel files in bettermodel/.

Geometry and textures still come from SternenfrontArt.java, so a body changes in one place: run that first, then this.
The joints come from rig-bodies.yml, which RigBody reads for the plain displays too. Animations are written here as keyframes: BetterModel plays idle, walk, death and damage by itself,
and the plugin asks for shoot. The joints, item models and keyframes below face +z with the right side at -x, but BetterModel
takes a model's north, -z, as the entity's front, so every position and rotation is written turned half round. BetterModel
turns bones tagged h_ with where the entity looks, and hi_ with their children too, which is how the head, the arms, the
gun and their markings follow a bot's aim. A part's markings and a leader's pauldrons are child bones of their own, so the
plugin can tint them in the unit colour and show the pauldrons apart from the rest of the body.
"""
import base64
import json
import math
import struct
import uuid
import yaml
from pathlib import Path

HERE = Path(__file__).parent
ASSETS = HERE / "src/assets/blockcraft"
OUT = HERE / "bettermodel"
NAMESPACE = uuid.UUID("9b1f6f4e-0d3a-4c55-9a55-6f6f7a1c2b10")
PLUGIN = HERE.parent / "paper/sternenfront/src/main/resources"
MOTION = yaml.safe_load((PLUGIN / "config.yml").read_text())["appearance"]["motion"]
RIG = yaml.safe_load((PLUGIN / "rig-bodies.yml").read_text())
MOTION_BOUNDS = {
    "stride-blocks": (0.5, 4), "cycle-ticks": (8, 40), "leg-degrees": (0, 50),
    "bob-height": (0, 0.1), "lean-degrees": (0, 20), "sway-degrees": (0, 12),
    "recoil-degrees": (0, 25), "recoil-blocks": (0, 0.15), "recoil-ticks": (2, 12),
}


def motion(family):
    profile = MOTION[family.rsplit("_", 1)[1]]
    return {key: min(hi, max(lo, float(profile[key]))) if math.isfinite(float(profile[key])) else lo
            for key, (lo, hi) in MOTION_BOUNDS.items()}

# The blaster each animated body holds; the B2's guns are part of its arms.
GUNS = {
    "hegemonie_soldier": "e5", "hegemonie_heavy": None, "hegemonie_scout": "e5s", "hegemonie_medic": "se14",
    "kolonien_soldier": "carbine", "kolonien_heavy": "repeater", "kolonien_scout": "long_rifle", "kolonien_medic": "pistol",
}
OUTLINED = "_outlined"
MARKS = "_marks"
PAULDRON = "pauldron"
LEFT_ARM = "arm_left"
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}


def stable(*parts):
    return str(uuid.uuid5(NAMESPACE, "/".join(map(str, parts))))


def turned(x, y, z):
    """A position or Euler rotation turned half round the vertical axis."""
    return [-x, y, -z]


def png_size(path):
    data = path.read_bytes()
    return struct.unpack(">II", data[16:24])


class Model:
    def __init__(self, family):
        self.family = family
        self.elements, self.textures, self.bones = [], {}, {}

    def texture(self, reference):
        path = ASSETS / "textures" / (reference.split(":", 1)[1] + ".png")
        if reference not in self.textures:
            width, height = png_size(path)
            self.textures[reference] = dict(index=len(self.textures), path=path, width=width, height=height)
        return self.textures[reference]

    def bone(self, name, parent, pivot):
        self.bones[name] = dict(name=name, parent=parent, pivot=turned(pivot[0] * 16, pivot[1] * 16, pivot[2] * 16), children=[])
        if parent:
            self.bones[parent]["children"].append(self.bones[name])
        return name

    def add(self, bone, model_path, at, scale=1.0):
        """Places an item model's elements so its centre, where the item display's origin sits, is at the joint."""
        model = json.loads((ASSETS / "models/item" / f"{model_path}.json").read_text())
        textures = model["textures"]
        place = lambda c: turned(*(at[i] * 16 + (c[i] - 8) * scale for i in range(3)))
        for index, element in enumerate(model["elements"]):
            # A gun's heat vents take their colour from the held item, which a rig has not, so they would glow white.
            if bone == "weapon" and any("tintindex" in face for face in element["faces"].values()):
                continue
            faces = {}
            for side, face in element["faces"].items():
                reference = textures[face["texture"].lstrip("#")]
                texture = self.texture(reference)
                sx, sy = texture["width"] / 16, texture["height"] / 16
                u1, v1, u2, v2 = face["uv"]
                faces[OPPOSITE.get(side, side)] = dict(uv=[u1 * sx, v1 * sy, u2 * sx, v2 * sy], texture=texture["index"],
                                                       rotation=(face.get("rotation", 0) + (180 if side in ("up", "down") else 0)) % 360)
            corners = place(element["from"]), place(element["to"])
            origin, rotation = corners[0], [0, 0, 0]
            spin = element.get("rotation")
            if spin:
                origin = place(spin["origin"])
                rotation["xyz".index(spin["axis"])] = spin["angle"]
            cube = dict(name=f"{bone}_{index}", box_uv=False, rescale=False, locked=False, render_order="default",
                        allow_mirror_modeling=True, autouv=0, color=0, type="cube",
                        **{"from": list(map(min, *corners)), "to": list(map(max, *corners))}, origin=origin, rotation=turned(*rotation),
                        faces=faces, uuid=stable(self.family, bone, model_path, index))
            self.elements.append(cube)
            self.bones[bone].setdefault("cubes", []).append(cube["uuid"])

    def outline(self, bone):
        return dict(uuid=stable(self.family, "bone", bone["name"]), isOpen=True,
                    children=[child for child in bone.get("cubes", [])] + [self.outline(child) for child in bone["children"]])

    def groups(self):
        return [dict(name=bone["name"], origin=bone["pivot"], rotation=turned(*bone.get("rotation", [0, 0, 0])), color=0, uuid=stable(self.family, "bone", bone["name"]),
                     export=True, mirror_uv=False, isOpen=True, locked=False, visibility=True, autouv=0, selected=False)
                for bone in self.bones.values()]


def keyframes(family, bone, channel, points):
    """{time: (x, y, z)}"""
    return [dict(channel=channel, data_points=[dict(zip("xyz", (str(round(value, 3)) for value in turned(*point))))],
                 uuid=stable(family, bone, channel, time), time=time, color=-1, interpolation="catmullrom")
            for time, point in points.items()]


def animation(family, name, loop, length, channels):
    animators = {}
    for bone, by_channel in channels.items():
        animators[stable(family, "bone", bone)] = dict(name=bone, type="bone", keyframes=[
            frame for channel, points in by_channel.items() for frame in keyframes(family, bone, channel, points)])
    return dict(uuid=stable(family, "animation", name), name=name, loop=loop, override=False, length=length, snapping=24,
                selected=False, anim_time_update="", blend_weight="", start_delay="", loop_delay="", animators=animators)


def still(x=0, y=0, z=0):
    return {0: (x, y, z)}


def animations(family, grip_degrees):
    profile = motion(family)
    cycle = int(profile["cycle-ticks"]) / 20
    leg, bob = profile["leg-degrees"], profile["bob-height"] * 16
    lean, sway = profile["lean-degrees"], profile["sway-degrees"]
    kick, back = profile["recoil-degrees"], profile["recoil-blocks"] * 16
    recovery = int(profile["recoil-ticks"]) / 20
    grip = {"rotation": still(x=grip_degrees)}
    across = grip_degrees + 8
    support = {"rotation": still(x=across, y=22)}
    return [
        animation(family, "idle", "loop", 2.4, {
            "torso": {"position": {0: (0, 0, 0), 1.2: (0, -0.2, 0), 2.4: (0, 0, 0)}},
            "hi_right_arm": {"rotation": {0: (grip_degrees, 0, 0), 1.2: (grip_degrees + 2, 0, 0), 2.4: (grip_degrees, 0, 0)}},
            "hi_left_arm": support,
        }),
        animation(family, "walk", "loop", cycle, {
            "left_leg": {"rotation": {0: (leg, 0, 0), cycle / 2: (-leg, 0, 0), cycle: (leg, 0, 0)}},
            "right_leg": {"rotation": {0: (-leg, 0, 0), cycle / 2: (leg, 0, 0), cycle: (-leg, 0, 0)}},
            "torso": {"position": {0: (0, 0, 0), cycle / 4: (0, bob, 0), cycle / 2: (0, 0, 0),
                                   cycle * 3 / 4: (0, bob, 0), cycle: (0, 0, 0)},
                      "rotation": {0: (lean, sway, 0), cycle / 2: (lean, -sway, 0), cycle: (lean, sway, 0)}},
            "hi_right_arm": grip,
            "hi_left_arm": support,
        }),
        animation(family, "shoot", "once", recovery, {
            "hi_right_arm": {"rotation": {0: (grip_degrees, 0, 0), 0.05: (grip_degrees - kick, 0, 0), recovery: (grip_degrees, 0, 0)},
                             "position": {0: (0, 0, 0), 0.05: (0, 0, -back), recovery: (0, 0, 0)}},
            "hi_left_arm": {"rotation": {0: (across, 22, 0), 0.05: (across - kick, 22, 0), recovery: (across, 22, 0)},
                           "position": {0: (0, 0, 0), 0.05: (0, 0, -back), recovery: (0, 0, 0)}},
            "torso": {"rotation": {0: (0, 0, 0), 0.05: (-kick / 3, 0, 0), recovery: (0, 0, 0)}},
        }),
        animation(family, "damage", "once", 0.3, {
            "root": {"rotation": {0: (0, 0, 0), 0.05: (-6, 0, 2), 0.3: (0, 0, 0)}},
            "hi_head": {"rotation": {0: (0, 0, 0), 0.05: (-10, 0, 0), 0.3: (0, 0, 0)}},
        }),
        animation(family, "death", "once", 0.9, {
            "root": {"rotation": {0: (0, 0, 0), 0.25: (-18, 0, 4), 0.7: (-86, 0, 8), 0.9: (-84, 0, 8)},
                     "position": {0: (0, 0, 0), 0.7: (0, -3, -4), 0.9: (0, -3, -4)}},
            "hi_right_arm": {"rotation": {0: (grip_degrees, 0, 0), 0.5: (-20, 0, -25)}},
            "hi_left_arm": {"rotation": {0: (across, 22, 0), 0.5: (-30, 0, 30)}},
            "right_leg": {"rotation": {0: (0, 0, 0), 0.6: (22, 0, 0)}},
            "left_leg": {"rotation": {0: (0, 0, 0), 0.6: (-12, 0, 0)}},
        }),
    ]


def build(family, gun):
    body = RIG["bodies"][family]
    joints = RIG["joints"][body["joints"]]
    scale = body.get("scale", 1)
    grip_degrees = round(math.degrees(body["arm-lift"]))
    shoulder = tuple(joints["shoulder"][axis] * scale for axis in "xyz")
    hip = (joints["hip"]["x"] * scale, joints["hip"]["y"] * scale, 0)
    torso_at = (0, joints["torso"]["y"] * scale, 0)
    head_at = (0, joints["head"]["y"] * scale, joints["head"]["z"] * scale)
    model = Model(family)
    model.bone("root", None, (0, 0, 0))
    model.bone("torso", "root", torso_at)
    model.bones["torso"]["rotation"] = [math.degrees(joints["torso"]["lean"]), 0, 0]
    model.bone("hi_head", "root", head_at)
    model.bone("hi_left_arm", "root", shoulder)
    model.bone("hi_right_arm", "root", (-shoulder[0], shoulder[1], shoulder[2]))
    model.bone("left_leg", "root", hip)
    model.bone("right_leg", "root", (-hip[0], hip[1], 0))
    parts = f"sternenfront/body/{family}"
    right_shoulder = (-shoulder[0], shoulder[1], shoulder[2])
    right_hip = (-hip[0], hip[1], 0)
    left_arm = LEFT_ARM if (ASSETS / "models/item" / f"{parts}/{LEFT_ARM}.json").exists() else "arm"
    for bone, part, at in (("torso", "torso", torso_at), ("hi_head", "head", head_at), ("hi_left_arm", left_arm, shoulder),
                           ("hi_right_arm", "arm", right_shoulder), ("left_leg", "leg", hip), ("right_leg", "leg_right", right_hip)):
        model.add(bone, f"{parts}/{part}", at, scale)
        side = bone.removeprefix("hi_")
        if (ASSETS / "models/item" / f"{parts}/{part}{MARKS}.json").exists():
            model.bone(f"{side}{MARKS}", bone, at)
            model.add(f"{side}{MARKS}", f"{parts}/{part}{MARKS}", at, scale)
        if part in ("arm", LEFT_ARM) and (ASSETS / "models/item" / f"{parts}/{PAULDRON}.json").exists():
            model.bone(f"{side}_{PAULDRON}", bone, at)
            model.add(f"{side}_{PAULDRON}", f"{parts}/{PAULDRON}", at, scale)
    if gun:
        # The gun hangs off the arm, which the animations hold out, so its bone turns back by the same angle to lie
        # level with the joints' weapon position when that grip is held.
        turn = math.radians(-grip_degrees)
        forward, up = joints["weapon"]["z"] * scale - shoulder[2], joints["weapon"]["y"] * scale - shoulder[1]
        grip = (joints["weapon"]["x"] * scale, shoulder[1] + up * math.cos(turn) - forward * math.sin(turn),
                shoulder[2] + up * math.sin(turn) + forward * math.cos(turn))
        model.bone("weapon", "hi_right_arm", grip)
        model.bones["weapon"]["rotation"] = [-grip_degrees, 0, 0]
        model.add("weapon", f"sternenfront/{gun}", grip, RIG["weapon-scale"])
    textures = []
    for reference, texture in model.textures.items():
        source = base64.b64encode(texture["path"].read_bytes()).decode()
        textures.append(dict(path="", name=f"{texture['path'].stem}.png", folder="", namespace="", id=str(texture["index"]), group="",
                             width=texture["width"], height=texture["height"], uv_width=texture["width"], uv_height=texture["height"],
                             particle=False, use_as_default=False, layers_enabled=False, sync_to_project="", render_mode="default",
                             render_sides="auto", frame_time=1, frame_order_type="loop", frame_order="", frame_interpolate=False,
                             visible=True, internal=True, saved=False, uuid=stable(family, "texture", reference),
                             relative_path="", source=f"data:image/png;base64,{source}"))
    size = next(iter(model.textures.values()))
    return dict(meta=dict(format_version="5.0", model_format="free", box_uv=False), name=family, model_identifier="", visible_box=[1, 1, 0],
                variable_placeholders="", variable_placeholder_buttons=[], timeline_setups=[], unhandled_root_fields={},
                resolution=dict(width=size["width"], height=size["height"]), elements=model.elements, groups=model.groups(),
                outliner=[model.outline(model.bones["root"])], textures=textures, animations=animations(family, grip_degrees))


def main():
    OUT.mkdir(exist_ok=True)
    for family, gun in GUNS.items():
        model = json.dumps(build(family, gun), separators=(",", ":"))
        for name in (family, family + OUTLINED):
            (OUT / f"{name}.bbmodel").write_text(model)
    print(f"Wrote {len(GUNS)} bodies, each plain and outlined")


if __name__ == "__main__":
    main()

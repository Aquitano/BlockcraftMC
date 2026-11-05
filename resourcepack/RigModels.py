"""Turns the generated body and blaster models into animated BetterModel files in bettermodel/.

Geometry and textures still come from SternenfrontArt.java, so a body changes in one place: run that first, then this.
The joints match RigBody. Animations are written here as keyframes: BetterModel plays idle, walk and death by itself, and
the plugin asks for shoot. Forward is +z and a body's right side is -x, as in the item models. BetterModel turns bones tagged
h_ with where the entity looks, and hi_ with their children too, which is how the arms and the gun follow a bot's aim.
"""
import base64
import json
import math
import struct
import uuid
from pathlib import Path

HERE = Path(__file__).parent
ASSETS = HERE / "src/assets/blockcraft"
OUT = HERE / "bettermodel"
NAMESPACE = uuid.UUID("9b1f6f4e-0d3a-4c55-9a55-6f6f7a1c2b10")
WEAPON_SCALE = 0.65

# Copied from RigBody: arm lift in radians, and scale and torso lean where a body has them.
BODIES = {
    "hegemonie_soldier": ("e5", dict(shoulderX=.25, shoulderY=1.47, hipX=.085, hipY=.96, torsoY=.98, headY=1.64, weaponX=-.22, weaponY=1.3, weaponZ=.53, lift=-1.1)),
    "hegemonie_heavy": (None, dict(shoulderX=.47, shoulderY=1.46, shoulderZ=.14, hipX=.22, hipY=.78, torsoY=.8, lean=.26, headY=1.32, headZ=.3, weaponX=0, weaponY=0, weaponZ=0, lift=-1.45, scale=1.25)),
    "hegemonie_scout": ("e5s", dict(shoulderX=.25, shoulderY=1.47, hipX=.085, hipY=.96, torsoY=.98, headY=1.64, weaponX=-.22, weaponY=1.3, weaponZ=.53, lift=-1.1)),
    "hegemonie_medic": ("se14", dict(shoulderX=.2, shoulderY=1.44, hipX=.09, hipY=.96, torsoY=.98, headY=1.58, weaponX=-.17, weaponY=1.3, weaponZ=.55, lift=-1.15)),
    "kolonien_soldier": ("carbine", dict(shoulderX=.3, shoulderY=1.4, hipX=.105, hipY=.95, torsoY=.97, headY=1.5, weaponX=-.26, weaponY=1.27, weaponZ=.53, lift=-1.1)),
    "kolonien_heavy": ("repeater", dict(shoulderX=.3, shoulderY=1.4, hipX=.105, hipY=.95, torsoY=.97, headY=1.5, weaponX=-.26, weaponY=1.27, weaponZ=.53, lift=-1.1)),
    "kolonien_scout": ("long_rifle", dict(shoulderX=.24, shoulderY=1.38, hipX=.085, hipY=.95, torsoY=.97, headY=1.51, weaponX=-.2, weaponY=1.25, weaponZ=.55, lift=-1.15)),
    "kolonien_medic": ("pistol", dict(shoulderX=.28, shoulderY=1.4, hipX=.1, hipY=.95, torsoY=.97, headY=1.5, weaponX=-.24, weaponY=1.27, weaponZ=.53, lift=-1.15)),
}
OUTLINED = "_outlined"


def stable(*parts):
    return str(uuid.uuid5(NAMESPACE, "/".join(map(str, parts))))


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
        self.bones[name] = dict(name=name, parent=parent, pivot=[pivot[0] * 16, pivot[1] * 16, pivot[2] * 16], children=[])
        if parent:
            self.bones[parent]["children"].append(self.bones[name])
        return name

    def add(self, bone, model_path, at, scale=1.0):
        """Places an item model's elements so its centre, where the item display's origin sits, is at the joint."""
        model = json.loads((ASSETS / "models/item" / f"{model_path}.json").read_text())
        textures = model["textures"]
        place = lambda c: [at[i] * 16 + (c[i] - 8) * scale for i in range(3)]
        for index, element in enumerate(model["elements"]):
            faces = {}
            for side, face in element["faces"].items():
                reference = textures[face["texture"].lstrip("#")]
                texture = self.texture(reference)
                sx, sy = texture["width"] / 16, texture["height"] / 16
                u1, v1, u2, v2 = face["uv"]
                faces[side] = dict(uv=[u1 * sx, v1 * sy, u2 * sx, v2 * sy], texture=texture["index"], rotation=face.get("rotation", 0))
            origin, rotation = place(element["from"]), [0, 0, 0]
            spin = element.get("rotation")
            if spin:
                origin = place(spin["origin"])
                rotation["xyz".index(spin["axis"])] = spin["angle"]
            cube = dict(name=f"{bone}_{index}", box_uv=False, rescale=False, locked=False, render_order="default",
                        allow_mirror_modeling=True, autouv=0, color=0, type="cube",
                        **{"from": place(element["from"]), "to": place(element["to"])}, origin=origin, rotation=rotation,
                        faces=faces, uuid=stable(self.family, bone, model_path, index))
            self.elements.append(cube)
            self.bones[bone].setdefault("cubes", []).append(cube["uuid"])

    def outline(self, bone):
        return dict(uuid=stable(self.family, "bone", bone["name"]), isOpen=True,
                    children=[child for child in bone.get("cubes", [])] + [self.outline(child) for child in bone["children"]])

    def groups(self):
        return [dict(name=bone["name"], origin=bone["pivot"], rotation=bone.get("rotation", [0, 0, 0]), color=0, uuid=stable(self.family, "bone", bone["name"]),
                     export=True, mirror_uv=False, isOpen=True, locked=False, visibility=True, autouv=0, selected=False)
                for bone in self.bones.values()]


def keyframes(family, bone, channel, points):
    """{time: (x, y, z)}"""
    return [dict(channel=channel, data_points=[dict(x=str(round(x, 3)), y=str(round(y, 3)), z=str(round(z, 3)))],
                 uuid=stable(family, bone, channel, time), time=time, color=-1, interpolation="catmullrom")
            for time, (x, y, z) in points.items()]


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
    grip = {"rotation": still(x=grip_degrees)}
    across = grip_degrees + 8
    support = {"rotation": still(x=across, y=22)}
    return [
        animation(family, "idle", "loop", 2.4, {
            "torso": {"position": {0: (0, 0, 0), 1.2: (0, -0.2, 0), 2.4: (0, 0, 0)}},
            "hi_right_arm": {"rotation": {0: (grip_degrees, 0, 0), 1.2: (grip_degrees + 2, 0, 0), 2.4: (grip_degrees, 0, 0)}},
            "h_left_arm": support,
        }),
        animation(family, "walk", "loop", 0.8, {
            "left_leg": {"rotation": {0: (32, 0, 0), 0.4: (-32, 0, 0), 0.8: (32, 0, 0)}},
            "right_leg": {"rotation": {0: (-32, 0, 0), 0.4: (32, 0, 0), 0.8: (-32, 0, 0)}},
            "torso": {"position": {0: (0, 0, 0), 0.2: (0, 0.5, 0), 0.4: (0, 0, 0), 0.6: (0, 0.5, 0), 0.8: (0, 0, 0)},
                      "rotation": {0: (4, 3, 0), 0.4: (4, -3, 0), 0.8: (4, 3, 0)}},
            "hi_right_arm": grip,
            "h_left_arm": support,
        }),
        animation(family, "shoot", "once", 0.2, {
            "hi_right_arm": {"rotation": {0: (grip_degrees, 0, 0), 0.05: (grip_degrees - 9, 0, 0), 0.2: (grip_degrees, 0, 0)}},
            "h_left_arm": {"rotation": {0: (across, 22, 0), 0.05: (across - 9, 22, 0), 0.2: (across, 22, 0)}},
            "torso": {"rotation": {0: (0, 0, 0), 0.05: (-3, 0, 0), 0.2: (0, 0, 0)}},
        }),
        animation(family, "death", "once", 0.9, {
            "root": {"rotation": {0: (0, 0, 0), 0.25: (-18, 0, 4), 0.7: (-86, 0, 8), 0.9: (-84, 0, 8)},
                     "position": {0: (0, 0, 0), 0.7: (0, -3, -4), 0.9: (0, -3, -4)}},
            "hi_right_arm": {"rotation": {0: (grip_degrees, 0, 0), 0.5: (-20, 0, -25)}},
            "h_left_arm": {"rotation": {0: (across, 22, 0), 0.5: (-30, 0, 30)}},
            "right_leg": {"rotation": {0: (0, 0, 0), 0.6: (22, 0, 0)}},
            "left_leg": {"rotation": {0: (0, 0, 0), 0.6: (-12, 0, 0)}},
        }),
    ]


def build(family, blaster, joints):
    scale = joints.get("scale", 1)
    grip_degrees = round(math.degrees(joints["lift"]))
    shoulder = (joints["shoulderX"] * scale, joints["shoulderY"] * scale, joints.get("shoulderZ", 0) * scale)
    hip = (joints["hipX"] * scale, joints["hipY"] * scale, 0)
    torso_at = (0, joints["torsoY"] * scale, 0)
    head_at = (0, joints["headY"] * scale, joints.get("headZ", 0) * scale)
    model = Model(family)
    model.bone("root", None, (0, 0, 0))
    model.bone("torso", "root", torso_at)
    model.bones["torso"]["rotation"] = [math.degrees(joints.get("lean", 0)), 0, 0]
    model.bone("h_head", "root", head_at)
    model.bone("h_left_arm", "root", shoulder)
    model.bone("hi_right_arm", "root", (-shoulder[0], shoulder[1], shoulder[2]))
    model.bone("left_leg", "root", hip)
    model.bone("right_leg", "root", (-hip[0], hip[1], 0))
    parts = f"sternenfront/body/{family}"
    model.add("torso", f"{parts}/torso", torso_at, scale)
    model.add("h_head", f"{parts}/head", head_at, scale)
    model.add("h_left_arm", f"{parts}/arm", shoulder, scale)
    model.add("hi_right_arm", f"{parts}/arm", (-shoulder[0], shoulder[1], shoulder[2]), scale)
    model.add("left_leg", f"{parts}/leg", hip, scale)
    model.add("right_leg", f"{parts}/leg", (-hip[0], hip[1], 0), scale)
    if blaster:
        # The gun hangs off the arm, which the animations hold out, so its bone turns back by the same angle to lie
        # level with the joints' weapon position when that grip is held.
        turn = math.radians(-grip_degrees)
        forward, up = joints["weaponZ"] * scale - shoulder[2], joints["weaponY"] * scale - shoulder[1]
        grip = (joints["weaponX"] * scale, shoulder[1] + up * math.cos(turn) - forward * math.sin(turn),
                shoulder[2] + up * math.sin(turn) + forward * math.cos(turn))
        model.bone("weapon", "hi_right_arm", grip)
        model.bones["weapon"]["rotation"] = [-grip_degrees, 0, 0]
        model.add("weapon", f"sternenfront/{blaster}", grip, WEAPON_SCALE)
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
    for family, (blaster, joints) in BODIES.items():
        model = json.dumps(build(family, blaster, joints), separators=(",", ":"))
        for name in (family, family + OUTLINED):
            (OUT / f"{name}.bbmodel").write_text(model)
    print(f"Wrote {len(BODIES)} bodies, each plain and outlined")


if __name__ == "__main__":
    main()

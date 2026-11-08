"""Checks generated gun poses, guarded blades and their vanilla item routes after generateBattle."""
import json
import math
import re
import unittest
from pathlib import Path

PACK = Path(__file__).parent / "src/assets"
REGISTRY = (Path(__file__).parent.parent / "paper/api/src/main/java/me/blockcraft/api/pack/PackModel.java").read_text()
GENERATOR = (Path(__file__).parent / "SternenfrontArt.java").read_text()
HANDS = ("firstperson_righthand", "firstperson_lefthand")
HOLDS = ("_firing", "_lowered", "_aim", "_firing_aim")
# FirstPersonHandsAndItemsRenderer in the 26.3 client: the arm's offset, then the turn it gives a blocking non-shield.
VANILLA_ARM_SHIFT = (0.56, -0.52, -0.72)
VANILLA_SWORD_BLOCK_SHIFT = (-0.14142136, 0.08, 0.14142136)
VANILLA_SWORD_BLOCK_DEGREES = (-102.25, 13.365, 78.05)
# The hand draws with a fixed 70 degree vertical field of view, and no window is narrower than it is tall.
HAND_VIEW_SLOPE = math.tan(math.radians(35))


def read_model(path):
    return json.loads((PACK / "blockcraft/models/item" / f"{path}.json").read_text())


def barrel_elevation(pose, left=False):
    pitch, yaw, _ = map(math.radians, pose["rotation"])
    if left:
        yaw = -yaw
    # ItemTransform rotates XYZ; the authored barrel points along +Z, so roll does not affect its axis.
    x = math.sin(yaw)
    y = -math.sin(pitch) * math.cos(yaw)
    z = math.cos(pitch) * math.cos(yaw)
    return math.degrees(math.atan2(y, math.hypot(x, z)))


def turned(vector, degrees):
    """Rotates as the client's rotationXYZ does: about Z first, then Y, then X."""
    x, y, z = vector
    rx, ry, rz = map(math.radians, degrees)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return [x, y, z]


def displayed(pose, pixel):
    point = [pose["scale"][axis] * (pixel[axis] / 16 - 0.5) for axis in range(3)]
    return [a + b / 16 for a, b in zip(turned(point, pose["rotation"]), pose["translation"])]


def held_view(pose, pixel):
    """Where the client draws a model pixel in the right hand, the camera looking along -Z."""
    return [a + b for a, b in zip(displayed(pose, pixel), VANILLA_ARM_SHIFT)]


def blocking_view(pose, pixel):
    """Where the client draws a model pixel in the right hand while the item blocks, the camera looking along -Z."""
    point = [a + b for a, b in zip(turned(displayed(pose, pixel), VANILLA_SWORD_BLOCK_DEGREES), VANILLA_SWORD_BLOCK_SHIFT)]
    return [a + b for a, b in zip(point, VANILLA_ARM_SHIFT)]


def sight(model):
    """The rear of the top of the gun's centre line, as SternenfrontArt's Sight finds it."""
    centre = [element for element in model["elements"]
              if "rotation" not in element and element["from"][0] <= 8 <= element["to"][0]]
    top = max(element["to"][1] for element in centre)
    return [8, top, min(element["from"][2] for element in centre if element["to"][1] == top)]


class WeaponPoses(unittest.TestCase):
    def test_registered_pose_models_and_item_routes(self):
        weapons = re.findall(r'Material\.(\w+), "(sternenfront/\w+)_firing"', REGISTRY)
        generated = re.findall(r'gun\("(\w+)", .+, GunPose\.\w+\)', GENERATOR)
        self.assertEqual(sorted(path for _, path in weapons), sorted(f"sternenfront/{name}" for name in generated))
        self.assertEqual(sorted(re.findall(r'Material\.(\w+), "(sternenfront/\w+)_lowered"', REGISTRY)), sorted(weapons))
        for material, path in weapons:
            with self.subTest(weapon=path):
                rest = read_model(path)
                self.assertEqual(rest["display"][HANDS[0]], rest["display"][HANDS[1]])
                for hold in HOLDS:
                    held = read_model(path + hold)
                    self.assertEqual(held["parent"], f"blockcraft:item/{path}")
                    self.assertEqual(set(held["display"]), set(HANDS))
                    self.assertNotIn("elements", held)
                    self.assertEqual(held["display"][HANDS[0]], held["display"][HANDS[1]])
                    for hand in HANDS:
                        self.assertNotEqual(rest["display"][hand], held["display"][hand])
                        self.assertEqual(rest["display"][hand]["scale"], held["display"][hand]["scale"])
                for texture in rest["textures"].values():
                    namespace, texture_path = texture.split(":")
                    self.assertTrue((PACK / namespace / "textures" / f"{texture_path}.png").is_file())
                route = json.loads((PACK / f"minecraft/items/{material.lower()}.json").read_text())
                serialized = json.dumps(route)
                for suffix in ("",) + HOLDS:
                    self.assertIn(f'"blockcraft:item/{path}{suffix}"', serialized)

    def test_registered_and_generated_guards_agree(self):
        registered = re.findall(r'"(sternenfront/\w+)", (?:true|false), Stance\.GUARD\)', REGISTRY)
        generated = re.findall(r'"(sternenfront/\w+)"', re.search(r"GUARDED = List\.of\(([^)]*)\)", GENERATOR).group(1))
        self.assertTrue(registered)
        self.assertEqual(sorted(registered), sorted(generated))

    def test_guarded_blades_draw_lit_and_in_view(self):
        for material, path in re.findall(r'Material\.(\w+), "(sternenfront/\w+)", (?:true|false), Stance\.GUARD\)', REGISTRY):
            with self.subTest(blade=path):
                rest, guard = read_model(path), read_model(path + "_guard")
                self.assertEqual(guard["parent"], f"blockcraft:item/{path}")
                self.assertEqual(guard["display"][HANDS[0]], guard["display"][HANDS[1]])
                textures = rest["textures"] | guard.get("textures", {})
                faces = [face for element in guard.get("elements", rest["elements"]) for face in element["faces"].values()]
                for face in faces:
                    namespace, texture_path = textures[face["texture"].lstrip("#")].split(":")
                    self.assertTrue((PACK / namespace / "textures" / f"{texture_path}.png").is_file())
                route = json.loads((PACK / f"minecraft/items/{material.lower()}.json").read_text())
                case = next(case["model"] for case in route["model"]["cases"] if case["when"] == f"blockcraft:{path}")
                self.assertEqual(case["property"], "minecraft:using_item")
                self.assertEqual(case["on_true"]["model"], f"blockcraft:item/{path}_guard")
                self.assertEqual(case["on_true"].get("tints"), case["on_false"].get("tints"))
                if any("tintindex" in face for face in faces):
                    self.assertTrue(case["on_true"].get("tints"))
                elements = rest["elements"]
                low = min(element["from"][1] for element in elements)
                high = max(element["to"][1] for element in elements)
                for y in ((low + high) / 2, high):
                    x, y, z = blocking_view(guard["display"][HANDS[0]], [8, y, 8])
                    self.assertLess(z, 0)
                    self.assertLess(abs(x / z), HAND_VIEW_SLOPE)
                    self.assertLess(abs(y / z), HAND_VIEW_SLOPE)

    def test_family_framing_recoil_and_lowering(self):
        def pose(name, firing=False):
            return read_model("sternenfront/" + name + ("_firing" if firing else ""))["display"][HANDS[0]]

        families = ("carbine", "heavy_rifle", "long_rifle", "pistol", "twin_pistol", "repeater", "wrist_blaster")
        self.assertEqual(len({json.dumps(pose(name)) for name in families}), len(families))
        for name in families:
            for hand in HANDS:
                with self.subTest(weapon=name, hand=hand):
                    rest = read_model("sternenfront/" + name)["display"][hand]
                    firing = read_model("sternenfront/" + name + "_firing")["display"][hand]
                    lowered = read_model("sternenfront/" + name + "_lowered")["display"][hand]
                    self.assertGreater(barrel_elevation(firing, hand == HANDS[1]),
                                       barrel_elevation(rest, hand == HANDS[1]))
                    self.assertLess(barrel_elevation(lowered, hand == HANDS[1]),
                                    barrel_elevation(rest, hand == HANDS[1]))
        pistol_kick = barrel_elevation(pose("pistol", True)) - barrel_elevation(pose("pistol"))
        rotary_kick = barrel_elevation(pose("repeater", True)) - barrel_elevation(pose("repeater"))
        self.assertGreater(pistol_kick, rotary_kick)
        self.assertLess(pose("repeater")["translation"][1], pose("carbine")["translation"][1])
        self.assertGreater(pose("repeater")["scale"][0], pose("carbine")["scale"][0])

    def test_aim_puts_the_sights_under_the_crosshair(self):
        for path in re.findall(r'"(sternenfront/\w+)", true, Stance\.AIM\)', REGISTRY):
            if path.endswith("_firing"):
                continue
            with self.subTest(weapon=path):
                model = read_model(path)
                aim = read_model(path + "_aim")["display"][HANDS[0]]
                x, y, z = held_view(aim, sight(model))
                self.assertAlmostEqual(z, -0.5, places=2)
                self.assertTrue(-0.05 < y < 0, y)
                self.assertLess(abs(x), 0.2 if "pistol" in path else 0.001)
                fired = read_model(path + "_firing_aim")["display"][HANDS[0]]
                self.assertGreater(barrel_elevation(fired), barrel_elevation(aim))
                self.assertEqual(aim["scale"], model["display"][HANDS[0]]["scale"])

    def test_wrist_forearm_and_vanilla_bounds(self):
        wrist = read_model("sternenfront/wrist_blaster")
        self.assertTrue(any(element["to"][2] < 0 for element in wrist["elements"]))
        for element in wrist["elements"]:
            for coordinate in element["from"] + element["to"]:
                self.assertGreaterEqual(coordinate, -16)
                self.assertLessEqual(coordinate, 32)


if __name__ == "__main__":
    unittest.main()

"""Checks generated gun poses and their vanilla item routes after generateBattle."""
import json
import math
import re
import unittest
from pathlib import Path

PACK = Path(__file__).parent / "src/assets"
REGISTRY = Path(__file__).parent.parent / "paper/api/src/main/java/me/blockcraft/api/pack/PackModel.java"
HANDS = ("firstperson_righthand", "firstperson_lefthand")
HOLDS = ("_firing", "_lowered")


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


class WeaponPoses(unittest.TestCase):
    def test_registered_pose_models_and_item_routes(self):
        weapons = re.findall(r'Material\.(\w+), "(sternenfront/\w+)_firing"', REGISTRY.read_text())
        self.assertEqual(len(weapons), 11)
        self.assertEqual(sorted(re.findall(r'Material\.(\w+), "(sternenfront/\w+)_lowered"', REGISTRY.read_text())), sorted(weapons))
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

    def test_wrist_forearm_and_vanilla_bounds(self):
        wrist = read_model("sternenfront/wrist_blaster")
        self.assertTrue(any(element["to"][2] < 0 for element in wrist["elements"]))
        for element in wrist["elements"]:
            for coordinate in element["from"] + element["to"]:
                self.assertGreaterEqual(coordinate, -16)
                self.assertLessEqual(coordinate, 32)


if __name__ == "__main__":
    unittest.main()

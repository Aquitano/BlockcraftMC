"""Checks generated gun poses and their vanilla item routes after generateBattle."""
import json
import re
import unittest
from pathlib import Path

PACK = Path(__file__).parent / "src/assets"
REGISTRY = Path(__file__).parent.parent / "paper/api/src/main/java/me/blockcraft/api/pack/PackModel.java"
HANDS = ("firstperson_righthand", "firstperson_lefthand")


def read_model(path):
    return json.loads((PACK / "blockcraft/models/item" / f"{path}.json").read_text())


class WeaponPoses(unittest.TestCase):
    def test_registered_firing_models_and_item_routes(self):
        weapons = re.findall(r'Material\.(\w+), "(sternenfront/\w+)_firing"', REGISTRY.read_text())
        self.assertEqual(len(weapons), 11)
        for material, path in weapons:
            with self.subTest(weapon=path):
                rest, firing = read_model(path), read_model(path + "_firing")
                self.assertEqual(firing["parent"], f"blockcraft:item/{path}")
                self.assertEqual(set(firing["display"]), set(HANDS))
                self.assertNotIn("elements", firing)
                self.assertEqual(rest["display"][HANDS[0]], rest["display"][HANDS[1]])
                self.assertEqual(firing["display"][HANDS[0]], firing["display"][HANDS[1]])
                for hand in HANDS:
                    self.assertNotEqual(rest["display"][hand], firing["display"][hand])
                    self.assertEqual(rest["display"][hand]["scale"], firing["display"][hand]["scale"])
                for texture in rest["textures"].values():
                    namespace, texture_path = texture.split(":")
                    self.assertTrue((PACK / namespace / "textures" / f"{texture_path}.png").is_file())
                route = json.loads((PACK / f"minecraft/items/{material.lower()}.json").read_text())
                serialized = json.dumps(route)
                for suffix in ("", "_firing"):
                    self.assertIn(f'"blockcraft:item/{path}{suffix}"', serialized)

    def test_family_framing_and_recoil(self):
        def pose(name, firing=False):
            return read_model("sternenfront/" + name + ("_firing" if firing else ""))["display"][HANDS[0]]

        families = ("carbine", "heavy_rifle", "long_rifle", "pistol", "twin_pistol", "repeater", "wrist_blaster")
        self.assertEqual(len({json.dumps(pose(name)) for name in families}), len(families))
        pistol_kick = abs(pose("pistol", True)["rotation"][0] - pose("pistol")["rotation"][0])
        rotary_kick = abs(pose("repeater", True)["rotation"][0] - pose("repeater")["rotation"][0])
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

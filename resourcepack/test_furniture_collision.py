import unittest
from pathlib import Path
import yaml

from FurnitureCollision import shared, planet, actor


class FurnitureCollisionTest(unittest.TestCase):
    def test_lobby_furniture_has_bounded_standing_surfaces(self):
        definitions = yaml.safe_load((Path(__file__).parent.parent / 'nexo/items/blockcraft_imported.yml').read_text())
        for name, top, limit in [('market_bread_table', .75, 1), ('market_carrot_table', .75, 1),
                                 ('market_market_stall', .875, 3), ('halloween_fence', 1, 5),
                                 ('halloween_fence_long', 1, 8)]:
            with self.subTest(name=name):
                cubes = definitions['blockcraft_' + name]['Mechanics']['furniture']['blockcraft_collision']['cubes']
                self.assertLessEqual(len(cubes), limit)
                self.assertTrue(cubes)
                for cube in cubes:
                    point, size = cube.split(' ')
                    self.assertAlmostEqual(float(point.split(',')[1])+float(size), top)
        self.assertNotIn('blockcraft_collision', definitions['blockcraft_halloween_candle']['Mechanics']['furniture'])

    def test_decorative_lights_have_no_collision(self):
        for name in ('ceiling_lamp', 'wall_lamp', 'landing_lamp', 'warning_beacon'):
            with self.subTest(name=name):
                shape = shared(name, [])
                self.assertEqual(shape['blocks'], [])
                self.assertEqual(shape['cubes'], [])

    def test_roof_terraces_have_four_jumpable_supports(self):
        shape, _ = planet('desert/roof_step')
        self.assertEqual(len(shape['cubes']), 4)
        tops = {round(float(c.split(' ')[0].split(',')[1])+float(c.split(' ')[1]), 6) for c in shape['cubes']}
        self.assertEqual(tops, {.5, .875})

    def test_recessed_entrance_keeps_player_clearance(self):
        shape, _ = planet('desert/entrance_recess')
        left, right = [], []
        for cube in shape['cubes']:
            point, size = cube.split(' '); x = float(point.split(',')[0]); size = float(size)
            (left if x < 0 else right).append(x + size/2 if x < 0 else x-size/2)
        self.assertGreaterEqual(min(right)-max(left), .8)
        self.assertLessEqual(len(shape['cubes']), 6)

    def test_equipment_preserves_its_standing_height(self):
        for name, top in [('water_vaporator', .625), ('water_cistern', 1.375), ('water_pump', .875)]:
            shape, _ = planet('desert/'+name)
            tops = [float(c.split(' ')[0].split(',')[1])+float(c.split(' ')[1]) for c in shape['cubes']]
            self.assertAlmostEqual(max(tops), top)
        self.assertLessEqual(len(actor('gonk')['cubes']), 2)


if __name__ == '__main__':
    unittest.main()

import unittest
import json
from pathlib import Path

from PIL import Image



class ImportedSurfaceTest(unittest.TestCase):
    def test_exported_props_keep_their_opaque_surfaces(self):
        assets = Path(__file__).parent / 'src/assets/blockcraft'
        expected = {'market_crate_stack': 108, 'market_bunting_posts': 78,
                    'market_bunting': 54, 'halloween_scarecrow': 47,
                    'halloween_grave_cross': 22, 'quest_board': 28,
                    'halloween_candle': 16, 'market_coins_medium': 36,
                    'market_fish_tray': 62}
        for name, minimum in expected.items():
            with self.subTest(prop=name):
                model = json.loads((assets / f'models/item/props/imported/{name}.json').read_text())
                textures = {}
                for key, value in model['textures'].items():
                    with Image.open(assets / 'textures' / (value.removeprefix('blockcraft:') + '.png')) as texture:
                        textures[key] = texture.convert('RGBA')
                opaque = 0
                for element in model['elements']:
                    for face in element['faces'].values():
                        texture = textures[face['texture'][1:]]
                        u0, v0, u1, v1 = face['uv']
                        x = min(texture.width - 1, int((u0 + u1) / 32 * texture.width))
                        y = min(texture.height - 1, int((v0 + v1) / 32 * texture.height))
                        opaque += texture.getpixel((x, y))[3] > 127
                self.assertGreaterEqual(opaque, minimum)

    def samples(self, route):
        assets = Path(__file__).parent / 'src/assets/blockcraft'
        model = json.loads((assets / ('models/item/' + route + '.json')).read_text())
        textures = {}
        for name, path in model['textures'].items():
            with Image.open(assets / 'textures' / (path.removeprefix('blockcraft:') + '.png')) as image:
                textures[name] = image.convert('RGBA')
        for element in model['elements']:
            for face in element['faces'].values():
                texture = textures[face['texture'][1:]]
                u, v, U, V = face['uv']
                yield texture.getpixel((min(texture.width - 1, int((u + U) / 32 * texture.width)),
                                        min(texture.height - 1, int((v + V) / 32 * texture.height))))

    def test_starwars_solid_faces_do_not_sample_transparency(self):
        for name in ('ee3', 'acklay', 'nexu'):
            with self.subTest(model=name):
                samples = list(self.samples('props/starwars/' + name))
                self.assertGreater(len(samples), 6)
                self.assertTrue(all(pixel[3] == 255 for pixel in samples[:6]))

    def test_candle_body_faces_sample_wax_instead_of_transparency(self):
        samples = list(self.samples('props/imported/halloween_candle'))
        self.assertEqual(len(samples), 16)
        for red, green, blue, alpha in samples[:12]:
            self.assertEqual(alpha, 255)
            self.assertGreater(min(red, green, blue), 180)


if __name__ == '__main__':
    unittest.main()

import copy
import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from split_pack import STACKS, build, encoded, owner, verify

SOURCE = Path(__file__).parent / 'src'


class SplitPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files, cls.packs = build(SOURCE)

    def test_profiles_preserve_routes_and_dependencies(self):
        verify(self.files, self.packs)
        path = 'assets/minecraft/items/blaze_rod.json'
        original = json.loads(self.files[path])
        for profile, stack in STACKS.items():
            effective = {key: value for layer in stack for key, value in self.packs[layer].items()}
            actual = json.loads(effective[path])
            self.assertEqual(actual['model'].get('fallback', actual['model']), original['model']['fallback'])
        self.assertEqual(STACKS['lobby'], ('base', 'lobby'))

    def test_empty_selectors_use_vanilla_fallback(self):
        path = 'assets/minecraft/items/blaze_rod.json'
        original = json.loads(self.files[path])
        self.assertEqual(json.loads(self.packs['base'][path])['model'], original['model']['fallback'])

    def test_missing_dependency_is_rejected(self):
        packs = copy.deepcopy(self.packs)
        del packs['base']['assets/blockcraft/textures/font/text.png']
        with self.assertRaisesRegex(ValueError, 'requires missing'):
            verify(self.files, packs)

    def test_global_hud_is_starwars_only(self):
        shader = 'assets/minecraft/shaders/core/text.vsh'
        self.assertNotIn(b'blockcraft_panel_', self.packs['base'][shader])
        self.assertIn(b'blockcraft_panel_', self.packs['starwars'][shader])
        for path in self.files:
            if '/boss_bar/' in path or '/bettermodel/' in path:
                self.assertIn(path, self.packs['starwars'])
                self.assertNotIn(path, self.packs['base'])

    def test_new_objectives_and_imported_furniture_follow_profile(self):
        for kind in ('models/item', 'items', 'textures/item'):
            self.assertEqual(owner(f'assets/blockcraft/{kind}/sternenfront/objectives/comms_console.json'), 'starwars')
            self.assertEqual(owner(f'assets/blockcraft/{kind}/props/imported/halloween/new_prop.json'), 'lobby')

    def test_lobby_and_starwars_selectors_keep_only_their_own_cases(self):
        item = 'assets/minecraft/items/paper.json'
        shader = 'assets/minecraft/shaders/core/text.vsh'
        fixtures = {'pack.mcmeta': self.files['pack.mcmeta'], shader: self.files[shader]}
        fixtures.update({path: data for path, data in self.files.items()
                         if '/shaders/include/' in path})
        cases = []
        for name, model in (('base', 'item/shared'), ('lobby', 'item/props/market/test'),
                            ('starwars', 'item/sternenfront/objectives/test')):
            fixtures[f'assets/blockcraft/models/{model}.json'] = encoded({'parent': 'minecraft:item/generated'})
            cases.append({'when': f'blockcraft:{name}/test', 'model': {
                'type': 'minecraft:model', 'model': f'blockcraft:{model}'}})
        fallback = {'type': 'minecraft:model', 'model': 'minecraft:item/paper'}
        fixtures[item] = encoded({'model': {'type': 'minecraft:select',
            'property': 'minecraft:custom_model_data', 'cases': cases, 'fallback': fallback}})
        with TemporaryDirectory() as directory:
            source = Path(directory)
            for path, data in fixtures.items():
                target = source / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            files, packs = build(source)
        verify(files, packs)
        self.assertEqual(json.loads(packs['lobby'][item])['model']['cases'], cases[:2])
        self.assertEqual(json.loads(packs['starwars'][item])['model']['cases'], [cases[0], cases[2]])
        self.assertEqual(json.loads(packs['base'][item])['model']['cases'], [cases[0]])


if __name__ == '__main__':
    unittest.main()

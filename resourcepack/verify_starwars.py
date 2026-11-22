import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / 'src/assets/blockcraft'
catalog = json.loads((ROOT / 'catalogs/starwars.json').read_text())
config = (ROOT.parent / 'nexo/items/blockcraft_starwars.yml').read_text()
for prop in catalog:
    name = prop['id']
    assert f'{name}:\n' in config
    item = json.loads((ASSETS / ('items/' + prop['model'] + '.json')).read_text())
    path = item['model']['model'].removeprefix('blockcraft:')
    model = json.loads((ASSETS / 'models' / (path + '.json')).read_text())
    for texture in model['textures'].values():
        assert (ASSETS / 'textures' / (texture.removeprefix('blockcraft:') + '.png')).is_file()
    for element in model['elements']:
        assert all(-16 <= v <= 32 for key in ('from', 'to') for v in element[key]), name
        for face in element['faces'].values():
            assert face['texture'][1:] in model['textures']
            assert all(0 <= v <= 16 for v in face['uv']), name
    if prop['kind'] == 'creature':
        for bone in prop['bones']:
            route = json.loads((ROOT / f'src/assets/bettermodel/items/model/{prop["rig"]}_{bone}.json').read_text())
            target = route['model']['model'].removeprefix('blockcraft:')
            assert (ASSETS / 'models' / (target + '.json')).is_file()
        rig = json.loads((ROOT / f'bettermodel/{prop["rig"]}.bbmodel').read_text())
        assert {a['name'] for a in rig['animations']} >= {'idle', 'walk', 'attack', 'damage', 'death'}
        bones = {b['uuid'] for b in rig['groups']}
        for animation in rig['animations']:
            assert set(animation['animators']) <= bones
        assert set(prop['bones']) <= {group['name'] for group in rig['groups']}, name
print(f'Validated {len(catalog)} Star Wars models and five animation sets.')

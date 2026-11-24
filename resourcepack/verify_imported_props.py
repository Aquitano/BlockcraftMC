import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / 'src/assets/blockcraft'
catalog = json.loads((ROOT / 'catalogs/furniture.json').read_text())
config = (ROOT.parent / 'nexo/items/blockcraft_imported.yml').read_text()
count = 0
for prop in catalog:
    name = prop['id']
    assert f'{name}:\n' in config, name
    item = json.loads((ASSETS / ('items/' + prop['model'] + '.json')).read_text())
    path = item['model']['model'].removeprefix('blockcraft:')
    model = json.loads((ASSETS / 'models' / (path + '.json')).read_text())
    for texture in model['textures'].values():
        assert (ASSETS / 'textures' / (texture.removeprefix('blockcraft:') + '.png')).is_file(), texture
    for element in model['elements']:
        assert all(-16 <= v <= 32 for key in ('from', 'to') for v in element[key]), name
        for face in element['faces'].values():
            assert face['texture'][1:] in model['textures'], name
            assert all(0 <= v <= 16 for v in face['uv']), name
            assert face.get('rotation', 0) in (0, 90, 180, 270), name
        count += 1
print(f'Validated {len(catalog)} imported items, {count} faces, textures, bounds, and Nexo definitions.')

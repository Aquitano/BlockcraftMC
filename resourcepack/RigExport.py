"""Native client assets for authored cube rigs, normalized like BetterModel 3.6 bones."""
import base64
import json
from pathlib import Path


def native_assets(name: str, model: dict) -> dict[str, bytes]:
    groups = {group['uuid']: group for group in model['groups']}
    elements = {element['uuid']: element for element in model['elements']}
    assets = {}

    def encoded(value):
        return json.dumps(value, separators=(',', ':')).encode()

    def visit(node):
        group = groups[node['uuid']]
        cubes = [elements[child] for child in node['children'] if isinstance(child, str)]
        visible = [cube for cube in cubes if any(face['texture'] is not None for face in cube['faces'].values())]
        if visible:
            pivot = group['origin']
            scale = max(1, max(abs(value - pivot[axis]) for cube in cubes for key in ('from', 'to')
                               for axis, value in enumerate(cube[key])) / 16)
            textures, native = {}, []
            for cube in visible:
                element = {key: [round((value - pivot[axis]) / scale + 8, 6)
                                 for axis, value in enumerate(cube[key])] for key in ('from', 'to')}
                if any(cube['rotation']):
                    element['rotation'] = dict(origin=[round((value - pivot[axis]) / scale + 8, 6)
                                                        for axis, value in enumerate(cube['origin'])],
                                               **dict(zip('xyz', cube['rotation'])))
                faces = {}
                for side, face in cube['faces'].items():
                    index = face['texture']
                    if index is None:
                        continue
                    texture = model['textures'][index]
                    texture_name = name + '_' + Path(texture['name']).stem
                    textures[str(index)] = 'bettermodel:item/' + texture_name
                    assets[f'assets/bettermodel/textures/item/{texture_name}.png'] = base64.b64decode(
                        texture['source'].split(',', 1)[1], validate=True)
                    faces[side] = dict(uv=[round(value * 16 / texture['width' if axis % 2 == 0 else 'height'], 6)
                                          for axis, value in enumerate(face['uv'])], texture=f'#{index}', tintindex=0)
                    if face.get('rotation', 0):
                        faces[side]['rotation'] = face['rotation']
                element['faces'] = faces
                native.append(element)
            textures['particle'] = next(iter(textures.values()))
            identifier = name + '_' + group['name']
            assets[f'assets/bettermodel/models/model/{identifier}_1.json'] = encoded(dict(textures=textures, elements=native))
            assets[f'assets/bettermodel/items/model/{identifier}.json'] = encoded({'model': {
                'type': 'model', 'model': f'bettermodel:model/{identifier}_1',
                'tints': [{'type': 'custom_model_data', 'default': 16777215}]}})
        for child in node['children']:
            if isinstance(child, dict):
                visit(child)

    for node in model['outliner']:
        visit(node)
    return assets


def export(source: Path, name: str, model: dict) -> None:
    for relative, data in native_assets(name, model).items():
        path = source / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

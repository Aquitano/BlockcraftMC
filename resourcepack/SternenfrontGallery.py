"""Builds single-display exhibition bodies and an inventory of Sternenfront's usable assets."""
import copy
import json
from AssetFiles import write_json as write
import re
from pathlib import Path

import yaml

ROOT = Path(__file__).parent
ASSETS = ROOT / 'src/assets/blockcraft'
MODELS = ASSETS / 'models/item'
RIG = yaml.safe_load((ROOT.parent / 'paper/sternenfront/src/main/resources/rig-bodies.yml').read_text())
NAMES = {
    'kolonien_soldier': 'Klontruppler · 181. Panzerdivision',
    'kolonien_heavy': 'Schwerer Klontruppler', 'kolonien_scout': 'Klon-Aufklärer', 'kolonien_medic': 'Klon-Sanitäter',
    'hegemonie_soldier': 'B1-Kampfdroide', 'hegemonie_heavy': 'B2-Superkampfdroide',
    'hegemonie_scout': 'B1-Aufklärer', 'hegemonie_medic': 'B1-Sanitätsdroide',
    'reinforcement_arc_trooper': 'ARC-Truppler', 'reinforcement_commando_droid': 'BX-Kommandodroide',
    'reinforcement_clone_commando': 'Klon-Kommando · Katarn-Rüstung',
    'hero_jedi': 'Obi-Wan Kenobi', 'hero_sith': 'Count Dooku',
    'hero_commander': 'Captain Rex', 'hero_bounty_hunter': 'Jango Fett',
}


def body(family, layout):
    joints = RIG['joints'][layout['joints']]
    shoulder, hip = joints['shoulder'], joints['hip']
    left = 'arm_left' if (MODELS / f'sternenfront/body/{family}/arm_left.json').exists() else 'arm'
    parts = [('torso', [0, joints['torso']['y'], 0]), ('head', [0, joints['head']['y'], joints['head']['z']]),
             ('arm', [-shoulder['x'], shoulder['y'], shoulder['z']]),
             (left, [shoulder['x'], shoulder['y'], shoulder['z']]),
             ('leg', [hip['x'], hip['y'], 0]), ('leg_right', [-hip['x'], hip['y'], 0])]
    model = dict(textures={}, elements=[])
    colour = 0x405748 if family == 'kolonien_soldier' else 0x30487A if family.startswith(('kolonien', 'reinforcement_arc', 'hero_commander')) else 0xE0B818
    for index, (part, offset) in enumerate(parts):
        for marked in (False, True):
            path = MODELS / f'sternenfront/body/{family}/{part}{"_marks" if marked else ""}.json'
            if not path.exists():
                continue
            source = json.loads(path.read_text())
            scale = layout.get('scale', 1) / 2
            def transform(point):
                return [round(8 + (point[i] - 8 + offset[i] * 16) * scale, 5) for i in range(3)]
            prefix = f'p{index}_{int(marked)}_'
            for key, value in source['textures'].items():
                model['textures'][prefix + key] = value
            for element in copy.deepcopy(source['elements']):
                element['from'], element['to'] = transform(element['from']), transform(element['to'])
                if 'rotation' in element:
                    element['rotation']['origin'] = transform(element['rotation']['origin'])
                for face in element['faces'].values():
                    face['texture'] = '#' + prefix + face['texture'].removeprefix('#')
                    if marked:
                        face['tintindex'] = 0
                    else:
                        face.pop('tintindex', None)
                model['elements'].append(element)
    model['textures']['particle'] = next(iter(model['textures'].values()))
    path = f'sternenfront/gallery/{family}'
    write(MODELS / f'{path}.json', model, compact=True)
    write(ASSETS / f'items/{path}.json', {'model': {'type': 'minecraft:model', 'model': f'blockcraft:item/{path}',
          'tints': [{'type': 'minecraft:constant', 'value': colour}]}}, compact=True)
    return dict(id=family, name=NAMES[family], category='Charaktere', item=f'blockcraft:{path}', scale=2)


def generate():
    entries = [body(family, layout) for family, layout in RIG['bodies'].items()]
    registry = (ROOT.parent / 'paper/api/src/main/java/me/blockcraft/api/pack/PackModel.java').read_text()
    messages = yaml.safe_load((ROOT.parent / 'paper/sternenfront/src/main/resources/messages.yml').read_text())
    paths = sorted(set(re.findall(r'STERNENFRONT_\w+\(Material\.\w+, "(sternenfront/[^"\n]+)"', registry)))
    paths += [str(path.relative_to(MODELS)).removesuffix('.json') for group in ('speeder', 'dropship', 'lander')
              for path in sorted((MODELS / f'sternenfront/{group}').glob('*.json'))]
    for path in dict.fromkeys(paths):
        name = path.rsplit('/', 1)[-1]
        if re.search(r'_(firing|lowered|aim|guard|off|25|50|75|door)$', name) or name.startswith(('bolt_', 'scorch_', 'spark_')):
            continue
        source = MODELS / f'{path}.json'
        if not source.exists():
            continue
        key = name.replace('_', '-')
        label = messages.get('blaster', {}).get(key, {}).get('name', name.replace('_', ' ').title())
        label = re.sub('<[^>]+>', '', label)
        category = 'Waffen' if key in messages.get('blaster', {}) or 'lightsaber' in name or name == 'relic_blade' else 'Ausrüstung'
        if any(f'/{group}/' in path for group in ('speeder', 'dropship', 'lander')):
            category = 'Fahrzeuge'
            label = path.removeprefix('sternenfront/').replace('/', ' · ').replace('_', ' ').title()
        route = 'sternenfront/gallery/' + path.removeprefix('sternenfront/').replace('/', '_')
        write(ASSETS / f'items/{route}.json', {'model': {'type': 'minecraft:model', 'model': f'blockcraft:item/{path}',
              'tints': [{'type': 'minecraft:constant', 'value': 0xFFFFFF}]}}, compact=True)
        entries.append(dict(id=route.rsplit('/', 1)[-1], name=label, category=category, item='blockcraft:' + route,
                            scale=2 if category == 'Fahrzeuge' else 1.3))
    for weapon in ('a280', 'e11d', 'dc17m'):
        write(ASSETS / f'items/props/starwars/{weapon}.json', {'model': {'type': 'minecraft:model',
              'model': f'blockcraft:item/sternenfront/{weapon}', 'tints': [{'type': 'minecraft:constant', 'value': 0xFFFFFF}]}}, compact=True)
    write(ROOT / 'build/sternenfront/gallery.json', entries, compact=True)
    print(f'Gallery: {len(entries)} models, including {len(RIG["bodies"])} complete characters')


if __name__ == '__main__':
    generate()

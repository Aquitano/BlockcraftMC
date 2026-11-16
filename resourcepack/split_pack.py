"""Build the network's split resource packs from an existing generated source tree."""
import argparse
import copy
import json
import re
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

PROFILES = ('base', 'lobby', 'modes', 'starwars')
STACKS = {'base': ('base',), 'lobby': ('base', 'lobby'),
          'modes': ('base', 'modes'), 'starwars': ('base', 'starwars')}
HUD_TEXTURES = ('ability_', 'minimap_', 'combat_', 'crosshair_', 'damage_arcs',
                'readiness', 'spread_ticks', 'objective_pips', 'hits', 'overlay_sternenfront_')


def owner(path):
    parts = path.split('/')
    if len(parts) < 4 or parts[0] != 'assets':
        return 'base'
    namespace, kind = parts[1:3]
    name = parts[-1]
    if namespace == 'bettermodel':
        return 'starwars'
    if namespace == 'minecraft':
        if '/boss_bar/' in path or '/post_effect/' in path or name in (
                'rendertype_outline.fsh', 'rendertype_lines.vsh'):
            return 'starwars'
        return 'base'
    if namespace != 'blockcraft':
        raise ValueError(f'Unclassified namespace: {path}')
    if any(part in ('sternenfront', 'deploy', 'battle', 'starwars') for part in parts[3:]):
        return 'starwars'
    if kind == 'font' and name in ('abilities.json', 'minimap.json'):
        return 'starwars'
    if kind == 'textures' and '/font/' in path and name.startswith(HUD_TEXTURES):
        return 'starwars'
    if kind == 'textures' and '/misc/' in path:
        return 'starwars'
    if 'target_outline' in name:
        return 'starwars'
    if 'props' in parts:
        return 'lobby'
    if kind in ('models', 'textures', 'items', 'font') and any(
            part in ('ttt', 'zombies', 'weapons') for part in parts[3:]):
        return 'modes'
    return 'base'


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def resource(identifier, kind, suffix):
    namespace, _, name = identifier.partition(':')
    if not name:
        namespace, name = 'minecraft', namespace
    return f'assets/{namespace}/{kind}/{name}{suffix}'


def references(path, data):
    if path.endswith(('.vsh', '.fsh', '.glsl')):
        for include in re.findall(r'#(?:moj_import|include)\s+<([^>]+)>', data.decode()):
            yield resource(include, 'shaders/include', '')
        return
    if not path.endswith('.json'):
        return
    value = json.loads(data)
    if '/models/' in path:
        if 'parent' in value:
            yield resource(value['parent'], 'models', '.json')
        for texture in value.get('textures', {}).values():
            if not texture.startswith('#'):
                yield resource(texture, 'textures', '.png')
    if '/items/' in path:
        def models(node):
            if isinstance(node, dict):
                for key, child in node.items():
                    if key == 'model' and isinstance(child, str):
                        yield resource(child, 'models', '.json')
                    else:
                        yield from models(child)
            elif isinstance(node, list):
                for child in node:
                    yield from models(child)
        yield from models(value)
    if '/font/' in path:
        for provider in value['providers']:
            if 'file' in provider:
                yield resource(provider['file'], 'textures', '')
            if provider['type'].removeprefix('minecraft:') == 'reference':
                yield resource(provider['id'], 'font', '.json')
    if path.endswith('/sounds.json'):
        for event in value.values():
            for sound in event['sounds']:
                if isinstance(sound, str):
                    yield resource(sound, 'sounds', '.ogg')
                elif sound.get('type', 'file') != 'event':
                    yield resource(sound['name'], 'sounds', '.ogg')
    if '/post_effect/' in path:
        for render_pass in value['passes']:
            for key, suffix in (('vertex_shader', '.vsh'), ('fragment_shader', '.fsh')):
                yield resource(render_pass[key], 'shaders', suffix)


def build(source):
    files = {p.relative_to(source).as_posix(): p.read_bytes()
             for p in sorted(source.rglob('*')) if p.is_file()}
    packs = {name: {} for name in PROFILES}
    for path, data in files.items():
        if path.startswith('assets/minecraft/items/'):
            original = json.loads(data)
            selector = original['model']
            if selector.get('property') != 'minecraft:custom_model_data':
                raise ValueError(f'Unexpected vanilla item definition: {path}')
            for profile, stack in STACKS.items():
                filtered = copy.deepcopy(original)
                filtered['model']['cases'] = [case for case in selector['cases']
                    if {owner(ref) for ref in references(path, encoded({'model': case['model']}))} <= set(stack)]
                if not filtered['model']['cases']:
                    filtered['model'] = filtered['model']['fallback']
                if profile == 'base' or filtered['model'] != json.loads(packs['base'][path])['model']:
                    packs[profile][path] = encoded(filtered)
        elif path.endswith('/sounds.json'):
            events = json.loads(data)
            for profile in PROFILES:
                selected = {key: event for key, event in events.items()
                            if ('starwars' if key.startswith('sternenfront.') else 'base') == profile}
                if selected:
                    packs[profile][path] = encoded(selected)
        elif '/font/' in path and path.endswith('.json') and owner(path) == 'base':
            font = json.loads(data)
            base_providers = [p for p in font['providers']
                              if 'file' not in p or owner(resource(p['file'], 'textures', '')) == 'base']
            packs['base'][path] = encoded(dict(font, providers=base_providers))
            if base_providers != font['providers']:
                packs['starwars'][path] = data
        else:
            packs[owner(path)][path] = data
    for profile in PROFILES:
        packs[profile]['pack.mcmeta'] = files['pack.mcmeta']
    return files, packs


def verify(files, packs):
    external = set()
    for profile, stack in STACKS.items():
        effective = {}
        for layer in stack:
            effective.update(packs[layer])
        for path, data in effective.items():
            for dependency in references(path, data):
                if dependency not in effective:
                    if dependency.startswith('assets/minecraft/') and dependency not in files:
                        external.add(dependency)
                    else:
                        raise ValueError(f'{profile}: {path} requires missing {dependency}')
            if path.endswith('.png') and path + '.mcmeta' in files and path + '.mcmeta' not in effective:
                raise ValueError(f'{profile}: missing animation metadata for {path}')
        allowed = set(stack)
        for path in effective:
            if owner(path) not in allowed:
                raise ValueError(f'{profile}: excluded asset {path}')
        for path, original in files.items():
            if path.startswith('assets/minecraft/items/'):
                actual = json.loads(effective[path])
                expected = json.loads(original)
                cases = [case for case in expected['model']['cases']
                         if {owner(ref) for ref in references(path, encoded({'model': case['model']}))} <= allowed]
                model = dict(expected['model'], cases=cases) if cases else expected['model']['fallback']
                assert actual['model'] == model, f'{profile}: lost item cases or fallback in {path}'
        if profile in ('base', 'modes'):
            assert not any('/boss_bar/' in p or '/bettermodel/' in p for p in effective)
            for path, data in effective.items():
                if path.endswith('/sounds.json'):
                    assert not any(key.startswith('sternenfront.') for key in json.loads(data))
    missing = set(files) - set().union(*(set(pack) for pack in packs.values()))
    assert not missing, sorted(missing)
    for path, data in files.items():
        if path.startswith('assets/minecraft/items/'):
            for case in json.loads(data)['model']['cases']:
                dependencies = {owner(ref) for ref in references(path, encoded({'model': case['model']}))}
                if not any(dependencies <= set(stack) for stack in STACKS.values()):
                    raise ValueError(f'No supported stack can render {path}: {case["when"]}')
    return sorted(external)


def write_zip(path, files):
    with ZipFile(path, 'w', compression=ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            entry = ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            archive.writestr(entry, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=Path(__file__).parent / 'src')
    parser.add_argument('--output', type=Path, default=Path(__file__).parent / 'build/split')
    parser.add_argument('--check', action='store_true', help='Validate without writing archives')
    args = parser.parse_args()
    source = args.source.resolve()
    if source == args.output.resolve() or source in args.output.resolve().parents:
        parser.error('Output must be outside the source tree')
    files, packs = build(source)
    external = verify(files, packs)
    # Players get credits for what the pack contains; map provenance and open questions stay in the repository.
    credits = source.parent.parent / 'CREDITS.md'
    if credits.is_file():
        shipped = True
        lines = []
        for line in credits.read_text().splitlines():
            if line.startswith('## '):
                shipped = line[3:] in ('Music', 'Sound effects', 'Models and textures')
            if shipped:
                lines.append(line)
        packs['base']['credits/CREDITS.md'] = ('\n'.join(lines) + '\n').encode()
    report = {'source': str(source), 'source_files': len(files), 'stacks_low_to_high': STACKS,
              'packs': {key: {'files': len(value), 'uncompressed_bytes': sum(map(len, value.values()))}
                        for key, value in packs.items()}, 'vanilla_dependencies': external}
    if not args.check:
        args.output.mkdir(parents=True, exist_ok=True)
        for profile, content in packs.items():
            write_zip(args.output / f'blockcraft-{profile}.zip', content)
        (args.output / 'split-report.json').write_bytes(encoded(report))
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()

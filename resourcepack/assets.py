"""Generate assets in isolation, validate the result, and apply reviewed output."""
import argparse
from dataclasses import dataclass
from datetime import datetime, timezone
import os
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys

from AssetFiles import write_json

ROOT = Path(__file__).resolve().parent.parent


@dataclass(frozen=True)
class Family:
    script: str
    requires: tuple[str, ...] = ()
    arguments: tuple[str, ...] = ()
    visual: bool = True


FAMILIES = {
    'bodies': Family('SternenfrontArt.java', arguments=('src',)),
    'props': Family('PropArt.java', arguments=('src', '../nexo', 'build/previews/props.png')),
    'objectives': Family('ObjectiveArt.py', arguments=('--output', 'build/previews/objectives')),
    'planet': Family('PlanetKitArt.py', arguments=('--output', 'build/previews/planet')),
    'shared': Family('MilitaryKitArt.py', arguments=('--output', 'build/previews/shared')),
    'actors': Family('KitActors.py'),
    'battle': Family('BattleArt.py', ('bodies',)),
    'rigs': Family('RigModels.py', ('bodies',)),
    'gallery': Family('SternenfrontGallery.py', ('battle', 'rigs')),
    'ui': Family('PackArt.java', ('bodies',), ('src',)),
    'hud': Family('HudArt.java', arguments=('src', 'build/previews/hud.png')),
    'stations': Family('StationArt.java', ('hud',), ('src',)),
    'yard': Family('YardArt.java', arguments=('src', 'build/previews/yard.png')),
    'dialogue': Family('DialogueArt.java', arguments=('src', '../paper/api/src/main/resources/dialogue-glyphs.properties')),
    'story': Family('StoryArt.java', arguments=('src',)),
    'presentation': Family('PresentationArt.java', arguments=('src',)),
    'sounds': Family('SoundArt.py', visual=False),
    'sternenfront-sounds': Family('SternenfrontSounds.py', visual=False),
    'story-sounds': Family('StorySounds.py', visual=False),
}
CHECKS = (
    ('verify_imported_props.py',),
    ('verify_starwars.py',),
    ('-m', 'unittest', 'test_imported_props', 'test_model_surfaces', 'test_furniture_collision',
     'test_split_pack', 'test_bettermodel_pack', 'test_asset_pipeline'),
    ('verify_weapon_poses.py',),
    ('verify_bettermodel.py',),
    ('split_pack.py', '--check'),
)


def generation_order(names: list[str]) -> list[str]:
    result = []
    visiting = set()

    def visit(name: str) -> None:
        if name in result:
            return
        if name in visiting:
            raise ValueError(f'Circular asset dependency: {name}')
        visiting.add(name)
        for dependency in FAMILIES[name].requires:
            visit(dependency)
        visiting.remove(name)
        result.append(name)

    for name in names:
        visit(name)
    return result


def artifacts(root: Path) -> dict[str, str]:
    paths = [*(root / 'resourcepack/src').rglob('*'),
             *(root / 'resourcepack/bettermodel').glob('*.bbmodel'),
             *(root / 'resourcepack/art/sternenfront').glob('*.png'),
             *(root / 'nexo/items').glob('*.yml'),
             *(root / 'paper/api/src/main/resources').glob('*glyphs.properties')]
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(paths) if p.is_file()}


def source_inputs(root: Path) -> dict[str, str]:
    paths = [*(root / 'resourcepack').glob('*.py'), *(root / 'resourcepack').glob('*.java'),
             *(root / 'resourcepack/catalogs').glob('*.json'),
             *(root / 'paper/sternenfront/src/main/resources').glob('*.yml')]
    paths.extend(root / p for p in ('resourcepack/finished-weapons.txt', 'resourcepack/pyproject.toml',
                                    'resourcepack/uv.lock', 'CREDITS.md',
                                    'paper/api/src/main/java/me/blockcraft/api/pack/PackModel.java'))
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(paths) if p.is_file()}


def run(arguments: list[str], directory: Path) -> int:
    print(f'Running {" ".join(arguments)}', flush=True)
    return subprocess.run(arguments, cwd=directory).returncode


def check(root: Path) -> int:
    logs = root / 'resourcepack/build/asset-checks'
    logs.mkdir(parents=True, exist_ok=True)
    for index, arguments in enumerate(CHECKS):
        result = subprocess.run([sys.executable, *arguments], cwd=root / 'resourcepack', capture_output=True, text=True)
        log = logs / f'{index + 1}-{arguments[0].removesuffix(".py").removeprefix("-")}.log'
        log.write_text(result.stdout + result.stderr)
        print(f'{"PASS" if result.returncode == 0 else "FAIL"} {" ".join(arguments)}', flush=True)
        if result.returncode:
            print(log.read_text(), file=sys.stderr)
            return result.returncode
    return 0


def prepare(output: Path) -> None:
    pack = output / 'resourcepack'
    pack.mkdir(parents=True)
    for pattern in ('*.py', '*.java', 'finished-weapons.txt'):
        for source in (ROOT / 'resourcepack').glob(pattern):
            shutil.copy2(source, pack / source.name)
    for relative in ('resourcepack/src', 'resourcepack/bettermodel', 'resourcepack/catalogs',
                     'resourcepack/art', 'nexo/items', 'paper/sternenfront/src/main/resources',
                     'paper/api/src/main/resources'):
        shutil.copytree(ROOT / relative, output / relative)
    shutil.copy2(ROOT / 'CREDITS.md', output / 'CREDITS.md')
    registry = Path('paper/api/src/main/java/me/blockcraft/api/pack/PackModel.java')
    (output / registry).parent.mkdir(parents=True)
    shutil.copy2(ROOT / registry, output / registry)


def changes(before: dict[str, str], after: dict[str, str]) -> list[str]:
    return sorted(path for path, digest in after.items() if before.get(path) != digest)


def apply(output: Path) -> int:
    report_path = output / 'asset-report.json'
    if not report_path.is_file():
        print(f'No generated result at {output}. Run generate first.', file=sys.stderr)
        return 1
    report = json.loads(report_path.read_text())
    if report['source'] != str(ROOT):
        print('This output belongs to another checkout.', file=sys.stderr)
        return 1
    if source_inputs(ROOT) != report['inputs']:
        print('Asset source inputs changed after generation. Generate a fresh result.', file=sys.stderr)
        return 1
    after = artifacts(output)
    if after != report['after']:
        print('Generated files changed after validation. Generate a fresh result.', file=sys.stderr)
        return 1
    before = report['before']
    current = artifacts(ROOT)
    expected = dict(before)
    expected.update({p: after[p] for p in report['changed'] if current.get(p) == after[p]})
    conflicts = sorted(p for p in current.keys() | expected.keys() if current.get(p) != expected.get(p))
    if conflicts:
        print('Accepted files changed since generation: ' + ', '.join(conflicts), file=sys.stderr)
        return 1
    status = check(output)
    if status:
        return status
    if artifacts(ROOT) != current or source_inputs(ROOT) != report['inputs']:
        print('Accepted assets or generator inputs changed during validation. Generate a fresh result.', file=sys.stderr)
        return 1
    for relative in report['changed']:
        source, target = output / relative, ROOT / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_name(target.name + '.asset-tmp')
        shutil.copy2(source, temporary)
        temporary.replace(target)
    print(f'Applied {len(report["changed"])} validated files. No files were deleted.')
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('list', help='List generator families and dependencies')
    commands.add_parser('check', help='Validate accepted native assets without generating anything')
    generate = commands.add_parser('generate', help='Generate and validate a scratch result without changing accepted assets')
    generate.add_argument('families', nargs='+', choices=(*FAMILIES, 'all'), help='all selects every visual family; sound families are explicit')
    generate.add_argument('--output', type=Path)
    generate.add_argument('--apply', action='store_true', help='Apply the validated output immediately')
    preview = commands.add_parser('preview', help='Render a finished native model using its current textures')
    preview.add_argument('model', help='Model identifier, for example blockcraft:item/props/imported/halloween_candle')
    preview.add_argument('--yaw', type=float, default=37)
    preview.add_argument('--output', type=Path)
    accept = commands.add_parser('apply', help='Validate and apply a previously generated result')
    accept.add_argument('output', type=Path)
    args = parser.parse_args()
    if args.command == 'list':
        for name, family in FAMILIES.items():
            print(f'{name:22} {family.script:28} requires: {", ".join(family.requires) or "none"}')
        print('Finished imports and the weapons in finished-weapons.txt are edited directly.')
        return 0
    if args.command == 'check':
        return check(ROOT)
    if args.command == 'preview':
        from NativePreview import preview
        output = args.output.resolve() if args.output else ROOT / 'resourcepack/build/previews' / (args.model.replace(':', '_').replace('/', '_') + '.png')
        if output.is_relative_to(ROOT) and not output.is_relative_to(ROOT / 'resourcepack/build'):
            parser.error('Preview output must be outside the checkout or under resourcepack/build.')
        try:
            preview(ROOT / 'resourcepack/src', args.model, output, args.yaw)
        except (ValueError, FileNotFoundError, KeyError) as error:
            parser.error(str(error))
        print(f'Software preview: {output}. Verify changed rendering in Minecraft.')
        return 0
    if args.command == 'apply':
        return apply(args.output.resolve())
    selected = [name for name, family in FAMILIES.items() if family.visual] if 'all' in args.families else args.families
    order = generation_order(selected)
    output = args.output.resolve() if args.output else ROOT / 'resourcepack/build/assets' / ('-'.join(args.families) + '-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%f'))
    if output.exists():
        print(f'Output already exists: {output}. Choose a new --output directory.', file=sys.stderr)
        return 1
    if output == ROOT or output in ROOT.parents or output.is_relative_to(ROOT) and not output.is_relative_to(ROOT / 'resourcepack/build'):
        print('Use an output outside the checkout or under resourcepack/build.', file=sys.stderr)
        return 1
    before = artifacts(ROOT)
    inputs = source_inputs(ROOT)
    prepare(output)
    for name in order:
        family = FAMILIES[name]
        java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
        executable = java if family.script.endswith('.java') else sys.executable
        status = run([executable, family.script, *family.arguments], output / 'resourcepack')
        if status:
            return status
    status = check(output)
    if status:
        return status
    after = artifacts(output)
    write_json(output / 'asset-report.json', {'source': str(ROOT), 'families': order, 'inputs': inputs, 'before': before,
                                             'after': after, 'changed': changes(before, after)})
    print(f'Validated output: {output}\nChanged accepted files: {len(changes(before, after))}')
    return apply(output) if args.apply else 0


if __name__ == '__main__':
    sys.exit(main())

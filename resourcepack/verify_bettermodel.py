"""Check that animated rig meshes are already present in the source pack."""
import argparse
import json
from pathlib import Path
from RigExport import native_assets


def verify(source, rigs):
    checked=0
    for path in sorted(rigs.glob('*.bbmodel')):
        model=json.loads(path.read_text())
        if model.get('blockcraft_native') == 1:
            for relative, expected in native_assets(path.stem, model).items():
                target = source / relative
                if not target.is_file() or target.read_bytes() != expected:
                    raise ValueError(f'{path.name}: stale native rig asset {target}')
        groups={g['uuid']:g for g in model.get('groups',[])}
        elements={e['uuid']:e for e in model.get('elements',[])}
        def visit(group):
            nonlocal checked
            spec=groups.get(group.get('uuid'),group)
            children=group.get('children',[])
            visible=any(isinstance(c,str) and elements.get(c,{}).get('faces') for c in children)
            if visible and spec['name']!='hitbox':
                route=source/'assets/bettermodel/items/model'/f'{path.stem}_{spec["name"]}.json'
                if not route.is_file():raise ValueError(f'{path.name}: missing native bone route {route}')
                checked+=1
            for child in children:
                if isinstance(child,dict):visit(child)
        for group in model.get('outliner',[]):
            if isinstance(group,dict):visit(group)
    return checked


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--source',type=Path,default=Path(__file__).parent/'src')
    parser.add_argument('--rigs',type=Path,default=Path(__file__).parent/'bettermodel');args=parser.parse_args()
    print(f'Validated {verify(args.source,args.rigs)} native BetterModel bone routes. Runtime export merging is unnecessary for these rigs.')

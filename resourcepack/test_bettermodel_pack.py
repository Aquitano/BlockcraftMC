import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from RigExport import export
from verify_bettermodel import verify


class BetterModelPackTest(unittest.TestCase):
    def test_generated_geometry_and_textures_must_match_the_rig(self):
        model = {'blockcraft_native': 1,
                 'groups': [{'uuid': 'body', 'name': 'body', 'origin': [4, 8, 12]}],
                 'elements': [{'uuid': 'cube', 'from': [-28, 8, 12], 'to': [36, 24, 28],
                               'origin': [4, 8, 12], 'rotation': [0, 0, 22.5],
                               'faces': {'north': {'uv': [0, 0, 32, 16], 'texture': 0}}}],
                 'outliner': [{'uuid': 'body', 'children': ['cube']}],
                 'textures': [{'name': 'body.png', 'width': 32, 'height': 16, 'source': 'data:image/png;base64,cG5n'}]}
        with TemporaryDirectory() as directory:
            root = Path(directory)
            source, rigs = root/'src', root/'rigs'
            rigs.mkdir()
            rig = rigs/'test.bbmodel'
            rig.write_text(json.dumps(model))
            export(source, 'test', model)
            self.assertEqual(verify(source, rigs), 1)
            mesh = json.loads((source/'assets/bettermodel/models/model/test_body_1.json').read_text())
            self.assertEqual(mesh['elements'][0]['from'], [-8, 8, 8])
            self.assertEqual(mesh['elements'][0]['to'], [24, 16, 16])
            self.assertEqual(mesh['elements'][0]['rotation']['origin'], [8, 8, 8])
            self.assertEqual(mesh['elements'][0]['faces']['north']['uv'], [0, 0, 16, 16])
            for changed in ('geometry', 'texture'):
                with self.subTest(changed=changed):
                    stale = json.loads(json.dumps(model))
                    if changed == 'geometry':
                        stale['elements'][0]['to'][1] += 1
                    else:
                        stale['textures'][0]['source'] = 'data:image/png;base64,bmV3'
                    rig.write_text(json.dumps(stale))
                    with self.assertRaisesRegex(ValueError, 'stale native rig asset'):
                        verify(source, rigs)
                    export(source, 'test', stale)
                    self.assertEqual(verify(source, rigs), 1)
                    export(source, 'test', model)

    def test_missing_rendered_bone_fails_staging_but_hitbox_needs_no_texture(self):
        with TemporaryDirectory() as directory:
            root=Path(directory);rigs=root/'rigs';rigs.mkdir();source=root/'src'
            model={'elements':[{'uuid':'cube','faces':{'north':{}}}],
                   'outliner':[{'name':'body','children':['cube']},{'name':'hitbox','children':['cube']}]}
            (rigs/'test.bbmodel').write_text(json.dumps(model))
            with self.assertRaisesRegex(ValueError,'missing native bone route'):verify(source,rigs)
            route=source/'assets/bettermodel/items/model/test_body.json';route.parent.mkdir(parents=True);route.write_text('{}')
            self.assertEqual(verify(source,rigs),1)


if __name__=='__main__':unittest.main()

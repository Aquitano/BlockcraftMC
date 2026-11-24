import json
import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import assets
from AssetFiles import write_json


class AssetPipelineTest(unittest.TestCase):
    def test_gallery_generates_bodies_once_before_both_dependants(self):
        order = assets.generation_order(['gallery', 'ui'])
        self.assertEqual(order.count('bodies'), 1)
        for name in order:
            for dependency in assets.FAMILIES[name].requires:
                self.assertLess(order.index(dependency), order.index(name))

    def test_apply_preserves_a_concurrent_edit(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / 'accepted'
            output = Path(temporary) / 'generated'
            relative = 'resourcepack/src/assets/blockcraft/models/item/example.json'
            accepted, generated = root / relative, output / relative
            write_json(accepted, {'version': 1})
            before = assets.artifacts(root)
            write_json(generated, {'version': 2})
            after = assets.artifacts(output)
            write_json(output / 'asset-report.json', {'source': str(root), 'inputs': assets.source_inputs(root), 'before': before,
                       'after': after, 'changed': [relative]})
            write_json(accepted, {'version': 3})
            with patch.object(assets, 'ROOT', root), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(assets.apply(output), 1)
            self.assertEqual(json.loads(accepted.read_text()), {'version': 3})

    def test_apply_rejects_output_modified_after_validation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / 'generated'
            generated = output / 'resourcepack/src/example.json'
            write_json(generated, {'version': 1})
            write_json(output / 'asset-report.json', {'source': str(root), 'inputs': assets.source_inputs(root), 'before': {},
                       'after': assets.artifacts(output), 'changed': ['resourcepack/src/example.json']})
            write_json(generated, {'version': 2})
            with patch.object(assets, 'ROOT', root), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(assets.apply(output), 1)
            self.assertFalse((root / 'resourcepack/src/example.json').exists())

    def test_apply_rejects_changed_generator_inputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / 'generated'
            generator = root / 'resourcepack/Example.py'
            generator.parent.mkdir()
            generator.write_text('version = 1\n')
            write_json(output / 'asset-report.json', {'source': str(root), 'inputs': assets.source_inputs(root),
                       'before': {}, 'after': {}, 'changed': []})
            generator.write_text('version = 2\n')
            with patch.object(assets, 'ROOT', root), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(assets.apply(output), 1)


if __name__ == '__main__':
    unittest.main()

import json
import tempfile
import unittest
from pathlib import Path
from bridge.adapters.snapshot_file import load_snapshot

class ExternalAdapterTests(unittest.TestCase):
    def test_valid(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'snapshot.json';p.write_text(json.dumps({'inventory': []}))
            self.assertEqual(load_snapshot(p)['inventory'], [])
    def test_invalid(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'snapshot.json';p.write_text('{"inventory":{}}')
            with self.assertRaises(ValueError): load_snapshot(p)
    def test_missing(self):
        with self.assertRaises(ValueError): load_snapshot('/missing/pipboy-snapshot-123.json')

"""Contract checks for the Android bundled demo and previous bridge protocol.

These are Python tests, not proof that Android compiled or the UI rendered.
"""
import json
import unittest
from pathlib import Path
from bridge.adapters.snapshot_file import load_snapshot

ROOT = Path(__file__).resolve().parents[1]

class Pass5ContractTests(unittest.TestCase):
    def test_android_demo_identical_to_mock_fixture(self):
        source = json.loads((ROOT/'samples/mock_snapshot.json').read_text())
        demo = json.loads((ROOT/'android/app/src/main/assets/demo_snapshot.json').read_text())
        self.assertEqual(source, demo)
        self.assertEqual(demo['protocol'], 1)
        self.assertEqual(demo['source'], 'mock')
        self.assertTrue(demo['inventory'])
        self.assertFalse(demo['capabilities']['equipmentWrite'])

    def test_ui_contract_fixture_has_required_fields(self):
        fixture = json.loads((ROOT/'samples/mock_snapshot.json').read_text())
        for field in ('character', 'inventory', 'special', 'quests', 'radio', 'map', 'survival', 'capabilities'):
            self.assertIn(field, fixture)
        self.assertIsInstance(fixture['inventory'], list)
        self.assertIsInstance(fixture['quests'], list)

    def test_external_file_adapts_version_one_for_android(self):
        import tempfile
        with tempfile.TemporaryDirectory() as dirname:
            path = Path(dirname)/'snapshot.json'
            path.write_text('{"inventory": []}')
            result = load_snapshot(path)
            self.assertEqual(result['protocol'], 1)
            self.assertEqual(result['source'], 'external-file-unverified')
            self.assertFalse(result['capabilities']['equipmentWrite'])

    def test_external_file_refuses_unknown_protocol(self):
        import tempfile
        with tempfile.TemporaryDirectory() as dirname:
            path = Path(dirname)/'snapshot.json'
            path.write_text('{"protocol": 99, "inventory": []}')
            with self.assertRaises(ValueError):
                load_snapshot(path)

if __name__ == '__main__':
    unittest.main()

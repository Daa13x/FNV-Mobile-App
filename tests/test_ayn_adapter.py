"""Synthetic upstream server tests: NEVER evidence of compatibility with a real mod."""
import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from bridge.adapters.ayn_http import transform_ayn_state, load_ayn_snapshot, UpstreamUnavailable
from bridge.server import State, make_handler


def example_ayn():
    return {
        "ready": True, "game": "FalloutNV", "tick": 42,
        "player": {"name": "Courier", "level": 14, "hp": 123, "hpMax": 200,
                   "ap": 38, "apMax": 60, "rads": 12, "weight": 10, "weightMax": 200,
                   "h2o": 22, "fod": 34, "slp": 56},
        "special": [{"name": "Strength", "value": 6}, {"name": "Intelligence", "value": 7}],
        "inventory": {"weapons": [{"id": "0A01234B", "name": "Laser Rifle", "count": 1,
                                   "weight": 8.0, "health": 0.6, "equipped": True}],
                      "apparel": [], "aid": [{"id": "00015169", "name": "Stimpak", "count": 8}],
                      "misc": [], "ammo": []},
        "quests": [{"id": "030003E5", "name": "Quest A", "active": True,
                   "objectives": [{"text": "Find a door", "done": False}]}],
        "map": {"world": "Capital Wasteland", "cell": "Megaton", "markers": []},
        "radio": [{"id": "0001", "name": "GNR", "active": True}],
        "perms": {"equip": True, "use": True, "fastTravel": True},
    }


class FakeGame(BaseHTTPRequestHandler):
    state = example_ayn()
    visited = []
    def do_GET(self):
        FakeGame.visited.append((self.command, self.path))
        if self.path != '/state':
            self.send_error(404)
            return
        raw = json.dumps(self.state).encode()
        self.send_response(200)
        self.send_header('Content-Length', str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)
    def log_message(self, *_):
        pass


class AynAdapterTests(unittest.TestCase):
    def test_schema_projection_and_no_write_controls(self):
        result = transform_ayn_state(example_ayn())
        self.assertEqual(result['source'], 'ayn-dual-screen')
        self.assertEqual(result['sequence'], 42)
        self.assertEqual(result['character']['location'], 'Megaton')
        self.assertEqual(result['special']['strength'], 6)
        self.assertEqual(result['inventory'][0]['name'], 'Laser Rifle')
        self.assertEqual(result['inventory'][0]['condition'], .6)
        self.assertEqual(result['quests'][0]['title'], 'Quest A')
        self.assertEqual(result['radio']['station'], 'GNR')
        self.assertNotIn('caps', result['character'])
        self.assertFalse(result['capabilities']['equipmentWrite'])
        self.assertFalse(result['capabilities']['fastTravel'])

    def test_fail_closed_if_no_loaded_save(self):
        data = example_ayn()
        data['ready'] = False
        with self.assertRaises(UpstreamUnavailable):
            transform_ayn_state(data)

    def test_reject_unexpected_game_and_malformed_inventory(self):
        data = example_ayn()
        data['game'] = 'Fallout3'
        with self.assertRaises(UpstreamUnavailable):
            transform_ayn_state(data)
        data['game'] = 'FalloutNV'
        data['inventory']['weapons'] = 'not a list'
        with self.assertRaises(UpstreamUnavailable):
            transform_ayn_state(data)

    def test_reject_nonfinite_and_invalid_counts(self):
        data = example_ayn()
        data['player']['hp'] = float('nan')
        self.assertNotIn('hp', transform_ayn_state(data)['character'])
        data['inventory']['weapons'][0]['count'] = True
        with self.assertRaises(UpstreamUnavailable):
            transform_ayn_state(data)

    def test_loopback_upstream_and_paired_bridge(self):
        FakeGame.visited = []
        FakeGame.state = example_ayn()
        upstream = ThreadingHTTPServer(('127.0.0.1', 0), FakeGame)
        upstream_thread = threading.Thread(target=upstream.serve_forever, daemon=True)
        upstream_thread.start()
        state = State({'source': 'mock', 'inventory': []}, '127.0.0.1', ayn_port=upstream.server_port)
        bridge = ThreadingHTTPServer(('127.0.0.1', 0), make_handler(state))
        bridge_thread = threading.Thread(target=bridge.serve_forever, daemon=True)
        bridge_thread.start()
        base = f'http://127.0.0.1:{bridge.server_port}'
        try:
            with urlopen(base+'/health') as resp:
                self.assertEqual(json.load(resp)['mode'], 'ayn_read_only')
            try:
                urlopen(base+'/v1/snapshot')
                self.fail('Unauthorized endpoint should reject')
            except HTTPError as exc:
                self.assertEqual(exc.code, 401)
            req = Request(base+'/v1/pair', data=json.dumps({'code': state.code}).encode(),
                          headers={'Content-Type': 'application/json'})
            with urlopen(req) as resp:
                token = json.load(resp)['token']
            request = Request(base+'/v1/snapshot', headers={'Authorization': 'Bearer '+token})
            with urlopen(request) as resp:
                first = json.load(resp)
            self.assertEqual(first['inventory'][0]['name'], 'Laser Rifle')
            FakeGame.state = example_ayn()
            FakeGame.state['inventory']['weapons'][0]['name'] = 'Plasma Rifle'
            FakeGame.state['tick'] += 1
            with urlopen(request) as resp:
                second = json.load(resp)
            self.assertEqual(second['inventory'][0]['name'], 'Plasma Rifle')
            self.assertEqual(second['sequence'], 43)
            self.assertEqual(FakeGame.visited, [('GET', '/state'), ('GET', '/state')])
            FakeGame.state['ready'] = False
            with self.assertRaises(HTTPError) as err:
                urlopen(request)
            self.assertEqual(err.exception.code, 503)
        finally:
            bridge.shutdown(); bridge.server_close()
            upstream.shutdown(); upstream.server_close()

    def test_reject_invalid_port(self):
        with self.assertRaises(ValueError):
            load_ayn_snapshot(port=1)
        with self.assertRaises(ValueError):
            load_ayn_snapshot(port='27303')

if __name__ == '__main__':
    unittest.main()

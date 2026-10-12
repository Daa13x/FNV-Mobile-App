import json
import threading
import unittest
from http.server import ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from bridge.server import State, make_handler


class PairingSafetyTests(unittest.TestCase):
    def setUp(self):
        self.state = State({'source': 'mock', 'inventory': []}, '127.0.0.1')
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), make_handler(self.state))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f'http://127.0.0.1:{self.server.server_port}'

    def tearDown(self):
        self.server.shutdown(); self.server.server_close()

    def post_pair(self, code):
        req = Request(self.base+'/v1/pair', data=json.dumps({'code': code}).encode(),
                      headers={'Content-Type': 'application/json'})
        try:
            with urlopen(req) as resp:
                return resp.status, json.load(resp)
        except HTTPError as exc:
            return exc.code, json.load(exc)

    def test_one_use_pairing_code_and_token_revocation(self):
        code = self.state.code
        status, result = self.post_pair(code)
        self.assertEqual(status, 200)
        self.assertEqual(self.post_pair(code)[0], 403)
        token = result['token']
        req = Request(self.base+'/v1/unpair', data=b'', method='POST',
                      headers={'Authorization': 'Bearer '+token})
        with urlopen(req) as resp:
            self.assertTrue(json.load(resp)['unpaired'])
        req = Request(self.base+'/v1/snapshot', headers={'Authorization': 'Bearer '+token})
        with self.assertRaises(HTTPError) as err:
            urlopen(req)
        self.assertEqual(err.exception.code, 401)

    def test_rate_limits_failed_codes(self):
        for _ in range(5):
            self.assertEqual(self.post_pair('bad')[0], 403)
        self.assertEqual(self.post_pair(self.state.code)[0], 429)

if __name__ == '__main__': unittest.main()

class PairRaceTests(unittest.TestCase):
    def test_two_threads_cannot_reuse_same_code(self):
        from concurrent.futures import ThreadPoolExecutor
        state = State({'inventory': []}, '127.0.0.1')
        code = state.code
        with ThreadPoolExecutor(max_workers=2) as workers:
            results = list(workers.map(lambda _: state.pair(code), range(2)))
        self.assertEqual(sum(bool(v) for v in results), 1)

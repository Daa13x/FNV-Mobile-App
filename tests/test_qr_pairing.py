"""QR pairing contract and HTTP integration; never uses Fallout."""
import json
import re
import threading
import time
import unittest
from http.server import ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from bridge.server import State, QR_TTL_SECONDS, lan_ipv4, make_handler


class QrInviteTests(unittest.TestCase):
    def setUp(self):
        self.state = State({'source': 'mock', 'inventory': []}, '0.0.0.0')

    def test_qr_is_one_time_long_random_secret_without_manual_code(self):
        invite = self.state.qr_invite('192.168.1.60')
        self.assertRegex(invite, r'^pipboyremote://pair\?v=1&host=192\.168\.1\.60&port=8765&secret=[A-Za-z0-9_-]{32}$')
        secret = invite.split('secret=')[1]
        self.assertNotIn(self.state.code, invite)
        self.assertIsNotNone(self.state.pair(qr_secret=secret))
        self.assertIsNone(self.state.pair(qr_secret=secret))
        self.assertIsNone(self.state.qr_invite('192.168.1.60'))

    def test_expired_qr_rejected_but_display_renews(self):
        previous = self.state.qr_secret
        self.state.qr_issued -= QR_TTL_SECONDS + 1
        self.assertIsNone(self.state.pair(qr_secret=previous))
        invite = self.state.qr_invite('192.168.1.60')
        self.assertNotIn(previous, invite)
        self.assertIsNotNone(self.state.pair(qr_secret=self.state.qr_secret))

    def test_explicit_new_qr_invalidates_predecessor(self):
        old = self.state.qr_secret
        self.state.new_qr()
        self.assertNotEqual(old, self.state.qr_secret)
        self.assertIsNone(self.state.pair(qr_secret=old))
        self.assertIsNotNone(self.state.pair(qr_secret=self.state.qr_secret))

    def test_ambiguous_code_and_secret_is_rejected(self):
        self.assertIsNone(self.state.pair(self.state.code, qr_secret=self.state.qr_secret))

    def test_hosts_must_be_private_rfc1918(self):
        for address in ('127.0.0.1', '0.0.0.0', '8.8.8.8', '192.0.2.1', 'bad.example', '192.168.1.3:8765'):
            with self.subTest(address=address), self.assertRaises(ValueError):
                lan_ipv4(address)
        for address in ('192.168.1.3', '10.0.0.1', '172.31.255.1'):
            self.assertEqual(lan_ipv4(address), address)

    def test_only_prototype_port_supported(self):
        with self.assertRaises(ValueError):
            self.state.qr_invite('192.168.1.6', 9000)


class QrHttpTests(unittest.TestCase):
    def setUp(self):
        self.state = State({'protocol': 1, 'source': 'mock', 'inventory': []}, '127.0.0.1')
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), make_handler(self.state))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f'http://127.0.0.1:{self.server.server_port}'

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def post(self, payload):
        request = Request(self.base + '/v1/pair', data=json.dumps(payload).encode(), headers={'Content-Type':'application/json'})
        try:
            with urlopen(request) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            return error.code, json.load(error)

    def test_pair_by_scanned_secret(self):
        invite = self.state.qr_invite('192.168.1.6')
        secret = invite.split('secret=')[1]
        status, body = self.post({'qr_secret': secret})
        self.assertEqual(status, 200)
        req = Request(self.base + '/v1/snapshot', headers={'Authorization':'Bearer ' + body['token']})
        with urlopen(req) as response:
            self.assertEqual(json.load(response)['source'], 'mock')
        self.assertEqual(self.post({'qr_secret': secret})[0], 403)

    def test_reject_mixed_credentials_and_malformed_payloads(self):
        self.assertEqual(self.post({'code': self.state.code, 'qr_secret': self.state.qr_secret})[0], 403)
        self.assertEqual(self.post(['not', 'an', 'object'])[0], 400)

    def test_rate_limiting_applies_to_qr_codes(self):
        for _ in range(5):
            self.assertEqual(self.post({'qr_secret':'invalid'})[0], 403)
        self.assertEqual(self.post({'qr_secret':self.state.qr_secret})[0], 429)


if __name__ == '__main__':
    unittest.main()

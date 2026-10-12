import json
import threading
import unittest
from http.server import ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'bridge'))
from server import State, make_handler

class BridgeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.state = State({'protocol':1,'source':'mock','inventory':[]}, '127.0.0.1')
        cls.server = ThreadingHTTPServer(('127.0.0.1',0),make_handler(cls.state))
        cls.thread = threading.Thread(target=cls.server.serve_forever,daemon=True)
        cls.thread.start()
        cls.base = f'http://127.0.0.1:{cls.server.server_port}'

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def request(self, path, data=None, token=None):
        headers = {'Content-Type':'application/json'}
        if token: headers['Authorization'] = 'Bearer '+token
        req = Request(self.base+path, data=json.dumps(data).encode() if data is not None else None, headers=headers)
        try:
            with urlopen(req,timeout=3) as r: return r.status,json.load(r)
        except HTTPError as e: return e.code,json.load(e)

    def test_health(self):
        self.assertEqual(self.request('/health')[0],200)
    def test_unauthorized(self):
        self.assertEqual(self.request('/v1/snapshot')[0],401)
    def test_pairing_and_snapshot(self):
        self.assertEqual(self.request('/v1/pair',{'code':'wrong'})[0],403)
        status,body=self.request('/v1/pair',{'code':self.state.code})
        self.assertEqual(status,200)
        self.assertEqual(self.request('/v1/snapshot',token=body['token'])[1]['source'],'mock')
    def test_mutations_not_available(self):
        self.assertEqual(self.request('/v1/equip',{'id':'x'})[0],404)

if __name__=='__main__': unittest.main()

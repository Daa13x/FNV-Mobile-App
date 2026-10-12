import json
import tempfile
import threading
import unittest
from pathlib import Path
from http.server import ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from bridge.server import State, make_handler

class LiveSnapshotTests(unittest.TestCase):
    def test_updates_and_fails_closed(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'snapshot.json'
            path.write_text(json.dumps({'inventory':[{'name':'Laser Rifle'}]}))
            state = State({'inventory':[]},'127.0.0.1',str(path))
            server = ThreadingHTTPServer(('127.0.0.1',0),make_handler(state))
            thread = threading.Thread(target=server.serve_forever,daemon=True)
            thread.start()
            base = f'http://127.0.0.1:{server.server_port}'
            try:
                req=Request(base+'/v1/pair',data=json.dumps({'code':state.code}).encode(),headers={'Content-Type':'application/json'})
                with urlopen(req) as response: token=json.load(response)['token']
                def fetch():
                    req=Request(base+'/v1/snapshot',headers={'Authorization':'Bearer '+token})
                    with urlopen(req) as response: return json.load(response)
                self.assertEqual(fetch()['inventory'][0]['name'],'Laser Rifle')
                path.write_text(json.dumps({'inventory':[{'name':'Stimpak'}]}))
                self.assertEqual(fetch()['inventory'][0]['name'],'Stimpak')
                path.unlink()
                with self.assertRaises(HTTPError) as error: fetch()
                self.assertEqual(error.exception.code,503)
            finally:
                server.shutdown();server.server_close()

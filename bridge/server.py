"""Read-only LAN Pip-Boy bridge. Python 3.10+, standard library only.
Supports mock data, external file, and loopback-only AYN /state snapshots.
No game memory access or write commands are implemented here.
"""
import argparse
import ipaddress
import hashlib
import hmac
import json
import os
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
try:
    from bridge.adapters.snapshot_file import load_snapshot
except ModuleNotFoundError:
    from adapters.snapshot_file import load_snapshot
try:
    from bridge.adapters.ayn_http import load_ayn_snapshot, UpstreamUnavailable
except ModuleNotFoundError:
    from adapters.ayn_http import load_ayn_snapshot, UpstreamUnavailable

ROOT = Path(__file__).resolve().parent.parent
MAX_BYTES = 16_384
MAX_PAIR_ATTEMPTS = 5
PAIR_WINDOW_SECONDS = 60
QR_TTL_SECONDS = 120

class PairRateLimited(Exception):
    pass

class State:
    def __init__(self, snapshot, host, snapshot_path=None, ayn_port=None, ayn_token=""):
        if snapshot_path and ayn_port is not None:
            raise ValueError("Choose one external source")
        self.snapshot = snapshot
        self.snapshot_path = snapshot_path
        self.ayn_port = ayn_port
        self.ayn_token = ayn_token
        self.host = host
        self.code = f'{secrets.randbelow(1_000_000):06d}'
        self.qr_secret = secrets.token_urlsafe(24)
        self.qr_issued = time.monotonic()
        self.paired_count = 0
        self.tokens = set()
        self.lock = threading.Lock()
        self.failed_attempts = {}  # client IP -> (window start, failures)
        self.started = time.monotonic()

    def new_qr(self):
        """Explicitly permit the next device to pair; invalidate the old QR."""
        with self.lock:
            self.qr_secret = secrets.token_urlsafe(24)
            self.qr_issued = time.monotonic()

    def qr_invite(self, advertised_host, port=8765):
        """Generate a one-time invitation for the on-PC display; never expose over HTTP."""
        host = lan_ipv4(advertised_host)
        if port != 8765:
            raise ValueError('Android prototype currently supports TCP 8765 only')
        with self.lock:
            now = time.monotonic()
            if self.qr_secret is not None and now - self.qr_issued > QR_TTL_SECONDS:
                self.qr_secret = secrets.token_urlsafe(24)
                self.qr_issued = now
            if self.qr_secret is None:
                return None
            return f'pipboyremote://pair?v=1&host={host}&port={port}&secret={self.qr_secret}'

    def pair(self, code=None, client_ip='127.0.0.1', qr_secret=None):
        # Single lock guarantees one-use pairing codes even under concurrent requests.
        with self.lock:
            now = time.monotonic()
            window_start, failures = self.failed_attempts.get(client_ip, (now, 0))
            if now - window_start >= PAIR_WINDOW_SECONDS:
                window_start, failures = now, 0
            if failures >= MAX_PAIR_ATTEMPTS:
                raise PairRateLimited('pairing attempts exceeded')
            # Reject ambiguous requests: exactly one credential type may be supplied.
            valid_code = (qr_secret is None and isinstance(code, str) and
                          now - self.started <= 600 and hmac.compare_digest(code, self.code))
            valid_qr = (code is None and isinstance(qr_secret, str) and
                        self.qr_secret is not None and
                        now - self.qr_issued <= QR_TTL_SECONDS and
                        hmac.compare_digest(qr_secret, self.qr_secret))
            valid = valid_code or valid_qr
            if not valid:
                self.failed_attempts[client_ip] = (window_start, failures + 1)
                return None
            token = secrets.token_urlsafe(32)
            self.tokens.add(hashlib.sha256(token.encode()).digest())
            self.code = f'{secrets.randbelow(1_000_000):06d}'
            self.qr_secret = None
            self.paired_count += 1
            self.started = now
            self.failed_attempts.pop(client_ip, None)
            return token

    def revoke(self, header):
        if not self.authorized(header):
            return False
        digest = hashlib.sha256(header[7:].encode()).digest()
        with self.lock:
            self.tokens.discard(digest)
        return True

    def authorized(self, header):
        if not header.startswith('Bearer '):
            return False
        digest = hashlib.sha256(header[7:].encode()).digest()
        with self.lock:
            return any(hmac.compare_digest(digest, t) for t in self.tokens)


def lan_ipv4(host):
    """Only RFC1918 IPv4 host addresses; do not advertise localhost or wildcard binds."""
    try:
        addr = ipaddress.IPv4Address(host)
    except ipaddress.AddressValueError as exc:
        raise ValueError('Provide a valid private IPv4 LAN address') from exc
    if not any(addr in ipaddress.IPv4Network(net) for net in ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')):
        raise ValueError('Provide an RFC1918 private LAN IPv4 address')
    return str(addr)

def make_handler(state):
    class Handler(BaseHTTPRequestHandler):
        def respond(self, status, payload):
            body = json.dumps(payload, separators=(',', ':')).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path == '/health':
                return self.respond(200, {'status':'ok','mode':'ayn_read_only' if state.ayn_port is not None else ('external_snapshot' if state.snapshot_path else 'mock'),'protocol':1})
            if not state.authorized(self.headers.get('Authorization', '')):
                return self.respond(401, {'error':'unauthorized'})
            if self.path == '/v1/snapshot':
                if state.ayn_port is not None:
                    try:
                        return self.respond(200, load_ayn_snapshot(state.ayn_port, state.ayn_token))
                    except (UpstreamUnavailable, ValueError):
                        return self.respond(503, {'error':'ayn_game_unavailable'})
                if state.snapshot_path:
                    try:
                        return self.respond(200, load_snapshot(state.snapshot_path))
                    except (ValueError, OSError):
                        return self.respond(503, {'error':'external_snapshot_unavailable'})
                return self.respond(200, state.snapshot)
            return self.respond(404, {'error':'not_found'})

        def do_POST(self):
            if self.path == '/v1/unpair':
                if not state.revoke(self.headers.get('Authorization', '')):
                    return self.respond(401, {'error':'unauthorized'})
                return self.respond(200, {'unpaired':True})
            if self.path != '/v1/pair':
                return self.respond(404, {'error':'not_found'})
            try:
                size = int(self.headers.get('Content-Length', '0'))
                if size <= 0 or size > MAX_BYTES:
                    return self.respond(413, {'error':'invalid_size'})
                data = json.loads(self.rfile.read(size))
                if not isinstance(data, dict):
                    return self.respond(400, {'error':'invalid_json'})
                token = state.pair(data.get('code'), self.client_address[0], data.get('qr_secret'))
            except PairRateLimited:
                return self.respond(429, {'error':'pairing_rate_limited'})
            except (ValueError, TypeError, json.JSONDecodeError, AttributeError):
                return self.respond(400, {'error':'invalid_json'})
            if not token:
                return self.respond(403, {'error':'invalid_or_expired_code'})
            return self.respond(200, {'token':token,'protocol':1})

        def log_message(self, fmt, *args):
            pass  # Never log pairing codes or bearer tokens.
    return Handler


def run(host='127.0.0.1', port=8765, snapshot_path=None, ayn_port=None, ayn_token='', qr_window=False, advertise_host=None):
    snapshot = json.loads((ROOT/'samples'/'mock_snapshot.json').read_text(encoding='utf-8'))
    state = State(snapshot, host, snapshot_path, ayn_port, ayn_token)
    httpd = ThreadingHTTPServer((host, port), make_handler(state))
    print(f'PipBoyRemote {"AYN READ-ONLY" if ayn_port is not None else ("EXTERNAL SNAPSHOT" if snapshot_path else "MOCK")} server: http://{host}:{httpd.server_port}', flush=True)
    print('Pairing code (expires in 10 minutes):', state.code, flush=True)
    if host not in ('127.0.0.1', 'localhost', '::1'):
        print('WARNING: HTTP pairing/token traffic is unencrypted on LAN. Use trusted isolated Wi-Fi only.', flush=True)
    if qr_window:
        if not advertise_host:
            raise ValueError('--advertise-host is required with --qr-window; do not guess LAN address')
        state.qr_invite(advertise_host, port)  # fail before showing a misleading QR
        try:
            from bridge.qr_window import show_window
        except ModuleNotFoundError:
            from qr_window import show_window
        t = threading.Thread(target=httpd.serve_forever, daemon=True)
        t.start()
        try:
            show_window(state, advertise_host, port)
        finally:
            httpd.shutdown()
            httpd.server_close()
            t.join(timeout=3)
    else:
        try:
            httpd.serve_forever()
        finally:
            httpd.server_close()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8765)
    sources = parser.add_mutually_exclusive_group()
    sources.add_argument('--snapshot-file', default=None)
    sources.add_argument('--ayn-port', type=int, default=None, help='Read /state on 127.0.0.1:<port> from installed AYN mod')
    parser.add_argument('--qr-window', action='store_true', help='Show an always-on-top pairing QR window (Windows title-screen companion overlay)')
    parser.add_argument('--advertise-host', help='PC RFC1918 IPv4 LAN address embedded in QR; required with --qr-window')
    args = parser.parse_args()
    token = os.environ.get('PIPBOY_AYN_TOKEN', '')
    if args.ayn_port is not None and (args.ayn_port < 1024 or args.ayn_port > 65535):
        parser.error('--ayn-port must be 1024..65535')
    if args.qr_window and not args.advertise_host:
        parser.error('--qr-window requires --advertise-host')
    if args.qr_window and args.host in ('127.0.0.1', 'localhost', '::1'):
        parser.error('--qr-window requires an explicit LAN --host (0.0.0.0 or your private IPv4)')
    if args.qr_window and args.port != 8765:
        parser.error('Android prototype QR currently requires --port 8765')
    run(args.host, args.port, args.snapshot_file, args.ayn_port, token, args.qr_window, args.advertise_host)

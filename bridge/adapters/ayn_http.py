"""Read-only interoperability adapter for an independently installed AYN Dual Screen mod.

No upstream code/assets are included. This consumer requests only GET /state on
loopback; it never invokes the upstream /action endpoint.
"""
import json
import math
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

MAX_STATE_BYTES = 2_000_000
CATEGORIES = (
    ("weapons", "Weapons"), ("apparel", "Apparel"), ("aid", "Aid"),
    ("misc", "Misc"), ("ammo", "Ammo"),
)

class UpstreamUnavailable(ValueError):
    """No validated, ready game snapshot can be obtained."""


def _number(value):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    return value if math.isfinite(value) else None


def _string(value, limit=256):
    return value[:limit] if isinstance(value, str) else ""


def _array(value, limit=3000):
    if not isinstance(value, list) or len(value) > limit:
        raise UpstreamUnavailable("Malformed or oversized game-state array")
    return value


def transform_ayn_state(data):
    """Translate documented AYN /state fields into our v1 read-only schema.

    Missing values are omitted rather than backfilled with plausible figures.
    No input dict/list is mutated. No commands are exposed.
    """
    if not isinstance(data, dict) or data.get("ready") is not True:
        raise UpstreamUnavailable("Game not ready or state format invalid")
    if data.get("game") != "FalloutNV":
        raise UpstreamUnavailable("Unexpected game identifier")
    player = data.get("player")
    src_inventory = data.get("inventory")
    if not isinstance(player, dict) or not isinstance(src_inventory, dict):
        raise UpstreamUnavailable("Missing player or inventory section")
    sequence = data.get("tick")
    if not isinstance(sequence, int) or isinstance(sequence, bool) or sequence < 0:
        raise UpstreamUnavailable("Missing/invalid snapshot tick")

    inv = []
    for key, category in CATEGORIES:
        for item in _array(src_inventory.get(key, [])):
            if not isinstance(item, dict):
                raise UpstreamUnavailable("Malformed inventory item")
            count = item.get("count")
            if not isinstance(count, int) or isinstance(count, bool) or count < 0:
                raise UpstreamUnavailable("Invalid item count")
            row = {
                "id": _string(item.get("id"), 64),
                "name": _string(item.get("name")),
                "category": category,
                "count": count,
                "equipped": item.get("equipped") is True,
            }
            for src, dst in (("weight", "weight"), ("value", "value"), ("health", "condition")):
                n = _number(item.get(src))
                if n is not None:
                    row[dst] = n
            inv.append(row)
    if len(inv) > 3000:
        raise UpstreamUnavailable("Inventory exceeds item limit")

    fields = {
        "name": _string(player.get("name")), "level": _number(player.get("level")),
        "hp": _number(player.get("hp")), "maxHp": _number(player.get("hpMax")),
        "ap": _number(player.get("ap")), "maxAp": _number(player.get("apMax")),
        "carryWeight": _number(player.get("weight")),
        "maxCarryWeight": _number(player.get("weightMax")),
        "radiation": _number(player.get("rads")),
    }
    character = {k: v for k, v in fields.items() if v is not None and v != ""}
    map_data = data.get("map")
    if not isinstance(map_data, dict):
        map_data = {}
    if isinstance(map_data.get("cell"), str):
        character["location"] = _string(map_data["cell"])
    special = {}
    for entry in _array(data.get("special", []), 32):
        if not isinstance(entry, dict):
            raise UpstreamUnavailable("Invalid SPECIAL entry")
        name = _string(entry.get("name"), 64).lower()
        val = _number(entry.get("value"))
        if name and val is not None:
            special[name] = val
    quests = []
    for q in _array(data.get("quests", []), 1000):
        if not isinstance(q, dict):
            raise UpstreamUnavailable("Invalid quest entry")
        objectives = []
        for obj in _array(q.get("objectives", []), 200):
            if isinstance(obj, dict):
                objectives.append(_string(obj.get("text")))
        quests.append({
            "id": _string(q.get("id"), 64),
            "title": _string(q.get("name")),
            "status": "complete" if q.get("completed") is True else ("active" if q.get("active") is True else "inactive"),
            "objectives": objectives,
        })
    radios = _array(data.get("radio", []), 200)
    station = next((_string(r.get("name")) for r in radios if isinstance(r, dict) and r.get("active") is True), None)
    return {
        "protocol": 1, "source": "ayn-dual-screen", "mode": "read_only",
        "game": "FalloutNV", "sequence": sequence,
        "bridgeReadAtUnixMs": int(time.time() * 1000),
        "character": character,
        "special": special,
        "inventory": inv,
        "quests": quests,
        "map": {"world": _string(map_data.get("world")), "cell": _string(map_data.get("cell")),
                "markers": _array(map_data.get("markers", []), 1000)},
        "radio": {"station": station, "playing": station is not None},
        "survival": {"hunger": _number(player.get("fod")), "thirst": _number(player.get("h2o")),
                     "sleep": _number(player.get("slp"))},
        "capabilities": {
            "inventoryRead": True, "characterRead": True, "questRead": True,
            "mapRead": True, "radioRead": True,
            "equipmentWrite": False, "itemUse": False, "fastTravel": False,
            "radioControl": False,
        },
    }


def load_ayn_snapshot(port=27303, token="", timeout=1.5):
    """Fetch from loopback only: no arbitrary host, proxy, redirect or game write."""
    if type(port) is not int or not 1024 <= port <= 65535:
        raise ValueError("Invalid upstream port")
    if not isinstance(token, str) or len(token) > 96 or any(ord(c) < 33 or ord(c) > 126 for c in token):
        raise ValueError("Invalid upstream token")
    req = Request(f"http://127.0.0.1:{port}/state", headers={"X-AYN-Token": token} if token else {})
    # Do not follow redirects to another service; default urllib follows them.
    from urllib.request import build_opener, HTTPRedirectHandler, ProxyHandler
    class NoRedirect(HTTPRedirectHandler):
        def redirect_request(self, request, fp, code, msg, headers, newurl):
            raise UpstreamUnavailable("Upstream redirect refused")
    opener = build_opener(ProxyHandler({}), NoRedirect())
    try:
        with opener.open(req, timeout=timeout) as response:
            if response.status != 200:
                raise UpstreamUnavailable("Upstream rejected state request")
            raw = response.read(MAX_STATE_BYTES + 1)
            if len(raw) > MAX_STATE_BYTES:
                raise UpstreamUnavailable("Upstream state too large")
    except (URLError, HTTPError, TimeoutError, OSError) as exc:
        raise UpstreamUnavailable("Upstream unavailable") from exc
    try:
        return transform_ayn_state(json.loads(raw))
    except (UnicodeError, ValueError) as exc:
        raise UpstreamUnavailable("Upstream state invalid") from exc

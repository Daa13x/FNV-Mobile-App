"""Read-only external snapshot file adapter; it cannot establish game provenance."""
import json
from pathlib import Path

MAX_FILE_BYTES = 2_000_000

def load_snapshot(path):
    p = Path(path)
    if not p.is_file() or p.is_symlink() or p.stat().st_size > MAX_FILE_BYTES:
        raise ValueError('missing or oversized snapshot')
    data = json.loads(p.read_text(encoding='utf-8'))
    if not isinstance(data, dict) or not isinstance(data.get('inventory'), list):
        raise ValueError('invalid inventory schema')
    # A file supplied by a separate process is not automatically live game state.
    if data.get('protocol', 1) != 1:
        raise ValueError('unsupported snapshot protocol')
    result = dict(data)
    result['protocol'] = 1
    result['source'] = 'external-file-unverified'
    result['mode'] = 'read_only'
    result['capabilities'] = {
        **(data.get('capabilities') if isinstance(data.get('capabilities'), dict) else {}),
        'equipmentWrite': False, 'itemUse': False, 'fastTravel': False, 'radioControl': False,
    }
    return result

# HISTORICAL PASS 2 NOTES — superseded by `UPSTREAM_REVIEW.md` and Pass 4 README

# Pass 2 integration boundary
Confirmed repository names through GitHub search, NOT source-reviewed:
- https://github.com/robojan/RealPipboyNV
- https://github.com/robojan/RealPipboyApp
- https://github.com/AidansLab/PipBoy3000Companion
- https://github.com/Abacus1829/ayn-dual-screen

No third-party source code was copied. Licenses, APIs, TTW compatibility and native safety are not verified.

Implemented: `python -m bridge.server --snapshot-file path/to/state.json` from project root.
This loads a trusted external JSON snapshot on each authenticated GET /v1/snapshot.
It requires an `inventory` array and returns HTTP 503 if the snapshot is unavailable.
The exporter must output the format in samples/mock_snapshot.json.

Not implemented: game exporter, live xNVSE integration, remote item actions, Android APK, encryption.
Never expose this development HTTP service on an untrusted network.

Next: inspect upstream code and licenses; implement an isolated, safe, atomic JSON exporter; test against actual VLK-TTW profile.

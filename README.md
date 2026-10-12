# FNV Mobile App / Pip-Boy Remote — Codex handoff

**Current source is Pass 6**. See [`docs/CODEX_HANDOFF.md`](docs/CODEX_HANDOFF.md) for the single comprehensive Codex work order, [`AGENTS.md`](AGENTS.md) for engineering rules, [`docs/PASS_HISTORY.md`](docs/PASS_HISTORY.md) for all six archived passes, and [`SECURITY.md`](SECURITY.md) before enabling LAN access. This repo is independent of LifePlanSystem. **No APK, Fallout game mod binary, or live TTW proof is supplied.**

---

# Pip-Boy Remote — Pass 6: QR pairing + read-only AYN bridge

**Status: QR-enabled source prototype, NOT a compiled APK, and NOT a verified live Fallout installation.**

**NEW IN PASS 6:** One-use QR pairing and optional always-on-top Windows QR overlay for a borderless/windowed Fallout title screen. The overlay is **not drawn inside the game menu**; this requires future xNVSE/menu verification. See `docs/PASS6_QR_PAIRING.md` and `docs/VERIFICATION_PASS6.md`.

This independent project contains:

- A **native Android app in Kotlin/Jetpack Compose** with STAT, ITEMS, DATA, MAP, SYNC, paired read-only bridge requests, optional 5-second foreground polling and a labelled offline demo.
- A **Python 3.10+ Windows-side HTTP bridge** exposing a read-only snapshot protocol. It can serve explicit mock data, an unverified JSON snapshot file, or **GET /state** from an independently installed AYN Dual Screen xNVSE plugin via PC loopback.
- An **offline HTML UI preview** (`preview/index.html`) which simulates screens and search without accessing Fallout or your PC. For this preview, no Android SDK is needed.
- Automated bridge tests, Android JVM test source, a build helper and documentation.

No game mods, assets, DLLs, installed executables, saves, or LPS repositories are changed by opening this package.

## Open the UI immediately

Open `preview/index.html` locally in any modern browser. This is a **simulated interactive design preview**. It cannot connect to Fallout. You can also view `preview/preview_stat.png` and `preview/preview_items.png` (screenshots of the browser preview, not Android runtime).

## Run the Python tests

From the folder containing this README:

```powershell
python -m unittest discover -s tests -v
```

The tests use fake data and a synthetic local upstream server, **not VLK-TTW**.

## Run the Windows bridge

From the project root on a PC with Python 3.10+:

```powershell
python -m bridge.server
# mock data on 127.0.0.1:8765; the printed pairing code is single-use
```

For an independently installed, running and properly configured AYN Dual Screen plugin:

```powershell
$env:PIPBOY_AYN_TOKEN = 'your-upstream-access-token'  # only if token configured upstream
python -m bridge.server --ayn-port 27303
```

The game mod must be installed **separately from its official distribution** in an isolated VLK-TTW mod-manager profile. This source archive includes no upstream code or binaries. **Never install a new native DLL directly into your active VLK-TTW profile for an unverified test.**

On the PC, ensure the upstream AYN plugin uses `AllowLanAccess=0` so only the bridge can access it on loopback. Disable the upstream game-control permissions for initial tests. AYN's upstream defaults may differ. Check the upstream project's documentation and licence:

- https://github.com/Abacus1829/ayn-dual-screen
- https://github.com/Abacus1829/ayn-dual-screen/blob/main/falloutnv/README.md
- https://github.com/Abacus1829/ayn-dual-screen/blob/main/LICENSE.txt

The default bridge bind address is **loopback-only**. To permit the phone to pair on a *trusted private Wi-Fi*, explicitly opt into a LAN bind:

```powershell
python -m bridge.server --ayn-port 27303 --host 0.0.0.0
```

Use only TCP port **8765** in a restricted Windows Private firewall rule for the test. Do not forward ports or place the service on public/untrusted Wi-Fi. **The bridge currently uses unencrypted HTTP, so network observers may see credentials.** A secure tunnel/TLS is a hard prerequisite before wider use.

## Android build on your Windows PC

Android Studio, Android SDK platform 35, Java 17, and a compatible Gradle 8.9 installation are required. The source doesn't contain a verified Gradle wrapper or generated APK. Run:

```powershell
.\scripts\build-android.ps1
```

The script runs Android JVM tests and `assembleDebug` and checks for a real APK; it errors if required Gradle tools are missing. If needed, install a compatible Gradle and generate the **official** wrapper using `gradle wrapper --gradle-version 8.9`. The script was not executed here because this environment has no Android SDK/Gradle and cannot access the necessary distribution.

In the Android app, scan the optional one-use QR overlay (Pass 6) or enter your PC's private IPv4 LAN address (e.g. `192.168.1.50`) and the one-use pairing code printed by the bridge. **Do not type `http://`, a port or a public IP address**. A valid pairing triggers the first snapshot; manual refresh and opt-in foreground polling are available. Use UNPAIR to revoke the bridge token. Tokens are kept in ViewModel memory, surviving rotation but not a cold app restart.

## Pass 5 compared with Pass 4

- Native UI screens redesigned for mobile portrait with bottom navigation.
- Filterable/searchable inventory, item details, quest objectives, map marker list, radio readout, SPECIAL/survival status.
- Honest no-data and cached/offline states. No placeholder game data is silently treated as live.
- Offline, clearly labelled mock-data demo mode.
- Added limited private IPv4 host validation, 5-second foreground refresh, in-memory token retention across rotation, and unpair control; changing PC requires unpair first.
- Fixed external-file adapter to advertise protocol v1 when safe and reject explicit incompatible protocol versions.
- Included an independent mobile HTML preview and Playwright screenshot evidence.

## Important limitations

- **Real VLK-TTW game compatibility, native Android build, phone pairing, performance and save safety remain unverified.** Upstream author's TTW reports are not local proof.
- The bridge only reads game state and does **not** expose equip/use/drop/fast-travel controls.
- Map tab is a marker list; no actual map texture is bundled or displayed.
- Auto-refresh polls, not WebSockets or push. Last fetch succeeded ≠ game state tick advanced.
- HTTP pairing is not production secure; do not distribute as a production mobile companion yet.
- LPS remains separate; its repositories and authentication system have not been edited.

See `docs/PASS6_QR_PAIRING.md`, `docs/VERIFICATION_PASS6.md`, `docs/PASS5_UI.md`, `docs/VERIFICATION.md`, `docs/NEXT_PASS.md`, and `docs/LPS_BOUNDARY.md`.

# Pip-Boy Remote → Codex master handoff (2026-10-12)

## Objective and user intent

Build a full, practical Android Pip-Boy companion to the maintainer's **Fallout: New Vegas with Tale of Two Wastelands, VLK-TTW** setup. Phone-first five-tab interface (STAT, ITEMS, DATA, MAP, SYNC), real read-only game data as the first acceptance gate, then safe equipment, consumables, radio, quests, map and companion capabilities when individually proven. Android target includes a Samsung phone; precise device build/SDK must be read from the user's current environment. Both Mojave and Capital Wasteland eventually need regression testing. The user also wants **QR-code pairing visible over Fallout's title screen**, ideally a native menu surface if it proves compatible; an existing Windows overlay is the interim approach.

The user previously asked for Codex and Claude as peer orchestrators, roughly 50/50 workshare, using ChatGPT/Nemotron as cheaper bounded reviewers only when those routes are genuinely available and permitted. Do not claim agents executed when only a prompt or delegation route exists. Follow actual available tools/credits and safety gates.

## Current checked-in artifact

The repository root is the **extracted Pass 6 source**. `history/` contains unmodified downloadable ZIP archives for Passes 1 through 6 and an older standalone browser preview. The latest source is **not** a compiled APK and **not** an installed game mod. Its Python bridge can serve a mock fixture, a JSON snapshot, or translated data from an **independently installed** AYN Dual Screen plugin's local HTTP `/state` endpoint. The AYN module is *not* bundled.

Source layout:

- `android/`: Kotlin + Jetpack Compose prototype, five-tab UI, QR code scanner integration source, in-memory session token; Android SDK/Gradle not supplied.
- `bridge/server.py`: Python bridge, mock/file/AYN state, session pairing/revocation, HTTP endpoints.
- `bridge/adapters/ayn_http.py`: reads AYN `/state` on `127.0.0.1:<configured port>` and projects known fields; read-only.
- `bridge/qr_window.py`: optional Windows always-on-top QR overlay, not native Fallout menu integration.
- `preview/`: simulated HTML UI and screenshots (not an Android runtime screenshot).
- `samples/`: simulated snapshot fixture.
- `tests/`: Python bridge and contract tests; Kotlin test source exists but full Android Gradle suite is unexecuted.
- `docs/`: past-pass protocol, upstream review, validation, and roadmap.
- `scripts/build-android.ps1`: guarded Windows build helper; requires installed SDK, Gradle and Java.

## Verified from the exact recovered ZIPs in this handoff

All six ZIP archives were present, ZIP integrity checked, and recovered into an independent staging area. Individual Python tests were run against each archive (Pass 1: 4, Pass 2: 7, Pass 3: 8, Pass 4: 17, Pass 5: 21, Pass 6: 30, all passed when rechecked). For Pass 6, the exact command was:

```powershell
python -m unittest discover -s tests -v
```

These tests use simulated or synthetic upstream sources. **None proves a loaded xNVSE plugin, live game, Android APK, USB camera scanning, title-screen appearance, or security of LAN HTTP.** A prior development receipt records isolated Kotlin QR parser compilation and synthetic QR image decode, but does not establish an Android application build. Re-run everything on your machine before publishing acceptance claims.

## Highest-priority next slice (Pass 7)

1. **Inventory and baseline first.** Confirm the exact remote state and clean working checkout; run all Python tests. Inspect Windows environment without altering it: Vortex/MO2 profile, VLK-TTW version, xNVSE logs, game process, plugin load order, availability of SDK 35, JDK 17, Gradle 8.9, Android Studio, ADB, phone and backup/save locations. Keep private paths out of public logs.
2. **Official AYN integration, isolated.** Review original upstream source and licence *again*. Install official binary from original distribution into an **isolated** VLK-TTW test profile only with approval. Check version/ABI (xNVSE 32-bit DLL), UI mod conflicts, memory/performance, flags/permissions. Configure `AllowLanAccess=0`, access token where available, and turn off all game-side write actions during read-only testing. No upstream source is bundled here.
3. **Independent upstream runtime proof.** Start game through the verified launcher. Inspect xNVSE log and AYN plugin log. Request `GET http://127.0.0.1:27303/state` with configured auth; verify `ready`, `tick`, character, FormID, item count, no false state from a failed game. Pick up/drop a disposable test item in the isolated save and show snapshot change. Test game quit => unavailable.
4. **Bridge proof.** Run `python -m bridge.server --ayn-port 27303` (with the upstream token in `PIPBOY_AYN_TOKEN` if configured). Verify `/health`, one-use pairing, `/v1/snapshot`, error 503 on unavailable upstream, and no accidental mock fallback. Record before/after item count and evidence. No remote game writes.
5. **Android compile.** Install/build from `android/`, generate a real Gradle wrapper if missing, use `scripts/build-android.ps1`, run Kotlin/JVM tests and `assembleDebug`, inspect manifest/network-security config and Compose API compatibility; fix compilation errors. The current source is not yet a proven build.
6. **Real Samsung smoke test.** Pair on a controlled private LAN, test QR scan, rotation, reconnect, revoked/expired invite, invalid credential, no-game screen, foreground polling, returning from background, search and item details. Verify the phone sees the same FormID/count as game, update after pickup. Test both worlds when available.
7. **Secure transport before normal deployment.** Replace plaintext LAN HTTP with authenticated encryption (locally pinned TLS certificate or verified tunnel). Do not make the bridge public. Threat-model camera QR exposure, replay, pairing-window expiry, credential storage and token revocation. Keep pairing restricted to the intended PC/network.
8. **Title-screen QR: two phases.** First verify current always-on-top Windows overlay on borderless/windowed mode without stealing focus. Then *research* game-native menu options (compatible XML/UIO/xNVSE menu APIs) and choose a reversible implementation only if supported by VLK-TTW. Do not claim it's native until shown in-game. Do not alter the main menu in the first install step.

## Explicit acceptance criteria

**Gate A: no-game:** Bridge health works, snapshot source correctly refuses unavailable game; Android UI never presents demo values as live; pairing and unpair remain correct.

**Gate B: real-game data:** Game logs prove plugin loaded; official upstream `/state` has `ready:true`; item changes during play propagate through bridge to real physical phone with matching item ID and count; elapsed time, poll tick and source are recorded.

**Gate C: UI and QR:** Real APK installs on physical Android device; tab/navigation and soft keyboard work; scan a title-screen-adjacent QR in a supported window mode; single-use QR, timeout and unpair work without exposure of secrets.

**Gate D: safety:** No regression/crash/save corruption in isolated profile across a bounded play session; revert plan tested; no installation in primary VLK-TTW profile without separate explicit permission. For each tested state log relevant files and exact build/commit but exclude private paths and saves.

**Gate E: production-readiness later:** encrypted channel, reconnect design, Android data persistence decision, app background behaviour, repeatable APK build and two-world TTW soak test. Nothing preceding should be described as production ready.

## Upstream research (not copied into repository)

- **AYN Dual Screen** — https://github.com/Abacus1829/ayn-dual-screen ; FNV README `falloutnv/README.md`; documented `/state` read-only endpoint, `POST /action` action API, claims TTW compatibility. It is **source-available with redistribution restrictions** in `LICENSE.txt`; do not copy plugin/binaries or publish derivatives without permission. Publisher's reports are not our verified TTW runtime proof.
- **Aidan's Pip-Boy 3000 Companion** — https://github.com/AidansLab/PipBoy3000Companion ; NV xNVSE plugin, named-pipe/USB target, item and character sync concepts. **CC BY-NC-SA 4.0** original licence. Its known issues include modded FormIDs. Research only until licences and architecture are understood.
- **Robojan RealPipboyNV/App** — https://github.com/robojan/RealPipboyNV and https://github.com/robojan/RealPipboyApp ; older Android/in-game linkage. Need specific licence, modern ABI and Android compatibility review before reuse.
- **xNVSE** — https://github.com/xNVSE/NVSE ; game-thread safety, NVSE SDK and 32-bit plugin requirements.

## LPS interaction boundary

Relevant current repositories for later integration: `Daa13x/LifePlanSystem` and `Daa13x/LifePlanSystemPublic` (both private as inspected through the owner's GitHub access on 2026-10-12). Their status must be rechecked. The public-named repository is a Capacitor/JS app with existing phone sync (`src/nativeConnection.js`), *not* the same native Kotlin application here. **Do not reuse LPS pairing credentials, modify its Android project, copy its private files, or merge authentication without authorisation.** Keep a separate app. If user requests LPS edits, read its `rules/LIS_RULES.md`, onboarding manifest, `AGENTS.md`, specific task rules, Git/synchronisation contract, and execute required local preflight before any changes. LPS cloud model writers are governed by that project's main-only policy; this standalone repo has its own rules.

## Known issues and unknowns

- Android may not build until wrapper/plugin version alignment and missing build infrastructure are corrected.
- QR overlay source is untested on Windows and is not drawn in game renderer.
- LAN pairing transfers bearer secrets over HTTP. High-entropy QR does **not** supply confidentiality against on-path attackers.
- Current API is polling, not WebSockets; game tick advancement and fresh state need separate treatment.
- Snapshot mapper intentionally omits/normalises unknown AYN fields; full map textures, complex item stack identity, equipment actions and companion inventory are not implemented.
- Full Android/TTW runtime proof remains unknown; do not infer it from author's statement.
- Current repo contains no game copyrighted assets, upstream DLLs, or production release builds.

## Work ownership and reporting

Use evidence-first iteration: inspect → reproduce → smallest fix → test → document. Avoid token-expensive parallel rituals. Delegate bounded review tasks only after model route capabilities are verified, use real Codex/Claude 50:50 if feasible, and do not invent execution evidence. Every finished slice should state Observed, Inferred, Unknown; diff, exact command/test output, build hashes, risk, and next slice. No unattended game focus stealing or user input simulation without explicit approval.

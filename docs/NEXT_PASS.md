# Pass 7 — real game/Android acceptance, native title-menu research and secure transport

## Locally verified by end of Pass 6

- Python bridge with AYN loopback read-only adapter; 30 automated Python tests with synthetic data and QR authentication tests.
- Android Kotlin/Compose source for five screens, demo and foreground polling, not yet compiled.
- HTML offline visual preview tested in headless Chromium in Pass 5; Pass 6 added QR pairing copy but did not rerun Chromium.
- New QR invitation generated/decoded with local tools. Android QR URI parser compiled in isolation with `kotlinc`, not as a full APK.
- Optional Windows overlay window source is unexecuted in the Windows/game environment.
- No VLK-TTW game runtime, real phone, APK build or secure transport proof.

## Prioritised next actions on the user's Windows PC

1. Inspect current VLK-TTW installation, mod-manager profile, xNVSE log, active plugins, saves and backups. Build an **isolated testing profile** and preserve existing game state.
2. Obtain official AYN Dual Screen from its original project. Check exact source/build/permissions; do not redistribute or modify restricted code in this package. Limit AYN to `127.0.0.1`, disable game write settings.
3. Run Fallout from its known working launcher/TTW configuration. Verify from the **game log**, not merely a DLL on disk, that the upstream plugin loaded.
4. Fetch upstream `GET http://127.0.0.1:27303/state` on PC, confirm `ready:true`, `game:FalloutNV`, tick and actual inventory changes while picking up a disposable item.
5. Launch bridge `python -m bridge.server --ayn-port 27303`. Confirm bridge authentication, state mapping, unchanged tick warning, error 503 when game quits and no mock fallback.
6. Confirm QR overlay is visible in your borderless/windowed Fallout title menu or separately on the Windows desktop; test single-use and expiry. Research native Fallout menu UI integration before claiming it appears inside the game renderer. Install Android SDK/Gradle on PC; generate official Gradle wrapper; run `scripts/build-android.ps1`. Resolve real compile/unit-test errors before any APK claim.
7. Test Google Code Scanner module availability and QR scanning on the Samsung device, including an invalid or expired code. Test debug APK on physical Samsung Android phone in portrait and rotated orientation, network permission, pairing, refresh, auto-poll pause on background, unpair, lost network, cached warning, and item detail/filter screens.
8. Confirm the *same* in-game item FormID and count appear on the phone, and update after pickup/drop. Repeat in both Mojave and Capital Wasteland if applicable. Keep explicit proof receipts with no personal paths or save files published.
9. Replace plaintext HTTP on the LAN with an authenticated encrypted channel (secure proxy/tunnel or TLS with pinned/trusted credentials); threat-model pairing/token handling. Only then consider routine use.
10. Re-evaluate optional LPS integration with its current full onboarding, sync and execution governance; do not merge repos or authentication by assumption.

## Pass 7 possibilities after acceptance

- Read-only per-item weapon/armour damage, condition and repair/sort calculations where upstream supplies verified fields.
- Map imagery served from *user-owned installed game assets*, subject to licences and clear source attribution; no redistribution of game textures.
- Companion and status/quest enhancements after field mapping and TTW tests.
- Explicit opt-in remote equip/use commands using game-thread queue and acknowledged-vs-effect-verified receipts, rollback/retry protection and item identity validation.
- LPS advisory mode: inventory summaries and suggestions, no autonomous game mutation by default.

Do not claim any of the above is already working.

# Pass 5 Android UI, test scope and accuracy

## UI modules implemented (source; compilation not verified)

- **STAT**: player name, level, HP/AP/carry progress, caps, rads, SPECIAL, survival. Missing fields render `—`, not fictional values.
- **ITEMS**: text search, category filter, name/quantity sort, equipment label, condition meter, read-only bottom-sheet details.
- **DATA**: quest list and objective expansion; radio station status with no action button.
- **MAP**: world/cell text and available markers; explicitly not a texture-based game map and no fast-travel controls.
- **SYNC**: PC private IPv4 input, pairing code, manual refresh, optional 5-second foreground polling, unpair token revocation, capabilities, warnings.
- **DEMO**: bundled copy of `samples/mock_snapshot.json`, visibly labelled as simulated and never presented as a live game.

## Safety decisions

- PC host input allows only RFC1918 private IPv4 literal addresses; no arbitrary internet origins or URL paths.
- Tokens remain in app memory only. Re-pair after cold launch. Changing PC clears token and cached snapshot.
- The app never sends game mutation requests (no `/action`, `/equip`, `/drop`, etc.).
- Auto-refresh occurs while the app lifecycle is at least STARTED. A successful fetch is not proof that the game tick advances.
- When requests fail, UI displays an offline warning and labels previously received data as cached.
- Development HTTP remains unencrypted; **not safe on public/untrusted Wi-Fi**. Upstream AYN listener should bind to loopback only.
- We do not bundle, fork or copy restricted upstream plugins, game UI textures, sounds or icons.

## Unverified / deferred

- Android APK compile, emulator UI inspection, runtime network permissions, physical Samsung phone pairing, battery/load impact.
- Actual VLK-TTW gameplay or live `/state` session; user PC is not accessible from this chat.
- Screen-reader/accessibility testing, landscape adaptation, save-safe remote commands.
- Production-grade TLS or authenticated secure-tunnel transport.

## Build on PC

See `scripts/build-android.ps1`. Requires Android Studio SDK, Java 17, Gradle or an official generated Gradle wrapper. Android SDK platform **35** and matching build tools are expected by `android/app/build.gradle.kts`.

Do not distribute APK as verified until compile, install, pairing, and actual game-data changes pass. Test the read-only bridge first in an isolated VLK-TTW profile.

Tokens and snapshots are held in an Android ViewModel to survive ordinary activity recreation (e.g. rotation) but not a cold process restart. Editing the PC address requires unpairing first to reduce orphaned bridge sessions. The demo button is disabled while paired for the same reason.

`preview/index.html` is a **separate static browser demonstration**, not a mobile APK nor the deployed UI code. It was tested with a simulated embedded JSON fixture and does not have pairing/network behaviour.

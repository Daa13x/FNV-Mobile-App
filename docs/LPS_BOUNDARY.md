# LifePlanSystem integration boundary (scoped, read-only repository review)

Reviewed sources (2026-10-12):
- `Daa13x/LifePlanSystem/AGENTS.md` and `skills/evidence-first-debugging/SKILL.md`.
- `Daa13x/LifePlanSystemPublic/AGENTS.md`, `package.json`, `android/app/build.gradle`, `src/nativeConnection.js`, `src/lpsWearBridge.js`.

**Observed:** LPS Public's Android shell is Capacitor/Gradle with web frontend and a separate Wear bridge; our prototype Android app is Kotlin/Jetpack Compose. LPS's `src/nativeConnection.js` already has a private-network-host check and an authenticated LPS phone-sync identity protocol. This Pip-Boy bridge is *not* that protocol.

**Do not:** combine the two pairing tokens, splice game state into LPS personal sync, expose game data to LLMs by default, or modify the LPS `main` repository based on this prototype.

Safe next-stage option:
1. Keep Fallout adapter separately runnable and local-first.
2. Once its protocol and security are verified, add an opt-in LPS web UI pane/client with an **explicitly distinct** service ID, pairing identity and permissions.
3. No direct game actions from model agents; actions remain denied by the bridge until separately permissioned and runtime-verified.
4. Follow LPS onboarding and Git authority rules before any LPS repo edits. The current session did **not** run the LPS write-cycle preflight or modify either repository.

LPS integration is **design only**, not implemented or tested. Current private/public repo branches and release state have not been validated through an authorized local checkout.

# Codex agent contract — FNV Mobile App

This repository is the independent **Pip-Boy Remote / FNV-Mobile-App** source project, not the LPS repository. The maintainer owns authorship; do not add AI co-author/sign-off trailers.

## First steps
1. Read `docs/CODEX_HANDOFF.md`, `README.md`, `docs/NEXT_PASS.md`, `docs/VERIFICATION_PASS6.md`, `docs/LPS_BOUNDARY.md` and `SECURITY.md`.
2. Check `git status --short --branch`, repository identity, tracked files, remote `main`, installed tools, and whether another writer is active. Preserve and sync the current checkout before changes. Do not force-push or rewrite published history.
3. Run `python -m unittest discover -s tests -v`; preserve the green baseline. Do not treat this as proof of an Android APK or running Fallout.
4. Independently check upstream project source, licences, SDK/build compatibility, and actual installed VLK-TTW environment. Existing notes are research, not a substitute for current evidence.
5. Work in small reversible slices; verify the user-visible journey. When unavailable, label the exact blocker rather than simulating a success receipt.

## Hard boundaries
- Do not alter the active VLK-TTW mod-manager profile, executable, xNVSE DLLs, installed saves, or unrelated PC/windows without explicit permission; use a test profile and backups.
- Treat the AYN second-screen integration as externally installed **source-available** software. Do not copy or redistribute it without its author's permission. Review `docs/UPSTREAM_REVIEW.md` and `docs/CODEX_HANDOFF.md`.
- This project's bridge is read-only; keep equip/use/drop/fast travel disabled until explicit, authenticated, game-thread-safe actions are designed, approved and verified.
- Do not expose insecure HTTP pairing to public networks or port-forward it. QR pairing is not a substitute for authenticated encrypted transport.
- The Windows QR overlay is a separate top-level window, **not** a native Fallout title-screen injection.
- Do not claim an APK, live Fallout connectivity, or TTW compatibility without actual runtime proof. Compilation alone is not runtime evidence.
- The LPS projects are a distinct ownership and auth boundary. If asked to edit LPS, perform its **current** complete onboarding and follow the repository-specific sync/Git contract. This repo's instructions do not override it.

## Reporting
Always distinguish OBSERVED, INFERRED, UNKNOWN; list exact commands, affected files, real test results and remaining blockers. A passing synthetic test is synthetic proof only.

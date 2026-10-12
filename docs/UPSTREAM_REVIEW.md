# Upstream projects: source-level review (2026-10-12)

This is a scoped, read-only GitHub inspection. We did not build, download binaries, run plugins, or copy upstream code into this deliverable.

## AYN Dual Screen — recommended independent runtime plugin

- Repository: https://github.com/Abacus1829/ayn-dual-screen
- Reviewed: `falloutnv/README.md`, `falloutnv/src/Dtos.h`, `falloutnv/src/WebServer.cpp`, `falloutnv/src/Config.cpp`, `falloutnv/src/main.cpp`, `LICENSE.txt`.
- The upstream README documents TTW support, `/state` JSON, `/action` game commands, and port **27303**. Game-thread ownership is explicitly documented; a queued command acknowledgement is **not** a verified in-game effect.
- Upstream claims TTW support, but it is neither tested here nor established for **VLK-TTW**.
- **Licence:** source-available custom licence; redistribution of modified source or builds is restricted. Therefore do not vendor it, fork/repackage it into this ZIP, or claim it is open-source. Users may independently install the official upstream build in accordance with its licence.
- **Risk:** default upstream settings allow LAN access and some actions. For this project's read-only path, disable upstream LAN exposure and game actions; leave the game-facing port loopback-only. Our adapter reads `/state` only.

## Aidan's PipBoy3000Companion — alternative for a future native bridge

- Repository: https://github.com/AidansLab/PipBoy3000Companion
- Reviewed: root `README.md`, `LICENSE`, `GamePlugin/src-nv/` directory listing.
- Designed to sync with a physical USB Pip-Boy; using its snapshot path for Android would still require a separate integration. Native xNVSE development is more invasive than using the existing AYN HTTP `/state` integration.
- **Licence:** CC BY-NC-SA 4.0 (including source). A derivative would need attribution, non-commercial restrictions and compatible share-alike terms. Not copied in this deliverable.
- Its README warns YUP can affect item FormIDs; VLK-TTW uses heavily modded content, so do not assume compatibility.

## Robojan RealPipboyApp / RealPipboyNV — historical reference

- Repositories: https://github.com/robojan/RealPipboyApp and https://github.com/robojan/RealPipboyNV
- Reviewed: repository root listings only (historical Android and native plugin projects).
- Licence and modern xNVSE / Android compatibility **not verified**. Do not copy or publish code from these projects on the assumption that GitHub visibility confers permission.

## Decision

Maintain our small, original standard-library-only Python interoperability client; let the user install the upstream AYN project directly. Do not redistribute upstream assets/code. Revisit a clean-room or explicitly licensed native adapter only if AYN cannot operate safely on VLK-TTW.

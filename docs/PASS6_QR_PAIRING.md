# Pass 6 — one-use QR pairing and Windows title-screen companion overlay

## What is implemented

- Windows bridge generates a random **24-byte / 192-bit QR pairing secret**, distinct from its existing manual six-digit code.
- Secret is **one use**, automatically expires after **120 seconds**, and never appears in an HTTP GET or log. A successful manual or QR pairing invalidates all previously displayed pairing credentials.
- Same IP-based rate limits apply to QR and manual pairing; bearer tokens remain in Android app memory only.
- URI format (versioned, fixed TCP port): `pipboyremote://pair?v=1&host=192.168.1.60&port=8765&secret=<32-character-URL-safe-secret>`.
- Android QR scan uses the Google Code Scanner (Google Play services) and does **not** request camera permission. It validates scheme, host, parameter names, duplicates, protocol version, RFC1918 IPv4 address, port and QR secret format before sending a POST.
- Windows can open a **small always-on-top QR window**. This window can overlay Fallout if the game is in compatible **borderless/windowed mode**. It is a separate OS window: **no plugin draws it inside Fallout's native main menu yet.**
- After a successful pairing the desktop window shows a confirmation and minimises, to avoid covering gameplay. Pair another phone by restoring the window and selecting PAIR ANOTHER DEVICE.
- Manual IP + six-digit fallback is preserved; bridge/game writes remain unavailable.

## Start it on Windows

Requirements: Python 3.10+; install QR UI dependencies if absent:

```powershell
python -m pip install "qrcode[pil]"
```

Check your own PC LAN address with `ipconfig`, e.g. `192.168.1.60`. With the existing, independently installed **AYN** plugin exposing `/state` on local port `27303`:

```powershell
$env:PIPBOY_AYN_TOKEN = 'only-if-an-upstream-token-is-configured'
python -m bridge.server --ayn-port 27303 --host 0.0.0.0 --advertise-host 192.168.1.60 --qr-window
```

Replace the IP with the PC's **real private IPv4 address**. The `--host` option opens the prototype bridge on all PC interfaces. Use a **narrow Windows Private firewall allow rule** restricted to the phone/local subnet for TCP 8765, **never port-forward** and don't use guest/public Wi-Fi. If the PC has multiple network adapters, use `--host 192.168.1.60` instead of `0.0.0.0` when practical.

For a **mock-data** demonstration without Fallout:

```powershell
python -m bridge.server --host 192.168.1.60 --advertise-host 192.168.1.60 --qr-window
```

This is not a VLK-TTW install script. Do not install new xNVSE binaries or change mod profiles through the bridge.

## Pair from Android

1. Put the phone and PC on the same trusted private network.
2. Open the native Pip-Boy Remote Android app > SYNC > **SCAN QR ON PC TITLE SCREEN**.
3. Point at the QR window shown on the PC. The app reads the host and one-time secret and attempts to pair immediately.
4. If the scanner module is missing, Google Play services may download it at first use. You can still use manual pairing.
5. A successful pair requests an inventory snapshot. If no game is running, the app reports game unavailable rather than inventing inventory.

QR invitation lifetime: 120 seconds. An open QR window automatically refreshes an expired code. If one phone pairs, the invitation is invalidated; restore the window and select **PAIR ANOTHER DEVICE** to authorise a second phone.

## Security limitations

**LAN HTTP is not encrypted.** Although the QR has a high-entropy secret, transmitting that secret and bearer token over HTTP permits an on-path network observer to intercept them. This is suitable only for controlled testing on a trusted network, not unattended or public deployment. Next pass: TLS/pinned cert or authenticated encrypted USB/tunnel transport before routine use.

The QR code itself grants pairing access for its short lifetime. Keep it on the PC screen only, do not post photos of a live QR code, and do not share logs containing it.

## Limitations and next pass

- The overlay is **not an actual Fallout title-screen UI component**. Full-screen exclusive rendering and Windows focus policy may hide it. Native game title-menu integration requires a separate xNVSE/menu UI compatibility study and VLK-TTW test profile.
- This pass has **no live game proof**, no tested Samsung APK, no verified Google scanner runtime and no encrypted transport.
- Research safe menu XML/UIO or script-extender-compatible menu surface for an optional real title-screen QR indicator; avoid interfering with the existing VLK-TTW UI stack.
- Test Android build and camera scanner on device, restart/reconnect/unpair behaviour, expired code, no-game mode, and two-device pairing.

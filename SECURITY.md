# Security and distribution restrictions

**This is an unfinished source prototype. Do not treat it as a production phone companion.**

- The bridge uses plain HTTP on the LAN: even a 192-bit one-use QR secret and authenticated bearer tokens **are not protected against a network observer**. Use only an isolated/trusted test network, local firewall rules, and *never* router port-forwarding. Routine use requires an authenticated encrypted transport.
- The separately installed AYN game mod should listen only on loopback (`AllowLanAccess=0`) with write permissions disabled during testing. Do not expose its controls on LAN.
- The code here does not supply any installable xNVSE DLL or game asset. Native Fallout plugins must only be evaluated in an isolated profile with backups and explicit permission before mutating files, saves or load order.
- Avoid logging live pairing secrets, passwords, tokens or private filesystem paths. Keep any real credentials in local ignored environment variables or secure storage, never committed.
- Upstream source-available licensing is restrictive. Do not vendor AYN Dual Screen code/builds, Aidan's code, or original Fallout art without a confirmed licence/permission path.
- Current Android tokens are in-memory only and session loss requires pairing again; persistence and revocation behaviour need device testing.
- QR window is separate desktop overlay; do not advertise native title-screen integration until tested.

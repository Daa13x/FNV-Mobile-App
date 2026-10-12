# Provenance — historical prototype passes

These historical archives are retained byte-for-byte from the ChatGPT development session. **Use root source as the most recent snapshot**, not the older ZIPs, unless bisecting a regression. The timeline below describes intended changes and locally rechecked tests, not actual game deployment.

| Pass | Archive | Main change | Local Python tests |
| --- | --- | --- | --- |
| 1 | `history/PipBoyRemote_Pass1.zip` | Android Compose skeleton + Python mock server | 4 |
| 2 | `history/PipBoyRemote_Pass2_Integration.zip` | External snapshot file adapter | 7 |
| 3 | `history/PipBoyRemote_Pass3_BridgeFix.zip` | Connect and repair bridge snapshot handling | 8 |
| 4 | `history/PipBoyRemote_Pass4_AYN_ReadOnly.zip` | Independent upstream AYN HTTP read-only adapter and LPS boundary notes | 17 |
| 5 | `history/PipBoyRemote_Pass5_Android_UI.zip` | Five-tab Android UI, mock UI preview, extra contract tests | 21 |
| 6 | `history/PipBoyRemote_Pass6_QR_Pairing.zip` | One-time QR invitation, Android scanner source, Windows overlay | 30 |

The preview screenshots in `preview/` reflect the browser mock, **not** Android screenshots. The archived Pass 5 standalone HTML preview is preserved in `history/PipBoyRemote_Pass5_Preview.html`; the current preview includes updated text.

`docs/VERIFICATION_PASS6.md` is the contemporaneous source receipt. Re-run tests and record environment-specific evidence. File digests for the archives are in `docs/ARCHIVE_SHA256.txt`.

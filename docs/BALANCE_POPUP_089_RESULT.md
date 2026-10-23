# Balance popup 0.8.89 — final software qualification

Release: https://github.com/Denberg28/TeleRC/releases/tag/v0.8.89
Verified product source: `912eb18b19f79bdf99f3cc22de760dee50ed5edb`.
Workflow: https://github.com/Denberg28/TeleRC/actions/runs/39512477329
Android version0.8.89/code107, retained signer. APK SHA256:
`83e3ee21887aa8896359cf3d752fb499d8d515e5f33ef8bc0d9e9f3161270558`.

| Gate | Final evidence |
| --- | --- |
| A — source/build | PASS — debug/release builds and lint. App-only; no affected firmware target. |
| B — behavior | PASS — 162 JVM tests, zero failures/errors/skips. |
| C — regression | PASS — core sync, packaging, bridge/mini/default sanitizers, profiles and balancer host suite. |
| D — software integration | PASS — full API35 native UI suite; Balance bounds/centering, equal padding, scrolling, visible footer, offline locks, Controls navigation, Close/reopen in three themes at normal and1.3 text. |
| E — physical HIL | BLOCKED — no actual phone/rover attached. |
| F — delivery | PASS — retained signer/code progression, alignment, artifact and APK hashes/ZIP CRCs, signed prerelease/source/evidence/checksums published. |

The new footer assertion exposed Android baseline alignment shifting a multiline
STOP label down in the initial run. Disabling baseline alignment and centering
the header/footer children fixes that defect. The complete final rerun passed.
Final Light/large, Dark/normal and Sepia/large screenshots were visually inspected;
the popup stays centered with its fixed footer visible and readable.

Only Balance presentation/lifecycle changes. Shared popup renderer, other tab
bodies and firmware are unchanged. Retain balancer0.3.0; no reflash.

Next concrete check: install over0.8.88 on the actual phone, inspect the popup at
the user's display/text scale and check Close/Back/background/reopen. For live
setup keep motor power disconnected during calibration and securely support the
chassis with wheels raised for direction tests; use BALANCE_POPUP_089.md.
Software success does not establish physical balancing or measured stop times.

Rollback: prefer a forward-code revert preserving preferences. Uninstalling to
force an Android downgrade deletes app data; export first. No stable promotion
or main merge was made. This documentation-only record does not change the
verified product source or release artifacts.

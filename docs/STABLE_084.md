# TeleRC v0.8.84 — stable: compact popups and consistent transitions

Owner-requested stable promotion on 19 October 2026 (Asia/Manila). This explicitly selects stable/Latest for the already qualified v0.8.84/code102 artifacts, overriding the repository's default candidate-only and hardware-gated promotion restriction for this release. It does not establish physical phone, rover, RF/FC/USB or field qualification.

All tabs use content-measured popup heights and screen-bounded widths, compact short forms/lists/notices, readable scrolling long content, shared action sizing/dismissal/transitions and stable Market category frames. Existing Setup Guide, compact pane, transmitter/phone controls, Waypoint Mission start and F1–F3 assignment remain included. No firmware reflash.

## Verified product and delivery

Product source: `f3eba8b620ca49b1030434fae6bb7f8dc94a246c`. [Qualification run39161856095](https://github.com/Denberg28/TeleRC/actions/runs/39161856095) passed protected-source review, JVM tests, debug/release lint/build, motor synchronization, host/mini/profile tests and default sanitizers, complete API35 native UI/transition/safety regressions, retained signing identity, increasing version, permissions/USB manifest, ZIP alignment/CRC and packaging/checksums. Native captures cover Light/Sepia/Dark and large text. Gates A–C and software D/F PASS; physical integration/installation/compatibility and E HIL remain BLOCKED/NOT TESTED.

Promotion verifies the successful run/source/tag, all six original asset digests and downloaded bytes, archive integrity, checksum manifest, native evidence and source archive commit. It changes release channel, Latest, title and release notes only. APK, source/evidence archives, original BUILD_INFO/START_HERE and checksum files remain byte-identical; their prerelease wording records the original qualification stage. The promotion workflow logs an audit linking the product and promotion commits. No rebuild, product-source edit or tag movement.

## Installation, acceptance and rollback

[Download TeleRC-v0.8.84.apk](https://github.com/Denberg28/TeleRC/releases/download/v0.8.84/TeleRC-v0.8.84.apk) and install over the existing same-signer app without uninstalling or clearing data. Retain settings, wallpaper, routes, records and wallet. No reinstall is needed if 0.8.84/code102 is already installed.

Follow the [phone/keyboard and restrained-wheel acceptance procedures](https://github.com/Denberg28/TeleRC/blob/f3eba8b620ca49b1030434fae6bb7f8dc94a246c/docs/POPUP_LAYOUT_084.md). Confirm every tab's popup bounds, validation/cancel behavior, large text, Market category stability and lifecycle cleanup. On restrained wheels with independent power cutoff, verify modal neutral/control revocation and FC HOLD; closing a sheet must not resume motion. Existing unsigned Wi-Fi/MAVLink and physical reconnect/stop/USB limitations remain documented; no cryptographic-channel or physical-reliability claim is added.

For rollback, use a higher-code same-signer forward revert preserving wallet v2 and app data; Android normally blocks version-code downgrades and uninstalling loses private data. Retain the preceding APK/source and FC parameter backup. Channel selection can be reverted without deleting assets or moving tags. Next concrete check: owner phone installation/keyboard layouts, then the linked raised-wheel acceptance.

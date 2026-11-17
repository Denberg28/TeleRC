# TeleRC 1.2.0rc1 delivery — 17 November 2026

Published signed prerelease: https://github.com/Denberg28/TeleRC/releases/tag/v1.2.0rc1
APK: https://github.com/Denberg28/TeleRC/releases/download/v1.2.0rc1/TeleRC-v1.2.0rc1.apk
Release source/tag: `3f6d0adc059827a9b527d26ac1e352841c7125f1`; code 1200; application `io.github.denberg28.telerc`.
Successful signed workflow: https://github.com/Denberg28/TeleRC/actions/runs/44086597994
Review PRs #15 and #16 are merged. The user explicitly authorized push/build/release. CLI Git writes lacked credentials; connected GitHub publishing preserved exact local Git-tree hashes, including executable file modes.

Delivered: compact single-rover Controls/Mission/Setup/LoRa, local 100-waypoint editor with save/edit/reorder/import/export, retained Wi-Fi/native USB LoRa, updated kickoff/AGENTS/constitution. No mission upload/AUTO/execution, game, FPV, balancing, native ELRS Backpack or standalone ELRS handshake profile is advertised. FC Wi-Fi retains released physical CH7 permission checks; Mini Wi-Fi retains sent-neutral ARM dwell. Unsupported old profiles return to disconnected Setup; reconnect never enables/arms.

Release continuity: available baseline v1.1.0rc1/code 1100/source `03b5b69ff39487678d25a8023c5438a773ca3d51` was newer than the initial default branch. Relevant control/parser/recovery/updater/response safeguards were carried forward. Initial 0.8.54 workflow `44083882236` failed closed on missing historical APK references and published nothing. Final packaging uses available v1.0.0 and v1.1.0rc1 and preserves their signing certificate.

Verified on final source: 68 JVM tests (0 failed/skipped), debug/release lint and APK builds, full host/default ASan/UBSan, ten actual ESP32 firmware configurations, firmware roles/images/offsets/digests and signer/version progression. BUILD_INFO and SHA256SUMS are release assets. Downloaded all five assets and independently checked GitHub asset digests, SHA256SUMS, package/code/version, retained signer, bridge archive commit, firmware manifest source and all 40 binary hashes. Release tag points to the build source. All PASS within stated software/build scope.

Signing certificate SHA-256: `5e57bd9bb3d10d97e5abdad4c51dbf5282708db73f1844fac272403952f9f5ec`.
APK SHA-256: `73ff27602046805bb573bb19fe6ace56c07ad84a635d47667be38c08ff9ac122`.
Firmware ZIP SHA-256: `b33f94394a4ac29f4a0f3456f4b0b1e1baf4a61819a3e75bff8b2b4b393016d5`.

Next required checks: install over retained signed 1.1.0rc1; validate profile migration, preferences, native Android layout/document picker, Wi-Fi/USB/RF/PC interoperability and every restrained-wheel authority/watchdog/fault procedure in TESTING.md. Record hardware/firmware/configuration and measured outputs. These checks and field validation remain NOT TESTED/BLOCKED, so stable promotion is blocked. Local planning does not execute a rover mission. Keep the old APK/data backup; Android downgrade may require backup/uninstall/reinstall.

This documentation-only checkpoint does not change the published source/tag/APK or reuse a version. Future development must start from current main and inspect both source and published inventory.

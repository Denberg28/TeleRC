# 1.2.0rc2 current limits — 18 November 2026

Published [v1.2.0rc2](https://github.com/Denberg28/TeleRC/releases/tag/v1.2.0rc2), source `e667d92`; signed workflow 44287143408 and independent asset/manifest digest checks PASS. Software/package gates are resolved for this candidate; physical/installed checks below remain open.

- **BLOCKED — device/HIL:** no phone, T3-S3 pair, FC or wheels attached. Gradient/Setup layout, USB scheduling, FC mission transfer, CH7 loss, installation and actual onboard route verification need the executable tests in TESTING.md. Prerelease only.
- **Compatibility:** Wi-Fi mission upload needs the new `legacy-bridge` / `TeleRCBridge` firmware wired directly to the ArduRover FC UART. Existing dedicated Wi-Fi-to-motor and USB LoRa paths do not support mission messages. Upload over an older bridge times out and never reports success.
- **Mission recovery:** cancelled/failed uploads do not prove the old onboard mission survived; no rollback/clear/start is sent. Verify/download the onboard route in a capable GCS before AUTO. The app uploads only while disarmed; transmitter handles subsequent execution. No app mission-start/AUTO action is implemented.
- **Freshness:** the 250 ms bound applies only to Android's pending USB batches. Device/FC USB buffering and unsigned Wi-Fi replay limitations remain. It is not measured radio reliability or end-to-end stop latency.
- Local full LeakSanitizer is blocked by the execution sandbox's ptrace/process-inspection restriction; address/undefined checks can run with leak detection disabled. Full default sanitizer CI remains required. Signing uses the existing protected CI identity.

Historical release evidence below does not verify this change.

# Verification limits and next work

Current candidate 1.2.0rc1 is published from `3f6d0adc059827a9b527d26ac1e352841c7125f1` with successful workflow `44086597994`. Software, signed packaging, identity/version/signature, all asset digests and firmware-source checks PASS. The old local SDK/signing/publication blockers below are historical and resolved for cloud delivery. Actual device UI, installed upgrade/migration, native USB/RF/PC integration and physical HIL remain untested/blocked. Stable promotion is blocked; local planning does not execute missions. See STATUS.md and TESTING.md for exact evidence and next checks.

- **Local Android environment — BLOCKED:** this workspace lacks the Android SDK and cannot download Gradle from `services.gradle.org`. Initial local unit/build/lint checks could not run; authorized cloud build evidence is recorded in the final release `BUILD_INFO.txt`. Verify API 26/33+ receiver behavior, queued ARM cancellation, and heartbeat recovery on devices even after cloud checks pass.
- **Local actual firmware toolchain — BLOCKED:** Arduino CLI/ESP32 toolchain is absent here. Host stubs do not establish target compilation. Updated pinned cloud CI/release gates compile seven primary sketches, two alternate LoRa builds and the four-input PWM converter. Review CI results are recorded on the draft PR; release results appear in `BUILD_INFO.txt` only after an authorized release.
- **BLOCKED — HIL:** no attached phone, T3-S3 pair, motor MCU, F405, drivers or motors. USB attachment, electrical levels, radio profile, timing, stopping, resets, power supply and repeated takeover need measured bench evidence.
- **NOT TESTED — PC integration:** preserve sparse MAVLink inputs, but validate the actual Windows client/relay, wheel removal and release-to-transmitter workflow.
- **Reviewed limitation — freshness/security:** Wi-Fi/legacy MAVLink lacks signing and a command timestamp/replay window. A trusted endpoint's duplicate/delayed valid frames can renew accepted-command freshness. LoRa's one-use challenge limits radio response replay, but USB/UART input is physically trusted. Do not characterize Wi-Fi as authenticated or full-stack replay protected.
- **Reviewed limitation — updater:** `/releases/latest` excludes prereleases. New test candidates require manual installation; candidate creation must not replace the latest retained stable artifact. The update receiver fix is source-reviewed; actual download/install/signing continuity remains untested.
- **Reviewed limitation — physical safe state:** zero PWM or neutral override does not prove coast/brake behavior, stop distance or an autonomous FC stop. Document each installed mode's expected output and measure it.

Next: verify the final release `BUILD_INFO.txt` and checksums, then run `docs/TESTING.md` on raised wheels. Package/publish a signed test candidate only after required automated and packaging checks pass. Stable promotion remains blocked by HIL and installation/compatibility checks.


0.8.53 software-build update: release source `0f7272da0d30b398dfe13c0a1008afca93f24c5c` passed run 37619553457, including retained signing identity, increased code 71, Android tests/debug/release lint/build, full host sanitizers and ten packaged firmware configurations. Actual T3-S3 builds use manufacturer SX1262/SX1276 revisions with QSPI PSRAM/native CDC. This resolves cloud software/packaging gates; local infrastructure limitations and installed-device, native USB, RF/driver timing, PC and HIL limitations remain. Download checksums and BUILD_INFO from v0.8.53; follow `bridge/FIRMWARE_FLASHING.md` before flashing.


0.8.54: mission planning is local-only (no onboard upload/execution or AUTO control); TeleRC CSV is not an autopilot mission format. Map tiles depend on internet/cache, while coordinate editing and plan storage remain local. No phone/emulator is attached in this workspace, so layout, document-provider behavior and upgrade remain NOT TESTED until device checks. The local Android SDK/download limits above still apply; cloud build/packaging evidence will be recorded after completion. UI simplification removes old simulated/inferred positions, not saved private track data.


17 November 2026 final local update: previous local SDK/JDK/network blockers were resolved for this source review. All 47 JVM tests, final debug APK, debug/release compilation and both lint variants pass (0 errors, 34 warnings). Signed release packaging remains BLOCKED by missing local protected keystore; no replacement signer was generated. Public branch push was rejected by automatic approval review, which requires explicit current-conversation publication authorization. No new branch/PR/release is published. Full local leak checking remains blocked by ptrace; address/undefined checks pass separately. Device/UI, RF, PC and physical gates above remain pending. TESTING.md and STATUS.md contain exact next checks.

Publication authorization update, 17 November 2026: user explicitly authorized push/build/release. The previous automatic-review authorization blocker is resolved; signed CI/packaging evidence is pending.


Current 1.2.0rc1 update: release inventory correction is in STATUS/TESTING. Available baseline is 1.1.0rc1/code 1100, not the old default-branch 0.8.x files. Final app code 1200 retains relevant published safety/parser/updater/response checks. Local checks now pass 68 JVM tests and both lint variants; cloud candidate packaging subsequently passed in run 44086597994. Removed profile preferences reset to Setup with auto-connect off. Native ELRS Backpack, standalone ELRS handshakes, balancing, FPV and onboard mission execution are not supported by this compact candidate. Physical/installation gates remain open.

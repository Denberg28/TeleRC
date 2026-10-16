# Readable maze rover 0.8.65

Baseline stable 0.8.64/code82; candidate 0.8.65/code83. [Scope, A–F gates, acceptance and rollback](READABLE_ROVER_065.md). Thin wall drawing and collision share rectangles; the larger oriented rover uses the earlier top-view design. Old records remain Classic; new geometry has separate best times. Required cloud Android/native/host/sanitizer/parser/signing/package checks remain pending until the final workflow succeeds. Phone readability and physical HIL remain BLOCKED. App-only: firmware unchanged, no affected ESP32 target. Latest stable 0.8.64 stays retained. Next check: phone upgrade and four-direction/reverse/wall-contact practice in all backgrounds.

# v0.8.64 release-channel status

The owner requested Latest stable promotion of the already qualified v0.8.64 build. See [channel promotion, evidence and remaining limits](STABLE_064.md). The channel designation supersedes older candidate-only wording for this release. Physical phone installation, receiver/driver compatibility and restrained-wheel HIL remain pending; no field-validation claim. Existing APK/sketch/source/evidence/checksums and build tag remain unchanged.

# Navy / offline maze / 95% mini candidate

Baseline app 0.8.62/code80 `bc7e983b88f2deea28aaf7c81e5ecffc159da949`; candidate 0.8.64/code82 + standalone mini 1.2.3. Scope, acceptance, installation and rollback: [NAVY_MAZE_064.md](NAVY_MAZE_064.md).

A source/build: reviewed; local Android/actual ESP32 toolchains BLOCKED; cloud Android and six pinned ESP32-S3 builds REQUIRED. B behavior: maze generator/collision/timing/record and 95% PWM regressions plus native three-page lifecycle/theme checks REQUIRED. C regression: local host/profile/sync PASS; final cloud default sanitizers REQUIRED. D software integration: Android packets through mini parser REQUIRED; actual transport/driver integration NOT TESTED. E physical HIL/phone installation: BLOCKED; use the executable restrained-wheel sequence in the guide. F delivery: retained signer, code82, matching source/sketch/binaries/evidence/checksums and prerelease REQUIRED. A successful final workflow and BUILD_INFO supersede pending software statuses only. No physical/field/stable claim.

Test disables auto-connect and disconnects live authority; returning to Controls needs explicit Connect. Practice motion/time is approximate and active-time based. The firmware 242/255 ceiling multiplies app sensitivity: Drive100 gives OUT242; Drive95 gives OUT229. Other driver profiles retain their old limits. Physical stop/current/supply validation remains pending. Next check: install on phone, verify offline isolation with raised wheels, and measure PWM/stop/recovery.

# Verification limits and next work

- **Local Android environment — BLOCKED:** this workspace lacks the Android SDK and cannot download Gradle from `services.gradle.org`. Initial local unit/build/lint checks could not run; authorized cloud build evidence is recorded in the final release `BUILD_INFO.txt`. Verify API 26/33+ receiver behavior, queued ARM cancellation, and heartbeat recovery on devices even after cloud checks pass.
- **Local actual firmware toolchain — BLOCKED:** Arduino CLI/ESP32 toolchain is absent here. Host stubs do not establish target compilation. Updated pinned cloud CI/release gates compile seven primary sketches, two alternate LoRa builds and the four-input PWM converter. Review CI results are recorded on the draft PR; release results appear in `BUILD_INFO.txt` only after an authorized release.
- **BLOCKED — HIL:** no attached phone, T3-S3 pair, motor MCU, F405, drivers or motors. USB attachment, electrical levels, radio profile, timing, stopping, resets, power supply and repeated takeover need measured bench evidence.
- **NOT TESTED — PC integration:** preserve sparse MAVLink inputs, but validate the actual Windows client/relay, wheel removal and release-to-transmitter workflow.
- **Reviewed limitation — freshness/security:** Wi-Fi/legacy MAVLink lacks signing and a command timestamp/replay window. A trusted endpoint's duplicate/delayed valid frames can renew accepted-command freshness. LoRa's one-use challenge limits radio response replay, but USB/UART input is physically trusted. Do not characterize Wi-Fi as authenticated or full-stack replay protected.
- **Reviewed limitation — updater:** `/releases/latest` excludes prereleases. New test candidates require manual installation; candidate creation must not replace the latest retained stable artifact. The update receiver fix is source-reviewed; actual download/install/signing continuity remains untested.
- **Reviewed limitation — physical safe state:** zero PWM or neutral override does not prove coast/brake behavior, stop distance or an autonomous FC stop. Document each installed mode's expected output and measure it.

Next: verify the final release `BUILD_INFO.txt` and checksums, then run `docs/TESTING.md` on raised wheels. Package/publish a signed test candidate only after required automated and packaging checks pass. Stable promotion remains blocked by HIL and installation/compatibility checks.


0.8.53 software-build update: release source `0f7272da0d30b398dfe13c0a1008afca93f24c5c` passed run 37619553457, including retained signing identity, increased code 71, Android tests/debug/release lint/build, full host sanitizers and ten packaged firmware configurations. Actual T3-S3 builds use manufacturer SX1262/SX1276 revisions with QSPI PSRAM/native CDC. This resolves cloud software/packaging gates; local infrastructure limitations and installed-device, native USB, RF/driver timing, PC and HIL limitations remain. Download checksums and BUILD_INFO from v0.8.53; follow `bridge/FIRMWARE_FLASHING.md` before flashing.


0.8.58 USB FPV: based on released v0.8.57, retaining separate sensitivities/expo and Mini rover Wi-Fi/ARM behavior. Only USB UVC receivers are supported. Phone/receiver/powered-hub integration, physical installation, visual/device QA and RF/latency behavior remain NOT TESTED. USB frames do not prove healthy FPV RF reception. Default local leak inspection is blocked by the runtime; the cloud workflow retains default ASan/UBSan/leak checks before prerelease publishing. No stable promotion. See USB_FPV.md.

# Mini Rover Wi-Fi-only 0.8.55 verification status

Baseline: TeleRC main `67b2cf5137989c1a6a65a4b02af960105d04afa3` (0.8.53/code 71). The earlier local 0.8.54 Wi-Fi profile supplied the neutral-dwell helper and protocol fixture. Active edition is 0.8.55/code 73 and new standalone firmware 1.2.0; the old 0.8.54 receiver requirement is removed. See `MINI_WIFI_ONLY.md` for acceptance criteria, wiring and executable raised-wheel procedures.

| Gate | Status | Evidence |
| --- | --- | --- |
| A source/build | BLOCKED | Diff/YAML/source review pass. Actual Android and ESP32 compilation blocked: SDK/core absent; dependency downloads unavailable. Prepared cloud build not triggered. |
| B behavior | PASS (host only) | Twenty driver/count/Arduino API host combinations pass. New firmware tests exercise boot/disarm, neutral-only arm, watchdog/no-rearm, link/peer loss, local STOP, release, reversal, malformed datagrams and clock rollover. Android behavior is source-reviewed; JVM/device tests pending. |
| C regression | PASS (host only) | Existing bridge suite, motor-core synchronization, new safety suite and address/undefined sanitizers pass. Local sanitizers use `ASAN_OPTIONS=detect_leaks=0` because this environment cannot inspect process tasks. Leak detection remains BLOCKED locally; cloud workflow retains default leak detection. |
| D integration | BLOCKED | Protocol fixture and interop harness are supplied. Actual Kotlin-emitted frame test awaits JVM build. No phone/rover end-to-end test. |
| E HIL | BLOCKED | No attached ESP32, drivers, phone or measuring equipment; run the procedure in MINI_WIFI_ONLY.md. No brownout, timing or field reliability claim. |
| F delivery | SOURCE ONLY | Reviewed sketch/source bundle and SHA-256 manifest. No APK or flashed binary built in this task. No main merge or release. |

The new signed artifact-only workflow `.github/workflows/mini-wifi-build.yml` runs host/sanitizer checks, actual ESP32 core 3.3.2 compilation, Android unit/lint/debug/release build, existing-signer comparison against 0.8.53, and artifact checksums. It does not publish a GitHub release or merge main. Prepared build branch: `work/mini-rover-wifi-only-055`. The empty branch currently points to the main baseline; the proposed code tree has been prepared but no branch ref has been moved to it and no build has run.

An earlier automatic approval review rejected pushing the previous task's build branch because repository publication was outside explicit authorization. Per AGENTS.md, “publication requires authorization.” Request explicit authorization for the prepared build branch before triggering the remote build. Next action after authorization: create the reviewed commit, advance only that build branch, run/inspect the signed build, fix any build failures, download and save the actual APK and firmware. Keep hardware status pending regardless of software result.

## Authorized build update — 15 October 2026

The user authorized the new APK + sketch release. Build `38693069439` at source `f90b1c9fe876bc3fa098c788f4e970cd8e9924c0` passed the full host regressions and default leak-enabled sanitizers, 20 host driver/count/API combinations, all six actual ESP32-S3/core 3.3.2 configurations, Android JVM tests/debug-release lint/build and Kotlin-to-firmware protocol interop. Packaging failed because an unauthenticated private-repository baseline download returned 404. Artifact upload separately hit the account's storage quota. These were delivery failures; app/firmware code was not changed after passing.

The follow-up workflow authenticates the baseline release download, retains certificate/version verification, uses a GitHub prerelease for the verified package and evidence, and keeps the existing latest release unchanged. Increased Gradle build memory addresses observed metaspace pressure. Final BUILD_INFO.txt and the successful release workflow supersede earlier local build blockers. Phone/rover installation and hardware tests remain BLOCKED; use MINI_WIFI_ONLY.md. This is a test candidate, with no stable promotion or main merge.

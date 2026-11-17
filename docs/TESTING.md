# TeleRC QA gates and bench procedure

## Current published software evidence — 1.2.0rc1

Signed candidate at https://github.com/Denberg28/TeleRC/releases/tag/v1.2.0rc1; source/tag `3f6d0adc059827a9b527d26ac1e352841c7125f1`, successful run `44086597994`. 68 JVM tests (0 failures/errors/skips), both lint/build variants, full host/default ASan/UBSan, ten actual firmware configurations, signer continuity and upgrade code 1100→1200 PASS. Downloaded assets independently pass all five GitHub digests, SHA256SUMS, APK identity/signer, bridge source comment, firmware manifest/source and all 40 binary digest/offset checks. See release BUILD_INFO and current STATUS.md.

A/C software/build scope PASS. B device UI, D actual USB/RF/PC integration, E physical HIL, and F installation/upgrade/rollback remain NOT TESTED/BLOCKED; F signed publication/package/checksum portion PASS. Test candidate only. The historical local/prototype records below are not the final release verdict. Follow the current 1.2.0rc1 procedures plus the retained restrained-wheel tests before stable promotion.

## Acceptance criteria for 0.8.52

1. CH1 steering and CH2 drive retain 1500 µs neutral and existing sensitivity/range. No output before fresh verified heartbeat and explicit Enable Control.
2. Heartbeat jitter under 4 seconds does not revoke driving. At 4 seconds of heartbeat age, revoke authority and clear commanded axes before accepting recovery telemetry. A new heartbeat must not resume commands without another Enable Control press.
3. Tab/background/stop/disconnect revoke authority; STOP releases to the receiver while lifecycle stop holds neutral. Capture the original endpoint port for asynchronous safety packets. No old-session telemetry may enable a replacement session.
4. Queued ARM expires at 500 ms or on stop/pause/session invalidation; recheck each retry. Preserve standard DISARM and actual heartbeat confirmation. No forced arm.
5. Discovery/telemetry do not refresh motor-axis freshness. DIRECT stale axes enter zero-PWM FAILSAFE/disarm after the 500 ms software threshold. Recovered DIRECT requires neutral dwell, fresh ownership, and explicit ARM. A competing Wi-Fi endpoint cannot refresh the current controller's commands/lease.
6. Preserve framed UART, LoRa provisioning/authentication, motor source selector, FC forwarding and sparse PC override semantics. Exactly one removable gateway may connect.
7. Lint/unit/build and host/sanitizer checks block candidate packaging. Automated release creation is prerelease-only until a separate reviewed stable promotion has all required hardware evidence.

## Commands and evidence rules

```sh
./scripts/check-android-env.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
python bridge/sync_motor_core.py --check
bridge/tests/run.sh
SANITIZE=1 bridge/tests/run.sh
```

For release packaging also run `:app:lintRelease :app:assembleRelease` with the existing signing identity, actual ESP32 CI builds, artifact identity/version/signature/checksum checks, and device installation/upgrade. Dependencies: JDK 17, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20, SDK/build tools 35; ESP32 core 3.3.2 and RadioLib 7.2.1. Use the pinned workflows for actual target configurations; host macros for Arduino API 2/3 are not target builds.

Record command, source commit, configuration/toolchain, expected/actual results, status and evidence location. Later source changes require rerunning affected checks. Call code inspection self-review, host tests stub-tested, actual compilation build verified, physical tests hardware tested; do not substitute one for another.

## 0.8.52 rebuild evidence

Baseline: published 0.8.51 commit `a8e6ed7bab4948ab221aeb639b3ca1ea617e0e21`, verified APK/signing identity and matching sketches. Only version and documentation metadata change. Baseline software checks passed: 48 JVM tests, debug/release lint without errors, full host sanitizers and eight actual ESP32 builds. These are baseline evidence, not results of the new build.

Rebuild gates A–C and software parts of D/F require a fresh successful workflow on the final 0.8.52 source; its `BUILD_INFO.txt` and SHA256SUMS record completion. Gate E/HIL and installed-device, PC and physical integration remain BLOCKED/NOT TESTED. Next concrete check: install the signed candidate over 0.8.51 with settings retained, then execute the restrained-wheel tests below. Stable promotion remains blocked.

## Initial local source-review gate status — 7 October 2026

Baseline `777c04e5`; the initial source-review commit is `4418be3`. Logs below describe local verification before the authorized GitHub release build. The release's `BUILD_INFO.txt` identifies the final release commit and supersedes these local software-build blockers when the required cloud checks pass. HIL and installed-device gates remain pending until separately measured.

| Gate | Status | Actual evidence / missing checks |
| --- | --- | --- |
| A — source/build | BLOCKED | Diff whitespace, shell/YAML configuration checks and motor-core synchronization pass. Android SDK absent; Gradle download network unreachable. Actual Arduino toolchain absent. No Android or actual-board build claim. |
| B — behavior | BLOCKED | Motor/gateway/parser/provisioning host behavior passes. Four new Android control-gate tests are added but have not run; device receiver/ARM/lifecycle checks remain. |
| C — regression | BLOCKED | 15 C++ host executions (4 hybrid API/transport combinations, 8 LoRa role/radio/Wi-Fi combinations, protocol, setup, Wi-Fi gateway) and 4 Python relay tests pass. Full JVM/device and actual PC regression pending. |
| D — integration | BLOCKED | Stub forwarding, framing, mailbox authentication and source recovery pass; real USB/radio/FC/client path pending. |
| E — HIL | BLOCKED | No connected target hardware or measurement equipment. Execute procedure below. |
| F — delivery | BLOCKED | Review source and patch available with checksum/rollback instructions. No APK built, signed, installed, uploaded or published by this session. Source bundle is not an eligible installable test candidate or stable release. |

Memory-safety checks: **PASS — address/undefined-behavior checks**, all 15 C++ host executions plus 4 Python tests completed with `ASAN_OPTIONS=detect_leaks=0 SANITIZE=1`; no ASan/UBSan findings. See `address-undefined-sanitizers.txt`. This sandbox prevents LeakSanitizer from reading process tasks; preserve that failure and report leak detection BLOCKED. Local address/undefined-behavior checks may run with `ASAN_OPTIONS=detect_leaks=0`; CI retains default leak detection and must pass without weakening checks.

## Restrained-wheel HIL procedure

Record phone/Android/app version, PC client/OS/wheel where used, all board/radio revisions and firmware commit/flags, key/profile match (no secret key in evidence), FC version/parameter snapshot, driver/motor/battery/supply, harness/pins/logic levels, source-selector position, and instrument/sample rate. Use raised wheels, reduced sensitivity/output and a reachable independent motor-power disconnect. Check polarity and supply arrangements against the wiring guide before powering.

Provisional engineering timing limits below are **test criteria**, not measurements or guaranteed physical stopping times. Measure PWM/driver-enable with a logic analyzer and separately record motor stop/coast distance. Record last valid command and fault timestamps; use at least 1 kHz capture for these limits.

| Test | Procedure | Pass condition |
| --- | --- | --- |
| Boot/reset | Power/reset each controller with source selector in each supported mode | No unintended drive output; DIRECT stays disarmed. Neutral dwell required for selected source. |
| Channel/direction/sensitivity | Enable, neutral ARM, test each axis at 25% and 100%; release | Correct CH1/CH2/sign/motor mapping; release returns neutral; motor reversal passes through zero. |
| Heartbeat recovery | Keep command path working; suppress returned heartbeat >4 s, then restore it while touching stick | Android authority revoked; recovery restores link only. No new non-neutral RC until explicit Enable. No automatic DIRECT re-arm. |
| DIRECT command loss | While driving, remove gateway/link; also repeat with discovery still arriving but no RC updates | Both driver PWM directions zero within provisional 550 ms of last valid axis frame; DIRECT disarmed. Restore link with non-neutral input: no motion until neutral dwell, explicit enable/arm. |
| FC PWM loss | Select AUTOPILOT; interrupt one required M5–M8 input | Zero motor PWM within provisional 200 ms of last valid FC pulse; restore via neutral dwell. |
| AUTOPILOT app loss | Test manual RC override timeout with FC configured; then autonomous mode separately | Manual override path neutralizes as specified. Document autonomous continuation; do not declare universal link-loss stop. |
| Lifecycle and handover | Switch tabs, background, press STOP, disconnect, resume; test transmitter takeover | Tab/background neutral holds; STOP releases receiver, which can move vehicle; disconnect follows installed controller's documented failsafe/latch. Resume requires explicit Enable. Measure each output transition. |
| Queued ARM | Delay TX execution >500 ms; press ARM then STOP/background/disconnect before transmission; restore transport | Expired/invalidated ARM never reaches motor/FC. DISARM and heartbeat confirmation remain functional. |
| Competing sources | Connect second Wi-Fi sender during active lease; try two control clients | Second endpoint cannot take authority during active lease. Do not physically connect two gateway TX pins. |
| Malformed/replay/congestion | Inject corrupt/truncated UART/radio data, stale LoRa response, and TX backpressure | Invalid frame/challenge rejected; no stored command refresh or uncontrolled motion. Record Wi-Fi's explicit replay limitation rather than claiming rejection. |
| Swap/power faults | Disarm/disconnect, remove power, swap gateway; reset/brownout each module | No hot swap; no motion at reboot or change of link. Fresh link and explicit authority required. |
| Endurance | 100 connect/enable/stop/disconnect cycles per intended transport, then 30 min restrained operation | No unintended output, stale ARM or connection/control-state divergence; no unbounded queue growth. Record every timeout/reset. |
| Upgrade/rollback | Install with retained signer over previous version; validate data/transport settings | Package/signature correct, version increases, retained settings/routes behave; rollback follows instructions below. |

Stop on uncontrolled output, ineffective required failsafe or a broken core path. Fix, rerun affected automated tests, then repeat affected HIL on final source/configuration.

## Promotion and rollback

Do not trigger release automation during source review. After required automated and packaging checks pass, authorized publishing can produce a clearly labeled prerelease. The manual updater ignores prereleases, so install candidates explicitly. Stable promotion requires every applicable gate, measured installed safe states, and recorded device/PC compatibility.

Keep previous APK, matching firmware and secret-free configuration backups. Source rollback: retain baseline `777c04e5` and branch independently; do not overwrite user work. Installed Android normally blocks version-code downgrade: restoring code 69 may require uninstalling code 70, which deletes app data. Export routes/settings first; never switch signing identity to force an upgrade. Do not erase motor/gateway NVS or replace pairing keys for an app-only rollback.

## LoRa setup UI review — 7 October 2026

Baseline `51e98a0797c476647dea88cbebdee6a52503c2a6`. See `docs/LORA_SETUP_REVIEW.md` for findings, gates A–F, rollback and executable USB/visibility/layout checks. This is an app-only review; no firmware change or signed release. Local motor sync, full host subset and ASan/UBSan checks pass. SDK/Gradle and sandbox LeakSanitizer blockers remain; Android CI must run on the final review commit, and physical layout/USB/HIL are pending.

## Four-driver PWM and LoRa command review — 7 October 2026

This extends the setup review above; its original app-only scope is historical. Firmware-review baseline is `19ffbb903bd447e151ac678510f9aa4ceec99866`. See [LORA_MOTOR_REVIEW.md](LORA_MOTOR_REVIEW.md) for findings, compatibility, gates A–F and executable tests. There are now 21 C++ host executions and 4 Python relay tests. Both Arduino core API branches and both PWM input modes are exercised. Actual target CI requires seven primary builds plus two alternate LoRa and one four-input PWM build with ESP32 core 3.3.2 / RadioLib 7.2.1. Android unit/lint/debug build must pass on the combined PR. No release is triggered by this review; HIL remains BLOCKED.


## 0.8.53 authorized candidate build

Reviewed baseline `8e2e6df550f919d27d9ee00c5a6dc0ed45e9c9d2` passed Android CI run 37614043150 and ESP32/host CI run 37614043123: Android unit/debug lint/build, 21 C++ executions, 4 Python relay tests, default full sanitizers, seven primary and three alternate target builds. Those are baseline evidence. Candidate version 0.8.53/code 71 requires fresh release validation including debug/release lint/build, signing continuity with published 0.8.50 and binary packaging/hash verification. The release's `BUILD_INFO.txt` identifies the final commit and successful run; it supersedes baseline software evidence only after completion.

| Gate | Candidate status before release workflow | Required evidence / limitation |
| --- | --- | --- |
| A — source/build | PASS baseline; candidate NOT TESTED | Final release run must build Android and ten pinned ESP32-S3 targets, including actual T3-S3 profiles |
| B — behavior | PASS simulator tested; hardware BLOCKED | PWM startup/loss/recovery and authenticated protocol-to-four-driver tests; device/USB operation pending |
| C — regression | PASS baseline; candidate NOT TESTED | Final JVM, lint and full host/sanitizer checks |
| D — integration | PASS host chain; physical BLOCKED | Correct firmware role/profile/package hashes; USB/RF/FC/driver measurements pending |
| E — HIL | BLOCKED | No installed hardware; use `LORA_MOTOR_REVIEW.md` and restrained-wheel procedure above |
| F — delivery | NOT TESTED before workflow | Signed prerelease, matching source/binaries, checksums, installation/rollback and verified signer/version |

Next concrete check after successful publication: manually install 0.8.53 over 0.8.52, verify retained settings, flash the intended motor/BASE/ROVER roles without whole-chip erase, provision blank radios and execute four-wheel mapping and loss/recovery HIL. Stable promotion remains blocked. Android downgrade to code 70 normally requires uninstalling code 71 and loses app data; export routes/settings first. Keep the 0.8.52 APK and matching source firmware for rollback, preserve pairing NVS and signing identity.


## 0.8.53 published software evidence — 7 October 2026

Release source `0f7272da0d30b398dfe13c0a1008afca93f24c5c`, tag [v0.8.53](https://github.com/Denberg28/TeleRC/releases/tag/v0.8.53). [Signed release run 37619553457](https://github.com/Denberg28/TeleRC/actions/runs/37619553457), [Android CI 37619553716](https://github.com/Denberg28/TeleRC/actions/runs/37619553716) and [ESP32/host CI 37619553416](https://github.com/Denberg28/TeleRC/actions/runs/37619553416) completed successfully. This section supersedes the candidate-before-workflow software statuses above. A later documentation-only commit does not alter the tag's app/firmware source or build evidence.

| Gate | Published candidate status | Evidence / remaining check |
| --- | --- | --- |
| A — source/build | PASS — build verified | Final debug/release APK builds/lint and ten actual pinned firmware configurations; source commit recorded in BUILD_INFO/firmware manifest |
| B — behavior | PASS — simulator tested; hardware BLOCKED | JVM setup/control tests and host PWM/authenticated-UART-to-four-driver tests; phone and wheel behavior pending |
| C — regression | PASS — automated subset | JVM suite, 21 C++ executions, 4 Python relay tests, 3 package regressions and default full ASan/UBSan/leak checks; actual PC/device tests NOT TESTED |
| D — integration | PASS — host/package; physical BLOCKED | Exact board roles/profiles, source commit, 40 binary sizes/hashes, image offsets and complete internal checksums verified; USB/RF/FC/electrical path pending |
| E — HIL | BLOCKED | No hardware measurements; execute restrained-wheel procedure and LORA_MOTOR_REVIEW.md |
| F — delivery | PASS — prerelease packaging | Signed APK with retained signer and code71>code70, source ZIP, compiled binary ZIP, BUILD_INFO and SHA256SUMS published; installed-device/field validation NOT TESTED |

Next concrete check: manually install over 0.8.52, verify retained settings, flash the matching dedicated motor/BASE/ROVER images with default-layout NVS preserved, and execute provisioning/mapping/loss/recovery HIL. Stable promotion remains blocked.


## Unpublished 0.8.54 source-prototype verification — 17 November 2026

Source baseline `67b2cf5`; branch `simplify-go-lora-missions`; final source is the commit attached to the release BUILD_INFO/CI run. No firmware source changed. Supersedes old Test-map/game UI criteria; control/failsafe and restrained-wheel criteria above remain required.

| Gate | Final local status | Evidence / required follow-up |
| --- | --- | --- |
| A — source/build | PASS (build verified, local software scope) | Final Java 17 run: debug APK, debug/release Kotlin compilation and debug/release lint pass. Both lint reports have 0 errors, 34 warnings. Required signed-release packaging remains blocked under F. |
| B — behavior | BLOCKED (partial automated PASS) | All 47 JVM tests pass (0 failures/errors), including 5 mission tests. Host control/provisioning tests pass. Actual UI/device acceptance checks remain NOT TESTED. |
| C — regression | PASS (automated scope) | JVM control/heartbeat/LoRa/MAVLink/joystick/route/version and host firmware/parser regression pass. Hardware/actual PC regression is not implied. |
| D — integration | BLOCKED | Stub protocol tests and Android compilation pass; real document provider, USB/RF, telemetry and actual PC interoperability NOT TESTED. |
| E — HIL | BLOCKED | No phone/T3-S3/motor hardware attached. Run restrained-wheel procedure above on both transports. |
| F — delivery | BLOCKED | Debug APK has correct package/code 72/version 0.8.54 and verified debug signature. Release packaging fails because `teleRcRelease.storeFile` is absent locally; existing protected CI signer required. Explicit current-conversation push/build/release approval received on 17 November; protected CI build/package verification now pending. Installation/upgrade/rollback NOT TESTED. |

Final source is the local review commit containing this evidence; no app code changed after this run. Log: [android-final.txt](evidence/2026-11-17/android-final.txt). Environment: checksum-verified Temurin 17.0.21+9, Gradle wrapper 8.11.1, AGP 8.9.2, Kotlin 2.1.20, Android SDK/build tools 35. Initial system-Java/network/SDK blockers were resolved for local checks using the workspace SDK, downloaded JDK and configured environment proxy; cloud publication remains a separate blocker.

Final command: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease --no-daemon` in that configured environment. Unit/debug/lint tasks succeed; overall command exits 1 at `:app:packageRelease`: `SigningConfig "teleRcRelease" is missing required property "storeFile"`. Do not substitute a new signer or weaken the signing check. Use existing release CI after approval.

Host commands: motor-core sync PASS; normal full host suite PASS; firmware packaging tests 3 PASS. Full `SANITIZE=1` is BLOCKED locally by LeakSanitizer ptrace/process-inspection restriction. Separate `ASAN_OPTIONS=detect_leaks=0 SANITIZE=1` PASS verifies address/undefined behavior, not leaks. Firmware source stayed unchanged across these checks; full leak-enabled CI remains required for candidate packaging.

Additional device checks (expected results; NOT TESTED until observations recorded):

1. Start without permissions/network: Controls opens, Connect usable, no control/ARM becomes active. Open Mission: sample area is labeled; coordinates can be added without tiles/GPS. Deny GPS, then grant via Locate phone; phone Home stays distinct from waypoints.
2. Add 3 map/coordinate points (include negative longitude), edit middle point, move earlier/later, delete, cancel Clear, export CSV, clear, import, cancel/confirm replacement. Close/reopen/rotate/theme switch: plan/order restored exactly. Import NaN/out-of-range/101 points/oversized/bad sequence: current plan unchanged. Picker cancel/unavailable provider/write failure: no crash or false export-success message. Simulate storage failure: saved previous atomic file remains recoverable, failure shown.
3. On a narrow landscape phone in both themes/large font, header/footer remain available and waypoint list scrolls independently of map. Map/list select the same numbered point; Fit shows all points; no game/servo-assignment/simulation path appears.
4. With a restrained rover, Enable/ARM/axis release/STOP retains existing outputs. Navigate Controls → Mission while holding a stick: neutral authority revoked immediately; planning sends no waypoint/AUTO/ARM/drive command. Return to Controls/recover heartbeat/reconnect: explicit Enable/ARM as appropriate. Background during coordinate/file dialogs: no motion resumes automatically.
5. Upgrade signed 0.8.53 → 0.8.54: original track/endpoint/appearance settings survive; no old estimate is shown as GPS. Validate native USB permissions/provisioning and both link paths. Follow all failsafe/latency/endurance tests above before stable promotion.


Debug APK SHA-256 (review/testing only, Android Debug certificate; not an upgrade signed with the release identity): `a74ac2c446e3e913e2c8b02160e5827ee75eabea569392e96fe44c9be9ed48c0`.


## 1.2.0rc1 release continuity — current checks

The published baseline is v1.1.0rc1/code 1100/source `03b5b69ff39487678d25a8023c5438a773ca3d51`; default main's old 0.8.x release inventory was stale. Final candidate code 1200 keeps the compact UI/local planner plus relevant published safety/parser/recovery/updater/control-response checks. The 0.8.54 attempt `44083882236` fails publication on unavailable historical baseline APK URLs and publishes nothing. It is not the final source.

Local Java 17 final build: 68 JVM tests, debug APK, debug/release compilation and both lint variants PASS; 0 errors, 33 warnings. Log: [android-120-final.txt](evidence/2026-11-17/android-120-final.txt). Host field protocol, motor/LoRa/gateway/parser/provisioning suite and packaging tests PASS. ASan/UBSan PASS with local leak detection disabled; full leak-enabled CI and signed actual-board packaging remain required on final source. Earlier 47-test evidence is historical only.

Relevant regressions retained from released tag: RcTakeoverTest (CRC/source/time/freshness/CH7 fail-closed), LiveControlGateTest (neutral-only enable and heartbeat expiry), MiniRoverWifiTest (sent-neutral dwell, interruption/clock reset, golden MAVLink interoperability), BackpackRecoveryTest's applicable Wi-Fi cases (bounded network checks, lifecycle/rebind without authority restoration), ControlResponseTest/JoystickMathTest (axis preferences/expo), strict MavlinkTest and bounded UpdateMetadataTest. Native ELRS Backpack transport test is not copied because that profile is excluded and unknown saved profiles reset to disconnected Setup.

Additional HIL acceptance: FC Wi-Fi CH7 missing/stale ≥750 ms must prevent enable/new ARM and revoke live authority; restoring permission must not re-enable or replay ARM. A fresh-heartbeat DISARM remains available after CH7 permission expires. Request RC_CHANNELS and verify source/monotonic timestamps. Mini Wi-Fi must send fresh neutral frames for ≥300 ms before ARM; interrupted/stale/moving frames reset dwell. Network removal must create a new disabled session. Exercise upgrades from actual signed 1.1.0rc1, removed saved profiles, separate sensitivity/expo retention, all tab/background paths, native USB/LoRa and legacy FC bridge gates. These are executable expected results, not measured physical evidence. Keep the original restrained-wheel timing/watchdog and independent power-cutoff checks.

Current gates: A/C automated software checks PASS locally; B UI/device, D real integration, E HIL and F signed/installed delivery remain BLOCKED until respective observations/CI packaging. The final release BUILD_INFO will identify the signed source commit and cloud gates; it cannot establish physical readiness.

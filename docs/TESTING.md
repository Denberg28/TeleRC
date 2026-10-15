# TeleRC 0.8.56 restoration verification

Restores the full pre-simplification UI from 0.8.53 / local 0.8.54 mini-profile development. App ID and signing identity remain; code 74 installs over 0.8.55/code 73. User scope: roll back the simplified layout, keep all prior features and add Wi-Fi arm/disarm for the mini rover. Firmware 1.2.0 is unchanged; actual-target compilation evidence is retained in 0.8.55/run 38694370783. There are no affected firmware targets to rebuild for this app-only change.

Acceptance: all original tabs/map/test/game/LoRa/themes/diagnostics remain; fixed Mini rover Wi-Fi endpoint; normal Enable then fresh-neutral ARM; heartbeat-confirmed arm/disarm; no CH5/CH6 requirement for standalone 1.2.0. STOP/tab/background/heartbeat-loss authority rules remain from the original full app. Run the raised-wheel procedures in MINI_ROVER_WIFI.md.

| Gate | Status before cloud build | Required evidence |
| --- | --- | --- |
| A source/build | PENDING | Full JVM, debug/release lint/build; signing and upgrade identity. |
| B behavior | PENDING | Neutral-ready gate and cancellation regressions; actual device UI pending. |
| C regression | PENDING | All existing JVM and bridge host/sanitizer tests. |
| D integration | PENDING | Kotlin command frames consumed by the real mini parser harness. Physical connection pending. |
| E HIL | BLOCKED | No attached phone/rover. |
| F delivery | PENDING | Signed prerelease APK, source, setup, evidence and checksums; higher code 74. |

The successful final workflow and release BUILD_INFO.txt supersede these pending software statuses. This is a test candidate; phone installation and hardware tests remain pending. Next concrete step: install over 0.8.55 and perform MINI_ROVER_WIFI.md raised-wheel tests. Do not uninstall to downgrade: this update restores behavior with a higher version code.

Previous full app verification procedures follow for retained features.

# TeleRC QA gates and bench procedure

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

## Final 0.8.56 result

PASS — signed software test candidate published from `33240eff444c35304fa594542b9641d5bac8e40f`.
Workflow: https://github.com/Denberg28/TeleRC/actions/runs/38696307553
Release: https://github.com/Denberg28/TeleRC/releases/tag/v0.8.56

Gates A/B/C and software part of D PASS: full JVM regression, debug/release lint and APK builds, legacy bridge host suite and default leak-enabled ASan/UBSan, mini heartbeat fixture and Kotlin-to-real-mini-parser ARM/DISARM/RC integration. Gate F software packaging PASS: app identity, code 74/name 0.8.56, increasing upgrade code and signing continuity against 0.8.55 verified. Release contains APK, source, setup, test evidence, BUILD_INFO and SHA256SUMS. Actual firmware was unchanged and reused its 0.8.55 target-build evidence. Gate E plus phone installation, actual Wi-Fi/LoRa/FC/driver behavior remain pending.

APK: 51995958 bytes; SHA-256 `48f56a4e2cc5ef53a2fab78fa2e82d5fc813efafe075852a80e4f60d2635fc05`.
The app restores the full prior UI; only Mini Wi-Fi support and safe arm gating are added. Install over 0.8.55 without uninstalling, retain mini sketch 1.2.0, select Mini rover Wi-Fi, Connect → Controls → Enable → neutral ready → ARM, and run the raised-wheel procedure before ground driving. No main merge, release deletion or stable promotion was performed.

## Mini MX1508 firmware 1.2.1 pending gate update

User report: Wi-Fi connected/ARM accepted but no motor motion, with GPIO pairs 4/5, 6/7, 8/9, 10/11. Mapping matches the existing sketch. Reproduced a full-input range defect: APK 1900 yielded 142 instead of configured 180 PWM; added failing regression before aligning full scale to ±400 µs. PWM write failures were ignored; now detected failures disarm and latch all outputs off. Added nonblocking command/PWM diagnostics and a 1 kHz MX1508 bench default. Frequency/electrical cause is not proven. No APK/UI change; keep signed 0.8.56 and its build evidence.

Gates A firmware build, B fault/scaling behavior, C host/profile/sanitizer regression and F firmware packaging require the final firmware workflow. D physical app/driver/power path and E HIL remain pending; run MINI_MX1508_121.md. That guide records exact wiring, common ground and driver power, 100% sensitivity raised-wheel test, expected CH/duty and diagnostic interpretation. Next physical evidence: centered and held-forward Serial Monitor lines at 115200 baud. Do not claim confirmed motion or physical repair from host/target compilation.

## Final mini firmware 1.2.1 software result

PASS — firmware candidate at `c2d43d41ab1e5514b20bd55a4e7710fdf1943053`, workflow https://github.com/Denberg28/TeleRC/actions/runs/38699947458, release https://github.com/Denberg28/TeleRC/releases/tag/mini-rover-v1.2.1.
Gates A firmware target build, B automated scaling/fault behavior, C regression/sanitizers/profile checks and F packaging PASS. Six actual core 3.3.2 ESP32-S3 configurations compiled, twenty driver/count/API host configurations passed, and default leak-enabled sanitizer suites passed. No app sources or APK were changed. D physical command-to-driver/motor integration and E hardware verification remain pending. The old range mismatch is reproduced and corrected; actual cause of the user's no-motion condition is not physically confirmed. Next evidence: Serial Monitor lines at armed neutral and held-forward, common ground and driver supply verification, then raised-wheel stopping/recovery test. See MINI_MX1508_121.md.

## Mini MX1508 1.2.2 response candidate

Baseline local `d2de989`, remote `83ef606d2cbfa9877308372ae94e31dde3b01a64`; built 1.2.1 source `c2d43d41ab1e5514b20bd55a4e7710fdf1943053`. User reports movement, audible steering and very slow drive. Scope: MX1508 PWM frequency, drive cap, steering gain only; app/protocol/GPIO and safety rules unchanged. See MINI_MX1508_122.md for assumptions, exact outputs, raised-wheel HIL and rollback. Reproduced failing full-duty regression on baseline before changing settings.

| Gate | Status before final workflow | Evidence / acceptance |
| --- | --- | --- |
| A — source/build | Reviewed; target NOT TESTED | Compile six actual pinned core 3.3.2 configurations |
| B — behavior | PASS — simulator tested | Exact forward/pivot PWM, proportional response, normalized combined inputs, stop/rearm and PWM-fault regression |
| C — regression | PASS local; final CI pending | Host/sanitizers and twenty profile/count/API combinations; other driver limits retained. App untouched, evidence run 38696307553 retained |
| D — integration | Software reviewed; physical NOT TESTED | User reports 1.2.1 movement; new noise/drive performance needs actual Serial lines and supply measurements |
| E — HIL | BLOCKED | No attached rover; use MINI_MX1508_122.md, verify noise, current/temperature, stopping and no automatic rearm |
| F — delivery | Pending final workflow | Prerelease source/binaries/checksums tied to commit, setup and rollback |

Next physical check: sensitivity 100%, raised wheels, small command first; confirm full forward and full steering Serial outputs, release/DISARM/command-loss zero, then brief ground test. Full duty can increase current; this is not current regulation or measured speed control. No stable promotion.

### Final 1.2.2 software result

PASS — built source `b27e7e5019f16140ce7d39e051aabf1a8f33f9a8`, [workflow 38702871979](https://github.com/Denberg28/TeleRC/actions/runs/38702871979), [prerelease mini-rover-v1.2.2](https://github.com/Denberg28/TeleRC/releases/tag/mini-rover-v1.2.2). Gates A, automated B, C and F PASS: six actual core 3.3.2 ESP32-S3 builds, twenty driver/count/API host combinations, legacy/mini host regression, default leak-enabled ASan/UBSan, ZIP CRC and SHA-256 verification completed. No app source changes; retain APK 0.8.56 and its prior Android evidence. D physical performance and E HIL remain pending. User-observed movement applies to 1.2.1 only; 1.2.2 noise/torque/current/temperature/stop behavior is not physically verified.

Documentation-only correction after the built tag: core 3.3.2 maps requested 8-bit duty 255 to raw LEDC register 256, so full-on `PWM=256` is expected (OUT remains 255). This is supported by pinned `esp32-hal-ledc.c` `ledcWrite`/`ledcRead`, not a hardware measurement. The Arduino deliverable's updated START_HERE includes that clarification; earlier tag guide's PWM=255 example should be read with this correction. Firmware source is unchanged from the successful build. The original release remains retained. Next check: restrained-wheel full-drive/full-steer Serial lines with sensitivity 100%, then stop/loss/recovery and brief ground-load measurement.

## Independent control response 0.8.57 candidate

Baseline: signed full-interface app 0.8.56/code74 (`33240eff444c35304fa594542b9641d5bac8e40f`), combined source branch `8764ace89f1800699a5e54f746eed8a3da4e8ca2` (local 665ee39). Scope: independent steering/drive sensitivity, common expo, legacy preference migration, bounded response math and compact Setup dialog. No firmware source, transport, ARM/STOP or command timing changes. See CONTROL_RESPONSE_057.md for formula, compatibility, acceptance checks and rollback.

| Gate | Candidate status before workflow | Evidence / limit |
| --- | --- | --- |
| A — source/build | Reviewed; build NOT TESTED | Final JVM, debug/release lint/build required on pinned Gradle/SDK |
| B — behavior | Reviewed; simulator tests pending | Independent gains, migration, expo known values/endpoints/monotonic bounds, neutral/invalid input and shaped MAVLink parser integration |
| C — regression | Pending | Existing Android tests and full legacy/mini host/sanitizer checks |
| D — integration | Software reviewed; hardware BLOCKED | CH1 steer/CH2 drive, 1500 neutral, all transport/firmware formats retained; actual phone/rover not attached |
| E — HIL | BLOCKED | Raised-wheel phone, short landscape/font/theme/accessibility, persistence/cancel and stopping checks in CONTROL_RESPONSE_057.md |
| F — delivery | Pending | Signed APK code75, signer match and progression against 0.8.56, source/evidence/checksums and prerelease |

Next physical check after software success: upgrade with an existing sensitivity, verify migration and persisted independent sliders, then restrained-wheel CH1/CH2 endpoint, expo, release and loss/recovery behavior. Stable promotion remains blocked on applicable HIL.

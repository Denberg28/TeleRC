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

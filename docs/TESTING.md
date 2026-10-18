# Mission function qualification — 0.8.86

Baseline v0.8.85/code103, remote 82bfbfce36d144dd3d16134b44f175b00665a1ba; candidate code104. [A–F gates, review, restrained-wheel procedures and rollback](MISSION_FUNCTIONS_086.md). Local host/default regressions and packaging tests PASS; local ASan/UBSan PASS with leak detection disabled (container ptrace restriction). Android local build BLOCKED by unavailable toolchain/network. Publication requires cloud full JVM/debug+release lint/build, default sanitizer gates, native map/sequence/settings/persistence/help/cancel tests and all existing UI tests, signer continuity and package validation. Final successful workflow and BUILD_INFO are authoritative software results. E physical FC/actuator/phone HIL remains BLOCKED; no stable promotion.

# Replay/Waypoint qualification — 0.8.85

Baseline stable 0.8.84/code102; candidate code103. [A–F gates, wallet v3 migration/rollback and phone procedures](REPLAY_COINS_085.md). Required new JVM/native replay chance/pickup/clear/reload/migration/restart/mode checks plus full existing software/build/signing/package gates. Physical phone/FC/HIL remains BLOCKED; final successful workflow and BUILD_INFO supply software outcomes.

# Stable channel promotion — v0.8.84

Owner-authorized on 19 October 2026. Product f3eba8b / qualification run39161856095 passed A–C and software D/F. Promotion verifies immutable artifact bytes/digests, native evidence and source/tag, then stable/Latest metadata. Physical integration/installation/compatibility and E HIL remain BLOCKED/NOT TESTED. [Audit and acceptance](STABLE_084.md).

# Compact popup qualification — 0.8.84

Baseline 307eb4b/code101; candidate code102. [A–F gates and phone/HIL acceptance](POPUP_LAYOUT_084.md). Required cloud full build/JVM/debug+release lint, source protection, host/default sanitizers, native compact sizing/frame/scroll/action/transition/Market checks and retained signer/package validation. Local Android toolchain unavailable; physical phone/keyboard/rover acceptance remains BLOCKED. Final successful workflow and BUILD_INFO provide software outcomes.

# Shared popups / review 0.8.83

Baseline c7d2e0/code100; candidate code101. [Review, A–F gates, acceptance and rollback](SHARED_DIALOGS_083.md). Full cloud JVM/debug/release lint/build, source protection, host/default sanitizers and native popup/transition/authority regressions required. Hardware and actual network replacement remain BLOCKED/NOT TESTED.

# Compact tabs 0.8.82

Baseline 38b2310/code99; candidate code100. [A–F gates, acceptance and rollback](COMPACT_TABS_082.md). Local Android SDK unavailable; no local build claim. Cloud checks and native geometry/authority/actions tests must pass; physical phone/rover checks remain BLOCKED.

# ELRS recovery gates — 0.8.81

Follow [scope, A–F gates and executable restrained-wheel acceptance](ELRS_RECOVERY_081.md). Required cloud gates include the new BackpackRecoveryTest suite, existing command-guard regressions, native UDP/live/stale state checks, full JVM/debug+release lint/build, protected-source scope, host/default sanitizers, retained signer/code99, source/evidence/checksums. Local source review/diff and Python verifier syntax checked; local Android toolchain BLOCKED. Final workflow results supersede pending software gates. Actual Android Wi-Fi replacement, RF/FC stopping, phone install and HIL remain NOT TESTED/BLOCKED. No affected ESP32 targets or reflash; no stable promotion.

# F405 ELRS and clear Waypoint flow — 0.8.80 development

Setup distinguishes Mini Wi-Fi from ArduRover Wi-Fi, LoRa and ELRS TX Backpack. FC ELRS guides cover receiver/transmitter wiring and CRSF phone/handset handover. Native Backpack supports telemetry with separate send/listen ports and handset authority; phone commands are locked to avoid competing RC overrides. Waypoint uses Select → Check rover → Review & confirm, visible Undo/Clear and a fixed STOP. [Compatibility, gates and hardware checks](ELRS_WAYPOINT_080.md). Development source; hardware validation remains blocked.

# Waypoint tab — 0.8.79

An isolated Controls-style Waypoint tab adds confirmed single destinations, saved multi waypoint routes and phone-supervised ArduRover Guided sequencing with fresh navigation/parameter checks and confirmed HOLD handling. Setup/Controls/Test bodies, manual protocol, FPV and firmware stay unchanged. Existing mini/filtering gateways are planning-only: live execution requires a separately validated compatible transparent MAVLink link and FC GPS/compass/EKF. [Scope, software gates, compatibility and restrained-wheel acceptance](WAYPOINT_079.md). Physical navigation and full SITL integration remain NOT TESTED/BLOCKED; no stable promotion.

# Practice worlds and accessories — 0.8.78

Published [0.8.78/code96 test release](https://github.com/Denberg28/TeleRC/releases/tag/v0.8.78), tag/source archive `5232a82494ead22c363f7f598102985f3bc2a60a`. A–D PASS (reviewed/build verified/emulator tested): product build/JVM/debug+release lint and host/default sanitizers in run39092597154, complete API35 AOSP native suite/signing/package steps in run39102476856, and exact source-equivalence proofs. F software delivery PASS in [publishing run39103359575](https://github.com/Denberg28/TeleRC/actions/runs/39103359575): all six assets, manifest digests, tag/source/APK provenance verified. E and physical phone installation/compatibility remain BLOCKED. Firmware/protocol unchanged; no affected actual-board targets or reflash. [Scope, complete A–F evidence, installation, acceptance and v2 wallet-compatible rollback](PRACTICE_WORLDS_078.md). Next: install over the existing app, verify wallet refunds/retained claims and run the documented phone/raised-wheel acceptance. No stable promotion.

# Stable theme transitions and compact navigation — 0.8.75

Light/Sepia/Dark now change in place without Activity recreation, connection teardown or reloading the cached wallpaper. Accent changes preserve endpoint drafts; reselecting the active option does nothing. Tab menus use compact 40 dp rows, scaled for system text and aligned to their button. [Release scope, A–F gates, evidence, installation and rollback](STABLE_075.md). Owner-authorized stable channel; physical phone/Skydroid/rover validation remains BLOCKED. App-only; no reflash.

# Sharp labels, aligned tabs and wallpaper opacity — 0.8.74 test build

Replaces blurred joystick shadows with crisp text on compact caption strips, aligns menu widths/edges with tab buttons, and shares consistent gaps/top/footer sizing across all tabs. Setup's Opacity preview saves 0–100% wallpaper opacity for Controls/Test (default 70%); Setup remains clear. [Scope, gates, phone checks and rollback](LABELS_LAYOUT_074.md). App-only, no reflash. Final workflow/BUILD_INFO supplies software evidence; phone/OEM/HIL remains BLOCKED. No stable promotion.

# Clear wallpaper and collapsible Setup — 0.8.73 test build

Removes the 53% wallpaper tint on all tabs and the rectangular joystick-panel fills in Controls/Test. Setup **− / +** hides/restores both panels, keeping the existing connection/update controls in the bottom corners and preserving draft inputs/state. Practice popups are sized/centered before opening with no window animation. [Scope, gates, phone checks and rollback](WALLPAPER_SETUP_073.md). App-only, no reflash. Final workflow/BUILD_INFO supplies software results; physical phone/OEM/HIL checks remain BLOCKED. No stable promotion.

# Flat Test popups — 0.8.72 test build

Restart, Levels and Market now use small headings, flat rows, quiet dividers and short text actions. Market has Vehicles/Colors tabs, smaller previews and a compact balance; restart drops the boxed statistics and long instructions. All actions retain 48 dp touch targets. [Scope, A–F gates, phone checks and rollback](MAZE_DIALOGS_072.md). Gameplay, wallet/prices, FPV/photo and firmware retained; no reflash. Final workflow/BUILD_INFO supplies software evidence; phone/HIL remains BLOCKED. No stable promotion.

# Practice dialogs and version — 0.8.71 test build

Levels and the three-bump restart popup now use padded, rounded themed layouts, scrolling content and fixed full-width actions. Level cards show current status and best times; restart separates time/bumps and confirms coins are kept. Test's bottom text is now the TeleRC version. [Scope, A–F gates, phone checks and rollback](MAZE_DIALOGS_071.md). App-only, no reflash; Market/wallet, FPV/photo and game behavior retained. Final workflow/BUILD_INFO supplies software evidence; phone/HIL remains BLOCKED. No stable promotion.

# Maze Market and coins — 0.8.70 test build

Market replaces the Test page's OFFLINE control; its button shows the saved coin balance. Collect randomly placed gold coins to unlock cosmetic vehicles and maze palettes. Fixed lifetime quotas total 220 coins against 180 for all paid items; retry/reload cannot duplicate rewards. Opening Market pauses practice. [Use, prices, A–F gates, phone acceptance and rollback](MAZE_MARKET_070.md). App-only, no reflash. Final workflow/BUILD_INFO supplies software results; phone/HIL remains BLOCKED. No stable promotion.

# Persistent wall-bump dots — 0.8.69 test build

Counted maze impacts leave opaque red dots with white outlines on the struck wall. They do not fade; pause/resume and navigation retain them for the current attempt. Restart/level changes clear them. [Scope, A–F evidence, phone checks and rollback](MAZE_BUMPS_069.md). Android-only; fixed levels, FPV/photo and firmware retained. Final workflow/BUILD_INFO supplies software results; phone/HIL remains BLOCKED. No stable promotion.

# Fixed maze levels — 0.8.68 test build

Twenty repeatable levels replace random shuffle. The single Lv/time header and taller courses expand the maze; END is the farthest passage from START. Three separate wall bumps stop the run and prompt a restart. Saved unlocks, replay and level times preserve legacy records. [Use, A–F gates, phone acceptance and rollback](MAZE_LEVELS_068.md). Android-only; FPV/photo features retained, no firmware reflash. Final workflow/BUILD_INFO supplies software evidence; phone/rover HIL remains BLOCKED. No stable promotion.

# FPV recovery and photo theme — 0.8.67 test build

Hold Connect FPV for advertised video formats (including YUYV compatibility) and diagnostics. Bounded USB recovery, explicit permission gating, single preview ownership and background resume preserve disabled control. Setup → Background → Choose image adds a private offline photo; Remove image restores the plain tone. See [use, gates, acceptance and rollback](FPV_THEME_067.md). Physical Skydroid/video compatibility remains BLOCKED; source and software checks do not establish clean RF/video. Firmware unchanged; no reflash.

# Skydroid USB FPV fix — 0.8.66 test build

USB video attachment registration now allows Android to offer TeleRC as a default handler; cold/warm USB launches open FPV with control disabled. Hold Connect FPV for receiver VID/PID, interfaces and permissions. No USB enumeration and connected non-UVC devices have distinct messages. See [setup, evidence, phone checks and rollback](SKYDROID_FPV_066.md). Firmware unchanged. Phone/Skydroid HIL remains BLOCKED; this is a review/test artifact, no stable promotion.

# Readable maze rover 0.8.65

Baseline stable 0.8.64/code82; candidate 0.8.65/code83. [Scope, A–F gates, acceptance and rollback](READABLE_ROVER_065.md). Thin wall drawing and collision share rectangles; the larger oriented rover uses the earlier top-view design. Old records remain Classic; new geometry has separate best times. Required cloud Android/native/host/sanitizer/parser/signing/package checks remain pending until the final workflow succeeds. Phone readability and physical HIL remain BLOCKED. App-only: firmware unchanged, no affected ESP32 target. Latest stable 0.8.64 stays retained. Next check: phone upgrade and four-direction/reverse/wall-contact practice in all backgrounds.

# v0.8.64 stable-channel promotion

The owner explicitly requested Latest stable publication on 16 October 2026. [Promotion record and unchanged-build provenance](STABLE_064.md). This version-specific instruction overrides the default candidate-only channel restriction; it does not turn absent physical evidence into PASS. The scoped promotion workflow verifies the exact release/source/tag, all pinned asset hashes/CRC, original successful qualification, native UI evidence, and unchanged inventory before/after selecting Latest. A/B/C, software D and package F passed run 38844598235. Physical D, installation and E/HIL remain NOT TESTED/BLOCKED. The next check remains the guide's restrained-wheel/phone acceptance sequence. No app/firmware code or signing identity changes during promotion.

# Navy / offline maze / 95% mini candidate

Baseline app 0.8.62/code80 `bc7e983b88f2deea28aaf7c81e5ecffc159da949`; candidate 0.8.64/code82 + standalone mini 1.2.3. Scope, acceptance, installation and rollback: [NAVY_MAZE_064.md](NAVY_MAZE_064.md).

A source/build: reviewed; local Android/actual ESP32 toolchains BLOCKED; cloud Android and six pinned ESP32-S3 builds REQUIRED. B behavior: maze generator/collision/timing/record and 95% PWM regressions plus native three-page lifecycle/theme checks REQUIRED. C regression: local host/profile/sync PASS; final cloud default sanitizers REQUIRED. D software integration: Android packets through mini parser REQUIRED; actual transport/driver integration NOT TESTED. E physical HIL/phone installation: BLOCKED; use the executable restrained-wheel sequence in the guide. F delivery: retained signer, code82, matching source/sketch/binaries/evidence/checksums and prerelease REQUIRED. A successful final workflow and BUILD_INFO supersede pending software statuses only. No physical/field/stable claim.

Test disables auto-connect and disconnects live authority; returning to Controls needs explicit Connect. Practice motion/time is approximate and active-time based. The firmware 242/255 ceiling multiplies app sensitivity: Drive100 gives OUT242; Drive95 gives OUT229. Other driver profiles retain their old limits. Physical stop/current/supply validation remains pending. Next check: install on phone, verify offline isolation with raised wheels, and measure PWM/stop/recovery.

# 0.8.62 color-only restoration

Baseline: released 0.8.61/code79 `0428b6f08b895e53aeefc9c660a7fa1f4a54b900`. Color reference: pre-compact 0.8.58. Scope: original palette with slightly deeper shades, former Pastel-to-Light migration, no layout/control/firmware rollback. See CLASSIC_COLORS_062.md.

| Gate | Before final cloud run | Required evidence |
| --- | --- | --- |
| A source/build | Reviewed; local Gradle/SDK BLOCKED | Cloud JVM/debug/release lint/build |
| B appearance | Contrast calculation PASS; source layout diff reviewed | 12 native Setup/Controls combinations, migration/restart, symmetry and large-font capture |
| C regression | Local host and sync PASS; final sanitizers pending | Cloud default ASan/UBSan and host suites; local leak inspection restricted |
| D integration | Protocol unchanged; physical NOT TESTED | Kotlin frames into actual mini parser |
| E HIL | BLOCKED | Phone/receiver/rover unavailable; executable procedure in CLASSIC_COLORS_062.md |
| F delivery | Pending | Code80 > code79, same signer, native/ZIP alignment, source/evidence/checksums; prerelease only |

The successful final workflow and BUILD_INFO supersede pending software results. Next check: install over 0.8.61, compare original stronger colors, confirm current layout and retained settings, then perform restrained-wheel acceptance. No stable promotion.

# 0.8.61 pastel theme qualification

Baseline: released 0.8.60/code78 `c3a7aff56912eed000066ed8f3a7f8cb80f31f7f`. Scope: pastel appearance and one-time Dark migration; no firmware/protocol changes. See PASTEL_THEME_061.md for acceptance, installation and rollback.

| Gate | Before final workflow | Required result |
| --- | --- | --- |
| A source/build | Reviewed; local Android BLOCKED (Gradle network/SDK) | Cloud JVM/debug/release lint/build |
| B behavior/UI | Migration source reviewed; native NOT TESTED | JVM migration tests; attempt 16 Setup/Controls combinations, migration/persistence, geometry and brightness; actual app/assertion failure blocks release |
| C regression | Local sync and legacy/mini host PASS; ASan/UBSan PASS with leak inspection disabled; default leak check BLOCKED locally | Cloud default sanitizer and full host regression |
| D integration | Protocol unchanged; physical NOT TESTED | Kotlin-to-actual-mini-parser check; phone/receiver/rover validation pending |
| E HIL | BLOCKED | Raised-wheel procedure in PASTEL_THEME_061.md and full procedure below |
| F delivery | Pending | Code79 > code78; retained signing identity, native/ZIP alignment, source/evidence/checksums; prerelease only |

The final workflow's BUILD_INFO and evidence supersede pending software statuses only after success. Physical acceptance remains pending. Next check: install over the screenshot's 0.8.60 without clearing data and verify it opens in Pastel, then check Setup/Controls and the selected theme after restart.

# 0.8.60 verification

Baseline: released 0.8.59/code77 commit `450fdb86331ffad27c8d8b2720abfe3702fa4dfc`. The user supplied actual phone screenshots showing excessive saturation and stretched Setup cards. Scope: tonal palette, symmetric content-sized Setup and UI evidence. Firmware/protocol/control behavior is unchanged; no actual-target firmware compilation is required for this app-only update.

A source/build: cloud unit/debug/release lint/build REQUIRED. B behavior/UI: attempt 12 API 35 native variants, equal panel dimensions, visible actions and large-font capture. Actual app/capture/assertion failures block release; unavailable emulator startup is BLOCKED with no visual pass claim. Read ui-evidence/STATUS.txt and BUILD_INFO for the actual outcome. C regression: host/default ASan/UBSan REQUIRED. D integration: Android-to-mini parser REQUIRED; physical link/USB NOT TESTED. E HIL: BLOCKED, no physical hardware. F delivery: signer continuity with 0.8.59, code78, native/ZIP alignment, release APK/source/evidence/checksums REQUIRED. Successful preceding workflow steps and BUILD_INFO supersede pending software gates; no stable readiness claim. Local Android build remains BLOCKED by SDK/network. Native emulator evidence is not actual-phone visual acceptance. See MUTED_SETUP_060.md for executable raised-wheel acceptance and rollback.

# 0.8.59 verification

Baseline 0.8.58/code76 commit `2228f1ea8315ef25311965230f693eebdc19a6ba`, successful workflow 38735282670. Only Android UI, theme migration and release packaging change; firmware and wire protocols are unchanged. Acceptance and rollback: COMPACT_UI_059.md.

Gates A (Android compile/lint/unit), B (theme migration regression), C (host/sanitizer regression), D (Android-to-mini parser integration), and F (signed package/version/alignment/checksums) must pass on the new commit in the compact UI release workflow. E (physical HIL), phone installation and visual/USB device QA are BLOCKED by absent hardware. Local Android build is BLOCKED by missing SDK/network; no local Android pass is claimed. Stable promotion is not authorized by software gates alone.

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


## USB FPV / larger controls 0.8.58

Authoritative baseline: released `v0.8.57`, commit `47cdf3e88f3f83fc9877890fdd0eec7a2befb052`, code75. The main branch still held 0.8.53; a prior local 0.8.54 build used that older baseline and is superseded. This update uses code76, retains all 0.8.57 ControlResponse/MiniRover behavior, and changes no firmware. Setup, acceptance and rollback are in USB_FPV.md.

| Gate | Local status / evidence | GitHub release gate |
| --- | --- | --- |
| A source/build | PASS: pinned Gradle 8.11.1/JDK17/SDK35; full debug/release compile/build and lint. Zero lint errors, 62 warnings. No affected ESP32 target. | Same builds/lint rerun on release source |
| B behavior | PASS: 72 JVM tests, zero failures/errors; includes existing sensitivity/expo/migration/MiniRover arm tests and new preview geometry/frame-age tests | All tests and Android-shaped frames through actual mini parser |
| C regression | PASS: legacy/mini host regression, twenty driver/count/API profiles, ASan/UBSan with local leak inspection disabled. Default LeakSanitizer BLOCKED by runtime /proc task restriction | Default leak-enabled sanitizers must pass before publishing |
| D integration | Software protocol checks; actual USB camera/phone/hub path BLOCKED | No hardware claim |
| E HIL/device visual QA | BLOCKED: no attached phone, receiver or rover. Run USB_FPV.md plus CONTROL_RESPONSE_057.md raised-wheel checks | No stable promotion |
| F packaging | Local APK code76, retained keystore signature, permission policy and ZIP alignment PASS | Compare signer/package/increasing version directly with released v0.8.57; verify source/evidence archives/checksums; publish prerelease only |

GitHub workflow `full-ui-wifi-arm.yml` is restricted to `work/fpv-ui-058-*` and publishes v0.8.58 only after its required checks. Actual run/result and source commit are carried in the release BUILD_INFO and Actions evidence. The separate sensitivity/expo files and MiniRover transport/arm logic are retained from 0.8.57; firmware reflash is unnecessary. Next physical check is upgrade from the installed 0.8.57 app, confirm saved independent sliders/expo, connect the actual UVC receiver, then run permission/swap/disconnect/background and raised-wheel control checks.

# Expanded console — 0.8.76

See [scope, acceptance, A–F gates, installation and rollback](CONSOLE_076.md). Baseline c9d3079/code93; candidate code94. Cloud results supersede pending software gates only. Actual phone/rover HIL remains BLOCKED.

0.8.76 final software result: tested source `cfa044896a2f8ca40dffb78366d635a38b3722b5`; run [39083855942](https://github.com/Denberg28/TeleRC/actions/runs/39083855942). Build/JVM/debug+release lint/native geometry/theme/maze/bridge sanitizers/signing/package checks PASS. Workflow overall FAILURE is artifact retention only: account Actions storage quota exhausted. No retained APK download. Physical HIL BLOCKED. See CONSOLE_076.md.

# Persistent USB FPV — 0.8.77

See [scope, gates, Android default/permission limits, phone acceptance and rollback](FPV_PERSISTENT_077.md). Baseline b193dda/code94; candidate code95. Required checks are rerun on the modified sources. Actual UVC detach/replug/default chooser/HIL remain BLOCKED; emulator no-device lifecycle checks do not establish physical reliability.


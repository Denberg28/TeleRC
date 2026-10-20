# TeleRC 0.8.87 / ESP32 balancer 0.3.0 — guided pre-test

New Balance tab before Guide in the upper-right Setup header; also available in
the tab menu. This is a paired app + balancer firmware update. Existing rover,
Waypoint, game/wallet and FPV behavior are retained.

Connect phone to TeleRC-Balancer Wi-Fi (default telerc123), choose Mini rover
Wi-Fi in Setup, address 192.168.4.1 and port14550, and Connect. Open Balance.
Wait for firmware status; older or unsupported firmware keeps actions locked.

1. Calibration ready / REUSED needs no full calibration. To recalibrate, disconnect
motor power, support upright/still, tap Calibrate & save and confirm. The rover
erases the offset, restarts and performs a fresh calibration. Link loss/recovery
never arms. The saved-offset format remains compatible with0.2.7/0.2.8.
2. With regulated motor power checked, secure chassis and suspend both wheels.
Tick the wheels-suspended confirmation, test Left, Right, then Both. Each pulse
is55/255 for150ms. Firmware completion is not physical movement feedback.
3. Check both wheels propel forward, forward/back tilt gives positive/negative
angle, and live angle/rate are upright/still. Tap Confirm directions and confirm
your observations. Both-motor test completion is required; single tests do not
qualify. Default direction confirmation remains per boot.
4. Open Controls, enable Phone control, center sticks and use normal ARM while
supported upright. The pre-test page never arms. Support before Stop/Disarm.

Versioned setup datagrams are accepted only from the current paired Wi-Fi peer.
Boot/session IDs and wrapping status sequences reject reordered replies within the
current boot. A changed boot ID clears local wheel/pending confirmations.
One-use random challenges expire after1.5s; app send queue expires after500ms and
controller request mailbox expires after500ms. Requests are not retried/replayed.
Readiness is always rechecked on the robot. Queries do not refresh RC authority.
Stop invalidates challenge/motion and wins over pending test requests. Tab leave
and app pause cancel queued actions and request stop for active/pending tests;
any accepted test also ends independently after150ms. Calibration uses an
acknowledged250ms deferred reset with motor output off. Runtime/STOP faults
cannot be cleared by a setup calibration request.

WPA2/paired endpoint is existing link protection, not cryptographic device
authentication. A challenge limits stale accidental replay but does not make an
untrusted Wi-Fi network safe. No motor current, voltage, encoder or physical
direction sensor is added. Sensor initialization, fault latching, output bounds,
physical stop and explicit arm gates remain required.

Baseline Android0b94da3 (0.8.86/code104); new0.8.87/code105. Firmware baseline0.2.8,
new0.3.0. Reviewed and host-tested locally; local Android SDK/Gradle and actual
ESP32 toolchain unavailable. Required cloud workflow checks signed APK/lint/JVM,
actual ESP32-S3 compilation, native three-theme/large-text layout and existing UI
regressions. Check its final status before using the build. Physical HIL remains
BLOCKED. No stable/public release is created by the review workflow.

Restrained hardware acceptance: check motor power/ground; upload exact firmware
folder with motor power off and correct board options; confirm saved calibration
reuse; verify all app statuses and old-firmware lock; test left/right/both with
wheels clear, stop/leave/background during test, expiry/reconnect without replay,
wrong direction refusing manual confirmation, calibration restart, persisted bias
after power-cycle, runtime/STOP fault rejection and normal Controls arm/disarm.
Record actual motion, timing, power and board/app versions. No physical balance
or radio reliability is claimed from software tests.

Install signed APK over existing app without clearing data. Flash only the
balancer with its exact pins/settings; other rover firmware does not need update.
Rollback requires a higher-code rebuild retaining the current wallet parser;
retain0.2.8 for firmware rollback (Serial setup only). Never clear app data or
restore an old armed/control state to downgrade.

## Current review/build status

Local review branch: codex/balance-pretest-087. GitHub push was rejected by
automatic approval review: publishing app/firmware source to the public repository
requires explicit authorization. No push, CI build, APK, public release, tag or
stable promotion was completed. The workflow is ready to build/sign/check on
that branch after authorization and does not create a public release.

Local PASS: complete existing bridge regression and address/undefined sanitizer
suite, mini Wi-Fi normal/sanitized tests and20 profile combinations, motor-core
synchronization, source diff checks, actual balancer controller/session tests.
Balancer assertions:41 default direction lock,671 controller/fault,76 remote
admin/controller,72 Serial,82 saved calibration,80 protocol plus20,000 malformed
packets,33 production network-adapter checks, mount integration and host syntax.
Kotlin JVM tests, Android debug/release lint/build, native UI/emulator checks and
ESP32 board compilation are BLOCKED locally (missing SDK/toolchains and Gradle
network unavailable). Tests/source added here do not constitute those results.

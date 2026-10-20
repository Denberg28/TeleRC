# Balance pre-test final software qualification

TeleRC Balance Pre-test — software-qualified test candidate
Android:0.8.87 / versionCode105
Matching standalone balancer firmware:0.3.0
Source commit:359d10ec0facd6501119f13bd18ed8ab82776b44
Cloud workflow:https://github.com/Denberg28/TeleRC/actions/runs/39419210517

PASS: signed debug/release compilation; debug/release lint; retained release
certificate and increasing version; Android native harness compilation.
PASS: 159 JVM tests, 0 failures, 0 errors, 0 skipped.
PASS: native API35 Balance-before-Guide header placement, Light/Sepia/Dark,
normal/large text, offline action locks and lifecycle; existing UI regression.
PASS: actual ESP32-S3 compilation with Espressif core3.3.2, CDC native USB,
generic4MB partition target; firmware934195 bytes flash,47288 bytes static RAM.
PASS: existing bridge/mini/protocol/profile and balancer host/sanitizer suites.
PASS: artifact digests, ZIP CRCs, per-file checksums and exact source tree match.

Physical Android installation/USB/Wi-Fi/motor power/motion/direction/IMU/HIL and
balance/PID tuning:NOT TESTED. No stable or public release was published.

APK SHA256:20f4b08041b06883cb0382b566ed8f7aaecc676c3c9ea4e67604f457b700a2e2
Firmware pins/PWM/PID/gyro persistence format remain as documented.
This final result supersedes initial build-pending statements in historical
source-review notes. Executable code equals the verified source commit.

## Evidence

- Workflow: https://github.com/Denberg28/TeleRC/actions/runs/39419210517
- App build artifact digest:98fad4226f31ce55501f0e168f340f90c3f599560b91f626e5fcf2ed93d0da54
- ESP32 build artifact digest:484bb73aca23438aa762f16fa2ed5f827f81c55f78b2777811d6f5f4f25087f1
- Native/JVM evidence digest:7fdca8e66853cdf1e75d0e6b9f8b120ef4929f16d5aff85827716e2f43721d08

Review/native screenshots inspected; normal and large-text panels scroll with
fixed Refresh and Stop/Disarm footer, and offline motor/calibration actions
remain disabled. Firmware test completion reports software output, not physical
movement. Install the paired signed APK and balancer0.3.0 sketch, follow
BALANCE_PRETEST_087.md and perform restrained hardware acceptance. Earlier
source-review states in that document describe work before this completed run.

This documentation-only record does not change executable source or invalidate
the product-source checks. Default stable channel is unchanged.

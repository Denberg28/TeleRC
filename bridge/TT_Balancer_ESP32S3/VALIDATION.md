# v0.2.5 verification — 20 October 2026

Baseline: v0.2.4. Reported hardware returns I2C 0x68, WHO_AM_I 0x70.
Scope: expose calibration/sample failure details and label the selected
compatibility profile accurately. Preserve readback, identity and motor/arm gates.
Wire/PID/RC mapping remains unchanged. Chip ID identifies a profile, not
physical authenticity or proven sensor/control performance.

## Evidence gates

| Gate | Result | Evidence / limit |
| --- | --- | --- |
| A: design and source review | PASS — reviewed | TeleRC MAVLink generator, neutral handover and ARM retries checked against current source; immutable blob IDs retained in README |
| B: actual target compile | BLOCKED | No ESP32 Arduino compiler/core installed here. Host fake APIs cannot establish board-build success |
| C: deterministic simulation | PASS — host simulator tested | Actual controller, codec/session and network adapter functions exercised under platform fakes |
| D: electrical/control HIL | BLOCKED | No connected board, motors, IMU or chassis; use HIL_CHECKLIST.md |
| E: installed APK/radio/field behavior | BLOCKED | Existing APK packet format matches inspected source; real Android/Wi-Fi interoperability, delay/range, tuning and reconnect behavior unmeasured |
| F: source packaging | PASS | Required headers/docs/tests included, per-file SHA-256 manifest, archive CRC verified |

This is a test-candidate source delivery, not a build-verified, hardware-tested,
field-validated or stable release. No precompiled firmware binary is supplied.

## Automated checks run

`bash tests/run.sh` with g++ C++17, `-Wall -Wextra -Werror`, AddressSanitizer
and UndefinedBehaviorSanitizer:

```
Default arming lock: 41 assertions PASS
Control/fault integration: 649 assertions PASS
TeleRC protocol/session: 80 assertions + 20000 malformed packets PASS
Network adapter: 22 assertions PASS
Production branches: host syntax check PASS
```

The first two builds include the actual `.ino` with platform fakes and both
direction-verification settings. The adapter test compiles and calls the
production Wi-Fi adapter with fake Wi-Fi/UDP/FreeRTOS APIs; the final syntax
check includes the actual production task/Wi-Fi branches. These are not real
FreeRTOS scheduling tests or an actual Arduino preprocessor/linker/board build.
LeakSanitizer is disabled because hosted traced processes cannot enumerate
threads; address/undefined behavior checks remain enabled.

Coverage includes:

- Reported MPU6500 ID 0x70 through actual sketch startup/calibration; separate
  accelerometer-filter write/readback and +/-2g sample scaling; default direction
  gate, alternate 0x69 address, filter write/read/mismatch faults, post-reset ID
  change, and runtime bus-fault motor cutoff. MPU6050 never writes ACCEL_CONFIG2.

- Sensor address fallback 0x68 -> 0x69, configured-address preference when
  both respond, NO_ACK, failed WHO_AM_I read, unsupported identity rejection (0x00/0xFF/0x71/0x73),
  config-write/read failures, readback mismatch values, two transient boot write
  failures then success, and runtime read failure without address switching.
- Network task creation even when PWM attachment, I2C setup, MPU identity/read
  or gyro calibration fails; controller fault status stays unready and unarmed.
- AP-mode, static-IP, access-point and UDP initialization failure reporting.
- Per-boot direction confirmation: no/aborted test cannot qualify, valid t plus
  manual v confirmation remains DISARMED, reset clears confirmation, peer-owned
  verification is rejected, and runtime confirmation enables actual TeleRC arming.
- Distinct physical/controller, stale RC, off-center, direction-check and expired
  ARM denial reasons. Standard STATUSTEXT wire length/CRC and repeat throttling.
- Default arm lock, upright calibration, forward/reverse sign and inactive
  H-bridge inputs, timed wheel test, serial stop, GPIO stop latch, faults staying
  latched, failed PWM attachment/writes, malformed IMU and bad sensor identity.
- Stale/clipped sensor data, zero frames, freefall/impact/sideways and forward
  falls, non-finite attitude, sustained output saturation and timing violations.
- CRC/reference packet validation, source/target IDs, signed-flag rejection,
  MAVLink 2 zero truncation, every truncated prefix of a reference packet,
  whole-datagram rejection including malformed tails and excess frame counts.
- Fixed TeleRC neutral/ARM retry sequence compatibility, duplicate/reordered
  moving rejection, wrapping sequences/timers, release/ignore values, pending
  ARM age, retry age not extending, cooldown and disarm precedence.
- Discovery and heartbeat not extending control freshness, station loss,
  neutral recovery gate, travel cancellation while balancing continues,
  actual Core-to-mailbox-to-controller integration at 10 Hz app command rate.
- Bounded yaw within balance headroom, endpoint/port rejection, oversized
  datagrams and heartbeat/ACK/ATTITUDE output framing.

## Review conclusions and material limits

The v0.2.0 AP-after-calibration defect remains fixed. The new specific blockers
make the default direction lock distinguishable from missing/off-center control
frames. The runtime confirmation is explicit operator acknowledgement, not an
automatic physical motor-direction or axis detector. No other known critical/high software finding remains
from this scoped review, but
hardware/timing gates remain blocked. Balance stability is unproven. The MPU
is configured for 1 kHz output with DLPF setting 3 and is read at nominal 250 Hz;
actual jitter and data freshness behavior require measurements on the target.

Wi-Fi APIs and packet parsing execute in a separate core-0 task. The core-1
control task owns Wire, PWM and Serial. Shared fixed-size snapshots use short
critical sections, never a network call under the control lock. Network stalls
also expire travel locally; they do not stop an already armed balance loop.

Legacy MAVLink CRC is not authentication and lacks nonce/timestamp-based replay
protection. Firmware cannot fully determine pre-receipt radio/kernel buffering
age. The neutral/release compatibility exception accepts safety-lowering frames
outside the moving sequence guard. Sparse ignored axes cannot refresh the lease.

No encoders, velocity/travel limit, current/temperature sensing, battery cutoff
or independent hardware motor-enable watchdog is provided. A CPU hang can leave
PWM active. TT motors may exceed MX1508 stall-current ratings or lack sufficient
speed/backlash performance for balance. Verify the actual hardware before use.

## Next concrete check

Compile/upload the exact folder using Espressif core 3.x and the actual ESP32-S3
board settings, then complete the power/direction gates and supported balancing
tests. Do not promote to stable until every applicable hardware/recovery gate
passes and PID tuning is established for the physical chassis. Roll back by
flashing the original standalone v0.1.0 source with motor power disconnected.

## Protocol reference for new diagnostics

STATUSTEXT uses standard MAVLink message 253, base payload 51 bytes (severity
plus 50 characters), CRC extra 83. No nonstandard payload is placed in ACKs.
References checked against the official message schema and generated header:
https://mavlink.io/en/messages/common.html#STATUSTEXT
https://github.com/mavlink/c_library_v2/blob/master/common/mavlink_msg_statustext.h

Serial reports exact local checks. The installed TeleRC APK may retain generic
DENIED text if it does not display STATUSTEXT; no app-side UI fix is claimed.

## Sensor evidence and remaining diagnosis

The submitted log establishes centered fresh controls and an initialization
fault. It cannot distinguish power/wiring/address/chip-ID/register failure.
The new diagnostic line is needed from the physical target; no hardware cause
is claimed fixed merely because the host tests pass.

WHO_AM_I remains 0x68 at both valid MPU6050 I2C addresses. The bare IC VDD range
is 2.375–3.46 V per TDK; breakout VIN requirements depend on its regulator/layout.
References checked:
https://invensense.tdk.com/wp-content/uploads/2015/02/MPU-6000-Register-Map.pdf
https://product.tdk.com/en/search/sensor/mortion-inertial/imu/info?part_no=MPU-6050
https://learn.adafruit.com/mpu6050-6-dof-accelerometer-and-gyro/pinouts

## MPU6500 register profile references

InvenSense RM-MPU-6500A-00, revision 2.1, manufacturer register-map document
(archived mirror): https://www.ic-components.jp/files/5b/MPU-6500.pdf
Sections 4.4-4.8 and 4.22-4.24 specify divider, gyro/accelerometer filtering,
scales and output layout; section 4.38 gives WHO_AM_I=0x70.
MPU6500 uses CONFIG=3, GYRO_CONFIG=0, ACCEL_CONFIG=0, ACCEL_CONFIG2=3,
SMPLRT_DIV=0. This yields 1 kHz sensor output, ~41 Hz gyro/accel filtering;
accelerometer filter delay differs from MPU6050 and requires chassis tuning.
No tests here establish electrical authenticity, real sensor timing, Wi-Fi
scheduling or balance performance. The existing hardware gates remain required.

## v0.2.5 additional coverage

Actual calibrate/readSample functions under deterministic fakes exercise inverted
Z mounting, low norm, sideways/forward tilt, gyro movement/bias, missing samples,
status/frame read failure, all-zero/FF frames, clipped Y gyro with captured raw
counts/axis, 600-sample variance/span rejection, collection timeout, stop switch
and successful calibration snapshots. Nonfinite classification is tested directly.
The same 41/649 controller assertions, 80 protocol assertions + 20,000 malformed
packets, 22 adapter assertions and production-branch host syntax check passed.
Calibration limits and sensor ranges remain unchanged. Compatibility-profile text
is not a physical chip identification. No hardware diagnosis is yet established.

## v0.2.6 reference-informed fix validation

2026-10-20: tests/run.sh passed with address/undefined-behavior sanitizers:
- Default direction lock: 41 assertions.
- Controller/fault integration: 670 assertions, including transient movement
  settling, complete-window restart and persistent-motion rejection.
- Separate BALANCER_IMU_MOUNT=1 actual sample/control test: angle/rate signs,
  both motor directions, sideways rejection and bus-fault propagation passed.
- Protocol/session: 80 assertions and 20,000 malformed packets.
- Network adapter: 22 assertions.
- Production task/Wi-Fi branches: host syntax check passed.

The original v0.2.5 suite was also run before edits and passed (41/649, 80 plus
20,000 malformed packets, 22 and production syntax). Existing configuration,
protocol and fault checks remain covered. No ESP32 build toolchain is installed,
so an Arduino-ESP32 target build and all physical HIL checks remain outstanding.
The exact physical calibration cause cannot be determined from the older
calibration-only fault string; obtain CAL/SAMPLE diagnostics if it persists.

## v0.2.7 saved-calibration validation

Host test suite passed using C++17, -Wall/-Wextra/-Werror and address/undefined
behavior sanitizers: default direction lock 41 assertions; controller/fault
integration 671 assertions; saved calibration 82 assertions; reference mounting,
80 protocol assertions plus 20,000 malformed packets, 22 adapter assertions,
and production-branch host syntax checks. No ESP32 target toolchain is available.

Saved-calibration tests exercise real sketch setup/check/command functions with
a deterministic NVS fake persisting across simulated resets: initial save, repeated
reuse without writes, new gravity angle (including upright-arm rejection),
checksum/size/schema/context/NaN/out-of-range rejection, sensor/address changes,
stable bias drift and full recalibration, moving/bus/clipped/stale/STOP/variance/
span rejection, transient settling, incomplete short/full window diagnostics,
failed storage open/write/erase, serial stop precedence, c gating in paired/
arming/balancing/test/runtime/STOP states, and calibration-fault software reset.

These are not physical NVS power-cut/wear tests, a target compilation, scheduler
measurements, or successful balance tests. Hardware power-cycle persistence and
the checklist remain pending. No precompiled binary is included.

## v0.2.8 Serial input/motor-test validation

Host suite passed with existing C++17 warning/error and address/undefined-behavior
sanitizer settings: 41 default-lock, 671 controller/fault, 72 Serial/diagnostic,
82 saved-calibration assertions; mount test, 80 protocol assertions plus 20,000
malformed packets, 22 adapter assertions and production-branch host syntax check.

New tests call actual sketch parsing/control paths and capture bounded emitted
telemetry under fake Serial: harmless ? query and expected output fields,
uppercase and leading CR/LF/space/tab, separate t/v/a actions, timed test completion,
stop precedence, whitespace-only/unknown input, peer ownership, arming/test/fault
blocks, startup discard with resend, 32-byte processing bound, explicit
PWM-write test-abort diagnostic, and uppercase c reset. Baseline controller and
storage regressions remain passing. The unchanged v0.2.7 storage format is
compatible. The Serial stub cannot prove native USB, UART, host-monitor behavior,
real NVS persistence, motor power/wiring or physical movement. Actual ESP32
compilation and HIL remain unperformed. This is a source test candidate.

## v0.3.0 remote pre-test source verification

Local host PASS:41 default lock;671 control/fault;76 new remote admin/controller;
72 Serial;82 saved calibration;80 protocol plus20,000 malformed packets;33
production network adapter; reference mounting; production-branch host syntax.
New tests cover one-use challenges, expiry/wrap, malformed actions, actual
left/right/both outputs, full-test direction requirement, stale request, stop
priority, active/fault-state rejection, output-write fault, recalibration erase
and deferred restart. Adapter tests exercise peer/port ownership, real reply
format, no query-to-RC freshness, bounded pending mailbox, duplicate rejection
and stale-controller refusal. Arduino-ESP32 target compilation remains BLOCKED,
as do all physical NVS/Wi-Fi/power/driver/robot tests.

# Hybrid controller review — 2026-10-03

Reviewed baseline: e7e8a386fb4ed7393509f873d0d0bb5be6fed0fa.
Scope: entire hybrid sketch, wiring/behavior documentation, Android MAVLink/lifecycle code, and current PC TeleRC MAVLink/UDP control code (9fbf053989203690195f1d0057fafcf0397a660a). No APK/PC executable changes.

## Findings and corrections

| Severity | Finding | Correction |
|---|---|---|
| High | AUTOPILOT Wi-Fi loss could leave stale RC override driving while FC PWM remained fresh | Track current apps' owned override channels; stop at 500 ms staleness and refresh neutral at 10 Hz |
| High | DIRECT partial channel updates could keep old throttle live | Separate CH1/CH2 deadlines; both must remain fresh |
| High | UART writes could block behind telemetry/control bursts | 1024-byte TX buffer; capacity check before complete writes; congestion stops/latches motor authority |
| High | Oversized UDP could route a truncated valid prefix | Reject datagrams larger than 280 bytes or with an incomplete read |
| High | DIRECT ARM could succeed without fresh neutral controls | Require fresh neutral CH1 and CH2; release of either disarms and zeros motor PWM |
| High | Explicit AUTOPILOT disconnect could recover automatically at neutral | Latch motors off; selector change required for AUTOPILOT recovery |
| Medium | Selector debounce allowed the old source to keep driving for 50 ms | Stop on first observed selector edge, then debounce source grant |
| Medium | ISR values and freshness timestamps could be read inconsistently | Critical-section snapshot; ISR rise tracking; invalid widths invalidate channel |
| Medium | LEDC attachment failure was ignored | Disable authority unless all PWM attachments succeed |
| Medium | New peer heartbeat could inherit old controller state | Expired-peer heartbeat handover now enters FAILSAFE |
| Medium | Diagnostic printing could block while motors run | Format into a bounded buffer and write only when Serial has space |
| Low | Documentation incorrectly described FC telemetry and universal recovery behavior | Distinguish local DIRECT state, forwarded FC state, and neutral/latch recovery |

## Feature and interoperability audit

| Feature | Result and boundary |
|---|---|
| DIRECT standalone rover | Preserved, one ESP32-S3, four BTS7960; no FC required |
| AUTOPILOT motor conversion | Four independent M5–M8 inputs, or configurable paired left/right; output assignments must be verified on the FC |
| FAILSAFE | Immediate commanded PWM zero; does not electrically isolate motor power or guarantee instant physical stopping |
| GPIO16 authority switch | LOW DIRECT / HIGH AUTOPILOT; 3.3 V input; 50 ms debounce and 300 ms neutral dwell |
| Android compatibility | v1 sysid 255/component 190; CH1 steering/CH2 drive; neutral-before-arm; status, heartbeat and ACK formats retained |
| PC compatibility | Same v1 identity and sparse override/release; use CH1/CH2, UDP local/target port 14550; Enable at neutral before DIRECT ARM |
| UART bridge | GPIO18 RX/GPIO17 TX, 115200 baud; generic complete MAVLink v1/v2 datagrams; only AUTOPILOT selection forwards controller commands |
| Heartbeat identity | ESP vehicle 1/component 1 in DIRECT; FC identity in AUTOPILOT, without competing ESP heartbeat |
| AUTOPILOT override timeout | Current app v1 format only; GCS heartbeat/discovery cannot extend motor-command freshness |
| Physical receiver takeover | Explicit FC override release clears tracked ownership; FC controls RC6/receiver arbitration |
| Autonomous navigation | FC responsibility; no waypoint/GPS/EKF implementation added to ESP |
| ELRS serial PC transport | Does not connect directly to this Wi-Fi sketch; requires the corresponding receiver/FC path |
| Diagnostics | Existing TELERC_STATUS_V1 retained; mode and motor state available on serial, not yet exposed by app diagnostics |
| Configuration identity | DIRECT target system/component 1/1; FC neutral-watchdog frames also target 1/1. Configure FC identity to match |

## Validation completed

Actual sketch compiled and executed against host peripheral stubs with C++17, -Wall -Wextra -Werror, for both ESP_ARDUINO_VERSION_MAJOR=2 and =3. Checks passed for:

- checksum-valid and checksum-invalid DIRECT RC frames;
- arm rejection without fresh neutral inputs;
- partial channel freshness and stale throttle stop;
- release/disarm and immediate motor zero;
- oversized UDP rejection;
- AUTOPILOT neutral watchdog frame encoding;
- UART backpressure latch;
- PWM signal loss, orphan edge and invalid width;
- failed PWM attachment;
- selector-edge stop and reversal through zero;
- explicit disconnect latch;
- MAVLink v2 structural incompatible-flag rejection;
- millisecond counter rollover.

`git diff --check` passed. The ESP32 GitHub workflow now compiles bridge, direct and hybrid sketches and runs both host regression branches.

Attempted required Android checks: `./gradlew :app:assembleDebug :app:testDebugUnitTest`. They could not start because the environment cannot download Gradle from services.gradle.org (Network is unreachable). This is not a passing Android build/test result. No Arduino CLI or ESP32 board package is installed locally; a genuine target build must be verified through CI.

## Remaining limits and required bench evidence

IP/port leases, source IDs and checksums do not authenticate a controller. Generic routed MAVLink uses structural checks; ArduPilot validates message CRC/signatures. No local signature verification, replay protection, or universal MAVLink v2 override watchdog exists. Use the current Android/PC v1 protocol for control.

AUTOPILOT disarmed PWM must be neutral (1500 us), or absent with motor stop. FC RC6 arbitration, RC override expiry, receiver failsafe and navigation failsafes remain FC responsibilities. When TeleRC owns no overrides, Wi-Fi loss does not stop a receiver-driven or autonomous rover solely because telemetry disappeared. Explicit disconnect does stop/latch the hybrid motor path.

A CPU hang, brownout, peripheral fault or reset can defeat software stop timing. External RPWM/LPWM pulldowns and independent motor power cutoff are necessary. Zero PWM is not proof of zero torque, braking performance, or physical stopping distance. 3.3 V logic acceptance depends on the actual BTS module input circuit; verify or buffer it.

Before ground use, raise wheels and measure: 500 ms DIRECT/override timeout, 150 ms PWM timeout plus loop latency, immediate selector stop, each required PWM wire removal, Wi-Fi loss with one and multiple associated clients, tab/background neutral, sparse release/RC6 takeover, explicit disconnect latch, UART congestion, neutral-only recovery, all wheel directions, reset/brownout behavior, and motor duty/ramp timing under maximum telemetry. Confirm actual F405 SERVO5–8 functions, PWM protocol (not DShot), endpoint calibration and identity. SITL and physical bench validation have not been performed here.

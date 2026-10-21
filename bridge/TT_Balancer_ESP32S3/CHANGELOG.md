# v0.3.2 — optional direction-confirmation gate (development candidate)

- `BALANCER_REQUIRE_DIRECTION_CONFIRMATION=0` by default: optional `t`/`v` bench commissioning, not an arming prerequisite.
- `verified=0` remains truthful until an actual confirmation; skipping the gate never marks the motors verified.
- `=1` restores v0.3.1's strict T-then-V arming lock.
- Explicit arming, sensor freshness/calibration, upright/still, hardware STOP, PWM faults, link freshness, centered controls, motor output bounds, and fault latching remain unchanged.
- Physical wheel directions, sensor sign, actual balance stability and safe operation are NOT established by these edits. Test with a tether and accessible kill switch; the observed -8.8 degree standing offset remains a separate blocker.

# v0.3.1 — 21 October 2026 — motor-test output and workflow

- Increase explicit left/right/both tests from 55/255 ×150ms to 140/255 ×300ms.
  Use TEST_MS consistently in firmware and diagnostics; compile-time 100..500ms
  cap. Balance PID, min/max duty, motor signs, calibration and fault gates retained.
- Use explicit LEDC channels 0..3 with inactive-leg-first output, as in MiniRover.
- Retain signed last-test commands/duty-register readback after timed zero.
- Append bounded output evidence to setup status; requires paired TeleRC0.8.90.
- App accepts original reports, shows test replies/output evidence, clarifies
  calibration completion and power checks, requires explicit observed direction.
- Add regressions for output mask/polarity, >150ms duration, timed zero, STOP,
  fault and rollover, new/old parser bounds and continued stale-session rejection.
- No physical torque, power, UI-phone or balancing validation is claimed.

# v0.3.0 — TeleRC app pre-test

- Add paired-peer-only versioned Wi-Fi setup query/action/stop with random one-use challenge,1.5s challenge lifetime,500ms controller request age and no command replay.
- Publish bounded copied calibration/arm/test/status snapshots outside the motor loop; boot ID plus status sequence supports ordered client status.
- Add left/right/both150ms tests at existing55/255; only Both qualifies for explicit direction confirmation.
- Recheck calibrated/disarmed/fresh/STOP state locally; calibration erase uses motor-off250ms deferred reset. Runtime/STOP faults stay latched.
- Preserve ordinary MAVLink control and saved gyro record format. Add actual-controller and production-adapter regression tests. Target compilation/HIL remain pending.

# v0.2.8 — Serial command acceptance and motor-test diagnosis

- Accept uppercase/lowercase commands and skip leading whitespace, CR/LF and tabs. Execute at most one action per bounded input batch; x remains highest priority.
- Add harmless ? query, processed-byte/boot-discard counters, raw/normalized command bytes and explicit accept/reject/test-completion results.
- Emit event-requested and periodic SERIAL diagnostics through the existing nonblocking output path; distinguish Serial RX from Wi-Fi RC rx.
- Keep calibration storage format compatible, retain test duty/duration, direction lock, explicit arming and motor/IMU fault gates.
- Add actual-sketch parser/diagnostic tests, including output text, uppercase/whitespace, stop precedence, peer/state/fault rejection, startup discard, bounded processing and PWM-write test abort.
- The reported physical cause remains unconfirmed until receive/test evidence is collected. No ESP32 target build or physical motor test was performed here.

# v0.2.7 — saved gyro calibration and startup diagnostics

- Save a successful full pitch-gyro calibration as a versioned, checksummed NVS record tied to sensor ID/address, I2C pins and mounting.
- Reuse it after a fresh 150-sample (~0.6-second) stillness/sensor check. Initialize gravity angle afresh; never restore armed state or direction confirmation.
- Perform full calibration if record is missing/invalid/incompatible, or a stable residual bias exceeds 1 degree/s. Other saved-check failures preserve the old record and inhibit motors.
- Add explicit Serial c: erase and restart only when disarmed/unpaired or from a calibration-only boot fault. Reject active control, runtime faults and STOP; x keeps precedence.
- Expand full-calibration settling budget to 15 seconds while preserving the complete 600-sample window and limits. Separate lastReject from current timeout stage.
- Report CAL store status; flash write errors retain valid live calibration without falsely claiming persistence.
- Add NVS reuse/corruption/context/drift/failure, command-gating, partial-window timeout, and motor-off regressions. ESP32 target build and HIL remain pending.

# v0.2.6 — reference-informed startup and mounting fix

- Reviewed arthurlawson/self-balancing-robot-v2 at 8ef439bde72eeed8028adbdb70472c1b5e2d1db4.
- Allow valid tilted/moving startup samples to settle inside the original 5-second calibration budget. Discard the whole partial window on each rejection; require 600 new accepted samples.
- Add an opt-in +Y-forward sensor mounting with paired accelerometer/gyro transformation, matching the reference X-gyro/roll convention. Default pins and mounting remain unchanged.
- Keep sensor-ID/profile checks, direction confirmation, explicit arm, runtime cutoffs, TeleRC protocol, PID gains and sensor filter settings.
- Add transient/persistent calibration-motion regressions and a separate mount=1 sample/controller integration test.
- No physical-chip identity, ESP32 binary build, or successful bench balance is claimed.

# v0.2.5 — 20 October 2026 — calibration and raw-sample diagnostics

- Report sensor identity as a selected compatibility profile. The user's board
  is labeled MPU6050 while WHO_AM_I reports 0x70; physical silicon is unverified.
- Split generic calibration rejection into specific stop, timing, orientation,
  magnitude, gyro movement/bias, variance/span and nonfinite reasons.
- Preserve the last calibration sample/stats and failing raw sample/axis.
- Split generic read/clipping failures into bus, zero/FF and clipped-axis causes.
- Rotate bounded CAL/SAMPLE/MPU Serial records; no blocking diagnostic reads,
  no post-fault sensor reinitialization and no change to calibration/PID limits.
- Host integration and malformed-packet tests pass; target build/HIL remain pending.

# v0.2.4 — 20 October 2026 — MPU6500 support

- Resolve the reported who68=0x70 startup rejection with an explicit MPU6500
  profile alongside the existing MPU6050 profile. Unknown IDs remain blocked.
- Verify identity again after reset; changes/read failures remain latched faults.
- Configure/read back MPU6500 ACCEL_CONFIG2=3 (41 Hz, 1 kHz samples); leave that
  register untouched on MPU6050. Retain +/-2g and +/-250dps scale settings.
- Add model/ID to bounded Serial diagnostics. Keep 250 Hz control, default
  direction verification, calibration and all runtime fault/arming guards.
- Add MPU6500 boot/calibration, alternate address, filter write/read/mismatch,
  reset identity change, direction lock, and runtime bus-fault regressions.
- Actual ESP32 compilation, hardware balance and PID tuning remain pending.

# v0.2.3 — 20 October 2026 — MPU initialization diagnosis

- Replace the generic MPU identity/config failure with specific address-ACK,
  identity-read, unsupported-ID, reset-write, config-write/read/readback stages.
- Probe 0x68 and 0x69 at boot, prefer the configured address and optionally use
  an alternate address only when WHO_AM_I confirms a supported MPU6050 (0x68).
- Report both probe ACKs/IDs, active address, bus pins/rate and the failing
  register/expected/actual/bus error in a bounded once-per-second Serial line.
- Allow power/clock settling and up to three boot-only configuration attempts.
  Runtime sample reads never retry, scan, change address or reset the IMU.
- Preserve all sensor/arming checks, fault latching, Wi-Fi and control behavior.
- Add host regressions for alternate-address operation, no ACK, wrong identity,
  failed identity/config reads, write/readback failures and transient boot retries.

# v0.2.2 — 20 October 2026 — arming and diagnostics optimization

- Separate physical pre-arm readiness from peer/control freshness and centering.
  Report the specific blocking condition instead of treating every denial as
  missing centered controls. Preserve explicit arm, upright hold and fault gates.
- Add received raw CH1/CH2 values, RC receive/reject counts, control age,
  direction-verification state and current/last pre-arm blockers to Serial.
- Emit standard rate-limited MAVLink STATUSTEXT reasons alongside command ACKs;
  display in the installed APK depends on its STATUSTEXT support.
- Add an explicit per-boot Serial direction confirmation: with app disconnected,
  run the timed suspended-wheel t test, visually inspect wheel/sensor signs, then
  send v. An aborted/no test cannot qualify; v never arms or persists across reset.
- Retain the compile-time verified-direction option for a checked hardware build.
- Preserve AP startup before preflight, independent local balance, watchdogs,
  neutral recovery, balance-priority steering and conservative bench PID/output limits.
- Add regressions for the new verification flow and exact denial reasons.

# v0.2.1 — 20 October 2026 — Wi-Fi startup fix

- Start the Wi-Fi task before PWM, I2C, MPU identity/config and gyro calibration
  checks. A sensor/calibration fault must not suppress the AP.
- Service control-status and bounded Serial diagnostics from the main loop if
  an early boot fault prevents creation of the dedicated balance task. Motors
  remain off and fault latching/arming requirements are preserved.
- Add explicit Wi-Fi MODE/IP/AP/UDP startup failure states to Serial output,
  with a runtime version prefix to identify the uploaded build.
- Add regressions for startup failures and each network initialization stage.
- No change to wiring, PID gains, network name/password or control mapping.

# v0.2.0 — 20 October 2026 — test candidate

- Add TeleRC Wi-Fi AP and matching UDP/MAVLink discovery, RC overrides,
  arm/disarm, heartbeat, command ACK and diagnostic ATTITUDE telemetry.
- Isolate network APIs on core 0; schedule balance on core 1 at nominal 250 Hz.
- Map CH2 to a bounded lean setpoint and CH1 to bounded differential torque.
  Balance output has priority over yaw when PWM headroom is limited.
- Cancel travel after 350 ms of stale complete controls; keep already armed
  local balancing active. Require a new 250 ms centered hold before travel resumes.
- Never auto-arm after discovery, reconnect, reboot or a latched fault.
- Handle TeleRC fixed neutral and arm retry sequence ranges separately from
  moving RC sequence validation. Validate whole datagrams before side effects.
- Reject stale pending arm snapshots, forced arming, invalid source/target,
  malformed CRC/length, unsupported signed frames and unknown messages.
- Keep fall/IMU/impact/output-limit/timing cutoffs. Use 1 kHz MPU samples read
  at 250 Hz to avoid equal-rate sensor/controller phase drift causing false gaps.
- Allow MPU wake/clock/filter settling before calibration freshness checks.
- Add deterministic protocol, mailbox and adapter regressions, malformed-packet
  fuzzing, source compatibility evidence and a restrained hardware test procedure.

No APK, GPS, mission controller, wheel encoders or physical validation added.

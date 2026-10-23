# Hardware verification — REQUIRED before free-standing operation

Current result: **BLOCKED — hardware is not attached to this environment.**
Record board/module, ESP32 core version, installed TeleRC APK version, motor
specification, driver module, wheel diameter, mass, PID values and measured
regulator voltages with each test. Keep a physical motor-power disconnect and
fall support available. Do not stall motors to measure current without a suitable
current-limited test setup; use the motor manufacturer's specifications first.

1. **Build/upload gate:** Compile the exact sketch folder on your actual
   dual-core ESP32-S3 target with Espressif core 3.x. Save build output/core and
   board settings. Verify clean flash/reset always starts DISARMED.
2. **Power gate, motors disconnected:** Confirm the motor supply is 5 V for
   assumed standard TT motors, logic supply is regulated 5 V into the correct
   DevKit input, sensor VDD/I2C pull-ups are 3.3 V, grounds are shared, and each
   driver input has a 10 kΩ pull-down. Verify USB cannot backfeed the regulators.
   Ensure both motor stall specifications fit the real per-channel driver limits.
3. **Calibration/IMU gate:** Check `MPU stage=MPU_READY`, a supported WHO_AM_I
   and the selected address. Test AD0 low/high with power removed between wiring
   changes; 0x68/0x69 selection must be correct and the sensor scales unchanged.
   An unsupported identity or failed configuration must still prevent arming.
   Capture the diagnostic line if startup fails. Verify the AP remains visible if the MPU is absent
   or calibration fails, while motors stay off and Serial/heartbeat report the
   fault. Perform wiring changes only with power removed. Then reconnect the MPU,
   hold upright/still at boot and reset. Verify failed/tilted
   calibration does not run motors. Check angle/rate signs, all three IMU axes
   and mounting. Native USB absence/reconnect must not block control.
4. **Suspended-wheel gate:** Leave verification flag at zero. Confirm TeleRC
   ARM is denied. With app disconnected, run Serial `t`; verify both wheels go
   forward and stop after 300 ms. Reverse one sign/lead only with power removed.
   After the checks pass, send Serial v and confirm verified=1 while still
   DISARMED. Verify that v is rejected after an aborted test or while TeleRC is
   paired, and that reset clears runtime verification. Alternatively use the
   checked build's compile-time verification setting.
5. **Connection gate:** Connect phone to TeleRC-Balancer/192.168.4.1:14550.
   Verify a heartbeat appears and Connect alone never arms. Confirm another
   Wi-Fi station/endpoint cannot replace the paired controller. Verify app
   function/mission buttons do not drive motors or bypass arming.
6. **Supported arm gate:** Hold chassis upright on a low-friction supported
   stand. Enable Control, center sticks, Arm. Check IN PROGRESS then actual
   ARMED heartbeat after the upright hold. Move the chassis/sticks during the
   hold; ARM must cancel. Check that a direction lock, tilt, stale RC and
   non-neutral sticks produce distinct Serial prearm/last fields. Verify no repeated arm request restarts a cancelled
   hold without a new deliberate tap after the retry window.
7. **Balance tuning gate:** In a bounded area with fall catch, tune PD balance
   before driving. Check a forward fall makes both wheels catch forward and
   a backward fall the reverse. Verify no violent oscillation, overheating,
   recurring timing faults, driver thermal shutdown or regulator brownout.
8. **Drive/steer gate:** Use tiny inputs. Confirm forward/reverse lean and left/
   right turn signs. Check full steer cannot suppress a needed balance correction.
   Measure response delay and loop/sample interval distribution under Wi-Fi load.
   Do not infer these from successful host tests or nominal task periods.
9. **Timeout/recovery gate:** While supported and balancing, interrupt valid RC
   for >350 ms, including by pausing the app or turning Wi-Fi off. Verify travel
   requests become zero, motors still correct balance, and pending ARM cancels.
   Restore the connection with a held non-neutral stick: motion must remain
   inhibited. Center for >=250 ms, then move again. Discovery alone must not
   restore travel. Verify actual behavior with your Android background settings.
10. **Stops and fault gate:** Test Stop Control, release, explicit Disconnect,
    Disarm, Serial x and GPIO12 separately. Stop Control/Disconnect should retain
    balance; supported Disarm should turn motors off. GPIO12/fall/fault must latch
    motor-off until reset. Under support, disconnect IMU signal/power and verify
    motor-off; do not reconnect live wiring unless the hardware allows it.
11. **Noise/reconnect gate:** With restraint, test rapid forward/reverse/steer,
    USB attach/remove and expected radio interference. Check supply dips, logs,
    motor current and temperature. Confirm the stop switch/power disconnect is
    reachable during each test. Reset/power cycle must never auto-arm.

Promotion criteria: actual-board compile PASS, all electrical/direction/stop
checks PASS, stable tuned supported balance, measured scheduling/network
behavior acceptable, no critical/high unresolved findings, and repeated recovery
checks PASS. Until then retain the test-candidate label.

## v0.2.5 MPU6500 hardware checks

- With motor power disconnected, upload and reset while upright/still. Confirm
  v0.2.5 and `MPU stage=MPU_READY profile=MPU6500_COMPAT id=0x70 selected=0x68`.
- Confirm calibration reaches DISARMED, angle responds to gentle tilt, and the
  stated axes/signs are correct. Unknown IDs/config faults must inhibit motors.
- Recheck wheel directions using the timed suspended-wheel test before `v`.
- Complete all existing balance/timing/fall checks; retune PD on the actual
  chassis because MPU6500 filter timing differs. No gain change is supplied.

## v0.2.5 calibration diagnostics

With motor power disconnected, reset in the documented upright/still orientation.
Capture CAL/SAMPLE/MPU records. Confirm the failed condition and sample values
agree with actual mounting/movement before changing configuration. Do not clear
the direction lock or relax calibration gates to bypass unexplained sensor data.

## v0.2.6 startup and mounting

- [ ] Motor power disconnected: reset upright/still and record CAL_READY.
- [ ] Brief initial movement followed by stillness inside the calibration budget
      can complete a whole fresh window; persistent tilt/movement still faults.
- [ ] Check configured sensor axes, live angle and rate against physical forward
      tilt with motor power disconnected; repeat after any mount/sign edit.
- [ ] Both mountings require the same suspended-wheel direction confirmation.
- [ ] Perform the existing power, stop, fault, link-loss and supported-balance
      checks; record results before considering this a stable hardware release.

## v0.2.7 saved-calibration checks (not yet executed on hardware)

- [ ] Motor power disconnected: first full calibration reports CAL_READY store=SAVED.
- [ ] Power cycle while upright/still: CAL_SAVED_READY store=REUSED n=150; remains DISARMED.
- [ ] Move during short startup check: it waits for a full stable window or faults, never drives motors.
- [ ] Confirm gravity angle is measured afresh rather than zeroed; arming still enforces upright/direction gates.
- [ ] Disarm/disconnect, send c: reboot and full calibration; confirm save persists on next power cycle.
- [ ] Verify c is rejected during arming, balancing, wheel test, runtime fault, active STOP and paired TeleRC.
- [ ] Change mount or sensor/address: full calibration required; recheck direction signs.
- [ ] Verify normal reuses do not increase NVS writes; evaluate warm/cold drift and recalibrate if needed.

## v0.2.8 command input (not yet executed on hardware)

- [ ] Motor supply disconnected: send ? via Serial Monitor; SERIAL bytes increases and reports RX_OK_NO_MOTOR_ACTION.
- [ ] Confirm uppercase, CR/LF and leading whitespace are accepted and x takes precedence.
- [ ] Wheels suspended: t pulses for 300 ms only, records completion and needs manual direction inspection.
- [ ] Verify actual driver supply/output and motion; do not equate software test completion with physical movement.
- [ ] Verify saved calibration still reuses after upload/power cycle, with default verification still required.

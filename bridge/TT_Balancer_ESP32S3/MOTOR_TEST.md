# v0.3.1 / TeleRC 0.8.90 — step 2 motor test

The reported v0.3.0 test was only 55/255 for 150 ms. A stationary TT motor
may not start at that setting. The report does not establish that this is the
sole hardware cause. v0.3.1 increases only the explicit diagnostic test to
140/255 for 300 ms at 20 kHz, bounded by MAX_PWM and a 500 ms compile-time ceiling.
No change is made to PID gains, motor signs, balancing minimum duty or arming.
There is no automatic kick on ARM, link recovery or every balance correction.

Upload with motor power removed. Use paired app 0.8.90. After calibration ready,
power the regulated motor rail, secure the chassis with both wheels raised and
check the power/suspension box. Tap Left, Right, Both separately and confirm each
dialog. The entire pulse is timed on the ESP32; no repeated phone command is
needed to stop it. STOP, fault, serial x and hardware stop retain priority.

| Evidence | Interpretation / next action |
| --- | --- |
| No accepted PWM test recorded | Request did not start a successful output write this boot. Check freshness, calibration, stop input and displayed reply/fault |
| TEST_RUNNING | Controller entered MOTOR_TEST and requested output |
| TEST_FINISHED | Timer completed; this does not prove wheel motion |
| TEST_ABORTED_FAULT | Read the fault reason and resolve it before retrying |
| PWM unavailable | PWM initialization failed or a latched fault detached outputs |
| L=140, R=-140; readback 140/0 and 0/140 | Default-sign Both pulse reached the LEDC duty registers. Check motor supply under pulse if physical wheels did not move |

Retained last-test readback is sampled immediately after applying PWM and stays
visible after the actual GPIO duties have returned to zero. It is labeled as a
past test; it does not indicate live voltage or a still-running motor. A fresh
boot or calibration clears evidence. Signed commands follow Config.h motor signs.

With power OFF, verify GPIO4/5 → left MX1508 input pair, GPIO6/7 → right input
pair, motors → output pairs, supply minus → common ESP32 GND. GPIO8/9 are the
sensor bus. Check the actual module labels. A connected 2S battery does not
establish motor voltage: measure the regulated supply directly at MX1508 +/−
while pulsing. Keep the documented supply appropriate for the motors/driver;
never feed raw 2S into ESP32 power or 3.3 V sensor pins. USB/logic power alone
cannot drive the motors. Check loose output wires, load, stalls and heating.
Do not bridge motor outputs to GPIO or confirm unobserved direction.

The new app reads both original 20-field v0.3.0 reports and extended 29-field
v0.3.1 reports. Firmware v0.3.1 requires app 0.8.90: the old 0.8.89 strict parser
rejects the new duration/fields. Original command/action/challenge format stays
unchanged. Saved gyro calibration remains compatible; do not erase all flash.

## Historical Serial diagnosis (v0.2.8; old pulse settings below)

# Diagnose Serial input and motor output — v0.2.8

The submitted v0.2.7 log passes CAL_SAVED_READY and MPU_READY, stays DISARMED,
and retains the original startup reason. That shows no accepted t test or arm
attempt in the shown records. It does not establish why commands were not
acted on, nor a motor/driver failure. The prior parser silently ignored
uppercase T/V/A and discarded t when whitespace/line endings occupied the
first byte of a batch. v0.2.8 fixes these demonstrated parser cases and exposes
USB/UART receive evidence. There is no claim that these caused this hardware
report until the new receive diagnostics are obtained.

## Check input first (motor supply disconnected)

1. Upload the whole v0.2.8 folder using your current ESP32-S3 board settings.
   Saved calibration remains compatible; avoid full-flash erase to preserve it.
2. Open the correct Serial Monitor at 115200 baud. Wait for the ready status.
3. Type `?` in its message/input box and press Enter or click Send. Any normal
   line-ending selection works. This query does not command motors or arm.
4. Expect a SERIAL line containing result=RX_OK_NO_MOTOR_ACTION. If bytes stays
   zero, no bytes were read by the firmware on its configured Serial interface.
   Confirm the host actually sends from the input box, the chosen board COM port,
   and USB/Serial settings. Do not remove direction checks to work around input.
5. Send `x`. Expect result=STOP_RECEIVED and DISARMED (or FAULT if already faulted).

## Check the bounded motor test

Keep TeleRC disconnected/unpaired. Support the chassis securely with both wheels
clear of the floor. Connect the regulated motor supply according to WIRING.md.
Send `t` once, then wait. Uppercase `T` is also accepted. Send commands separately;
at most one action is executed per bounded batch, and x anywhere wins.

| SERIAL result | Meaning / next step |
| --- | --- |
| IDLE and bytes=0 | No Serial input bytes have been processed since boot |
| BOOT_INPUT_DISCARDED_RESEND_AFTER_READY | Input arrived before the post-startup empty-buffer guard completed; send again after ready |
| RX_OK_NO_MOTOR_ACTION | Query reached the command handler |
| UNKNOWN_SEND_?_T_V_A_X_C | Received an unsupported first non-whitespace character; command is its hex code |
| TEST_STARTED | t accepted and bounded PWM test requested |
| TEST_FINISHED_CHECK_WHEELS_BEFORE_V | Timed test completed in software; physical motion/direction still require inspection |
| TEST_ABORTED_FAULT | Fault stopped the test; read the ordinary status fault reason |
| REJECTED_DISCONNECT_TELERC | A paired peer owns control; disconnect and wait for peer lease expiration |
| REJECTED_FAULT_STOP_OR_IMU | Fault, active STOP or unavailable calibration blocks test |
| REJECTED_NOT_DISARMED | Another motor test, arming or balancing state is active |
| ARM_NEEDS_DIRECTION_CHECK | Local arm arrived, but t plus manual v confirmation is incomplete |
| VERIFY_NEEDS_COMPLETED_TEST | v arrived before a full t test, or after an interrupted test |
| VERIFY_NEEDS_UPRIGHT_STILL_FRESH | Hold upright/still with fresh sensor samples before v |

SERIAL bytes counts USB/UART input consumed by the sketch. The normal status's
rx counts valid Wi-Fi RC input, not Serial. command is the normalized selected
command byte (t=0x74, v=0x76, a=0x61, x=0x78, ?=0x3F). lastByte is the last raw
byte in the processed batch; CR=0x0D and LF=0x0A are normal line endings.
An event requests a nonblocking report; periodic SERIAL output also appears
approximately every four seconds. It may follow pending telemetry output.

The test remains 55/255 PWM for 150 ms. This may be too short/weak to overcome
some motors' friction; no test-duty increase or physical torque claim is made.
If TEST_FINISHED appears but the wheels never move, check motor supply voltage
at the MX1508 supply terminals during the pulse, common ground, GPIO4/5 to the
left bridge inputs and GPIO6/7 to the right bridge inputs. Confirm output-pair
and motor wiring with power off. Board labels vary; follow its actual pinout.
Do not confirm v until BOTH wheel directions and sensor signs are checked.
There is no current/voltage/speed feedback, so PWM reporting cannot prove
voltage, current, motor motion or driver health.

When both tests pass, hold upright/still, send v, and confirm verified=1.
Support the robot upright over a clear surface before requesting a, or connect
TeleRC, enable control, center sticks and Arm. Calibration and the default
per-boot direction confirmation remain separate.

Please capture the SERIAL line after ? and after t, plus the normal status.
Host tests pass; target ESP32 compilation and physical checks remain pending.

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

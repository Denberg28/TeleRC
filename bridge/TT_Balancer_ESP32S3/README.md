# v0.3.2 direct-arm candidate

The previous v0.3.1 motor-direction `t` then `v` step is OPTIONAL by default (`BALANCER_REQUIRE_DIRECTION_CONFIRMATION=0` in `Config.h`). The firmware will still show `verified=0` until you perform the actual wheel test and confirm. If you want the former mandatory lock, set the macro to `1` and rebuild.

**This is not an automatic arming mode.** After startup the motor output remains zero until explicit serial `a` (unpaired) or TeleRC ARM (paired). Valid calibrated IMU, fresh samples, acceptable upright angle/rate, real STOP, valid PWM, centered/fresh TeleRC controls, arm hold, and fault cutoffs remain enforced. Failure of any blocks arming. Verify motor and sensor polarity before applying motor power; incorrect feedback can cause immediate violent acceleration. The user's static -8.8-degree reading is beyond ±4-degree upright gate; fix mount/physical upright rather than relaxing it. Wi-Fi password is a demonstration default and must be changed for real use.

# TeleRC balancer motor-test update — v0.3.1

Pair with TeleRC **0.8.90/code108**. Open the dropdown → Balance.
App 0.8.89 only understands the old 150 ms status and cannot use the new
300 ms test report; upgrade the APK before flashing v0.3.1.

1. If Calibration ready is shown, step 1 is already complete. Recalibrate after
   changing sensor mounting or a failed saved check, with motor power removed.
2. Apply regulated motor power and common ground. Secure the chassis and raise
   both wheels. Confirm the checkbox, then test Left, Right, Both separately.
3. Each explicit test applies **140/255 PWM at 20 kHz for 300 ms**, then zero.
   Signed output follows LEFT_SIGN/RIGHT_SIGN and the inactive H-bridge leg is
   cleared first. The balancing PID, MAX_PWM and MIN_PWM are unchanged; this
   test pulse is never automatically injected into ordinary balancing.
4. Check last-test command/readback in the app, even after the pulse ends.
   No movement opens power/pin/output checks. Peripheral readback is NOT motor
   voltage, current, direction or wheel-motion feedback.
5. Confirm actual movement and tilt signs using the new observation checkbox,
   then Confirm directions. Only a completed Both test qualifies. Setup never
   arms: use Controls → Phone control → centered sticks → ARM deliberately.

Left motor input GPIO4/5, right GPIO6/7; sensor SDA/SCL remain GPIO8/9.
The 4-wheel MiniRover GPIO8–11 motor map must not be copied onto this balancer.
MiniRover already uses 20 kHz proportional PWM without an automatic kick. This
update uses the same explicit LEDC channel assignment/active-leg method, with
an independently bounded diagnostic duty. No physical starting-torque claim.
See MOTOR_TEST.md for electrical diagnosis and HIL_CHECKLIST.md for acceptance.

# TeleRC Balancer ESP32-S3 — v0.3.1 test candidate

ESP32-S3 + MPU6050 or MPU6500 + one MX1508 + two TT motors. This replaces the standalone
v0.1.0 firmware with TeleRC legacy Wi-Fi/MAVLink control. No APK is included.
Read [WIRING.md](WIRING.md) before applying motor power.

**Motor-test / Serial input update:** v0.3.0 accepts uppercase/lowercase
commands and leading whitespace/line endings. It reports received Serial bytes
and explicit command results instead of silently ignoring unsupported input.
Send **`?`** from the Serial Monitor input box and press Enter/Send; expect
`SERIAL ... result=RX_OK_NO_MOTOR_ACTION`. Then, with TeleRC disconnected and
wheels suspended, send **`t`**. Expect `TEST_STARTED` and
`TEST_FINISHED_CHECK_WHEELS_BEFORE_V`. If no input bytes arrive, the SERIAL record
shows `bytes=0`; `rx=0` in the normal status refers to Wi-Fi RC packets instead.
See [MOTOR_TEST.md](MOTOR_TEST.md). The motor pulse remains 140/255 for 300 ms.
The new diagnostics do not prove the motor wiring/power path works, and direction
verification remains required before arming. Saved gyro calibration is retained.

**Saved calibration:** A successful full gyro calibration is saved automatically.
Next startup reuses the active pitch gyro offset after a fresh ~0.6-second
stillness/sensor check (up to 5 seconds to settle). It reports
`CAL stage=CAL_SAVED_READY store=REUSED`. The first boot needs ~3 seconds of still
upright samples, with up to 15 seconds to settle; success reports
`CAL stage=CAL_READY store=SAVED`. A stable stored-bias mismatch over 1 degree/s
triggers full recalibration. Corrupt, incompatible or missing records also need
full calibration. Motion, bus/data failures and STOP still block readiness.

To force recalibration, disconnect TeleRC, disarm, support upright with motor
power disconnected, and send **`c`** at 115200 baud. This erases saved calibration
and restarts the board. It also works from a calibration-only boot fault; runtime
and stop faults still need a physical reset. `c` is rejected during motor tests,
arming, balancing, or while a TeleRC peer is paired. Allow the normal pairing
lease to expire after disconnecting. Change sensor mounting or replace the IMU:
use `c` and redo direction checks. Changed mount, sensor ID/address or I2C pins
invalidate the saved record automatically. Erasing all flash also clears it.

Only gyro bias is saved. Boot remains DISARMED; `t`/`v` direction confirmation
is still per boot unless the verified build setting is explicitly configured.
A live accelerometer measurement establishes angle on every boot: a physically
upright reading of -6 to -7 degrees is a mounting/trim issue, and is not silently
zeroed. See [CALIBRATION.md](CALIBRATION.md).

**Reference mounting:** Config.h supports the reference-style X-gyro mounting;
default mounting and pins remain unchanged. See [REFERENCE_FIX.md](REFERENCE_FIX.md).

**Calibration diagnostics:** v0.3.0 reports `CAL stage=...` with measured
acceleration (g), gyro rates (degrees/s), accepted sample count, tilt, norm and
variation. `SAMPLE stage=...` identifies bus errors, invalid frames and the raw
clipped axis. A fault freezes these snapshots; it does not disable the guards.
Upload with motor power disconnected and reset upright/still; copy one CAL and
one SAMPLE line after the fault. See MPU_TROUBLESHOOTING.md for thresholds.

**Sensor profile:** The submitted `who68=0x70` selects an MPU6500-compatible register profile.
The module is labeled MPU6050; its actual physical silicon remains unconfirmed.
v0.3.0 selects its explicit filter configuration and verifies identity after
reset. It also retains MPU6050 support. Wiring and TeleRC settings are unchanged.
Expected sensor diagnostic: `MPU stage=MPU_READY profile=MPU6500_COMPAT id=0x70`.
Hold upright and still for startup calibration. `verified=0` remains a separate
arming requirement: complete the suspended-wheel checks in ARMING.md.

**MPU startup diagnosis:** [MPU_TROUBLESHOOTING.md](MPU_TROUBLESHOOTING.md)
explains the new once-per-second `MPU stage=` line. Startup probes 0x68/0x69 and
uses an identity-confirmed MPU6050 (ID 0x68) or MPU6500 (ID 0x70). Unsupported chip IDs stay inhibited;
failed configuration reads/writes report their specific register and values.

**Arming update:** [ARMING.md](ARMING.md) explains the actual pre-arm blocker
shown in Serial, rather than assuming every denial means off-center sticks.
The default direction lock can now be confirmed for the current boot using
Serial `t`, inspect directions/axes, then `v`; no gain or wiring changes are
made by this confirmation and it never arms the motors.

**Wi-Fi startup fix:** v0.2.0 started its AP only after successful MPU/PWM checks
and gyro calibration. A failed check could therefore hide the SSID entirely.
v0.2.1 starts the network task before those checks. Early faults retain Wi-Fi
and diagnostic servicing while arming/motor output remain blocked. Connection
still requires working hardware, a valid ESP32-S3 build and successful AP setup.

**Status: reviewed and host tested. Actual ESP32 compilation and physical tests
are BLOCKED/unperformed here. This is not a hardware-validated stable release.**

## Connect with TeleRC

| Setting | Value |
| --- | --- |
| Phone Wi-Fi network | `TeleRC-Balancer` |
| Default Wi-Fi password | `telerc123` — change `WIFI_PASSWORD` in Config.h for your device |
| App transport | Legacy Wi-Fi UDP / Wi-Fi |
| App rover address | `192.168.4.1` |
| UDP port | `14550` |
| Vehicle MAVLink system/component | `1 / 1` |
| GCS source system/component | `255 / 190`, as used by TeleRC |

No APK update is required for the basic discovery, joystick and arm/disarm
messages inspected in TeleRC's current source. This is source-level compatibility;
physical testing with your installed APK remains required. Angle/rate ATTITUDE
telemetry and specific pre-arm STATUSTEXT messages are emitted for diagnostic
clients; the existing TeleRC UI may not show them. Serial at 115200 baud is the
reference diagnostic output for this firmware.
This controller is generic MAVLink, not ArduPilot. GPS, missions, F1/F2/F3 servos,
ELRS and LoRa are not implemented here. Those commands return UNSUPPORTED.
The app's GPS-derived map and speed estimate must not be treated as real
balancer position or velocity feedback.

1. With motor power disconnected, upload the sketch and keep all `.h` files
   from the sketch folder beside the `.ino`.
2. Use **esp32 by Espressif Systems 3.x**, your exact dual-core ESP32-S3 board,
   and the flash/PSRAM settings matching it. No external MPU/MAVLink libraries.
   Native USB Serial requires USB CDC On Boot enabled; Serial is 115200 baud.
3. First keep `BALANCER_DIRECTIONS_VERIFIED 0`. Stand the chassis upright and
   motionless for the first full calibration; later boots need the short saved-offset check.
4. Before opening TeleRC, perform the suspended-wheel checks below, then send
   Serial `v` to confirm their result for this boot. Alternatively, after checking,
   set `BALANCER_DIRECTIONS_VERIFIED 1` and upload for a persistent build setting.
5. Join the Wi-Fi above. Android may report no internet; keep this connection.
   Open TeleRC, set address/port, tap Connect, and wait for the rover heartbeat.
6. Put the robot upright in a clear, bounded test area with a fall support.
   Tap **Enable Control first**, center both sticks, then tap **Arm**. Keep it
   centered and upright for 1 second. Only the resulting ARMED heartbeat confirms
   success. An IN PROGRESS ACK means the upright check is still running.
7. Begin with tiny stick movements. Drive requests a bounded lean; steering
   requests differential motor torque. Neither is a closed-loop speed command.

If Enable Control is not active, the app sends an initial neutral burst when
Arm is pressed but no continuing control stream. This firmware cancels arming
when that stream expires. Keep the app foreground and controls centered during
arming. A declined/cancelled arm needs a new deliberate tap after centering;
retries from the old request cannot extend its lifetime.

## If the Wi-Fi network is missing

1. Disconnect motor power. Power the ESP32-S3 through a suitable USB data cable
   and verify that the v0.3.1 sketch uploaded successfully to the correct board.
2. Scan the phone's Wi-Fi settings for **TeleRC-Balancer**. The app cannot discover
   a rover until the phone joins its access point.
3. Open Serial Monitor at **115200 baud**, with USB CDC On Boot enabled if using
   native USB, then press RESET. Look for a line beginning `v0.3.1`.
4. Inspect `wifi=` and the final fault reason:

| Serial field | Meaning / next check |
| --- | --- |
| `wifi=STARTING` | Network task has not finished initialization; persistent/repeating startup needs reset/power/board-setting investigation |
| `wifi=READY` | AP and UDP initialization succeeded; check SSID/password configuration and phone Wi-Fi scan |
| `wifi=MODE_FAILED` | ESP32 Wi-Fi AP mode initialization failed |
| `wifi=IP_FAILED` | AP static-IP configuration failed |
| `wifi=AP_FAILED` | Access point creation failed; check configuration, power and core/board build |
| `wifi=UDP_FAILED` | AP creation succeeded but UDP bind failed; Wi-Fi may be visible while control is unavailable |
| `FAULT ... MPU_...` | Read the separate `MPU stage=` line and MPU_TROUBLESHOOTING.md; check power, SDA GPIO8, SCL GPIO9 and AD0 before arming |
| `FAULT ... calibration` | Reset while holding chassis upright/still; AP visibility no longer depends on successful calibration |

A repeated reboot/brownout, the wrong firmware/board, or an actual Wi-Fi startup
failure can still prevent an SSID. Use the Serial output to distinguish them.
The direction-verification flag is required for arming, not for Wi-Fi visibility.
Network initialization is not restarted during active balancing.

## Suspended-wheel direction check

Keep the phone app disconnected. With both wheels off the floor:

- Confirm MPU +X points forward, +Y left, +Z up. Forward tilt must make `angle`
  positive; during forward rotation, `rate` must be positive. Remount if wrong.
- Send Serial **`t`** once for a capped **300 ms** forward wheel pulse. Both wheels
  must turn to propel the robot forward. Fix `LEFT_SIGN`/`RIGHT_SIGN` (or motor
  lead order with power off), re-upload and recheck.
- After visually confirming that both wheels propel forward and the sensor
  signs match the specified axes, send Serial **`v`** to confirm directions for
  this boot. A completed `t` test is required first; an aborted test does not
  qualify. `v` does not arm the robot. Reset clears this session confirmation.
- For a persistent build setting, change `BALANCER_DIRECTIONS_VERIFIED` to 1
  only after checking both wheel directions and sensor signs, then re-upload.
- Serial **`a`** can request local bench arming only while no TeleRC peer is paired.
  Serial **`x`** stops/disarms. The GPIO12 button latches a fault until reset.
  Local arm/test/verification commands are rejected while TeleRC owns the link. Serial stop
  remains available. A local motor test must never run with wheels on the floor.

## Response and loss behavior

The core-1 task runs local balance at nominal 250 Hz. The core-0 task owns Wi-Fi,
UDP parsing and telemetry. Only short fixed-size snapshots cross between tasks;
network calls and packet parsing do not run inside the balance task. Actual
radio latency and scheduling jitter still need measurement.

| Event | Firmware behavior |
| --- | --- |
| Drive CH2 1100…1900, 1500 neutral | Requests up to ±2° lean, with a short bounded setpoint slew |
| Steer CH1 1100…1900, 1500 neutral | Requests up to ±28 PWM differential; balance correction gets priority |
| Normal neutral | Returns requested lean/yaw toward zero; keeps balance active |
| 350 ms without valid complete CH1/CH2 | Clears lean/yaw requests and inhibits travel; balancing continues |
| Control returns after timeout | Moving packets remain inhibited until centered frames persist for 250 ms |
| Wi-Fi station loss / explicit Disconnect / RC release | Cancels travel and pending arming; an already armed robot keeps balancing |
| Reconnect/discovery | Establishes link only; never arms or restores old drive requests |
| Explicit Disarm | Turns motors off immediately; support the robot first, because it will fall |
| Fall, sensor/loop fault, stop button | Motors off, PWM detached, fault latched; reset and pass startup sensor/calibration checks |

**Stop Control** and **Disarm** have different effects. Use Stop Control to cancel
travel while remaining balanced. Support the chassis before Disarm. Disconnecting
Wi-Fi is not an emergency motor kill. Keep the physical motor-power switch
reachable. With no encoders, neutral lean cannot guarantee a stationary robot,
controlled braking distance, bounded speed or position hold.

MAVLink CRC, expected source/target IDs, one paired phone endpoint, bounded
packet sizes, whole-datagram validation and best-effort moving-sequence checks
reject malformed, duplicate and many reordered packets. Discovery/heartbeat
never refresh the motion lease. TeleRC's fixed neutral sequence 0/1/2 and arm
retry sequence 200/201/202 are handled explicitly. Neutral/release/disarm are
safety-lowering operations and may be accepted regardless of motion sequence.

The legacy protocol has no session nonce, sender timestamp or MAVLink signature.
WPA2 Wi-Fi access is the link protection; CRC is not authentication. Firmware
cannot prove the age of an otherwise valid packet already delayed in the radio
or kernel buffer, and cannot provide cryptographic replay protection without an
app protocol change. Expired pending ARM snapshots are rejected locally after
500 ms; duplicate retries never extend the original request time.

## Tune and verify

Starting KP=18, KI=0, KD=0.70; MAX_PWM=160/255. These are bench defaults, not gains
tuned for your robot. Keep KI and MIN_PWM zero initially. Confirm directions,
then tune PD balance before increasing drive lean, yaw or PWM limits.

- Weak, gradual fall: inspect torque/supply and increase KP cautiously.
- Oscillation: reduce KP and/or increase KD in small steps; excessive KD causes
  chatter and heat. Slow/high-backlash TT motors may not be suitable.
- Persistent drift: adjust TRIM_DEG ~0.2° at a time. Encoders are needed for
  reliable speed/position control. Lean control can cause continued acceleration.
- A sustained 300 ms PWM limit trips the controller. Never bypass it to hide
  insufficient torque, incorrect signs or a driver thermal/current problem.

See [ARMING.md](ARMING.md), [HIL_CHECKLIST.md](HIL_CHECKLIST.md), [VALIDATION.md](VALIDATION.md) and
[CHANGELOG.md](CHANGELOG.md). Software cannot measure current, motor speed,
battery voltage or driver temperature with this wiring. There is no independent
hardware motor-enable watchdog: a full CPU hang could leave PWM active until
motor power is cut. Use the physical disconnect and the required input pull-downs.

## Source protocol evidence and rollback

Compared against TeleRC `app/src/main/java/io/github/denberg28/telerc/Mavlink.kt`
(blob `aaeb58b42254c4a1962c743da7850a998fa16d22`) and MainActivity.kt
(blob `edb4f4d935860b78a6d93b98177a48d2e5fe7f59`) from the repository on
20 October 2026, including its neutral handover and arm retry workflow. Also
reviewed the saved MiniRover v1.2.2 codec. References are evidence, not dependency
files you must install.

For rollback, disconnect motor power and flash the original v0.1.0 standalone
sketch from the prior package; it has no TeleRC Wi-Fi support. Recheck direction
signs, pins and PID configuration after any firmware change. Boot always remains
disarmed. Never copy neutral latches or throttle ramps from a conventional rover
into the inner balance controller.

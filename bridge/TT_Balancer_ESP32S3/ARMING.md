# Arming — v0.2.6

An ARM DENIED result does not establish that the sticks are off-center. This
firmware also checks motor direction confirmation, sensor calibration/freshness,
PWM readiness, stop switch, tilt/rate, connection and fresh complete CH1/CH2.

## First setup with the default configuration

1. Keep TeleRC disconnected and suspend both wheels clear of the floor. Keep
   the motor-power switch reachable. Use the power wiring in WIRING.md.
2. Boot upright and still for full calibration or the brief saved-offset check. Open Serial at 115200
   baud, no line ending. Check the line starts `v0.2.6` and Wi-Fi reports READY.
3. Tilt forward/back with motors inactive: angle must become positive/negative,
   and the rate sign must agree during rotation. Check the Config.h mounting selection: 0 for sensor +X forward/+Y left/+Z up, 1 for sensor +Y forward/-X left/+Z up. The robot-frame angle/rate signs must agree.
4. Return upright. Send **t** once and watch both wheels during the 150 ms pulse.
   They must propel the robot forward. If wrong, cut motor power and correct
   motor signs/leads, then repeat. If they do not move, do not confirm the test.
5. Once both wheel directions and sensor signs are correct, return upright/still
   and send **v**. Confirm `verified=1`; the mode must remain DISARMED.
6. Put the robot on the floor with a fall support. Connect TeleRC, Enable Control,
   center both sticks for at least 1 second, then Arm while holding upright.
   Hold still until the actual ARMED heartbeat confirms the upright check.

`v` records your manual confirmation for this boot. The controller cannot
visually verify wheel rotation or physical sensor mounting. A completed motor
test is a prerequisite; its completion alone does not prove correct direction.
Do not send v until you have checked both. It does not change PID gains or arm.
Reset clears runtime confirmation. To retain a verified configuration across
reset, set `BALANCER_DIRECTIONS_VERIFIED 1` in Config.h after checking the actual
hardware, then upload. Repeat checks after changing motor wiring/signs or IMU axes.

If the final reason starts `MPU_`, resolve the sensor fault first using the
separate `MPU stage=` line and [MPU_TROUBLESHOOTING.md](MPU_TROUBLESHOOTING.md).
Centered controls do not establish sensor readiness. `angle=0` / `rate=0` in
an initialization fault are defaults, not valid attitude measurements.

## Read the actual blocker

Example Serial fields (values shown are illustrative):

```
v0.2.6 DISARMED wifi=READY prearm=VERIFY_DIRECTIONS_T_THEN_V last=VERIFY_DIRECTIONS_T_THEN_V verified=0 CH1=1500 CH2=1500 age=12 rx=30 reject=0 angle=0.30 rate=0.05 pwm=0
```

This example has centered channels but lacks direction confirmation. The
periodic `prearm=` value is the current local readiness check; `last=` records
the last network arm rejection/completion reason. A short retry cooldown may
still apply even when all current physical/control checks show READY.

| Blocker | Action |
| --- | --- |
| VERIFY_DIRECTIONS_T_THEN_V | Complete the above suspended-wheel test and manual confirmation, or use a checked build with verification flag 1 |
| IMU_NOT_READY / SENSOR_STALE | Check sensor power, SDA8/SCL9, AD0/address; resolve any fault and reset upright |
| FAULT_RESET_REQUIRED | Read the final Serial reason, correct the cause, then reset and pass the startup checks |
| PWM_NOT_READY | Check the board/pin configuration and actual ESP32 compile; do not force-arm |
| RELEASE_STOP_SWITCH | Release GPIO12 stop; a latched stop fault still needs reset |
| HOLD_UPRIGHT_WITHIN_4_DEG | Hold chassis near physical upright / TRIM_DEG; check sensor mounting and reported angle |
| HOLD_STILL | Stop rotating/shaking the chassis during the upright hold |
| CONNECT_TELERC | Join TeleRC-Balancer Wi-Fi, use 192.168.4.1:14550, tap Connect |
| NO_COMPLETE_RC_RECEIVED | Enable Control; check raw CH1/CH2 and rx counter. No complete control frame has arrived |
| RC_EXPIRED_ENABLE_CONTROL | Refresh control after stopping; heartbeat/discovery alone do not refresh RC |
| CENTER_CH1_CH2 | Release both sticks; valid neutral window is 1475–1525 microseconds on BOTH channels |
| WAIT_THEN_TAP_ARM_AGAIN | Wait at least 0.6 seconds, re-center and send a new deliberate Arm request |
| ARM_REQUEST_EXPIRED / ARM_CANCELLED | Re-center, hold upright, and tap Arm again; do not replay the old request |
| CONTROLLER_STATUS_EXPIRED | Controller status stopped updating; investigate scheduling/faults and reset safely |

`CH1`/`CH2` show the last validated override's raw first two fields, including
release (0) or ignore (65535) values. `rx` counts decoded targeted RC frames;
`reject` counts rejected transport/control traffic. `age` is the local time since
the last accepted complete CH1/CH2 command, not a sender timestamp. A failed
frame, ignored axis, heartbeat or discovery cannot extend motor-control freshness.

The firmware also transmits a standard MAVLink STATUSTEXT reason with an arm
denial/progress result. The existing APK may still show generic DENIED if it
does not display STATUSTEXT. Serial is available without an APK update.

Balance readiness is not proof of a tuned or stationary robot: no encoders or
current/temperature sensing are present. Follow the supported tuning procedure.

Saved gyro calibration does not save the runtime t/v direction confirmation or
an armed state. See CALIBRATION.md for the disarmed/unpaired c reset command.

## v0.2.8 command reception

Uppercase/lowercase commands and leading whitespace/line endings are accepted.
Send ? from Serial Monitor's input box and click Send/press Enter to confirm
RX_OK_NO_MOTOR_ACTION before trying t. See MOTOR_TEST.md for SERIAL results.
Normal status rx counts Wi-Fi input; SERIAL bytes counts processed USB/UART input.

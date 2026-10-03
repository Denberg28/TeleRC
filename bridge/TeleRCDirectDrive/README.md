# TeleRC ESP32-S3 Direct Drive

This firmware runs a **basic TeleRC rover without a SpeedyBee/Pixhawk/ArduRover flight controller**.

Architecture:

~~~
TeleRC Android APK / PC TeleRC
             |
          Wi-Fi UDP
             |
          ESP32-S3
             |
       4x BTS7960
             |
          4 motors
~~~

The existing Android TeleRC and PC TeleRC MAVLink control protocol is preserved. The ESP32-S3 provides the minimum rover-side MAVLink heartbeat and ARM/DISARM behavior expected by the apps, then converts CH1/CH2 commands directly into four BTS7960 motor outputs.

## Control mapping

- CH1 = steering
- CH2 = bidirectional drive
- 1500 us = neutral
- CH3/CH4 are ignored for motor mixing
- CH5-CH8 are rejected by this basic direct-drive profile

Differential mixing is:

~~~
left  = drive + steering
right = drive - steering
~~~

The result is normalized before being converted to motor duty, so mixed steering cannot exceed the configured maximum output.

## ESP32-S3 to BTS7960 pin assignment

| Motor | RPWM | LPWM |
|---|---:|---:|
| Front Left | GPIO 8 | GPIO 9 |
| Rear Left | GPIO 10 | GPIO 11 |
| Front Right | GPIO 12 | GPIO 13 |
| Rear Right | GPIO 14 | GPIO 15 |

For each BTS7960:

~~~
ESP32 RPWM --------> BTS RPWM
ESP32 LPWM --------> BTS LPWM
regulated 5 V -----> BTS VCC
regulated 5 V -----> BTS R_EN
regulated 5 V -----> BTS L_EN
common GND --------> BTS GND

motor battery + ---> BTS B+
motor battery - ---> BTS B-
motor -------------> BTS M+ / M-
~~~

Do not feed motor-battery voltage directly into the ESP32. Use an appropriate regulator for the ESP32 and BTS logic supply.

The ESP32 and all BTS7960 logic grounds must share a reference. Keep high-current motor return paths in the motor power distribution wiring rather than through ESP32 wiring.

## Wi-Fi

Default access point:

- SSID: `TeleRC-Rover`
- ESP32 address: `192.168.4.1`
- UDP port: `14550`

When `PERSONAL_AP_PASSWORD` is left empty, the sketch generates a random 16-character password once and stores it in ESP32 NVS. Open Serial Monitor at 115200 baud after boot to read the password.

Do not commit a personal password to the repository.

## Android TeleRC

1. Flash `TeleRCDirectDrive.ino` to the ESP32-S3.
2. Raise all four rover wheels.
3. Connect the phone to `TeleRC-Rover`.
4. In TeleRC Setup use:
   - Host: `192.168.4.1`
   - Port: `14550`
5. Tap Connect.
6. Wait for a healthy rover heartbeat.
7. Keep both controls neutral and use ARM.
8. Enable control.
9. Test steering and drive at low input first.

The current Android mapping is already CH1 steering / CH2 drive, so no APK protocol change is required for this direct-drive firmware.

## PC TeleRC

Use TeleRC UDP mode with the ESP32 target:

- target host: `192.168.4.1`
- target port: `14550`
- steering channel: CH1
- throttle/drive channel: CH2

PC TeleRC sends sparse MAVLink RC overrides. The direct-drive firmware accepts those semantics and requires both CH1 and CH2 to be actively owned before motor movement is allowed.

## ARM/DISARM behavior

This ESP32-only rover implements a local software arm state.

At boot:

~~~
DISARMED
motor outputs = 0
~~~

ARM is accepted only when the rover is stopped and the active controls are neutral. DISARM immediately zeros all four motor outputs.

The firmware returns MAVLink COMMAND_ACK and reflects the arm state in its rover HEARTBEAT so existing TeleRC ARM/DISARM UI can follow the actual ESP32 state.

ARM is not a substitute for a physical emergency stop or motor-power disconnect.

## Failsafe behavior

The firmware implements:

- motors disabled at boot
- 500 ms live-control packet watchdog
- immediate stop and disarm on watchdog timeout
- immediate stop and disarm on explicit TeleRC disconnect
- immediate stop and disarm when the Wi-Fi controller leaves the ESP32 AP
- immediate stop when CH1 or CH2 ownership is released
- acceleration slew limiting
- faster deceleration
- mandatory zero crossing before motor reversal
- 20 kHz BTS7960 PWM
- serial diagnostics
- controller lease/handover isolation

Loss of Wi-Fi or application control therefore does not rely on an F405 or Mission Planner failsafe.

## Motor direction

Motor order:

~~~
0 = Front Left
1 = Rear Left
2 = Front Right
3 = Rear Right
~~~

If a motor runs backward, first verify wiring. If mechanical mounting requires software inversion, change the matching entry in:

~~~cpp
constexpr bool INVERT_MOTOR[4] = {
  false,
  false,
  false,
  false
};
~~~

Do the same for steering/drive direction using `INVERT_STEERING` and `INVERT_DRIVE`.

## First bench test

1. Disconnect motor battery power.
2. Verify ESP32/BTS logic wiring and common ground.
3. Raise all wheels.
4. Flash the firmware.
5. Open Serial Monitor at 115200.
6. Connect Android or PC TeleRC.
7. Confirm heartbeat/link becomes healthy.
8. Confirm the rover boots DISARMED.
9. Confirm ARM is rejected if a drive control is not neutral.
10. ARM at neutral.
11. Apply low motor power/current limit if available.
12. Move DRIVE slightly forward and verify all wheels.
13. Test reverse.
14. Test left/right steering.
15. While moving slowly, close/disconnect TeleRC and confirm an immediate stop.
16. While moving slowly, turn off Wi-Fi and confirm an immediate stop.
17. Reconnect and verify the rover remains DISARMED until explicitly armed again.

Only proceed to ground testing after every stop path works with the wheels raised.

## When to use this firmware

Use **TeleRCDirectDrive** for:

- basic manual TeleRC rovers
- Android joystick control
- PC steering-wheel control
- low-cost rover builds
- platforms that do not need autonomous navigation

Use the existing **TeleRCBridge + ArduRover flight controller** architecture when you need mature EKF/navigation, waypoint missions, geofencing, RTL, or other autopilot functions.

The intended TeleRC roadmap is therefore:

~~~
BASIC
TeleRC -> ESP32-S3 -> motor drivers

ADVANCED / AUTONOMOUS
TeleRC -> ESP32-S3 <-> ArduPilot FC -> navigation/motor system
~~~

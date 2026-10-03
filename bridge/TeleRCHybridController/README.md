# TeleRC Hybrid ESP32-S3 Rover Controller

This firmware combines the two existing TeleRC rover paths into one ESP32-S3 controller.

## Modes

### DIRECT

```
TeleRC Android / PC TeleRC
          |
       Wi-Fi UDP
          |
       ESP32-S3
          |
      4x BTS7960
          |
        Motors
```

### AUTOPILOT

```
SpeedyBee F405 / Pixhawk
       M5-M8 PWM
          |
       ESP32-S3
          |
      4x BTS7960
          |
        Motors
```

### FAILSAFE

```
ESP32-S3
   |
all motor PWM = 0
```

FAILSAFE always has priority over DIRECT and AUTOPILOT.

## Mode selector

GPIO16 selects the requested source:

- GPIO16 LOW = DIRECT
- GPIO16 HIGH = AUTOPILOT

The input uses the ESP32 internal pulldown, so an unconnected selector defaults to DIRECT.

Do not apply 5 V to GPIO16.

A simple SPST switch can be wired:

```
3.3 V ---- switch ---- GPIO16
```

Switch open = DIRECT  
Switch closed = AUTOPILOT

Every mode transition first enters FAILSAFE. The selected source must then remain valid and neutral for 300 ms before it receives motor authority.

This prevents stale throttle or an already-moving autopilot output from taking over during a source change.

## SpeedyBee / flight-controller inputs

Default independent four-wheel mapping:

| F405 output | ESP32 input | Wheel |
|---|---:|---|
| M5 | GPIO4 | Front Left |
| M6 | GPIO5 | Rear Left |
| M7 | GPIO6 | Front Right |
| M8 | GPIO7 | Rear Right |

The F405 and ESP32 must share ground.

If your F405 is configured for only LEFT and RIGHT skid-steer outputs, set:

```cpp
constexpr bool AUTOPILOT_PAIRED_SKID_STEER = true;
```

Then:

- GPIO4 = LEFT
- GPIO5 = RIGHT
- GPIO6/GPIO7 ignored

Do not assume M5-M8 functions. Verify the ArduRover SERVOx_FUNCTION assignments before motor testing.

## BTS7960 outputs

| Wheel | RPWM | LPWM |
|---|---:|---:|
| Front Left | GPIO8 | GPIO9 |
| Rear Left | GPIO10 | GPIO11 |
| Front Right | GPIO12 | GPIO13 |
| Rear Right | GPIO14 | GPIO15 |

For each BTS7960:

```
ESP32 RPWM --------> BTS RPWM
ESP32 LPWM --------> BTS LPWM
regulated 5 V -----> BTS VCC
regulated 5 V -----> BTS R_EN
regulated 5 V -----> BTS L_EN
common GND --------> BTS GND
```

The motor battery goes only to the BTS power stage through properly sized wiring, fuse/disconnect and distribution hardware.

## DIRECT mode behavior

DIRECT preserves the existing TeleRC protocol:

- ESP32: 192.168.4.1
- UDP: 14550
- CH1 = steering
- CH2 = bidirectional drive
- Android and PC TeleRC compatibility
- local MAVLink rover heartbeat
- local ARM/DISARM
- command watchdog

The rover enters DIRECT only after the TeleRC source is connected and neutral/stable.

After DIRECT becomes active, motor motion still requires an explicit TeleRC ARM.

## AUTOPILOT mode behavior

AUTOPILOT uses the F405/Pixhawk PWM outputs directly.

The ESP32 does not attempt to replace ArduPilot navigation, EKF, waypoint logic or vehicle failsafes.

The ESP32 only performs:

- PWM validation
- source arbitration
- motor output conversion
- slew limiting
- reversal-through-zero
- stale-input failsafe

If the required F405 PWM input is lost for more than 150 ms, the ESP32 immediately enters FAILSAFE and zeros all motor outputs.

## Important ARM behavior

The ESP32 local TeleRC ARM state applies only to DIRECT mode.

In AUTOPILOT mode, the flight controller remains responsible for its own arming logic and output state.

The hybrid firmware intentionally does not claim that the F405 is armed because it receives only PWM motor outputs, not the F405 arm-state telemetry.

## Source transition examples

DIRECT to AUTOPILOT:

```
DIRECT
  |
GPIO16 -> HIGH
  |
FAILSAFE
motors = 0
  |
validate M5-M8
  |
require neutral 300 ms
  |
AUTOPILOT
```

AUTOPILOT to DIRECT:

```
AUTOPILOT
  |
GPIO16 -> LOW
  |
FAILSAFE
motors = 0
  |
TeleRC connected
  |
neutral 300 ms
  |
DIRECT
  |
TeleRC ARM required
```

## Failsafe triggers

The controller enters FAILSAFE for:

- DIRECT control packet timeout
- DIRECT controller lease loss
- Wi-Fi client loss while DIRECT is active
- explicit TeleRC disconnect
- AUTOPILOT PWM loss
- DIRECT/AUTOPILOT source change
- controller handover

FAILSAFE performs an immediate hard stop:

```
target PWM = 0
actual PWM = 0
DIRECT arm = false
DIRECT ownership cleared
```

A source cannot automatically resume motion after a failsafe.

## First bench test

1. Raise all wheels.
2. Disconnect motor battery.
3. Verify ESP32, F405 and BTS grounds are common.
4. Verify M5-M8 are connected only to GPIO4-7.
5. Verify BTS RPWM/LPWM are connected only to GPIO8-15.
6. Verify GPIO16 selector uses 3.3 V only.
7. Flash `TeleRCHybridController.ino`.
8. Open Serial Monitor at 115200 baud.
9. Leave GPIO16 LOW and connect TeleRC.
10. Confirm FAILSAFE -> DIRECT after neutral dwell.
11. ARM via TeleRC.
12. Test DIRECT steering/drive at low power.
13. DISARM.
14. Set GPIO16 HIGH.
15. Confirm the firmware immediately enters FAILSAFE.
16. Verify M5-M8 are neutral.
17. Confirm FAILSAFE -> AUTOPILOT after the neutral dwell.
18. Command low-speed ArduRover output.
19. Disconnect one required M5-M8 signal and verify immediate FAILSAFE.
20. Return GPIO16 LOW and confirm DIRECT does not move until TeleRC is connected and armed again.

Only perform ground testing after all stop paths work with wheels raised.

## Recommended TeleRC stack

```
BASIC
TeleRC -> ESP32-S3 -> BTS7960

HYBRID
                     +-> DIRECT TeleRC
ESP32-S3 arbiter ----|
                     +-> F405 M5-M8 AUTOPILOT
          |
       BTS7960

FULL AUTONOMOUS
TeleRC <-> ESP32-S3 <-> ArduPilot
                        |
                   GPS / EKF / mission
```

The hybrid controller keeps the ESP32 as the final motor-safety and source-arbitration layer while allowing the F405 to remain the navigation/autonomy controller.

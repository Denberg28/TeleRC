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

After DIRECT becomes active, motor motion requires an explicit TeleRC ARM with fresh neutral CH1 and CH2 packets (both less than 500 ms old). Android sends neutral before ARM. With current PC TeleRC, enable control with the wheel and pedals centered, then ARM; an ARM sent before any neutral override is rejected. Releasing either owned channel stops and disarms DIRECT.

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

The ESP32 motor arbiter does not use the FC armed state as an input. In AUTOPILOT, TeleRC receives actual FC arm-state telemetry over UART; the FC must suppress motor PWM appropriately when disarmed.

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

DIRECT requires a new ARM after a failsafe. AUTOPILOT PWM-loss recovery requires valid neutral PWM for 300 ms; it may then resume following new FC output. Explicit disconnect and UART congestion latch motor authority off until a selector change; DIRECT discovery can also clear the disconnect latch.

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


## MAVLink UART integration

The same ESP32-S3 now also carries a bidirectional MAVLink UART link to the optional F405/Pixhawk.

### UART wiring

For SpeedyBee F405 V4 UART3:

```
F405 T3  -> ESP32 GPIO18 (RX)
F405 R3  <- ESP32 GPIO17 (TX)
F405 GND -> ESP32 GND
```

Set ArduRover:

```
SERIAL3_PROTOCOL = 2
SERIAL3_BAUD     = 115
```

The UART runs at 115200 baud.

### Mode-gated MAVLink behavior

In DIRECT mode:

```
TeleRC -> Wi-Fi -> ESP32 local controller -> BTS7960
```

TeleRC steering/drive and local ARM/DISARM terminate at the ESP32. They are not forwarded to the F405.

In AUTOPILOT mode:

```
TeleRC <-> Wi-Fi UDP <-> ESP32 <-> UART MAVLink <-> F405
                                      |
                                   M5-M8
                                      |
                                   ESP32
                                      |
                                  BTS7960
```

The ESP32 forwards complete MAVLink datagrams from the paired TeleRC endpoint to the F405 and forwards complete F405 MAVLink frames back to TeleRC.

This allows the optional F405 to provide:

- actual ArduRover heartbeat
- arm/disarm state
- GPS / GLOBAL_POSITION_INT
- mission/navigation telemetry
- other MAVLink telemetry supported by the current TeleRC apps

The motor path remains separate: F405 M5-M8 are still the AUTOPILOT motor command source, and the ESP32 continues to apply the PWM freshness checks, source arbitration, motor conversion and hard failsafe.

### Identity handling

The ESP32 sends its own minimal rover heartbeat only while DIRECT is selected.

When AUTOPILOT is selected, the local heartbeat is suppressed and TeleRC sees the F405 heartbeat forwarded over UART. This avoids two competing rover identities on the same TeleRC session.

### Safety boundary

MAVLink routing does not bypass the motor arbiter.

Even if TeleRC can communicate with the F405 over UART:

```
MAVLink traffic != motor authority
```

AUTOPILOT motor output is accepted only when:

- GPIO16 selects AUTOPILOT
- the required M5-M8 PWM inputs are valid
- those PWM inputs remain fresh
- the neutral-transfer dwell has completed

Loss of required F405 PWM still forces FAILSAFE and all BTS7960 outputs to zero.

### Combined pin map

| Function | ESP32-S3 |
|---|---:|
| F405 M5 PWM | GPIO4 |
| F405 M6 PWM | GPIO5 |
| F405 M7 PWM | GPIO6 |
| F405 M8 PWM | GPIO7 |
| BTS FL RPWM / LPWM | GPIO8 / GPIO9 |
| BTS RL RPWM / LPWM | GPIO10 / GPIO11 |
| BTS FR RPWM / LPWM | GPIO12 / GPIO13 |
| BTS RR RPWM / LPWM | GPIO14 / GPIO15 |
| DIRECT/AUTOPILOT selector | GPIO16 |
| F405 MAVLink TX -> ESP RX | GPIO18 |
| F405 MAVLink RX <- ESP TX | GPIO17 |

This remains a one-ESP32 architecture.

## Reviewed safety and communication behavior

- Each DIRECT axis has its own 500 ms freshness deadline; refreshing steering cannot preserve stale drive.
- Current Android/PC MAVLink v1 RC overrides are tracked in AUTOPILOT. After 500 ms without a fresh update to any owned channel, motors stop and the ESP32 refreshes neutral on owned FC channels at 10 Hz. Explicit channel release clears ownership and allows the receiver to take over. GCS heartbeats do not extend the control deadline.
- Wi-Fi loss during receiver control or an autonomous mission is governed by FC failsafes; the ESP32 does not seize RC ownership if TeleRC owns no channels.
- Selector edges stop motors immediately; source grant waits for 50 ms debounce and 300 ms neutral dwell.
- Explicit disconnect latches off AUTOPILOT authority. Toggle the selector to reset it, then provide neutral PWM. Reconnecting telemetry alone does not clear that latch.
- PWM captures use a critical-section snapshot. Invalid pulse widths invalidate that input; orphan falling edges are rejected.
- UART writes use a 1024-byte transmit buffer and require enough capacity for a complete datagram. Congestion stops/latches motors rather than blocking the motor loop.
- UDP datagrams are limited to 280 bytes. Oversized, short, fragmented, or structurally malformed datagrams are rejected, never routed as a truncated valid prefix.
- DIRECT accepts current apps' MAVLink v1 override and arm commands. The AUTOPILOT transport carries MAVLink v1/v2; generic FC CRC validation remains at ArduPilot. The override watchdog tracks the current apps' v1 format only. MAVLink v2 control from other clients needs an extended watchdog parser before use.
- IP leasing, sysid/component checks, and CRC are filters, not cryptographic authentication. Signed frames can be transported to the FC, but this sketch neither verifies signatures nor blocks replay.
- Successful LEDC setup is required before granting motor authority. Use external RPWM/LPWM pulldowns so reset/boot GPIO float cannot drive a motor; the software cannot stop a latched PWM peripheral if execution freezes. A hardware cutoff is required for that fault.

Host regression tests compile the actual sketch against peripheral stubs for Arduino core API branches 2 and 3. They verify control freshness, release/disarm, PWM invalidation/loss, immediate selector stops, neutral watchdog frames, UART congestion, and oversized UDP rejection. They do not validate ESP32 timing, power electronics, or the FC configuration.

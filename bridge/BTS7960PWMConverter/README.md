# TeleRC 4x BTS7960 PWM Converter

This folder contains a standalone ESP32-S3 sketch that converts standard ArduRover/flight-controller servo PWM into the two logic PWM signals required by each BTS7960/IBT-2 brushed DC motor driver.

It is intended for the current four-motor TeleRC rover architecture where each wheel motor has its own BTS7960.

> Important: this is a companion firmware, not a replacement for the existing TeleRC Wi-Fi/MAVLink bridge. If you flash this sketch onto the same ESP32-S3 that currently runs TeleRCBridge.ino, the bridge firmware will be replaced. Use a second ESP32-S3 for the converter unless the two firmwares are deliberately merged later.

## Default architecture

~~~
TeleRC Android
      |
    Wi-Fi
      |
ESP32-S3 TeleRCBridge
      |
   MAVLink UART
      |
SpeedyBee F405 V4 / ArduRover
      |
  servo PWM outputs
      |
ESP32-S3 PWM Converter
      |
  +---+---+---+---+
  |   |   |   |   |
BTS BTS BTS BTS
 FL  RL  FR  RR
  |   |   |   |
 DC motors
~~~

The default converter mode uses two FC PWM signals:

- LEFT command drives both Front Left and Rear Left.
- RIGHT command drives both Front Right and Rear Right.
- 1500 us is neutral.
- Approximately 1000 us is full reverse.
- Approximately 2000 us is full forward.

The sketch can also be switched to four independent RC inputs by setting:

~~~
constexpr bool PAIRED_SKID_STEER = false;
~~~

## ESP32-S3 pin assignment

### Flight-controller PWM inputs

| Function | ESP32-S3 GPIO | Use |
|---|---:|---|
| LEFT PWM input | GPIO 4 | Required in paired skid-steer mode |
| RIGHT PWM input | GPIO 5 | Required in paired skid-steer mode |
| Motor 2 input | GPIO 6 | Four-input mode only |
| Motor 3 input | GPIO 7 | Four-input mode only |

Connect only the FC signal wire to the GPIO. FC ground must also be connected to ESP32 ground.

An example with two available F405 PWM outputs is:

~~~
F405 configured LEFT motor output  -> ESP32 GPIO4
F405 configured RIGHT motor output -> ESP32 GPIO5
F405 GND                           -> ESP32 GND
~~~

Do not assume an FC output number is LEFT or RIGHT until its ArduRover SERVOx_FUNCTION/motor assignment has been verified. Test the output in Mission Planner with motor power disconnected.

### BTS7960 outputs

| Wheel / driver | RPWM | LPWM |
|---|---:|---:|
| Front Left | GPIO 8 | GPIO 9 |
| Rear Left | GPIO 10 | GPIO 11 |
| Front Right | GPIO 12 | GPIO 13 |
| Rear Right | GPIO 14 | GPIO 15 |

Each BTS7960 receives one RPWM/LPWM pair.

## BTS7960 logic wiring

For each BTS7960:

~~~
ESP32 assigned RPWM ----> BTS7960 RPWM
ESP32 assigned LPWM ----> BTS7960 LPWM

Regulated 5 V ----------> BTS7960 VCC
Regulated 5 V ----------> BTS7960 R_EN
Regulated 5 V ----------> BTS7960 L_EN

Common GND -------------> BTS7960 GND

Motor battery + --------> BTS7960 B+
Motor battery - --------> BTS7960 B-
Motor ------------------> BTS7960 M+ / M-
~~~

R_IS and L_IS may be left unused for this version.

### Common ground

The logic-side grounds must share the same reference:

~~~
SpeedyBee F405 GND
        |
ESP32-S3 GND
        |
BTS7960 #1 GND
BTS7960 #2 GND
BTS7960 #3 GND
BTS7960 #4 GND
~~~

Do not route motor current through the FC or ESP32 ground wiring. The common ground connection is a signal reference; high-current motor return paths should go through the motor power distribution wiring.

## Power wiring

Recommended high-level power layout:

~~~
24 V battery
     |
 main fuse / disconnect
     |
 power distribution
  |    |    |    |
 BTS1 BTS2 BTS3 BTS4
  |    |    |    |
 FL   RL   FR   RR motors

24 V battery
     |
 regulated buck converter
     |
 5 V logic rail
  |          |
ESP32-S3   BTS logic VCC/EN
~~~

Never connect a 24 V motor battery directly to the ESP32-S3 5 V/VIN pin unless a suitable regulator is in between.

For higher-power motors, give each BTS7960 adequate cooling and use wire, connectors, fuses and distribution hardware sized for measured current. Motor stall current can be several times normal running current and should be treated as the worst-case sizing condition.

## Software safety behavior

The converter implements:

- valid RC pulse window of 850-2150 us
- calibrated command range of 1000-2000 us
- 1500 us center
- +/-35 us neutral deadband
- 150 ms signal timeout
- all-stop on missing paired input
- acceleration slew limiting
- faster deceleration toward neutral
- mandatory zero crossing before motor reversal
- 20 kHz motor PWM
- 115200-baud serial diagnostics

If either LEFT or RIGHT input disappears in the default paired mode, all four motor targets are set to zero.

This converter is an additional safety layer, not the rover's only failsafe. Keep ArduRover failsafes, RC override timeout, transmitter failsafe and a physical motor-power disconnect.

## First bench test

1. Raise all four wheels off the ground.
2. Disconnect motor battery power while checking logic wiring.
3. In the sketch, temporarily change MAX_MOTOR_DUTY from 255 to 140.
4. Upload the sketch to the dedicated ESP32-S3.
5. Open Serial Monitor at 115200 baud.
6. Confirm both input channels are near 1500 us at neutral.
7. Confirm losing either FC PWM signal causes command values to return to zero.
8. Apply motor power.
9. Move forward slowly and verify all wheels rotate in the correct rover direction.
10. Test reverse and steering at low duty.
11. Verify signal-loss stop behavior.
12. Only after successful bench tests, increase MAX_MOTOR_DUTY gradually.

## Motor direction correction

If a wheel rotates opposite the intended direction, correct the physical wiring or change its entry in INVERT_MOTOR.

Example: invert both right-side motors:

~~~
constexpr bool INVERT_MOTOR[4] = {
  false,  // Front Left
  false,  // Rear Left
  true,   // Front Right
  true    // Rear Right
};
~~~

Motor index order is:

~~~
0 = Front Left
1 = Rear Left
2 = Front Right
3 = Rear Right
~~~

## Diagnostics

Serial output is printed every 250 ms:

~~~
RC(us): 1500* 1500* 1500! 1500! | CMD: 0 0 0 0
~~~

An asterisk means the input has a fresh valid pulse. An exclamation mark means that input is stale/not valid.

In paired mode, GPIO6/GPIO7 are not required, so their stale indicators can be ignored.

## File

Open TeleRC_4x_BTS7960_PWM_Converter.ino in Arduino IDE and select the correct ESP32-S3 board definition before compiling/uploading.

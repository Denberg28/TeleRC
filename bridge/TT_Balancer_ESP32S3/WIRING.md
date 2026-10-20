# Wiring and power — TeleRC Balancer v0.2.5

Pin allocation is unchanged from v0.1.0.

## Power — do this before uploading

Assumption: standard 3–6 V TT motors. Check the actual motor specification.
A 2S lithium pack reaches 8.4 V when full. Do not connect it directly to these
motors or the ESP32-S3 power pins. PWM capping does not reduce pulse voltage or
provide current protection.

Use two separately regulated branches from the battery:

| From | To | Notes |
| --- | --- | --- |
| 2S positive | Fuse, master switch, then both buck inputs | Fuse/wire rating must match the measured load and wiring. |
| Motor buck output, regulated **5.0 V** | MX1508 motor supply `+`/`VCC` | Size for both motors' combined starting/stall load; verify the driver also supports it. |
| Logic buck output, regulated **5.0 V** | ESP32-S3 board `5V` input | Use a DevKit with a documented regulated 5 V input. Never raw 2S. |
| Battery negative / buck negatives | Common GND | Join grounds at a power distribution point; avoid sending motor return current through ESP32/IMU wires. |
| ESP32-S3 `3V3` | MPU breakout `VCC` | Only a breakout that works from 3.3 V. Verify actual 3.3 V at MPU VDD; some clone regulator boards need a different arrangement. |

For initial small-motor benches, a motor buck genuinely capable of ~3 A is a
reasonable starting size; calculate from your actual motors, not this label.
Use a separate ~1 A or higher logic regulator. Extra supply capacity does not
increase the MX1508 current rating. Do not parallel the regulators' outputs.
Keep battery/regulated 5 V off USB VBUS unless your board explicitly supports
simultaneous power sources. Safest upload procedure: battery disconnected,
USB connected; then unplug USB before switching to regulated battery power.
For live Serial testing with battery, use a correctly isolated/data-only USB
arrangement supported by your DevKit; do not cut or improvise a cable while powered.

The linked MX1508 IC datasheet lists 2–9.6 V supply and asymmetric channel limits:
0.8 A continuous/1.5 A peak for one bridge and 1.5 A continuous/2 A peak for the
other. Clone modules may differ. Do not assume 1.5 A continuous on both channels.
Some TT motors exceed these limits at stall (the example DFRobot TT motor lists
2.8 A). Verify motor stall specifications at your chosen voltage before driving
the pair. If they exceed the actual driver's per-channel ratings, change the
driver to one with sufficient continuous/peak current and current limiting.
Software cannot measure current or driver temperature with this wiring.

Place a 470–1000 µF electrolytic (rated at least 10 V) across the 5 V motor input,
close to the driver, and a 100 nF ceramic across each motor's terminals.
Check buck manufacturer guidance on output capacitance. Use short motor power
wires, short twisted I2C wiring, and a rigid IMU mounting away from motor vibration.
Use a protected/balanced 2S pack and appropriate charger. There is no battery
voltage measurement or low-voltage cutoff in this sketch; use pack protection
and a separate cell-voltage alarm.

## Signal wiring

Pins below are GPIO numbers on a generic ESP32-S3 DevKit. Check your exact board
exposes them and does not use them for built-in peripherals.

| ESP32-S3 | Connect to |
| --- | --- |
| GPIO4 | MX1508 IN1, left motor bridge input 1 |
| GPIO5 | MX1508 IN2, left motor bridge input 2 |
| GPIO6 | MX1508 IN3, right motor bridge input 1 |
| GPIO7 | MX1508 IN4, right motor bridge input 2 |
| GPIO8 | MPU-6050 SDA |
| GPIO9 | MPU-6050 SCL |
| GPIO12 | Normally-open stop pushbutton, other terminal to GND |
| 3V3 | Compatible MPU-6050 breakout VCC |
| GND | MX1508 GND, MPU GND, regulator grounds |

| Other connection | Purpose |
| --- | --- |
| MX1508 OUT1/OUT2 | Left motor terminals |
| MX1508 OUT3/OUT4 | Right motor terminals |
| MPU AD0 → GND | I2C address 0x68 |
| MPU INT | Leave disconnected; firmware polls DATA_RDY status |
| MPU XDA/XCL | Leave disconnected |
| Each MX1508 IN pin → GND through **10 kΩ** | Holds motors off during boot/reset when ESP32 pins float |

Module silkscreen varies: identify each input/output pair from your module's
pinout, rather than its physical position. MPU I2C pull-ups must go to **3.3 V**,
never 5 V. If none are fitted, add approximately 4.7 kΩ SDA→3V3 and SCL→3V3.

The GPIO12 switch is a software stop. Pressing it latches FAULT until reset;
an unwired or broken switch wire is not detected. A reachable physical motor
power switch is the independent stop for a software hang. Cut motor power
before picking up or adjusting the robot.

## Mechanics and MPU orientation

Use two wheels on the same axle line and a rigid upright chassis, with its
center of mass above that line. No fixed third wheel should touch the ground
in normal balancing. Use a loose test support/tether that catches a fall without
constraining normal fore/aft motion. Keep hands away from spinning wheels.

Mount MPU axes: **+X toward robot front, +Y toward left wheel, +Z upward**.
Read the axis arrows on the sensor breakout, not the text orientation.
The code uses `atan2(-ax, az)` and positive gyro-Y for forward tilt.
The board should read angle near 0° upright; tilt its top forward to read a
positive angle, backward for negative. During forward rotation, `rate` should
be positive. If mounting differs, remount; changing a motor sign cannot fix a
sensor-axis mismatch. Avoid attaching the MPU with soft foam that lets it wobble.


## References

- MX1508 manufacturer datasheet, hosted copy: https://uelectronics.com/wp-content/uploads/2018/04/MX1508_Datasheet.pdf
- Example TT motor, not necessarily your model: https://www.dfrobot.com/product-100.html
- Espressif LEDC: https://docs.espressif.com/projects/arduino-esp32/en/latest/api/ledc.html
- Espressif Wi-Fi: https://docs.espressif.com/projects/arduino-esp32/en/latest/api/wifi.html
- Espressif I2C: https://docs.espressif.com/projects/arduino-esp32/en/latest/api/i2c.html

Firmware v0.2.5 probes 0x68 and 0x69 during boot and chooses a responding
MPU6050 with WHO_AM_I=0x68 or MPU6500 with WHO_AM_I=0x70. Keep AD0 at a defined level; discovery is not a
replacement for a solid AD0/GND connection. Runtime failures remain latched and
never switch addresses or reset the IMU while balancing.

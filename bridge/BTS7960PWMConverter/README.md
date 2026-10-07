# ESP32-S3 four-BTS7960 PWM converter

This companion converts flight-controller servo PWM into four RPWM/LPWM driver pairs. For joystick commands from a rover LILYGO T3-S3, flash [TeleRCMotorController](../TeleRCMotorController/README.md) instead: that sketch accepts the gateway UART, preserves CH1 steering/CH2 drive, and requires explicit DIRECT ARM. The PWM-only converter has no serial control parser, radio or DIRECT ARM state.

The PWM-only path is FC servo outputs → this ESP32-S3 → four BTS7960 drivers. Keep it on a separate MCU from a gateway. Flashing it onto the dedicated motor MCU replaces its UART command functionality.

## Build and input selection

Open the **BTS7960PWMConverter** folder as an Arduino sketch. `BTS7960PWMConverter.ino` is the required folder entrypoint; Arduino also compiles the original `TeleRC_4x_BTS7960_PWM_Converter.ino` in that folder. Do not include the original file again.

Default `TELERC_PWM_PAIRED=1`: GPIO4 is LEFT for FL/RL; GPIO5 is RIGHT for FR/RR. Set `TELERC_PWM_PAIRED=0` before the default definition, or pass the compiler flag below, for independent FL/RL/FR/RR inputs on GPIO4/5/6/7. Four-input mode has `FAILSAFE_ALL_STOP=true` by default.

```bash
arduino-cli compile --fqbn esp32:esp32:esp32s3:CDCOnBoot=cdc bridge/BTS7960PWMConverter
arduino-cli compile --fqbn esp32:esp32:esp32s3:CDCOnBoot=cdc --build-property 'compiler.cpp.extra_flags=-DTELERC_PWM_PAIRED=0' bridge/BTS7960PWMConverter
```

CI uses ESP32 core 3.3.2. Host regressions also exercise the core 2 LEDC API branch. Choose the actual carrier's flash/PSRAM settings for uploading.

## Wiring

| Connection | ESP32-S3 GPIO / destination |
| --- | --- |
| FC LEFT / independent FL PWM | GPIO4 |
| FC RIGHT / independent RL PWM | GPIO5 |
| Independent FR PWM | GPIO6 (four-input mode) |
| Independent RR PWM | GPIO7 (four-input mode) |
| FL RPWM / LPWM | GPIO8 / GPIO9 |
| RL RPWM / LPWM | GPIO10 / GPIO11 |
| FR RPWM / LPWM | GPIO12 / GPIO13 |
| RR RPWM / LPWM | GPIO14 / GPIO15 |
| FC and all driver logic GND | Common ESP32 signal ground |

Verify the FC's actual SERVOx_FUNCTION assignments before connecting; GPIO4/5 are already mixed LEFT/RIGHT commands in paired mode, rather than raw steering/throttle. Only connect signal levels suitable for ESP32 3.3 V GPIO. Do not feed 5 V into an input.

Retain the [hybrid wiring guide](../TeleRCHybridController/README.md) and installation checks for regulated logic power, driver VCC/enable levels, external PWM pulldowns, common signal ground, motor distribution, fusing and physical battery cutoff. Motor current must not return through the MCU/FC signal wiring. R_IS/L_IS are unused by this firmware. GPIO-low initialization begins in `setup()`; external pulldowns and a physical power cutoff are still required for reset, unpowered MCU or CPU/peripheral faults.

## Software behavior

- Accept 850–2150 µs pulses with a witnessed rising edge; commands clamp to 1000–2000 µs with 1500 µs center and ±35 µs deadband.
- Capture width, timestamp and validity atomically. Invalid or orphan falling edges invalidate that channel immediately.
- Boot and signal-loss recovery wait for **300 ms of fresh neutral required inputs**. A displaced input cannot start or resume motion. PWM authority is enabled after neutral dwell; there is no separate ARM input in this companion.
- If a required input becomes invalid or reaches the **150 ms** freshness deadline, zero actual output/current duty at the next 5 ms control iteration. Fault stops bypass slew; normal command changes retain acceleration/deceleration slew and mandatory zero crossing before reversal.
- Both directions are zero before startup delays. A failed LEDC attachment inhibits motion. Outputs use eight LEDC channels at 20 kHz / 8 bit.
- Diagnostics attempt one bounded message every 250 ms, dropping it when serial capacity is insufficient. `*` means fresh; `!` means invalid/stale. GPIO6/7 are unused in paired mode. `source=neutral-wait` or `PWM=failed` identifies inhibition.

All-stop is the supported/tested default. Changing `FAILSAFE_ALL_STOP` changes four-input fault policy and requires separate bench acceptance. This firmware's zero PWM is a software output state, not a measurement of braking, coast time or stopped wheels.

## Restrained-wheel acceptance

See [the review and executable bench procedure](../../docs/LORA_MOTOR_REVIEW.md). Check logic first with motor battery disconnected. Raise/restrain wheels, reduce `MAX_MOTOR_DUTY` for powered tests, and provide an accessible physical cutoff. Verify neutral boot, displaced boot inhibition, all four directions, reversal, every required PWM wire loss, neutral-only recovery, reset and serial backpressure. Correct wheel direction through wiring or `INVERT_MOTOR` in order FL, RL, FR, RR. Hardware timing and electrical compatibility remain unmeasured until those checks are recorded.

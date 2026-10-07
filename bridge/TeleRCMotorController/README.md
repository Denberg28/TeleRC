# Dedicated ESP32-S3 motor controller

Flash `TeleRCMotorController.ino` to the existing motor ESP32-S3. Install `bridge/libraries/TeleRCLink` first. No Wi-Fi is started. The motor/PWM/selector/F405 pins retain the hybrid mapping. Communications UART is RX GPIO21 / TX GPIO39 at 115200 baud.

Read [the full integration and connection guide](../LORA_INTEGRATION.md) before configuring or powering motors. The firmware is for bench validation, not a verified field release.

This is the correct motor sketch for joystick commands arriving from a rover LILYGO T3-S3. The separate `BTS7960PWMConverter` accepts only FC servo pulses and cannot decode LoRa gateway UART commands. Keep this motor MCU separate from the BASE and ROVER radios. See [the four-driver review](../../docs/LORA_MOTOR_REVIEW.md) for wiring, software findings and restrained-wheel acceptance tests.

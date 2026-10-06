# TeleRC 0.8.52 sketch bundle

This ZIP contains matching source sketches, shared libraries and wiring guides. Select the sketch for each physical board; the dedicated motor sketch requires the included TeleRCLink library. The APK and sketch bundle share a release commit recorded in `BUILD_INFO.txt`.

## Arduino IDE installation

1. Extract the complete bridge ZIP. Install Espressif's ESP32 board package **3.3.2** in Boards Manager. Select the actual ESP32-S3 board and enable **USB CDC On Boot** when using native USB.
2. Copy `bridge/libraries/TeleRCLink` into your Arduino sketchbook's `libraries/TeleRCLink` folder. On a default Windows setup this is `Documents/Arduino/libraries/TeleRCLink`. The copied folder must contain `library.properties` and `src`. Restart Arduino IDE. Do not upload a thin `.ino` without its library.
3. For LoRa BASE/ROVER only, install **RadioLib 7.2.1** from Library Manager. Match the compiled radio variant to the actual T3-S3 radio chip: default SX1262; select the documented SX1276 build for that hardware. Other variants need a separate profile and are unsupported.
4. Open the selected sketch's own folder and `.ino`, choose the correct USB port, and upload with motor power disconnected. Retain board-specific flash/NVS settings; no credential is bundled.

## Select each sketch

| Board / setup | Sketch |
| --- | --- |
| Fixed ESP32-S3 generating four BTS7960 motor outputs | `TeleRCMotorController/TeleRCMotorController.ino` |
| Swappable Wi-Fi ESP32-S3 command module | `TeleRCWiFiGateway/TeleRCWiFiGateway.ino` |
| LoRa rover T3-S3 command module | `TeleRCLoRaRover/TeleRCLoRaRover.ino` |
| LoRa base T3-S3 attached to Android by native USB OTG | `TeleRCLoRaBase/TeleRCLoRaBase.ino` |
| Legacy single ESP32-S3 hybrid motor/FC arrangement | `TeleRCHybridController/TeleRCHybridController.ino` |
| Legacy ESP32-S3 Wi-Fi transport to an F405 that owns motor outputs | `TeleRCBridge/TeleRCBridge.ino` |

The release workflow compiles these six targets and alternate SX1276 BASE/ROVER builds. Other retained sketches in the archive are historical options and are outside that actual-target build set.

## Connect and validate

Use `SWAPPABLE_GATEWAYS.md` for the keyed UART harness, supply and 3.3 V logic requirements. For LoRa, use `LORA_INTEGRATION.md` and the APK's LoRa setup tab to provision both roles with the same key/profile, then restart. Use only a locally permitted radio configuration.

For Wi-Fi, obtain the board's generated password from its local Serial Monitor, join TeleRC-Rover, select Wi-Fi UDP, and connect to 192.168.4.1:14550. For LoRa, select the USB base transport after provisioning. Wait for actual motor/FC heartbeat, enable control, and ARM from neutral for DIRECT operation. CH1 is steering, CH2 drive; release centers both. Following heartbeat timeout, a recovered link requires Enable Control again; DIRECT failsafe recovery additionally requires explicit ARM.

Start with raised wheels and reduced sensitivity. Verify direction, release, STOP/receiver takeover, disconnect, tab/background behavior, source-selector changes, command/FC-PWM loss and power/reset before loaded testing. Tests and wiring details are included in the integration guides and repository `docs/TESTING.md`. This is a software-verified test candidate; physical timing, installed-device behavior and field performance remain unverified.

# TeleRC — TeleRC Go Wi-Fi controller + Mission Planning

TeleRC adopts **TeleRC Go**'s clean Wi-Fi connect-and-drive controller (dark navy wallpaper UI, translucent proportional CH1/CH2 joysticks, 50 Hz RC cadence, single ARM/DISARM flow) and adds **only** the **Mission** planning tab with acknowledged ArduRover Wi-Fi waypoint upload via the ESP32-S3 `TeleRCBridge` firmware.

Signed test candidate: [TeleRC v1.2.0rc4 APK](https://github.com/Denberg28/TeleRC/releases/download/v1.2.0rc4/TeleRC-v1.2.0rc4.apk) · [matched firmware, source and checksums](https://github.com/Denberg28/TeleRC/releases/tag/v1.2.0rc4). Device/HIL validation remains pending.

## Use

- **Controls (TeleRC Go):** Join rover Wi-Fi (`TeleRC-Rover`, default `192.168.4.1:14550`), tap **Phone connect**, wait for a verified rover heartbeat, center sticks, and tap **ARM** (for ArduRover, ensure Switch A / CH7 > 1800 µs and `MANUAL` mode). Large translucent proportional sticks drive CH1 steering and CH2 drive at 50 Hz (20 ms) with `GO_RECEIVER_DEADBAND_US = 35`. Tap **DISARM** to stop/disarm or **Disconnect** to close the link.
- **Mission (Wi-Fi via ESP32-S3):** Tap **Mission** in the header (automatically neutralizes/stops driving while keeping the Wi-Fi link connected). Tap the map or **Add** to enter coordinates; tap any list row to edit, move earlier/later, or delete. Plans autosave locally (`mission.csv`). Import/Export uses `sequence,latitude,longitude` CSV (up to 100 waypoints). Planned routes are orange, phone tracks blue, measured rover GPS (`GLOBAL_POSITION_INT`) green. Tap **Upload Wi-Fi** to replace the onboard mission on a **disarmed** ArduRover through the matching `TeleRCBridge` ESP32-S3 firmware with fresh heartbeat and Switch A (CH7 > 1800 µs) permission. Upload never arms or starts navigation; verify the onboard route and select `AUTO` on your transmitter.
- **Settings:** Tap **Settings** for Wi-Fi settings, Connection details (IPv4, rover port, phone port, system ID), independent Steering/Drive Sensitivity (25–100%), and custom Background wallpaper. Tap the top-left `TeleRC v...` version text to check updates.

## Hardware schematic & HGLRC M10 GPS

Read [`docs/GPS_HGLRC_M10_SCHEMATIC.md`](docs/GPS_HGLRC_M10_SCHEMATIC.md) and [`ardurover/params/ardurover_f405v4_hglrc_m10_esp32s3_wifi.param`](ardurover/params/ardurover_f405v4_hglrc_m10_esp32s3_wifi.param) for:
- Exact pin-by-pin wiring of **HGLRC M10 / M100-5883 / M100 Pro** to **SpeedyBee F405 V4** (`UART6 T6/R6` crossed + `4V5`/GND + `I2C1 SDA/SCL` straight for QMC5883L compass).
- **ESP32-S3 `TeleRCBridge`** wiring to SpeedyBee F405 V4 (`UART3 T3/R3` ↔ `GPIO18/17`, 115200 baud) and firmware selection.
- Fixes for the legacy ArduRover `.param` snapshot (`RC7_OPTION=46` + `MODE_CH=6` and `COMPASS_EXTERNAL=1` / `COMPASS_USE=1`).

## Scope & upgrade continuity

This build removes the offline maze/game, LoRa USB tab, Setup tab clutter, simulated positions, and external servo buttons. Application ID remains `io.github.denberg28.telerc` with the retained signing certificate and increasing `versionCode` (`1203`). Existing `mission.csv`, `route-session.csv`, sensitivity sliders, and legacy Wi-Fi host/port preferences are preserved on upgrade. No new runtime dependency is introduced.

## Build and validation

JDK 17, Android SDK 35, Build Tools 35.0.0 and the pinned Gradle 8.11.1 wrapper are required:

```sh
./scripts/check-android-env.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug --no-daemon
python3 bridge/sync_motor_core.py --check
python3 -m unittest discover -s scripts -p 'test_firmware_package.py'
bridge/tests/run.sh
SANITIZE=1 bridge/tests/run.sh
```

Release signing, debug/release builds, actual ESP32-S3 compilation (10 firmware targets), checksums, and packaging run in `.github/workflows/release.yml` using existing secrets. Read [`UPDATES.md`](UPDATES.md), [`docs/TESTING.md`](docs/TESTING.md), [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`docs/KNOWN_ISSUES.md`](docs/KNOWN_ISSUES.md), [`docs/GPS_HGLRC_M10_SCHEMATIC.md`](docs/GPS_HGLRC_M10_SCHEMATIC.md), and [`docs/CODE_EDIT_GUIDE.md`](docs/CODE_EDIT_GUIDE.md).

## Test candidate notice & rollback

**Test candidate only:** physical safe states, Wi-Fi reliability, HGLRC M10 GPS/compass lock, installation/upgrade, and full vehicle behavior require restrained-wheel bench validation. Reconnect/background/tab transitions never automatically enable or arm. Wi-Fi MAVLink is endpoint/CRC checked, not cryptographically signed. Retain signed `v1.2.0rc3` (`versionCode 1202`) and export `mission.csv` before any downgrade.

# TeleRC · Mini Rover Wi-Fi edition

Android 0.8.55 and ESP32-S3 firmware 1.2.0 provide direct Wi-Fi rover control, confirmed arm/disarm, independent steering/drive sensitivity, and failsafe disarm. No receiver or flight controller is required.

Read [setup, wiring, limits and bench tests](docs/MINI_WIFI_ONLY.md). The APK is intentionally Wi-Fi-only; multi-link 0.8.53 remains the rollback baseline. Physical tests remain required before stable promotion.

The active sketch is `bridge/TeleRC_MiniRover_WiFi/`. Other bridge folders are retained legacy code and are not part of the mini rover setup. Builds use `.github/workflows/mini-wifi-build.yml`. Author: md. MIT license.

# TeleRC 1.2.0rc4 status — 24 November 2026

- **Goal & scope:** Adopt **TeleRC Go** (`Denberg28/telerc_go` `go-v1.0.12`) into `Denberg28/TeleRC` (`io.github.denberg28.telerc`) and add **only** the **Mission** planning tab over Wi-Fi via ESP32-S3 (`TeleRCBridge`); recheck firmware & schematic for **HGLRC M10** GPS installation; review features, sanitize, push, and build signed APK prerelease `v1.2.0rc4` (`versionCode 1203`).
- **Baseline:** `main` commit `b4f7ba9508ee62d134e69b34da1711a8deb5a39e` (published signed `v1.2.0rc3`, code `1202`).
- **Completed changes:**
  - Adopted TeleRC Go Controls UI, dark wallpaper (`go_background.jpg` + custom image import), translucent proportional CH1/CH2 joysticks, 20 ms (50 Hz) Wi-Fi UDP `GoLink`, `GoControl`, `GoProtocol`, `GoElrs`, `GoStopPolicy`, and compact 2×2 Settings dialog.
  - Added only the Mission planning tab (`MissionPlannerView`, `RouteMapView`, `MissionPlan`, `MissionTransfer`, `RouteSession`) wired into `GoLink` (`GoMissionGate`, `GLOBAL_POSITION_INT` rover GPS request/parsing, acknowledged disarmed CH7-gated Wi-Fi upload).
  - Removed obsolete non-Go Android files/tests (LoRa USB UI/transport, old Setup/gates), removed junk `ardurover/params/sample`, and sanitized hardcoded personal Wi-Fi password `"alongwayhome"` + double-escaped `\n` in `bridge/TeleRCBridge/TeleRCBridge.ino`.
  - Added verified HGLRC M10 GPS & ESP32-S3 schematic (`docs/GPS_HGLRC_M10_SCHEMATIC.md`) and ArduRover parameter overlay (`ardurover/params/ardurover_f405v4_hglrc_m10_esp32s3_wifi.param`) addressing the legacy `MODE_CH=7`/`RC6_OPTION=46` and disabled external compass issues.
- **Local verification (PASS):**
  - OpenJDK 17 + SDK 35 + Build Tools 35.0.0 (`./scripts/check-android-env.sh`): PASS
  - `./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug --no-daemon`: PASS (`66` JVM tests, `0` errors / `22` warnings in debug & release lint)
  - `python3 bridge/sync_motor_core.py --check`, `test_firmware_package.py`, `bridge/tests/run.sh`, `SANITIZE=1 bridge/tests/run.sh`: PASS
- **Next step:** Push review branch `feat/adopt-telerc-go-wifi-mission`, open PR, confirm GitHub Actions (`Android checks` + `ESP32-S3 bridge compile`) pass, merge to `main` to run `Signed APK release` for `v1.2.0rc4`, and verify published release assets/checksums.
- **Rollback:** Retain signed `v1.2.0rc3` (`versionCode 1202`) APK and matching firmware; export `mission.csv` before any uninstall required for Android versionCode downgrade.

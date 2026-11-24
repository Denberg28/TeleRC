# Targeted code edits

| Desired result | File | Section/symbol | Edit and units/scope | Related settings and check |
| --- | --- | --- | --- | --- |
| Change navigation / screens | `MainActivity.kt` | `UI — Root shell`, `Page`, `switchTo`, `renderControls`, `renderMission` | Two screens only: Controls (Go) and Mission; switching stops driving / cancels upload | Verify tab switch, Back key, and orientation/lifecycle |
| Change colors / dark surfaces | `AppColors.kt`, `AppUi.kt`, `BackgroundTheme.kt` | `UI — Theme surfaces and accent palette`, `UI — Shared action buttons` | Dark navy RGB surfaces, button fills, disabled contrast | Verify contrast over wallpaper and in Mission sidebar |
| Change Go joystick scale / dial | `JoystickView.kt`, `JoystickMath.kt`, `ControlResponse.kt` | `UI — Go translucent dial`, `Control — Go proportional mapping` | Travel `radius * 0.62f`, 8% neutral guard, `GO_RECEIVER_DEADBAND_US = 35` | `GoProportionalResponseTest`, `JoystickMathTest`, restrained-wheel HIL |
| Change Wi-Fi transport / ARM / CH7 | `GoLink.kt`, `GoControl.kt`, `GoProtocol.kt` | `Connection — Wi-Fi UDP transport`, `Control — Authority gate`, `GoMissionGate` | 20 ms RC loop, 1500 ms HB freshness, 300 ms neutral dwell, 750 ms CH7 freshness | `GoControlTest`, `GoStopPolicyTest`, restrained-wheel HIL |
| Change Wi-Fi mission upload timing | `MissionTransfer.kt` | `Mission — Retry policy`, `MissionWire` | 1500 ms retry, 3 retries/step, 120000 ms overall deadline | `MissionTransferTest`, `field_protocol_test.cpp`, FC HIL |
| Adjust mission editor layout | `MissionPlannerView.kt` | `UI — Mission planner view`, `UI — Planner spacing` | Map:list 3:2, 48 dp action rows, dark surface sidebar | List scroll, map tap, coordinate validation |
| Adjust plan bounds / CSV | `MissionPlan.kt` | `Mission — Bounds and CSV schema` | 100 points, 16,384 characters, decimal degrees | `MissionPlanTest`; keep CSV backward compatibility |
| Change wallpaper import limits | `ThemeImagePolicy.kt`, `ThemeImageStore.kt` | `MAX_BYTES`, `MAX_EDGE`, `importImage` | 20 MB cap, 1920 px max edge, atomic private JPEG | `ThemeImagePolicyTest` |
| Change ESP32-S3 FC bridge AP / UART | `bridge/TeleRCBridge/TeleRCBridge.ino`, `FieldProtocol.h` | `FC_RX_GPIO`, `FC_TX_GPIO`, `PERSONAL_AP_PASSWORD`, `selectApPassword` | RX GPIO18 / TX GPIO17, 115200 baud; keep `PERSONAL_AP_PASSWORD` empty in Git | `field_protocol_test.cpp`, ESP32-S3 compile CI |
| Check HGLRC M10 GPS & F405 wiring | `docs/GPS_HGLRC_M10_SCHEMATIC.md`, `ardurover/params/ardurover_f405v4_hglrc_m10_esp32s3_wifi.param` | UART6 `T6`/`R6`, `I2C1 SDA/SCL`, `RC7_OPTION=46` | Crossed UART6, straight I2C1, `4V5`/GND, `MODE_CH=6` | Mission Planner GPS/compass/CH7 bench check |

Kotlin files live in `app/src/main/java/io/github/denberg28/telerc/`. Rules live in `AGENTS.md`; test evidence in `docs/TESTING.md`. Do not tune control/watchdog/electrical limits from comments alone.

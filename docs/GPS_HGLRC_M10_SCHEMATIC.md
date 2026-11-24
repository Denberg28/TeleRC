# HGLRC M10 GPS & ESP32-S3 Wi-Fi Mission Planning Schematic (SpeedyBee F405 V4 + ArduRover)

This guide covers the verified wiring, ESP32-S3 firmware selection, and ArduRover parameter audit for running **TeleRC (Go UI + Wi-Fi Mission Planning)** with a **SpeedyBee F405 V4** flight controller and an **HGLRC M10** family GPS module (4-pin M10 Mini or 6-pin M100 / M100-5883 / M100 Pro with QMC5883L compass).

## 1. System architecture (why GPS connects to the F405 V4, not the ESP32-S3)

```
+-----------------------------+          Wi-Fi UDP 14550          +-------------------------------+
|  Android Phone (TeleRC)     | <===============================> |  ESP32-S3 (TeleRCBridge.ino)  |
|  - Controls (Go 50 Hz)      |    AP: TeleRC-Rover (192.168.4.1) |  - GPIO18 (RX) <- F405 T3     |
|  - Mission (Wi-Fi upload)   |                                   |  - GPIO17 (TX) -> F405 R3     |
+-----------------------------+                                   +---------------+---------------+
                                                                                  | UART3 (115200, 3.3V)
                                                                                  v
+-----------------------------+   UART6 (115200, 3.3V) + I2C1     +-------------------------------+
|  HGLRC M10 / M100-5883 GPS  | <===============================> |  SpeedyBee F405 V4 (ArduRover)|
|  - TX -> R6, RX -> T6       |    4V5 + GND (+ SDA/SCL compass)  |  - Runs EKF3 & AUTO missions  |
|  - Optional QMC5883L on I2C |                                   |  - Emits GLOBAL_POSITION_INT  |
+-----------------------------+                                   |  - Enforces RC7_OPTION=46     |
                                                                  +---------------+---------------+
                                                                                  | M5-M8 PWM (3.3V)
                                                                                  v
                                                                  +-------------------------------+
                                                                  |  Motor Stage                  |
                                                                  |  - BTS7960PWMConverter ESP32  |
                                                                  |    (GPIO4-7 -> 4x BTS7960)    |
                                                                  |    OR direct bidirectional ESC|
                                                                  +-------------------------------+
```

- **ArduRover on the SpeedyBee F405 V4 owns GPS, EKF3 navigation, and AUTO mission execution.** Wiring the HGLRC M10 directly to the F405 V4 ensures ArduRover has direct 10 Hz / 5 Hz u-blox position fixes and streams `GLOBAL_POSITION_INT` over UART3 → ESP32-S3 → TeleRC's Mission map.
- **Do not wire the HGLRC M10 to the ESP32-S3 bridge.** `TeleRCBridge` is a stateless MAVLink transport and permission filter; it does not run an EKF or autonomous waypoint controller.

## 2. Necessary ESP32-S3 firmware selection

| Hardware role | Sketch / release binary target | Supports Wi-Fi Mission Upload? | Notes |
| --- | --- | --- | --- |
| **ArduRover Wi-Fi bridge ESP32-S3** | `bridge/TeleRCBridge/TeleRCBridge.ino` (`legacy-bridge`) | **YES** (disarmed + CH7 > 1800 µs) | Required for TeleRC Mission tab Wi-Fi upload & `GLOBAL_POSITION_INT` forwarding. Flash with ESP32 core 3.3.2. |
| **Motor PWM converter ESP32-S3** (if driving 4× BTS7960 from F405 M5–M8) | `bridge/BTS7960PWMConverter/BTS7960PWMConverter.ino` (`pwm-converter-paired` or `pwm-converter-four-input`) | N/A (PWM-only motor stage) | Separate board from `TeleRCBridge`; converts F405 M5–M8 PWM to 4× BTS7960 RPWM/LPWM with 150 ms loss failsafe. |
| **Dedicated Wi-Fi gateway + Motor MCU** (`TeleRCWiFiGateway` + `TeleRCMotorController`) | `wifi-gateway` + `motor-esp32s3` | **NO** (manual control only) | Framed UART command mailbox drops mission frames; use `TeleRCBridge` on F405 UART3 for mission upload. |
| **Standalone Mini / Go 4WD rover** (no F405 FC) | `TeleRC_Go_4WD` / `TeleRC_MiniRover_WiFi` (from `telerc_go`) | **NO** (local Mission editor only) | No ArduRover FC onboard; TeleRC drives manually over Wi-Fi and keeps Mission editing local. |

> **Security note (sanitized in v1.2.0rc4):** `TeleRCBridge.ino` now keeps `PERSONAL_AP_PASSWORD` empty in Git. Leave it empty to use a board-unique password printed on USB Serial Monitor (115200 baud, `TR-XXXX-XXXXXXXX`), or set a 12–63 character password locally before flashing without committing it.

## 3. HGLRC M10 → SpeedyBee F405 V4 pin-by-pin installation

Pin assignments below are verified against ArduPilot's official `libraries/AP_HAL_ChibiOS/hwdef/speedybeef4v4/hwdef.dat`.

### A. 6-pin HGLRC M100 / M100-5883 / M100 Pro (GPS + QMC5883L compass — recommended for ArduRover)

| HGLRC M100 Pin | Signal | SpeedyBee F405 V4 Pad | STM32 Pin / Bus | Rule |
| --- | --- | --- | --- | --- |
| `5V` (Red) | 3.7–5.0 V power | **`4V5`** (preferred) or `5V` | 4.5 V / 5 V BEC (up to 1 A) | `4V5` is powered on BOTH USB and battery for bench GPS testing. **Never connect to `9V` or `BAT`.** |
| `GND` (Black) | Ground | **`G` / `GND`** | Common ground | Share ground with FC, receiver, and ESP32-S3. |
| `TX` | GPS UART TX (3.3 V) | **`R6`** | `PC7` (`USART6_RX`, DMA) | **CROSSED** (GPS TX → FC RX). |
| `RX` | GPS UART RX (3.3 V) | **`T6`** | `PC6` (`USART6_TX`, DMA) | **CROSSED** (GPS RX → FC TX). |
| `SDA` | Compass I2C data (3.3 V) | **`SDA`** (front side) | `PB9` (`I2C1_SDA`) | **STRAIGHT** (SDA → SDA). |
| `SCL` | Compass I2C clock (3.3 V) | **`SCL`** (front side) | `PB8` (`I2C1_SCL`) | **STRAIGHT** (SCL → SCL). |

### B. 4-pin HGLRC M10 Mini (GPS only, no compass)

Wire only the first four rows (`5V → 4V5`, `GND → G`, `TX → R6`, `RX → T6`). Leave `SDA` and `SCL` unconnected. Note that SpeedyBee F405 V4 has **no onboard compass**, so a rover without an external compass has no static yaw heading at standstill; an external compass module (M100-5883 / M100 Pro) is strongly recommended for reliable ArduRover `AUTO` missions.

### C. ESP32-S3 `TeleRCBridge` → SpeedyBee F405 V4 wiring (no pin conflict with GPS)

| ESP32-S3 (`TeleRCBridge`) | SpeedyBee F405 V4 Pad | STM32 Pin / Bus | Notes |
| --- | --- | --- | --- |
| **GPIO18** (`FC_RX_GPIO`) | **`T3`** | `PC10` (`USART3_TX`) | 3.3 V UART, 115200 8N1 (crossed). |
| **GPIO17** (`FC_TX_GPIO`) | **`R3`** | `PC11` (`USART3_RX`) | 3.3 V UART, 115200 8N1 (crossed). |
| **GND** | **`G` / `GND`** | Common ground | Required signal reference. |
| **5V / VIN** (board regulator input) | Regulated **5 V** BEC | Separate from motor pack | Never apply 5 V to any ESP32-S3 GPIO. |

### D. Complete SpeedyBee F405 V4 UART & I2C conflict map

| ArduPilot Port | Physical Pads | STM32 Pins | Assigned Function | Conflict Check |
| --- | --- | --- | --- | --- |
| `SERIAL0` | USB-C | `PA11`/`PA12` | MAVLink2 (Mission Planner / QGC) | Bench setup |
| `SERIAL1` | `T1`/`R1` | `PA9`/`PA10` | DJI VTX / unused | Free if no VTX |
| `SERIAL2` | `T2`/`R2` (`SBUS`) | `PA2`/`PA3` | RC Receiver (`CRSF`/`ELRS` or `SBUS`) | `SERIAL2_PROTOCOL = 23` |
| `SERIAL3` | **`T3`/`R3`** | `PC10`/`PC11` | **ESP32-S3 `TeleRCBridge`** | `SERIAL3_PROTOCOL = 2`, `115200` |
| `SERIAL4` | Internal BT | `PA0`/`PA1` | **Disabled (`-1`)** | Wired to internal BT chip; unusable in ArduPilot |
| `SERIAL5` | `R5` | `PD2` | ESC telemetry / unused | RX-only pad |
| `SERIAL6` | **`T6`/`R6`** | `PC6`/`PC7` | **HGLRC M10 GPS (DMA)** | `SERIAL6_PROTOCOL = 5`, `115200` |
| `I2C1` | **`SDA`/`SCL`** | `PB9`/`PB8` | **HGLRC M100-5883 QMC5883L** (+ onboard SPL06 baro `0x76`) | Shared I2C bus, distinct addresses (`0x0D` vs `0x76`) |

## 4. Critical ArduRover parameter audit (fixes vs legacy snapshot)

Inspecting `ardurover/params/ardurover_esp32_s3_mavlink_02oct2026.param` revealed **two real configuration bugs** that would break HGLRC M10 compass heading or TeleRC Wi-Fi control/mission upload if loaded unmodified:

1. **Switch A / CH7 conflict (CRITICAL):**
   - Legacy file had `MODE_CH = 7`, `RC6_OPTION = 46`, `RC7_OPTION = 0`.
   - Both `TeleRC` (`GoRadioTelemetry`) and `TeleRCBridge` (`FieldProtocol.h`) gate phone ARM, HOLD, and Wi-Fi Mission Upload on **CH7 > 1800 µs** (Switch A) and require **`RC7_OPTION = 46`** (`RC Override Enable`) on the FC.
   - **Fix:** Set `RC7_OPTION = 46`, `RC6_OPTION = 0`, and move `MODE_CH = 6` (or `8`, mapped to `MANUAL = 0`, `HOLD = 4`, `AUTO = 10` on a separate 3-position switch).
2. **External compass disabled (CRITICAL for M100-5883 / M100 Pro):**
   - Legacy file had `COMPASS_EXTERNAL = 0` and `COMPASS_USE = 0` while `EK3_SRC1_YAW = 1` (Compass).
   - SpeedyBee F405 V4 has no internal compass.
   - **Fix:** For 6-pin M100-5883 / M100 Pro, set `COMPASS_ENABLE = 1`, `COMPASS_EXTERNAL = 1`, `COMPASS_USE = 1`, `COMPASS_AUTO_ROT = 2`, reboot, confirm the external QMC5883L appears in Mission Planner, and run **Compass Calibration** away from BTS7960/motor power wires.

Apply the verified overlay [`ardurover/params/ardurover_f405v4_hglrc_m10_esp32s3_wifi.param`](../ardurover/params/ardurover_f405v4_hglrc_m10_esp32s3_wifi.param) after backing up your current parameters.

## 5. Physical mounting & bench verification checklist (motors disconnected)

1. **Mounting:** Mount the HGLRC M10 ceramic patch antenna facing **straight up** with clear sky view, elevated on a mast/standoff away from BTS7960 drivers, battery leads, and the ESP32-S3 Wi-Fi antenna to minimize RF/magnetic interference. Note the module's forward arrow for compass orientation.
2. **Power check:** Connect USB-C to the F405 V4 (motor battery disconnected). Confirm the HGLRC M10 **Red Power LED** is solid on (`4V5` rail). Check continuity and polarity before applying battery power.
3. **GPS fix check:** Take the rover outside or near a window. Confirm the **Blue PPS LED** begins blinking once a 3D fix is acquired, and verify in Mission Planner (`Flight Data → Status`) that `gpsstatus >= 3` and `satcount >= 6`.
4. **Compass check (6-pin M100-5883 / Pro):** Confirm QMC5883L is detected on I2C1, complete compass calibration, and verify HUD heading tracks north/east/south/west accurately while rotating the chassis by hand.
5. **TeleRC Wi-Fi & Mission verification:**
   - Power `TeleRCBridge` ESP32-S3, join `TeleRC-Rover` Wi-Fi, tap **Phone connect** in TeleRC.
   - Verify `Switch A (CH7)` HIGH shows `Connected · tap ARM` in Controls and `Wi-Fi upload ready` in Mission (while disarmed), and `ROVER GPS · Xs ago` appears once the F405 has a 3D GPS fix.
   - Flip Switch A LOW: verify TeleRC immediately shows `Transmitter control` and disables both ARM and Wi-Fi Upload.
   - Upload 3 test waypoints while disarmed with Switch A HIGH, confirm `Upload acknowledged · rover remains disarmed`, then read back the mission in Mission Planner/QGC over USB to confirm waypoints 1–3 match and Home is unchanged before ever selecting `AUTO` on the transmitter.

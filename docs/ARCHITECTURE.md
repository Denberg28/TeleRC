# Current change: 1.2.0rc4

Baseline `b4f7ba9` / published `v1.2.0rc3` (code `1202`), adopting **TeleRC Go** (`Denberg28/telerc_go` `go-v1.0.12`) for the Android controller while adding **only** the **Mission** planning tab over Wi-Fi via ESP32-S3 (`TeleRCBridge`).

- **Single Wi-Fi transport (`GoLink`)**: Owns the bound Android Wi-Fi UDP socket (`192.168.4.1:14550`, default phone port `14550`, system ID `1`), 20 ms (50 Hz) proportional RC override cadence, `GoControl` (1500 ms heartbeat freshness, 300 ms neutral dwell, 2 s ARM transaction), ArduRover `RC_CHANNELS` CH7 > 1800 µs (750 ms freshness) and standalone Go/Mini rover `TELERC_OWN` auto-detection, live `GLOBAL_POSITION_INT` rover GPS parsing, and `MissionTransfer` under one lock and worker thread.
- **Mission tab**: Only added screen alongside Go Controls. Switching between Controls and Mission neutralizes sticks/stops driving while keeping the Wi-Fi link connected; leaving Mission, opening Settings, pausing, disconnecting, editing the plan, arming, or losing heartbeat/CH7 cancels any active `MissionTransfer` without auto-resume. `GoMissionGate` enforces disarmed, CH7-permitted, foreground-Mission ArduRover state. No game/maze, LoRa USB tab, Setup tab, external servo UI, or AUTO/MISSION_START command exists in this app.
- **Firmware & HGLRC M10 GPS**: `bridge/TeleRCBridge/TeleRCBridge.ino` is sanitized (committed password removed; honors local 12–63 char `PERSONAL_AP_PASSWORD` with `TR-XXXX-XXXXXXXX` fallback; fixed `Serial.printf` newlines). HGLRC M10 / M100-5883 installs on **SpeedyBee F405 V4** `UART6` (`T6`/`R6` crossed) + `4V5`/GND (+ `I2C1 SDA/SCL` straight for QMC5883L compass), never on the ESP32-S3 bridge. See [`docs/GPS_HGLRC_M10_SCHEMATIC.md`](GPS_HGLRC_M10_SCHEMATIC.md) and [`ardurover/params/ardurover_f405v4_hglrc_m10_esp32s3_wifi.param`](../ardurover/params/ardurover_f405v4_hglrc_m10_esp32s3_wifi.param).

Historical 1.2.0rc2 and earlier architecture records follow below.

# Current change: 1.2.0rc2

Baseline `2375fc6` / published v1.2.0rc1. Joystick presentation now uses Go-style ticks, a gradient center-to-position line and a small position light; touch travel, response preferences and channel mapping remain authoritative in the existing input code. Setup uses two compact scrollable panels; advanced endpoint and diagnostic details open on demand.

`MissionPlan` retains the same private CSV schema. `MissionTransfer` owns a copied upload snapshot under MainActivity's `commandLock`; only the captured link worker sends its packets. An explicit confirmation starts MISSION_COUNT, sequential requested MISSION_ITEM_INT (or legacy MISSION_ITEM), then final MISSION_ACK. Source system/component, GCS target, mission type, frame length/flags and CRC are checked. Sequence 0 is ArduPilot's reserved Home slot; route items use sequences 1–N, relative global frame and NAV_WAYPOINT only. No SET_HOME, clear, ARM, AUTO or MISSION_START is emitted. Transfer can retry a step three times at 1500 ms, with an overall 120 s deadline. Duplicate requests are bounded; invalid/out-of-order requests cannot advance the transfer. There is no automatic continuation after cancellation/reconnect. MAVLink has no transaction identifier here: the ACK confirms protocol acceptance, not downloaded route verification or physical readiness. A failed/cancelled transfer can leave changed/partial onboard contents; verify before AUTO.

Upload requires the Mission page foreground, control disabled, no pending ARM, fresh FC heartbeat, disarmed state and fresh physical CH7 permission. The bridge independently requires its observed disarmed FC/CH7 gate. `TeleRCBridge/FieldProtocol.h` admits only bounded COUNT and waypoint ITEM forms; PC sparse overrides and all existing downstream timeouts remain unchanged. App session/background/edit/loss invalidates transfer state without sending an arm, clear or start. The FC/transmitter remains responsible for execution and failsafes.

| Pair | Compatibility / upgrade |
| --- | --- |
| Android 1.2.0rc2 ↔ this release's legacy-bridge TeleRCBridge ↔ ArduRover | New disarmed Wi-Fi upload path; paired bridge upgrade required. Physical integration pending. |
| Android 1.2.0rc2 ↔ old TeleRCBridge | Existing driving/control formats retained; upload rejected/timeouts; upgrade only the intended FC bridge. |
| Android 1.2.0rc2 ↔ dedicated Wi-Fi gateway/motor, Mini rover or USB LoRa | Existing control paths retained; no mission transfer support; local plan editor remains usable. |
| Android 1.2.0rc2 ↔ LoRa BASE/ROVER/motor ↔ PC client | Radio/UART formats, provisioning and firmware unchanged. Android USB receive batches now cap at 16 pending frames, expiring after 250 ms local age. Actual PC/USB/RF regression pending. |

`TelemetryMailbox` bounds only the Android USB receive backlog, not radio latency or the motor watchdog. It drops oldest overflow and expired/backwards-time entries before delivery. Frames still waiting in device/USB/FC buffers lack creation timestamps; this change cannot establish full-stack freshness/replay protection. Wi-Fi remains unsigned, endpoint/CRC-checked, not authenticated MAVLink.

References: [MAVLink mission protocol](https://mavlink.io/en/services/mission.html), [ArduPilot mission transfer](https://ardupilot.org/dev/docs/mavlink-mission-upload-download.html). ArduRover 4.6.3 source review: [reserved Home read behavior](https://github.com/ArduPilot/ardupilot/blob/Rover-4.6.3/libraries/AP_Mission/AP_Mission.cpp). Golden frame vectors are generated independently with pymavlink; it is a validation aid, not a runtime dependency.

Historical baseline architecture/evidence follows; statements about local-only planning below describe prior releases.

# TeleRC control and compatibility

Published 1.2.0rc1/code 1200: source/tag `3f6d0adc059827a9b527d26ac1e352841c7125f1`, successful signed run `44086597994`. Release assets and current STATUS.md record verified package/signature/checksums and remaining physical validation.

Release inventory checked 17 November: published baseline is `v1.1.0rc1` / code 1100 / source `03b5b69ff39487678d25a8023c5438a773ca3d51`. The old default branch and 0.8.x artifact references were stale. Final 1.2.0rc1/code 1200 preserves the relevant released safety/parser/updater/response checks while removing feature UI. Historical records below are not proof that old assets remain downloadable.

## 1.2.0rc1 simplification

Single-rover screens: Controls (default), Mission, Setup, LoRa setup. `MissionPlan` owns validated bounded planning data; `MissionPlannerView` owns map/list editing; MainActivity owns atomic private persistence and Android document pickers. `RouteMapView` displays measured tracks and local planned waypoints only. The old game, simulation/RC inference and servo-assignment UI are removed; old track files including estimate rows still decode for upgrade compatibility, but estimates are no longer displayed or generated.

Mission actions do not call LinkTransport, MAVLink, Enable, ARM or any vehicle-mode API. CSV is a local planning exchange, not a QGroundControl/MAVLink mission protocol. Import validates fully before confirmation/replacement; save uses AtomicFile. Up to 100 waypoints, 16,384 CSV characters, decimal-degree coordinate validation; no change to firmware, telemetry, channel mapping, sensitivity, arm cancellation, freshness, STOP/release or watchdog semantics. No paired firmware update is required for this Android-only feature change. Use the existing matched hardware role firmware; actual phone/USB/radio/PC operation remains unverified.

Current software validation is recorded in TESTING.md. Prior-version notes below are historical evidence, not validation of the new UI.

## Historical source baseline records

Historical source-record candidate: **0.8.53 / code 71**, published from `0f7272da0d30b398dfe13c0a1008afca93f24c5c`. It packages the LoRa setup and four-driver review plus matched binary/source firmware. Release workflow [37619553457](https://github.com/Denberg28/TeleRC/actions/runs/37619553457) passed signing continuity/version progression, JVM tests, debug/release lint/build, full host sanitizers, ten actual firmware configurations and archive verification. Baseline retained for rollback: 0.8.52 commit `51e98a0797c476647dea88cbebdee6a52503c2a6`. Physical operation remains unverified; compatibility details for 0.8.53 are below.

## Signal paths

| Configuration | Command path | Output authority |
| --- | --- | --- |
| Dedicated Wi-Fi, DIRECT | Android/PC → UDP Wi-Fi ESP32-S3 gateway → framed UART → motor ESP32-S3 → BTS7960 | Fixed motor MCU; explicit DIRECT ARM required |
| Dedicated LoRa, DIRECT | Android USB / PC USB relay → T3-S3 BASE → authenticated LoRa → T3-S3 ROVER → framed UART → motor MCU | Fixed motor MCU; same arm/freshness checks |
| Dedicated AUTOPILOT | Gateway → motor MCU MAVLink UART → F405; F405 M5–M8 → motor MCU → drivers | Physical selector chooses FC PWM; FC remains responsible for autonomous/receiver failsafes |
| Legacy combined hybrid | Android/PC → UDP → combined ESP32-S3 → drivers or FC UART/PWM | Legacy hybrid arbiter |

Exactly one gateway connects to the motor UART. The 44-pin board is a passive breakout, not a link selector. Swap the keyed harness with controller and motor power removed. Read `bridge/SWAPPABLE_GATEWAYS.md` and `bridge/LORA_INTEGRATION.md` for wiring and power details.

## Authority and recovery

| Event/state | Android behavior | Downstream behavior |
| --- | --- | --- |
| Connected, fresh heartbeat | Link status available; control remains disabled until requested | No automatic arm from telemetry/discovery |
| Enable Control | CH1 steer / CH2 drive at up to 10 Hz; neutral 1500 µs; calibrated range 1100–1900 µs | DIRECT motion additionally requires motor MCU armed state |
| Heartbeat age ≥4 seconds | Clear axes and revoke authority before admitting recovery heartbeat; maintain transport | With no further fresh commands, independent watchdog applies |
| Heartbeat recovery/reconnect | Fresh link becomes available; explicit Enable Control required | DIRECT recovery is disarmed; neutral source dwell and fresh channel ownership required |
| Tab change/background | Disable authority; send neutral, preserve telemetry connection | Dedicated DIRECT remains neutral until expiry, which enters FAILSAFE/disarms; legacy FC bridge holds neutral |
| STOP CONTROL | Neutral then release override to receiver | DIRECT release disarms/motor-off; AUTOPILOT receiver may command motion |
| Disconnect | Neutral then explicit disconnect; close old session using its captured port | Motor MCU failsafe/latch or legacy bridge handover; loss of the final message falls back to watchdog behavior |
| Queued ARM after stop/pause/session change or age ≥500 ms | Cancel request; retries recheck current state; DISARM can still target the same captured session | No force-arm or pre-arm bypass |

Dedicated controller constants: DIRECT axis timeout 500 ms, FC PWM timeout 150 ms, neutral source dwell 300 ms, selector debounce 50 ms, pairing lease 5 seconds. These are software thresholds, **not measured end-to-end stop times**. Dedicated FAILSAFE sets both PWM directions to zero; driver coast/brake and physical stopping distance require measurement. AUTOPILOT link loss does not universally stop autonomous movement. Legacy neutral refresh is 100 ms after its 500 ms command timeout.

LoRa: 200 ms host command mailbox freshness, 90 ms one-use challenge window, bounded UART/radio frames, no queued control retransmission. HMAC authenticates radio frames; it does not encrypt them. UART uses CRC and a physically trusted connection. Wi-Fi/legacy MAVLink validate endpoint and framing/checksum but do not authenticate commands or reject all replay/reordering. Their accepted-packet timestamp does not prove sender creation time.

## Compatibility matrix

| Pair | Protocol/source review | Software/hardware status |
| --- | --- | --- |
| Android 0.8.52 ↔ 0.8.50 dedicated Wi-Fi gateway/motor firmware | Wire format unchanged; CH1/CH2 and release semantics retained | Baseline software gates PASS; rebuild gates required; end-to-end hardware BLOCKED |
| Android 0.8.52 ↔ 0.8.50 LoRa BASE/ROVER/motor firmware | UART framing, provisioning, HMAC/key/profile unchanged | Host authentication/provisioning tests PASS; actual USB/radio integration BLOCKED |
| Android 0.8.52 ↔ legacy TeleRCBridge/hybrid | Existing UDP discovery, override, arm/disarm/disconnect messages retained | Baseline host/actual-target builds PASS; rebuild gates required; device tests BLOCKED |
| Existing PC TeleRC ↔ dedicated/legacy firmware | Firmware unchanged; sparse CH1/CH2 ignore values retained | Actual PC client/version interoperability NOT TESTED in this session |
| F405 ArduRover 4.6.3 ↔ motor MCU | Existing M5–M8 inputs, GPIO16 selector, 115200 UART retained | Parameter/electrical/HIL verification BLOCKED |

No paired firmware upgrade is required by the unchanged 0.8.52 wire format. The matching 0.8.51 LoRa core uses a statically owned radio module instead of an unowned boot allocation; packets, keys, profile and pins are unchanged. Use the included LoRa sketches to carry that memory-ownership fix. Reflash only the intended sketch, retain board-specific configuration and credentials, and revalidate physical behavior after a firmware/configuration change.


## 0.8.53 candidate compatibility

Android versionCode 71 packages reviewed source `8e2e6df550f919d27d9ee00c5a6dc0ed45e9c9d2` plus release metadata/build tooling. CH1/CH2, 1500 µs neutral, UART/radio format, pins, profile, key and NVS schema remain compatible. Updated BASE preserves sparse axes and stop priority; updated ROVER latches RX restart failures; dedicated motor initializes output GPIOs before delays. PWM-only users require 300 ms fresh neutral at startup/recovery and get immediate invalid/stale-input stop. Deploy and bench-test the intended firmware roles together. The binary package uses explicit generic motor and manufacturer T3-S3 profiles, rather than treating host tests as target compilation. See `bridge/FIRMWARE_FLASHING.md` and `docs/LORA_MOTOR_REVIEW.md`.


Final release continuity: FC Wi-Fi uses RcTakeover's checksum/source/monotonic-time checks and 750 ms CH7 permission lifetime; live sends and queued ARM recheck permission, foreground Controls and captured session. LiveControlGate requires neutral axes. Mini Wi-Fi uses MiniRoverArmGate (300 ms sent-neutral dwell, fresh last sent frame), separate from FC permission. Unsupported saved transport profiles are reset to Setup with auto-connect disabled. Wi-Fi loss rebinds a new socket with control disabled. AppUpdater retains bounded metadata and DownloadManager completion verification; native dialogs replace its former custom dialog UI. These components and regression tests come from the published 1.1.0rc1 tag. Legacy FC bridge carries that tag's FieldProtocol/ARM permission checks; use the matching updated bridge for that path. Dedicated motor/LoRa formats and provisioning are unchanged.

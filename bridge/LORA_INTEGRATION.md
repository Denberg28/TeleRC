> Update: blank boards can now be provisioned in the APK LoRa setup tab. See [swappable gateways](SWAPPABLE_GATEWAYS.md). The compile-time private configuration remains supported, but an existing valid NVS radio profile takes precedence.

# TeleRC dedicated motor + LoRa integration

Status: implementation for bench validation, not a field-qualified release. Android v0.8.48 adds native USB OTG input to the LoRa base; PC uses the included USB relay. The original Wi-Fi hybrid .ino remains independently available.

## Architecture and firmware

| Device | Firmware | Responsibility |
|---|---|---|
| ESP32-S3 DevKitC-1 motor controller | `TeleRCMotorController/TeleRCMotorController.ino` | Four BTS7960 motor outputs; DIRECT/AUTOPILOT selector; independent axis freshness; neutral-transfer dwell; PWM validation; slew/reversal limiting |
| Rover LILYGO T3-S3 | `TeleRCLoRaRover/TeleRCLoRaRover.ino` | Authenticated point-to-point LoRa, one-use challenges, bounded UART transport; no Wi-Fi started |
| Base LILYGO T3-S3 | `TeleRCLoRaBase/TeleRCLoRaBase.ino` | USB joystick input by default, plus LoRa; optional legacy local UDP is disabled by default |
| Optional SpeedyBee F405 | Existing ArduRover installation | Navigation, receiver arbitration, GPS/EKF, M5-M8 PWM and mode-gated MAVLink telemetry |

Three ESP32-based boards total: one motor ESP32-S3 and two T3-S3 boards. Motor control does not execute on either communications board.

Use `TeleRCMotorController` for this UART command path. `BTS7960PWMConverter` is a separate FC-PWM-only companion; it has no UART command decoder or DIRECT ARM state. [The four-driver review](../docs/LORA_MOTOR_REVIEW.md) documents the distinction and the current sanitization changes.

## Connections

| From | To | Notes |
|---|---|---|
| Rover T3-S3 GPIO43 TX | Motor ESP32 GPIO21 RX | UART 115200, 3.3 V logic |
| Rover T3-S3 GPIO44 RX | Motor ESP32 GPIO39 TX | UART 115200, 3.3 V logic |
| Rover T3-S3 GND | Motor ESP32 GND | Common signal reference |
| F405 M5/M6/M7/M8 | Motor ESP32 GPIO4/5/6/7 | Existing four-wheel PWM mapping |
| Four BTS RPWM/LPWM | Motor ESP32 GPIO8/9, 10/11, 12/13, 14/15 | FL, RL, FR, RR |
| 3.3 V through selector switch | Motor ESP32 GPIO16 | LOW/open DIRECT; HIGH AUTOPILOT |
| F405 T3 / R3 | Motor ESP32 GPIO18 RX / GPIO17 TX | Optional MAVLink UART, 115200 |

GPIO39 avoids USB GPIO19/20 on the motor ESP32. Verify actual board exposure before wiring. T3 GPIO43/44 are taken from the manufacturer's T3-S3 pin map; native USB CDC must be enabled so USB serial does not occupy those pins. GPIO17/18 on the T3-S3 serve onboard I2C and are not our UART choice.

Retain the hybrid guide's BTS logic power, fusing, motor power disconnect, external PWM pulldowns and signal-level checks. Feed regulated 5 V only to a board's supported USB/5V input; never its 3V3 pin or GPIO. Avoid backfeeding a connected USB host; verify the specific board's supply path. Keep antennas attached before transmission.

## Build and provisioning

1. Install ESP32 Arduino core 3.3.2 and RadioLib 7.2.1 (pinned CI versions).
2. Copy `bridge/libraries/TeleRCLink` into the Arduino sketchbook `libraries` directory. All three new sketches need this library.
3. Use the ESP32-S3 board profile matching your hardware. For T3-S3, follow LILYGO's board revision, 4 MB flash / 2 MB QSPI PSRAM settings; enable **USB CDC On Boot**.
4. Copy `TeleRCLoRaConfigLocal.h.example` into EACH LoRa sketch folder as `TeleRCLoRaConfigLocal.h`.
5. Select the actual radio: SX1262 or SX1276, matching frequency/front-end/antennas at both ends. SX1278, SX1280 and LR1121 are not implemented here.
6. Set an installation-appropriate permitted frequency and transmit power. Frequency deliberately has no operational default. The initial radio profile is SF7, 500 kHz bandwidth, CR4:5, preamble 8, sync word 0x12; verify its permitted use for the location and hardware. This is a latency-oriented bench profile, not a guaranteed long-range profile.
7. Generate 32 random pairing bytes locally, for example `python -c "import secrets; print(','.join('0x'+format(x,'02x') for x in secrets.token_bytes(32)))"`. Put the same initializer in `TELERC_RADIO_KEY` on both gateways. Do not share or commit the key. Replace `{0}` and set `TELERC_RADIO_PROVISIONED=1` only when configured.
8. Flash motor, rover and base firmware to their respective devices.

Unconfigured gateway builds compile but deliberately do not start radio transmission. A target compile is not evidence of a working RF connection. Core 2/3 host motor tests cover both LEDC API branches; actual target CI uses core 3.3.2.

CLI example from the repository root:

```bash
arduino-cli compile --fqbn esp32:esp32:esp32s3:CDCOnBoot=cdc --libraries bridge/libraries bridge/TeleRCMotorController
arduino-cli compile --fqbn esp32:esp32:esp32s3:CDCOnBoot=cdc --libraries bridge/libraries bridge/TeleRCLoRaRover
arduino-cli compile --fqbn esp32:esp32:esp32s3:CDCOnBoot=cdc --libraries bridge/libraries bridge/TeleRCLoRaBase
```

The generic CLI profile is used for CI compile checks; select the correct flash/PSRAM profile when uploading to your actual T3-S3.

## Existing Android and PC operation

### Android joystick over USB OTG and LoRa

Use Android TeleRC **v0.8.48 or later**, select **LoRa via USB base** in Setup, and connect the phone through a data-capable USB OTG cable to the base T3-S3's **native ESP32-S3 USB CDC** port. Leave `TELERC_BASE_WIFI=0`. Tap Connect and grant Android's USB permission prompt. The app accepts exactly one connected Espressif native CDC ACM device (vendor 0x303A); CP210x/CH340/CH343 USB UART adapters are not supported by this driver.

The complete joystick path is **Android joystick → USB OTG → base T3-S3 → LoRa → rover T3-S3 → UART → motor ESP32-S3 → BTS7960 PWM**. No Wi-Fi network, UDP network socket or internet service is used for this control path. ARM/DISARM and incoming vehicle frames use the same USB transport. Disconnect, detach, tab/background neutral and the motor watchdog remain active. Native USB enumeration, phone power/OTG compatibility and device permissions require physical testing.

### Optional legacy local Wi-Fi at base

Set `TELERC_BASE_WIFI=1` only if intentionally using the optional legacy local Wi-Fi input. Join **TeleRC-LoRa-Base** (not the rover). The base remains `192.168.4.1`, UDP `14550`. Get the generated password from base Serial Monitor, or configure a private password. Existing Android and PC UDP clients retain their normal settings and CH1 steering/CH2 bidirectional drive. This Wi-Fi leg covers only the nearby operator-to-base connection.

### USB PC link, no Wi-Fi needed

`TELERC_BASE_WIFI=0` is now the default. Close Serial Monitor before opening the USB relay:

```powershell
py -m pip install pyserial
py bridge/lora_usb_relay.py --serial COM7
```

Set PC TeleRC target IP to `127.0.0.1`, target port to `14551`, and local port to `14550`. If the installed PC version cannot edit target port, use the local Wi-Fi base path; native PC application serial transport is not added by this change. The helper binds loopback only. Do not run another application on either local UDP port. Android native USB OTG is included; BLE is not implemented.

### Control sequence

Raise wheels. Select DIRECT, connect the app, send centered controls, wait for the 300 ms source-neutral dwell, then ARM. If ARM is rejected during transfer, wait for DIRECT and retry at neutral. After a timeout, center/enable controls and explicitly ARM again. Merely receiving LoRa polls does not arm or refresh motor command freshness.

AUTOPILOT retains the physical GPIO16 selector and the F405's receiver/RC6 rules. M5-M8 remain the motor command source; LoRa carries the current app's RC overrides/arm commands into the mode-gated MAVLink UART. Explicit disconnect remains a latched motor stop; AUTOPILOT needs a selector change and neutral dwell to reset it.

## Protocol and optimization

- Rover-led request/response slots: a new random 64-bit challenge for each poll. Base responses must echo it; rover accepts only one response within 90 ms after poll transmission completes. Old, repeated and wrong-direction responses are rejected.
- HMAC-SHA256 truncated to 128 bits authenticates header/body. This provides authentication, not encryption or resistance to radio jamming. Local Wi-Fi/USB and physical motor UART remain trusted interfaces; this is not end-to-end signed MAVLink.
- Radio bodies are at most 96 bytes (124 bytes including authenticated header/tag). No radio fragmentation, blocking radio operation on the motor board, or bulk telemetry queue.
- Control mailbox coalesces only touched CH1–CH4 values, each with its own 200 ms expiry; ignored channels never renew another axis. It also retains the latest heartbeat, a bounded event and a separate release slot. A poll gap over 250 ms clears queued commands. Release and DISARM both survive either arrival order, cancel queued drive/ARM and block later motion until dispatch. Disconnect has highest priority. Expired commands are never replayed after recovery.
- Each accepted RC packet is forwarded at most once. No cached throttle is periodically resent. Sparse channel updates preserve the original motor controller's independent 500 ms CH1/CH2 deadlines.
- UART framing: A5 5A, uint16 little-endian length, original datagram, CRC16-CCITT (initial 0xffff) over length+payload. At most 280 payload bytes; incomplete frames expire at 20 ms. The local UART is CRC-protected, not cryptographically authenticated.
- Commands admitted: current apps' MAVLink v1 heartbeat, RC override, ARM/DISARM, discovery and explicit disconnect. The base checks CRC, controller/target identity, channel range/reserved channels and standard ARM/DISARM parameters; motor/FC parsers remain final validators. Mission upload, parameter download, generic MAVLink v2 control and external servo commands are not supported by this LoRa mailbox.
- Telemetry priority: COMMAND_ACK, real vehicle heartbeat, then latest small SYS_STATUS/GPS_RAW_INT/GLOBAL_POSITION_INT or compatibility diagnostics. Other FC telemetry and frames larger than 96 bytes are dropped. This is not a transparent Mission Planner radio replacement.
- Poll interval: 100 ms after transmit completes. Actual update rate is lower than 10 Hz and depends on airtime, host events and radio errors. Do not increase spreading factor without re-evaluating response window and motor timeout.

## Stop boundaries

Motor ESP32 independently retains: separate 500 ms DIRECT axes and FC override freshness checks; 150 ms required PWM deadline; 300 ms neutral handover; 50 ms source selector debounce with immediate edge stop; explicit new DIRECT ARM after failsafe. Removing LoRa or UART cannot preserve DIRECT motion through communications heartbeat alone.

Receiver-driven/autonomous AUTOPILOT can continue through loss of the TeleRC link when TeleRC owns no overrides. That is inherited hybrid behavior, governed by FC failsafes; explicit disconnect still stops/latches the motor path. If a universal stop-on-LoRa-loss policy is needed for AUTOPILOT, it requires a separate explicitly defined supervisory input/policy.

A CRC/authenticated frame does not prove physical motor safety. Software zero PWM does not isolate battery power or guarantee braking distance. Independent power cutoff remains necessary for CPU/peripheral hang faults.

## Validation and bench acceptance

Run `python bridge/sync_motor_core.py --check` and `bridge/tests/run.sh`. The generated motor core is reproducibly derived from the independently flashable original hybrid sketch; this check catches future drift. CI compiles legacy bridge/hybrid, dedicated motor and both LoRa roles, plus SX1276/USB-only variants.

CI also compiles the Wi-Fi gateway and standalone PWM converter in paired and four-input configurations (seven primary and three alternate builds total). Host tests exercise the actual dedicated motor parser after authenticated protocol framing and UART decoding; they do not emulate RF airtime or establish physical operation. Radio receive-restart failures now report inactive, clear pending commands/challenges and require a board restart; the motor's independent watchdog still governs stopping.

Host tests compile both gateway roles with both radio profiles and local Wi-Fi enabled/disabled against peripheral stubs. They also cover both core motor API branches, axis staleness, RC release, neutral-only ARM, PWM loss, selector transitions, UART backpressure, framing/CRC/timeout, HMAC corruption/wrong key/wrong direction, one-use/expired challenges, latest command coalescing, stale mailbox purge and USB relay decoding. These are software checks, not measured ESP32/RF/driver timing.

With motor battery disconnected first and wheels raised for powered tests:

1. Confirm no motion at boot, missing gateway, radio initialization failure or absent pairing config.
2. Confirm non-neutral ARM rejection; arm at neutral and verify four wheel directions at low duty.
3. Remove rover UART TX, stop base software, switch off base, and obstruct the RF link separately. Measure motor zero within the 500 ms control deadline plus loop delay. Test steering-only updates with stale throttle.
4. Inject corrupt/duplicate/old radio responses; confirm they do not renew motor freshness or re-arm.
5. Test tab change, backgrounding, Disable, explicit Disconnect and USB helper closure.
6. Restore communications with throttle displaced; verify DIRECT remains disarmed. Center and explicitly ARM before motion.
7. Test selector edges, neutral dwell, each M5-M8 wire loss, FC RC6 takeover and FC neutral-watchdog behavior in AUTOPILOT.
8. Measure command interval and input-to-motor latency, worst gap, packet loss and stopping time with motor electrical noise, then repeat at increasing distances/obstructions. A control gap reaching 500 ms must cause a stop, not a longer watchdog by default.
9. Verify reboot/brownout behavior, physical cutoff and reset-safe BTS input levels.

Only move to ground testing once these stop paths pass. Range and reliability versus Wi-Fi remain unmeasured.

Hardware/API references: [LILYGO T3-S3 SX1262](https://github.com/Xinyuan-LilyGO/LilyGo-LoRa-Series/blob/master/docs/en/t3_s3_sx1262/t3_s3_sx1262_hw.md), [manufacturer examples](https://github.com/Xinyuan-LilyGO/LilyGo-LoRa-Series), [RadioLib](https://github.com/jgromes/RadioLib).

# TeleRC control and compatibility

## Scope and baseline

Current release candidate: **0.8.53 / code 71**, published from `0f7272da0d30b398dfe13c0a1008afca93f24c5c`. It packages the LoRa setup and four-driver review plus matched binary/source firmware. Release workflow [37619553457](https://github.com/Denberg28/TeleRC/actions/runs/37619553457) passed signing continuity/version progression, JVM tests, debug/release lint/build, full host sanitizers, ten actual firmware configurations and archive verification. Baseline retained for rollback: 0.8.52 commit `51e98a0797c476647dea88cbebdee6a52503c2a6`. Physical operation remains unverified; compatibility details for 0.8.53 are below.

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

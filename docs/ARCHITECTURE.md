# TeleRC control and compatibility

## Scope and baseline

Current development: 0.8.51 / code 69. Remote baseline: `777c04e5ed8a32be9cb999b7c39761157e4099c4` (0.8.50), tree `6cf43eae85915881b049bed874d97693a1eac77d`. The recovered local source tree was byte-identical to that remote tree. Published 0.8.49 (`35152c4`) is a retained release baseline, not a claim of validated physical operation. Earlier October 6 review commits were unavailable; this branch redoes the unfinished work from the verified baseline.

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
| Android 0.8.51 ↔ 0.8.50 dedicated Wi-Fi gateway/motor firmware | Wire format unchanged; CH1/CH2 and release semantics retained | Firmware host tests PASS; Android build and end-to-end tests BLOCKED |
| Android 0.8.51 ↔ 0.8.50 LoRa BASE/ROVER/motor firmware | UART framing, provisioning, HMAC/key/profile unchanged | Host authentication/provisioning tests PASS; actual USB/radio integration BLOCKED |
| Android 0.8.51 ↔ legacy TeleRCBridge/hybrid | Existing UDP discovery, override, arm/disarm/disconnect messages retained | Hybrid host tests PASS; legacy actual-target/device tests BLOCKED |
| Existing PC TeleRC ↔ dedicated/legacy firmware | Firmware unchanged; sparse CH1/CH2 ignore values retained | Actual PC client/version interoperability NOT TESTED in this session |
| F405 ArduRover 4.6.3 ↔ motor MCU | Existing M5–M8 inputs, GPIO16 selector, 115200 UART retained | Parameter/electrical/HIL verification BLOCKED |

No paired firmware upgrade is required by the 0.8.51 wire format. The matching 0.8.51 LoRa core uses a statically owned radio module instead of an unowned boot allocation; packets, keys, profile and pins are unchanged. Use the included LoRa sketches to carry that memory-ownership fix. Reflash only the intended sketch, retain board-specific configuration and credentials, and revalidate physical behavior after a firmware/configuration change.

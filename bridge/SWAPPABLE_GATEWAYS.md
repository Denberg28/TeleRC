# TeleRC swappable communication gateways

Keep one dedicated ESP32-S3 running `TeleRCMotorController` in the 44-pin terminal adapter. Fit either an ESP32-S3 running `TeleRCWiFiGateway` or a T3-S3 running `TeleRCLoRaRover` using the common UART harness. Only the fixed motor board generates PWM and arbitrates DIRECT/AUTOPILOT; radio work cannot block its loop.

The photographed product is a passive terminal breakout, not an I/O expander or switch. Its listing specifies ESP32-S3 44-pin / 25.4 mm header spacing and warns that other boards need the same pinout. A T3-S3 V1.2/V1.3 uses two 13-pin headers and must use a separate harness/adapter. Never insert it directly into the 44-pin socket. Check the actual board revision, supply pin labels and continuity before connecting power.

## Common connector

Connector numbering below is our harness definition, not a manufacturer's header pin number. Use a keyed four-pin connector, label both ends, and swap only with power removed. Only one gateway may be connected; tying two transmitters together is not a transport selector.

| Harness contact | Fixed motor ESP32-S3 | Wi-Fi gateway ESP32-S3 | Rover T3-S3 |
|---|---|---|---|
| 1: regulated board supply | board 5V/VIN as documented | board 5V/VIN as documented | board 5V as documented |
| 2: ground | GND | GND | GND |
| 3: command toward motor | RX GPIO21 | TX GPIO39 | TX GPIO43 |
| 4: telemetry toward gateway | TX GPIO39 | RX GPIO21 | RX GPIO44 |

UART is 115200, 8N1, 3.3 V logic, with the existing length/CRC framed datagrams. Supply is not a UART signal: never connect 5 V to a GPIO. If USB powers a board during provisioning, disconnect the external supply unless the board's documented power arrangement explicitly supports both. Keep motor supply separate from the regulated controller supply with appropriate grounding. GPIO19/20 remain available for native USB. Motor driver/FC pins are unchanged from `LORA_INTEGRATION.md`.

## Wi-Fi operation

Flash `TeleRCWiFiGateway` to the removable command ESP32-S3 and the existing `TeleRCMotorController` to the fixed board. The gateway provides `TeleRC-Rover`, a random password retained in its NVS and printed on its local USB serial console, and UDP 192.168.4.1:14550. Select Wi-Fi UDP in TeleRC Setup. New APK installations default to Wi-Fi; existing transport preferences persist. The gateway validates the current TeleRC command subset, leases one local phone endpoint, drops congested traffic rather than queueing it, and forwards actual motor/FC telemetry. It never synthesizes vehicle heartbeat or renews a cached joystick command. The legacy combined hybrid firmware is still available for old installations.

## LoRa setup tab

Flash a BASE and a ROVER sketch with the exact supported radio variant (SX1262 or SX1276). Default firmware boots with the radio inactive and exposes USB setup; `TELERC_RADIO_VARIANT` is a firmware/hardware choice, not an app setting. SX1278, SX1280 and LR1121 require other profiles and are unsupported here.

In TeleRC's **LoRa setup** tab:

1. Disconnect motor power and raise the wheels. Attach one T3-S3's native USB CDC port to the phone by OTG. Disconnect an existing Wi-Fi session before opening the USB connection.
2. Connect USB board and Read board settings. The result identifies BASE/ROVER, radio variant, active state, frequency, power, a short non-secret pairing fingerprint, RX/reject/TX-failure/UART-drop counters. This local response does not establish a rover heartbeat or joystick authority.
3. Enter a locally permitted frequency in MHz (up to three decimals) and power (2–17 dBm). Capability bounds of 150–960 MHz are not regulatory permission. The initial fixed low-latency profile is SF7 / bandwidth 500 kHz / coding rate 4:5. Whether that bandwidth and continuous bidirectional polling are permitted depends on location, band and applicable rules; no general-purpose compliant frequency is assumed.
4. Generate a 32-byte pairing key, or enter your existing 64 hexadecimal digits. Save to the inactive board and wait for **Saved on board**. Key/profile are retained privately in the app so the second board can receive exactly the same settings. Keys are masked and are not printed in board info, logs or documentation; provisioning USB and physical flash access are trusted.
5. Disconnect and restart the configured board. Repeat with the other role using the same key, frequency and power. Leave the BASE attached to the phone for joystick control; the ROVER connects to the fixed PWM board.

Settings are stored as one NVS blob; save failure is reported. Save does not activate the radio until restart. An active radio rejects setup changes, preserving the running link. To re-pair an active board, remove motor power, erase its NVS and reflash; this also removes any board-local credentials. There is deliberately no remote radio reconfiguration over the motor link. The short CRC fingerprint is only an accidental mismatch aid, not proof of secure pairing. The key is HMAC authentication material; it does not encrypt motor traffic.

LoRa is direct BASE↔ROVER, with one-use challenge responses and fresh-command expiry. It is not Meshtastic mesh messaging: no routing, stored messages or retransmitted joystick history. Both LoRa boards have Wi-Fi disabled in the default build. Android reads actual motor/FC heartbeat before enabling control, just as on Wi-Fi.

## Swap and validation

Disarm, disconnect TeleRC, remove controller/motor power, swap the keyed gateway harness, then power up and select the matching app transport. Repeat raised-wheel stop/arm/direction checks. Never hot-swap. Radio polling, USB presence and telemetry cannot arm the fixed motor controller. Its 500 ms command freshness, neutral dwell, reversal limiting and physical source selector remain authoritative. In independent AUTOPILOT operation, FC/receiver failsafes remain responsible; losing the communication link is not a universal stop for an autonomous FC.

Host tests and target compilation validate parsing and code paths. USB attachment, real packet latency, interference/range, resets/brownouts, connector polarity and all physical stop timings still need bench testing. LoRa resilience is an installation-dependent hypothesis; measure it against the working Wi-Fi link.

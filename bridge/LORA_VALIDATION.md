# LoRa separation validation record

Baseline: TeleRC main `71bf86174954dce0114fe7c4206c35fac75a451d`.
Scope: dedicated motor ESP32-S3 firmware, T3-S3 LoRa base/rover gateways, shared bounded protocol library, local USB-to-UDP relay, wiring/provisioning documentation and CI expansion. Android v0.8.48 now adds a native ESP32-S3 CDC USB OTG transport and an explicit transport selector; PC application source is unchanged. The independently flashable original Wi-Fi hybrid sketch is unchanged.

## Completed local checks

- `python bridge/sync_motor_core.py --check`: passed. Generated motor code matches the reproducible UART adaptation of the existing hybrid sketch.
- `bridge/tests/run.sh`: passed. Four motor regression runs (legacy Wi-Fi/dedicated UART × Arduino core API branches 2/3), one protocol/authentication suite, eight gateway regression runs (base/rover × SX1262/SX1276 × local Wi-Fi on/off), four Python relay tests.
- C++ builds use C++17 with `-Wall -Wextra -Werror`.
- Protocol suite also passed AddressSanitizer and UndefinedBehaviorSanitizer with `ASAN_OPTIONS=detect_leaks=0`. LeakSanitizer itself cannot run under this environment's process tracing; memory-leak validation is not claimed.
- Protocol noise test: 100,000 arbitrary UART bytes with bounded parser-state assertions.
- HMAC host tests use OpenSSL's HMAC-SHA256 implementation behind the mbedTLS API adapter. ESP32 firmware uses mbedTLS; the host checks do not validate the embedded crypto library/build.
- Actual gateway loops are compiled/executed against SPI/RadioLib/serial/Wi-Fi stubs. They verify accepted forwarding, duplicate response rejection, malformed bundle rejection, all-or-nothing UART backpressure handling and oversized radio rejection. Stubs are not evidence of actual radio timing.
- SX1276 interrupt registration was checked against manufacturer-bundled RadioLib declarations and uses the required `RISING` argument.
- Workflow YAML and the five-sketch compile matrix parsed successfully. `git diff --check`: passed.

## Uncompleted checks and publication

- Required Android commands `./gradlew :app:assembleDebug :app:testDebugUnitTest` were attempted. They failed to start because Gradle download from services.gradle.org is network-unreachable. Local Android build/test remains blocked; [Android CI](https://github.com/Denberg28/TeleRC/actions/runs/37236059947) successfully ran unit tests and assembled the debug APK. New SerialDatagram unit tests cover fragmented/multiple frames, CRC corruption, expired partial frames, known firmware-compatible CRC vector and size bounds; They passed in CI along with existing application tests.
- Arduino CLI / ESP32 target toolchain is not installed locally. The expanded GitHub workflow defines actual target builds using ESP32 core 3.3.2 and RadioLib 7.2.1, and all five target builds passed in [firmware CI](https://github.com/Denberg28/TeleRC/actions/runs/37236059937), including SX1276/USB-only gateway builds.
- Initial Git push was rejected by automatic approval review for insufficient explicit destination authorization. The user subsequently approved publication. Destination was verified as public `denberg28/TeleRC`; the approved explicit-URL push then failed because shell Git has no credentials. Publication succeeded through the connected GitHub API on branch `feat/dedicated-motor-lora`, reviewed in PR #2. No private pairing key is present in the commit.
- SITL, physical UART/RF interoperability, Android/PC end-to-end control, GPIO capture/PWM timing, brownout/reset behavior, stopping latency, range, packet-loss rate and superiority to Wi-Fi remain untested. Follow the acceptance procedure in `LORA_INTEGRATION.md` with wheels raised.

## Important design boundaries

DIRECT requires fresh CH1 and CH2 within 500 ms and explicit neutral-only ARM after a timeout. Radio polling alone never refreshes those deadlines. AUTOPILOT preserves the existing FC takeover/autonomous behavior: without TeleRC-owned overrides, FC-driven motion is governed by FC failsafes rather than unconditional LoRa-loss stop.

Radio HMAC authenticates over-air packets; traffic is not encrypted. One-use short-lived rover challenges protect motor-bound commands against replay. This is not end-to-end MAVLink signing, base-host authentication, telemetry replay hardening against a captured-poll adversary, or protection against jamming. Local UDP/USB/UART peers remain within the trusted installation boundary.

Only the current apps' v1 controls/ARM/DISARM and selected small telemetry are supported. Bulk parameter/mission operations, generic v2 controls, external servo commands and video are excluded. Board frequency/variant/key are explicit provisioning inputs; default gateways do not transmit.

## USB joystick control update

Default base configuration is `TELERC_BASE_WIFI=0`. Android uses native ESP32-S3 CDC ACM via USB OTG with permission/endpoint validation; PC uses the USB relay. Radio polling carries the unchanged joystick MAVLink datagrams to the rover T3-S3 and dedicated motor ESP32-S3. Legacy UDP remains an explicit optional app transport. Android USB endpoint writes are serialized and bounded; partial write or device detach closes the link, while the motor watchdog remains independent. Physical Android USB enumeration and phone OTG supply tests remain pending.

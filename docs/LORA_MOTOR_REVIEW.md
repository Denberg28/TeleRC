# Four-BTS7960 PWM and LoRa joystick review

Firmware baseline: `19ffbb903bd447e151ac678510f9aa4ceec99866` (the preceding LoRa setup review on draft PR #6). Retained main baseline: `51e98a0797c476647dea88cbebdee6a52503c2a6`, Android 0.8.52/code 70. Work branch: `review/lora-motor-sanitize`; delivery extends `review/lora-setup-ui`. Scope: PWM companion, shared motor startup, radio mailbox/failure handling, blank-board provisioning, meaningful regressions and compile gates. No version/signing changes or release publication.

## Findings and corrections

| Severity / area | Observed defect or mismatch | Correction / evidence |
| --- | --- | --- |
| High — sketch selection | PWM companion accepts FC pulses only; cannot decode T3-S3 UART joystick frames | Use dedicated `TeleRCMotorController` for DIRECT LoRa control; document separate FC-only companion |
| High — PWM startup/recovery | Fresh displaced input could move immediately after boot or signal recovery | Require fresh neutral ±35 µs for 300 ms before admitting PWM source; regression reproduced before fix |
| High — PWM loss | Zero target still left nonzero current PWM during slew-down | Hard-zero duty/current at next control iteration for invalid/stale required input; regression reproduced before fix |
| High — sparse radio axes | Latest whole packet overwrote a previous sparse steering/throttle packet before a slot | Coalesce touched channels separately, preserve each 200 ms timestamp, rebuild valid CRC; regression reproduced before fix |
| High — stop priority | DISARM left queued drive; a release/DISARM could overwrite the other stop operation | Clear motion, retain separate release and DISARM slots, block motion/ARM until dispatch; test both arrival orders |
| Medium — input capture | Missing rising edge or non-atomic width/time snapshot could manufacture freshness | Track rise/validity, invalidate malformed pulses, snapshot under capture lock; test orphan/invalid/wrap |
| Medium — resource/error handling | PWM attachment and radio RX restart outcomes were ignored; short UART writes uncounted | Inhibit failed PWM setup; mark radio inactive, clear challenge/commands after failed RX restart; count short writes |
| Medium — provisioning | New strict Android parser rejected blank firmware's inactive frequency 0 | Permit unset frequency only on inactive boards; test actual blank INFO and Save eligibility |
| Low — build/diagnostics | PWM folder lacked matching Arduino entrypoint; repeated print calls could block | Add comment-only entrypoint, bounded capacity-checked diagnostics; compile both input configurations |

Dedicated/hybrid output GPIOs are now set LOW at the beginning of `setup()`, before startup delays/network work. Existing independent 500 ms DIRECT-axis watchdogs, explicit neutral ARM, selector arbitration, release semantics and 300 ms neutral handover are retained. External pulldowns are still needed before `setup()` executes.

## Correct installed command path

Android joystick → USB OTG → BASE T3-S3 → authenticated direct LoRa → ROVER T3-S3 → UART → dedicated motor ESP32-S3 → four BTS7960s. Exactly one removable communications gateway connects to the fixed motor MCU. The BASE and ROVER use their respective `TeleRCLoRaBase` / `TeleRCLoRaRover` sketches; the motor uses `TeleRCMotorController`. FC-PWM-only installations use `BTS7960PWMConverter` instead and follow its distinct input/recovery policy.

| Signal | From | To |
| --- | --- | --- |
| Commands | ROVER T3-S3 TX GPIO43 | Motor RX GPIO21 |
| Return telemetry | Motor TX GPIO39 | ROVER T3-S3 RX GPIO44 |
| Reference | ROVER ground | Motor/common logic ground |
| FL driver | Motor GPIO8 / GPIO9 | RPWM / LPWM |
| RL driver | Motor GPIO10 / GPIO11 | RPWM / LPWM |
| FR driver | Motor GPIO12 / GPIO13 | RPWM / LPWM |
| RR driver | Motor GPIO14 / GPIO15 | RPWM / LPWM |
| Selector | Motor GPIO16 LOW/open | DIRECT (HIGH selects FC AUTOPILOT) |

UART is 115200 baud, 3.3 V logic. Native USB CDC must be enabled on T3-S3 so USB does not occupy GPIO43/44. Radio GPIOs remain the manufacturer mapping; SX1262 SPI SCLK5/MISO3/MOSI6/CS7, reset8/DIO1=33/BUSY34 are internal to each T3-S3, not motor wiring. Confirm the installed board/radio revision. [Manufacturer reference](https://github.com/Xinyuan-LilyGO/LilyGo-LoRa-Series/blob/master/examples/LoRa/T3S3/SX1262_PingPong/SX1262_PingPong.ino).

## Operation and Meshtastic alignment

The compact centered UI follows connect → read local device → configure radio/key → save ACK → restart → reconnect/verify → check remote heartbeat → explicit Enable/ARM. Show/Hide applies to the shared pairing key, automatically remasking on pause/navigation/disconnect. It is a 32-byte HMAC key rather than a Wi-Fi password. A matching 16-bit fingerprint is only a comparison hint; radio heartbeat/control acceptance establish more useful connection evidence.

TeleRC remains its own point-to-point authenticated protocol: no Meshtastic mesh, BLE pairing, region/preset API or payload encryption. Fixed SF7/BW500/CR4:5 and one-use 90 ms responses retain the current timing. See [the setup review](LORA_SETUP_REVIEW.md) for UI checks and authoritative Meshtastic references. Sparse axis values expire separately after 200 ms in the BASE mailbox; ignored values never renew another axis. DISARM/release cancel queued motion, and discovery/heartbeat never renew downstream motor axes. Failed RX restart requires board restart, then fresh commands and explicit authority; no automatic motor re-arm.

## Compatibility and rollback

CH1 steering, CH2 bidirectional drive, 1500 µs neutral, UART/radio datagrams, identity/target validation, radio pins/profile, NVS schema and key remain unchanged. Android/PC existing frames remain compatible. Deploy the updated BASE for sparse coalescing/stop priority, updated ROVER for RX-failure handling, and updated dedicated motor for earliest software GPIO-low startup; test the installed set together. No key erase or forced re-pair is required. PWM-companion users gain a new neutral startup/recovery requirement and immediate fault stop; FC inputs must provide neutral before movement.

Rollback by rebuilding the retained matching baseline on an isolated branch and reflashing the correct device roles with power removed. Restore the BASE/ROVER pair together when investigating protocol behavior; preserve NVS/key and private configuration. Reflashing the PWM companion over the dedicated motor removes UART control. App signing/version are unchanged; no downgrade/install artifact is produced here.

## Gates A–F and evidence

| Gate | Status at local review | Evidence / remaining check |
| --- | --- | --- |
| A — source/build | PASS source checks; BLOCKED local target build | Generated motor sync and diff checks pass. Actual ESP32/Android builds use draft-PR CI; record successful run IDs on PR before delivery |
| B — behavior | PASS simulator tested; BLOCKED hardware | Actual PWM sketch and dedicated motor parser run in host stubs, including neutral startup/loss/recovery, all four outputs, authentication/UART chain and stop priority; phone/radio behavior requires bench checks |
| C — regression | PASS host subset | 21 C++ executions (4 hybrid, 4 PWM, 2 LoRa-to-motor, 8 gateway, protocol, setup, Wi-Fi) and 4 Python tests; Android unit tests/lint/debug build required in CI |
| D — integration | PASS host protocol chain; BLOCKED physical | Real framing/HMAC/challenge/mailbox/UART/motor code exercised; no measured RF airtime, native USB, voltage/driver or actual PC compatibility |
| E — HIL | BLOCKED | No connected phone/T3-S3/motor/FC hardware; execute procedure below |
| F — delivery | Source review only | Draft PR, changelog, compatibility and rollback; no signed candidate, stable promotion or field validation |

Reviewed and simulator-tested evidence is distinct from build-verified CI and hardware-tested/field-validated evidence. Local default `SANITIZE=1 bridge/tests/run.sh` cannot inspect sandbox process tasks: LeakSanitizer fails under tracing. ASan/UBSan can run with `ASAN_OPTIONS=detect_leaks=0`; keep full default sanitizers in CI. Local SDK/Arduino toolchains are unavailable. CI must compile seven primary ESP32-S3 targets and three alternates with ESP32 3.3.2 / RadioLib 7.2.1, and run Android unit/lint/debug build. Source inspection or host stubs alone do not satisfy actual-target compilation.

## Executable restrained-wheel acceptance

Record firmware commit and device/radio revisions, configuration without keys, battery/supply voltage, capture timestamps, mode, last valid axis/pulse and fault time. Begin with motor battery disconnected. Verify logic levels, common ground, driver-enable/reset-safe input state and pin routing. For powered tests raise/restrain wheels, reduce maximum duty, keep physical cutoff reachable and use a logic analyzer (at least 1 kHz capture; substantially faster to inspect 20 kHz PWM). The limits below are provisional acceptance criteria, not measured promises. Measure wheel stop/coast separately.

| Check | Procedure | Required result |
| --- | --- | --- |
| DIRECT boot | Flash correct three roles; power motor/ROVER with BASE absent; repeat reset with joystick displaced | All eight motor outputs zero; no automatic arm |
| Provisioning/UI | Read inactive blank BASE/ROVER, confirm frequency unset; Save same frequency/power/key, receive ACK, restart/read; wrong partner key/frequency; Show then leave tab/background | Blank board can Save; active board cannot; remote heartbeat distinguishes radio link; key remasks; no unsolicited Enable/ARM |
| Four-driver mapping | Connect USB BASE, hold CH1/CH2=1500 for ≥300 ms; explicitly Enable and ARM; command low forward/reverse, then steer at neutral drive | Correct FL/RL/FR/RR directions; pivot sides oppose; RPWM/LPWM never active simultaneously |
| Independent axes | Drive, stop CH2 updates while continuing sparse CH1 updates; repeat with CH1 absent; keep discovery/heartbeat flowing | All outputs zero and DIRECT disarmed within provisional 550 ms of the last missing-axis command |
| Radio/UART loss | Separately disconnect ROVER TX, turn off BASE, interrupt RF, detach phone; restore with displaced joystick | Same 550 ms criterion; recovery remains stopped until neutral dwell and explicit new Enable/ARM |
| Stop priority | Send drive, then sparse release and DISARM before next radio slot; repeat reversed order; follow with more drive/ARM | Stop operations survive; queued motion is not dispatched; receiver-release behavior retained; no automatic restart |
| Invalid/replay/error | Inject corrupt UART frame, wrong-key/old/duplicate RF response; use diagnostic/backpressure test fixture | No freshness renewal or arm; independent watchdog stops motion. RX restart failure marks inactive; restart/verify before control |
| PWM companion | Use its separate FC-input firmware; boot with required pulses at 1800, then neutral ≥300 ms, then low movement; remove/corrupt each required pulse | Displaced boot/recovery stays zero; valid neutral permits source; all-stop output zero within provisional 160 ms of last valid pulse (invalid pulse within next control cycle) |
| Source/FC | On dedicated motor only, change GPIO16; test neutral dwell, FC M5–M8 wire loss and receiver takeover using existing TESTING.md | Immediate edge stop; mode-specific authority and independent FC/PWM watchdogs preserved |
| Electrical/range | Reset/brownout each MCU, check unpowered/reset inputs, motor-noise supply behavior and physical cutoff; only then increase distance/obstruction | No unexplained motion; capture worst command gap/latency/loss. Any 500 ms DIRECT gap stops, without extending watchdog |

Next concrete check: successful final draft-PR CI, followed by installed-device provisioning and mapping/loss tests above. Hardware readiness remains BLOCKED until measured evidence is recorded.

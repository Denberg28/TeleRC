# TeleRC 0.8.80 development — F405 ELRS and clear Waypoint flow

Baseline: published 0.8.79 source `86ef96319b6fe97147ca4b071de510cf3bb1ac4e` (local snapshot `96bd7ca5ded86ba6862839464fcfc965478adac6`), app 0.8.79/code97, branch `work/elrs-waypoint-080`. Version increases to 0.8.80/code98; app ID, permissions and signing configuration are retained. This is a test candidate; software gates are required before publication and hardware qualification remains blocked.

The initial working tree already contained standalone Mini ELRS firmware and app authority work. That local work is preserved separately; the published app-only source retains the baseline firmware. This change does not redirect Mini receiver pins to the flight controller or qualify that optional firmware. The visible connection menu focuses on the requested Mini Wi-Fi and FC paths. No flashable firmware is delivered by this change.

## Choose the connection

| Setup choice | Phone link | Receiver / transmitter | Control and Waypoint |
| --- | --- | --- | --- |
| Mini rover · Wi-Fi | Phone → ESP32-S3 Wi-Fi | No FC required | Existing mini manual control; Waypoint planning only |
| ArduRover · Wi-Fi bridge | Phone → Wi-Fi gateway → F405 | Optional ELRS CRSF RX → F405, bound handset | Phone RC override with deliberate FC/handset handover; live Waypoint requires a transparent compatible link |
| ArduRover · LoRa USB base | Phone USB OTG → LoRa BASE → ROVER → F405 chain | Optional ELRS CRSF RX → F405, bound handset | Existing manual LoRa; current gateway filters navigation, so planning only |
| ArduRover · ELRS TX Backpack | Phone Wi-Fi → TX Backpack → ELRS TX → RX → F405 MAVLink UART | MAVLink-capable TX/RX pair and TX Backpack | Handset manual control, phone telemetry/FPV; phone motion/arm/navigation commands locked |

A phone does not generate ELRS RF. Standard CRSF RC mode, native ELRS MAVLink, AirPort and the custom TeleRC LoRa protocol are distinct. Native ELRS USB and standalone telemetry-radio operation are not implemented by this update. A listed path is protocol support, not a measured RF/driver/phone reliability claim.

## F405 V4 wiring and handset handover

| ELRS receiver signal | SpeedyBee F405 V4 |
| --- | --- |
| TX | R2 (uninverted UART2 RX) |
| RX | T2 (UART2 TX) |
| GND | GND |
| Power | Supply within the exact receiver's rated voltage |

Reserve the existing UART3 Wi-Fi/LoRa gateway and UART6 GPS. M100 compass uses the FC's appropriate I2C connection. Do not attach a second device to a UART already in use, and do not use the inverted SBUS pad for CRSF/MAVLink. This guide targets F405 V4, not the Wing boards.

For normal CRSF, configure `SERIAL2_PROTOCOL=23`, `RSSI_TYPE=3`, and CRSF in `RC_PROTOCOLS` (bit9 / value512 if selecting CRSF alone). Current ArduPilot guidance sets `RC_OPTIONS` bit13 for ELRS 420 kbaud; confirm availability/behavior in the installed ArduRover build, preserve other required bits, reboot and verify receiver calibration. Do not replace the full bitmask blindly. Receiver and transmitter must use compatible firmware, the same band/binding setup, CH1 steering and centered CH2 forward/reverse.

Assign a spare receiver channel to `RCx_OPTION=46` (RC Override Enable). LOW inhibits MAVLink RC overrides, HIGH permits them. Center handset controls before switching or releasing phone control. Also assign and test a transmitter **HOLD mode switch**; disabling RC override alone does not cancel Guided navigation. Review `SYSID_MYGCS=255` for the 4.6.3 path (`MAV_GCS_SYSID` on newer versions), finite `RC_OVERRIDE_TIME`, and independently tested RC/GCS loss actions. Leave Ignore Receiver / Ignore MAVLink Overrides disabled. TeleRC does not write parameters or bind radios.

The phone can enable normal FC joystick override through Wi-Fi/LoRa while CRSF stays linked. STOP CONTROL sends neutral then releases override to the receiver. A non-neutral handset can command movement on release or override timeout; receiver release is not a motor-off guarantee. Use the transmitter HOLD switch for an independent stop. Reconnection never grants phone control or arms.

## Native ELRS Backpack

Configure a compatible TX/RX pair for MAVLink, and set Backpack → Telemetry → WiFi in the ELRS Lua menu. Minimum documented versions are ELRS3.5 and Backpack1.5. In ELRSv3 explicitly set receiver serial protocol; ELRSv4 configures it with TX Link Mode. Use the correct RF band and region for the hardware. Firmware-update Wi-Fi is not telemetry forwarding.

For the receiver UART, ExpressLRS currently specifies `SERIAL2_PROTOCOL=2`, `SERIAL2_BAUD=460`, `RSSI_TYPE=5`; confirm the actual receiver UART baud. Some ArduPilot wiki examples show115, so do not mix receiver firmware defaults. Configure/verify telemetry stream rates in Mission Planner against available RF throughput. Slow 900MHz modes may not satisfy the freshness requirements of other features.

AP defaults: Backpack IP `10.0.0.1`, password `expresslrs`; **TeleRC Send14555 → Backpack Listen14555**, **Backpack Send14550 → TeleRC Listen14550**. Both ports and the IP are editable. For router/organization Wi-Fi use the assigned Backpack IPv4 address and ensure client isolation/firewall policy permits both UDP directions. ELRS endpoint preferences are separate from the existing FC Wi-Fi address/port.

The session captures its port/profile and binds the phone socket to Wi-Fi. It accepts only the configured peer IP + send-port as telemetry source and then validates MAVLink source/CRC as before. The8192-byte receive buffer handles batched MAVLink datagrams beyond the old512-byte buffer. A bounded foreground GCS heartbeat registers the phone with the Backpack; bridge discovery/disconnect text is not sent. Heartbeats stop after a known FC heartbeat goes stale and while backgrounded.

**Why native phone commands are locked:** upstream ELRS `SerialMavlink::sendRCFrame` emits handset `RC_CHANNELS_OVERRIDE` at10ms intervals. Simultaneous phone overrides would contend; no claimed seamless arbitration exists here. An immutable session transport accepts only the exact checksum-valid TeleRC GCS heartbeat. Neutral, release, ARM/DISARM, Guided/HOLD and bridge text are rejected even if another UI path attempts them. Handset arming/stopping remains on the transmitter. Use CRSF + separate FC Wi-Fi/LoRa for phone takeover with a persistent handset link.

## Waypoint placement and wording

- Left: explicit Single point / Mission chooser, saved points, visible Undo and Clear; Review point / Review route fixed below.
- Center: large map, short point count/planning status, Center map and Add coordinates.
- Right: three short steps, current blocker or navigation progress, Help & safety, with red STOP · HOLD fixed at the bottom.
- Tapping a single point saves the selection. Review opens the confirmation after readiness passes; Start rechecks the immutable route and current readiness. No destination dispatch from a map tap.
- Setup details, limits and failsafe parameter names live in Help. No firmware safety check, supervision limit, ACK/mode/target sequence, automatic-resume prohibition or live-link restriction is removed. Existing `WaypointNavigation`, `WaypointProtocol`, manual codec, FPV, Test and route-session sources remain protected.

## Acceptance criteria and evidence

| Gate | Status | Evidence |
| --- | --- | --- |
| A source/build | Pending final Android checks | Version98; scope guard; JVM, debug/release lint/build and native test compilation |
| B behavior | Pending JVM | Peer pinning, distinct ports, batching and immutable telemetry-only transport rejects commands; existing navigation and control regressions |
| C regression | PASS with sanitizer limitation | Motor sync; bridge/default host; mini host;20 driver/count/API profiles; packaging unit tests. Default LeakSanitizer blocked by container `/proc` restriction; ASan/UBSan with leak inspection disabled PASS |
| D integration | NOT TESTED | No actual ELRS TX/RX/Backpack or FC and no full ArduRover SITL attached |
| E phone/HIL | BLOCKED | Native emulator checks run in the release workflow; physical phone/USB/rover equipment unavailable |
| F package | NOT PUBLISHED | No stable promotion, signed APK or actual-board firmware qualification claimed |

Build/test results below supersede pending software statuses only. No software test proves real RF performance or stopping distance.

## Restrained-wheel HIL procedure

1. Back up FC parameters and installed app/source versions. Restrain all wheels and keep motor power independently switchable. Verify PWM source selector, directions, neutral and manual emergency stop before applying drive power.
2. Test Mini Wi-Fi unchanged, then FC Wi-Fi and LoRa CH1/CH2, arm/disarm confirmation, stop, background, lost heartbeat and reconnect. Reconnect must leave phone control disabled. No firmware flash is required for the app changes.
3. With CRSF on R2/T2, calibrate handset CH1/CH2; verify receiver link remains active while phone telemetry is connected. LOW override switch must block phone joystick output. Center both sources, switch HIGH and explicitly enable phone control. Test LOW/HOLD override during input and phone STOP handover. Record actual FC/PWM outputs.
4. Interrupt phone Wi-Fi/LoRa with handset centered, then non-neutral under restrained conditions. Measure which FC loss/override timeout action occurs and the stopping time. Interrupt receiver separately. Restore either link and verify no automatic phone enable/arm; transmitter behavior follows FC configuration.
5. Configure native ELRS MAVLink and Backpack. Verify telemetry source IP/port, both UDP directions, router/AP modes and stable FC heartbeats. Move handset controls while using all app tabs. Phone enable/arm are locked; Check rover stays planning-only; packet capture must show only phone GCS heartbeats, never neutral/release/arm/HOLD/reposition or TeleRC ASCII discovery.
6. Stop/background/reconnect native telemetry. The handset must keep authority without a transient phone neutral/release. Test telemetry-only return path loss and resumed foreground. Verify no automatic motor or authority transition. Confirm FC RC/GCS loss action independently.
7. Test Waypoint editing/clear/cancel/draft persistence across Light/Sepia/Dark, compact landscape and large fonts. Map taps must not command movement. Verify planned-only explanations match Mini, LoRa and ELRS. For a separately qualified transparent FC link, run the unchanged `WAYPOINT_079.md` navigation/failsafe acceptance procedure with the transmitter HOLD switch.

## Install, rollback and next check

When a signed candidate is later requested, use the retained release key and code98 or higher for an in-place update. Do not distribute the unsigned local release build. Retain phone settings, wallet and drafts. Rollback is a forward-version rebuild of the baseline with wallet compatibility; do not uninstall/clear data or force a downgrade. ELRS preferences use separate keys; they can remain unused by rollback. Firmware rollback is outside this app-only change; preserve the previously working sketch/parameter backup.

Next: complete software gates, native UI review, then the actual F405/ELRS handover and loss tests above. Hardware gates remain BLOCKED until measured evidence exists.

## Primary references reviewed 18 October 2026

- https://www.expresslrs.org/software/mavlink/
- https://ardupilot.org/rover/docs/common-tbs-rc.html
- https://ardupilot.org/rover/docs/common-speedybeef4-v3.html
- https://ardupilot.org/dev/docs/mavlink-rcinput.html
- https://github.com/ExpressLRS/Backpack/blob/master/lib/WIFI/devWIFI.cpp (blob26b4013f22cfc5599d3c815b259dfdddd7a020e0), `lib/config/config.cpp` defaults
- https://github.com/ExpressLRS/ExpressLRS/blob/master/src/src/rx-serial/SerialMavlink.cpp (blob1f492759d37b5b86c5632dcb95318e9685a285cb)

## Authorized release build

The owner requested development and release packaging on18 October2026. The dedicated branch workflow rebuilds the current source with the existing release secret, runs full JVM/debug/release lint/build, host/default sanitizers and native API35 UI checks, verifies the signer/package/version against published0.8.79, and publishes only a prerelease with APK, source, check evidence and checksums. No firmware flash or stable promotion is claimed. Workflow/BUILD_INFO results supersede pending software rows above; actual FC/ELRS/phone HIL remains BLOCKED.

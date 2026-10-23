# TeleRC 0.8.81 — ELRS recovery and handset authority

Baseline: software-qualified 0.8.80/code98, commit `944f847e62316e82143899cc39e8dc4f55518c32`, build run `39122672708`. Candidate: 0.8.81/code99. The owner authorized sanitization and release on 18 October 2026. This is an app-only signed test candidate; physical qualification remains pending.

## Changes

- Retry Backpack registration at most once per second while the app is foregrounded and connection is requested, even after a previously received F405 heartbeat becomes stale. The former heartbeat-dependent retry could prevent recovery after peer/IP changes.
- At the same bounded interval, check whether the actual Android Network used to bind the socket is still present. If removed, close through the existing session teardown and retry connection after the existing two-second interval. A new session binds to available Wi-Fi. Ordinary heartbeat gaps and Network capability changes do not tear down the socket.
- Show UDP-open/waiting, live F405 telemetry, stale F405 telemetry, and Backpack reconnection separately. HANDSET CONTROL describes the selected authority policy; it does not prove current RF reception or motor output.
- Keep connection intent visible across the UI/worker threads. Stop, background and session replacement never restore phone control or arm/navigation requests.

## Connection and authority

Phone Wi-Fi ↔ TX Backpack ↔ ELRS TX ⇄ ELRS RX ↔ SpeedyBee F405 V4. Native ELRS remains handset-controlled. TeleRC sends only the existing checksum-valid MAVLink PING registration, not a GCS heartbeat. The immutable telemetry transport continues to reject phone overrides, neutral/release, ARM/DISARM, HOLD/navigation and proprietary bridge messages. This update does not enable phone takeover, mission upload or live Waypoint over native ELRS.

Use ELRS MAVLink mode and Lua → Backpack → Telemetry → WiFi. Firmware-update Wi-Fi is a different service. AP defaults: address 10.0.0.1; phone Send14555 → Backpack Listen14555; Backpack Send14550 → phone Listen14550. Router mode uses the Backpack's actual assigned IPv4 address, which must be updated if DHCP changes it. A replacement Android Network can be rebound automatically; the app does not infer a different Backpack address, bypass Android Wi-Fi approval, switch to cellular or promise reconnection timing.

The F405 V4 wiring and UART settings remain as documented in [0.8.80 setup](ELRS_WAYPOINT_080.md): receiver TX→R2, RX→T2, common GND and rated receiver supply; MAVLink UART protocol/baud must match the receiver. Reserve the separate GPS UART and compass connection. The phone must remain near the Backpack; ELRS provides the rover RF link.

Phone Wi-Fi/app/Backpack loss leaves handset authority unchanged. ELRS RF loss must be handled independently on the F405: review finite override timeout, RC-loss detection and a HOLD loss action in the actual firmware. Phone registration is not GCS heartbeat supervision. Validate any already configured GCS failsafe separately. RF recovery must not be treated as permission to restart driving. Measure the real installed motor/driver stop behavior.

For currently supported phone/handset handover, use normal ELRS CRSF into the F405 plus a separate compatible FC Wi-Fi/LoRa connection. RCx_OPTION46 controls override permission; a separate handset HOLD mode switch interrupts Guided navigation. Existing filtering LoRa gateways remain planning-only. Native single-link mission support requires a separately designed and tested authority/mode contract.

## Verification gates

| Gate | Evidence/status before the final workflow |
| --- | --- |
| A source/build | Reviewed; local Android SDK/Gradle environment BLOCKED; cloud JVM/debug/release lint/build required |
| B behavior | New JVM outage/retry/background/disconnect/network-removal/command-guard regressions and native UDP/live/stale authority states required |
| C regression | Existing protected navigation engine/codecs/Test/FPV/firmware scope checks, motor sync, host/mini/profile/default sanitizers required |
| D integration | Software emulator checks required; actual Android network replacement and complete RF/FC integration NOT TESTED |
| E physical HIL | BLOCKED: no phone, TX/RX/Backpack, F405 or rover attached |
| F package | Signed APK/code99, retained signer versus v0.8.80, source/evidence ZIPs, BUILD_INFO and SHA256SUMS required before public prerelease |

The successful final workflow and BUILD_INFO supersede pending software rows only. No firmware source or actual ESP32 target changes; no firmware reflash is needed. Local checkout is a connector-materialized source subset, not a complete buildable repository; final required checks run against the complete cloud checkout.

## Restrained-wheel acceptance

1. Back up FC parameters, current APK version and drafts. Restrain wheels and provide an independent motor-power cutoff. Verify handset neutral, directions, arm/disarm and HOLD before drive testing.
2. Connect native ELRS and TeleRC. Verify configured IP and both UDP ports. Confirm live F405 telemetry, disabled phone drive/arm and planning-only Waypoint. Capture phone UDP: only valid registration PINGs, at most one per second, must leave the app.
3. Interrupt FC telemetry for over eight seconds without dropping phone Wi-Fi. Confirm stale status while PING attempts continue and handset authority remains unchanged. Restore telemetry; live status must return without Enable/ARM/mission output.
4. Toggle phone Wi-Fi off/on or restart the Backpack so Android replaces its Network. Confirm the old socket closes and a new socket binds after Wi-Fi is available. Repeat ten times; record recovery delay, Network changes and any manual Android Wi-Fi prompts. A changed Backpack IPv4 address still needs explicit correction.
5. Background/foreground TeleRC, disconnect explicitly, then connect again. Background and explicit disconnect must stop registration. Foreground resumes registration only when connection is still requested. No phone neutral/release/ARM/navigation packets may appear during these transitions.
6. Separately interrupt ELRS RF with wheels restrained. Verify F405 HOLD and measure output/stop timing; restore RF and use deliberate handset mode selection. Repeat with the phone disconnected. Do not count phone telemetry recovery as an RF failsafe test.
7. Verify existing Mini Wi-Fi, FC Wi-Fi/LoRa, Controls/Test, FPV reconnect, wallet and Waypoint planning remain usable. Physical checks are independent of passing automated tests.

## Install, rollback and next check

Install the signed APK over the existing app. Application ID, permissions, signing configuration, wallet and settings formats are retained. No firmware update is included. Keep the previous APK/parameter backup; rollback after installation uses a forward-version rebuild of the baseline with existing wallet compatibility, rather than uninstalling or forcing a lower version code.

Next: verify the final source/tag/checksums and perform steps 1–6 above on the phone/F405/ELRS hardware. The candidate must not be labeled field-validated or stable from software checks alone.

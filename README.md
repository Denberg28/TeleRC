# GoSim 0.1.0 — private prototype

Offline Android landscape controller for an ArduPilot vehicle with a **MAVLink UDP bridge** on the same local Wi-Fi network. Target: Android 8+, IPv4 UDP endpoint at port 14550. No cloud account, location, Bluetooth, or internet service is required. The Android INTERNET permission permits local UDP sockets.

## Setup and flow

1. Configure the bridge to forward full MAVLink packets in both directions to the Android device on UDP 14550. Set the vehicle MAVLink stream to include HEARTBEAT at least once per second. Configure ArduPilot RC override acceptance and a tested RC/GCS failsafe separately.
2. Remove propellers. Connect phone to the bridge Wi-Fi. Enter its IPv4 address and port and tap CONNECT.
3. Wait for LINK ACTIVE; move roll, pitch, throttle, yaw sliders. Non-throttle axes center when released. Release Control disconnects. Backgrounding the app also disconnects.
4. On a timeout (>1.5 s without heartbeat), transmission stops. Reconnection requires a new heartbeat. This prototype does not arm or switch modes.

**Bench/SITL only.** Loss of Wi-Fi, a crashed app, malicious local traffic, or a mismatched ArduPilot configuration can defeat user expectations. ArduPilot must provide independent RC override timeout and failsafe settings. There is no authenticated MAVLink signing or validated telemetry yet. Do not fly or drive a live craft with this version.

## Acceptance criteria

- Valid IPv4 and port retained locally; invalid input rejected.
- Receives a vehicle heartbeat from the configured IPv4 endpoint before sending any RC override.
- Sends channels 1–4 in 1000–2000 µs at no more than 10 Hz; channels 5–8 ignored.
- Stops transmission within 1.5 s of lost heartbeat, on pause, or on disconnect.
- No arm command, auto connection, telemetry storage, or remote dependency.

## Build

JDK 17, Android SDK platform 35, Gradle 8.11.1. Run `gradle :app:assembleDebug :app:testDebugUnitTest`. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Install with `adb install -r ...`. Gradle wrapper is not included because this environment cannot fetch and validate the official distribution; CI pins the Gradle action version. Never use an APK from a different signing identity to upgrade an installed app: back up settings and uninstall the old build first. Release signing keys must remain outside Git and be reused for subsequent releases.

## Assumptions and decisions

- Transport MVP: local Wi-Fi UDP to an ESP32/Raspberry Pi MAVLink bridge; direct ESP32 serial, USB flight controller, and ELRS are deferred. ELRS is a radio protocol, not an Android transport; its transmitter hardware and integration need separate design.
- Generic roll/pitch/throttle/yaw sliders suit SITL and bench checks. Vehicle-specific mappings and a gamepad layout need field research.
- A single known endpoint, an observed heartbeat, and lifecycle shutdown reduce accidental output; they do not authenticate the sender.
- Private test first; no public release until hardware-in-loop failsafe, message validation, and signing continuity are verified.

## Status and next milestone

Source implemented. No local Android SDK/Gradle or device was available at creation, so compilation, APK installation, real packet timing, and flight controller behavior remain unverified. Next: build in CI, test against ArduPilot SITL with a UDP bridge, then bench test with propellers removed. Add validated MAVLink framing/signing and a dedicated two-axis gamepad UI before vehicle testing.

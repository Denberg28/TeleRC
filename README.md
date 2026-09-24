# TeleRC 0.5.0 — rover joystick private test

Offline Android controller for an ArduRover using a bidirectional MAVLink UDP bridge on the same local Wi-Fi network. Android 8+, IPv4. The app contains no cloud service or account. The first enabled craft profile is **Rover**; multirotor, fixed wing, watercraft, and rocket are named future profiles and have no active controls.

## Rover setup

1. Configure the bridge to forward complete MAVLink packets in both directions on one configured UDP port, with HEARTBEAT at least once per second. The bridge must send from the same IP **and port** entered in TeleRC. Default is 192.168.4.1:14550. The phone binds the same UDP port locally.
2. Configure ArduRover default RC input mapping, steering CH1 and bidirectional throttle CH3, with 1500 µs neutral for each. Verify the vehicle's motor directions, deadband, `RC_OVERRIDE_TIME`, GCS/RC failsafe, and independent emergency stop before connecting. TeleRC does not configure these parameters.
3. Raise wheels off the floor for initial checks. Connect phone to bridge Wi-Fi. In portrait Setup & Config, enter the endpoint and tap Connect. Open landscape Controls, then tap **Enable control** after a checksum-valid heartbeat. Both joysticks return to 1500 when released.
4. Tap Stop / Disable Control or leave the app to send a best-effort neutral frame followed by MAVLink release override. A lost heartbeat disables control within 1.5 seconds; vehicle failsafe remains essential.

## Offline test drive

The third, landscape **Test drive** tab is an offline endless retro rover course. Hold drive forward to approach randomly placed obstacle gates, steer through the gap, and watch the OBSTACLES counter. A collision resets the run; release drive before starting again. Steering and drive respond directly and the stick display centers immediately on release. Its steering (CH1) and drive (CH3) joysticks use the same direction, range, deadband, and spring return as the live controls. Steer while driving to turn, pull down to reverse, and tap Reset course to return to the start. The test scene has its own inputs and never sends MAVLink or requires a connection. Switching tabs disables live control.

**Private bench/SITL tests only.** UDP endpoint and checksum checks do not authenticate a vehicle. No MAVLink signing, telemetry validation, arm/mode controls, vehicle configuration, or tested link failsafe is provided. Never use this prototype to control a moving vehicle or flight craft.

## Architecture and future craft profiles

`CraftProfile` identifies available profiles. `RoverControls` owns the rover RC channel mapping and neutral values. `Mavlink` frames the transport-independent channel output; `MainActivity` owns UDP lifecycle and UI. Each future craft requires its own mapping, neutral/failsafe behavior, interaction design, and bench/SITL evidence before enabling. Rocket control requires a separate safety design and is explicitly unavailable.

The first build uses local Wi-Fi UDP to an ESP32/Raspberry Pi MAVLink bridge. Direct USB serial, Bluetooth, ELRS hardware integration, and other transport layers are outside this build. ELRS itself is not a phone transport.

## Acceptance criteria

- Invalid IPv4 address and port are rejected; valid endpoint persists locally.
- No override is sent until a checksum-valid heartbeat arrives from the configured IP and source port **and** the user enables control.
- Default rover mapping sends CH1 steering, CH3 bidirectional drive, CH2/CH4 neutral; CH5–8 ignored; at most 10 Hz.
- Both joysticks center on touch release. Disable, disconnect, and app pause attempt a neutral frame and release override. Lost heartbeat stops periodic output and disables the controls.
- Other craft profiles cannot be selected or operated. The offline test course cannot send vehicle commands.

## Build and status

JDK 17, Android SDK platform 35, Gradle 8.11.1. Run `gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon`. APK path: `app/build/outputs/apk/debug/app-debug.apk`. The GitHub workflow builds and uploads this debug APK on push. This environment did not have Gradle or Android SDK, so no local APK or installation test was completed. Debug APK signing is only suitable for private testing; retain a stable signing key for future upgrades. Preserve application ID `io.github.denberg28.telerc` and version progression. Test with SITL and a bridge, then raised-wheel bench testing, before any further vehicle use.

## Direct updates

Setup & Config has a manual Check for updates button. It reads public GitHub release metadata without a credential, verifies the downloaded APK and opens the system installer. See [UPDATES.md](UPDATES.md) for signing secrets and first-install constraints. No signed release has been published by this source package.

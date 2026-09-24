# TeleRC 0.7.2 — rover joystick private test

Offline Android controller for an ArduRover using a bidirectional MAVLink UDP bridge on the same local Wi-Fi network. Android 8+, IPv4. The app contains no cloud service or account. The first enabled craft profile is **Rover**; multirotor, fixed wing, watercraft, and rocket are named future profiles and have no active controls.

## Rover setup

1. Configure the bridge to forward complete MAVLink packets in both directions on one configured UDP port, with HEARTBEAT at least once per second. The bridge must send from the same IP **and port** entered in TeleRC. Default is 192.168.4.1:14550. The phone binds the same UDP port locally.
2. Configure ArduRover default RC input mapping, steering CH1 and bidirectional throttle CH3, with 1500 µs neutral for each. Verify the vehicle's motor directions, deadband, `RC_OVERRIDE_TIME`, GCS/RC failsafe, and independent emergency stop before connecting. TeleRC does not configure these parameters.
3. Raise wheels off the floor for initial checks. Connect phone to bridge Wi-Fi. In portrait Setup & Config, enter the endpoint and tap Connect. Open landscape Controls, then tap **Enable control** after a checksum-valid heartbeat. Both joysticks return to 1500 when released.
4. Tap Stop / Disable Control or leave the app to send a best-effort neutral frame followed by MAVLink release override. A lost heartbeat disables control within 1.5 seconds; vehicle failsafe remains essential.

## Offline test drive

The third, landscape **Test drive** page has Game and Test modes selected with the compact bottom switch. The top mode tabs are removed to enlarge the course. Optional music is controlled by the small bottom music button in Game. Game is an endless retro obstacle course: drive forward and move directly left or right with the steering stick to pass gates. It saves the best gate count locally after each pass; a collision restarts the run. Optional generated tones are off by default and stop when leaving the scene. Test has no obstacles and previews CH1 steering and CH3 bidirectional drive with inertia and heading-dependent movement; steering alone cannot slide a stationary rover. This is an illustrative model, not a calibrated replica of any particular rover. Left and right turn its heading while moving; releasing drive brakes the preview quickly. Switching Game and Test preserves joystick input. Both modes remain offline and never send MAVLink. Switching pages disables live control.

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

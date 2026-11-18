# TeleRC — single-rover control and Wi-Fi mission planning

TeleRC is the Android controller in the TeleRC ecosystem, simplified around TeleRC Go's connect-and-drive workflow while retaining native USB LoRa and waypoint planning with acknowledged ArduRover Wi-Fi upload. PC-TeleRC owns fleet operations.

Signed test candidate: [TeleRC v1.2.0rc2 APK](https://github.com/Denberg28/TeleRC/releases/download/v1.2.0rc2/TeleRC-v1.2.0rc2.apk) · [matched firmware, source and checksums](https://github.com/Denberg28/TeleRC/releases/tag/v1.2.0rc2). Device/HIL validation remains pending.

## Use

- **Controls:** Connect the saved Wi-Fi or USB LoRa link, wait for a rover heartbeat, Enable Control, then ARM when required. Large spring-return sticks control CH1 steering and CH2 drive. STOP CONTROL releases to the receiver; Disconnect closes the link. The center map shows measured phone/rover positions, not simulated motion.
- **Mission:** tap the map or Add to enter coordinates; tap a list row to edit, move earlier/later, or delete. Plans autosave locally. Import/Export uses `sequence,latitude,longitude` CSV with consecutive 1-based sequence numbers and up to 100 waypoints. Clear and Import ask before replacing data. Planned routes are orange, phone tracks blue, rover tracks green. Choose **Upload Wi-Fi** to replace the onboard mission on a disarmed ArduRover through the matching `TeleRCBridge` firmware. Fresh heartbeat and physical CH7 phone permission are required throughout. Success requires the final rover acknowledgement; cancel/loss/edit/background stops the transfer and marks the onboard plan unverified. Upload does not arm or start navigation. Use the configured transmitter for AUTO and mission execution; verify the onboard route first. Mini rover and USB LoRa keep local editing only.
- **Setup:** use the always-visible Setup tab to select Wi-Fi UDP or native USB LoRa, choose ArduRover Wi-Fi, standalone Mini rover Wi-Fi or USB LoRa; connect, or open Wi-Fi settings / LoRa pairing. Endpoint editing is in **Wi-Fi endpoint** while disconnected; sensitivity, accent and diagnostics open compact dialogs. Tap the version to check updates.
- **LoRa setup:** provision/read back one T3-S3 at a time; connect the BASE for control. Existing roles, pairing and firmware remain compatible. Follow [the integration guide](bridge/LORA_INTEGRATION.md).

Map tiles require internet or existing cache. Coordinate editing, private plan storage and CSV work without tiles. Without accepted GPS the map camera uses a labeled sample area; no sample coordinate is recorded as Home or added without an explicit waypoint action. Allow precise location through Android settings when phone Home/tracking is needed.

The app removes the old game/test course, music, simulated vehicle settings, inferred positions and servo-assignment buttons. Existing private tracks and supported endpoint/response preferences are retained. Unsupported saved link profiles return to Setup with automatic connection off. Dedicated LoRa/motor firmware is unchanged; USB telemetry is capped at 16 pending frames with a 250 ms local backlog lifetime. Mission upload requires the updated legacy FC Wi-Fi bridge from this release; the dedicated Wi-Fi-to-motor gateway does not implement mission transfer; the legacy FC bridge retains its released permission checks. Application ID stays `io.github.denberg28.telerc`; retain the release signer for upgrades. No new runtime dependency is introduced.

## Build and validation

JDK 17, Android SDK 35, Build Tools 35.0.0 and the pinned Gradle 8.11.1 wrapper are required. Run:

```sh
./scripts/check-android-env.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
python bridge/sync_motor_core.py --check
bridge/tests/run.sh
SANITIZE=1 bridge/tests/run.sh
```

Release signing, debug/release lint/build, actual ESP32 compilation, checksums and packaging use the existing GitHub release workflow and existing secrets. Do not create a replacement signer. On Windows use `scripts/setup-windows.ps1`; APK output is `app/build/outputs/apk/debug/app-debug.apk`. Read [updates/signing](UPDATES.md), [QA and restrained-wheel checks](docs/TESTING.md), [architecture/compatibility](docs/ARCHITECTURE.md), [known limits](docs/KNOWN_ISSUES.md) and [manual-edit guide](docs/CODE_EDIT_GUIDE.md).

**Test candidate only:** physical safe states, radio/USB reliability, installation and full vehicle behavior require measured validation. Reconnect/background/tab transitions never automatically enable or arm. CH1/CH2 remain 1500 µs neutral, 1100–1900 µs at 100%, up to 10 Hz. A fresh authenticated radio path is distinct from a validated motor stop; keep independent downstream watchdogs and a physical power cutoff. Wi-Fi MAVLink is endpoint/checksum checked, not signed/authenticated.

## Project rules and rollback

The updated [kickoff](docs/PROJECT_KICKOFF.md), [AGENTS](AGENTS.md), [constitution](Marlou_Prompt_Constitution_v3.md) and [reusable template](Project_Kickoff_Template.md) record scope and workflow. Published 1.1.0rc1/code 1100 is the retained upgrade/rollback baseline; the new candidate is 1.2.0rc2/code 1201; retain 1.2.0rc1/code 1200 for app rollback. Export the plan and preserve existing data/settings before uninstalling; normal Android downgrade to an older versionCode requires uninstall/reinstall and can erase private data. Use the retained signed APK and matching firmware/configuration for rollback. The manual updater excludes prereleases; install candidates explicitly from GitHub Releases.

## Released control checks retained

ArduRover Wi-Fi requires a valid physical CH7 permission reading above 1800 µs and younger than 750 ms; stale/missing data fails closed. The app requests RC_CHANNELS from the FC and still requires neutral controls, explicit Enable and ARM. Restoring permission never re-enables driving. Use the matching FC/receiver configuration and bridge, and verify transmitter takeover on restrained wheels. Standalone Mini rover Wi-Fi uses its own sent-neutral dwell before ARM; it does not require FC radio permission. USB LoRa retains its dedicated motor/selector gates. Native ELRS Backpack, standalone ELRS handshakes and balancing profiles are unavailable in this compact build.

The default branch was older than the published release. The final build carries forward the relevant v1.1.0rc1 control, parser, updater and response checks rather than replacing them with the old source baseline. The old 0.8.54 attempt was not published because its historical APK download references were unavailable.

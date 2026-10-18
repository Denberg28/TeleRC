# TeleRC 0.8.79 / code97 — Waypoint tab test candidate

## Scope and baseline

Baseline: 0.8.78 signed release, with development documentation at `29ee8d5cd85746d8f1816dcd72a2a70b9ea5c2b2`. Only the new Waypoint page is designed here. Setup, Controls and Test rendering/behavior, manual MAVLink codec, measured/estimated route session, FPV, wallet and all firmware are preserved. CI compares their method bodies and source against that baseline. The shared page dropdown gains Waypoint and the Activity forwards its new component's telemetry/lifecycle. App ID, release signer and permissions stay the same; version increases to code97.

This is preparatory software for an HGLRC M100 GPS + compass installation. No phone, GPS, compass, flight controller, receiver or powered rover is attached to this development environment. Live navigation is experimental and is not hardware/field qualified. No stable promotion.

## Compatibility — read before installation

- Route planning, coordinates, single/multiple mode, numbered map points and saved drafts work without rover hardware.
- Live navigation requires an **ArduPilot ground rover** (MAV_TYPE_GROUND_ROVER=10, MAV_AUTOPILOT_ARDUPILOTMEGA=3), a correctly calibrated flight-controller GPS/compass/EKF and a compatible transparent MAVLink Wi-Fi connection. ArduRover 4.6.3 is the reviewed protocol reference. Boats and aircraft are rejected.
- **The currently supplied TeleRC Wi-Fi/LoRa gateways filter navigation/parameter packets; they are planning-only for this tab.** Their sketches are deliberately unchanged. Check link must actually read settings and confirm an FC HOLD round trip. Missing replies lock motion rather than guessing compatibility. LoRa and Mini rover presets are blocked by the adapter.
- An M100 connected to the existing standalone mini rover ESP32-S3 does not add an autonomous controller to its current sketch. For the reviewed execution path the GPS UART and compass I2C feed the flight controller; board pin/power/protocol configuration must be checked against the exact M100 and FC models. No new wiring or firmware is silently assumed.
- A separate, explicitly scoped gateway/firmware update and restrained-wheel end-to-end qualification are needed for the current TeleRC hardware chain. This app does not authorize or install them.

## Operator flow

1. Dropdown → Waypoint. The page uses the Controls three-column proportions, compact navigation/buttons, shared palette/wallpaper and expanded center, with a dedicated map and planning/safety panels instead of motor joysticks. Existing tabs do not acquire navigation widgets.
2. Choose Go to / single or Mission / multiple. Tap the map or enter decimal `latitude, longitude`. Single selection opens the execution check/confirmation; multiple taps append up to 20 numbered waypoints. Tap a route row to remove it, Undo to remove the last, or hold Undo / clear to confirm clearing all. Switching to single asks before keeping only the last point. Drafts save locally; authority never saves.
3. Street tiles use the existing OpenFreeMap service and need internet/cache; tile failure shows a plain planning surface and coordinates remain usable. The default Naga reference is explicitly planning-only and never becomes rover GPS. Locate centers the last measured rover or first planned point. A line is a planned straight segment, not a traversability or obstacle assessment.
4. Connect the compatible vehicle in Setup. **Check link requests HOLD**, reads FC parameters, requests navigation telemetry and waits for a checksum-valid acknowledgement plus a later HOLD heartbeat. It never arms, calibrates, writes parameters, uploads an AUTO mission or bypasses pre-arm checks. Arm separately with Controls or the transmitter, then check again as needed. STOP/HOLD leaves the rover armed.
5. Go / Start mission displays the immutable ordered route, speed, operating limit and safety behavior. Proceed rechecks the route and current readiness. Cancel sends no destination. Navigation stays locked until all checks below pass.
6. Navigation releases prior RC override while FC HOLD has been confirmed, then sends atomic `COMMAND_INT / MAV_CMD_DO_REPOSITION` with change-mode flag and explicit 0.5 m/s. Each destination needs an accepted ACK, Guided heartbeat and matching POSITION_TARGET_GLOBAL_INT echo. No prior Guided target is resumed and no destination is retried automatically.
7. The mission is a **phone-supervised Guided route**, not an uploaded FC AUTO mission. Three distinct fresh position samples within 3 m, with at least 2 seconds since dispatch, advance to the next point. Every next destination is independently acknowledged. Completion requests HOLD and is only reported confirmed after accepted HOLD plus rover mode confirmation. The FC's stored AUTO mission remains untouched.
8. Keep TeleRC foreground and connected. STOP, leaving the page, backgrounding, timeout or lost readiness cancels queued motion, withdraws navigation GCS heartbeats and requests HOLD (normal foreground GCS liveness may resume only after HOLD is confirmed). Reconnecting/restoring/reopening never resumes or arms. A new Check and route confirmation are required. A failed HOLD remains **unconfirmed**, with an independent transmitter/emergency stop instruction.

## Readiness and bounds

- Fresh (at most 2 seconds) autopilot heartbeat, measured GLOBAL_POSITION_INT including heading, GPS_RAW_INT, EKF_STATUS_REPORT and SYS_STATUS. The UI rejects delayed queued telemetry (>500 ms) and checks prior freshness before a recovery sample refreshes it.
- GPS 3D/DGPS/RTK fix (types3–6), at least 6 satellites, HDOP 0.01–2.5; healthy/enabled/present GPS, compass and AHRS; EKF attitude/horizontal velocity/absolute position and acceptable finite variances, no constant-position/uninitialized flags; battery at least20%. Unknown battery is blocked. Current FC Home required. Repeated/reordered FC position clocks and repeated GPS measurement timestamps cannot refresh freshness or count as arrival samples. A moving FC clock reset cancels navigation. Phone location and dead reckoning are never navigation inputs.
- Fresh parameter readback (10 seconds): `SYSID_MYGCS=255`, `FS_GCS_ENABLE=1`, `FS_ACTION=2` (Hold), `FS_EKF_ACTION=1`, `FS_GCS_TIMEOUT=2..3`, `FS_TIMEOUT=1..3`, `WP_SPEED=0.1..1` m/s. Set/test these deliberately in Mission Planner. TeleRC never writes them. Different firmware names/semantics remain blocked.
- Max20 points, each within100 m of measured start; max500 m total route,120 seconds per destination and10 minutes total. At0.5 m/s a distant point or long route can hit these conservative time limits before arrival. A current position beyond100 m, large GPS jump, low battery, pilot mode change, disarm, unhealthy/stale navigation, denied destination or missing target echo requests HOLD.
- All requests/dispatches are bounded. Motion packets expire after500 ms and are checked against the current navigation generation/session/page/foreground/manual authority immediately before send. Stop/disconnect invalidates old generations. HOLD retries at most3 times. Navigation GCS heartbeats run during foreground supervision/checking. Cancellation/failure withdraws them until HOLD is confirmed. After a confirmed HOLD round trip, ordinary GCS liveness continues while foreground, connected, heartbeat-fresh and in manual/HOLD modes so returning to unchanged Controls does not produce a false FC GCS failsafe. It carries no navigation/RC/arm authority; background, telemetry loss, unconfirmed HOLD or an unexpected autonomous mode withdraws it.
- The100 m bound is a software operating limit, **not an FC geofence** or a guaranteed stopping boundary. GPS can be inaccurate; boundaries must allow braking distance. Configure an onboard fence if needed. There is no obstacle avoidance, automatic RTL, remote force-arm or physical motor-off guarantee.
- GCS failsafe timing includes FC implementation/scheduling and FS_TIMEOUT; it is not a measured end-to-end stopping time. HOLD may coast/brake according to the FC/driver configuration. App freezes/crashes/power loss must be covered by the independent FC GCS/EKF failsafes. Do not rely on a final wireless STOP packet.

## Evidence gates

| Gate | Status before final CI | Required evidence |
| --- | --- | --- |
| A source/build | Reviewed; local SDK/Gradle BLOCKED | Baseline scope guard, JVM, debug/release lint/build and native APK compilation |
| B behavior | Reviewed; execution pending | Bounded draft, protocol fields/CRC/source checks, readiness/confirmation, ACK/mode/target sequencing, unique arrival samples, queue cancellation, GPS/battery/mode/stale/recovery/timeout/stop regressions |
| C regression | Pending final CI | Bridge sync, host/default ASan/UBSan and existing JVM/native suites, unchanged tab bodies and firmware |
| D integration | App/state-machine simulated tests only; full ArduRover SITL and compatible-link integration NOT TESTED | Actual frame exchange and measured FC stop/failsafe behavior; current shipped gateways intentionally blocked |
| E phone/HIL | BLOCKED | No attached equipment; run the executable sequence below |
| F package | Pending | Retained .78 signer, app ID/code97, permissions/USB/alignment/CRC, APK/source/evidence/checksums; prerelease only |

Successful required CI steps and BUILD_INFO supersede pending software statuses only. A successful software package does not qualify navigation on a rover.

## Executable acceptance after hardware/link preparation

1. Preserve a full FC parameter dump, saved AUTO mission and existing app data. Raise and restrain all wheels, keep motor power separately switchable, and establish transmitter emergency stop/manual takeover. Verify physical source selects FC PWM, not DIRECT, and FC motor mapping/direction/neutral/brake behavior. Stop here if independent authority is uncertain.
2. Install over0.8.78 without uninstalling. Verify Setup/Controls/Test and FPV/wallet unchanged, Waypoint drafts persist after restart, map taps/coordinates/cancel/remove/clear work, and large fonts/all three backgrounds fit. Do not confuse reference map or phone/estimated positions with rover GPS.
3. With existing mini/filtering gateway, press Check: no destination can dispatch; after timeout the UI reports no HOLD reply/planning only. Verify packet logs. GPS on this firmware must not unlock it.
4. With the separately validated compatible FC link, configure/read back the stated failsafe parameters; confirm actual values in Mission Planner independently. Test wrong/disabled/unknown/stale settings, invalid GPS/compass/EKF, missing battery, disarmed state, unsupported vehicle type, stale home and out-of-bounds route all prevent Proceed.
5. In restrained-wheel conditions, select a close single destination, inspect/Cancel (no destination), then Proceed. Log source/target IDs, CRCs, RC release after confirmed HOLD, command192 speed0.5/flag1/coordinates, accepted ACK, Guided heartbeat and matching target. Confirm real orientation/motor direction. Never test with people/traffic in the path.
6. Test multiple points, no premature/duplicate advance, complete→HOLD confirmation, preserved onboard AUTO mission, STOP and no auto resume. Force denied/lost ACK or target mismatch, position jump, stale GPS/heading/EKF, battery19%, pilot mode change, disarm, waypoint/route timeout and100 m limit; each must stop issuing destinations and request HOLD. Restore telemetry and verify no resume.
7. Kill/pause the app, switch tabs, block either telemetry direction, power off the phone/gateway and reconnect. Measure actual FC/driver stopping time/distance, verify GCS and EKF failsafes without the phone, check there is no other GCS sending the same heartbeat identity, and confirm an old queued destination never executes after STOP or a new session.
8. Only after those pass perform a close-range clear-ground walking-speed test with a spotter and independent stop. Record logs, firmware/board/parameter hashes, GPS/compass calibration, supply voltage, stop/current/braking measurements and operator outcome. Hardware/field statuses remain BLOCKED until measured evidence exists.

## Installation and rollback

Update in place using the same signer. No firmware is included/modified and no reflash is required to use planning; existing gateways cannot execute the new commands. Rollback must be a forward-version build preserving the0.8.78 v2 wallet and settings; an Android downgrade is not an in-place path. The new `waypoint_route` preference contains only a bounded draft/mode and may be ignored by rollback; no movement resumes from it. Do not clear app data/uninstall just to remove this tab.

## Protocol references

- [ArduRover Guided commands](https://ardupilot.org/dev/docs/mavlink-rover-commands.html)
- [ArduRover4.6.3 command192 atomic reposition implementation](https://github.com/ArduPilot/ardupilot/blob/Rover-4.6.3/Rover/GCS_Mavlink.cpp)
- [ArduRover4.6.3 parameters](https://github.com/ArduPilot/ardupilot/blob/Rover-4.6.3/Rover/Parameters.cpp) and [failsafe actions](https://github.com/ArduPilot/ardupilot/blob/Rover-4.6.3/Rover/failsafe.cpp)
- [MAVLink common messages](https://mavlink.io/en/messages/common.html), [ArduPilot EKF_STATUS_REPORT](https://mavlink.io/en/messages/ardupilotmega.html#EKF_STATUS_REPORT)

Next check: run final cloud software qualification, review native Waypoint captures, then prepare a separately scoped compatible gateway/FC integration and perform the restrained-wheel sequence. Do not treat hardware installation alone as navigation readiness.

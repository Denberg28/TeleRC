# TeleRC v0.8.86 / code104 — map mission functions

Software-qualified test candidate only after the required release workflow succeeds. Existing stable remains v0.8.84. App-only update; no firmware or wiring changes and no reflash.

## Plan and start

1. In Waypoint, normal map taps add numbered points in Multiple points mode (the new planner default). Single point still replaces the selected destination.
2. Tap F1, F2 or F3 to select function placement. Tap a map location, choose ON or OFF, and the event is inserted at that numbered point. Normal waypoint placement resumes automatically. Repeat in any order. With a function selected, tapping an existing marker inserts a new event at that location. Tap a route row to edit its actions or remove it; Undo/Clear remain available.
3. Long-press F1/F2/F3 for output assignment and ON/OFF PWM settings. Defaults are OFF1100/ON1900 microseconds; verify the actual accessory's safe endpoints first. Each used function needs a unique dedicated output with SERVOx_FUNCTION=0 and fresh MIN/MAX readback containing both endpoints. Outputs M5–M8 remain reserved for motors. Every function's final planned event must be OFF; add a later OFF point after ON.
4. Mission start always performs fresh telemetry, navigation, failsafe parameter and HOLD checks. It then asks you to confirm the exact ordered coordinates, actions, output mappings and PWM values. Checks do not actuate functions. The rover must already be explicitly armed; the app never arms for a mission.
5. After confirmation, the rover must report stationary HOLD before all used functions are initialized OFF. Each leg then uses the existing Guided destination command. On reaching an action point, it confirms stationary HOLD again, executes that point's actions in listed order, verifies accepted ACK plus fresh reported output PWM, then proceeds to the next point.
6. Completion, STOP, leaving Waypoint, backgrounding, stale telemetry and detected failures cancel pending navigation/ON writes and request HOLD plus all used functions OFF. OFF attempts continue for other functions if one fails. No automatic resume occurs. If OFF is unconfirmed, restore the compatible link and press STOP explicitly to retry; phone-enable and new arming are blocked until recovery. Disarming remains available.

Single point visits one selected destination. Multiple points visits every listed coordinate, including function-event points, in order. This is phone-supervised Guided sequencing; it does not upload or run an FC AUTO mission. Keep TeleRC foreground and connected. Mini Wi-Fi, LoRa and native handset-only ELRS Backpack profiles retain planning-only locks; live execution requires a separately validated transparent ArduRover MAVLink link.

## Review and safety boundaries

- Exact plan/settings/checked-session snapshots guard confirmation. Draft persistence restores planning only, never authority or execution. Actions include the coordinate fingerprint; malformed/mismatched actions lock execution until Clear/rebuild.
- Existing limits remain: 20 total numbered points, 100 m operating radius, 500 m route length, 0.5 m/s requested speed, 10 min route and 2 min leg limits. Arrival needs three fresh positions within approximately 3 m and at least 2 seconds on the leg; it is not a precise action trigger. Stationary HOLD needs two distinct position samples spanning at least250 ms with reported speed at most0.1 m/s.
- Absolute DO_SET_SERVO183 writes are serialized, generation-bound and expire from the send queue after500 ms. Each action has at most three transmissions (confirmation0/1/2), a4 second deadline, and requires accepted ACK plus matching post-send SERVO_OUTPUT_RAW. Pre-send replies cannot finish an action. STOP invalidates unsent ON and destination dispatches under the same lock used by final transmission.
- Port0 SERVO_OUTPUT_RAW with increasing FC timestamp is required. Outputs9–16 require present MAVLink2 extension bytes. Unsupported/absent output reporting fails closed. Reported PWM is FC telemetry, not measured physical actuator position or a proof that a load switched OFF.
- All used outputs are requested OFF before motion, at completion and on STOP/failure. Lost link, killed app, FC reset or power loss cannot guarantee delivery or physical OFF. Provide an independent actuator watchdog and cutoff, in addition to motor failsafes. HOLD leaves the rover armed. Use a safe accessory with no payload/hazard during acceptance.
- Existing GPS/compass/EKF/health/battery/arm/parameter and target-echo gates remain. No PARAM_SET, pre-arm bypass, AUTO mission upload, auto-arming or recovered-link control activation is introduced. Reconnect clears telemetry, dispatches and readiness; unresolved OFF prevents starting another sequence.
- Source protection checks retain manual codec, transport/watchdogs, source selector, FPV, popup engine, offline game/wallet and all firmware. MainActivity changes are limited to explicit pending-HOLD/OFF handover guards. Queues/retries/persistence are bounded. No secrets are added. Wi-Fi/MAVLink is still unsigned and unauthenticated: use an isolated trusted control network; this release does not provide adversarial replay protection.

Protocol references: [ArduPilot servo command](https://ardupilot.org/dev/docs/mavlink-move-servo.html), [MAVLink command ACK protocol](https://mavlink.io/en/services/command.html), [SERVO_OUTPUT_RAW](https://mavlink.io/en/messages/common.html#SERVO_OUTPUT_RAW), [Rover4.6.3 parameters](https://ardupilot.org/rover/docs/parameters-Rover-stable-V4.6.3.html). The dedicated disabled-function policy is deliberately narrower than all firmware-supported servo modes.

## Qualification A–F

| Gate | Evidence / result |
|---|---|
| A — scope/security/safety review | Reviewed: baseline v0.8.85/code103, commit82bfbfce36d144dd3d16134b44f175b00665a1ba; app/navigation only. Protected sources/firmware checked by required scope script. Physical guarantees explicitly limited. |
| B — automated behavior | Local host regression and three packaging tests PASS; local ASan/UBSan PASS with detect_leaks=0 due container ptrace restriction. Required cloud default sanitizers, complete JVM planner/protocol/auxiliary/navigation/cancellation tests and existing regressions must PASS. |
| C — build/package | Required debug/release lint/build, native harness compile, APK identity104/0.8.86, same verified signer as v0.8.85, permissions, ZIP CRC/alignment and source/evidence/checksum bundle must PASS. Local Android build BLOCKED by toolchain/network; use final workflow/BUILD_INFO for actual result. No affected ESP32 targets. |
| D — simulator/UI | Required API35 native actual map taps, mixed sequence, editing, assignment/PWM settings, saved reload, help/layout and cancelled-start checks plus complete existing theme/large-text/popups/maze/replay/FPV/ELRS/Waypoint regressions must PASS. Full ArduRover SITL integration NOT TESTED. |
| E — hardware/field | BLOCKED: no physical phone, FC, output load, RF/link-loss or restrained-wheel acceptance. No hardware/field reliability or stable readiness claim. |
| F — delivery/compatibility | Required signer/version/source/checksum/release-asset verification must PASS; final successful workflow and BUILD_INFO record results. Physical upgrade/installation/compatibility NOT TESTED. |

Publication is gated on software checks; this document intentionally does not predeclare CI outcomes. Published BUILD_INFO plus the successful tagged-source workflow supply those outcomes. Evidence includes mission-functions-plan/help screenshots, all native results, JVM XML and lint reports.

## Restrained-wheel and phone acceptance (next required check)

Disconnect loads and raise/restrain wheels; keep transmitter/emergency stop and independent accessory cutoff in reach. Use the pinned supported ArduRover setup and a validated transparent link. Do not perform first testing with a moving rover or hazardous mechanism.

1. Install over the existing app. Verify wallet/progress, replay coins, appearance, saved endpoints and normal control release/neutral behavior. Verify normal/large-text Waypoint pane and help with keyboard/system insets.
2. Assign one safe test output outside M5–M8, SERVOx_FUNCTION=0 and verified limits. With an oscilloscope or independent PWM reader, verify OFF/ON polarity/endpoints without a load. Try duplicate/motor output, invalid limits, missing output telemetry, stale GPS and disarmed state: start must remain locked.
3. Plan normal point → F1 ON → normal point → F2 ON → F1 OFF → F2 OFF. Restart the app and verify exact rows/order/actions. Mission start must recheck, show the correct sequence/mapping, and send no servo/destination before explicit confirmation. Cancel must not start.
4. Confirm with safe wheels restrained. Observe initial HOLD/stationarity and all-OFF before the first destination. Feed measured positions or controlled SITL/bench telemetry to each target; verify HOLD, exact one-output action, ACK+PWM gating and next-leg order. Test missing/rejected ACK, missing/wrong PWM, absent port/extension output and moving HOLD: no next leg, bounded retries, HOLD/OFF recovery.
5. During ON and during a delayed queued command, press STOP, change tabs, background, disconnect Wi-Fi, reset FC and interrupt telemetry separately. Verify no late ON/next-leg write or automatic restart, all reachable functions get OFF attempts, unconfirmed stop is clearly shown and independent cutoffs stop the actual loads. Restore link: explicit STOP recovery and fresh checks are required. Verify transmitter takeover/disarm independently.
6. Complete a balanced route and verify HOLD plus every function OFF. Repeat with two/three functions, same-location events and outputs9–16 (only if real MAVLink2 output telemetry is available). Record actual times, physical endpoints, stop distances, hardware/firmware versions and failures before considering field validation or stable promotion.

## Install and rollback

Download TeleRC-v0.8.86.apk from this release, verify SHA256SUMS.txt and install over the app without uninstalling/clearing data. Same application identity/signing key; versionCode104. No reflash or paired firmware upgrade. Source, BUILD_INFO, checksums and test evidence are attached to the exact product commit.

v0.8.85 introduced wallet v3; older unmodified parsers cannot read it. Function action drafts are new and older apps ignore them. Rollback therefore requires a higher-code forward rebuild retaining the v3 wallet parser and either retaining these action semantics or explicitly clearing/rebuilding action plans with the operator; never silently execute an old coordinate-only draft. Never clear app data to downgrade. Independent cutoff first if any output is unconfirmed; an app rollback cannot guarantee physical OFF. Existing v0.8.84 remains the stable channel, not a wallet-compatible downgrade path.

## 0.8.36
- Keep ARM/DISARM on the existing MAVLink link instead of performing receiver handover first.
- Neutralize TeleRC joystick output before ARM/DISARM while preserving the active transport session.
- Add transport-drop diagnostics for discovery send, UDP receive, and control send failures.
- Includes the v0.8.35 background command-transmission fix.

## 0.8.35
- Android networking reliability update for command transmission.
- Includes the validated post-0.8.34 sender-thread fix.
- Compatible with the current ESP32-S3 bridge sketch.

## 0.8.34
- Fix MAVLink ARM/DISARM flow: arm/disarm now neutralizes and releases RC override first, then sends one standard MAV_CMD_COMPONENT_ARM_DISARM COMMAND_LONG with confirmation=0.
- Parse MAVLink COMMAND_ACK for command 400 and show ACCEPTED / TEMPORARILY REJECTED / DENIED / UNSUPPORTED / FAILED in TeleRC instead of silently waiting for heartbeat state.
- Set ARMING_OPTIONS=0 in the checked-in Rover profile so pre-arm messages are no longer suppressed. Normal ArduRover arming checks remain enabled; no force-arm bypass is used.
- Final Android checks pass with the corrected arming flow; ESP32 bridge compilation remains green from the matching handover/safety bridge state.

- Make deliberate STOP CONTROL and explicit Disconnect hand control back to the physical RC receiver: TeleRC sends neutral first, then MAVLink RC override release. Tab changes, app backgrounding, heartbeat loss and unexpected link loss still use neutral-hold.
- Align TeleRC joystick output with the calibrated RC1/RC2 range (1100-1900 us instead of 1000-2000 us) to avoid early saturation and abrupt steering/throttle response.
- Soften the checked-in Rover profile for bench driving with MOT_SLEWRATE=40 and MANUAL_STR_EXPO=0.3. RC_OVERRIDE_TIME remains 3 s as a fallback if MAVLink overrides disappear without an explicit release.

## 0.8.33
- Stop the LINK ACTIVE / HEARTBEAT DELAYED visual oscillation by removing the intermediate delayed banner; LINK ACTIVE remains until the verified heartbeat is genuinely lost.
- Increase verified-heartbeat control freshness tolerance from 2.5 s to 4.0 s and lost-heartbeat UI grace from 6 s to 8 s, while retaining the independent 500 ms ESP32 control-packet watchdog and 10 Hz neutral-hold.
- Measure FC-side autopilot HEARTBEAT cadence in the ESP32 bridge and expose heartbeat count, latest gap and maximum gap through Diagnose Link and USB diagnostics.
- Signed release workflow re-runs Android verification before publishing.

## 0.8.32
- Simplify the ESP32-S3 bridge transport to the early stable model: start SoftAP and UDP once and do not restart/rebind them for normal phone association events.
- Keep control safety independent from transport: tab changes and app backgrounding send neutral, and the bridge keeps the existing 500 ms watchdog plus 10 Hz neutral-hold.
- Add an explicit TELERC_DISCONNECT_V1 routing message. Pressing Disconnect sends neutral, clears the bridge phone pairing immediately, closes the Android UDP socket, and disables auto-reconnect.
- Keep passive telemetry recovery: if the paired phone is absent for five seconds, FC MAVLink returns to broadcast without restarting Wi-Fi.
- Preserve CH1 steering, CH2 drive, ARM/DISARM, external servo assignments, MAVLink validation, and bridge diagnostics.
- Android checks and ESP32-S3 bridge compilation pass for this simplified transport release.

## 0.8.31
- Keep the TeleRC UDP session persistent when Android backgrounds or temporarily suspends the Activity while another app is in use.
- Remove the 8-second packet-silence transport teardown; heartbeat loss still disables rover control, but no longer resets the socket or target system by elapsed time alone.
- Reconnect remains reserved for actual UDP/socket failure or an explicit operator Disconnect.
- Correct link diagnostics to reference CH1 steering and CH2 drive/throttle.
- The signed release workflow re-runs Android tests/build verification before publishing.

## 0.8.30
- Change the TeleRC rover drive/throttle command from RC CH3 to RC CH2 end-to-end.
- Update live-control UI labels, sent-command logging, dead-reckoning input, ESP32 bridge diagnostics, diagnostic sketch, and the checked-in ArduRover profile to use CH2.
- Steering remains on CH1; CH3 and CH4 are neutral in TeleRC live control.
- Android checks and the matching ESP32-S3 bridge compile pass for the final CH2 mapping.

## 0.8.29
- Stabilize the Android rover link by no longer tearing down UDP when Android temporarily changes the captured Wi-Fi Network capability state.
- Separate transport health from MAVLink heartbeat health: heartbeat loss still disables live control, but does not by itself churn the UDP connection.
- Treat sustained total bridge silence as the transport failure condition; reconnect only after roughly 8 seconds without any ESP32 packet or on a real socket failure.
- Keep automatic reconnect at 2 seconds after a genuine transport failure, while preserving the faster 500 ms ESP32 control watchdog and 2.5-second heartbeat control cutoff.
- Extend Diagnose Link with ESP32 access-point restart count and phone station-disconnect event count so AP resets, phone Wi-Fi drops, and FC/UART heartbeat loss can be distinguished.
- Retain v0.8.28 dedicated ARM / DISARM, F1/F2/F3 external-servo assignments, and neutral-hold failsafe behavior.
- Android checks and the matching ESP32-S3 bridge compile pass for this release.

## 0.8.28
- Make the large former Servo button a dedicated rover ARM / DISARM control on Controls and Test Drive; its label follows the real armed state reported by MAVLink HEARTBEAT.
- Send normal MAV_CMD_COMPONENT_ARM_DISARM commands through a narrowly whitelisted ESP32-S3 bridge path; ArduRover arming checks remain active and no force-arm bypass is used.
- Keep F1, F2 and F3 exclusively for external servo output assignments; automatically clear the temporary ARM/DISARM assignment if it was previously stored on an F-button.
- Harden Stop, tab changes, app pause, Disconnect and heartbeat loss: TeleRC sends repeated neutral commands and the bridge holds 1500 neutral at 10 Hz instead of releasing RC override back to a possibly non-neutral receiver.
- Keep the bridge-side 500 ms control watchdog and Wi-Fi station/AP-loss failsafes; recovery restores telemetry only and never resumes joystick authority automatically.
- Include passing Android unit/build checks and matching ESP32-S3 bridge compilation for this release.

## 0.8.27
- Rebuild the latest TeleRC code after the v0.8.26 landscape spacing and footer-overlap fixes.
- Retain the layered Wi-Fi/control fail-safe introduced in v0.8.25.
- No control mapping or protocol behavior changes from v0.8.26.

## 0.8.26
- Fix Test Drive footer overlap where CSV could intrude into the centered DISCONNECTED/link-status area.
- Use equal left/right footer zones with a fixed centered link status on Controls and Test Drive.
- Tighten button dimensions and horizontal spacing for Game/SIM/GPS/Reset/CSV/F1/F2/F3 while preserving touch targets.
- Normalize landscape shell, panel, top-control, center-pane and footer spacing for a cleaner balanced layout.

## 0.8.25
- Harden the ESP32-S3 bridge fail-safe: Wi-Fi station disconnect and AP-stop events now neutralize steering/throttle and release RC override immediately.
- Keep the existing 500 ms control-packet watchdog as a second bridge-side stop path for silent UDP/link stalls.
- Record automatic fail-safe reason/count in ESP32 USB diagnostics and count the neutral/release bytes sent to the flight controller.
- Keep TeleRC control non-latching: reconnection restores telemetry only; the operator must explicitly enable control again.

## 0.8.24
- Make Reset restart the offline Test simulator, alongside the Game return and servo assignment updates in 0.8.23.

## 0.8.23
- Replace the hidden Game/Test switch with an explicit Test / Game return button.
- Center text messages across the application.
- Add persistent Servo/F1/F2/F3 output assignments, prevent duplicates and reserve rover motor outputs M5–M8.
- Keep assignment separate from operation: current bridge only supports RC overrides.

## 0.8.22
- Add saved joystick sensitivity from 25 to 100 percent; retain neutral release and assigned axes.
- Compact Setup with saved accent choices and a fixed non-scrolling layout.
- Reacquire phone GPS after Test Reset, including stationary fixes.
- Retain requested GPS recentering until the map style is ready; reject stale and invalid fixes.

## 0.8.21
- Add a saved light/dark theme switch in Setup and blue application accents.
- Make Setup landscape with dropdown navigation and a compact two-column layout.
- Separate navigation and Servo buttons from joystick cards with a clear gap on Controls and Test drive.
- Apply theme colors to cards, inputs, overlays, joysticks and system bars.

## 0.8.20
- Extend the center pane to the top on Controls and Test drive, beside compact navigation and Servo buttons.
- Move link status to the lower center and remove the bottom explanatory text.
- Use matching narrow joystick panels and F1/F2/F3 placeholders on both screens.
- Keep navigation in the dropdown only; remove Show tabs.

## 0.8.19
- Add compact navigation above steering with expandable and collapsible tabs.
- Add a Servo placeholder above Drive and F1/F2/F3 placeholders at bottom right.
- Expand the center map and move Enable/Stop into the bottom toolbar.
- New auxiliary buttons display not-configured feedback and send no commands.

## 0.8.18
- Use compact equal-width steering and drive panels around a larger center map pane.
- Move Enable/Stop above the map and reduce Controls card padding and headings.
- UI layout update only; rover commands and bridge firmware are unchanged.

## 0.8.17
- Accept the bridge's valid zero-valued RC release report after Stop, preserving CH3 history in Link diagnostics. Display released channels as neutral 1500.
- Report neutral CH1/CH3 from the bridge after a release while retaining the non-neutral drive range.

## 0.8.16
- Retain the lowest and highest non-neutral CH3 values received by the ESP32 and count changed drive commands. Diagnose link now shows this history after leaving Controls, when the stop command has reset the live CH3 value to 1500.
- Keep compatibility with earlier bridge diagnostic formats. Motor response still requires bench verification.

## 0.8.15
- Report ESP32 accepted/rejected control frames, UART transmit bytes, last CH1/CH3 values, and heartbeat age in Link diagnostics.
- Refresh bridge diagnostics once per second. This release adds observability; motor motion and intermittent heartbeat loss still require a bench diagnosis.

## 0.8.14
- Test and Live crosshairs request a current precise phone GPS fix and center the map once a valid fix arrives.
- Let the crosshair retry location permission and open device location settings when services are off, without interrupting active rover control.

## 0.8.13
- Distinguish a delayed heartbeat from a lost link in Setup while preserving the 2.5-second control cutoff.
- Limit queued link status UI refreshes to four per second, avoiding redundant main-thread work during telemetry bursts.
- Add a test proving display grace never extends control authority.

## 0.8.12
- Keep telemetry connected when switching pages or briefly leaving the app; stop and release joystick control immediately when the app loses focus.
- Remember a requested connection and retry every two seconds on return or after Wi-Fi socket failure. Never resume control automatically.

## 0.8.11
- Allow 2.5 seconds between verified rover heartbeats before marking the link stale, accommodating a missed 1 Hz packet while retaining neutral and release on loss.
- Recheck heartbeat freshness when a queued UI update runs, avoiding a false control stop after a newer heartbeat arrives.

## 0.8.10
- Correct RC_CHANNELS_OVERRIDE CRC extra to 124; prior Android and ESP32 bridge used heartbeat CRC 50, so the FC discarded joystick frames.
- The updated bridge accepts legacy app packets and rewrites their checksum before UART. Flash it before installing the updated APK.

## 0.8.9
- Bind the rover UDP socket explicitly to Wi-Fi so a phone using mobile data alongside an offline ESP32 access point cannot route TeleRC discovery or control traffic over cellular.
- Explain that a missing bridge diagnostic reply may mean an older ESP32 sketch is installed; keep live control blocked without a valid heartbeat.

## 0.8.8
- Add a Diagnose link action in Setup that explains whether the ESP32 bridge is reachable, whether it sees FC UART bytes and complete frames, and whether TeleRC receives a valid rover heartbeat.
- Send receive-only bridge diagnostics to the paired phone every three seconds; diagnostics cannot enable control or issue rover commands.
- Continue to block control until a valid autopilot heartbeat is received.

## 0.8.7
- Send a receive-only discovery packet so the ESP bridge can route MAVLink telemetry directly to the phone before control is enabled.
- Explain missing heartbeat when Enable Control is tapped; continue blocking all motor commands until a valid heartbeat arrives.
- Print ESP UART frame and Wi-Fi client counters to diagnose wiring, serial settings, and phone connectivity.

## 0.8.6
- Keep the Controls recovery map visible after Stop, link loss, and app pause while command output remains disabled.
- Show the age of the last rover GPS fix; recenter on that fix, falling back to the last RC estimate and then Home.
- Freeze dead reckoning on link loss and resume only after new transmitted frames, without inventing movement during an outage.
- Save the estimated path separately from rover GPS so the recovery view survives screen rebuilds.

## 0.8.5
- Verify direct update APK signing certificate against installed TeleRC and require the exact release APK filename.
- Ignore unverified signed MAVLink 2 heartbeats and heartbeats from non-autopilot components.
- Serialize RC sends with Stop so a queued override cannot follow neutral and release.
- Bound route restoration and live route history to protect storage and memory.

## 0.8.4
- Transform the Controls center card into a live dead reckoning map while control is enabled; Stop, link loss, and leaving Controls return the card.
- Integrate only transmitted RC frames with the configured offline speed and pivot settings, label the cyan estimate, and display real rover GPS separately in purple.
- Re-anchor the estimate once on the first valid GPS fix and keep accumulated drift visible afterward. Reset the estimate on each enable.
- Display estimated displacement from the anchor, heading, and speed in the live map without affecting RC output.

## 0.8.3
- Replace MapLibre demo tiles with the OpenFreeMap Liberty street style used in HARU. Show a clearly labeled sample map position until precise phone GPS sets fixed Home.
- Keep the cyan rover visible at Home, draw an estimated joystick route, and configure offline vehicle name, maximum speed, and pivot turn rate by tapping the map badge.
- Add a Game motor loop while driving or steering and a collision sound when hitting a gate.

## 0.8.2
- Show a labeled cyan heading-aware offline rover preview on the Test map after Home GPS is fixed; keep simulation separate from measured telemetry.
- Replace the quiet frame-driven Game beeps with a looping melody that plays while the music button is on, including while stationary; use media volume.

## 0.8.1
- Center Enable/Stop between equal-width Steer and Drive panels.
- Keep Game road stripes continuous across course resets.
- Draw a heading-aware rover icon from MAVLink telemetry on the Test map, including rotation in place; persist bearing in CSV.

## 0.8.0
- Record transmitted live rover controls and validated global position telemetry in the Test route session.
- Set a fixed Home from the first precise phone GPS fix and draw later phone movement as a separate route.
- Add MapLibre Test map with pinch, compass, route reset, CSV export, and access to the offline simulator.

## 0.6.2
- Reject truncated MAVLink 2 heartbeats without disrupting the connection loop.
- Use monotonic time for heartbeat age and refresh the debug artifact name.

## 0.6.1
- Removed the Test Drive page heading so the course fills more of the display.

## 0.6.0
- Endless offline obstacle gates with pass counter and collision reset.
- Direct test drive steering and throttle response, instant joystick centering, centered control labels.

# Changelog

## 0.5.0 — offline test drive
- Add a third landscape tab with a retro top-down rover course, moving vehicle, speed and distance readouts, and reset.
- Use independent steering and drive joysticks for offline practice without sending MAVLink.
- Keep live control disabled when switching tabs or backgrounding the app.

## 0.4.1 — manual direct updates
- Add a Setup & Config update button for public GitHub release APKs.
- Verify SHA-256 digest, package identity and version before Android installer handoff.
- Prepare a manual signed release workflow using repository Actions signing secrets.

## 0.4.0 — joystick controller
- Replace rover sliders with visibly moving spring-return steering and drive joysticks.
- Put Enable/Stop in a dedicated card to the right of Drive to avoid overlap.
- Disable and neutralize controls on orientation changes.

## 0.3.1 — layout refinement
- Move tab buttons beneath the header on both pages.
- Respect status, navigation and display-cutout insets; tighten spacing.

## 0.3.0 — two-tab interface
- Portrait Setup & Config with craft, bridge settings and link status cards.
- Landscape Controls with separate steering and drive panels; page changes disable control.

## 0.2.0 — rover private test source
- Rover steering CH1 and bidirectional throttle CH3; spring return to 1500 µs neutral.
- Explicit control enable after checksum-valid heartbeat; control disables on link loss, pause, or disconnect.
- Best-effort neutral and RC override release on disable; profile model reserves other craft without activating them.
- Local APK compilation and bench tests still pending.

## 0.1.0 — source prototype
- Local Wi-Fi UDP MAVLink RC override with heartbeat gate, timeout, disconnect, and offline endpoint persistence.
## 0.8.0
- Record transmitted live rover controls and validated global position telemetry in the Test route session.
- Set a fixed Home from the first precise phone GPS fix and draw later phone movement as a separate route.
- Add MapLibre Test map with pinch, compass, route reset, CSV export, and access to the offline simulator.

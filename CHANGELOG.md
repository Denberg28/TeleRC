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

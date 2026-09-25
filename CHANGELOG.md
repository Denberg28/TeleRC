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

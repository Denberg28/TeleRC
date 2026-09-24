# Changelog

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

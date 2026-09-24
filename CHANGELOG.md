# Changelog

## 0.2.0 — rover private test source
- Rover steering CH1 and bidirectional throttle CH3; spring return to 1500 µs neutral.
- Explicit control enable after checksum-valid heartbeat; control disables on link loss, pause, or disconnect.
- Best-effort neutral and RC override release on disable; profile model reserves other craft without activating them.
- Local APK compilation and bench tests still pending.

## 0.1.0 — source prototype
- Local Wi-Fi UDP MAVLink RC override with heartbeat gate, timeout, disconnect, and offline endpoint persistence.

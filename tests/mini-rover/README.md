# MiniRover wire compatibility fixture

`MiniMavlink.h` is an unchanged copy from `TeleRC-MiniRover-FS-iA6B-iBUS-v1.1.0-candidate.zip` (15 October 2026). This fixture is not a firmware update. The archive SHA-256 is recorded in `docs/MINI_ROVER_WIFI.md`.

`emit-heartbeat.cpp` emits the real mini rover heartbeat format, including MAV_AUTOPILOT_GENERIC, ground-rover type and component 1. The JVM test consumes that fixture and writes packets using Android production `Mavlink`. `interop.cpp` then feeds those exact packets to the mini rover decoder and verifies target, source, channels, release, normal ARM/DISARM and corrupt-checksum rejection. These are protocol tests, not device/Wi-Fi or motor tests.

Run after the Android JVM tests:

```sh
g++ -std=c++17 -Wall -Wextra -Werror tests/mini-rover/interop.cpp -o /tmp/mini-interop
/tmp/mini-interop app/build/mini-rover-interop
```

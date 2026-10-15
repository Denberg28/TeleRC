# TeleRC 0.8.56 — full interface with mini rover Wi-Fi arm/disarm

This restores the previous full TeleRC app. The Wi-Fi bridge, USB LoRa, Controls, map/test/game, themes and diagnostics are retained. The mini Wi-Fi addition selects the fixed `192.168.4.1:14550` endpoint and local port 14550, and requires fresh centered control frames before ARM. The ARM/DISARM label changes only when rover heartbeat reports the actual state.

Install signed 0.8.56/code 74 over 0.8.55/code 73 without uninstalling. The higher version code implements a behavior rollback without Android's lower-version installation restriction. Existing settings and app identity remain.

## Mini rover connection

1. Keep the existing standalone **Wi-Fi-only mini rover sketch 1.2.0**. No new sketch upload is required. It needs no receiver or flight controller.
2. Raise the wheels. Join **TeleRC-MiniRover**, password **telerc123**. Choose Stay connected/use without internet. Close PC TeleRC; the sketch permits one Wi-Fi client.
3. In Setup choose **Mini rover Wi-Fi**, then Connect. Existing bridge endpoints remain saved for their own connection profile.
4. Open Controls, tap **ENABLE CONTROL**, keep both sticks centered until **ARM · READY**, tap ARM and wait for ARMED confirmation. Steer CH1, drive CH2, neutral 1500 µs.
5. DISARM clears axes, sends normal MAVLink command 400 without force-arm parameters, and waits for the rover's reported state. STOP CONTROL releases mini rover override and disarms it. Leaving Controls/backgrounding revokes app driving and sends neutral. The standalone sketch independently disarms if valid motor commands expire after 500 ms. Reconnection never enables or arms automatically.

For the normal Wi-Fi bridge/FC or USB LoRa, retain the previous setup and downstream safety rules. A receiver-gated 1.1.x mini sketch still requires its documented radio permission sequence; the APK does not bypass firmware permissions. The standalone mini rover ignores F1/F2/F3 auxiliary outputs and has no measured GPS unless additional supported telemetry is supplied; map estimates remain estimates.

## Verification and limits

The restored app is checked with the full JVM tests, debug/release lint/build, legacy bridge host/sanitizer tests and Kotlin-to-mini-parser protocol checks. The firmware is unchanged from the six ESP32-S3 configurations verified for 1.2.0 in release 0.8.55. No hardware compatibility or installation success is claimed without testing the actual phone/rover.

Raised-wheel checks: confirm all original tabs/features are present; install over 0.8.55 with settings retained; select each intended connection mode; connect the mini Wi-Fi profile; verify ARM is refused before Enable and fresh neutral; test ARM/DISARM heartbeat confirmation, spring return, STOP, background/tab change, Wi-Fi loss and recovery without automatic motion. Measure stopping separately. Retain motor-power cutoff access. Stable promotion remains blocked by hardware checks.

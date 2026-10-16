# TeleRC 0.8.64 and Mini rover 1.2.3

Baseline: released app 0.8.62/code80, commit bc7e983b88f2deea28aaf7c81e5ecffc159da949, with mini 1.2.2 firmware. Candidate: app 0.8.64/code82 and standalone mini firmware 1.2.3-mx1508-95pct. The signed app upgrades in place with existing settings. No main merge or stable promotion is included.

## Navy appearance

The existing Blue selection becomes Navy, so a saved Blue preference automatically uses the new palette. Filled actions use dark navy #172F53 with white labels. Light/Sepia use that navy for accents; Dark uses a lighter navy-derived ink for legibility while retaining the dark navy button fill. Teal, Violet, Slate and current compact Setup/Controls geometry are retained.

## Offline Test

Open the page dropdown on Setup or Controls and choose Test. The same landscape joystick sizes, CH1 steer and CH2 forward/reverse response settings surround the center maze. START is lower left; END is upper right. A white nose on the rover indicates forward direction. Steering rotates the rover, including in place; drive moves it forward or backward. Walls block movement. The practice physics is an approximation, not a measurement of physical acceleration or stopping distance.

Every shuffled 15×11 maze has one connected passage network and a route from START to END. Shuffle creates a new course, Retry keeps the course and resets the run. Start begins active timing; Pause/Resume, backgrounding and the Clear times dialog stop motion and timing, reset both sticks, and require explicit Resume. Reaching END freezes the timer and saves a clear once. Retry is required for another attempt.

The current maze seed and up to 50 latest clear records survive restart. Clear times shows the latest 20 with seed/date; the center shows the best retained time for the same maze. Times on different random courses are not directly comparable. An unfinished run resets after activity or process recreation, and never becomes a saved clear. Active practice time excludes pauses; this is an informal practice record, not an anti-cheat leaderboard.

Entering Test disables app control, cancels queued ARM, sends the existing neutral/disconnect handover for the captured link, closes live transport, and switches off auto-connect. Test does not create a map/camera, request location, connect a transport, assign servos, or arm a rover. Late USB permission grants and auto-reconnect cannot reconnect while Test is open. Returning to Controls requires explicit Connect/Enable/ARM again. A dropped handover still relies on the receiver watchdog; simulator isolation does not prove physical motor stopping.

## 95% MX1508 sketch

The user confirmed that 95% means maximum motor PWM output. MX1508 maximum demand is now 242/255 (94.90%, nearest integer to 95%), proportional across the stick range, including steering pivots and mixed inputs. There is no launch boost or new acceleration ramp. Other driver profiles retain 180 and their existing steering limits. The 20 kHz PWM, GPIO pairs, 30 ms reversal coast, 250 ms neutral dwell, 500 ms command watchdog, explicit arm/recovery, PWM-fault latch and zero-output neutral/disarm remain.

App sensitivity and firmware ceiling multiply: with Drive sensitivity 100%, a 1900 µs forward command produces OUT=242. With Drive 95%, full stick is 1880 µs and produces OUT=229 (about 90% of 255 after the deadband). Use Drive 100% if you want the firmware's full 95% ceiling. Existing saved sensitivities are preserved. PWM percentage is not measured speed, acceleration, motor voltage regulation or current limiting.

The sketch is for the standalone ESP32-S3 mini rover. Extract the sketch ZIP, open TeleRC_MiniRover_WiFi/TeleRC_MiniRover_WiFi.ino in Arduino IDE, install ESP32 core 3.3.2, choose ESP32S3 Dev Module with USB CDC on boot enabled, verify Config.h and HardwareProfile.h for your installed driver/count, then upload. Default four-motor MX1508 pairs: left motors GPIO4/5 and 6/7; right motors GPIO8/9 and 10/11. Keep the established common ground and motor-rated supply. Default prebuilt binaries are only for this four-motor MX1508 configuration; use the included source for another profile. Do not flash this standalone sketch to the dedicated motor/gateway architecture.

The APK can practice without flashing firmware. Flash mini 1.2.3 to apply the 95% motor ceiling; older firmware retains its previous ceiling. MAVLink formats and Wi-Fi SSID/port are unchanged. Other FC/LoRa firmware does not need this mini sketch.

## Acceptance and evidence

Software packaging requires Android JVM tests/debug and release lint/build; seeded maze solvability/uniqueness, collision, timer, finish-once, pause/reset and record tests; native 12 theme/accent Setup/Controls/Test variants, actual joystick movement, background pause, retry/shuffle and stored-record restart checks; full legacy/mini host and default ASan/UBSan regression, twenty driver/count/API host profiles, six actual ESP32-S3 core 3.3.2 configurations; Android-to-mini parser integration; retained signer and increasing version, ZIP/native alignment and SHA-256/ZIP integrity. BUILD_INFO and the workflow reports identify actual completed results and source commit. Native UI completion is required for this candidate; startup, app or assertion failures block publication.

Local firmware host/profile/sync tests PASS; local ASan/UBSan runs use detect_leaks=0 because process inspection is restricted. Android SDK and Arduino toolchain are unavailable locally; the cloud workflow performs actual APK/target builds and default sanitizer checks. Hardware, phone installation and electrical/ground performance remain BLOCKED. Candidate status only, no field validation.

With restrained wheels and reachable motor-power cutoff:

1. Install over 0.8.62 without uninstalling. Verify retained sensitivities, navy selection, all three pages and simultaneous joystick touches.
2. Join rover Wi-Fi, Connect, Enable, center to neutral-ready, ARM. With Drive 100%, check Serial at 115200: CH2=1900, OUT=242; at Drive 95%, CH2=1880, OUT=229. Check steering pivots both ways, mixed commands, progressive small inputs and no output before ARM. Verify configured motor voltage, current and temperature under load.
3. Confirm neutral and DISARM give zero, reversal coasts, command loss disarms after the configured 500 ms threshold, and recovery requires fresh neutral plus explicit ARM. These are software timing thresholds; measure actual physical stop distance.
4. While commanding motion with wheels restrained, enter Test. Confirm neutral/disconnect and motor-off; move both practice sticks and Shuffle/Retry without real motor motion. Repeat with USB LoRa and a late USB grant, background/return, process recreation, heartbeat recovery and repeated tab switches. Return to Controls: it must remain disconnected/disabled until explicit action.
5. Finish a maze, confirm the time and same-maze best, Retry, Shuffle, background/Resume and restart to verify record persistence. Test narrow landscape, large font and Dark visibility on the actual phone.

Rollback: retain app 0.8.62 and mini 1.2.2 assets/source. Android normally rejects a lower version code; use a future same-signer higher-code restoration to preserve data. Do not uninstall to downgrade. Mini 1.2.2 can be reflashed with motor power isolated but restores full 255 MX1508 duty. Next concrete check after software success: phone upgrade and the restrained-wheel sequence above.

## Native runner correction and candidate provenance

The first 0.8.63/code81 candidate at 73590b197ed9c7cd81297bbfefbdbe98fe66ba9e passed Android/firmware software checks, but the emulator image installation exhausted runner disk before capture. Its BUILD_INFO correctly records native UI BLOCKED. A progress message incorrectly inferred UI success from the continued step; the full log revealed the startup failure. No native visual pass applies to that candidate.

This 0.8.64/code82 candidate keeps the feature implementation and adds early cleanup of completed Arduino and NDK tooling before emulator image download, with a free-space prerequisite. Native UI success is now mandatory. It compares the signer directly with published 0.8.63 and keeps increasing version codes, without replacing or retagging prior artifacts. The new final workflow and BUILD_INFO carry the actual UI outcome. Upgrade from 0.8.62 or 0.8.63 in place.

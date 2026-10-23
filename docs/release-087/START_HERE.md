# TeleRC Balance Pre-test — v0.8.87 + firmware v0.3.0

Install the signed TeleRC-v0.8.87.apk over your existing app. Keep app data.
Upload TT_Balancer_ESP32S3/TT_Balancer_ESP32S3.ino with all its headers beside it,
using the ESP32-S3 board/flash/USB settings matching your board. Disconnect motor
power for upload. Espressif3.x supplies all dependencies; cloud checks used3.3.2.
Do not erase all flash if you want to retain saved gyro calibration.

1. Join phone Wi-Fi TeleRC-Balancer (default password telerc123). In TeleRC Setup,
choose Mini rover Wi-Fi, address192.168.4.1 and port14550, then Connect.
2. Tap Balance immediately before Guide in the upper-right Setup header.
Wait for firmware status. Unsupported/older firmware keeps setup actions locked.
3. CAL_SAVED_READY / REUSED means the saved offset already passed its short check.
Use Calibrate & save only when needed: disconnect motor power, hold upright and
still, confirm, and wait for rover restart and new calibration. No Serial needed.
4. Check regulated motor supply/common ground and secure both wheels clear of
floor. Tick the suspension confirmation, then test Left, Right and Both. Each
pulse remains55/255 for150ms. Firmware test completion does not prove movement;
if wheels stay still, check the physical supply/driver/wiring rather than
confirming unobserved directions. STOP stays available for a recognized board.
5. After observing both wheels propel forward and forward/back tilt produces
positive/negative angle, hold upright/still and tap Confirm directions. A
completed Both test is required. Default confirmation is per boot.
6. Open Controls, enable Phone control, center sticks, then ARM while supported
upright. Setup never arms or enables driving. Support the chassis before disarm.

Scroll the guided panel for lower steps, especially with larger system text.
The tab menu also contains Balance. Calibration, angle/rate, saved/reused status
and exact readiness blockers are shown live; motor test/verification/calibration
requests expire and are not automatically retried after a missed reply.

Keep all wiring and board settings from WIRING.md/Config.h. The application
cannot measure motor supply voltage/current, prove wheel direction or tune PID
from these tests. Physical balancing remains unverified. The firmware retains
MPU6050 and MPU6500-compatible ID0x70 profiles and saved gyro offset compatibility.

No other rover firmware needs changing. This is a paired update for the balancer.
Saved app settings, game wallet, mission planning and FPV are retained. Stable
channel remains unchanged; install this APK manually for restrained bench tests.
See BUILD_INFO.txt for source, check results and remaining hardware limits.

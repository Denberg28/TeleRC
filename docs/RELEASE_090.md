# TeleRC v0.8.90 + balancer firmware v0.3.1

Step2 motor tests now apply140/255 PWM at20kHz for300ms, replacing the old
55/255 for150ms pulse that may not start a TT motor. Tests remain explicitly
confirmed, timed and stopped locally; the balance PID/gains and duty limits,
calibration storage, direction verification and fault protections are retained.

Balance setup displays the test reply, controller fault reason and last-test
PWM register readback after the pulse ends. No movement opens pin/power checks.
Calibration ready already completes step1. Confirm directions additionally
requires acknowledging actual observed wheel motion and sensor tilt signs.
Software completion/register readback does not prove physical wheel motion.

## Install in this order

1. Install TeleRC-v0.8.90.apk over0.8.89; signing identity and app data are retained.
2. Disconnect motor power and upload the complete TT_Balancer_ESP32S3 sketch
   from TT-Balancer-ESP32S3-TeleRC-v0.3.1.zip. Keep all headers with the .ino.
   Use the board/flash/USB settings matching the physical ESP32-S3. Avoid
   erase-all to preserve compatible saved gyro calibration.
3. Reset upright/still. Connect to TeleRC-Balancer / telerc123,192.168.4.1:14550.
   Open dropdown → Balance. If calibration is ready, proceed to step2.
4. Apply the regulated motor supply and common ground. Secure the chassis with
   both wheels raised, acknowledge power/suspension and test Left → Right → Both.
5. Observe actual forward wheel movement and positive/negative forward/back
   tilt signs. Only then check the observation box and Confirm directions.
   Setup never arms; use Controls → Phone control → center sticks → ARM.

Firmware0.3.1 requires app0.8.90:0.8.89 rejects its longer/extended test report.
The new app remains compatible with firmware0.3.0. Left motor inputs areGPIO4/5,
rightGPIO6/7;GPIO8/9 remain IMU SDA/SCL. Do not copy the4-wheel MiniRover motor map.
If PWM readback appears without movement, measure regulated motor voltage at
MX1508 +/− during the pulse and check input/output wiring with power removed.

## Verification and limits

Release automation requires JVM tests, debug/release lint/build, retained signer
and increasingcode108, host/default sanitizers,20 MiniRover profiles, nativeAPI35
UI regression in three themes/large text, and both actualESP32-S3 mounting
builds with Arduino-ESP323.3.2. BUILD_INFO and test evidence identify the exact
source/run and final results. SHA256SUMS verifies all downloadable assets.

This is a prerelease for restrained bench testing. Actual phone/rover Wi-Fi,
motor voltage/current/starting torque/direction, stop timing and balance/PID
performance remain NOT TESTED. The physical no-movement report is not yet
confirmed resolved. Public release creation was authorized by the owner.
The current stable channel remains as previously selected.

Rollback: with motor power removed, upload firmware0.3.0; the new app supports
it. Keep saved app data and gyro storage. Prefer a forward-code app rollback
rather than uninstalling to downgrade, which can lose local data.

## Release qualification correction

Initial run39535542355 passed host/default sanitizers, JVM/debug+release
build/lint and both actual firmware targets. Native UI timed out in the existing
replay reward test before Balance: the route helper could hand off neutral drive
while still outside END. The helper now stops simulated inertia, aligns in place
and explicitly supplies forward drive for the production View frame to finish.
Game code, END threshold, onClear/wallet behavior and all reward assertions remain
unchanged. The complete native suite and release gates must pass on the corrected
source before publication; final BUILD_INFO identifies that run.

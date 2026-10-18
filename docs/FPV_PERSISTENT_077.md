# TeleRC v0.8.77 — persistent USB FPV

TeleRC remembers the last receiver's VID/PID and camera/map choice. Replugging can change Android's device ID; recovery matches the remembered receiver type and opens only one unambiguous, currently granted device. Repeated Connect taps keep an existing session. Actual USB permission is checked each time, never stored as a Boolean grant.

Select TeleRC and Always / Use by default in Android's USB app chooser when offered. The app cannot choose that system default itself. Existing USB grants are reused; automatic recovery never intentionally requests permission. If Android revokes access, tap Connect FPV to approve it. Denial stops reconnect until an explicit retry. Long-press Connect FPV for USB default help, diagnostics, or Auto reconnect On/Off. CAMERA permission must also be granted; choosing Android's one-time camera access can require a later approval.

Recovery retains the desired connection across unplug/replug, Controls view recreation and foreground resume. USB permission is separate from camera-open timeouts. Permission callbacks are scoped to the requesting view/current attachment; disposed and old-session callbacks are ignored. A destroyed texture stops preview so its replacement restarts correctly. Only foreground, visible camera previews open USB sessions. Background/other tabs release camera resources; returning to Controls restores the requested preview without enabling or arming the rover.

Retry delays are 1, 2 and 4 seconds, then at most once per 30 seconds while requested. A sustained 3-second sequence of fresh frames resets that budget. Recovery, new camera opens and permission requests disable control. Physical stopping and USB stability are not proven by software checks.

## Validation gates

Baseline v0.8.76/code94, source b193dda4400de32e07b29a4d6e18e8b87bf5222a. Candidate 0.8.77/code95. Firmware/protocol/CH1/CH2 and signing identity unchanged; no firmware reflash.

| Gate | Required evidence |
| --- | --- |
| A source/build | JVM tests, debug/release lint/build, instrumentation APK |
| B behavior | Production-policy tests: new USB ID, existing/default grants, one pending request, denial, stale grant, ambiguity, repeated Connect, revoked access, background/hidden/stopped gating and sustained-frame recovery |
| C regression | Bridge sync, host/default ASan/UBSan, existing JVM and native photo/theme/maze/FPV suites |
| D integration | Native no-device repeated Connect, menu/default help, auto reconnect off, disposed receiver cleanup; signer/code/permissions/USB registration/CRC/alignment |
| E physical HIL | BLOCKED — actual phone, UVC receiver, hub/power and rover not attached |
| F delivery | Signed candidate APK, source/evidence archives, BUILD_INFO and verified SHA256SUMS; no stable promotion |

The completed workflow and BUILD_INFO supply final software statuses. Unit decisions and emulator no-device checks are not actual UVC reconnect measurements. Android/OEM chooser wording/default persistence and USB power/cable/RF issues still require the user's phone/receiver.

## Acceptance on the phone

1. Install over v0.8.76 without uninstalling; confirm settings, maze wallet and center-pane layout.
2. With motors restrained/raised, connect the UVC receiver. Select TeleRC and Always / Use by default if Android offers it. Grant CAMERA while using the app.
3. Start FPV. Tap Connect repeatedly: the picture should not reopen or ask again while its grant/session is valid. Switch map/camera, background/resume and return to Controls; no automatic enable or arm.
4. Replug the same receiver ten times. With Android's default/grant active, verify automatic reopening without a new app-generated permission request. If access is absent, the app waits for Connect rather than repeatedly prompting.
5. Deny a permission request: no loop. Explicit Connect permits a new request. Remove the receiver while its prompt is pending; a late callback must not open a replacement device.
6. Test two identical receivers: no arbitrary device selection. Different receiver types require an explicit Connect with one receiver attached.
7. Briefly interrupt frames/power, restore it, and verify sustained fresh frames reset the retry budget. After repeated failures, recovery slows to 30 seconds; Auto reconnect Off stops it.
8. Verify STOP, background and link loss still neutralize the rover; camera recovery never re-enables or arms it. Record actual recovery time, prompts, VID/PID and errors.

Rollback: Android blocks versionCode downgrades. A forward-version rebuild of v0.8.76 with the same signer can restore earlier behavior while retaining data. Avoid uninstalling unless data loss is acceptable.

Android references: https://developer.android.com/develop/connectivity/usb/host and https://developer.android.com/reference/android/hardware/usb/UsbManager . Pinned UVC helper review: shiyinghan/UVCAndroid 1.0.13 CameraHelper/CameraConnectionService/USBMonitor.

# TeleRC 0.8.59 — compact controls and Sepia background

Signed test candidate, Android versionCode 77, based on released 0.8.58 (`2228f1ea8315ef25311965230f693eebdc19a6ba`).

- Navigation contains Setup and Controls. Test drive/game and the LoRa provisioning page are removed.
- Light, Sepia and Dark backgrounds are available from Setup. Existing Light/Dark preference and independent accent colors are retained.
- Reduced outer padding and wider joystick panels leave a central map/USB FPV view with the swap/connect buttons below.
- Independent steering/drive sensitivity, expo, explicit Enable/ARM, F1–F3, Wi-Fi choices and USB LoRa operation remain.
- Route CSV export moves to Setup → Diagnose link → Export route. Recorded routes and existing map estimate settings remain local.

## Installation and checks

Install `TeleRC-v0.8.59.apk` over the existing signed app without uninstalling. No firmware reflash is required; keep the installed mini 1.2.2 or the existing matching FC/receiver firmware. Provision a new LoRa board through its existing firmware/configuration tools before connecting; this compact app no longer offers radio provisioning.

For video, attach a USB UVC receiver/capture device using Type-C OTG. Select Controls → Connect FPV, approve camera/USB access, then explicitly Enable control again. Show camera / Show map swaps the central view. Simultaneous USB LoRa and video require a suitable powered hub. See USB_FPV.md for receiver requirements and signal limitations.

Raised-wheel acceptance: verify both sticks spring to neutral; independent sensitivities/expo persist; Enable then centered ARM reaches heartbeat-confirmed state; STOP, tab change, background, heartbeat loss, DISARM and disconnect stop command authority correctly. Reconnect must require Enable again. Verify fresh video, camera/map swaps, USB unplug/replug, hub contention and route export on the actual phone. Check Light/Sepia/Dark persistence and readable controls at the phone's font/display settings.

## Evidence and limits

Release publication requires full JVM regression, debug/release lint/build, host safety and default ASan/UBSan checks, Android-to-mini parser integration, retained signer/upgrade identity, APK alignment and checksums. The workflow build evidence ZIP contains reports and BUILD_INFO.txt binds the package to its source commit. Actual firmware is unchanged; no new target compilation is claimed.

Local Android compilation is BLOCKED by unavailable SDK/Gradle network; GitHub Actions performs those gates. Phone installation, visual device QA, receiver/hub operation and rover HIL remain BLOCKED until measured. This release is a prerelease candidate; stable readiness is not claimed.

Rollback: 0.8.58 retains the previous pages. Android rejects a normal downgrade to its lower versionCode; use a future signed higher-code restoration to preserve app data. Do not uninstall or erase stored data just to roll back. Next check: install this candidate on the phone and perform the acceptance sequence above.

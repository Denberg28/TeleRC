# Skydroid 5.8 GHz OTG receiver — TeleRC 0.8.66 test build

Baseline: remote review head 445fd0369c2b98e34e2a968b7596a5610ab169c1, app 0.8.65/code83. Retained stable: 0.8.64/code82. This app-only patch is 0.8.66/code84. Firmware, control channels, wire formats and native video dependency remain unchanged. No firmware reflash.

## Review findings and changes

The Activity lacked USB_DEVICE_ATTACHED intent/metadata, so Android could not offer TeleRC as a USB default handler. Register USB video class 14; Android checks interface classes too, covering composite receivers without claiming serial LoRa devices or all USB peripherals. Use singleTop and handle cold/warm attachment launches against currently enumerated devices. Open Controls/camera and neutralize app authority before requesting CAMERA/USB access. Attachment and preview completion never enable controls or arm.

The old receiver scan already checked video interfaces. Its empty-result message conflated no USB enumeration with connected non-UVC devices. Distinguish both cases and add a long-press Connect FPV diagnostic showing VID/PID, class/interface classes and permissions. Do not assume a Skydroid VID/PID, proprietary protocol, resolution or tested compatibility without descriptors/hardware evidence. Default-app registration does not itself fix an empty Android USB bus.

## Install and use

Install the signed test APK over the current TeleRC; retain settings. Disconnect the receiver. If another FPV app opens automatically, use Android Settings → Apps → that app → Open by default → Clear defaults (wording varies by phone). Close that app. Reconnect the Skydroid through a data-capable OTG connection; choose TeleRC and select the always/default option if Android offers it. Grant CAMERA/USB access. If Android does not present a chooser, open TeleRC → Controls → Connect FPV.

Hold **Connect FPV** for USB diagnostics. No USB devices means Android has not enumerated the receiver: check phone OTG, cable/data contacts and power. Connected device but UVC false means its descriptors do not expose USB video class; retain the VID/PID/interface listing for follow-up. Do not change developer USB file-transfer settings as a substitute for OTG host mode.

## Acceptance and evidence

A source/build: source review and local diff/XML checks PASS; local Android SDK/Gradle BLOCKED by missing SDK/network. Required cloud unit/debug/release lint/build and compiled manifest/signer checks are recorded in BUILD_INFO only after success.

B behavior: regression tests cover video-class and composite descriptor selection and exclusion of serial/vendor-only devices. Real default chooser, permission denial/grant and video format negotiation BLOCKED without phone/receiver.

C regression: local host tests PASS; local default LeakSanitizer BLOCKED by /proc restrictions; local ASan/UBSan PASS with leak detection disabled (15 C++ configurations and four Python relay tests). Cloud runs full default sanitizers before producing the test artifact.

D software integration: compiled APK checks require action/metadata pair, video class filter, singleTop, retained signer, app ID, increasing version and unchanged capture permissions. Actual USB/LoRa hub coexistence and simultaneous video/control NOT TESTED.

E phone/HIL: BLOCKED. Raise wheels and keep motor cutoff reachable. Connect with live authority enabled; verify neutral/disabled before dialogs. Test cold/warm default launch, deny/grant CAMERA/USB permission, reconnect, another app's default, background/return, map swap and removal. Verify no auto-enable/ARM or stale video after USB loss. Record phone/Android, exact Skydroid model, VID/PID, format and USB power/cable. Measure frame latency before FPV driving. USB frames do not prove healthy RF reception.

F delivery: an unpublished draft download contains the signed APK, source, guide, reports, BUILD_INFO and checksums. GitHub Actions artifact storage quota blocked ordinary workflow artifacts; use the draft to retain the verified package without publishing a release or promoting stable. Firmware unchanged so no affected ESP32 compilation required. Retain 0.8.65/0.8.64 for rollback; an Android downgrade may require uninstalling and losing private settings/routes. Next check: install this APK and validate chooser plus the diagnostic listing on the actual Skydroid.

Reference: Android USB host discovery/permission contract, https://developer.android.com/develop/connectivity/usb/host .

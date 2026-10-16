# TeleRC 0.8.67 — FPV recovery and photo backgrounds

Baseline: qualified 0.8.66/code84 source 045c322df8b749fbf7ac93ebc4227425bbdf2a0c. Candidate: 0.8.67/code85. App-only; firmware, pins, control protocol, signing identity and UVC dependency unchanged. No reflash.

## Use

Hold Connect FPV → choose Auto, YUYV compatibility, MJPEG or USB diagnostics. The format preference survives restart. For the reported colored-line picture, try YUYV compatibility first. Only advertised resolutions/formats/frame rates are selected; Auto prefers MJPEG and 640×480, then advertised nearby modes/rates. If a requested format is absent, choose Auto. Connect FPV resets the three-attempt recovery budget. Failure/open timeout/stalled USB frames trigger 1/2/4-second bounded retries, advancing through advertised profiles. Recovery disables app control; it never enables control or arms. Permission denial stops recovery. Background/return may reopen the same permitted receiver after a short release delay; no unattended permission requests. Removal requires reconnect/default-app selection or Connect FPV. Map hiding pauses preview without destroying the desired session.

The screenshot proves frames are being rendered, not which component corrupts them. A format/decoder mismatch, RF channel/signal/PAL/NTSC, receiver/camera power or USB transport may be responsible. Fresh USB frames can carry static, broken, frozen or no-signal video. This update does not classify RF health or automatically detect image corruption. Use diagnostics to record actual format, advertised profiles and last error; compare the same receiver/channel in its known-working viewer.

Setup → Background → Choose image imports a private offline copy. The current Light/Sepia/Dark tone overlays the image for readable text; controls/cards and the live video remain distinct. Remove image restores the plain theme. Cancel/invalid/oversized import retains the previous image. Input is limited to 20 MiB / 100 megapixels and decoded longest edge to 1920 pixels; the saved copy is a still JPEG. Modern Android honors image orientation; Android 8 uses bounded sampling and EXIF transforms. No broad storage permission or persistent gallery access is needed.

## Review and verification gates

A source/build: inspect generation-guarded USB callbacks, single preview/surface ownership, finite reopen budget, permission gating, private atomic photo import and bounded off-UI decode. Local motor-core sync/host regression PASS; local Gradle/SDK unavailable. Required cloud JVM, debug/release lint/build and instrumentation APK build are recorded in BUILD_INFO after success. Firmware untouched: no affected actual ESP32 target.

B behavior: JVM tests cover advertised profiles, unsupported formats/descriptors, image bounds/sampling and recovery backoff. API 35 native checks cover photo import/decode, invalid/oversized input preserving old bytes, actual Setup/Controls rendering under three tones, private-file persistence/restart/removal and the FPV format menu. Real DocumentsUI/gallery result delivery and API 26 EXIF import remain phone checks; they are not claimed by direct native import tests.

C regression: host tests and local address/undefined sanitizer checks run; local LeakSanitizer /proc access remains BLOCKED. Cloud full default sanitizers required. No bypass of control/ARM watchdogs.

D software integration: compiled USB chooser action/metadata/class, app ID/version, original signer, capture permissions, ZIP alignment/CRC and checksums required. USB open/format/frame-gap/suspend behavior source-reviewed; no synthetic USB tests presented as receiver compatibility.

E phone/receiver HIL: BLOCKED. With raised wheels and motor cutoff reachable, install over the old signed app. Check default USB launch, CAMERA/USB deny/grant, Auto and advertised YUYV/MJPEG, 20 map swaps, 10 background/return cycles, USB removal/replug and simultaneous sticks/video. Verify neutral/disabled control before dialogs and after any recovery; explicitly re-enable after link/preview recovery. Record Skydroid model/VID/PID, phone/Android, selected and advertised modes, receiver power, RF channel/PAL/NTSC and frame/latency evidence. Photograph/cable/power issues cannot be fixed by USB permission changes. Test low/no RF signal separately from USB stalls. Test Choose image/cancel/replace/remove, restart and all pages with a large/rotated image and reject invalid/over-limit images. Keep prior photo after failed import.

F delivery: signed software-verified test build in an unpublished draft because Actions artifact quota is full. Include APK, source, native screenshots/reports, guide, BUILD_INFO and checksums. No stable promotion/field claim. Retain 0.8.66 APK; Android downgrade may require uninstalling and losing private settings/routes/photo. Next concrete check: compare Auto versus YUYV on the actual Skydroid and verify a chosen gallery image survives restart.

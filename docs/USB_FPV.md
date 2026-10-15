# USB FPV and larger controls — 0.8.58 development

Baseline: Denberg28/TeleRC commit `47cdf3e88f3f83fc9877890fdd0eec7a2befb052`, version 0.8.57 / code 75. This Android-only change uses version 0.8.58 / code 76. Firmware and MAVLink formats are unchanged.

## Operation

1. Connect a **USB UVC video receiver/capture adapter** to the phone's Type-C port through a data-capable OTG connection. An analog/composite or HDMI receiver needs a USB UVC capture adapter. The Type-C connector alone does not establish UVC compatibility.
2. Open Controls and tap **Connect FPV** below the center view. Grant camera permission and USB access. This action neutralizes/turns off app control before either permission dialog. Camera connection never arms or enables the rover.
3. After video appears and dialogs are closed, explicitly enable control again when ready. Steer and Drive remain at both sides of the center view.
4. Tap **Show map / Show camera** below the center view. View swaps do not revoke joystick authority, reset the route, or reopen USB permission dialogs. A hidden camera preview pauses; map recording and dead reckoning continue according to the existing control/route rules.
5. After fully backgrounding the app, returning may require **Connect FPV** again. Re-enable rover control explicitly. Camera removal shows a disconnected message and leaves the independent control link governed by its existing timeout/stop rules.

Joystick panels are 20% wider; circles use more of the panel width. On width-limited landscape screens the circle diameter increases about 30%. Both visual radius and input travel share the same geometry. The spring return, channel mapping and configured sensitivity are retained.

## Hardware compatibility and limits

- Direct UVC support uses `com.herohan:UVCAndroid:1.0.13`; it does not depend on a phone vendor exposing the receiver through Camera2. Only one video device is selected; multiple UVC devices produce an instruction to leave one attached.
- A proprietary receiver that does not expose USB video-class interfaces needs its vendor's SDK and is unsupported by this implementation. The phone must support USB host/OTG and supply adequate power.
- With rover Wi-Fi, the receiver uses the USB port and control remains on Wi-Fi. With a USB LoRa base, use an OTG hub with sufficient power for both devices. The camera filters USB video interfaces; the LoRa transport keeps its ESP32-S3 CDC filter. Sharing a hub is **not hardware verified**.
- Video keeps its aspect ratio with black bars. The dependency negotiates its default preview format; receiver-specific format/resolution selection is not implemented. No video recording, microphone capture or storage permission is requested.
- A black status overlay masks a retained TextureView image after disconnect/error/pause or a two-second USB frame gap. A USB frame timestamp cannot detect a receiver that continues to emit a frozen or no-signal picture after RF loss. This is not measured camera latency or RF link-health monitoring.
- Camera native libraries, USB permission handling, receiver formats, actual execution on 16 KB page-size phones, simultaneous touch/video/map behavior and power draw require phone/receiver testing. No field/stable claim is made.

## Bench acceptance

Use raised wheels and a reachable motor-power cutoff. Record phone/Android, receiver model, USB VID/PID, video format, hub/power source and firmware versions.

1. Open Controls on small/large landscape screens, in both themes. Confirm larger circles/knobs, visible labels and no overlap with center actions. Drag each stick to both extremes; release/cancel and multi-touch must still give 1500 µs neutral on the assigned channel.
2. With app control enabled, press Connect FPV. Confirm neutral and disabled control before CAMERA/USB permission prompts. Deny each prompt, reconnect, grant, then check video. Neither completion nor heartbeat recovery may enable/arm the rover.
3. Grant camera permission while the app pauses/resumes; background during USB approval; switch tabs before completion. No preview may attach to a destroyed screen. Re-enter and reconnect normally.
4. Re-enable control. Swap map/camera 50 times while moving both sticks. Verify no channel discontinuity attributable to a view swap, no route reset and no replay of ARM commands. Check sustained 10 Hz RC and downstream timeout behavior under video load.
5. Unplug camera USB while steering; verify a disconnected overlay, no old frame displayed as live and no accidental LoRa USB selection. Repeat with Wi-Fi control and a powered hub/USB LoRa base.
6. Remove only the FPV RF signal, keeping USB attached. Record what the receiver renders; do not interpret ongoing USB frames as a healthy RF link. Measure real glass-to-glass latency if FPV driving is intended.
7. Background/return and rotate/change theme repeatedly; confirm neutral authority, camera cleanup and explicit reconnect/Enable. Test unsupported receiver and low-power hub without allowing app crashes or stale command replay.

## Build and rollback

Run the existing Android debug tests/lint/build and release lint/build gates with SDK 35/JDK 17 and the pinned Gradle wrapper. A source compile is not an APK build or installation test. Use the retained release keystore for a release APK; never substitute a new signing identity.

Keep the previous APK and export routes/settings before changes. Roll back the Android source patch to baseline `47cdf3e`; no firmware rollback or reflash is needed. Android version-code downgrade can require uninstalling the app, which erases private data. Preserve the original signing identity.

This update is built on the released 0.8.57 branch/tag. It retains Control response steering/drive sensitivity and expo, legacy preference migration, Mini rover Wi-Fi and its fresh-neutral ARM gate, normal Wi-Fi/FC and LoRa USB choices. No 0.8.54 artifact should be substituted for this update; its earlier main-branch baseline predates 0.8.57.

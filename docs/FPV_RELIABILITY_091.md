# TeleRC 0.8.91 — external USB FPV reliability candidate

Baseline: paired prerelease v0.8.90/code108, release commit
f25a3388736344a373dcb65e488b2a0d422291aa (identical local source tree).
This Android-only update is code109. Keep balancer firmware0.3.1 and other
firmware unchanged. No vehicle protocol, authority, PID or wallet migration.

## Result

TeleRC remembers the selected UVC receiver and automatically reuses actual
Android USB grants. A fresh validated USB attach/default launch requests a new
connection even after a previous manual disconnect. Background restoration
still honors Auto reconnect Off, and no automatic path requests permission.
Repeated explicit Connect reuses a valid session/grant; denial does not loop.

Video format negotiation now keeps the USB session open, queues an advertised
preview-size update in the pinned UVC helper and confirms readback before
starting preview. The old code released the whole receiver for this adjustment.
A transient frame gap gets one preview restart first. No-frame startup gets8s;
a previously active stream gets3s of missing rendered frames before that
restart. Another failure after the restart escalates to a USB reopen with the
existing bounded1/2/4/30s retry delays. Three seconds of sustained fresh frames
restore the preview-restart budget and save the confirmed format per receiver
VID/PID. A saved format is reused only if it is still advertised and permitted
by the selected video mode. Temporary USB-close events keep the format;
negotiation rejection, persistent frame failure and repeated open errors try a
fallback. Diagnostics show preview restarts, USB reopens and last-frame age.

Android controls its USB default and permission dialogs. Select TeleRC and
Always / Use by default on attachment if offered, and grant Camera while using
the app. An ordinary app cannot silently set that system default or guarantee
approval never returns after physical detach, grant revocation, app data reset
or OEM policy. USB activity registration remains video-class-only; serial/LoRa
USB devices are not claimed. No permission is stored as if it were a grant.

## Install and bench acceptance

1. Install TeleRC-v0.8.91.apk over0.8.90 without uninstalling. Same signer and
   increasing code preserve settings and balance/game data. No firmware flash.
2. With rover wheels raised, connect the UVC receiver by a data-capable OTG
   cable. Select TeleRC and Always if offered. Close competing USB camera apps.
   If another app opens, clear its USB defaults in Android Settings and replug.
3. Start FPV once. Keep Auto reconnect On. Repeated Connect, camera/map switches
   and three background/resume cycles should not request USB access again
   while Android retains that grant. Verify no automatic enable or ARM.
4. Run video30 minutes. Open FPV video options > USB diagnostics and record
   VID/PID, mode, frame age and both recovery counters. Confirm a stable format
   is saved after sustained frames and reused on return to Controls.
5. Replug the receiver10 times. With Android's default grant, check automatic
   picture return without app-generated prompts. If the OS withholds access,
   approve it once; the app must not display a repeated permission loop.
6. Briefly interrupt camera/RF input separately from USB power. Verify recovery
   when input returns. USB frames alone do not prove clean RF video. Check
   camera/channel/PAL/NTSC and regulated receiver power separately. If using a
   powered OTG hub, confirm the phone supports its data/power arrangement.
7. Turn Auto reconnect Off; resume must stay disconnected. A new Android
   attachment/default launch is a fresh request and can restore the connection.
   Unplug during approval: a stale callback must not open a replacement device.
   Two identical receivers remain ambiguous and must not be selected randomly.
8. Verify STOP, link loss and app background still disable vehicle control.
   FPV recovery never enables or arms the rover.

Rollback: Android rejects lower version codes. Prefer a forward-code rebuild
of0.8.90 with the retained signer; do not uninstall and lose app-local data.

## Gates and limitations

| Gate | Evidence |
| --- | --- |
| A source/build | Required: JVM, debug/release lint/build and test APK; BUILD_INFO/run gives final result |
| B behavior | Regression tests for preview restart/escalation, warmup, sustained recovery, saved-format validation and native attach/lifecycle policy |
| C regression | Core sync, host/default sanitizers, MiniRover profiles and complete native UI suite |
| D integration | NativeAPI35 no-device FPV/menu/lifecycle and full three-theme/large-text app regression; compiled USB registration, signer, version, CRC and alignment checks |
| E physical HIL | BLOCKED: actual phone, UVC receiver/OTG/power and rover unavailable |
| F delivery | Signed candidate, exact source/evidence, BUILD_INFO and SHA256SUMS; no public/stable promotion by this workflow |

Software tests cannot establish physical USB/RF reliability or OEM chooser
behavior. Acceptance above remains NOT TESTED. Candidate publication is not
stable qualification. Android primary reference:
https://developer.android.com/develop/connectivity/usb/host . Pinned helper
source reviewed: shiyinghan/UVCAndroid1.0.13,
6018c7f20238832929cf61427427bb7828056260, CameraHelper/CameraInternal/UVCCamera.

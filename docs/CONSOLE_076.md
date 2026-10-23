# TeleRC v0.8.76 — expanded center panes

Controls and Test now extend the center pane through the former bottom footer. Test's version label and Controls' connection-status footer are removed. Enable/Stop and Start/Pause/Resume sit below the left joystick; F1–F3 and Clear times sit below the right. The joystick areas retain their size and caption alignment.

Install the signed APK over the existing TeleRC app without uninstalling to retain settings and maze progress. No firmware reflash is needed. This is a test release; phone/receiver/rover validation remains pending. The current stable release channel is unchanged.

## Validation and provenance

The app, firmware, Gradle/build configuration, scripts and test sources match qualified commit cfa044896a2f8ca40dffb78366d635a38b3722b5 exactly. Qualification run: https://github.com/Denberg28/TeleRC/actions/runs/39083855942

| Gate | Result |
| --- | --- |
| A source/build | PASS — JVM tests, debug/release lint and builds, instrumentation APK |
| B behavior | PASS — emulator: full-height pane, bottom alignment, no overlaps or clipped actions; Light/Sepia/Dark and normal/1.3 text |
| C regression | PASS — bridge sync/host/default ASan/UBSan plus native theme, photo, maze, Market and FPV menu checks |
| D software integration | PASS — unchanged protocols; fresh APK signing, identity/code94, permissions, USB registration, CRC and alignment verified during packaging |
| E physical HIL | BLOCKED — no attached phone, receiver or rover |
| F delivery | The packaging workflow verifies all checksums and archive CRCs before publishing APK, source/evidence archives, BUILD_INFO and SHA256SUMS as release assets |

The original qualification's overall failure was exclusively Actions artifact storage quota. Its original run/job metadata, full log, native result and screenshots are included in the evidence ZIP. Source equivalence is checked before reusing that evidence. Packaging performs a fresh signed APK build and independent signing continuity against the available v0.8.52 APK; the expected v0.8.75 release download was unavailable. No claim that native tests ran again during packaging.

## Phone checks and rollback

Confirm saved settings and wallet after installing, then verify both center panes reach the bottom button baseline. Repeat all themes and larger text with system bars/cutout visible. Exercise Levels, Restart and Clear times.

With wheels raised: explicit Connect → Enable → neutral → ARM; check CH1 steer/CH2 drive, release/STOP, background and link loss/recovery. Reconnection must not automatically enable or arm.

Android blocks versionCode downgrades. Restoring the old layout while retaining data requires a forward-version rebuild with the same signer; avoid uninstalling unless data loss is acceptable. Source, signing and package identifiers are recorded in BUILD_INFO. No main merge or stable promotion is part of this release.

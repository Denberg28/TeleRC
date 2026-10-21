# TeleRC Go 1.0.1 controls-only release

Signed candidate: [go-v1.0.1](https://github.com/Denberg28/TeleRC/releases/tag/go-v1.0.1).
APK/source commit: `e64b34e3e06c77cda95192d5c1d9e29b2fad9001`.
Product change: one landscape Controls screen, supplied ship background behind
transparent joystick panels, navy power launcher, Phone connect and compact
stopped settings. Wi-Fi is the fresh-install default; existing profiles persist.
Phone control/ARM remain explicit. Use ELRS stops/disarms/releases phone control;
the centered handset must cycle CH5 LOW then HIGH. No GoLink or firmware change.

APK SHA256: `4fd03ce9ce5266c9a9ee795d3c5193ccf1d8a3926f080fe8feaa0446c89618f3`.
Package `io.github.denberg28.telerc.go`, version 1.0.1/code 2, retained signer.
Existing Go 1.0.0 firmware is compatible; no app-only reflash is required.

## Evidence

Full release workflow [39605441833](https://github.com/Denberg28/TeleRC/actions/runs/39605441833) passed.
A separate capture-only workflow [39607405776](https://github.com/Denberg28/TeleRC/actions/runs/39607405776)
passed with a Git diff guard proving Go main sources, resources, build configuration
and firmware unchanged from the published source. Capture source:
`6015b55c2cadc0baa36ca0004a322a85d9cc587e`. It waits for the rendered navy surface
in all screenshot quadrants, avoiding Android starting/rotation-window captures.
Corrected images are attached as `TeleRC-Go-v1.0.1-visual-evidence.zip`.

| Gate | Status | Evidence |
| --- | --- | --- |
| A source/build | PASS, reviewed/build verified | Both-app Android regressions; Go debug/release lint and signed build |
| B behavior | PASS, simulator tested | Controls-only normal/large text; live UDP ARM/motion, heartbeat recovery, reboot, settings stop, passive observer, ELRS takeover/release and ArduRover HOLD; repeated in capture run |
| C regression | PASS, host/simulator tested | Eight Go JVM tests; full-app JVM suite; shared-core synchronization; bridge, mini and Go host/default sanitizer checks |
| D integration | PASS software; physical BLOCKED | Go Kotlin frames through actual four-wheel sketch parser; MX1508/BTS7960 x Wi-Fi/ELRS, four actual ESP32-S3 core 3.3.2 compilations |
| E hardware | BLOCKED | No physical phone installation, RF, driver, motor or field timing measurement |
| F delivery | PASS software/package verified | APK signer/identity/code, alignment/ZIP checks, source/firmware/evidence packages, published hashes; local APK matches published SHA256 |

An earlier emulator attempt timed out in the final FC sequence. Added diagnostics
and two subsequent complete emulator runs passed. No underlying hardware or RF
root cause is claimed; retain candidate status until the physical checks pass.
Earlier launch-transition screenshots are superseded by the visual-evidence ZIP.

## Next physical check and rollback

Install over Go 1.0.0 and verify retained settings and the navy icon. Restrain the
wheels and use the procedures in TESTING.md: connect Wi-Fi, Phone control,
neutral ARM, low-output direction checks, STOP, background/loss/recovery, then
ELRS takeover/release, CH5 rearm sequence and CH6 STOP. Confirm recovery never
automatically drives; measure stop times on the actual phone/rover configuration.
Select ELRS receiver in Settings > Connection details for the matched RF image.
For ArduRover use its Wi-Fi profile and verify MANUAL/HOLD and independent failsafes.

Keep Go 1.0.0 APK and configuration backups. Android downgrade normally needs
uninstalling code 2 and loses Go data. Full TeleRC is a separate package. No
firmware or radio provisioning rollback is needed for this app-only update.

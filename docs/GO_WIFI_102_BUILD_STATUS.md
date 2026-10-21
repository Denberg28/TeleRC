# TeleRC Go 1.0.2 stable — 22 October 2026

Published stable: https://github.com/Denberg28/TeleRC/releases/tag/go-v1.0.2

Product/package source: `482f0a79d9b022fd24797584de2088a9de6449e1`.
Successful qualification: https://github.com/Denberg28/TeleRC/actions/runs/39650910673
This final result supersedes earlier pending/blocked software status for 1.0.2.

One Wi-Fi control screen contains Phone connect, Settings and a combined
ARM/DISARM action. Settings footer, central Phone control and app STOP are
removed. The supplied background, transparent joystick panels and inset navy
power icon are retained. SA/CH7 selects optional receiver authority. Revoking
phone control preserves the actual armed state reported by receiver telemetry.

| Gate | Final observed result |
| --- | --- |
| A build | PASS: both-app Android regression/build, Go debug/release lint and signed release APK. |
| B behavior | PASS: 12 Go JVM tests, zero failures/errors/skips; merged ARM cancellation/expiry and observed ARM state regression. |
| C regression | PASS: shared-core sync, bridge/mini/Go host suites, default cloud ASan/UBSan and driver/profile checks. |
| D software integration | PASS: Kotlin packets to actual firmware parser, four actual ESP32-S3 core 3.3.2 builds, native normal/large-text UI and independent live UDP fixture. |
| E physical | BLOCKED / NOT TESTED: physical phone upgrade, rover/RF/driver/power and field timing. Execute TELERC_GO.md restrained-wheel acceptance. |
| F package | PASS: retained signer, distinct Go identity, version code 3, alignment/CRC, all release checksums, source/firmware/evidence archives and public non-prerelease metadata. |

Native coverage includes busy UDP port recovery, ARM cancellation, normal
ARM/DISARM, telemetry loss/recovery, reboot, repeated popup transitions,
standalone SA takeover/return, passive armed radio observation, automatic
ArduRover detection and FC HOLD. Screenshots were visually inspected: both text
scales keep main controls and labels readable; enlarged settings uses its bounded
scrolling body with Done visible. The inset power launcher was inspected.

APK SHA-256:
`991043838e7cb31aa90ed02040333c4a944f25acb3aef49ab4a85ebea56c867f`.
Four-wheel firmware archive SHA-256:
`4311197618e14083ba9f49eec7ae7c37b600c98240ff77737ea88c6e7d1e328f`.

Install the signed APK over Go 1.0.1. Standalone rovers require the paired 1.0.2
firmware; retain motor/driver/pin/inversion settings. ArduRover retains its FC
firmware and needs explicit SA-to-CH7/RC7_OPTION=46 setup. Full TeleRC remains a
separate installation and its Latest release is unchanged. Operation, wiring,
rollback and the next physical checks are in TELERC_GO.md and GO_WIRING.md.

Stable is the owner's requested distribution channel, not physical or field
qualification. Release binaries and tag remain pinned to the product source
above; this documentation-only result does not change those binaries.

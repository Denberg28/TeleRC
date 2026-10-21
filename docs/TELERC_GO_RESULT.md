# TeleRC Go 1.0.0 — release result

Stable distribution: https://github.com/Denberg28/TeleRC/releases/tag/go-v1.0.0
Built product source: `941bda900cf35e49c5d604fdfe79b8bf8c741438`.
Successful qualification: https://github.com/Denberg28/TeleRC/actions/runs/39518099111
APK: 906,504 bytes; `io.github.denberg28.telerc.go`, version1.0.0/code1.

| Gate | Result and evidence |
| --- | --- |
| A — source/build | PASS — both apps build/regression; Go debug/release lint and signed package; four actual ESP32-S3 core3.3.2 builds. |
| B — behavior | PASS — eight Go JVM tests, zero failures/errors/skips; app-generated sparse CH1/CH2, normal ARM/disarm and HOLD frames through the actual sketch decoder. |
| C — regression | PASS — byte-identical shared Android source, motor-core sync, legacy/mini/profile/Go host tests and default ASan/UBSan. Full app product sources remain unchanged. |
| D — software integration | PASS — API35 native tabs, theme/text geometry, live UDP neutral dwell/ARM/movement, telemetry loss/recovery, reboot, passive ELRS observer, FC HOLD and offline Test isolation. Corrected rendered visual captures PASS; Setup/Controls/Test reviewed in Light/Sepia/Dark with normal/1.3 text and visible fixed actions. |
| E — physical HIL | BLOCKED — no physical phone, RF, board, driver, wheel direction, current/temperature or measured stop time. Run the raised-wheel acceptance before ground operation. |
| F — delivery | PASS — retained signer, distinct package, removed camera/location/installer/audio permissions and native SDKs, alignment, ZIP CRC and SHA256 checks. APK, source, four-profile firmware and evidence published. Stable metadata is the owner's explicit distribution choice; physical reliability is not certified. |

APK SHA256:
`76f86824a2d6061e2321499d44cacff6e52c5f3f15e0960a4f8954a8c5f7ef21`

Firmware ZIP SHA256:
`0f6e0ae32653939413e726664981efc3f1aeeedc4844ed4ccaadb73d928a0514`

Downloaded qualification artifact SHA256:
`b7f1055b78e0514c5a0d87453aee0e8697dbdc2fa95f830da01bef47311faa98`

All release assets matched their published digests; inner SHA256SUMS and all ZIP
CRCs passed. The firmware archive contains application, bootloader, partitions
and merged images for MX1508/BTS7960 × Wi-Fi/ELRS. Individual binary hashes are
recorded in [GO_FIRMWARE_SHA256SUMS.txt](GO_FIRMWARE_SHA256SUMS.txt).
The full-app Latest tag remains `v0.8.84`; Go does not enter the full-app updater.

## Visual evidence follow-up

The original functional/native assertions passed, but immediate screenshot calls
could capture the preceding tab before Android presented a frame. A test-only
follow-up waits for UI idle and presentation before capture. Product source and
published APK/firmware are unchanged. The rerun passed all functional checks and
captured eighteen presented tab images. Representative Setup, Controls and Test
captures were visually reviewed; Test now shows its maze rather than the prior tab.

Capture test source: `4bc9fb5c156250711399be614e6caffb29d8c614`.
Successful follow-up: https://github.com/Denberg28/TeleRC/actions/runs/39519487836
Corrected asset: `TeleRC-Go-v1.0.0-visual-evidence.zip` on the stable release.
SHA256: `5a0f16940498192aaec547f04f392eb4f174fdda90b3dc3d2c3a0cef3ebdbc76`.
This asset supersedes the initial evidence ZIP for screenshot review. Its published
hash and inner ZIP CRC were verified after download. The initial suite's protocol
and geometry results remain valid.

## Installation, acceptance and rollback

Use [TELERC_GO.md](TELERC_GO.md) and [GO_WIRING.md](GO_WIRING.md). Choose the
actual driver and ELRS mode; install the separate Go APK, flash the matching
image with motor power removed, join the rover AP, Connect, Enable, center and
ARM. Do not substitute a legacy standalone image without the boot nonce.

Next physical check: validate supply/logic levels and four wheel directions with
the chassis supported. Measure neutral/release, STOP, Wi-Fi loss, RF loss, CH6,
BOOT and recovery behavior, plus phone/RF takeover. Recovery must never enable
or arm by itself. Record phone, board, receiver, driver, configuration and times.
Physical operation stays unqualified until those checks are recorded.
Rollback retains full TeleRC0.8.89 and the previous matched firmware; uninstalling
Go preserves full TeleRC settings. Remove motor power before firmware/wiring changes.

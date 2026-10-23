# TeleRC v0.8.64 — latest stable

Release-channel promotion requested by the repository owner on 16 October 2026. Promote the existing signed v0.8.64/code82 APK and mini-rover 1.2.3 assets without rebuilding, replacing files, moving the tag or changing application/firmware code. This explicit owner instruction overrides the repository's default candidate-only promotion restriction for this release. It does not establish hardware validation or alter the qualification policy for future releases.

## Included

- Dark navy replaces the Blue accent, preserving saved selection and the compact Setup/Controls layout.
- Offline Test uses the Controls joysticks around a random solvable maze, with Shuffle, Retry, pause/resume, saved clear times and a best time for the same maze.
- Entering Test disconnects live control and disables auto-connect. Returning to real driving requires Connect, Enable and ARM again.
- Mini-rover 1.2.3 limits MX1508 output to 242/255 (approximately 95% PWM). Set app Drive sensitivity to 100% to reach that ceiling; Drive 95% produces approximately 90% PWM. Flash the sketch separately to apply the firmware limit.

## Evidence and remaining validation

Built source: `cee84f40ad7ebf99bfd2152f7fd0c339ca423639`.

[Successful build and qualification](https://github.com/Denberg28/TeleRC/actions/runs/38844598235): Android unit tests, debug/release lint and signed builds, host/default ASan/UBSan regression, twenty driver/count/API host combinations, six actual ESP32-S3 builds, Android-to-mini parser integration, twelve native Setup/Controls/Test theme/accent combinations, practice movement/pause/retry/shuffle and record loading/persistence, signing continuity, alignment and package checksums.

| Gate | Evidence status |
| --- | --- |
| A — build/source | PASS — build verified |
| B — behavior/UI | PASS — automated and emulator tested |
| C — regression | PASS — host/JVM/default sanitizer checks |
| D — integration | PASS — software parser integration; physical link/driver/phone compatibility NOT TESTED |
| E — physical HIL/installation | BLOCKED — no attached phone/rover or measured bench evidence |
| F — package/delivery | PASS — retained signed build; promotion rechecks hashes, archive integrity, evidence, source/tag and Latest metadata |

Stable is the owner's chosen GitHub release channel. Software qualification does not establish physical stopping distance, current/temperature, actual USB receiver compatibility or field reliability. The original BUILD_INFO, source ZIP, START_HERE, checksums and evidence remain intact and retain their original build-time candidate wording. This promotion record supersedes channel wording only.

## Installation and rollback

Install TeleRC-v0.8.64.apk over the existing same-signer app without uninstalling. Version code 82 retains normal upgrade behavior. Flash mini 1.2.3 only to the matching standalone ESP32-S3 rover; it is not the dedicated F405 motor/gateway sketch. Existing FC/LoRa firmware need not be reflashed for the app theme or practice page.

Use START_HERE.md in the release for wiring, configuration and the complete restrained-wheel acceptance sequence. Next physical check: install on the phone, verify retained settings and offline isolation with raised wheels, then measure neutral/DISARM, command-loss stopping, recovery and PWM outputs. Hardware results remain pending until recorded.

Rollback retains prior release assets. Android normally rejects a version-code downgrade; use a future same-signer higher-code restoration to preserve app data. Mini 1.2.2 can be reflashed with motor power isolated, but restores its previous full-duty ceiling. The previous Latest stable release was v0.8.50 when inspected. A channel rollback can mark v0.8.64 prerelease again and restore the audit-recorded previous Latest stable release; retain all tags and artifact files.

Adds **Balance** immediately before **Guide** in the upper-right Setup header. Calibration, saved-calibration status, short Left/Right/Both motor tests, direction verification and Stop/Disarm are now available in TeleRC.

**Paired update:** install `TeleRC-v0.8.87.apk` and upload the sketch in `TT-Balancer-ESP32S3-TeleRC-v0.3.0.zip`. Read `START_HERE.md` first. Keep app data and avoid erasing ESP32 flash to retain saved calibration. No other rover firmware needs changing.

Software checks passed: signed release with retained certificate, 159 JVM tests, debug/release lint, native API35 UI regression and actual ESP32-S3 compilation. [Verified build and test run](https://github.com/Denberg28/TeleRC/actions/runs/39419210517). Source: `359d10ec0facd6501119f13bd18ed8ab82776b44`.

**Prerelease for restrained bench testing. Physical motor motion, direction, Wi-Fi operation and balancing/PID tuning have not been verified.** Suspend the wheels for motor tests and confirm directions only after observing them. Motor pulses remain 55/255 for 150ms; Setup never arms. Use Controls to enable and arm after the pre-test passes.

Assets include the signed APK, matching firmware source, full app source, setup guide, build result, test evidence and SHA256 checksums. Roll back by restoring your previously working paired APK/firmware; Android downgrade may require uninstalling and losing app data, so retain a settings backup. Stable update channel is unchanged.

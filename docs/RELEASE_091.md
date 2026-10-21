# TeleRC v0.8.91 — USB FPV reliability

Install TeleRC-v0.8.91.apk over0.8.90 without uninstalling. The APK retains the
existing signing identity, application ID and app data; versionCode is109.
This is an Android-only update. Keep balancer firmware0.3.1; no reflash.

- Reuse actual Android USB approval and restore the remembered UVC receiver.
  A fresh Android attach/default launch can reconnect after manual disconnect.
- Negotiate an advertised video format while retaining the USB session.
- Restart a stalled preview before reopening USB, with bounded retry delays.
- Remember a format after sustained frames, and reuse it only while advertised
  and compatible with the selected mode.
- Preserve the two-second stale-video vehicle-control cutoff. Recovery never
  enables or arms the rover. Diagnostics show frame age and recovery counters.

On receiver attachment, choose TeleRC and Always / Use by default if Android
offers it, then grant Camera while using the app. Android owns USB defaults and
grants; physical replug, permission reset or OEM policy can require approval
again. TeleRC cannot silently force that system choice.

The exact signed build from source
`d876ac1c6b4d55b77a2c1be21a50b31be6aea8b9` is retained from qualified run
[39545978020](https://github.com/Denberg28/TeleRC/actions/runs/39545978020).
170 JVM tests, debug/release build and lint, host/default sanitizers, complete
native API35 UI regression, USB registration, signer continuity, increasing
version code, archive CRC and alignment checks passed. Publication validates
that retained artifact's GitHub digest and all downloadable checksums. No
runtime, test or firmware source is changed by the publication workflow.

This is a prerelease. Actual phone/UVC/OTG power, RF quality, reconnect latency
and rover hardware acceptance remain NOT TESTED/BLOCKED. Software qualification
does not establish physical receiver reliability. The current stable channel
is unchanged. See START_HERE.md for the restrained-wheel acceptance procedure,
setup, known limits and rollback. SHA256SUMS.txt covers every companion asset.

Rollback requires a forward-version rebuild of0.8.90 with the retained signer;
Android blocks ordinary version-code downgrades. Avoid uninstalling and losing
local settings/calibration/game data.

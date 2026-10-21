# Go 1.0.3 acceptance and rollback

User scope: fit popup content without scrollbars; correct padding/spacing;
lower-center status and ARM/DISARM; clickable version at the upper LEFT
(corrected from an earlier upper-right request), including check/download/install.

Baseline Go 1.0.2, distinct package io.github.denberg28.telerc.go, code3. New APK
uses code4 and the same release signer; firmware/protocol/pins are unchanged.
Full TeleRC product files are unchanged. App-specific sheets avoid modifying the
shared full-app popup policy. Connection Save and Cancel share a horizontal
footer; fixed 48dp targets retain enough room at 1.3 font scale and short screens.

GoUpdater uses bounded GitHub Go-tag metadata, exact stable APK name/origin and
SHA-256 digest. DownloadManager completion is only a hint: its successful record,
file size/hash, actual package archive's package/version code/version name and
installed signer are verified before permission/installer handoff. No download
or installation occurs simply by checking. No credentials are embedded. Opening
sheets revokes phone control; approving Download disconnects it; return never
restores authority or arms. Source-specific permission and Android installation
confirmation remain required. See TELERC_GO.md for operation and hardware limits.

Cloud A/B/C/D/F evidence is pending. Native checks use an actual greater-code
same-debug-signer Go APK fixture, a current APK, a full TeleRC APK, wrong digest
and version, plus deterministic stable/prerelease metadata. Geometry checks assert
fully visible non-overlapping form controls and no scroll containers. Captures
cover settings, connection, sensitivity, update sheet and controls at normal and
large fonts, including a shorter landscape viewport. Prior UDP faults remain.

Next physical check: install over Go 1.0.2 on the actual phone; inspect all sheets,
edit and save each field, vary sensitivities, check the version label while
connected to internet, allow this app as an install source only when ready, and
confirm the Android installer upgrades only Go. Then run restrained-wheel
ARM/DISARM, popup/background/reconnect and SA takeover/return acceptance.
Physical download/permission/installer and HIL gates remain NOT TESTED/BLOCKED.

Rollback: retain Go 1.0.2 APK and existing firmware; Android downgrade normally
requires uninstall and loses app settings. Prefer a forward-code corrective APK
when retaining settings matters. The full TeleRC Latest release is not replaced.


## Final release result — 22 October 2026

Stable: https://github.com/Denberg28/TeleRC/releases/tag/go-v1.0.3
Product/package source: e209341c47273e8fccce90dbd8d8daafdcf7a390.
Successful run: https://github.com/Denberg28/TeleRC/actions/runs/39660238824

A/B/C/D/F PASS: both-app Android regression; 17 Go JVM tests with zero failures,
errors or skips; Go debug/release lint/build; retained signer and code4; all host
and default cloud sanitizer gates; actual four-profile ESP32-S3 compilation;
Kotlin/firmware parser integration; native popup-fit/non-overlap/no-scroll tests,
normal and large text including 640x360dp landscape; lower-center ARM/status and
clickable upper-left version; connection Save/Cancel; actual newer same-package
APK verification and wrong hash/version/current/full-app rejection; stable Go
metadata selection; all prior live UDP, reboot, port conflict, RF observer,
SA takeover/return and FC HOLD checks. Final screenshots were visually reviewed.
All downloaded artifact/release SHA-256 values and ZIP integrity were verified.

APK SHA-256:
56c122fdfadb564c92118e04c802949d9fc59cf2f98b22b0fd3594b550a6daa0.
The final successful native run supersedes the earlier fixture-transfer issue
and metadata-test timeout. Selection and archive rejection assertions remain
required in the passing final suite. Stable metadata is public, draft=false and
prerelease=false; full TeleRC Latest remains unchanged.

E remains NOT TESTED/BLOCKED: physical phone installation/permission/installer
acceptance and rover/RF/power/field validation. No silent installation is claimed.
Retain existing Go 1.0.2 firmware; this update does not require reflashing.
Next: install this APK on the actual phone and follow the acceptance above.

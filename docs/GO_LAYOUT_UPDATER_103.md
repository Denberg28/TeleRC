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

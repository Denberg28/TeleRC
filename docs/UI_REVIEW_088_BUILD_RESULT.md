# TeleRC 0.8.88 final software qualification

Published prerelease: https://github.com/Denberg28/TeleRC/releases/tag/v0.8.88
Verified source: `181056fe0ef06e9717a3634089753a12414ea112`.
Verified workflow: https://github.com/Denberg28/TeleRC/actions/runs/39429830406
Android0.8.88/code106; retained signing identity. APK SHA256:
`1db38a24c7dba9765060e20ec722884b2f1f8cb7874237609ac5fa6010fbd4d3`.

| Gate | Final status | Evidence / practical limit |
| --- | --- | --- |
| A — source/build | PASS — build verified | Debug/release build and lint; no affected ESP32 target; firmware unchanged from balancer0.3.0 |
| B — behavior | PASS — simulator tested | 162 JVM tests, zero failures/errors/skips; consent session/boot/readiness, MAVLink source/flags/length/truncation/recipient regressions |
| C — regression | PASS — software verified | Core sync, packaging, bridge/mini/default leak-enabled ASan/UBSan,20 profiles, balancer assertions and20k malformed packets |
| D — integration | PASS — native software tested | API35 complete UI suite: dropdown-only Balance, modal/menu control guard, update broadcast guard, centered bounded popups, normal+1.3 text/three themes, Controls/Waypoint/FPV/game/persistence regression |
| E — HIL | BLOCKED — hardware not tested | No actual phone/rover/USB/radio/motor/stop-latency/balancing measurement; execute UI_SAFETY_REVIEW_088.md acceptance |
| F — delivery | PASS — prerelease packaging | Retained signer, increasing code106, ZIP alignment/CRCs, source/evidence/guide/review and SHA256; all downloaded release checksums verified |

Native popup centering initially failed because a nonzero premeasured decor size
could be sampled before WindowManager positioning. The harness now waits for an
attached, focused, laid-out frame; exact bounds/centering and350ms stability
assertions remain. The complete rerun passed. Final message/menu screenshots
were visually inspected; header Balance shortcut is absent, the five-entry menu
aligns with its anchor, and fixed popup actions remain visible.

App-only upgrade: install signed APK over0.8.87 retaining data; retain working
firmware. Balancer setup still requires0.3.0, already supplied with0.8.87.
No main merge or stable promotion. Latest stable remains0.8.84.

This record supersedes build-pending software rows in the initial review.
Remaining security/feature/resource gaps are documented in UI_SAFETY_REVIEW_088.md;
software success is not physical reliability or a security certification.
Next concrete check: phone upgrade, all-theme/large-text layouts, then the
restrained-wheel reconnect/consent/STOP/failsafe acceptance in that review.
Rollback should preserve walletv3/preferences through a forward-code rollback;
uninstalling simply to downgrade loses user data.

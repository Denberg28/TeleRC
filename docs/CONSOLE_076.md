# Expanded console — 0.8.76 test release

Baseline: v0.8.75/code93, c9d307919d59423f3e76d845174f5a16cbe26a6f. Candidate code94. App-only: no firmware or protocol changes, no reflash.

Controls and Test use full-height center panes. Bottom controls belong to the side columns: Enable/Stop left and F1–F3 right; Start/Pause/Resume left and Clear times right. Test version and Controls status footer are removed. Setup retains connection diagnostics and version. Center gains one scaled 48 dp action row plus its 8 dp gap while side joystick bounds stay the same. Side button widths follow their columns; F1–F3 share available width.

Acceptance: center top/bottom match its row; bottom actions align with its bottom without intersection; all actions remain fully visible; no Controls DISCONNECTED or Test version label; themes, menus, maze persistence, pause, clear times and safety gating retained. Native geometry assertions run across Light/Sepia/Dark at normal and 1.3 font scale alongside existing tests.

| Gate | Status before cloud run | Evidence / next check |
| --- | --- | --- |
| A source/build | Reviewed; local Android BLOCKED | Cloud JVM, debug/release lint/build, instrumentation APK required; local SDK unavailable |
| B behavior | Reviewed; native geometry pending | Full-height center, separate side actions and hidden footer labels |
| C regression | Pending cloud | Existing bridge sync/host/ASan/UBSan, JVM and native theme/maze suite |
| D integration | App-only reviewed; device NOT TESTED | No protocol, firmware, watchdog or authority changes |
| E HIL | BLOCKED | No phone/rover attached; use raised-wheel steps below |
| F delivery | Pending cloud | Retained signer, code94, source/evidence ZIPs and SHA256SUMS; Actions artifact only |

Inspect the completed Expanded console qualification run for final software results. No release publication or stable promotion is performed.

Phone acceptance: install over 0.8.75, confirm settings/wallet retained; verify Controls and Test center reaches the bottom action baseline, both joystick captions remain aligned and all bottom buttons fit in landscape with system bars/cutout. Repeat all themes and larger text; exercise Levels, Restart and Clear times. In Controls use raised wheels: explicit Connect → Enable → neutral → ARM; check CH1/CH2 and release, STOP, background and link-loss/recovery. Reconnection must not automatically enable or arm. No measured physical result is claimed.

Rollback: retain v0.8.75 and its data. Android blocks a versionCode downgrade; restoring the prior layout should use a forward-version rebuild of v0.8.75, retaining signing identity. Avoid uninstalling unless data loss is acceptable. Next concrete check: review cloud qualification and phone geometry/raised-wheel behavior.

## Authorized release packaging

The owner requested a release build after the qualification above. Release packaging verifies an exact git diff match for app, firmware, build configuration, scripts and tests against qualified source cfa044896a2f8ca40dffb78366d635a38b3722b5. It recovers the original successful job metadata/log/native results/screenshots, rebuilds the signed release APK, and repeats signing continuity against v0.8.75 plus identity/code94/permissions/USB/CRC/alignment checks. Native/JVM/lint/sanitizer evidence is reused only after that exact source match; it is not claimed as rerun in packaging.

The release uses GitHub Releases assets rather than Actions artifact storage, retaining APK, source, evidence ZIPs, BUILD_INFO and SHA256SUMS. v0.8.76 is a prerelease; v0.8.75 remains the latest stable. The previous quota blocker is resolved for delivery only when the release-packaging workflow succeeds. No firmware update or main merge. Phone/receiver/rover HIL remains BLOCKED. Install over 0.8.75 without uninstalling, retain settings, and perform the phone acceptance steps above.

# Expanded console — 0.8.76 review build

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

## Final software result

Built source: `cfa044896a2f8ca40dffb78366d635a38b3722b5`. [Qualification run 39083855942](https://github.com/Denberg28/TeleRC/actions/runs/39083855942) completed all verification and packaging steps successfully; its overall FAILURE is exclusively the two Actions artifact-upload steps, which reported account storage quota exhausted. Do not describe the overall workflow as successful or provide an APK download that was not retained.

| Gate | Final evidence |
| --- | --- |
| A | PASS — build verified: JVM tests, debug/release lint/build and instrumentation APK |
| B | PASS — simulator tested: full-height console, no side-action intersection, visible bottom actions; three themes, normal and 1.3 font |
| C | PASS — existing bridge sync/host/default sanitizers, JVM and native theme/maze/photo/FPV-menu regressions |
| D | PASS — software reviewed/build verified: retained signer vs 0.8.75, app/code94/USB registration/permissions/alignment; physical NOT TESTED |
| E | BLOCKED — actual phone, receiver and rover unavailable |
| F | PASS — signed packaging, source/evidence ZIP generation and checksum checks; BLOCKED — artifact retention/download due to account Actions storage quota |

Native Controls and Test frames were recovered from the job log and visually reviewed: center pane extends to the side action baseline, button labels remain visible, and removed footers are absent. No firmware reflash. No main merge, published release, stable promotion, deletion of old artifacts, or quota/billing changes. Next concrete check: restore artifact delivery when requested, then run the phone acceptance sequence above. This documentation-only follow-up does not alter the tested app source.

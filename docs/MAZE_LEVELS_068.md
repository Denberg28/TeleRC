# TeleRC 0.8.68 — fixed maze levels

Baseline: qualified 0.8.67/code85, source `31d57a22c93fe862ebb657e6b7246e485ebcbe2e`. Candidate: 0.8.68/code86. Android app only; retained FPV recovery/photo backgrounds, app identity, signing identity, CH1/CH2 mapping and all firmware/protocols. No firmware reflash. Stable 0.8.64 remains retained; no stable promotion.

## Use and acceptance

Open Test from the page dropdown. The header is only `Lv 1 · 00:00.00`; the maze ID, best-time row and heading text have been removed. Taller courses use the recovered vertical space with the same thin-wall collision geometry and oriented rover. Twenty fixed, repeatable levels replace Shuffle. Levels 1–5 use 15×9 cells, 6–10 use 19×11, 11–15 use 23×13, and 16–20 use 27×15. These tiers increase course size, not a measured monotonic driving difficulty. START stays lower left. Breadth-first distance selects END at the farthest reachable passage from START; each course is a connected tree, so this is the longest route from START, rather than a fixed corner or the graph diameter between arbitrary endpoints.

The footer shows `OFFLINE · Bumps 0/3`. A blocked movement counts one bump. Holding, releasing/re-pushing, pivoting or pausing at the same wall does not repeatedly count it. Move away at least 0.15 world units and regain 0.10 world units of extra wall clearance before another impact can count. The third bump freezes movement and active time and shows Restart level. Confirming resets that same course, timer, bumps and stick inputs to READY; press Start practice to move again. Restart level is also available during any run. Pausing and navigating away do not reset the bump budget.

Clear a level to save its time and unlock the next. Next level opens the newly unlocked course at READY. Levels lists unlocked courses and their best retained clear times; hold Next level to replay an earlier level. The last level returns to Levels. Current level and completed frontier persist across app restarts; an interrupted run restarts READY with zero timer/bumps. Earlier random clear times remain labeled Legacy and do not become level records or unlock levels. Up to 50 latest clears remain saved, so a level's older best can leave the retained history. No new live control, USB, location or network dependency is added to the maze. Entering Test still disconnects the live rover and disables auto-connect; returning to Controls requires explicit Connect.

## Gates A–F

| Gate | Required evidence and status |
| --- | --- |
| A — source/build | Reviewed: fixed catalog, farthest-goal BFS, contact latch, terminal failure, progress clamps, versioned records and native UI callbacks. Cloud JVM tests, debug/release lint/build and instrumentation build required. Local Android SDK/Gradle unavailable. |
| B — behavior | JVM coverage: all 20 deterministic connected/acyclic/closed courses and longest-route goals; actual steer/drive solver across all four tiers; three separate impacts versus held/paused contact, frozen failure and reset; progression/saved-state sanitization and legacy record preservation. API 35 native integration: larger maze area with photo theme, three-bump prompt/reset, real animation-frame clear callback, record/unlock/next-level, activity restart and locked-level exclusion. |
| C — regression | Motor-core sync and host regression required; local ASan/UBSan with leak inspection restricted, cloud full default sanitizers required. Native photo import/rollback/three-theme renders/removal and FPV options retained. |
| D — software integration | Original signer, app ID, code86 progression, compiled USB attach registration, permissions, APK CRC/16K alignment and SHA256 checks required. Firmware unchanged: no affected ESP32 compile target. |
| E — phone/rover | BLOCKED: no physical phone/receiver/rover attached. Simulator tests do not establish physical reliability, gallery picker behavior or Skydroid compatibility. Run phone acceptance below. |
| F — package | After all preceding automated gates pass: signed test APK, exact source archive, test/lint/native screenshots, BUILD_INFO with source SHA, guide and checksums in an unpublished draft. No publication or stable promotion. |

The final successful workflow and its BUILD_INFO supersede pending software statuses in this source guide. They do not change physical gate E. Source and artifact hashes are recorded by that workflow; no self-referential commit/hash is embedded in source.

## Next phone check and rollback

With wheels restrained and motor cutoff reachable, install the signed update over 0.8.67. Retain photo, theme, FPV mode and legacy clear history. Enter Test while previously connected; confirm disconnect/auto-connect off and no live stick commands. Verify the single Lv/time header and taller course at normal/large font. Push a wall continuously, pause/resume and re-push: still one bump. Retreat and strike twice more: stop on bump three, one restart prompt, frozen time, disabled sticks. Restart: same level, 00:00.00, 0/3, no movement until Start. Clear level 1, press Next level, restart the app and verify level 2 remains selected; replay level 1 through Levels. Verify END at the longest route and compare later tiers for legibility. Return to Controls: explicit connection/enable/arm remains required. Recheck Skydroid format/USB recovery and photo background; they are retained, not newly hardware-qualified.

Rollback: keep the 0.8.67 signed APK and prior source commit above. Android may require uninstalling to downgrade; uninstall loses private settings, progress, routes and photo. Version 0.8.67 cannot read new level clear records; earlier legacy rows remain compatible. Use a forward-version revert for rollback without uninstall/data loss. Next concrete check: phone installation and the three-contact/restart → clear/unlock → restart-persistence sequence above.

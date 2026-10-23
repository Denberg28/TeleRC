# TeleRC 0.8.71 — practice dialogs and version footer

Baseline: qualified 0.8.70/code88 source `69f12c76c8ecfec8e9c947ee83579dfa88aa63aa`. Candidate 0.8.71/code89. Android presentation only: no maze geometry/physics, progression, wallet/prices/spawns, bump rule/dots, FPV/photo, protocol, firmware or signing identity changes. No firmware reflash. No stable promotion.

The Test footer now shows `TeleRC v0.8.71`, derived from BuildConfig so later releases show their own version. The former OFFLINE/bump-count footer is removed. Practice remains offline and disconnects the live link on entry as before; Market continues to identify offline cosmetics. The three-bump limit, persistent red wall marks and restart behavior remain active.

Levels and the three-bump restart popup share a rounded themed surface, 20 dp outer padding, 16 dp content/action gaps, fixed header/footer and a scrollable body. Width and height are capped to the visible window with outer margins, keeping action buttons available on short landscape screens. Actions have a minimum 48 dp touch height. Light/Sepia/Dark colors follow the existing app preference.

Levels uses 68 dp minimum cards with a numbered badge, left-aligned level name/best time and Current/Cleared/Play status. It lists unlocked levels only. Selecting a row starts the same fresh attempt as before; Done/back/outside dismissal only closes the chooser. The current level has a filled background. The restart popup separates elapsed time and the three-bump count into a compact summary, explains the fresh attempt and retained coins, and presents a full-width Restart level action. It remains mandatory after the third bump. The in-maze Restart level button retains its existing immediate-reset behavior.

## Review and gates A–F

| Gate | Required evidence / source status |
| --- | --- |
| A — source/build | Reviewed: shared dialog layout and window bounds, lifecycle dismissal, existing reset/selection callbacks and version-derived footer. Local Android SDK unavailable; cloud JVM/debug/release lint/build and instrumentation build required. |
| B — appearance/behavior | Existing native third-bump/reset and unlock/restart checks exercise real popup callbacks. Added native captures/checks use all 20 unlocked levels at system font scale 1.3 under Light/Sepia/Dark; actions remain fully visible and at least 48 dp, scrolling selects level 20, Restart invokes its callback. Actual Test checks the version footer. Cosmetic layout work adds no redundant JVM unit tests. |
| C — regression | Motor-core sync/host suites/local ASan/UBSan required; local leak inspection restricted. Cloud full default sanitizers required. Native red-dot, Market purchase/coin/wallet, three-theme photo import/removal and FPV menu checks retained. |
| D — software integration | Same app ID/signer, increasing code89, compiled USB registration, existing permissions, APK CRC/16K alignment and SHA256 checks required. No affected ESP32 compile target. |
| E — phone/HIL | BLOCKED: phone/receiver/rover unavailable. Native simulator layout checks do not establish physical installation or phone readability. |
| F — package | After software gates pass: signed test APK, exact source, JVM/lint/native screenshots, guide, BUILD_INFO/source SHA and checksums in an unpublished draft. No publication/stable promotion. |

The final successful workflow and BUILD_INFO supersede pending software results only. No hardware/field claim. Native captures include the real two-level chooser/restart, all three themes at larger font and the version footer.

## Next phone check and rollback

Install over 0.8.70. With rover wheels restrained and cutoff accessible, enter Test and confirm no live link/control output. Verify the bottom version string. Open Levels at normal/larger phone text: padding, current/best/status alignment, row selection, scrolling and Done/back dismissal. Hit three separate walls: frozen time, retained dots and coins, visible summary and Restart button. Confirm Restart resets timer/bumps/dots and requires Start. Recheck Market, selected cosmetics, coin wallet and FPV/photo settings. Explicit Connect/Enable/Arm remains required before live driving. No reflash.

Keep the 0.8.70 APK/source for rollback. No persistence migration is added. Android downgrade may require uninstalling and losing private settings, progress/history/wallet/routes/photo; prefer a forward-version revert. Next concrete check: phone popup padding/scrolling/action visibility under normal and large text in each theme, then restart and footer verification.

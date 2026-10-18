# TeleRC 0.8.84 / code102 — compact popup layouts

Baseline: published 0.8.83/code101, commit `307eb4b7072abd383b2e845070fe93716f864e25`. Android-only test candidate; no firmware reflash or paired upgrade. Install over the existing app to retain settings, routes, records and wallet. Use the APK, source, evidence, BUILD_INFO and SHA256SUMS from the same release.

## Behavior and review

- Setup, Controls, Waypoint and Test share `PopupLayout`: measure actual wrapped content before opening, center within Android's visible frame, keep 16 dp horizontal / 12 dp vertical clearance, cap height at 340 dp, and scroll only the body when needed. This removes the previous forced 340 dp form/list height and character-count message-height estimate.
- Preferred widths are 360 dp for short notices/confirmations, 420 dp for lists/Levels, 440 dp for forms/Market and 480 dp for long guides. Each is capped to the usable screen. Headers/actions retain at least 48 dp touch targets and scale with system text. Titles/actions may wrap. Short coordinate forms, route menus and help messages no longer retain unused long-list space.
- All app sheets now share the tracked dialog lifecycle, theme palette, dim amount, centered gravity and zero window animation. Tab/theme/background transitions cancel stale sheets. Opening a modal retains the existing neutral/HOLD and authority-revocation rules; closing never enables control or resumes motion. Mandatory practice restart remains noncancelable by Back/outside touch.
- Market keeps its measured opening frame while switching categories or buying/equipping, preventing tab-dependent window jumps. Its callbacks, wallet logic, level selection/restart, vehicle physics and persistence remain protected by source comparison and native regressions.
- Anchored navigation menus retain their shared button width, compact rows and tab-switch behavior. System permission, picker and installer windows remain Android-owned.

Source review: navigation engines, codecs, transport/recovery, command/ARM gates, MainActivity, WaypointTab, updater and firmware are byte-identical to the baseline. Only popup presentation and tests/package records change. No new permissions, networking, storage schema or dependency changes.

## Gates A–F

| Gate | Evidence required before publication |
| --- | --- |
| A source/build | Reviewed; local Android build BLOCKED by unavailable Gradle download/SDK. Required cloud JVM, debug/release lint and signed builds. |
| B UI | API35 native three-theme normal/large-text compact list/form/message checks; long-list/guide scrolling, fully visible actions, frame caps/centering, no opening movement, validation retention, cleanup and stable Market category size. Existing all-tab regressions retained. |
| C regression | Motor synchronization, packaging/host/mini/profile tests and default cloud ASan/UBSan. Local sanitizer run disables LSan because ptrace prevents leak inspection; no local leak result claimed. |
| D integration | Native transitions/authority regressions required. Physical phone/FC/USB/network behavior NOT TESTED. |
| E HIL | BLOCKED: no phone, rover, transmitter or FC attached. |
| F delivery | Signed code102 APK; app/signer continuity versus 0.8.83; USB manifest, permissions, ZIP alignment/CRC, source/evidence ZIPs and verified checksums. |

Final successful workflow and BUILD_INFO supply completed software outcomes. This is a test prerelease, with no hardware/field qualification or stable promotion claim.

## Phone and restrained-wheel acceptance

1. Install over 0.8.83 without clearing data. On each tab open all menus, guides, settings, slider forms, F1–F3 assignment, mission notices/coordinates and Test Levels/restart/Market. Repeat Light/Sepia/Dark, default and large text, keyboard shown/hidden and available display modes. Confirm compact short content, readable wrapped text, reachable actions, scrolling long content and no opening jump.
2. Cancel a form/slider: no save. Enter invalid coordinates: error stays in the form. Valid coordinates still require explicit selection/mission confirmation. Repeatedly switch Market categories and purchase/equip: window frame remains fixed; balance/selection persists.
3. While a sheet is open, switch theme/tab and background/restore. Confirm no leaked window, stale callback, automatic start or implicit authority. Back/outside dismiss ordinary dialogs; mandatory practice restart requires Restart level.
4. With wheels restrained and an independent power cutoff, enable phone control, open a Controls modal and verify neutral/control revocation at the FC. Close it: no motion until explicit Enable. On a separately validated Guided route, open help and verify HOLD at the FC; dismissal must not resume navigation. Repeat the prior release's physical RF-loss, STOP/background, stale ARM and reconnect acceptance; independent downstream watchdogs remain required.

Rollback requires a forward-version rebuild of the retained 0.8.83 product preserving wallet v2/settings. Keep the prior APK and FC parameter backup. Next concrete check after software delivery: physical phone layout/keyboard and raised-wheel modal neutral/HOLD procedures above.

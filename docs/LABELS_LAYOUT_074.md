# TeleRC 0.8.74 — sharp captions, aligned tabs and wallpaper opacity

Baseline: qualified 0.8.73/code91 source `ac586f08209e30ea8ba88b52397fa4aa361ad34f`, successful workflow 38971736940. Candidate 0.8.74/code92. Android presentation only; original app ID/signer retained. No firmware reflash, paired upgrade, publication or stable promotion.

## Findings and changes

The 2 dp text-shadow radius added over the clear wallpaper in 0.8.73 creates a visible halo around STEER/DRIVE and channel values. It is removed. Both Controls and Test now use small opaque theme-colored caption strips containing the heading/channel feedback. Text uses the normal sharp renderer with no shadow; background contrast is independent of the wallpaper. The large rectangular joystick areas remain transparent. Matching two-line feedback space keeps both joystick centers level, even when a direction/value wraps. Input mapping, sensitivity/expo, knob geometry, release and safety gates are unchanged.

The platform PopupMenu chooses its own minimum width, which made the tab list wider than its button. A shared anchored popup now uses the button's measured width, aligned start edge, 4 dp gap, themed flat rows and no entrance animation. It scrolls when necessary, marks the active page and supports selection/outside/back dismissal. Page menus are dismissed on render, pause and destroy. Navigation retains the same disable-control/offline Test behavior.

All tabs now use 8 dp outer gaps plus system/cutout insets, consistent 48 dp top/footer bars scaling with larger system text, and 40 dp central secondary action rows. Setup's Background and Opacity controls share one compact row; the panel count and minimized bottom-corner behavior are retained.

Setup → **Opacity · 70%** opens a preview and 0–100% slider for the wallpaper in Controls/Test only. The default is 70% photo opacity over the selected plain theme background to reduce distraction around the central console. 0% shows the plain background; 100% shows the clear photo. Setup always shows its photo at 100%. Apply saves the bounded percentage and updates the setting label without rebuilding Setup or discarding draft inputs. Cancel leaves the saved value unchanged. No photo is modified; imports, private storage and removal are retained. Restoring a previously selected value after activity restart applies on the next console visit.

## A–F gates

| Gate | Reviewed / required evidence |
| --- | --- |
| A — source/build | Reviewed shared caption/menu construction, transparent outer panels, inset/gap/bar consistency, popup lifecycle, bounded opacity and page-specific rendering, Apply/Cancel and retained navigation/reset/purchase callbacks. Cloud JVM tests, debug/release lint/build and instrumentation compile required; local SDK unavailable. |
| B — native UI | Controls/Test in Light/Sepia/Dark at normal and 1.3 font scale: zero text-shadow radius, opaque caption pixels, computed title contrast ≥7 and feedback ≥4.5, ≤2 feedback lines and equal joystick centers. Every tab's menu bounds match its button and sit directly below it; selection exercised and screenshots retained. Opacity preview/Apply/Cancel, 0/100% pixel blends on both consoles, unchanged clear Setup and restart persistence checked. Existing Setup minimization/draft/corner and practice-dialog checks retained. |
| C — regression | Local motor-core sync, host and ASan/UBSan PASS (leak inspection disabled because this container restricts /proc). Cloud full default sanitizers required. Native level/restart/unlock, permanent bump pixels, Market/coins/wallet, photo invalid/oversize rollback/removal and FPV format menu retained. No redundant JVM tests for cosmetic values. |
| D — software integration | Retained ID/signer, increasing code92, compiled USB attach/singleTop/class filter, existing permissions, APK CRC/16K alignment and SHA256 required. No affected firmware target. Physical integration NOT TESTED. |
| E — phone/HIL | BLOCKED: actual phone/Skydroid/rover unavailable. Simulator checks do not establish phone/OEM text rasterization or physical control behavior. |
| F — package | On successful checks: signed APK, exact source, JVM/lint/native evidence, guide, source SHA in BUILD_INFO and checksums in an unpublished draft. Stable 0.8.64 unchanged. |

The final successful workflow and BUILD_INFO supersede pending software results only. No hardware/field claim.

## Install, next phone check and rollback

Install over 0.8.73. In Controls/Test, check sharp STEER/DRIVE and all live channel values over the user's bright/dark wallpaper, including Sepia. Confirm both joystick circles stay aligned. On each tab open the menu: matching button width/edge, compact gap, readable active row, working selection/outside/back dismissal. Check normal/larger text, central scene size and safe gaps around side navigation/cutouts.

In Setup, adjust Opacity: preview and Cancel first, then Apply at 0%, 70% and 100%. Setup stays clear while both consoles use the saved amount. Restart and verify persistence, minimize/restore Setup, check endpoint draft retention and connection/update corners. Recheck saved game/wallet/cosmetics, photo and FPV options.

With wheels restrained and cutoff accessible, verify tab navigation disables live authority, Test disconnects and no appearance change enables/arms or reconnects control. Explicit Connect/Enable/Arm is still required for live driving. Stop on unexpected output; no reflash.

Keep qualified 0.8.73 APK/source for rollback; prefer a forward-version revert. Only `appearance.console_wallpaper_opacity` is new and is ignored by older versions. Android downgrade can require uninstalling and losing private photo/settings/progress/wallet/routes. Next concrete check: actual phone sharpness/menu alignment and opacity acceptance above.

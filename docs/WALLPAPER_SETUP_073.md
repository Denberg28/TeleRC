# TeleRC 0.8.73 — clear wallpaper, transparent sticks and collapsible Setup

Baseline: qualified 0.8.72/code90 source `51c6e2a0a1fa6e7e67557d07237a79383a37d1da`, successful workflow 38964611852. Candidate 0.8.73/code91. Android-only; no protocol/firmware/maze/economy changes or paired upgrade. Original app ID and signer retained. No reflash, publication or stable promotion.

## Findings and changes

The photo drawable applied a theme-color scrim at 0x88/255 (53%) opacity on every tab. This created the washed-out/foggy appearance. It now draws the center-cropped photo directly, retaining the existing bounded, private import and image filtering. Setup cards and central game/camera/map surfaces keep their readable fills. Modal background dimming remains intentional while a dialog is open.

Controls/Test steering and drive rectangles now have no background or border. Round pads, knob/travel, layout padding, CH1/CH2 mapping, sensitivity/expo, neutral return and enable/failsafe gates are retained. Small local text shadows provide edge contrast for the labels over photos; the wallpaper has no global overlay.

The practice popup shell resized its window in an OnShow callback after the default window could appear. That is a source of repositioning; OEM window animation may add sideways motion. Levels, restart and Market now receive their final screen-capped dimensions, centered gravity, zero x/y offsets and disabled window animations before show. The flat layouts, scrolling/touch targets and callbacks are retained. Phone/OEM motion remains a physical acceptance check.

Setup's header now stays accessible above the two settings panels. Tap **−** to minimize both and **+** to restore. In minimized mode the existing Connect button moves to the lower left and Check for updates moves to the lower right. The rest of the page shows the photo. Navigation stays available. The preference persists across activity restart and tab changes. Toggling moves the same views without rebuilding settings, discarding draft inputs, reconnecting or enabling/arming control. Expanded mode retains full connection/preference controls.

The compact connection button shows Connect, Connecting…, Waiting for rover… or Connected according to actual intent/transport/fresh-heartbeat state. A pending/stale heartbeat never displays Connected. When active, tapping it still disconnects; tooltip/accessibility text states that action. Update progress uses transient messages while panels are hidden; the existing verified update workflow remains manual. The version footer remains dynamic.

## A–F gates

| Gate | Reviewed / required evidence |
| --- | --- |
| A — source/build | Reviewed pre-show window configuration, untinted draw, only joystick panel background removal, same-view Setup reparenting, persisted presentation preference and connection captions. Cloud JVM/debug/release lint/build/instrumentation compile required; local Android SDK unavailable. |
| B — simulator appearance/behavior | Native pixel comparisons against the imported image in Light/Sepia/Dark on Setup/Controls/Test, alpha-zero rectangular joystick corners, screenshots. Setup collapse/restore preserves draft endpoint and button identity/actions; footer actions checked at exact bottom corners with ≥48 dp targets; activity/tab persistence checked. Large-font 1.3 checks all themes, all 20 levels, restart/Market actions, centered/stationary window bounds and zero animation, both Market categories/purchases. |
| C — regression | Motor-core sync, host suites and local ASan/UBSan with restricted-container leak inspection disabled; cloud full default sanitizers required. Retained native FPV menu/photo import/invalid/oversize rollback/removal, coin/wallet, level unlock/restart and permanent red-dot checks. |
| D — software integration | Retained app ID/signer, increasing code91, compiled USB attach/singleTop/class filter, existing permissions, APK CRC/16K alignment and SHA256 checks required. No affected ESP32 target. Physical transport NOT TESTED. |
| E — phone/HIL | BLOCKED: user phone/Skydroid/rover unavailable. Emulator results cannot qualify OEM popup animations, hardware connectivity or field use. |
| F — package | On success: signed APK, exact source, native/JVM/lint evidence, BUILD_INFO with source SHA, guide and checksums in an unpublished draft. Stable 0.8.64 unchanged. |

Final successful workflow and BUILD_INFO supersede pending software checks only. No hardware or field-validation claim.

## Install and next phone checks

Install the signed test APK over 0.8.72. Open Levels/Market and trigger the third-bump prompt several times on the actual phone: verify the very first appearance is centered and does not slide or recenter from the right. Recheck at larger text with side navigation/cutout and all themes. Compare wallpaper with the source image outside modal dimming on all three tabs. Confirm joystick rectangles reveal the photo and round pads/labels remain readable.

In Setup, enter an endpoint draft, minimize, restore and verify the draft remains. Minimize again; verify Connect and Check for updates at the bottom corners, + restores panels, and minimized state survives reopening/tab changes. Exercise manual update feedback. With wheels restrained and cutoff accessible, explicitly connect: distinguish Connecting/Waiting for rover/Connected, then disconnect from the compact control. No minimize/restore or heartbeat recovery may enable/arm. Enter Test and confirm disconnect/neutral/no auto-connect; returning to Controls still requires explicit Connect/Enable/Arm. Stop on unexpected output. No reflash.

Keep the qualified 0.8.72 APK/source for rollback; prefer a forward-version revert. The only new stored value is `appearance.setup_minimized`, ignored by older builds. Android downgrade can require uninstalling and losing private settings/photo/progress/wallet/routes. Next concrete check: the actual phone popup first-frame position and clear wallpaper/minimized Setup acceptance above.

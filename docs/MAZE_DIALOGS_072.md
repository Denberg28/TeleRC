# TeleRC 0.8.72 — flat Test popups

Baseline: qualified 0.8.71/code89, source `f4669639273d8c3fe8c1e7abf5961cca4a483c11`, successful workflow 38915361410. Candidate 0.8.72/code90. Android presentation only; no firmware reflash or paired upgrade. Keep the original app ID and signer. Stable 0.8.64 remains unchanged; this is an unpublished review/test build.

## Changes and acceptance

All three Test popups share a flat themed surface, small 16 sp heading, subtle separators, 16 dp horizontal/8 dp vertical outer padding and plain text actions. No numbered tiles, boxed metrics, outlined cards, explanatory paragraphs or full-width filled action bars. Windows are bounded to the visible screen; scrolling rows yield space to fixed headers/actions. Touch targets stay at least 48 dp despite smaller visual treatment.

- **Levels:** 52 dp rows with level, best time and a quiet current/cleared marker. The header shows unlocked/20 and Done. Only unlocked levels are selectable. Height contracts for short lists.
- **Restart:** a 360 × 184 dp capped prompt, short bump/time summary, “Coins kept. Fresh start.” and a right-aligned Restart level action. The third-bump prompt remains mandatory. Reset still clears timer/bumps/dots and requires Start.
- **Market:** Vehicles/Colors tabs, four items per category, 36 dp previews, item name and short Buy/price, Use or Selected action. Balance stays in the header with Done. No catalog description block or repeated item names in buttons. Accessible labels retain item names, price and equipped state. Purchases and tab switching update in place; opening/closing still pauses practice and requires explicit Resume.

Wallet, ownership, progression, clear times, prices, lifetime coin quotas, spawn/pickup, physics, red wall dots, FPV/photo preferences and live-control behavior remain compatible. Test's footer continues to show the version from BuildConfig.

## A–F verification

| Gate | Evidence / required result |
| --- | --- |
| A — source/build | Reviewed shared shell, screen bounds, lifecycle dismissal, category switching and existing purchase/reset/selection callbacks. Cloud JVM tests, debug/release lint/build and instrumentation compilation required; no local Android SDK. |
| B — native appearance/behavior | API35 instrumentation exercises real third-bump/reset, level unlock/reload, Market purchase/insufficient funds/equip, coin collection and wallet reload. All three themes at 1.3 system font scale: level20 selection, fully visible 48 dp actions, restart callback, both Market tabs and purchases, screenshots. No redundant JVM tests for reversible visual changes. |
| C — regression | Motor-core sync and host suites; local ASan/UBSan with leak inspection disabled because this container restricts /proc. Cloud full default ASan/UBSan/leaks required. Existing FPV menu, photo decode/import/invalid/oversize rollback, persistence/removal and bump-pixel checks retained. |
| D — software integration | Original signer/app ID, increasing code90, compiled USB attach registration, permissions, APK CRC and 16K alignment required. Firmware unchanged; no affected ESP32 target. Physical integration NOT TESTED. |
| E — phone/HIL | BLOCKED: phone/Skydroid/rover unavailable. Emulator readability and behavior are simulator tested only; no hardware/field claim. |
| F — delivery | After automated checks: signed APK, exact source commit, native/JVM/lint evidence, BUILD_INFO, guide and SHA256 checksums in an unpublished draft. No stable promotion. |

Successful final workflow and BUILD_INFO supersede pending software results only. Source SHA is embedded in BUILD_INFO and the source ZIP is archived from that same commit.

## Install, next check and rollback

Install the signed APK over 0.8.71. On the phone, compare all three popups at normal and larger text in Light/Sepia/Dark: readable rows/actions, short-list sizing, level scrolling, both Market tabs, price/balance/Selected feedback, Done/back/outside dismissal. Hit three separate walls; confirm frozen attempt/dots and kept coins, then Restart → fresh timer/marks and explicit Start. Confirm selected vehicle/color and wallet after app restart, version footer, background photo and FPV menu.

With wheels restrained and cutoff accessible, enter Test from a live connection and verify disconnect/neutral/no automatic reconnect or arm. Return to Controls and require explicit Connect/Enable/Arm; stop immediately on unexpected output. This is the next physical acceptance check, not completed evidence.

Retain qualified 0.8.71 APK/source for rollback. No persistence migration is introduced. Prefer a forward-version revert; Android downgrade may require uninstalling and losing private settings, progress, wallet, routes and photo. Do not publish as stable until applicable phone/hardware gates are completed.

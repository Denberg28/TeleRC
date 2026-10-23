# TeleRC 0.8.85 / code103 — replay coins and clear mission flow

Baseline: stable v0.8.84/code102, product `f3eba8b620ca49b1030434fae6bb7f8dc94a246c`; promotion audit `cb7206c5dd3d209d778b82c1e1db4af0fcd64510`. Android-only test candidate. Stable/Latest remains 0.8.84 until separately promoted. No firmware reflash or paired upgrade.

## Test tab

A previously completed level has a 25% chance of one extra gold coin on replay. Its chance is saved per level: restart, tab/theme changes and reopening the app reuse that outcome until the level is cleared. Normal unclaimed first-play coins remain available and never duplicate. Bonus placement uses the existing reachable, collision-safe random positions outside START/END.

Collect the bonus and reach END to bank one coin. Pickup alone, failure, leaving/reloading or pausing does not pay. Finishing without collecting it pays nothing; each completed playthrough permits a fresh roll. Only one bonus can be credited per attempt. Original 220 first-play coins and catalog/prices remain unchanged; replay earnings are bounded at 1,000,000 to keep storage/arithmetic finite.

Both the on-screen Restart level and the three-bump restart confirmation immediately start the fresh offline attempt. They reset time, impacts, pose and held stick inputs first. Initial entry/new level still waits for Start practice; pause, tab/background and live-control authority rules remain explicit.

Wallet v3 retains v1/v2 claims, purchases, equipped vehicle/accessories and adds a bounded replay-earnings counter. Completion, records, reward and next-offer boundary are saved together. A v3 wallet is not readable by an unmodified older app. **Rollback requires a higher-code build with the v3 wallet parser**, preserving bonus earnings and ownership; do not uninstall/clear data or install an unmodified older parser as a data-preserving rollback.

## Waypoint review

Mission start follows the selected mode: **Single point** uses the selected destination; **Multiple points** visits the displayed list in order, then requests HOLD. The old Mission dropdown is renamed Multiple points. Start checks link/rover readiness and asks for explicit confirmation before navigation; it never arms automatically. The confirmation lists mode, order, coordinates and operating limits, and refuses a stale selection/mode. This is foreground phone-supervised ArduRover Guided navigation, not an uploaded FC AUTO mission.

The reported “Planning only. Needs ArduRover + GPS.” screenshot is the Mini rover profile. Mini rover, this LoRa gateway and native transmitter-only ELRS Backpack do not gain live navigation from map selection. The notice now explains the selected route mode, required compatible ArduRover Wi-Fi/GPS link and that movement has not started. Help and per-mode steps explain the sequence. Navigation engines, FC checks, command bounds, STOP/HOLD, transmission restrictions and independent watchdogs remain unchanged.

## A–F verification

| Gate | Required evidence |
| --- | --- |
| A source/build | Reviewed scope; cloud JVM, debug/release lint/build and retained signing. Local Android SDK/Gradle download BLOCKED. |
| B behavior | New JVM chance/no-reroll, coin reachability/payout/idempotence, v2→v3 migration/purchases/reload/bounds and neutral immediate-restart tests. Native seeded offer, pickup without payout, abandonment/reload, clear-only single credit, no-offer/uncompleted-level cases; both restart paths and Waypoint mode/blocked-start workflow; complete existing UI suite. |
| C regression | Motor synchronization, host/mini/profile/packaging tests and default cloud ASan/UBSan. Local address/UB checks use detect_leaks=0 because ptrace blocks LSan; no local leak claim. |
| D integration | Software route and transition regressions required. Physical FC/phone/USB/network compatibility NOT TESTED. |
| E HIL | BLOCKED: no connected phone/rover/transmitter/FC; no physical/field readiness claim. |
| F delivery | Same-signer code103 APK versus 0.8.84; manifest/permissions/alignment/CRC; source/evidence, BUILD_INFO and checksums. |

Successful final workflow/BUILD_INFO supplies software outcomes. Source comparison protects connection/authority/nav engines, fixed maze geometry/physics, catalog, shared popups, camera and firmware. No affected board compile/reflash is required.

## Installation and acceptance

1. Install over the existing app without clearing data. Verify wallet balance/claims/cosmetics, fixed level progress and records survive migration. Repeat Test restart from READY/RUNNING/PAUSED/CLEARED and after three bumps: the new attempt shows Pause, runs immediately, starts neutral and clears impacts/time. Leaving/backgrounding still pauses.
2. Replay a completed level repeatedly. At most one bonus is offered; retry/reopen must not reroll its presence. Pickup followed by abandonment/failure must not pay. Pickup plus END adds exactly one and persists after reopening. Clearing without pickup adds none. Uncompleted levels receive only normal first-play coins.
3. In Waypoint try Single point and Multiple points. Confirm route order and mode in the start dialog, cancel without moving, and check planning-only explanations in Mini/LoRa/ELRS profiles. A qualified compatible ArduRover link is required before the existing raised-wheel GPS/readiness/HOLD procedure; repeat STOP/tab/background/link-loss tests with wheels restrained and an independent power cutoff. Never count software-only tests as stopping or FC-failsafe proof.

Next concrete check: owner phone restart/bonus/migration acceptance, then compatible ArduRover restrained-wheel single/multiple route/HOLD validation. Retain the baseline APK/source and FC parameter backup; use a v3-compatible forward revert if needed.

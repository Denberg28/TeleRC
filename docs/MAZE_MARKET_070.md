# TeleRC 0.8.70 — maze Market and collectible coins

Baseline: qualified 0.8.69/code87 source `a2adab0b910802d7a2d5a26b7050598e92d60cbf`. Candidate 0.8.70/code88. Android-only; original app ID/signer, fixed course version 3, collision/steering physics, three-bump restart/dots, progress/history, FPV recovery/photo theme and all firmware/protocols remain compatible. No reflash. No stable promotion.

## Use and economy

On Test, the former OFFLINE control above DRIVE is now `Market · balance`. The footer still explicitly says OFFLINE. Gold coins appear at random reachable passage centers, never START/END or walls. Drive over one while RUNNING to bank 1 coin. Opening Market pauses the run and clears stick input; closing it leaves practice paused until Resume/Start. The scrollable Market has real vehicle/palette previews, prices, Buy, Use and Equipped states. Buy also equips the item. Unaffordable purchases are disabled and the model independently rejects them. Ownership, selected items, balance and collection history save locally as one wallet snapshot. No payments, account or network service.

| Item | Coins | Appearance |
| --- | --- | --- |
| Scout | Free | Original rover |
| Rally | 20 | Narrow striped racing body |
| Buggy | 35 | Wider open roll cage |
| Hauler | 50 | Cargo bed |
| Navy | Free | Original maze colors |
| Forest | 15 | Green passages/walls |
| Ember | 25 | Warm copper passages/walls |
| Glacier | 35 | Blue ice passages/walls |

Vehicles are cosmetic: identical movement and collision radius. Palettes affect the offline maze, leaving the app/photo background preference available as before. The free Scout retains the existing accent. Each level has a lifetime supply, not an endlessly refilling spawn timer: levels 1–5 have 8 coins each, 6–10 have 10, 11–15 have 12 and 16–20 have 14. Total 220 coins; the complete paid catalog costs 180, leaving 40 spare. First color costs roughly two fully collected early levels; first vehicle roughly three. These are capacity calculations, not measured player completion times. Reachable random placement may require exploration beyond the START→END route.

Coins bank immediately and survive a failed attempt. Restart resets time/bumps/dots and randomizes only the remaining unclaimed coin slots. New attempts, app restarts and replaying a level cannot reissue a previously credited slot. Fully collected levels have no more coins; choose uncleared/uncollected levels for more. Existing level progress remains usable after upgrade and starts with a fresh zero-coin wallet. Markets do not unlock levels or alter best times. The economy codec/version fixes both slot quotas and prices; future catalog price/supply changes require an explicit migration rather than silently revaluing existing wallets.

## Review and gates A–F

| Gate | Status / required evidence |
| --- | --- |
| A — source/build | Reviewed: pure bounded wallet, derived nonnegative balance, unique claimed slots, randomized safe placement, same renderer for maze/previews, paused Market and one-snapshot persistence. Local Android SDK unavailable; cloud JVM/debug/release lint/build and instrumentation APK build required. |
| B — behavior | JVM: 220/180 supply-price balance and early unlocks; buying/equipping/overspend/duplicates; corrupt bounded save sanitization; unique reachable random placement for all 20 levels; driving pickup, pause, retry and reload anti-duplication. API 35: native Market scrolling, Buy/equip two items, disabled insufficient-funds item, real frame-driven pickup/wallet update, reset and activity-restart persistence. Valid prior-play wallet is a test fixture for purchases, not a claim that the native test earns all prior coins through touch driving. Native bump-dot/reset and level clear/unlock checks retained. |
| C — regression | Motor-core sync, host suites and local address/undefined sanitizers required (local leak inspection restricted). Cloud full default sanitizers required. Native three-tone/photo import/removal and FPV menu checks retained. |
| D — software integration | Original signer, same app ID, increasing code88, compiled USB registration/permissions, APK CRC/16K alignment and checksums required. No affected ESP32 compile target. |
| E — phone/HIL | BLOCKED: no phone/receiver/rover attached. Native simulator evidence does not prove phone readability or physical link operation. Use next-check procedure below. |
| F — package | After software gates pass: signed test APK, exact source, JVM/lint/native screenshots, BUILD_INFO/source SHA and checksums in an unpublished draft. No publication/stable promotion. |

The final successful workflow and BUILD_INFO supersede pending software results only. Render captures cover gold coins, vehicle/color Market tabs through scrolling and an equipped rover/palette. All saves remain private local preferences; clearing app data/uninstall also clears wallet/ownership. The codec rejects unknown IDs/out-of-range slots and prevents negative balances from malformed ownership. It is not tamper-proof against rooted devices, manual preference editing or restoring an older full app backup.

## Next phone check, installation and rollback

Install over 0.8.69. With wheels restrained and cutoff accessible, enter Test: confirm live link disconnected/auto-connect off and OFFLINE footer. Collect a coin, inspect the Market balance, pause/retry and verify it cannot pay twice. Fail on three bumps and verify earned coins remain. Restart the app: wallet and equipped items persist; the run itself begins fresh as before. Inspect coins through every maze tier; buy a color/vehicle when affordable, switch to the free items, and verify cosmetics never change handling. Open Market with a stick held: practice pauses/neutralizes; close and require explicit Resume. Check small screen/large font scrolling, all background tones/photo, bump dots and clarity of END. Return to Controls with explicit Connect/Enable/Arm only.

Keep the prior 0.8.69 APK/source for rollback. Its maze ignores the new wallet preference; it does not understand coins/ownership. Android downgrade may require uninstalling, losing private settings/progress/history/routes/photo/wallet. Prefer a forward-version revert to retain data. Next concrete check: phone pickup → bank → retry without duplicate → buy/equip → app restart sequence.

# TeleRC 0.8.78 — practice worlds and accessories

Baseline: signed 0.8.77/code95, APK source `965a5782b5600df681c141cf7913b5fdefdbf852`; development baseline `b1997536dafc90f245bb1581e98b890fa81e19cd` includes qualified native-test synchronization and documentation. Candidate: 0.8.78/code96, same application ID/signer. Android-only; no firmware or protocol change, no ESP32 reflash. No stable promotion or field-validation claim.

Setup's version is at lower center in expanded and minimized modes. Narrow layouts give it its own line below the connection/update corner actions.

| Test levels | Environment | Scenery |
| --- | --- | --- |
| 1–5 | City | Asphalt grid, building/window details |
| 6–10 | Forest | Green floor, leaf canopies, undergrowth |
| 11–15 | Desert | Sand floor, dune contours, cactus details |
| 16–20 | Space | Star field, planet/ring details |

Scenery is deterministic and cosmetic, drawn below actual walls, coins, START/END and the vehicle. Wall rectangles, collision radius, fixed course seeds/version, unlocks, three-bump restart and clear-time records stay unchanged.

Market → Vehicles groups Scout/Rally/Buggy/Hauler under Land, Skiff under Sea, Skywing under Air and Starship under Space. Each has a distinct oriented top-view silhouette. Market → Accessories replaces Colors: Hood (6), Armament (12), Wheels (10), Exhaust (8), Spoiler (14). Buy equips; Remove hides an owned accessory; Use equips it again for free. Accessories stack and follow vehicle changes. Armament is a cosmetic model; no firing or live control feature. All vehicles keep the same rover maze handling and collision radius.

The 220-coin lifetime supply and unique claims are unchanged. Existing vehicle prices/ownership persist. Retired Navy/Forest/Ember/Glacier colors disappear; their spent coins return automatically because v2 retains the claims and no longer deducts those purchases. Migration writes one canonical v2 snapshot and cannot reissue collected coins. All paid vehicles/accessories together cost 220; no real money, cloud or new permissions.

## Gates A–F

| Gate | Required evidence / current status |
| --- | --- |
| A source/build | Reviewed; cloud JVM/debug+release lint/build required; local SDK unavailable |
| B behavior | v1 refund/idempotence/claims/vehicle migration, bounded v2 sanitization, accessory stacking/remove/re-equip, budget and environment boundaries; native render/geometry tests required |
| C regression | Fresh bridge sync, host/default ASan/UBSan and existing JVM/native photo/theme/maze/FPV suites required |
| D software integration | Native purchase/persistence/removal, seven distinct silhouettes within collision envelope, four environments, lower-center Setup version across themes/large text; required |
| E physical HIL | BLOCKED — no phone/UVC receiver/rover attached |
| F delivery | Same signer, code96 progression, APK alignment/CRC/USB registration, source/evidence ZIPs and verified checksums required; phone upgrade portion BLOCKED |

Only successful completed workflow steps and release BUILD_INFO establish software PASS. Native emulator checks are not physical receiver, rover or phone tests. No firmware target is affected by this update.

## Installation and next acceptance check

Install over the existing signed TeleRC without uninstalling or clearing app data. Use the test-release APK directly; the stable updater excludes prereleases.

1. Confirm the version at lower center on Setup; expand/minimize, change Light/Sepia/Dark and use large text. Corner actions must remain visible and clickable.
2. Open Test and check levels 1/5, 6/10, 11/15, 16/20 for the correct environment and visible walls/coins/front chevron. Existing unlocks/times remain.
3. If colors were owned, confirm returned coin value, retained vehicles and collected coins; restart the app and ensure no second refund or duplicate coin collection.
4. Buy/equip a vehicle in each class when funded. Preview and maze must match; heading/reverse, wall impacts and three-bump restart must behave consistently.
5. Buy/equip all accessory types, remove/re-equip, switch vehicle and restart. No duplicate charges, refunds on Remove, lost ownership or invisible direction indicator.
6. With rover wheels raised/restrained, confirm Test remains offline and returns to Controls without automatic enable/arm. Verify STOP, neutral, background/link-loss behavior and existing FPV reconnect manually.

Record phone model, OS/font/orientation, screenshots, starting/ending wallet balances and any failure. Actual hardware/installation HIL remains BLOCKED until measured.

Rollback: Android prevents lower version codes. An unmodified 0.8.77 cannot read v2 wallet saves; use a forward-version repair build that reads v2, retains all claims and refunds unsupported cosmetics while restoring earlier presentation. Do not uninstall to downgrade because that loses private progress/settings.

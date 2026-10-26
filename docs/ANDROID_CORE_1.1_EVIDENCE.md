# TeleRC Android v1.1.0rc1

Application source: 03b5b69ff39487678d25a8023c5438a773ca3d51
Native UI harness: 19ee9353867ee18ba114dc32794c029bd628c9a6

Scope: full Android setup/controls, Wi-Fi, direct framed LoRa USB control and provisioning, offline Test and balancing; mission/navigation code removed. TeleRC Go remains the minimal controller. PC TeleRC retains fleet/relay missions.

Current balancer firmware v0.4.0rc2 uses heartbeat, attitude and status events. The app keeps the legacy v0.3 pre-test separate because the newer firmware does not implement that admin protocol. Select Balancer · Wi-Fi, join TeleRC-2WD-Test, Connect, then use Controls for explicit neutral enable and ARM. Local balancing/paused recovery belong to the firmware.

The final application also guards live sends against replaced sessions/background state, keeps DISARM available when physical permission expires, and pauses reconnect after USB permission denial until a deliberate Connect retry. Standalone profiles say Stop control.

Legacy direct LoRa and PC fleet/relay LoRa are distinct protocols. This APK does not add Android fleet/relay support or assume compatibility from the board model.

Firmware sources are unchanged. Android package/signing identity is retained, version code increases from 1000 to 1100. Hardware installation, USB permission behavior on a phone, RF range, takeover, motor stopping and actual balancing remain NOT TESTED in this session. Use the restrained checks in START_HERE.md and the balancer firmware HIL checklist.

## Final signed build

PASS: build https://github.com/Denberg28/TeleRC/actions/runs/40288365262
- 150 JVM unit tests; debug/release lint and APK compilation.
- Existing bridge regression suite and full cloud address/undefined/leak sanitizer checks.
- Retained v1.0.0 signing certificate, unchanged package ID, increasing code 1100.
- Downloaded artifact and every packaged SHA-256 checksum verified.
- APK SHA-256: d0800ad42de2a764f628dcf02c1c132bc36b421d1e1646a4250f5955a5753f9c.

Next physical check: install on the phone, deny USB permission and confirm there is no repeated prompt; tap Connect to retry. Then execute the raised-wheel link-loss/recovery and explicit ARM/DISARM checks in START_HERE.md. Do not infer motor output or receiver health from a connection indicator.

## Native Android UI

PASS: https://github.com/Denberg28/TeleRC/actions/runs/40293580334
Test-only commit: 19ee9353867ee18ba114dc32794c029bd628c9a6.
The production MainActivity matches the signed APK source; the test branch uses a dedicated instrumentation runner.

Five mission-free pages in Light, Sepia and Dark themes, native tab menus,
disconnected Balance state, restored LoRa provisioning and no restored control
authority during page navigation passed on an Android 35 emulator.

## Delivery status and next check

Source/build, automated behavior/regression, signer and candidate packaging gates passed. Physical integration and HIL remain pending. This is a test candidate, not stable/field qualification. No GitHub release or stable promotion was performed. Review: https://github.com/Denberg28/TeleRC/pull/14.

Use the app with the matching direct-link firmware profile. Check installation, USB denial/retry, disconnect/reconnect, neutral control, explicit ARM/DISARM and balancer pause/recovery on the real phone and restrained robot before use. Keep the previous signed APK/configuration backup for rollback; Android normally requires uninstall/data loss for a version downgrade.

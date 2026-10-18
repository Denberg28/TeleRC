# TeleRC 0.8.82 — compact Setup and mission actions

Baseline: 0.8.81/code99, release commit `38b2310342f510b344ee4e6413e142a8f6533424`. App-only candidate 0.8.82/code100. No firmware reflash.

Setup: Guide is in the top bar immediately before minimize. It opens the existing ELRS TX/RX instructions (or the mini Wi-Fi guide selector). Both settings panels stay in a fixed, compact landscape pane. Connect/status share a row; ELRS Send/Listen ports share the endpoint row. Collapse/restore preserves drafts and the existing corner actions.

Controls: Use transmitter names the existing neutral/release handover. Phone control enables the current compatible link only after fresh telemetry and existing authority checks. On native TX Backpack, Phone control opens Use phone Wi-Fi and can switch to disconnected FC Wi-Fi Setup; this does not enable, arm or connect automatically. Native ELRS continues to reject phone drive/arm/navigation packets. The actual transmitter receiver, override permission and HOLD mode switch must be configured and verified on the FC.

Waypoint: Mission start replaces Check rover. It checks the rover/HOLD round trip and then opens the existing route confirmation when ready. It never arms the rover, uploads AUTO missions or starts motion without confirmation. Empty plans and unsupported links stay blocked. STOP, backgrounding, disconnect and tab exit cancel pending start intent. F1–F3 replace Review point/route and share the existing external servo assignment dialog; these buttons assign outputs, they do not send servo movement commands. M5–M8 remain reserved. Help & safety uses short operating instructions; all navigation engine limits and fail-safe readback rules remain unchanged.

## Verification and acceptance

| Gate | Status before cloud qualification |
| --- | --- |
| A source/build | Reviewed; local SDK missing. Cloud JVM/debug/release lint/build required. |
| B behavior/UI | Native fixed Setup geometry, Guide ordering, phone Wi-Fi cancel/switch, F1–F3 and Mission start planning-lock checks required; existing navigation regressions retained. |
| C regression | Motor sync and host/mini/default sanitizer checks required. |
| D integration | No protocol/firmware change. Native engine/transport guards retained; real phone/FC/radio NOT TESTED. |
| E HIL | BLOCKED: physical phone/transmitter/rover unavailable. |
| F delivery | Signed code100 APK, signer continuity, source/evidence/checksums required before candidate publication. |

Successful final workflow and BUILD_INFO replace pending software statuses only. Physical qualification remains pending; no stable readiness claim.

## Phone and restrained-wheel checks

1. Install over 0.8.81 without clearing data. Check Setup in all themes and normal/large text: both panels fully visible, no vertical drag, Guide before minimize. Edit endpoint, collapse and restore: draft and connection state retained.
2. With ELRS selected, open Guide and verify TX/RX and separate UDP port instructions. Controls shows Use transmitter and Phone control. Cancel Use phone Wi-Fi: link unchanged. Open Setup: FC Wi-Fi selected, disconnected and control disabled. Connect deliberately to the rover bridge.
3. With wheels restrained and the receiver configured, enable Phone control at neutral. Use transmitter must neutralize/release to receiver; verify actual pilot authority. Re-enabling phone control requires fresh telemetry and existing authority checks. Verify independent transmitter HOLD and RF-loss behavior.
4. Waypoint shows Mission start, STOP · HOLD and F1–F3. Check shared assignments in Controls; M5–M8 cannot be assigned. Read Help & safety; no parameter dump or obsolete Review point/Check rover instructions.
5. In planning-only profiles Mission start must never send movement. On a validated transparent FC link with GPS/compass/EKF and read-back failsafes, arm separately, select a route, then tap Mission start. Verify checks/HOLD before confirmation; Cancel must not navigate. Repeat STOP/background/disconnect/tab exit during checking: no delayed start prompt or motion. Confirm deliberately for a restrained navigation check; validate all baseline WAYPOINT_079.md HIL tests before driving.

Rollback: retain the 0.8.81 APK and parameter backup; restore using a forward-version rebuild that preserves wallet v2 and saved preferences. Do not uninstall or force a downgrade. Next check: final cloud qualification, then phone geometry and restrained handover/navigation acceptance above.

Software qualification history: run 39153631308 passed product builds, JVM/debug+release lint, host/default sanitizers, and earlier native layout/theme checks; the new servo assignment test exposed an inaccessible Close action. A synchronized retry in run 39154777535 reproduced the problem. The final product removes the competing custom panel from the standard single-choice servo dialog, retains reserved-output filtering, and verifies that the actual Close view is fully visible and clickable. The final workflow rebuilds/signs the changed product and reruns all required software/native gates; neither failed native run qualifies this release.

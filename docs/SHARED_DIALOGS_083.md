# TeleRC 0.8.83 — shared popups and reliability review

Baseline: 0.8.82/code100, commit `c7d2e00a553849931e18913b54854d587259995f`, successful qualification run39155746002. Candidate: 0.8.83/code101. Owner authorized review, development and release on 19 October 2026. App-only; no firmware reflash.

## Changes and acceptance

- Setup, Controls, Waypoint, update offers and Test's Clear times use a shared flat popup: themed rounded surface, small title, quiet dividers, scrolling content, fixed 48 dp minimum actions, centered bounds configured before first show and no animation. Existing Test Levels/restart/Market retain their established renderer and appearance. Anchored tab menus keep their existing shared menu contract. Native Android permission, file-picker and installer windows remain system-owned.
- Popup content uses one scroll owner, including forms previously wrapped in ScrollView. Validation can keep a form open without dismissing it; Cancel never saves. Positive/negative/list callbacks preserve their existing explicit meanings. Theme/page changes, pause and destruction close app sheets and stale references.
- Opening a modal revokes manual phone authority. A moving Guided route requests HOLD before its dialog opens. Closing a popup never resumes manual or autonomous motion. Mission confirmation still follows fresh readiness checks and explicit approval, without automatic arming.
- Waypoint removes the redundant Disarmed / unknown and Mode unknown footer. Steps and profile guidance are shorter; blocked navigation and HOLD failures remain visible. Tap the rover-check summary for full diagnostic details. Help & safety is concise. No arming, GPS/EKF, parameter-readback or operating-limit check is removed.
- Ordinary rover Wi-Fi now checks for removal of its captured Android Network at most once per second, like Backpack Wi-Fi. Removed networks tear down the old session and use the existing reconnect loop. Capability changes and heartbeat gaps alone do not churn sockets. Recovery never restores control/ARM/navigation; USB skips Wi-Fi inspection, and Backpack alone sends registration PINGs.
- Release metadata is bounded to 1 MiB before JSON parsing. Duplicate update checks are suppressed; completed checks reset the guard. Closed/background Activities cannot receive new update-offer windows. HTTPS URL, digest, package, increasing version and signer checks remain.

## Review record

Reviewed application startup/lifecycle, all modal call sites and tab transitions, UDP peer selection/session replacement, heartbeat expiry, queued ARM cancellation, neutral/release authority, native ELRS command filtering and registration, USB framed transport/bounds, navigation engine/codec/HOLD supervision, FPV permission/reconnect ownership, theme/photo persistence, updater verification, and firmware interoperability boundaries. The practice engine, wallet, navigation engine, codecs, FPV implementation and firmware are protected against unintended changes by the source gate and regression suite. Test's Clear times changes only popup factory.

| Area | Finding and disposition |
| --- | --- |
| Popups | Different native/test shells and ownership could leave old-theme windows or offscreen actions. Shared bounded sheets and lifecycle cleanup implemented. |
| Control workflow | A modal could obscure live controls. Explicit neutral/revocation or Guided HOLD now precedes showing it; no resume on dismissal. |
| Reconnect | Regular Wi-Fi could retain a socket bound to a removed Android Network. Extended bounded rebind detection; heartbeat/capability churn does not reconnect. |
| Updater | Network JSON allocation and duplicate checks were unbounded. 1 MiB limit and single-check guard implemented. |
| Security | Exact peer address/port checks, CRC checks, immutable native ELRS transport restrictions, Android USB grants, URL/hash/signer/version gates retained. Wi-Fi MAVLink remains unsigned and lacks authenticated replay protection; trusted network operation is still required. No claim of cryptographic channel authentication. |
| Command safety | Existing epoch/session/age checks, fresh-neutral gates, explicit ARM, finite watchdogs and FC readiness/HOLD rules retained. Rebinding cannot grant authority. |
| Verification limits | Software evidence does not measure RF range, physical reconnection delay, stop distance, brownouts, real USB preview or installed FC failsafes. |

The review identifies and addresses the findings above; it is not a formal proof or a claim that the complete stack is vulnerability-free. Firmware is unchanged, so no affected board target requires recompilation for this app release. Existing paired-upgrade and actual-board qualification evidence is retained, not relabeled as new hardware testing.

## Gates A–F

| Gate | Before final workflow |
| --- | --- |
| A source/build | Reviewed; local Android SDK/Gradle network BLOCKED. Cloud full JVM/debug+release lint/build required. |
| B UI/behavior | Native shared list/form/message in Light/Sepia/Dark and large text, visible actions, validation retention, centering/no movement, transition cleanup and modal authority revocation required; full existing UI regressions retained. |
| C regression | Motor synchronization, host/mini/profile tests and default cloud sanitizers required. Local ASan/UBSan can run with leak inspection disabled because ptrace blocks LSan; no local leak pass claim. |
| D integration | Software native/transport checks required; real Android network replacement, FC/receiver/USB integration NOT TESTED. |
| E HIL | BLOCKED: no phone, rover, transmitter, drivers or FC attached. |
| F delivery | Signed code101 APK, retained signer versus 0.8.82, native/ZIP alignment, source/evidence and SHA256SUMS required before test-candidate publication. |

Final workflow and BUILD_INFO supersede pending software gates only. No stable or field-validation claim.

## Phone and restrained-wheel checks

1. Install over 0.8.82 without clearing data. Test every guide, settings list, slider form, servo assignment, FPV options/help, Waypoint notice/confirmation/coordinates and Clear times. Check all themes, large text, full visible fixed actions, content scrolling and no sideways opening jump. Native OS permission/installer windows use Android styling.
2. Cancel sliders and coordinates: settings/route stay unchanged. Invalid coordinates keep the form open. Change theme/tab or background/restore while a sheet is open: no stale window, draft action, implicit authority or delayed start.
3. With wheels restrained, enable phone control then open F1 or a Controls dialog while moving. Verify neutral/revocation; dismiss and confirm no motion until explicit Enable. During a validated Guided route, open help/servo assignment and confirm HOLD at the FC; closing does not resume. Verify independent transmitter HOLD/power cutoff.
4. On ordinary FC/mini Wi-Fi and native Backpack separately, toggle phone Wi-Fi or restart the AP so Android replaces its Network. Confirm old socket closes and automatic reconnect binds to Wi-Fi after it returns. Control and ARM remain disabled. Repeat ten cycles; record measured delays. Network capability changes alone should not force disconnects. USB must not inspect Wi-Fi.
5. Repeat 0.8.82 restrained takeover, heartbeat loss, stale ARM, STOP/tab/background, navigation checks/HOLD and RF-loss procedures. Verify actual stop outputs and physical stopping independently. Do not count software checks as hardware acceptance.
6. Verify update checks with public release access, no network, repeated presses, backgrounding, bad metadata/digest/signer, and Android installer handoff; no installation is allowed without valid identity/integrity and explicit user intent.

Rollback uses a forward-version rebuild of the retained 0.8.82 product with wallet v2 compatibility and settings preserved. Keep the previous APK and FC parameter backup. Next: final software qualification, then the phone/raised-wheel procedures above.

Qualification follow-up: initial run39158705216 passed source/host/default sanitizer gates and caught a missing Builder cancel-listener compatibility method during Kotlin compilation. The final shared factory preserves that listener and invokes cancellation cleanup on lifecycle/page teardown; native transition coverage asserts the callback. A complete build and UI rerun is required before publication.

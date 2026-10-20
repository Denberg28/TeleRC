# Balance popup layout — 0.8.89

Baseline: `5a40f81c4d916ee5e29e46c9df168fb18b708e30` (0.8.88/code106).
Scope: only Balance presentation and its popup lifecycle. Code107 enables an
in-place upgrade with the retained signer. Firmware remains balancer0.3.0.

Balance now opens in a centered sheet using the existing PopupLayout content
measurement: preferred width420dp, maximum height340dp, constrained to the visible
screen with32dp horizontal and24dp vertical clearance. The body scrolls within
those bounds. Matching12dp side/8dp vertical frame padding, readable line spacing,
consistent section gaps, equal motor buttons and fixed equal-width Refresh,
STOP / Disarm and Controls actions keep the layout compact. A balanced header
keeps the title centered and provides a48dp Close action. Close/Back/outside tap
returns to Setup; Controls opens Controls. Configuration/background/navigation
remove the old dialog and preserve deliberate re-entry and command cancellation.
Existing command dispatch, confirmations, readiness locks and motor safety
remain in place. Other pages and the shared popup renderer are unchanged.

Acceptance: normal and1.3x text in Light/Sepia/Dark; centered screen-bounded frame,
readable content, scrolling long forms, fully visible footer, equal padding,
o offline commands, Controls dismissal, re-open and Close returning to Setup.
The existing native UI harness now checks these properties for Balance.

## Verification gates

| Gate | Evidence before final CI |
| --- | --- |
| A source/build | Reviewed; local Android BLOCKED by absent SDK and Gradle download network access. Final CI runs debug/release builds and lint. |
| B behavior | Host regression PASS. Balance protocol/firmware source unchanged. Native popup geometry/lifecycle checks pending CI. |
| C regression | Motor-core sync and host suite PASS. Local default LeakSanitizer BLOCKED by /proc task access; final CI uses default sanitizers. |
| D integration | Native three-theme/normal+large-text full UI suite pending CI. No affected firmware target. |
| E HIL | BLOCKED: no attached physical phone/robot. |
| F delivery | Non-publishing CI verifies retained signer, increasing code, alignment and checksums. No stable promotion. |

Final CI results apply only to the commit named by BUILD_INFO. A passing emulator
suite is software UI evidence; physical phone/rover behavior remains unmeasured.

Phone acceptance: install signed0.8.89 over0.8.88. Open dropdown → Balance in each
theme and at larger text size. Scroll to all sections; confirm the popup remains
centered, footer stays visible, text wraps, and Close/Back/reopen/background work.
With motor power disconnected, connect the balancer and verify status and
calibration confirmation. Secure the chassis with wheels raised before motor
tests. Cancel a confirmation by navigation/background and verify it cannot issue
an old command. Setup never arms; return to Controls deliberately for operation.

Rollback: retain signed0.8.88 and firmware0.3.0. Prefer a forward-code rollback
reverting this UI commit while preserving app data. Android downgrade usually
requires uninstalling and deletes local settings/data; export first. The next
concrete check is the popup on the user's physical phone at their display scale.

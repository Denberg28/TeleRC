# Targeted code edits

| Desired result | File | Section/symbol | Edit and units/scope | Related settings and check |
| --- | --- | --- | --- | --- |
| Change navigation/screens | MainActivity.kt | UI — Navigation, navigation, switchTo, render | Three equal 48 dp tabs (Setup/Controls/Mission); LoRa pairing under Setup | Tab switch must revoke control; device lifecycle check |
| Give joysticks/map more room | MainActivity.kt | UI — Single-rover controls | Layout weights 2:3:2; 48 dp header/footer | Verify narrow landscape, large text, both themes |
| Change colors/spacing | MainActivity.kt, AppColors.kt | UI — Theme surfaces, UI — Buttons, UI — Cards, UI — Outer padding, UI — Accent palette | 16 dp shell/card interior padding; 10–12 dp gaps; accent RGB | Light/dark contrast, selected tabs and disabled states |
| Change control sensitivity | MainActivity.kt, JoystickView.kt | showControlResponse, ControlResponseStore, input | Per-axis 25–100%, expo 0–100%; preserves released preferences | CH1 steer/CH2 drive and neutral release; HIL before range changes |
| Change joystick line / light | JoystickView.kt | UI — Axis scale, onDraw | Tick/marker size, gradient and stroke in dp; travel remains radius × 0.62 | Draw/touch use the same travel; both themes/device checks |
| Change Wi-Fi upload timing | MissionTransfer.kt | Mission — Retry policy, MissionWire | 1500 ms retry, 3 retries/step, 120000 ms total | MissionTransferTest; bridge allowlist and HIL required |
| Change LoRa local buffering | TelemetryMailbox.kt, UsbLoRaTransport.kt | Connection — Receive backlog | 16 frames / 250 ms local receive age | TelemetryMailboxTest; actual USB congestion/endurance HIL |
| Adjust mission editor layout | MissionPlannerView.kt | UI — Planner spacing | Map:list 3:2; 48 dp action rows | List scroll, map gestures and coordinate validation |
| Adjust plan bounds/CSV | MissionPlan.kt | Mission — Bounds and CSV schema | 100 points, 16,384 characters, decimal degrees | MissionPlanTest; retain backward import compatibility |
| Change plan saving/import | MainActivity.kt | saveMission, onActivityResult, documentBusy | Atomic private mission.csv; bounded import/export on one worker, separate from control TX | Round trip, picker cancel/failure, upgrade and write-failure checks |
| Change map layers/placement | RouteMapView.kt | draw, showPlan | Phone blue, rover green, plan orange | Never write sample camera origin as Home/waypoint |
| Change LoRa provisioning | MainActivity.kt, LoRaSetup.kt | renderLoRa, LoRaSetupExchange | Existing profile/key/read-back | Required JVM, firmware, USB/RF checks; do not alter protocol casually |
| Preserve RC permission and neutral readiness | RcTakeover.kt, MiniRoverWifi.kt, MainActivity.kt | fieldControlAllowed, activate/enable, toggleArmDisarm, revokeExpiredControl | FC permission <750 ms; Mini sent-neutral dwell ≥300 ms | RcTakeoverTest, MiniRoverWifiTest, LiveControlGateTest, actual restrained takeover checks |
| Keep Wi-Fi rebind safe | BackpackRecovery.kt, MainActivity.kt | poll, boundWifi, start/stop | Check captured Network at most once/second; never auto-enable | BackpackRecoveryTest plus actual Android network loss/recovery |

Kotlin files live in `app/src/main/java/io/github/denberg28/telerc/`. Rules live in AGENTS.md; scope in PROJECT_KICKOFF.md; test evidence in TESTING.md. Do not tune control/watchdog/electrical limits from comments alone.

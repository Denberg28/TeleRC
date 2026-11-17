# Targeted code edits

| Desired result | File | Section/symbol | Edit and units/scope | Related settings and check |
| --- | --- | --- | --- | --- |
| Change navigation/screens | MainActivity.kt | Page, pageDropdown, render | One shared menu; default Controls | Tab switch must revoke control; device lifecycle check |
| Give joysticks/map more room | MainActivity.kt | UI — Single-rover controls | Layout weights 2:3:2; 48 dp header/footer | Verify narrow landscape, large text, both themes |
| Change colors/spacing | MainActivity.kt, AppColors.kt | ink/pale/surface, shell, button | dp padding inside; layout margins outside | Light/dark contrast and disabled states |
| Change control sensitivity | MainActivity.kt, JoystickView.kt | renderSetup, input | Saved 25–100%, scaling around 1500 µs | CH1 steer/CH2 drive and neutral release; HIL before range changes |
| Adjust mission editor layout | MissionPlannerView.kt | UI — Planner spacing | Map:list 3:2; 48 dp action rows | List scroll, map gestures and coordinate validation |
| Adjust plan bounds/CSV | MissionPlan.kt | Mission — Bounds and CSV schema | 100 points, 16,384 characters, decimal degrees | MissionPlanTest; retain backward import compatibility |
| Change plan saving/import | MainActivity.kt | saveMission, onActivityResult, documentBusy | Atomic private mission.csv; bounded import/export on one worker, separate from control TX | Round trip, picker cancel/failure, upgrade and write-failure checks |
| Change map layers/placement | RouteMapView.kt | draw, showPlan | Phone blue, rover green, plan orange | Never write sample camera origin as Home/waypoint |
| Change LoRa provisioning | MainActivity.kt, LoRaSetup.kt | renderLoRa, LoRaSetupExchange | Existing profile/key/read-back | Required JVM, firmware, USB/RF checks; do not alter protocol casually |

Kotlin files live in `app/src/main/java/io/github/denberg28/telerc/`. Rules live in AGENTS.md; scope in PROJECT_KICKOFF.md; test evidence in TESTING.md. Do not tune control/watchdog/electrical limits from comments alone.

# TeleRC 0.8.69 — persistent wall-bump dots

Baseline: qualified 0.8.68/code86 source `d4f29c9d91907c57d40913d9e5570b3dd6d58f19`. Candidate: 0.8.69/code87. App-only; same identity/signer and level course version 3. Maze layout, driving physics, three-impact rule, progress/records, FPV/photo theme, live control and firmware remain compatible. No firmware reflash. Stable 0.8.64 is retained; no stable promotion.

Each counted wall contact adds one opaque red dot with a white outline at the closest point on the struck wall. World coordinates anchor it while the rover moves. Dots are painted after the rover so they remain visible. There is no fade clock or alpha animation. Held contact does not add duplicates. The same maximum of three impacts bounds both the marks and bump counter; repeated hits at the same spot can visually overlap. Pause/resume, navigation away/back and view recreation within the same activity retain the attempt's marks, including after a clear or third-bump stop. Restart level clears all dots along with time/count/inputs. A selected/next level creates a fresh attempt. As in 0.8.68, an app/process restart begins a new READY attempt; attempt marks are not saved to disk. Level progress and recorded clear times still persist.

## Gates A–F

| Gate | Source status and required result |
| --- | --- |
| A — source/build | Reviewed: bounded model-owned impact positions, contact-point projection, snapshot list and opaque renderer. Local Android SDK unavailable. Cloud JVM/debug/release lint/build and instrumentation APK build required. |
| B — behavior | JVM regression checks wall-anchored position, one mark for held/paused contact, previous marks retained through three impacts/failure, and reset removal. API 35 native tests sample actual rendered red pixels before and after a 1.2-second pause, assert their removal after the restart dialog, capture dot/reset screenshots, and retain level clear/unlock/persistence checks. |
| C — regression | Local motor-core sync/host suite and ASan/UBSan required; local leak inspection restricted. Cloud full default sanitizers required. Existing native three-tone/photo import/removal and FPV-format checks retained. |
| D — software integration | Original signer, same app ID, increasing code87, USB attach metadata/class/singleTop, capture permissions, APK CRC/16K alignment and SHA256 checks required. No affected firmware target. |
| E — phone/hardware | BLOCKED: no attached phone/receiver/rover. Simulator render evidence is not phone legibility or physical reliability evidence. Run the acceptance sequence below. |
| F — package | After software gates pass: signed test APK, source, native screenshots/reports, BUILD_INFO/source SHA and checksums in an unpublished draft. No stable promotion. |

The final successful workflow and BUILD_INFO supersede pending software statuses only. No hardware/field claim. Review source and artifact hashes from that package.

## Install, next check and rollback

Install the signed update over 0.8.68. With rover wheels restrained and cutoff accessible, enter Test and confirm live link/auto-connect are off. Drive into a wall: one red dot on the wall. Hold/release/push at that spot: no duplicates. Back away, pause for at least ten seconds, leave Test/return and confirm the dot remains at its location. Make two more separate contacts and confirm marks remain behind the restart prompt. Restart level: all dots disappear, time/count reset and movement waits for Start. Verify later levels and Light/Sepia/Dark/photo backgrounds, then explicitly reconnect and enable before any live control. No firmware reflash.

Rollback source is the baseline above; keep the 0.8.68 signed APK. Android downgrade may require uninstalling and losing private settings, progress, records, routes and photo. A forward-version revert avoids that downgrade. No data migration is added by this change. Next concrete check: phone red-dot visibility, pause/navigation retention and restart removal in all backgrounds.

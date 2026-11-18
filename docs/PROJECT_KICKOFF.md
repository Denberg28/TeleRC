# Active scope — 18 November 2026

User requested TeleRC Go-style gradient joystick, simpler Setup, stronger LoRa and mission planning via Wi-Fi. Repository Denberg28/TeleRC, review branch `update-joystick-setup-wifi-missions`, baseline main `2375fc6` / retained published v1.2.0rc1. Standing constitution authorization covers commit/push and eligible GitHub prerelease delivery.

Implement Android UI/input-pointer recovery, bounded/age-limited USB telemetry and explicit acknowledged disarmed Wi-Fi mission upload through the paired legacy FC bridge. Acceptance: same control mapping/range/neutral; compact reachable settings; expired/overflow USB batches discarded; protocol requests source/target/CRC/type/sequence checked; upload succeeds only after all items and final ACK; bounded retries; no upload resume/arm/AUTO/start on recovery; original plan/settings schema retained. Mini/dedicated USB LoRa keep local planning. No mission execution UI is added. Tests and limitations belong in TESTING.md; signal path/compatibility in ARCHITECTURE.md. No physical reliability claim without HIL.

Rollback: retain signed v1.2.0rc1 plus matching firmware/settings and export CSV before uninstalling. Android code downgrade may require uninstall/data loss. The new bridge retains prior command formats; restore only the intended legacy FC bridge if necessary, preserving other roles and NVS.

Historical kickoff follows.

# TeleRC simplification kickoff — 17 November 2026

Based on Project Kickoff Template 1.3, AGENTS 1.4 and Constitution 3.2 from Denberg28/Agents_md commit `012797d58ae19c713b7051f233d4023db94afd67`.

TeleRC (`Denberg28/TeleRC`) is the Android single-rover controller. Use TeleRC Go (`Denberg28/telerc_go`, reference commit below) as the simple connect-and-drive reference. Retain Wi-Fi UDP, native USB LoRa provisioning/control and add local waypoint planning. Continue the existing Kotlin/native Android/MapLibre stack without new dependencies or firmware protocol changes. PC-TeleRC remains the fleet product.

Baseline: source `67b2cf5`; packaged candidate 0.8.53/code 71 at `0f7272d`. Neither is hardware validated. Preserve the previous signed APK and firmware. Work on `simplify-go-lora-missions`; standing Constitution authorization covers commit, push and a prerelease after automated/packaging gates. Stable promotion requires HIL.

User flow: open Controls → Connect (saved Wi-Fi/LoRa) → fresh heartbeat → Enable → explicit ARM if needed → drive → STOP/release or Disconnect. Setup owns endpoint, transport, sensitivity, theme and updates. LoRa setup retains board provisioning and read-back. Mission opens with live control revoked → add by map tap or coordinates → edit/reorder/delete → local autosave → import/export TeleRC CSV. Planning never transmits waypoints or changes vehicle modes.

Acceptance: four focused screens; no game/music/test vehicle/servo-assignment UI; CH1/CH2 and all freshness/handover/watchdogs unchanged; no automatic enable/arm on reconnect; valid plans of 0–100 points survive CSV round trips; invalid/out-of-range/oversized import leaves current plan unchanged; delete/clear/import are recoverable through export and destructive plan replacement is confirmed; map distinguishes measured rover/phone tracks from planned waypoints; background/tab transitions revoke live control. Map tiles need internet/cache; coordinates, plan edits and CSV work without tiles. No autonomy, fleet orchestration, radio mesh/relay management or physical readiness claims.

Checks: required host/motor-core/sanitizer commands; JVM plan/control/protocol regressions, debug/release lint/build and signing/package verification in existing CI; restrained-wheel lifecycle, native USB/LoRa, installation/upgrade and failsafe tests in TESTING.md. On missing SDK/hardware continue cloud/software work and mark physical gates BLOCKED.

Rollback: export the mission and preserve private app data/settings before uninstalling. Android cannot normally install an older versionCode over the new one; reinstall retained signed 0.8.53 only after backup, then restore compatible data manually. Firmware is unchanged and must match the installed hardware roles.

TeleRC Go reference commit: `9c3b2d9dac822d698f4b93549d1612c78ebf6e27`.


Release inventory correction (17 November): default-branch 0.8.x source was older than the available published v1.1.0rc1/code 1100. Final version is 1.2.0rc1/code 1200. Preserve relevant physical RC permission, standalone neutral dwell, foreground/session send checks, Wi-Fi rebind, strict MAVLink parser, updater checks and steering/drive/expo preferences from published source `03b5b69ff39487678d25a8023c5438a773ca3d51`. The initial 0.8.54 release attempt fails closed on missing historical APK assets and is not a delivered release. Final packaging verifies against available v1.0.0 and v1.1.0rc1. Roll back using retained 1.1.0rc1 after data backup/uninstall as necessary; physical compatibility remains subject to HIL.

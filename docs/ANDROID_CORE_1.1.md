# TeleRC Android 1.1.0rc1

TeleRC now focuses on manual operation, Wi-Fi, direct LoRa, diagnostics,
offline practice and balancing. Navigation/mission implementation is removed
from the APK. PC TeleRC remains the mission/fleet application; TeleRC Go remains
the minimal Wi-Fi controller. No feature unlock, account or subscription is added.

## Baseline and implementation

Baseline: TeleRC release/publish-v1.0.0 commit
8657a04 (v1.0.0 app code 1000). New code: 1100, same Android application ID
and existing signing identity. Firmware is unchanged in this app-only update.

- Setup, Controls, Test, Balance and LoRa setup are accessible in the tab menu.
- Mission plan/map, Guided navigation, mission functions and their lifecycle
  dispatch are removed. Existing saved mission preferences are left dormant.
- Measured tracks, dead reckoning, CSV export, F1–F3 output assignments,
  offline practice, response settings, themes and optional FPV remain.
- Named Balancer Wi-Fi profile uses the standalone native command protocol,
  192.168.4.1:14550 and a sent-neutral ARM dwell. It avoids the ArduRover
  CH7 permission requirement, which does not exist in standalone firmware.
- The Balance panel reads CRC-checked heartbeat, attitude and status events
  from firmware v0.4.0rc2. Wrong system/component, corrupted/nonfinite or
  reordered attitude is ignored. Old data is labeled stale. New boot tokens
  clear diagnostics and revoke app control. ARM is distinct from output state;
  a heartbeat alone cannot identify whether motors are currently paused.
- Legacy v0.3 calibration/direction tests are an explicit entry and require
  their own valid versioned reply. v0.4 does not implement that admin protocol.
- Direct LoRa board read/pair/restart UI is restored with its existing serialized
  timeout/readback protection. It uses native USB CDC, not USB-UART adapters.
- Live TX rechecks session identity, connection, foreground and Controls page
  inside the command lock. A replaced session cannot transmit a newly enabled
  session's axes through its old transport. DISARM remains available for an
  armed rover when physical permission telemetry expires.
- Reconnection restores transport only. Enable and ARM remain deliberate.

## Compatibility

| Firmware/profile | Supported scope | Limit |
| --- | --- | --- |
| ESP32-S3 mini Wi-Fi native | Manual, ARM/DISARM, diagnostics | Join actual firmware SSID; single controller |
| ESP32-S3 balancer v0.4.0rc2 | Manual travel + local balance, live attitude/events | PID/calibration use firmware serial workflow |
| Legacy v0.3 balancer admin | Calibration and direction pre-test | Matching TELERC_BALANCE_V1 response required |
| ArduRover Wi-Fi bridge | CH1/CH2 manual control, telemetry, ELRS permission | Existing CH7 and receiver setup still required |
| TeleRC framed LoRa BASE/ROVER | Manual control + USB local provisioning | Direct single-rover profile, matching key/radio settings |
| New fleet/relay LoRa firmware | PC TeleRC workflow | Not compatible by assumption; no Android fleet/relay support claimed |
| ELRS TX Backpack | Retained telemetry-only profile | Native handset owns RC; no competing phone commands |

Changing apps does not change firmware pin assignments, independent watchdogs,
radio authentication or FC failsafes. Wi-Fi CRC/endpoint validation is not
command authentication. No paired firmware upgrade is introduced here.

For balancer v0.4, link loss cancels travel but local balancing can continue.
PAUSED retains ARM and can resume motor output when upright even after link
loss once balance has engaged. DISARM before handling. The app cannot replace
the local 4 ms PID loop or verify wheel direction from PWM alone.

## Verification and gates

| Gate | Status at source preparation | Evidence |
| --- | --- | --- |
| A source/build | Source reviewed; Android cloud build pending | New build-only workflow checks both lint/build variants and signer/version |
| B behavior | New JVM regressions pending build | Boot/fault/ARM distinction, wrong peers, CRC, stale/reordered/nonfinite attitude |
| C regression | Host and address/undefined checks PASS locally | Full default leak check blocked by sandbox /proc visibility; CI retains it |
| D integration | NOT TESTED physically | Protocol review against current balancer source and retained LoRa framing |
| E HIL | BLOCKED | No connected phone, radio, FC, motor or driver hardware |
| F delivery | Candidate only after build success | Exact source archive, signer report, SHA-256 sums; no stable promotion |

Local SDK is absent and Gradle network download is unavailable. The signed
build-only workflow runs existing signing secrets on the isolated same-repo
branch and uploads artifacts without publishing a GitHub release. Its final
APK_VERIFICATION.json is authoritative for commit, tests and package identity.
No firmware target source changes; actual-board recompilation is not applicable.

## Restrained validation before use

1. Install over v1.0.0; confirm retained settings and no resumed driving.
2. For each intended link, repeat connect/enable/stop/disconnect 20 times.
   Lose heartbeat/Wi-Fi/USB while a stick is held, restore it and confirm no
   app authority returns until deliberate enable. Repeat background/tab changes.
3. Confirm CH1 steer/CH2 bidirectional drive, centered 1500 us, ARM confirmation
   and DISARM when CH7 permission is absent. Measure installed stop behavior.
4. Connect one inactive LoRa board, read/save/restart/read, then repeat partner.
   Match profile/key. Disconnect during Save; late ACK must not confirm a retry.
5. For balancer v0.4, use its HIL checklist. Verify supported upright ARM,
   continued local balancing with neutral travel, pause/upright recovery, local
   balance during link loss, explicit DISARM, controller fault and reboot.
   Do not assume heartbeat ARM means motors are off or active.
6. Open Test and confirm no vehicle commands or reconnection attempts. Return
   to Setup and connect deliberately. Check CSV export and themes/large text.

Rollback: retain the previous signed APK and settings backup. Android blocks
version-code downgrades; reinstalling older code may require uninstall/data
loss. Do not change signer or erase firmware NVS to force a rollback.

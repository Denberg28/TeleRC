# TeleRC 0.8.88 application review

## Baseline and scope

Baseline: product 0.8.87/code105 at `359d10ec0facd6501119f13bd18ed8ab82776b44`, qualified run39419210517, with publication/docs branch head `3c47aca17cc6c392dc9582e9381090cf2d48b9f7`. Candidate0.8.88/code106. Review covers Android entry points, control/transport/session lifecycle, MAVLink/manual telemetry, Waypoint/functions, Balance setup, USB/FPV, update/install, file import/storage, practice persistence, navigation and shared UI. Firmware source and wire formats are retained; existing host regression is rerun. This is a source and software review, not a penetration test, radio/physical qualification or a guarantee of defect absence.

Acceptance: one Balance navigation entry; bounded compact/symmetric shared popups; readable enabled/disabled actions across three themes and large text; no consent crossing a reboot/reconnect; malformed/unsupported telemetry cannot refresh state; valid MAVLink2 truncation remains compatible; duplicate update broadcasts cannot spawn concurrent hash workers; manual/GPS/ELRS/STOP isolation and saved data regression pass.

## Confirmed findings and fixes

| Finding | Priority | Result |
| --- | --- | --- |
| Balance calibration/test/direction dialog could outlive a reconnect and act on replacement state | High correctness issue | Fixed: consent binds boot/session/action, rechecks fresh readiness, and is invalidated on leave, disconnect or reboot; all setup actions covered by regression |
| Manual parsers accepted unknown MAVLink2 incompatibility flags; ACK/position source components were unrestricted | Medium | Fixed: reject unsupported flags, non-autopilot sources, extraneous bytes, invalid message sizes and ACKs addressed to another GCS; checksum and endpoint filters retained |
| Valid MAVLink2 zero-truncated ACK/position fields could be lost or treated as absent | Medium compatibility | Fixed: restore missing trailing zeros; independent encoded fixtures cover truncation and rejection cases |
| Repeated/forged matching download broadcasts could launch concurrent APK hash workers | Medium availability | Fixed: single-flight verification, captured download/hash, authoritative successful DownloadManager status and one completed result; early broadcasts do not consume the genuine completion |
| Duplicate Balance header entry, unbounded dropdown height, inconsistent tab action styles and popup button heights | UI | Fixed: dropdown-only entry, screen-bounded scrolling menu, shared action widget/disabled palette, tighter popup side insets/width and equal-height footer actions |

Reference protocol rules: https://mavlink.io/en/guide/serialization.html and official COMMAND_ACK definition https://github.com/mavlink/c_library_v2/blob/master/common/mavlink_msg_command_ack.h . Unknown incompatibility flags must be rejected; MAVLink2 trailing zero removal is part of the format. These checks are not cryptographic authentication.

## Review coverage and retained contracts

| Area | Evidence inspected / retained behavior |
| --- | --- |
| Manual drive / ARM / STOP | MainActivity, LiveControlGate, MiniRoverArmGate, HeartbeatHealth, ControlResponse, JoystickMath; CH1 steer/CH2 drive/1500 neutral, independent gains, normal non-force ARM, neutral dwell, epoch/500ms cancellation, fresh heartbeat expiry, tab/pause disable, explicit recovery |
| Wi-Fi / USB / ELRS | Exact peer address+port, captured socket/network identity, removal-only rebinding, bounded framed USB payload/parser and partial-write fail-close, native Backpack transport accepts only registration PING and never phone RC/ARM |
| Mission / F1–F3 | WaypointNavigation/Protocol/Functions/Tab: immutable confirmed draft, fresh GPS/heading/EKF/health/battery and failsafe parameter readback; 100m/500m limits, ACK+Guided+target, HOLD and output OFF, bounded retry/queue, no resume after session loss |
| Balance | Versioned status, challenge/sequence/age and one-use request, disarmed/still guards, bounded motor pulse, physical direction consent, expiring dispatch, Stop and boot/session invalidation |
| FPV / exported Activity / USB permissions | Current USB bus/grant revalidation, no trusted attachment extras alone, one active generation, ambiguous device rejection, rate-limited retry, no implicit permission grant or restored driving authority |
| Updates / permissions | HTTPS GitHub URL allowlist, 1MiB metadata bound, APK size/hash/package/signer/increasing-code validation; Android installer approval retained; no cloud credentials; backup and cleartext disabled; unnecessary audio/storage permissions removed |
| Local data / imports | Private app preferences/files, bounded photo bytes/dimensions, AtomicFile writes, bounded route/wallet/mission parsing, saved cosmetic/mission compatibility and no persisted running authority |
| UI / accessibility | Shared action palette/typography/insets, shared popup frame/scroll owner, fixed footer actions, anchored compact menu, normal+1.3 font and Light/Sepia/Dark native suite; Android-owned permission/picker/installer styles retained |

## Remaining gaps and practical limits

| Gap | Consequence / next concrete work |
| --- | --- |
| Wi-Fi/MAVLink and balancer admin are unsigned, unencrypted at application layer | Endpoint/CRC/challenges do not authenticate an on-network sender. Operate only on an isolated trusted AP with a changed firmware Wi-Fi password. A paired firmware+app authentication/replay-protection design is separate work; do not expose control UDP ports publicly. LoRa HMAC authenticates radio frames but does not encrypt them. |
| Default firmware AP passwords and local pairing key storage | Defaults are for setup; firmware configuration must provision a private password. Pairing preferences are app-private and backup-disabled, but not hardware-backed encryption. Rooted/compromised device confidentiality is not established. |
| Navigation compatibility | Existing Mini/USB LoRa/native handset-only Backpack are planning-only. New external fleet/LoRa mission firmware is not established compatible by this APK; a negotiated capability/protocol and integration tests are needed before unlocking it. |
| Supervised navigation | Waypoint is foreground Guided sequencing, not uploaded FC AUTO. Link/phone loss can leave HOLD/OFF unconfirmed; measured independent FC failsafe/cutoff remains required. No auto-resume is introduced. |
| Map data and location privacy | OpenFreeMap style/tiles need internet/cache and expose map requests to that provider. Local GPS/command estimates are not navigation-grade truth. Offline map-package management is not implemented. |
| Native dependencies and threat testing | UVCAndroid/MapLibre native dependencies and their transitive versions are pinned as before; this review does not establish a complete current vulnerability assessment, SBOM or supply-chain attestation. Follow-up: dependency/advisory inventory and version-specific native hardening review. |
| Resource/performance | TX executor is serialized with stale-send expiry but its Java task queue is not globally bounded. Per-message protocol queues/payloads are bounded. Route history can reach100k commands and is serialized at lifecycle save; long-session performance and bounded executor admission deserve a dedicated measured change. |
| Android compatibility | API35 emulator covers software UI behavior, not all API26–35/OEM phones, system keyboard sizes or extreme font/display settings. Physical USB/UVC/LoRa and power/network churn remain unmeasured. |
| Physical balance/motor | No motor supply/current/direction/stopping measurement, PID/torque tuning or physical balancing evidence. A reported test PWM is not proof of movement. |

No force-arm/pre-arm bypass, PWM increase, firmware change, new autonomous feature or stable promotion is included. UI code still has a large MainActivity; AppUi centralizes repeated actions, while a wholesale architecture rewrite is deferred to avoid changing control ownership during this fix.

## Verification gates

Final workflow/BUILD_INFO supersedes pending software gates below only after success.

| Gate | Status before final CI | Required evidence |
| --- | --- | --- |
| A — source/build | Reviewed; Android locally BLOCKED | Full JVM/debug/release lint/build and retained signer/code106; no affected ESP32 target |
| B — behavior | Regression added; final JVM/native pending | Consent reconnect/reboot/readiness; MAVLink flags/components/length/truncation/recipient; duplicate/early update broadcast; existing control/mission/game tests |
| C — regression | Local host/mini/20 profiles/balancer PASS | Default ASan/UBSan in CI; local leak scanner cannot inspect /proc tasks; package/core sync pass |
| D — software integration | Pending full native suite | API35 navigation, popup bounds/transition, normal+large text/three themes, mission/Controls/FPV regression |
| E — HIL | BLOCKED | No attached phone/rover; execute procedure below |
| F — delivery | Pending | Exact source, APK, evidence, guide/review, SHA256, retained signer and prerelease only |

## Executable phone / restrained-wheel acceptance

1. Install over0.8.87 retaining data. Check endpoint/response, wallpaper, wallet/levels, mission draft/function mappings. Open each tab; verify a single Balance item in dropdown, no Setup shortcut, compact centered popups with all actions visible in Light/Sepia/Dark and larger font. Open/close/cancel repeatedly and rotate/background; no stale dialogs or enabled authority should remain.
2. Disconnect motor power. On balancer0.3.0, connect and open a calibration confirmation. Break/reconnect Wi-Fi or restart the board before confirming. The old confirmation must send no calibration; refresh and open a new one deliberately. Repeat for motor test/directions with wheels securely suspended.
3. On raised wheels, Enable/neutral/ARM intentionally; check separate CH1/CH2, release, STOP, app pause, lost heartbeat, Wi-Fi removal and recovery. No reconnect or dialog dismissal may enable or arm. Measure physical stop latency independently; existing4s phone heartbeat/500ms downstream thresholds are not measured stopping times.
4. Verify handset-only ELRS never exposes phone drive/ARM/live Waypoint. Check USB detach/replug/permissions and UVC preview recovery; no camera callback may restore driving.
5. On a compatible restrained ArduRover, verify actual HOLD, fresh GPS/compass/EKF/failsafe parameter readback and auxiliary OFF before any ground mission. Test link loss and transmitter/cutoff intervention; do not infer live compatibility from planning-only UI.
6. Check a real update download and installer permission. Interrupt/retry, duplicate completion and return from Settings. Only the correct package/signer/newer version can be offered. Record OEM/API, source/APK hash, observed results and remaining failures before any stable decision.

Rollback: retain source and signed0.8.87, balancer0.3.0 and user data. Prefer a forward-code rollback preserving walletv3/preferences; uninstalling to downgrade loses data. Stable channel is unchanged.

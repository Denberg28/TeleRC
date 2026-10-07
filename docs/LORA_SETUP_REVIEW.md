# LoRa setup UI and operation review

Baseline: `51e98a0797c476647dea88cbebdee6a52503c2a6` (Android 0.8.52/code 70). Review branch: `review/lora-setup-ui`. Scope: Android LoRa setup UI, USB admin presentation/confirmation, tests and documentation. No radio/firmware, control mapping, watchdog, signing or release-version changes. Rollback is the retained baseline; no board NVS/key erase is needed for this app-only review.

## Findings and changes

| Area | Finding | Reviewed change |
| --- | --- | --- |
| Key visibility | Permanently masked field with no reveal control | Show/Hide with caret preservation; hide on pause, page change and disconnect; disable view-state/autofill storage |
| Layout | Full-width ungrouped vertical widgets, uneven padding, excessive text | Centered responsive columns, compact cards, 12/10 dp card padding, 8 dp gaps, paired actions, 48 dp targets, persistent labels, expandable details |
| USB setup | Local link can connect to either role; incomplete readiness guidance | Automatic local read on LoRa-page connect; show BASE/ROVER; distinguish radio active, USB connected and fresh rover heartbeat |
| Saving | Phone prefs updated before board success | Persist only the sent draft after SAVED_RESTART_BOARD; failed/unknown replies never persist it |
| Admin exchanges | No waiting timeout; stale reply can mislead | One pending exchange; 3-second timeout; require reconnect after timeout; clear state when USB session changes |
| Board state | Save allowed without reading role/activation | Save enabled only after current-session inactive-board read; reject active boards; restart requirement survives subsequent reads |
| Pairing | Easy to overwrite key between boards | Confirm draft replacement; show non-secret CRC16 comparison hint; never read key back from board |
| Errors | Cable/permission/open errors hard to distinguish | Explicit permission, native USB discovery, multiple-board and serial-app guidance |

## Alignment with Meshtastic

Use its connection-first setup order, separate device/radio/key controls, read actual local state, save then verify, and distinguish local device connection from remote communication. Sources: official [initial configuration](https://meshtastic.org/docs/getting-started/initial-config/), [LoRa settings](https://meshtastic.org/docs/configuration/radio/lora/) and [channel configuration](https://meshtastic.org/docs/configuration/radio/channels/).

TeleRC is a direct authenticated control protocol, not Meshtastic firmware or a mesh. It provisions permitted numeric frequency/power and a 32-byte HMAC key. The radio profile is fixed for existing response timing. Do not expose pretend region/preset/QR/BLE controls that the current firmware cannot apply. Meshtastic's channel PSK/encryption model is distinct from TeleRC's authentication-only key. The 16-bit displayed fingerprint can collide; matching fingerprints alone do not prove equal keys or an authenticated working rover link.

## Acceptance and evidence

| Gate | Local status | Evidence / required next check |
| --- | --- | --- |
| A — source/build | BLOCKED locally | Motor core sync and diff checks PASS; JDK 17 available; SDK absent; Gradle download network unreachable. Run Android CI on the review commit. |
| B — behavior | BLOCKED pending JVM/device | Add setup exchange and reply/fingerprint tests; require Android CI and physical USB checks. |
| C — regression | PASS for host subset | All 15 C++ executions and 4 Python tests PASS; Android tests/build/lint pending CI. |
| D — integration | BLOCKED | Firmware protocol unchanged; actual USB BASE/ROVER and Android integration untested. |
| E — HIL | BLOCKED | No connected phone/radios/motor hardware. Perform procedure below. |
| F — delivery | Review source only | No APK release created. Version/signing unchanged. Draft PR CI and device checks precede release packaging. |

Host ASan/UBSan with `ASAN_OPTIONS=detect_leaks=0 SANITIZE=1` PASS. Default LeakSanitizer cannot inspect this sandbox's process tasks and fails before useful leak results; leak detection remains BLOCKED locally. Preserve default sanitizers in CI. Host stubs do not establish board or physical behavior.

## Device checks (motor battery disconnected for provisioning)

1. Open LoRa tab in light/dark themes, narrow landscape/split screen and large font. Check centered equal columns or narrow stacked cards, labels, keyboard reachability, 48 dp actions, scrolling, cutouts and no clipped key/Show control. Repeat after returning from another tab.
2. Paste a test key, place caret, Show then Hide; content/caret stay unchanged. Show then background/app-switch, navigate away, disconnect and return: masked. Generate over an existing draft: cancel preserves it; replace changes only draft. No keys in logs/screenshots/evidence.
3. Attach none, a charge-only cable, unsupported adapter and multiple boards; verify specific guidance and no control. Deny/grant permission; retry. Detach while pending permission or during a read/save; no stale-session ACK applies to a new board.
4. Attach a blank inactive BASE: auto-read shows role, chip, local frequency/power/fingerprint/counters without a rover heartbeat. Save accepts valid values; invalid frequency/power/key leaves the board unchanged. Phone prefs persist only on successful ACK; cancel/timeout/storage failure leaves previous confirmed prefs intact.
5. Suppress a reply past 3 seconds, then deliver it: no Save success or draft persistence. Read/Save stay blocked until disconnect/reconnect. Restart a successfully saved board, reconnect and read active state. Read alone after Save must not falsely clear restart requirement.
6. Provision inactive ROVER separately with the same confirmed draft. Its USB connection never shows rover-ready or enables the Controls shortcut. Restart it; compare both frequency/power/fingerprint; no key is read back.
7. Reattach BASE and power the motor MCU with raised wheels. With absent/incorrect partner key/frequency or lost heartbeat, Controls shortcut stays unavailable. Correct radio/rover heartbeat enables shortcut; control/ARM never enables automatically. Test neutral, explicit Enable/ARM and loss/recovery with `docs/TESTING.md`.
8. Confirm active boards reject Save, ordinary Wi-Fi setup still works, USB persists across tabs, and phone restart restores only last board-confirmed pairing values. No paired firmware upgrade is required.

Next concrete check: run Android CI against the review commit, then visual/USB checks 1–6 on the user's phone before requesting a signed candidate release.

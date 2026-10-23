# TeleRC 0.8.57 — independent sensitivity and expo

Install the signed **TeleRC-v0.8.57.apk** update over 0.8.56; keep app data and the same signing identity. The full interface, Mini rover Wi-Fi, Wi-Fi bridge/FC and LoRa USB choices remain available. **No firmware upload or wiring change is needed.** Existing mini 1.2.2 and other installed compatible firmware retain their limits and safety behavior.

## Use

Open **Setup → THEME & CONTROL FEEL → Control response**. Adjust the three sliders, then tap **Apply**. Cancel discards the draft. The Setup summary shows all three percentages. Settings persist across app restart and apply to both Controls and Game/Test joysticks. Test remains a simulation, not a live-control path.

| Slider | Range | Effect |
| --- | --- | --- |
| Steering sensitivity | 25–100% | Limits CH1 displacement around 1500 µs independently |
| Drive sensitivity | 25–100% | Limits CH2 forward/reverse displacement independently |
| Expo | 0–100% | Softens both axes near center while preserving each sensitivity's full-stick endpoints |

Higher expo means gentler center response. It is a response curve, not a time delay, acceleration ramp or speed feedback controller. At 0% response is linear outside the existing 8% stick deadband. Maximum travel stays 1100–1900 µs at sensitivity 100%, or 1400–1600 at 25%. Neutral and release are always 1500. High expo plus low sensitivity can produce a small command below a motor's starting torque; reducing expo or increasing the appropriate sensitivity changes that command without bypassing the driver's own limits.

The curve uses `y = (1 − e) × x + e × x³`, where `e` is Expo/100 and `x` is the signed clamped assigned axis. The existing deadband runs first, then expo, then the axis's sensitivity. Example at half-stick and 100% sensitivity: 0% expo → 1700 µs, 50% → 1625, 100% → 1550. Full stick remains 1900 at all three values; the opposite direction mirrors around neutral (integer conversion can differ by 1 µs).

## Upgrade and compatibility

Both new sensitivities inherit the old shared sensitivity on first upgrade; expo defaults to **0%**, so upgrading does not increase either axis or alter the old curve. Existing independently saved values are not overwritten by the legacy key. Invalid persisted percentages are bounded, and non-finite math input yields neutral. The old shared key remains for source/Android rollback compatibility.

Control settings are edited from Setup, which disables live authority before entry. Apply saves the three values together. Newly created Controls/Test sticks use that saved snapshot. Re-enter Controls and explicitly Enable Control as usual; changing settings never enables or arms. ARM/DISARM, CH1/CH2 mapping, STOP/release, lifecycle neutralization and transport watchdogs are unchanged. Expo cannot defeat a firmware deadband, power/current ceiling, failsafe or receiver takeover.

This APK uses application ID `io.github.denberg28.telerc`, version code **75**. Automated packaging must verify signer continuity against 0.8.56/code74 before publishing. Android normally blocks downgrade to code74; uninstalling loses app data. Export routes first if a downgrade is necessary. Firmware rollback is unnecessary for this app-only update.

## Raised-wheel phone checks

1. Install over 0.8.56, confirm your saved shared sensitivity becomes both new values and expo is 0%. Change steering to 25%, drive to 100%, expo to 0%; Apply, reopen the dialog, then restart the app and verify persistence. Cancel a different draft and verify it was not saved.
2. With wheels raised, cutoff ready, connect the intended control link. Enter Controls, Enable and ARM only when required by the installed rover. Full steering should report CH1 1400/1600; full drive CH2 1100/1900. Changing drive sensitivity must not alter steering output, and vice versa. Confirm off-axis movement remains neutral.
3. Set both sensitivities to 100%, expo to 50%. At roughly half stick, output should be closer to neutral than at 0% expo; full stick still reaches endpoints. At 100% expo center is softest. Test forward/reverse and left/right, release/cancel, then STOP/DISARM and loss/recovery. No automatic enable/rearm after return.
4. Confirm Game/Test reflects the saved response while remaining unable to command motors. Check dialog scrolling, labels, slider touch targets and Apply/Cancel on a short landscape display, large font, and light/dark themes. Returning from Setup must not reuse held stick input.

## Evidence and limits

Required software evidence: unit tests for migration/independence/expo endpoints/monotonic bounds/invalid input, debug and release lint/build, host regression and default sanitizers, production shaped MAVLink packets through the actual mini parser, package/version/signer checks, archive CRC and SHA-256. Firmware source is unchanged from its prior tested releases. Physical installation, UI appearance/accessibility on a device, actual Wi-Fi/LoRa/FC and motor HIL remain pending until measured. This is a signed software test candidate, not a new claim of field validation.

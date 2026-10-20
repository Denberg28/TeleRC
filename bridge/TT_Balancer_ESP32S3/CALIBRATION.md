# Saved gyro calibration — v0.2.7

No APK update is required. Upload the complete sketch folder using the same
ESP32-S3 settings. Preferences is part of the Espressif board package.

| Situation | Expected CAL diagnostic | Action |
| --- | --- | --- |
| First successful full calibration | `CAL_READY store=SAVED n=600` | Offset is saved automatically; complete direction verification before arming |
| Later power-on/reset | `CAL_SAVED_READY store=REUSED n=150` | Brief stillness check completed using the saved bias |
| Storage write failed | `CAL_READY store=SAVE_FAILED` | Live calibration works this boot, but persistence did not succeed |
| Movement occurred earlier, latest partial window is valid | `CAL_COLLECTION_TIMEOUT lastReject=CAL_GYRO_MOVEMENT_OR_BIAS` | Stillness window was incomplete after 15 seconds; latest sample may be valid |
| Forced recalibration | Send `c` while disarmed and unpaired | Clears record and reboots; hold upright and still for full calibration |

CAL fields have other values between these shown fields. Normal reuse takes
about 0.6 seconds of sampling, in addition to sensor/network startup. Valid
movement/tilt resets the short window within a 5-second budget. Freshness,
nonfinite, clipping, bus, gravity, gyro, variance and tilt-span checks remain.
The stored offset must match a fresh stable gyro mean within 1 degree/s. A
stable mismatch triggers full calibration; other failed checks fault without
rewriting the record. Full calibration still needs all 600 new accepted samples.

The persisted value is the robot-frame pitch gyro bias, not physical upright,
accelerometer offsets, angle, PID gains, remote commands or an armed state.
Only the pitch gyro is integrated by this controller. Its stored record contains
a schema version, mounting/pin/sensor context and checksum. Corrupt/unknown records
are rejected. A replacement sensor with the same ID cannot be uniquely detected;
force recalibration after replacement. Temperature changes can alter gyro bias;
recalibrate if drift is observed, even when the short check passes.

Flash writes occur after a successful full calibration or explicit erase, never
on each control sample or ordinary reuse. SAVE_FAILED does not turn good live
calibration into a sensor fault. Do not assume the record was saved unless the
CAL storage status says SAVED or REUSED. A failed calibration never overwrites
a previously saved record.

The `c` command is an explicit software restart after erasing the record. Motor
output stays off. It is permitted when DISARMED, or from a failed startup
calibration after MPU initialization succeeded. It is rejected when a peer owns
the link, GPIO12 STOP is active, or motor/arming/balancing/runtime-fault state is
active. Serial `x` takes precedence over `c` in the same command batch. A failed
flash erase does not reboot. Reset/USB reconnection alone preserves the record;
full flash erase removes it. Ordinary upload preservation depends on IDE erase
settings/partition layout. Boot always remains DISARMED.

Implementation reference: official Arduino-ESP32 Preferences documentation:
https://docs.espressif.com/projects/arduino-esp32/en/latest/api/preferences.html

This source has host tests, not a completed ESP32 target build or physical test.

# MPU startup diagnosis — v0.2.6

Your v0.2.3 diagnostic reports `ack68=1 read68=1 who68=0x70 err68=0`.
The sensor acknowledged and returned its identity. ID 0x70 selects an MPU6500-compatible register profile;
the old MPU6050-only driver rejected it. This does not establish board provenance
or sensor health. v0.2.5 adds the MPU6500 register profile, readback checks,
post-reset identity confirmation and normal calibration/fault requirements.
Keep the existing wiring and 3.3 V-compatible sensor supply arrangement.

## Get the precise result

Disconnect motor power, upload v0.2.7, open Serial at 115200 baud and reset.
A bounded diagnostic line is emitted each second, rotating between `CAL`,
`SAMPLE` and `MPU`. Each appears approximately every three seconds. Copy all three.
Wi-Fi remains available during a failed sensor check; fault/arming guards remain.

| Stage | Meaning / next action |
| --- | --- |
| MPU_NO_I2C_ACK | Neither 0x68 nor 0x69 acknowledged. Check module power, common GND, actual GPIO8 SDA and GPIO9 SCL, connections/pull-ups and the board pinout |
| MPU_WHO_READ_FAILED | An address acknowledged but register 0x75 could not be read completely; check power/bus signal quality and the error/count fields |
| MPU_UNSUPPORTED_CHIP_ID | The register was readable but no responding device identified as a supported MPU6050/MPU6500. Send the who68/who69 values and a clear photo of the board/IC markings |
| MPU_ADDRESS_CONFIG_MISMATCH | An alternate supported sensor exists but automatic address selection was disabled; correct MPU_ADDRESS or enable MPU_AUTO_ADDRESS |
| MPU_RESET_WRITE_FAILED | Chip identified but the reset register write failed; inspect bus/power stability |
| MPU_CONFIG_WRITE_FAILED / MPU_CONFIG_READ_FAILED | Configuration transaction failed; `reg`, `bus`, `wanted` and `got` identify the failing operation |
| MPU_CONFIG_READBACK_MISMATCH | Up to three writes/readbacks disagreed. Report `reg`, `expected`, `actual`; do not bypass the check |
| MPU_READY | Expected identity and configuration succeeded. Gyro calibration/physical orientation and direction verification are separate checks |
| MPU_SAMPLE_READ_FAILED | A later sensor read failed. The controller faults rather than scanning/resetting the sensor during balance |

`ack68`/`ack69` indicate address acknowledgements. `read68`/`read69` indicate a
successful WHO_AM_I read. A `who=0x00` value with `read=0` is not a measured chip ID.
`err68`/`err69` and `bus` are Wire return codes; `wanted`/`got` are byte counts.
`expected`/`actual` are configuration comparison fields, meaningful for a
configuration-readback failure. `selected` shows the configured/preferred address
on failure and the active sensor address once one is selected.

The I2C address is 0x68 (AD0 LOW) or 0x69 (AD0 HIGH). It is independent
of chip identity: WHO_AM_I is 0x68 for MPU6050 or 0x70 for MPU6500. Only these
two profiles are supported. Other IDs remain inhibited. `model` and `id` report
the selected profile; the profile is verified again after reset.
`MPU_IDENTITY_CHANGED_AFTER_RESET` requires checking sensor/bus stability.

Expected sensor line for the reported hardware:
`MPU stage=MPU_READY profile=MPU6500_COMPAT id=0x70 selected=0x68 ...`
This proves configuration success only. Complete gyro calibration and direction
checks separately before arming. A `verified=0` lock is expected until directions
are checked; it must not be bypassed to resolve a sensor failure.

## Wiring and power checks, with power removed before changes

| MPU breakout | ESP32-S3 |
| --- | --- |
| SDA | GPIO8, not physical header position 8 |
| SCL | GPIO9, not physical header position 9 |
| GND | Common GND |
| AD0 | GND for the preferred 0x68 address |
| VCC | Appropriate input for the actual breakout; the original guide assumes a 3.3 V-compatible board |

INT/XDA/XCL remain disconnected. Make sure SDA/SCL pull-ups go to 3.3 V, not 5 V.
Keep I2C wires short and away from motor leads. Check solder joints and connector
contact; verify GPIO8/9 are available on your actual ESP32-S3 board.

The bare MPU6050 chip requires 2.375–3.46 V at VDD. Some breakout boards have an
onboard regulator and accept a higher VIN; others do not. Feeding a regulator
from 3.3 V can leave its output too low on some modules. Identify the board and
measure its sensor supply rail before changing input voltage. Do not connect
5 V directly to bare-chip VDD or ESP32 I2C signals. A board photo helps identify
the correct power arrangement.

The firmware starts at MPU_I2C_HZ=400000. If measurements show a bus/wiring
problem, MPU_I2C_HZ can be set to 100000 for diagnosis; the existing read-time
limits remain enforced and actual control timing must be rechecked. A lower
clock does not repair missing power, swapped wires or an unsupported chip.

After resolving a fault, reset while holding the robot upright/still. Address
selection occurs only at startup. There is no automatic in-motion recovery.
Complete calibration and direction checks before arming (see ARMING.md).

## v0.2.5 calibration and sample diagnostics

Your module is labeled MPU6050. WHO_AM_I=0x70 is a reported register identity;
it does not prove physical chip model, authenticity or correct scale response.
`profile=MPU6500_COMPAT` describes the driver settings selected from that ID.
WHO_AM_I=0x68 selects MPU6050_COMPAT. Keep existing wiring and power unchanged.
Do not force an identity value to clear the fault.

A successfully configured sensor can still fail calibration. The old
`calibration: reset upright/still` message did not reveal which requirement
failed. v0.2.5 retains all original limits and reports the first failed check:

| CAL stage | Requirement / next check |
| --- | --- |
| CAL_ACCEL_MAGNITUDE | Acceleration norm must be 0.90–1.10 g. Check measured norm while stationary; do not assume movement is the only cause |
| CAL_Z_NOT_UP | az must be at least +0.94 g. Check actual axis arrows and mounting; a vertical board may fail |
| CAL_SIDE_TILT | Absolute ay must be at most 0.15 g |
| CAL_FORWARD_TILT | Absolute forward angle must be at most 10 degrees |
| CAL_GYRO_MOVEMENT_OR_BIAS | Every gyro axis must be within +/-8 degrees/s; a failed check can also reflect bias or bad data |
| CAL_NO_FRESH_SAMPLE | No ready sample within the existing freshness deadline |
| CAL_COLLECTION_TIMEOUT | Fewer than 600 accepted samples within 15 seconds; lastReject reports earlier rejection |
| CAL_GYRO_VARIANCE | Y gyro variance exceeded 0.25 (degrees/s)^2 |
| CAL_TILT_VARIATION | Measured tilt span exceeded 1.5 degrees |
| CAL_NONFINITE | A measurement/derived value was not finite |
| CAL_STOP_SWITCH | Stop input is active |
| CAL_READY | Startup gyro calibration completed; separate arming/direction checks remain |

`valid` means the CAL sample fields contain a successfully decoded sample.
They preserve the latest decoded calibration sample; for a later bus/clipping
failure, use the SAMPLE record for the failing operation. `n` is the accepted
count; `angle` in the ordinary status stays zero until successful calibration.
`variance`/`span` are meaningful only after collecting all 600 samples.

SAMPLE stages distinguish STATUS_BUS_ERROR, FRAME_BUS_ERROR, ALL_ZERO,
ALL_FF, AXIS_CLIPPED, WAITING and VALID (each prefixed SAMPLE_).
`rawValid=1` means a complete raw frame was captured, not that it passed validation.
Raw counts are AX,AY,AZ,GX,GY,GZ. `clippedAxis` is 0..5 in that order, or -1
when no axis clipped. Scaling is 16384 counts/g and 131 counts/(degrees/s)
for the configured ranges. No raw-valid flag is set for a failed bus read.
Snapshots freeze on a latched fault; there is no in-motion scan/reset/recovery.

Test with motor power disconnected. Mount the sensor with its marked +Z axis
up, +X forward and +Y left when the chassis is upright; check the actual PCB
arrows rather than assuming the component side defines the axes. Hold still
at reset, wait at least four seconds after the fault and send CAL/SAMPLE/MPU
lines. They will distinguish mounting, movement/bias, scaling and data failures.

## v0.2.6 settling and mounting

A moving or tilted but valid sample resets the entire calibration collection
and waits for upright/still within the full-calibration budget (15 seconds in v0.2.7). It no
longer immediately latches the first mounting/movement failure. All 600 new
samples, the variance/span limits, and all other safety limits still apply.
Persistent tilt/movement still faults. Bus failures, invalid/clipped raw data,
missing fresh samples and the stop switch still abort immediately. A sample
window that finishes with excessive variance or span is still rejected.

`CAL` values are in the configured robot frame; `SAMPLE AX/AY/AZ/GX/GY/GZ` remain
raw sensor counts. `mount=0` uses sensor +X forward/+Y left/+Z up. `mount=1` uses
sensor +Y forward/-X left/+Z up and transforms both gyro and accelerometer.
Choose from physical board arrows and test the signs before motor tests.
See REFERENCE_FIX.md for comparison with the linked repository.

## v0.2.7 persistence

See CALIBRATION.md. CAL_SAVED_READY means saved offsets passed a fresh 150-sample
check. CAL_SAVED_CHECK_TIMEOUT means that short check could not collect its whole
window within 5 seconds. Earlier rejected motion is recorded separately as
lastReject; it is not presented as the cause of a later partial-window timeout.
A fresh full calibration still requires 600 accepted samples.

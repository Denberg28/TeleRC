# v0.2.6: use the reference to diagnose the startup fault

Reference reviewed: https://github.com/arthurlawson/self-balancing-robot-v2
Revision: 8ef439bde72eeed8028adbdb70472c1b5e2d1db4

## What the supplied log establishes

`MPU_READY`, a successful 14-byte read, and `id=0x70` establish successful
initialization using the selected compatibility profile. The final
`calibration: reset upright/still` reason in v0.2.4 locates the immediate failure
in calibration, rather than centered RC controls. It does not identify the exact
calibration condition. v0.2.5 added the measured CAL/SAMPLE diagnostics.
A breakout sold as MPU6050 is not proven to contain a particular chip by its
label; this firmware reports the observed ID without overriding it.

## What was learned from the reference

- Its IMU service uses Adafruit MPU6050, the X gyro and accelerometer Y/Z for
  roll, and a negative reported roll for its mounting. TeleRC originally used
  the Y gyro and accelerometer -X/Z for pitch. These are different mountings.
- Its driver is DRV8833, control is ESP-NOW and its custom PCB uses SDA12/SCL11.
  TeleRC uses MX1508, Wi-Fi/MAVLink and SDA8/SCL9. Do not flash it unchanged.
- Its gains, filter settings and automatic activation depend on its chassis.
  They have not been copied into this firmware. No Kalman filter migration is
  needed to fix an initialization/calibration rejection.
- Adafruit MPU6050's begin() checks WHO_AM_I against its MPU6050 identity and
  returns false on mismatch. Substituting it would reject the reported 0x70 ID.

Source files:
https://github.com/arthurlawson/self-balancing-robot-v2/blob/main/firmware/robot_firmware/src/services/imu_service/imu_service.cpp
https://github.com/arthurlawson/self-balancing-robot-v2/blob/main/firmware/robot_firmware/src/services/imu_service/imu_service.h
https://github.com/arthurlawson/self-balancing-robot-v2/blob/main/firmware/robot_firmware/include/config.h
https://github.com/adafruit/Adafruit_MPU6050/blob/master/Adafruit_MPU6050.cpp

The repository has a non-commercial license. These changes are independently
implemented calibration-window handling and coordinate transformation; no
reference source code is bundled or transplanted.

## Apply this candidate

1. Disconnect motor power. Upload TT_Balancer_ESP32S3.ino with Espressif
   Arduino-ESP32 3.x, then reset and open Serial at 115200.
2. Keep the chassis upright/still immediately after reset. The existing
   5-second budget must contain a full new 600-sample stable window (~2.4s).
   A transient tilt/movement restarts the collection instead of immediately
   latching a fault. Persistent movement or tilt still fails.
3. Check CAL stage=CAL_READY and DISARMED. If faulted, retain the full CAL,
   SAMPLE and MPU lines; these determine the next correction. Reset is still
   required after a latched fault. Never force the ID or bypass readiness.
4. With motors off, gently tip forward/back: angle and rate must show the
   same sign while rotating (positive forward). Default BALANCER_IMU_MOUNT=0
   requires sensor +X forward/+Y left/+Z up. If physically mounted with sensor
   +Y forward/-X left/+Z up, set BALANCER_IMU_MOUNT=1 in Config.h and re-upload.
   This uses negative X gyro and negative accelerometer roll, matching the
   reference mounting convention. Z must still point up. Neither preset fixes
   an upside-down or vertically mounted sensor; remount it.
5. Complete the suspended-wheel t -> inspect both wheels -> v sequence in
   ARMING.md before TeleRC arming. No APK update is required for these changes.

## Validation boundary

Host tests exercise the actual sketch with deterministic platform/sensor fakes,
including both mountings, motor output signs, transient and persistent startup
movement, identity profiles, invalid data, bus faults and the existing control
and network gates. They do not establish real sensor timing, board compilation,
electrical authenticity, Wi-Fi scheduling, correct wiring or physical balance.
An ESP32 toolchain is unavailable here; this is a source test candidate.

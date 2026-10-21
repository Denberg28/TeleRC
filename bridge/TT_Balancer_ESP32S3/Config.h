#pragma once
#include <stdint.h>

// ESP32-S3 DevKit pin numbers are GPIO numbers. Check your board's pinout.
constexpr uint8_t LEFT_IN1 = 4, LEFT_IN2 = 5;
constexpr uint8_t RIGHT_IN1 = 6, RIGHT_IN2 = 7;
constexpr uint8_t MPU_SDA = 8, MPU_SCL = 9;
constexpr uint8_t STOP_PIN = 12; // Normally-open stop switch: GPIO12 -> GND.
constexpr uint8_t MPU_ADDRESS = 0x68; // AD0 -> GND; use 0x69 for AD0 -> 3V3.
constexpr bool MPU_AUTO_ADDRESS = true; // Probe 0x68/0x69; MPU6050 ID=0x68, MPU6500 ID=0x70.
constexpr uint32_t MPU_I2C_HZ = 400000; // Try 100000 only for a measured wiring/bus problem.
static_assert(MPU_ADDRESS==0x68 || MPU_ADDRESS==0x69, "MPU address must be 0x68/0x69");

// Direction confirmation is optional for commissioning, but wheel/IMU polarity MUST be physically checked before powered balancing.
// 0 skips only the manual t->v arming prerequisite; all physical/link/fault checks remain.
#ifndef BALANCER_REQUIRE_DIRECTION_CONFIRMATION
#define BALANCER_REQUIRE_DIRECTION_CONFIRMATION 0
#endif
static_assert(BALANCER_REQUIRE_DIRECTION_CONFIRMATION == 0 || BALANCER_REQUIRE_DIRECTION_CONFIRMATION == 1, "Invalid direction confirmation policy");
constexpr bool REQUIRE_DIRECTION_CONFIRMATION = BALANCER_REQUIRE_DIRECTION_CONFIRMATION == 1;

// Set to 1 ONLY after suspended-wheel direction/axis checks.
// Or leave 0 and use Serial t -> inspect directions -> v to confirm for this boot.
#ifndef BALANCER_DIRECTIONS_VERIFIED
#define BALANCER_DIRECTIONS_VERIFIED 0
#endif
constexpr bool DIRECTIONS_VERIFIED = BALANCER_DIRECTIONS_VERIFIED == 1;

// Positive output must propel BOTH wheels toward the robot's front.
// Mirrored motors commonly require opposite signs. Verify your own wiring.
constexpr int LEFT_SIGN = +1;
constexpr int RIGHT_SIGN = -1;

// Sensor mounting: 0 = +X forward, +Y left, +Z up (existing wiring/mount).
// 1 = +Y forward, -X left, +Z up (X-gyro/roll mounting, as in the reference).
// Both accelerometer AND gyro are transformed together. Recheck t/v after edits.
#ifndef BALANCER_IMU_MOUNT
#define BALANCER_IMU_MOUNT 0
#endif
static_assert(BALANCER_IMU_MOUNT==0 || BALANCER_IMU_MOUNT==1, "Choose IMU mount 0 or 1");
// Robot axes after transformation: +X forward, +Y left, +Z up.
// Positive pitch and gyroY mean falling forward. Remount if axes differ.
constexpr float KP = 18.0f;  // PWM / degree. Starting values, not tuned gains.
constexpr float KI = 0.0f;   // PWM / (degree * second). Start at zero.
constexpr float KD = 0.70f;  // PWM / (degree / second).
constexpr float TRIM_DEG = 0.0f; // Positive trim requests a forward lean.
constexpr float INTEGRAL_LIMIT = 25.0f; // PWM contribution, anti-windup.
constexpr int MAX_PWM = 160; // 0..255. Initial bench cap, not current limiting.
constexpr int MIN_PWM = 0;   // Optional measured friction compensation: 0..MAX_PWM.
// Suspended-wheel diagnostic only. Never inject this pulse into balancing.
constexpr int TEST_PWM = 140;
constexpr uint32_t TEST_MS = 300;
constexpr uint32_t PWM_HZ = 20000;
constexpr uint32_t PERIOD_US = 4000; // 250 Hz.
constexpr float FILTER_TAU = 0.35f; // Complementary filter time constant, seconds.
constexpr float FALL_DEG = 30.0f;
constexpr float ARM_DEG = 4.0f;
constexpr float ARM_RATE_DPS = 10.0f;
constexpr uint32_t UPRIGHT_MS = 1000;
constexpr uint32_t NO_SAMPLE_US = 12000;
constexpr uint32_t CALIBRATION_TIMEOUT_MS = 15000; // Complete fresh ~2.4s window after settling.
constexpr uint16_t SAVED_CHECK_SAMPLES = 150; // ~0.6s; motor output remains off.
constexpr uint32_t SAVED_CHECK_TIMEOUT_MS = 5000;
constexpr float SAVED_BIAS_RESIDUAL_DPS = 1.0f; // Recalibrate if stored bias drifted.
constexpr uint32_t SATURATION_MS = 300;
// TeleRC legacy Wi-Fi/MAVLink endpoint. Change password before field use.
constexpr char WIFI_SSID[] = "TeleRC-Balancer";
constexpr char WIFI_PASSWORD[] = "telerc123";
constexpr uint16_t UDP_PORT = 14550;
constexpr uint8_t SYSTEM_ID = 1, COMPONENT_ID = 1;
constexpr uint32_t CONTROL_TIMEOUT_MS = 350, PEER_TIMEOUT_MS = 3500;
constexpr uint32_t CENTER_HOLD_MS = 250;
constexpr float MAX_LEAN_DEG = 2.0f; // Full drive stick requests this lean, not PWM.
constexpr float MAX_YAW_PWM = 28.0f;
constexpr float LEAN_SLEW_DPS = 10.0f;
constexpr float YAW_SLEW_PWM_PS = 240.0f;
constexpr int RC_DEADBAND_US = 25, RC_FULL_SCALE_US = 400;
static_assert(sizeof(WIFI_PASSWORD)>=9 && sizeof(WIFI_PASSWORD)<=64, "Use 8..63 password characters");
static_assert(MAX_LEAN_DEG>0 && MAX_LEAN_DEG<=4, "Keep initial drive lean small");
static_assert(MAX_YAW_PWM>=0 && MAX_YAW_PWM<MAX_PWM, "Reserve balance authority");
static_assert(MAX_PWM > 0 && MAX_PWM <= 255, "Invalid PWM cap");
static_assert(MIN_PWM >= 0 && MIN_PWM <= MAX_PWM, "Invalid minimum PWM");
static_assert(TEST_PWM > 0 && TEST_PWM <= MAX_PWM, "Invalid test PWM");
static_assert(TEST_MS >= 100 && TEST_MS <= 500, "Keep motor test bounded to 100..500 ms");
static_assert(LEFT_SIGN * LEFT_SIGN == 1 && RIGHT_SIGN * RIGHT_SIGN == 1,
              "Motor signs must be +1 or -1");

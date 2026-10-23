#pragma once
// Header declarations also avoid Arduino sketch auto-prototype ordering issues.
enum class Mode { DISARMED, ARMING, BALANCING, MOTOR_TEST, FAULT };
struct Sample { float ax, ay, az, gx, gy, gz; };

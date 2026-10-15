#pragma once
#include <stdint.h>
#include "HardwareProfile.h"
namespace config {
constexpr char VERSION[]="1.2.1-mx1508-diagnostics";
constexpr char WIFI_SSID[]="TeleRC-MiniRover";
constexpr char WIFI_PASSWORD[]="telerc123";
constexpr uint16_t UDP_PORT=14550;
constexpr uint8_t SYSTEM_ID=1, COMPONENT_ID=1;
constexpr uint8_t GCS_SYSTEM_ID=255, GCS_COMPONENT_ID=190;
constexpr uint32_t CONTROL_TIMEOUT_MS=500, PEER_TIMEOUT_MS=3000;
constexpr uint32_t NEUTRAL_HOLD_MS=250, MOTOR_TICK_MS=5, REVERSAL_COAST_MS=30;
constexpr uint8_t MAX_PWM=180;
constexpr int RC_DEADBAND_US=25;
// TeleRC APK commands 1100..1900, not 1000..2000 at full stick.
constexpr int RC_FULL_SCALE_US=400;
constexpr float STEER_GAIN=0.50f;
constexpr int BOOT_STOP_PIN=0, STATUS_LED_PIN=21;
constexpr bool MOTOR_INVERT[4]={false,false,false,false};
constexpr uint8_t MOTOR_TRIM_PERCENT[4]={100,100,100,100};
constexpr uint8_t MOTOR_DUTY_CAP[4]={180,180,180,180};
static_assert(sizeof(WIFI_PASSWORD)>=9 && sizeof(WIFI_PASSWORD)<=64,"Password needs 8..63 characters");
static_assert(STEER_GAIN>0 && STEER_GAIN<=1,"Invalid steer gain");
static_assert(RC_DEADBAND_US>=0 && RC_DEADBAND_US<RC_FULL_SCALE_US && RC_FULL_SCALE_US<=500,"Invalid RC scaling/deadband");
static_assert(MOTOR_TRIM_PERCENT[0]<=100 && MOTOR_TRIM_PERCENT[1]<=100 && MOTOR_TRIM_PERCENT[2]<=100 && MOTOR_TRIM_PERCENT[3]<=100,"Trims may only reduce duty");
}

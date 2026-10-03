/*
  TeleRC Direct Drive - ESP32-S3 -> 4x BTS7960
  Target: ESP32-S3 DevKitC-1 style boards

  PURPOSE
  -------
  Run a basic four-motor skid-steer rover without an ArduPilot flight controller.

    TeleRC Android APK / PC TeleRC
              |
           Wi-Fi UDP
              |
           ESP32-S3
              |
         4x BTS7960
              |
           4 motors

  COMPATIBILITY
  -------------
  - TeleRC UDP endpoint: 192.168.4.1:14550
  - Discovery: TELERC_DISCOVER_V1
  - Disconnect: TELERC_DISCONNECT_V1
  - MAVLink 1 source expected from TeleRC: sysid 255 / compid 190
  - Steering: CH1
  - Bidirectional drive: CH2
  - Generates a minimal MAVLink rover HEARTBEAT so existing Android and PC
    TeleRC builds can establish a healthy vehicle link without an F405.
  - Accepts normal MAV_CMD_COMPONENT_ARM_DISARM and returns COMMAND_ACK.

  SAFETY
  ------
  - Motors are DISARMED at boot.
  - Both CH1 and CH2 must be actively owned before any motor command is applied.
  - 500 ms command watchdog immediately stops and disarms the rover.
  - Wi-Fi client loss and explicit TeleRC disconnect immediately stop/disarm.
  - Normal neutral commands use slew-limited deceleration.
  - Direction reversals must pass through zero.
  - Motor outputs initialize at zero.
  - Use a physical motor-power emergency stop / disconnect.
  - First test with wheels raised and motor current limited where practical.

  BTS7960 LOGIC
  --------------
  This sketch drives RPWM/LPWM only. Keep BTS7960 R_EN/L_EN enabled from a
  suitable 5 V logic supply as in the existing TeleRC converter wiring.
  ESP32, BTS7960 logic and power-system signal ground must share a reference.
  Do not route motor current through ESP32 ground wiring.
*/

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <Preferences.h>
#include <esp_system.h>
#include <esp_arduino_version.h>
#include <cstring>

// -----------------------------------------------------------------------------
// USER CONFIGURATION
// -----------------------------------------------------------------------------

constexpr uint16_t UDP_PORT = 14550;
constexpr uint8_t VEHICLE_SYSID = 1;
constexpr uint8_t VEHICLE_COMPID = 1;

constexpr uint32_t CONTROL_TIMEOUT_MS = 500;
constexpr uint32_t PHONE_LEASE_MS = 5000;
constexpr uint32_t HEARTBEAT_PERIOD_MS = 1000;
constexpr uint32_t STATUS_PERIOD_MS = 1000;
constexpr uint32_t DIAG_PERIOD_MS = 500;

constexpr uint16_t RC_MIN_US = 1000;
constexpr uint16_t RC_CENTER_US = 1500;
constexpr uint16_t RC_MAX_US = 2000;
constexpr uint16_t RC_DEADBAND_US = 25;

constexpr uint32_t PWM_FREQ_HZ = 20000;
constexpr uint8_t PWM_BITS = 8;
constexpr int MAX_MOTOR_DUTY = 255;

// 5 ms loop: full acceleration is about 0.32 s at ACCEL_STEP=4.
// Deceleration is intentionally faster.
constexpr uint32_t CONTROL_PERIOD_US = 5000;
constexpr int ACCEL_STEP = 4;
constexpr int DECEL_STEP = 16;

constexpr bool INVERT_STEERING = false;
constexpr bool INVERT_DRIVE = false;

// Motor order: Front Left, Rear Left, Front Right, Rear Right.
// Change only after a raised-wheel direction test.
constexpr bool INVERT_MOTOR[4] = {
  false,
  false,
  false,
  false
};

constexpr uint8_t RPWM_PIN[4] = {8, 10, 12, 14};
constexpr uint8_t LPWM_PIN[4] = {9, 11, 13, 15};

const char AP_SSID[] = "TeleRC-Rover";
// Leave empty in Git. Set only in a private local copy if a fixed password is
// required. With an empty value, a random 16-character password is generated
// once and stored in ESP32 NVS.
const char PERSONAL_AP_PASSWORD[] = "";

const char DISCOVERY[] = "TELERC_DISCOVER_V1";
const char DISCONNECT[] = "TELERC_DISCONNECT_V1";

const IPAddress AP_IP(192, 168, 4, 1);
const IPAddress AP_BROADCAST(192, 168, 4, 255);

WiFiUDP udp;
Preferences preferences;

// -----------------------------------------------------------------------------
// STATE
// -----------------------------------------------------------------------------

IPAddress controllerIp;
uint32_t lastControllerMs = 0;
uint32_t lastControlMs = 0;
uint32_t lastHeartbeatMs = 0;
uint32_t lastStatusMs = 0;
uint32_t lastDiagMs = 0;
uint32_t lastControlUs = 0;

uint8_t mavSequence = 0;
uint8_t ownedMask = 0;  // bit0 = CH1, bit1 = CH2
uint16_t steeringUs = RC_CENTER_US;
uint16_t driveUs = RC_CENTER_US;

bool armed = false;
uint8_t previousStationCount = 0;

int targetCmd[4] = {0, 0, 0, 0};
int currentCmd[4] = {0, 0, 0, 0};

uint32_t udpRxBytes = 0;
uint32_t mavRxFrames = 0;
uint32_t acceptedCommands = 0;
uint32_t rejectedCommands = 0;
uint32_t motorUpdateCycles = 0;
uint32_t armCommands = 0;
uint32_t failsafeCount = 0;

// -----------------------------------------------------------------------------
// MAVLINK MINIMAL ENCODING / VALIDATION
// -----------------------------------------------------------------------------

uint16_t crcByte(uint16_t crc, uint8_t value) {
  uint8_t tmp = value ^ (crc & 0xff);
  tmp ^= tmp << 4;
  return (crc >> 8) ^ (uint16_t(tmp) << 8) ^
         (uint16_t(tmp) << 3) ^ (tmp >> 4);
}

bool validMavlinkV1(const uint8_t *p, size_t n, uint8_t payloadLen,
                    uint8_t msgId, uint8_t crcExtra) {
  if (n != size_t(payloadLen) + 8 || p[0] != 0xfe ||
      p[1] != payloadLen || p[5] != msgId) {
    return false;
  }

  uint16_t crc = 0xffff;
  for (size_t i = 1; i < size_t(payloadLen) + 6; ++i) {
    crc = crcByte(crc, p[i]);
  }
  crc = crcByte(crc, crcExtra);

  const uint16_t received =
      uint16_t(p[payloadLen + 6]) |
      (uint16_t(p[payloadLen + 7]) << 8);
  return received == crc;
}

size_t makeMavlinkV1(uint8_t msgId, const uint8_t *payload,
                     uint8_t payloadLen, uint8_t crcExtra,
                     uint8_t *out, size_t capacity) {
  const size_t frameLen = size_t(payloadLen) + 8;
  if (capacity < frameLen) return 0;

  out[0] = 0xfe;
  out[1] = payloadLen;
  out[2] = mavSequence++;
  out[3] = VEHICLE_SYSID;
  out[4] = VEHICLE_COMPID;
  out[5] = msgId;
  memcpy(out + 6, payload, payloadLen);

  uint16_t crc = 0xffff;
  for (size_t i = 1; i < size_t(payloadLen) + 6; ++i) {
    crc = crcByte(crc, out[i]);
  }
  crc = crcByte(crc, crcExtra);
  out[payloadLen + 6] = uint8_t(crc);
  out[payloadLen + 7] = uint8_t(crc >> 8);
  return frameLen;
}

uint16_t rcValue(const uint8_t *p, uint8_t ch) {
  return uint16_t(p[6 + ch * 2]) |
         (uint16_t(p[7 + ch * 2]) << 8);
}

bool validRcOverride(const uint8_t *p, size_t n) {
  if (!validMavlinkV1(p, n, 18, 70, 124) ||
      p[3] != 255 || p[4] != 190 ||
      p[22] != VEHICLE_SYSID || p[23] != VEHICLE_COMPID) {
    return false;
  }

  bool touched = false;
  for (uint8_t ch = 0; ch < 4; ++ch) {
    const uint16_t value = rcValue(p, ch);
    if (value == 0xffff) continue;
    touched = true;
    if (value != 0 && (value < RC_MIN_US || value > RC_MAX_US)) {
      return false;
    }
  }

  // CH5-CH8 remain inaccessible to this basic direct-drive profile.
  for (uint8_t ch = 4; ch < 8; ++ch) {
    if (rcValue(p, ch) != 0xffff) return false;
  }
  return touched;
}

bool validArmCommand(const uint8_t *p, size_t n, bool &requestArm) {
  if (!validMavlinkV1(p, n, 33, 76, 152) ||
      p[3] != 255 || p[4] != 190 ||
      p[36] != VEHICLE_SYSID || p[37] != VEHICLE_COMPID ||
      p[38] != 0) {
    return false;
  }

  const uint16_t command = uint16_t(p[34]) | (uint16_t(p[35]) << 8);
  if (command != 400) return false;  // MAV_CMD_COMPONENT_ARM_DISARM

  const bool disarm =
      p[6] == 0 && p[7] == 0 && p[8] == 0 && p[9] == 0;
  const bool arm =
      p[6] == 0 && p[7] == 0 && p[8] == 0x80 && p[9] == 0x3f;
  if (!arm && !disarm) return false;

  // param2..param7 must stay zero: no force-arm/bypass semantics.
  for (int i = 10; i < 34; ++i) {
    if (p[i] != 0) return false;
  }

  requestArm = arm;
  return true;
}

bool validGcsHeartbeat(const uint8_t *p, size_t n) {
  return validMavlinkV1(p, n, 9, 0, 50) &&
         p[3] == 255 && p[4] == 190;
}

// -----------------------------------------------------------------------------
// UDP / MAVLINK TELEMETRY
// -----------------------------------------------------------------------------

bool localController(const IPAddress &ip) {
  return ip[0] == 192 && ip[1] == 168 && ip[2] == 4 &&
         ip[3] > 1 && ip[3] < 255;
}

bool pairedController(const IPAddress &ip) {
  return controllerIp == IPAddress(0, 0, 0, 0) ||
         controllerIp == ip ||
         millis() - lastControllerMs >= PHONE_LEASE_MS;
}

bool newControllerAfterTimeout(const IPAddress &ip) {
  return controllerIp != IPAddress(0, 0, 0, 0) &&
         controllerIp != ip &&
         millis() - lastControllerMs >= PHONE_LEASE_MS;
}

void sendToController(const uint8_t *data, size_t length) {
  const bool pairFresh =
      controllerIp != IPAddress(0, 0, 0, 0) &&
      millis() - lastControllerMs < PHONE_LEASE_MS;
  const IPAddress destination = pairFresh ? controllerIp : AP_BROADCAST;

  if (udp.beginPacket(destination, UDP_PORT)) {
    udp.write(data, length);
    udp.endPacket();
  }
}

void sendHeartbeat() {
  // HEARTBEAT payload:
  // custom_mode(uint32), type, autopilot, base_mode, system_status,
  // mavlink_version.
  uint8_t payload[9] = {};
  payload[4] = 10;  // MAV_TYPE_GROUND_ROVER
  payload[5] = 0;   // MAV_AUTOPILOT_GENERIC
  payload[6] = 0x40 | (armed ? 0x80 : 0x00);  // manual-input + armed flag
  payload[7] = 4;   // MAV_STATE_ACTIVE
  payload[8] = 3;   // MAVLink protocol version field

  uint8_t frame[17];
  const size_t n = makeMavlinkV1(0, payload, sizeof(payload), 50,
                                 frame, sizeof(frame));
  if (n) sendToController(frame, n);
}

void sendCommandAck(uint8_t result) {
  uint8_t payload[3] = {
    0x90, 0x01,  // command 400
    result
  };
  uint8_t frame[11];
  const size_t n = makeMavlinkV1(77, payload, sizeof(payload), 143,
                                 frame, sizeof(frame));
  if (n) sendToController(frame, n);
}

void sendCompatibilityStatus() {
  if (controllerIp == IPAddress(0, 0, 0, 0) ||
      millis() - lastControllerMs >= PHONE_LEASE_MS) {
    return;
  }

  // Keep TELERC_STATUS_V1 so the current Android Diagnose Link screen receives
  // a response. In direct-drive mode the first two fields mean UDP bytes and
  // valid MAVLink frames rather than FC-UART bytes/frames.
  char status[144];
  const int n = snprintf(
      status, sizeof(status),
      "TELERC_STATUS_V1,%lu,%lu,%lu,%lu,%lu,%lu",
      static_cast<unsigned long>(udpRxBytes),
      static_cast<unsigned long>(mavRxFrames),
      static_cast<unsigned long>(acceptedCommands),
      static_cast<unsigned long>(rejectedCommands),
      static_cast<unsigned long>(motorUpdateCycles),
      static_cast<unsigned long>(armCommands));

  if (n > 0 && n < int(sizeof(status)) &&
      udp.beginPacket(controllerIp, UDP_PORT)) {
    udp.write(reinterpret_cast<const uint8_t *>(status), size_t(n));
    udp.endPacket();
  }
}

// -----------------------------------------------------------------------------
// MOTOR OUTPUT
// -----------------------------------------------------------------------------

#if ESP_ARDUINO_VERSION_MAJOR < 3
constexpr uint8_t RPWM_CH[4] = {0, 2, 4, 6};
constexpr uint8_t LPWM_CH[4] = {1, 3, 5, 7};
#endif

void attachMotorPwm() {
  for (uint8_t i = 0; i < 4; ++i) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
    ledcAttach(RPWM_PIN[i], PWM_FREQ_HZ, PWM_BITS);
    ledcAttach(LPWM_PIN[i], PWM_FREQ_HZ, PWM_BITS);
#else
    ledcSetup(RPWM_CH[i], PWM_FREQ_HZ, PWM_BITS);
    ledcSetup(LPWM_CH[i], PWM_FREQ_HZ, PWM_BITS);
    ledcAttachPin(RPWM_PIN[i], RPWM_CH[i]);
    ledcAttachPin(LPWM_PIN[i], LPWM_CH[i]);
#endif
  }
}

void pwmWritePin(uint8_t motor, bool rpwm, uint8_t duty) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(rpwm ? RPWM_PIN[motor] : LPWM_PIN[motor], duty);
#else
  ledcWrite(rpwm ? RPWM_CH[motor] : LPWM_CH[motor], duty);
#endif
}

void setBtsMotor(uint8_t motor, int command) {
  command = constrain(command, -MAX_MOTOR_DUTY, MAX_MOTOR_DUTY);
  if (INVERT_MOTOR[motor]) command = -command;

  if (command > 0) {
    pwmWritePin(motor, false, 0);
    pwmWritePin(motor, true, uint8_t(command));
  } else if (command < 0) {
    pwmWritePin(motor, true, 0);
    pwmWritePin(motor, false, uint8_t(-command));
  } else {
    pwmWritePin(motor, true, 0);
    pwmWritePin(motor, false, 0);
  }
}

void zeroTargets() {
  for (uint8_t i = 0; i < 4; ++i) targetCmd[i] = 0;
}

void hardStopMotors() {
  zeroTargets();
  for (uint8_t i = 0; i < 4; ++i) {
    currentCmd[i] = 0;
    setBtsMotor(i, 0);
  }
}

void clearControlOwnership() {
  ownedMask = 0;
  steeringUs = RC_CENTER_US;
  driveUs = RC_CENTER_US;
  lastControlMs = 0;
}

void triggerFailsafe(const char *reason) {
  hardStopMotors();
  clearControlOwnership();
  armed = false;
  ++failsafeCount;
  Serial.printf("FAILSAFE: %s | count=%lu\n", reason,
                static_cast<unsigned long>(failsafeCount));
}

int pwmToAxis(uint16_t value) {
  value = constrain(value, RC_MIN_US, RC_MAX_US);
  int delta = int(value) - int(RC_CENTER_US);
  if (abs(delta) <= RC_DEADBAND_US) return 0;

  const int sign = delta > 0 ? 1 : -1;
  int magnitude = abs(delta) - RC_DEADBAND_US;
  const int usable = 500 - RC_DEADBAND_US;
  magnitude = constrain(magnitude, 0, usable);
  return sign * (magnitude * 1000 / usable);
}

int slewToward(int current, int target) {
  if (current == target) return current;

  if ((current > 0 && target < 0) ||
      (current < 0 && target > 0)) {
    if (abs(current) <= DECEL_STEP) return 0;
    return current > 0 ? current - DECEL_STEP : current + DECEL_STEP;
  }

  const bool increasing =
      target != 0 && abs(target) > abs(current);
  const int step = increasing ? ACCEL_STEP : DECEL_STEP;

  if (target > current) {
    current += step;
    if (current > target) current = target;
  } else {
    current -= step;
    if (current < target) current = target;
  }
  return current;
}

bool controlsOwned() {
  return (ownedMask & 0x03) == 0x03;
}

bool controlsNeutral() {
  return abs(int(steeringUs) - int(RC_CENTER_US)) <= RC_DEADBAND_US &&
         abs(int(driveUs) - int(RC_CENTER_US)) <= RC_DEADBAND_US;
}

bool safeToArm() {
  if (!controlsNeutral() && controlsOwned()) return false;
  for (uint8_t i = 0; i < 4; ++i) {
    if (currentCmd[i] != 0) return false;
  }
  return true;
}

void computeTargets() {
  if (!armed || !controlsOwned()) {
    zeroTargets();
    return;
  }

  int steer = pwmToAxis(steeringUs);
  int drive = pwmToAxis(driveUs);

  if (INVERT_STEERING) steer = -steer;
  if (INVERT_DRIVE) drive = -drive;

  long left = long(drive) + long(steer);
  long right = long(drive) - long(steer);
  const long peak = max(labs(left), labs(right));

  if (peak > 1000) {
    left = left * 1000 / peak;
    right = right * 1000 / peak;
  }

  const int leftDuty =
      int(constrain(left, -1000L, 1000L) * MAX_MOTOR_DUTY / 1000L);
  const int rightDuty =
      int(constrain(right, -1000L, 1000L) * MAX_MOTOR_DUTY / 1000L);

  targetCmd[0] = leftDuty;
  targetCmd[1] = leftDuty;
  targetCmd[2] = rightDuty;
  targetCmd[3] = rightDuty;
}

void updateMotors() {
  computeTargets();
  for (uint8_t i = 0; i < 4; ++i) {
    currentCmd[i] = slewToward(currentCmd[i], targetCmd[i]);
    setBtsMotor(i, currentCmd[i]);
  }
  ++motorUpdateCycles;
}

// -----------------------------------------------------------------------------
// COMMAND PROCESSING
// -----------------------------------------------------------------------------

void applyRcOverride(const uint8_t *p) {
  const uint16_t ch1 = rcValue(p, 0);
  const uint16_t ch2 = rcValue(p, 1);

  if (ch1 == 0) {
    ownedMask &= ~0x01;
    steeringUs = RC_CENTER_US;
  } else if (ch1 != 0xffff) {
    ownedMask |= 0x01;
    steeringUs = ch1;
  }

  if (ch2 == 0) {
    ownedMask &= ~0x02;
    driveUs = RC_CENTER_US;
  } else if (ch2 != 0xffff) {
    ownedMask |= 0x02;
    driveUs = ch2;
  }

  // Refresh the direct-drive watchdog only when CH1 or CH2 was explicitly
  // addressed. Ignored channels cannot keep stale motor authority alive.
  if (ch1 != 0xffff || ch2 != 0xffff) {
    lastControlMs = millis();
  }

  // Releasing either required direct-drive channel is an immediate safe stop.
  if (!controlsOwned()) {
    hardStopMotors();
  }
}

void handleArmCommand(bool requestArm) {
  ++armCommands;

  if (!requestArm) {
    armed = false;
    hardStopMotors();
    sendCommandAck(0);  // MAV_RESULT_ACCEPTED
    Serial.println("DISARM accepted.");
    return;
  }

  if (!safeToArm()) {
    armed = false;
    hardStopMotors();
    sendCommandAck(1);  // MAV_RESULT_TEMPORARILY_REJECTED
    Serial.println("ARM rejected: controls are not neutral.");
    return;
  }

  armed = true;
  sendCommandAck(0);
  Serial.println("ARM accepted.");
}

// -----------------------------------------------------------------------------
// WIFI PASSWORD
// -----------------------------------------------------------------------------

String generatedPassword() {
  static const char alphabet[] =
      "ABCDEFGHJKLMNPQRSTUVWXYZ"
      "abcdefghijkmnopqrstuvwxyz"
      "23456789";

  char password[17];
  for (uint8_t i = 0; i < 16; ++i) {
    password[i] = alphabet[esp_random() % (sizeof(alphabet) - 1)];
  }
  password[16] = '\0';
  return String(password);
}

String loadApPassword() {
  const String personal = String(PERSONAL_AP_PASSWORD);
  if (personal.length() >= 12 && personal.length() <= 63) {
    return personal;
  }

  if (!preferences.begin("telerc", false)) {
    // NVS failure fallback: still avoid an open AP for this boot.
    return generatedPassword();
  }

  String stored = preferences.getString("ap_pass", "");
  if (stored.length() < 12 || stored.length() > 63) {
    stored = generatedPassword();
    preferences.putString("ap_pass", stored);
  }
  preferences.end();
  return stored;
}

// -----------------------------------------------------------------------------
// DIAGNOSTICS
// -----------------------------------------------------------------------------

void printDiagnostics() {
  const uint32_t now = millis();
  if (now - lastDiagMs < DIAG_PERIOD_MS) return;
  lastDiagMs = now;

  Serial.printf(
      "ARM=%s pair=%s own=%02X CH1=%u CH2=%u "
      "L=%d/%d R=%d/%d rx=%lu ok=%lu bad=%lu fs=%lu clients=%u\n",
      armed ? "YES" : "NO",
      controllerIp == IPAddress(0, 0, 0, 0)
          ? "none" : controllerIp.toString().c_str(),
      ownedMask,
      steeringUs, driveUs,
      currentCmd[0], targetCmd[0],
      currentCmd[2], targetCmd[2],
      static_cast<unsigned long>(mavRxFrames),
      static_cast<unsigned long>(acceptedCommands),
      static_cast<unsigned long>(rejectedCommands),
      static_cast<unsigned long>(failsafeCount),
      WiFi.softAPgetStationNum());
}

// -----------------------------------------------------------------------------
// SETUP / LOOP
// -----------------------------------------------------------------------------

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println();
  Serial.println("=== TeleRC ESP32-S3 DIRECT DRIVE boot ===");

  for (uint8_t i = 0; i < 4; ++i) {
    pinMode(RPWM_PIN[i], OUTPUT);
    pinMode(LPWM_PIN[i], OUTPUT);
  }
  attachMotorPwm();
  hardStopMotors();

  const String password = loadApPassword();

  WiFi.persistent(false);
  WiFi.mode(WIFI_AP);

  if (!WiFi.softAPConfig(AP_IP, AP_IP, IPAddress(255, 255, 255, 0)) ||
      !WiFi.softAP(AP_SSID, password.c_str()) ||
      !udp.begin(UDP_PORT)) {
    Serial.println("FATAL: TeleRC direct-drive startup failed.");
    hardStopMotors();
    while (true) delay(1000);
  }

  previousStationCount = WiFi.softAPgetStationNum();

  Serial.printf("Direct drive ready: %s @ %s:%u\n",
                AP_SSID, WiFi.softAPIP().toString().c_str(), UDP_PORT);
  Serial.printf("Wi-Fi password: %s\n", password.c_str());
  Serial.println("Mapping: CH1 steering / CH2 bidirectional drive.");
  Serial.println("BTS outputs: FL 8/9, RL 10/11, FR 12/13, RR 14/15.");
  Serial.println("State: DISARMED. Raise wheels before ARM.");
}

void loop() {
  const int packetSize = udp.parsePacket();
  if (packetSize > 0) {
    const IPAddress sender = udp.remoteIP();
    const uint16_t senderPort = udp.remotePort();
    uint8_t p[280];
    const int count = udp.read(p, sizeof(p));
    while (udp.available()) udp.read();

    if (count > 0) udpRxBytes += uint32_t(count);

    const bool sourceOk =
        senderPort == UDP_PORT &&
        localController(sender) &&
        pairedController(sender);

    if (sourceOk &&
        packetSize == int(sizeof(DISCOVERY) - 1) &&
        count == packetSize &&
        memcmp(p, DISCOVERY, sizeof(DISCOVERY) - 1) == 0) {
      if (newControllerAfterTimeout(sender)) {
        triggerFailsafe("controller handover");
      }
      controllerIp = sender;
      lastControllerMs = millis();
    }
    else if (sourceOk &&
             packetSize == int(sizeof(DISCONNECT) - 1) &&
             count == packetSize &&
             memcmp(p, DISCONNECT, sizeof(DISCONNECT) - 1) == 0) {
      triggerFailsafe("explicit disconnect");
      controllerIp = IPAddress(0, 0, 0, 0);
      lastControllerMs = 0;
      ++acceptedCommands;
    }
    else if (sourceOk && count == 26 && validRcOverride(p, count)) {
      if (newControllerAfterTimeout(sender)) {
        triggerFailsafe("RC sender handover");
      }
      controllerIp = sender;
      lastControllerMs = millis();
      ++mavRxFrames;
      ++acceptedCommands;
      applyRcOverride(p);
    }
    else {
      bool requestArm = false;
      if (sourceOk && count == 41 && validArmCommand(p, count, requestArm)) {
        if (newControllerAfterTimeout(sender)) {
          triggerFailsafe("arm sender handover");
        }
        controllerIp = sender;
        lastControllerMs = millis();
        ++mavRxFrames;
        ++acceptedCommands;
        handleArmCommand(requestArm);
      }
      else if (sourceOk && count == 17 && validGcsHeartbeat(p, count)) {
        controllerIp = sender;
        lastControllerMs = millis();
        ++mavRxFrames;
        // Harmless GCS heartbeat: recognized but not counted as a drive command.
      }
      else if (localController(sender) && senderPort == UDP_PORT) {
        ++rejectedCommands;
      }
    }
  }

  const uint8_t stations = WiFi.softAPgetStationNum();
  if (previousStationCount > 0 && stations == 0 &&
      controllerIp != IPAddress(0, 0, 0, 0)) {
    triggerFailsafe("Wi-Fi client lost");
    controllerIp = IPAddress(0, 0, 0, 0);
    lastControllerMs = 0;
  }
  previousStationCount = stations;

  if (controlsOwned() && lastControlMs != 0 &&
      millis() - lastControlMs >= CONTROL_TIMEOUT_MS) {
    triggerFailsafe("control packet timeout");
  }

  const uint32_t nowUs = micros();
  if (uint32_t(nowUs - lastControlUs) >= CONTROL_PERIOD_US) {
    lastControlUs = nowUs;
    updateMotors();
  }

  const uint32_t nowMs = millis();
  if (nowMs - lastHeartbeatMs >= HEARTBEAT_PERIOD_MS) {
    lastHeartbeatMs = nowMs;
    sendHeartbeat();
  }

  if (nowMs - lastStatusMs >= STATUS_PERIOD_MS) {
    lastStatusMs = nowMs;
    sendCompatibilityStatus();
  }

  printDiagnostics();
  delay(1);
}

/*
  TeleRC Hybrid Rover Controller
  ESP32-S3 -> 4x BTS7960 with optional SpeedyBee/Pixhawk PWM authority
  and mode-gated bidirectional MAVLink UART routing

  MODES
  -----
  DIRECT:
    TeleRC Android / PC TeleRC -> Wi-Fi UDP -> ESP32-S3 -> BTS7960

  AUTOPILOT:
    TeleRC <-> Wi-Fi UDP <-> ESP32-S3 <-> MAVLink UART <-> F405/Pixhawk
    F405/Pixhawk M5-M8 PWM -> ESP32-S3 -> BTS7960

  FAILSAFE:
    ESP32-S3 -> all motor PWM = 0

  MODE SELECTION
  --------------
  GPIO16 LOW  = DIRECT requested (internal pulldown makes this the default)
  GPIO16 HIGH = AUTOPILOT requested

  Every source change passes through FAILSAFE and requires the newly selected
  source to be valid and neutral for SOURCE_NEUTRAL_DWELL_MS before authority
  is granted. Never drive GPIO16 with 5 V.

  DEFAULT F405 INPUT MAP
  ----------------------
  M5 -> GPIO4  -> Front Left
  M6 -> GPIO5  -> Rear Left
  M7 -> GPIO6  -> Front Right
  M8 -> GPIO7  -> Rear Right

  MOTOR OUTPUT MAP
  ----------------
  Front Left  -> RPWM GPIO8  / LPWM GPIO9
  Rear Left   -> RPWM GPIO10 / LPWM GPIO11
  Front Right -> RPWM GPIO12 / LPWM GPIO13
  Rear Right  -> RPWM GPIO14 / LPWM GPIO15

  IMPORTANT
  ---------
  - Test with wheels raised.
  - Keep a physical motor-power emergency stop/disconnect.
  - ESP32, flight controller and BTS7960 logic grounds must share a reference.
  - Do not feed 5 V into ESP32 GPIOs.
  - Do not feed motor-battery voltage directly into the ESP32.
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

// F405/Pixhawk MAVLink UART. SpeedyBee F405 V4 example:
// FC T3 -> ESP GPIO18 (RX), FC R3 <- ESP GPIO17 (TX), common GND.
constexpr int FC_MAV_RX_GPIO = 18;
constexpr int FC_MAV_TX_GPIO = 17;
constexpr uint32_t FC_MAV_BAUD = 115200;

constexpr uint32_t DIRECT_CONTROL_TIMEOUT_MS = 500;
constexpr uint32_t CONTROLLER_LEASE_MS = 5000;
constexpr uint32_t HEARTBEAT_PERIOD_MS = 1000;
constexpr uint32_t STATUS_PERIOD_MS = 1000;
constexpr uint32_t DIAG_PERIOD_MS = 500;

constexpr uint32_t FC_PWM_TIMEOUT_US = 150000;
constexpr uint32_t SOURCE_NEUTRAL_DWELL_MS = 300;
constexpr uint32_t MODE_DEBOUNCE_MS = 50;

constexpr uint16_t RC_MIN_US = 1000;
constexpr uint16_t RC_CENTER_US = 1500;
constexpr uint16_t RC_MAX_US = 2000;
constexpr uint16_t RC_VALID_MIN_US = 850;
constexpr uint16_t RC_VALID_MAX_US = 2150;
constexpr uint16_t RC_DEADBAND_US = 35;

constexpr uint32_t PWM_FREQ_HZ = 20000;
constexpr uint8_t PWM_BITS = 8;
constexpr int MAX_MOTOR_DUTY = 255;

constexpr uint32_t CONTROL_PERIOD_US = 5000;
constexpr int DIRECT_ACCEL_STEP = 4;
constexpr int DIRECT_DECEL_STEP = 16;
constexpr int AUTOPILOT_ACCEL_STEP = 8;
constexpr int AUTOPILOT_DECEL_STEP = 20;

constexpr bool INVERT_STEERING = false;
constexpr bool INVERT_DRIVE = false;

// Default is four independent F405 outputs, M5-M8 -> four wheels.
// Set true if the FC is configured with only LEFT/RIGHT motor outputs:
// GPIO4 is LEFT and GPIO5 is RIGHT; GPIO6/GPIO7 are then ignored.
constexpr bool AUTOPILOT_PAIRED_SKID_STEER = false;

constexpr bool INVERT_MOTOR[4] = {
  false,  // Front Left
  false,  // Rear Left
  false,  // Front Right
  false   // Rear Right
};

constexpr uint8_t FC_PWM_PIN[4] = {4, 5, 6, 7};
constexpr uint8_t RPWM_PIN[4] = {8, 10, 12, 14};
constexpr uint8_t LPWM_PIN[4] = {9, 11, 13, 15};
constexpr uint8_t MODE_SELECT_PIN = 16;

const char AP_SSID[] = "TeleRC-Rover";
const char PERSONAL_AP_PASSWORD[] = "";

const char DISCOVERY[] = "TELERC_DISCOVER_V1";
const char DISCONNECT[] = "TELERC_DISCONNECT_V1";

const IPAddress AP_IP(192, 168, 4, 1);
const IPAddress AP_BROADCAST(192, 168, 4, 255);

WiFiUDP udp;
Preferences preferences;
HardwareSerial fcMav(1);

// -----------------------------------------------------------------------------
// MODE / STATE
// -----------------------------------------------------------------------------

enum class ControlMode : uint8_t {
  FAILSAFE = 0,
  DIRECT = 1,
  AUTOPILOT = 2
};

ControlMode activeMode = ControlMode::FAILSAFE;
ControlMode requestedMode = ControlMode::DIRECT;
ControlMode lastRawRequestedMode = ControlMode::DIRECT;

uint32_t rawModeChangedMs = 0;
uint32_t sourceNeutralSinceMs = 0;
uint32_t failsafeCount = 0;

IPAddress controllerIp;
uint32_t lastControllerMs = 0;
uint32_t lastDirectControlMs = 0;

uint8_t mavSequence = 0;
uint8_t directOwnedMask = 0;  // bit0=CH1 steering, bit1=CH2 drive
uint16_t steeringUs = RC_CENTER_US;
uint16_t driveUs = RC_CENTER_US;
bool directArmed = false;

volatile uint32_t fcRiseUs[4] = {0, 0, 0, 0};
volatile uint32_t fcLastValidUs[4] = {0, 0, 0, 0};
volatile uint16_t fcPulseUs[4] = {
  RC_CENTER_US, RC_CENTER_US, RC_CENTER_US, RC_CENTER_US
};

int targetCmd[4] = {0, 0, 0, 0};
int currentCmd[4] = {0, 0, 0, 0};

uint8_t previousStationCount = 0;
uint32_t lastHeartbeatMs = 0;
uint32_t lastStatusMs = 0;
uint32_t lastDiagMs = 0;
uint32_t lastControlLoopUs = 0;

uint32_t udpRxBytes = 0;
uint32_t mavRxFrames = 0;
uint32_t acceptedCommands = 0;
uint32_t rejectedCommands = 0;
uint32_t motorUpdateCycles = 0;
uint32_t armCommands = 0;

// F405 MAVLink UART framing/diagnostics.
uint8_t fcMavFrame[280];
uint16_t fcMavFrameSize = 0;
uint16_t fcMavFrameExpected = 0;
uint32_t fcMavLastByteMs = 0;
uint32_t fcMavRxBytes = 0;
uint32_t fcMavRxFrames = 0;
uint32_t fcMavTxBytes = 0;

// -----------------------------------------------------------------------------
// HELPERS
// -----------------------------------------------------------------------------

const char *modeName(ControlMode mode) {
  switch (mode) {
    case ControlMode::DIRECT: return "DIRECT";
    case ControlMode::AUTOPILOT: return "AUTOPILOT";
    default: return "FAILSAFE";
  }
}

bool directRequestedByPin() {
  return digitalRead(MODE_SELECT_PIN) == LOW;
}

ControlMode rawRequestedMode() {
  return directRequestedByPin()
      ? ControlMode::DIRECT
      : ControlMode::AUTOPILOT;
}

void zeroTargets() {
  for (uint8_t i = 0; i < 4; ++i) targetCmd[i] = 0;
}

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
  if (command != 400) return false;

  const bool disarm =
      p[6] == 0 && p[7] == 0 && p[8] == 0 && p[9] == 0;
  const bool arm =
      p[6] == 0 && p[7] == 0 && p[8] == 0x80 && p[9] == 0x3f;
  if (!arm && !disarm) return false;

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

// Generic router validation is deliberately structural rather than checksum
// aware because MAVLink CRC-extra depends on message ID. The F405 remains the
// final MAVLink checksum validator. Pairing + source sysid/compid prevent
// unrelated Wi-Fi traffic from being routed into the flight controller.
size_t mavlinkFrameLengthAt(const uint8_t *p, size_t n, size_t offset) {
  if (offset >= n) return 0;

  const uint8_t magic = p[offset];
  if (magic != 0xfe && magic != 0xfd) return 0;

  const size_t header = magic == 0xfd ? 10 : 6;
  if (n - offset < header) return 0;

  const size_t payload = p[offset + 1];
  const bool signedV2 =
      magic == 0xfd && (p[offset + 2] & 0x01) != 0;
  const size_t total =
      header + payload + 2 + (signedV2 ? 13 : 0);

  return (n - offset >= total) ? total : 0;
}

bool validControllerMavlinkDatagram(const uint8_t *p, size_t n) {
  if (!n) return false;

  size_t offset = 0;
  bool sawFrame = false;

  while (offset < n) {
    const size_t frameLen = mavlinkFrameLengthAt(p, n, offset);
    if (!frameLen) return false;

    const bool v2 = p[offset] == 0xfd;
    const uint8_t sysid = p[offset + (v2 ? 5 : 3)];
    const uint8_t compid = p[offset + (v2 ? 6 : 4)];

    if (sysid != 255 || compid != 190) return false;

    sawFrame = true;
    offset += frameLen;
  }

  return sawFrame && offset == n;
}

// -----------------------------------------------------------------------------
// UDP / TELEMETRY
// -----------------------------------------------------------------------------

bool localController(const IPAddress &ip) {
  return ip[0] == 192 && ip[1] == 168 && ip[2] == 4 &&
         ip[3] > 1 && ip[3] < 255;
}

bool controllerLeaseFresh() {
  return controllerIp != IPAddress(0, 0, 0, 0) &&
         millis() - lastControllerMs < CONTROLLER_LEASE_MS;
}

bool pairedController(const IPAddress &ip) {
  return controllerIp == IPAddress(0, 0, 0, 0) ||
         controllerIp == ip ||
         millis() - lastControllerMs >= CONTROLLER_LEASE_MS;
}

bool newControllerAfterTimeout(const IPAddress &ip) {
  return controllerIp != IPAddress(0, 0, 0, 0) &&
         controllerIp != ip &&
         millis() - lastControllerMs >= CONTROLLER_LEASE_MS;
}

void sendToController(const uint8_t *data, size_t length) {
  const IPAddress destination =
      controllerLeaseFresh() ? controllerIp : AP_BROADCAST;

  if (udp.beginPacket(destination, UDP_PORT)) {
    udp.write(data, length);
    udp.endPacket();
  }
}

void sendHeartbeat() {
  uint8_t payload[9] = {};
  payload[0] = uint8_t(activeMode);  // custom_mode low byte for diagnostics
  payload[4] = 10;                   // MAV_TYPE_GROUND_ROVER
  payload[5] = 0;                    // MAV_AUTOPILOT_GENERIC

  // DIRECT arm state is locally authoritative. AUTOPILOT forwarding does not
  // claim the F405 arm state because no arm-status signal is available here.
  payload[6] = 0x40 |
      ((activeMode == ControlMode::DIRECT && directArmed) ? 0x80 : 0x00);
  payload[7] = 4;  // MAV_STATE_ACTIVE
  payload[8] = 3;

  uint8_t frame[17];
  const size_t n = makeMavlinkV1(0, payload, sizeof(payload), 50,
                                 frame, sizeof(frame));
  if (n) sendToController(frame, n);
}

void sendCommandAck(uint8_t result) {
  uint8_t payload[3] = {
    0x90, 0x01,  // MAV_CMD_COMPONENT_ARM_DISARM = 400
    result
  };
  uint8_t frame[11];
  const size_t n = makeMavlinkV1(77, payload, sizeof(payload), 143,
                                 frame, sizeof(frame));
  if (n) sendToController(frame, n);
}

void sendCompatibilityStatus() {
  if (!controllerLeaseFresh()) return;

  // Keep the existing Android diagnostics wire format. With Hybrid firmware,
  // fields 1/2 are the F405 MAVLink UART RX byte/frame counters and field 5
  // is bytes routed from TeleRC to the F405 UART.
  char status[160];
  const int n = snprintf(
      status, sizeof(status),
      "TELERC_STATUS_V1,%lu,%lu,%lu,%lu,%lu,%lu",
      static_cast<unsigned long>(fcMavRxBytes),
      static_cast<unsigned long>(fcMavRxFrames),
      static_cast<unsigned long>(acceptedCommands),
      static_cast<unsigned long>(rejectedCommands),
      static_cast<unsigned long>(fcMavTxBytes),
      static_cast<unsigned long>(armCommands));

  if (n > 0 && n < int(sizeof(status)) &&
      udp.beginPacket(controllerIp, UDP_PORT)) {
    udp.write(reinterpret_cast<const uint8_t *>(status), size_t(n));
    udp.endPacket();
  }
}

// -----------------------------------------------------------------------------
// F405 MAVLINK UART ROUTER
// -----------------------------------------------------------------------------

bool routeAutopilotTelemetry() {
  // requestedMode is used instead of only activeMode so the F405 heartbeat is
  // available while FAILSAFE is validating the AUTOPILOT neutral handover.
  return requestedMode == ControlMode::AUTOPILOT;
}

void consumeFcMavByte(uint8_t b) {
  ++fcMavRxBytes;

  if (fcMavFrameSize && millis() - fcMavLastByteMs > 100) {
    fcMavFrameSize = 0;
    fcMavFrameExpected = 0;
  }
  fcMavLastByteMs = millis();

  if (!fcMavFrameSize && b != 0xfe && b != 0xfd) return;

  if (fcMavFrameSize >= sizeof(fcMavFrame)) {
    fcMavFrameSize = 0;
    fcMavFrameExpected = 0;
    return;
  }

  fcMavFrame[fcMavFrameSize++] = b;

  if (fcMavFrameSize == 3) {
    const bool v2 = fcMavFrame[0] == 0xfd;
    fcMavFrameExpected =
        (v2 ? 10 : 6) + fcMavFrame[1] + 2 +
        ((v2 && (fcMavFrame[2] & 1)) ? 13 : 0);

    if (fcMavFrameExpected > sizeof(fcMavFrame)) {
      fcMavFrameSize = 0;
      fcMavFrameExpected = 0;
      return;
    }
  }

  if (fcMavFrameExpected &&
      fcMavFrameSize == fcMavFrameExpected) {
    ++fcMavRxFrames;

    if (routeAutopilotTelemetry()) {
      sendToController(fcMavFrame, fcMavFrameSize);
    }

    fcMavFrameSize = 0;
    fcMavFrameExpected = 0;
  }
}

void serviceFcMavlink() {
  // Bound UART work per loop so telemetry bursts cannot starve motor safety.
  for (int i = 0; i < 512 && fcMav.available(); ++i) {
    consumeFcMavByte(uint8_t(fcMav.read()));
  }
}

bool routeControllerMavlinkToFc(const uint8_t *data, size_t length) {
  if (requestedMode != ControlMode::AUTOPILOT ||
      !validControllerMavlinkDatagram(data, length)) {
    return false;
  }

  const size_t written = fcMav.write(data, length);
  fcMavTxBytes += written;
  return written == length;
}

// -----------------------------------------------------------------------------
// F405 PWM INPUT CAPTURE
// -----------------------------------------------------------------------------

static inline void IRAM_ATTR captureFcEdge(uint8_t ch) {
  const uint32_t now = micros();

  if (digitalRead(FC_PWM_PIN[ch])) {
    fcRiseUs[ch] = now;
  } else {
    const uint32_t width = now - fcRiseUs[ch];
    if (width >= RC_VALID_MIN_US && width <= RC_VALID_MAX_US) {
      fcPulseUs[ch] = static_cast<uint16_t>(width);
      fcLastValidUs[ch] = now;
    }
  }
}

void IRAM_ATTR fc0ISR() { captureFcEdge(0); }
void IRAM_ATTR fc1ISR() { captureFcEdge(1); }
void IRAM_ATTR fc2ISR() { captureFcEdge(2); }
void IRAM_ATTR fc3ISR() { captureFcEdge(3); }

bool fcChannelFresh(uint8_t ch, uint32_t nowUs) {
  const uint32_t last = fcLastValidUs[ch];
  return last != 0 &&
         uint32_t(nowUs - last) <= FC_PWM_TIMEOUT_US;
}

bool autopilotSourceFresh(uint32_t nowUs) {
  if (AUTOPILOT_PAIRED_SKID_STEER) {
    return fcChannelFresh(0, nowUs) && fcChannelFresh(1, nowUs);
  }

  for (uint8_t i = 0; i < 4; ++i) {
    if (!fcChannelFresh(i, nowUs)) return false;
  }
  return true;
}

bool pulseNeutral(uint16_t us) {
  return abs(int(us) - int(RC_CENTER_US)) <= RC_DEADBAND_US;
}

bool autopilotSourceNeutral(uint32_t nowUs) {
  if (!autopilotSourceFresh(nowUs)) return false;

  if (AUTOPILOT_PAIRED_SKID_STEER) {
    return pulseNeutral(fcPulseUs[0]) && pulseNeutral(fcPulseUs[1]);
  }

  for (uint8_t i = 0; i < 4; ++i) {
    if (!pulseNeutral(fcPulseUs[i])) return false;
  }
  return true;
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

void hardStopMotors() {
  zeroTargets();
  for (uint8_t i = 0; i < 4; ++i) {
    currentCmd[i] = 0;
    setBtsMotor(i, 0);
  }
}

int pulseToMotorCommand(uint16_t value) {
  value = constrain(value, RC_MIN_US, RC_MAX_US);
  int delta = int(value) - int(RC_CENTER_US);
  if (abs(delta) <= RC_DEADBAND_US) return 0;

  const int sign = delta > 0 ? 1 : -1;
  int magnitude = abs(delta) - RC_DEADBAND_US;
  const int usable = 500 - RC_DEADBAND_US;
  magnitude = constrain(magnitude, 0, usable);

  const long scaled =
      long(magnitude) * long(MAX_MOTOR_DUTY) / long(usable);
  return sign * int(scaled);
}

int pulseToAxis1000(uint16_t value) {
  value = constrain(value, RC_MIN_US, RC_MAX_US);
  int delta = int(value) - int(RC_CENTER_US);
  if (abs(delta) <= RC_DEADBAND_US) return 0;

  const int sign = delta > 0 ? 1 : -1;
  int magnitude = abs(delta) - RC_DEADBAND_US;
  const int usable = 500 - RC_DEADBAND_US;
  magnitude = constrain(magnitude, 0, usable);

  return sign * int(long(magnitude) * 1000L / usable);
}

int slewToward(int current, int target, int accelStep, int decelStep) {
  if (current == target) return current;

  if ((current > 0 && target < 0) ||
      (current < 0 && target > 0)) {
    if (abs(current) <= decelStep) return 0;
    return current > 0 ? current - decelStep : current + decelStep;
  }

  const bool increasing =
      target != 0 && abs(target) > abs(current);
  const int step = increasing ? accelStep : decelStep;

  if (target > current) {
    current += step;
    if (current > target) current = target;
  } else {
    current -= step;
    if (current < target) current = target;
  }
  return current;
}

// -----------------------------------------------------------------------------
// DIRECT SOURCE
// -----------------------------------------------------------------------------

bool directControlsOwned() {
  return (directOwnedMask & 0x03) == 0x03;
}

bool directControlsNeutral() {
  return pulseNeutral(steeringUs) && pulseNeutral(driveUs);
}

void clearDirectOwnership() {
  directOwnedMask = 0;
  steeringUs = RC_CENTER_US;
  driveUs = RC_CENTER_US;
  lastDirectControlMs = 0;
}

bool safeToDirectArm() {
  if (activeMode != ControlMode::DIRECT) return false;
  if (directControlsOwned() && !directControlsNeutral()) return false;

  for (uint8_t i = 0; i < 4; ++i) {
    if (currentCmd[i] != 0) return false;
  }
  return true;
}

void applyRcOverride(const uint8_t *p) {
  const uint16_t ch1 = rcValue(p, 0);
  const uint16_t ch2 = rcValue(p, 1);

  if (ch1 == 0) {
    directOwnedMask &= ~0x01;
    steeringUs = RC_CENTER_US;
  } else if (ch1 != 0xffff) {
    directOwnedMask |= 0x01;
    steeringUs = ch1;
  }

  if (ch2 == 0) {
    directOwnedMask &= ~0x02;
    driveUs = RC_CENTER_US;
  } else if (ch2 != 0xffff) {
    directOwnedMask |= 0x02;
    driveUs = ch2;
  }

  if (ch1 != 0xffff || ch2 != 0xffff) {
    lastDirectControlMs = millis();
  }

  if (!directControlsOwned()) {
    hardStopMotors();
  }
}

void handleArmCommand(bool requestArm) {
  ++armCommands;

  if (activeMode != ControlMode::DIRECT) {
    directArmed = false;
    sendCommandAck(2);  // MAV_RESULT_DENIED
    Serial.println("ARM/DISARM denied: DIRECT mode is not active.");
    return;
  }

  if (!requestArm) {
    directArmed = false;
    hardStopMotors();
    sendCommandAck(0);
    Serial.println("DIRECT DISARM accepted.");
    return;
  }

  if (!safeToDirectArm()) {
    directArmed = false;
    hardStopMotors();
    sendCommandAck(1);  // MAV_RESULT_TEMPORARILY_REJECTED
    Serial.println("DIRECT ARM rejected: outputs/control not neutral.");
    return;
  }

  directArmed = true;
  sendCommandAck(0);
  Serial.println("DIRECT ARM accepted.");
}

// -----------------------------------------------------------------------------
// MODE ARBITRATION / FAILSAFE
// -----------------------------------------------------------------------------

void enterFailsafe(const char *reason, bool countTrip = true) {
  hardStopMotors();
  directArmed = false;
  clearDirectOwnership();
  activeMode = ControlMode::FAILSAFE;
  sourceNeutralSinceMs = 0;

  if (countTrip) ++failsafeCount;

  Serial.printf("MODE -> FAILSAFE: %s | count=%lu\n",
                reason,
                static_cast<unsigned long>(failsafeCount));
}

bool directSourceSafeForTransfer() {
  // A paired controller is enough if it owns no channels yet. If it already
  // owns the channels, both must be neutral before authority can transfer.
  if (!controllerLeaseFresh()) return false;
  return !directControlsOwned() || directControlsNeutral();
}

bool sourceSafeForTransfer(ControlMode mode, uint32_t nowUs) {
  if (mode == ControlMode::DIRECT) {
    return directSourceSafeForTransfer();
  }
  if (mode == ControlMode::AUTOPILOT) {
    return autopilotSourceNeutral(nowUs);
  }
  return false;
}

void updateRequestedMode() {
  const uint32_t now = millis();
  const ControlMode raw = rawRequestedMode();

  if (raw != lastRawRequestedMode) {
    lastRawRequestedMode = raw;
    rawModeChangedMs = now;
  }

  if (raw != requestedMode &&
      now - rawModeChangedMs >= MODE_DEBOUNCE_MS) {
    requestedMode = raw;
    enterFailsafe(
        requestedMode == ControlMode::DIRECT
            ? "mode switch requested DIRECT"
            : "mode switch requested AUTOPILOT",
        false);
  }
}

void updateModeArbiter(uint32_t nowUs) {
  updateRequestedMode();

  if (activeMode != ControlMode::FAILSAFE &&
      activeMode != requestedMode) {
    enterFailsafe("requested source changed", false);
  }

  if (activeMode == ControlMode::DIRECT) {
    if (!controllerLeaseFresh()) {
      enterFailsafe("DIRECT controller lease lost");
      return;
    }

    if (directControlsOwned() &&
        lastDirectControlMs != 0 &&
        millis() - lastDirectControlMs >= DIRECT_CONTROL_TIMEOUT_MS) {
      enterFailsafe("DIRECT control packet timeout");
      return;
    }

    return;
  }

  if (activeMode == ControlMode::AUTOPILOT) {
    if (!autopilotSourceFresh(nowUs)) {
      enterFailsafe("AUTOPILOT PWM lost");
      return;
    }
    return;
  }

  // FAILSAFE -> selected source only after a stable neutral dwell.
  if (!sourceSafeForTransfer(requestedMode, nowUs)) {
    sourceNeutralSinceMs = 0;
    return;
  }

  const uint32_t nowMs = millis();
  if (sourceNeutralSinceMs == 0) {
    sourceNeutralSinceMs = nowMs;
    return;
  }

  if (nowMs - sourceNeutralSinceMs < SOURCE_NEUTRAL_DWELL_MS) {
    return;
  }

  hardStopMotors();
  activeMode = requestedMode;
  sourceNeutralSinceMs = 0;

  if (activeMode == ControlMode::DIRECT) {
    directArmed = false;
    // Require fresh CH1/CH2 ownership after entering DIRECT so a command stored
    // before a mode transition cannot resume motion.
    clearDirectOwnership();
  }

  Serial.printf("MODE -> %s\n", modeName(activeMode));
}

// -----------------------------------------------------------------------------
// SOURCE TO MOTOR TARGETS
// -----------------------------------------------------------------------------

void computeDirectTargets() {
  if (!directArmed || !directControlsOwned()) {
    zeroTargets();
    return;
  }

  int steer = pulseToAxis1000(steeringUs);
  int drive = pulseToAxis1000(driveUs);

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

void computeAutopilotTargets(uint32_t nowUs) {
  if (!autopilotSourceFresh(nowUs)) {
    zeroTargets();
    return;
  }

  if (AUTOPILOT_PAIRED_SKID_STEER) {
    const int left = pulseToMotorCommand(fcPulseUs[0]);
    const int right = pulseToMotorCommand(fcPulseUs[1]);

    targetCmd[0] = left;
    targetCmd[1] = left;
    targetCmd[2] = right;
    targetCmd[3] = right;
    return;
  }

  for (uint8_t i = 0; i < 4; ++i) {
    targetCmd[i] = pulseToMotorCommand(fcPulseUs[i]);
  }
}

void updateMotorTargets(uint32_t nowUs) {
  switch (activeMode) {
    case ControlMode::DIRECT:
      computeDirectTargets();
      break;
    case ControlMode::AUTOPILOT:
      computeAutopilotTargets(nowUs);
      break;
    default:
      zeroTargets();
      break;
  }
}

void updateMotors(uint32_t nowUs) {
  updateMotorTargets(nowUs);

  if (activeMode == ControlMode::FAILSAFE) {
    hardStopMotors();
    ++motorUpdateCycles;
    return;
  }

  const int accelStep =
      activeMode == ControlMode::AUTOPILOT
          ? AUTOPILOT_ACCEL_STEP
          : DIRECT_ACCEL_STEP;
  const int decelStep =
      activeMode == ControlMode::AUTOPILOT
          ? AUTOPILOT_DECEL_STEP
          : DIRECT_DECEL_STEP;

  for (uint8_t i = 0; i < 4; ++i) {
    currentCmd[i] =
        slewToward(currentCmd[i], targetCmd[i], accelStep, decelStep);
    setBtsMotor(i, currentCmd[i]);
  }
  ++motorUpdateCycles;
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
// UDP COMMAND HANDLING
// -----------------------------------------------------------------------------

void handleUdpPacket() {
  const int packetSize = udp.parsePacket();
  if (packetSize <= 0) return;

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
      enterFailsafe("controller handover");
    }
    controllerIp = sender;
    lastControllerMs = millis();
    return;
  }

  if (sourceOk &&
      packetSize == int(sizeof(DISCONNECT) - 1) &&
      count == packetSize &&
      memcmp(p, DISCONNECT, sizeof(DISCONNECT) - 1) == 0) {
    enterFailsafe("explicit TeleRC disconnect");
    controllerIp = IPAddress(0, 0, 0, 0);
    lastControllerMs = 0;
    ++acceptedCommands;
    return;
  }

  // In AUTOPILOT selection, the ESP32 becomes a transparent MAVLink transport
  // for complete TeleRC MAVLink datagrams. The F405 validates each frame's
  // MAVLink checksum. Motor authority still remains gated by M5-M8 PWM and the
  // ESP32 mode/failsafe arbiter.
  if (sourceOk &&
      requestedMode == ControlMode::AUTOPILOT &&
      count > 0 &&
      validControllerMavlinkDatagram(p, size_t(count))) {
    if (newControllerAfterTimeout(sender)) {
      enterFailsafe("MAVLink sender handover");
    }

    controllerIp = sender;
    lastControllerMs = millis();
    ++mavRxFrames;

    bool requestedArm = false;
    if (count == 41 && validArmCommand(p, count, requestedArm)) {
      ++armCommands;
    }

    if (routeControllerMavlinkToFc(p, size_t(count))) {
      ++acceptedCommands;
    } else {
      ++rejectedCommands;
    }
    return;
  }

  if (sourceOk && count == 26 && validRcOverride(p, count)) {
    if (newControllerAfterTimeout(sender)) {
      enterFailsafe("RC sender handover");
    }

    controllerIp = sender;
    lastControllerMs = millis();
    ++mavRxFrames;
    ++acceptedCommands;

    // DIRECT only: consume TeleRC steering/drive locally. This packet is never
    // forwarded to the F405 while DIRECT is selected.
    applyRcOverride(p);
    return;
  }

  bool requestArm = false;
  if (sourceOk && count == 41 && validArmCommand(p, count, requestArm)) {
    if (newControllerAfterTimeout(sender)) {
      enterFailsafe("arm sender handover");
    }

    controllerIp = sender;
    lastControllerMs = millis();
    ++mavRxFrames;
    ++acceptedCommands;
    handleArmCommand(requestArm);
    return;
  }

  if (sourceOk && count == 17 && validGcsHeartbeat(p, count)) {
    controllerIp = sender;
    lastControllerMs = millis();
    ++mavRxFrames;
    return;
  }

  if (localController(sender) && senderPort == UDP_PORT) {
    ++rejectedCommands;
  }
}

// -----------------------------------------------------------------------------
// DIAGNOSTICS
// -----------------------------------------------------------------------------

void printDiagnostics(uint32_t nowUs) {
  const uint32_t nowMs = millis();
  if (nowMs - lastDiagMs < DIAG_PERIOD_MS) return;
  lastDiagMs = nowMs;

  Serial.printf(
      "MODE=%s REQ=%s DIRECT_ARM=%s pair=%s own=%02X "
      "CH1=%u CH2=%u | FC=%u%c %u%c %u%c %u%c | "
      "CMD=%d %d %d %d | MAV=%luB/%luF TX=%luB | fs=%lu clients=%u\n",
      modeName(activeMode),
      modeName(requestedMode),
      directArmed ? "YES" : "NO",
      controllerIp == IPAddress(0, 0, 0, 0)
          ? "none" : controllerIp.toString().c_str(),
      directOwnedMask,
      steeringUs,
      driveUs,
      fcPulseUs[0], fcChannelFresh(0, nowUs) ? '*' : '!',
      fcPulseUs[1], fcChannelFresh(1, nowUs) ? '*' : '!',
      fcPulseUs[2], fcChannelFresh(2, nowUs) ? '*' : '!',
      fcPulseUs[3], fcChannelFresh(3, nowUs) ? '*' : '!',
      currentCmd[0], currentCmd[1], currentCmd[2], currentCmd[3],
      static_cast<unsigned long>(fcMavRxBytes),
      static_cast<unsigned long>(fcMavRxFrames),
      static_cast<unsigned long>(fcMavTxBytes),
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
  Serial.println("=== TeleRC HYBRID ROVER CONTROLLER boot ===");

  pinMode(MODE_SELECT_PIN, INPUT_PULLDOWN);

  for (uint8_t i = 0; i < 4; ++i) {
    pinMode(FC_PWM_PIN[i], INPUT_PULLDOWN);
    pinMode(RPWM_PIN[i], OUTPUT);
    pinMode(LPWM_PIN[i], OUTPUT);
  }

  attachInterrupt(digitalPinToInterrupt(FC_PWM_PIN[0]), fc0ISR, CHANGE);
  attachInterrupt(digitalPinToInterrupt(FC_PWM_PIN[1]), fc1ISR, CHANGE);
  if (!AUTOPILOT_PAIRED_SKID_STEER) {
    attachInterrupt(digitalPinToInterrupt(FC_PWM_PIN[2]), fc2ISR, CHANGE);
    attachInterrupt(digitalPinToInterrupt(FC_PWM_PIN[3]), fc3ISR, CHANGE);
  }

  attachMotorPwm();
  hardStopMotors();

  fcMav.setRxBufferSize(4096);
  fcMav.begin(FC_MAV_BAUD, SERIAL_8N1, FC_MAV_RX_GPIO, FC_MAV_TX_GPIO);

  requestedMode = rawRequestedMode();
  lastRawRequestedMode = requestedMode;
  rawModeChangedMs = millis();

  const String password = loadApPassword();

  WiFi.persistent(false);
  WiFi.mode(WIFI_AP);

  if (!WiFi.softAPConfig(AP_IP, AP_IP, IPAddress(255, 255, 255, 0)) ||
      !WiFi.softAP(AP_SSID, password.c_str()) ||
      !udp.begin(UDP_PORT)) {
    Serial.println("FATAL: TeleRC hybrid controller startup failed.");
    hardStopMotors();
    while (true) delay(1000);
  }

  previousStationCount = WiFi.softAPgetStationNum();

  Serial.printf("Wi-Fi: %s @ %s:%u\n",
                AP_SSID,
                WiFi.softAPIP().toString().c_str(),
                UDP_PORT);
  Serial.printf("Wi-Fi password: %s\n", password.c_str());
  Serial.printf("Requested mode at boot: %s\n", modeName(requestedMode));
  Serial.println("GPIO16 LOW=DIRECT, HIGH=AUTOPILOT.");
  Serial.println("F405 PWM: M5->4, M6->5, M7->6, M8->7.");
  Serial.printf("F405 MAVLink: T3->GPIO%d RX, R3<-GPIO%d TX @ %lu baud\n",
                FC_MAV_RX_GPIO, FC_MAV_TX_GPIO,
                static_cast<unsigned long>(FC_MAV_BAUD));
  Serial.println("BTS: FL 8/9, RL 10/11, FR 12/13, RR 14/15.");
  Serial.println("Boot state: FAILSAFE; selected source must become neutral/stable.");
}

void loop() {
  handleUdpPacket();
  serviceFcMavlink();

  const uint8_t stations = WiFi.softAPgetStationNum();
  if (activeMode == ControlMode::DIRECT &&
      previousStationCount > 0 &&
      stations == 0 &&
      controllerIp != IPAddress(0, 0, 0, 0)) {
    enterFailsafe("Wi-Fi client lost");
    controllerIp = IPAddress(0, 0, 0, 0);
    lastControllerMs = 0;
  }
  previousStationCount = stations;

  const uint32_t nowUs = micros();
  updateModeArbiter(nowUs);

  if (uint32_t(nowUs - lastControlLoopUs) >= CONTROL_PERIOD_US) {
    lastControlLoopUs = nowUs;
    updateMotors(nowUs);
  }

  const uint32_t nowMs = millis();

  if (requestedMode == ControlMode::DIRECT &&
      nowMs - lastHeartbeatMs >= HEARTBEAT_PERIOD_MS) {
    lastHeartbeatMs = nowMs;
    sendHeartbeat();
  }

  if (nowMs - lastStatusMs >= STATUS_PERIOD_MS) {
    lastStatusMs = nowMs;
    sendCompatibilityStatus();
  }

  printDiagnostics(nowUs);
  delay(1);
}

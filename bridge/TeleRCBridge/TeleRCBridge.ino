#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <Preferences.h>
#include <esp_system.h>
#include <cstring>

// ESP32-S3 DevKitC-1 example. Change GPIOs if your S3 board exposes different pins.
// FC T3 -> GPIO18 (RX); FC R3 <- GPIO17 (TX); FC GND <-> ESP GND.
constexpr int FC_RX_GPIO = 18;
constexpr int FC_TX_GPIO = 17;
constexpr uint32_t FC_BAUD = 115200; // ArduRover SERIAL3_BAUD = 115
constexpr uint16_t UDP_PORT = 14550;
constexpr uint32_t CONTROL_WATCHDOG_MS = 500; // 5 missed 10 Hz control frames
constexpr uint32_t SAFE_HOLD_REFRESH_MS = 100; // keep neutral override alive at 10 Hz
const char DISCOVERY[] = "TELERC_DISCOVER_V1"; // routing only; never sent to the FC
const char DISCONNECT[] = "TELERC_DISCONNECT_V1"; // explicit app disconnect; never sent to the FC
const char AP_SSID[] = "TeleRC-Rover";
// Optional: enter your own 12-63 character Wi-Fi password here on your PC.
// Leave empty to generate a private password. Never commit your filled-in value.
const char PERSONAL_AP_PASSWORD[] = "";
// Generated once on first boot and retained in the ESP32's nonvolatile storage.
// Open USB Serial Monitor at 115200 to read the board's unique Wi-Fi password.
char apPassword[64] = {};
const IPAddress AP_IP(192, 168, 4, 1);
const IPAddress AP_BROADCAST(192, 168, 4, 255);

HardwareSerial fc(1);
WiFiUDP udp;
Preferences settings;
IPAddress phone(0, 0, 0, 0);
uint32_t lastPhonePacketMs = 0;
uint32_t lastControlMs = 0;
bool controlActive = false;
bool safeHoldActive = false;
uint32_t lastSafeHoldMs = 0;
uint8_t targetSystem = 0;
uint8_t bridgeSequence = 0;

uint8_t serialFrame[280];
uint16_t serialSize = 0;
uint16_t serialExpected = 0;
uint32_t serialByteMs = 0;
uint32_t serialBytesSeen = 0;
uint32_t serialFramesSeen = 0;
uint32_t fcHeartbeatCount = 0;
uint32_t lastFcHeartbeatMs = 0;
uint32_t lastFcHeartbeatGapMs = 0;
uint32_t maxFcHeartbeatGapMs = 0;
uint32_t lastDiagnosticMs = 0;
uint32_t discoveryCount = 0;
uint32_t commandCandidates = 0;
uint32_t commandsAccepted = 0;
uint32_t commandsRejected = 0;
uint32_t uartCommandBytesWritten = 0;
uint16_t lastSteer = 1500;
uint16_t lastDrive = 1500;
uint16_t driveMin = 1500;
uint16_t driveMax = 1500;
uint32_t driveChangedCount = 0;
const char *lastReject = "none";

enum class FailsafeReason : uint8_t {
  NONE = 0,
  APP_STOP,
  CONTROL_TIMEOUT,
  EXPLICIT_DISCONNECT,
};
uint32_t failsafeCount = 0;
FailsafeReason lastFailsafeReason = FailsafeReason::NONE;

size_t sendOverride(uint16_t value);

const char *failsafeReasonName(FailsafeReason reason) {
  switch (reason) {
    case FailsafeReason::APP_STOP: return "app-stop";
    case FailsafeReason::CONTROL_TIMEOUT: return "control-timeout";
    case FailsafeReason::EXPLICIT_DISCONNECT: return "explicit-disconnect";
    default: return "none";
  }
}

void triggerFailsafe(FailsafeReason reason) {
  if (!targetSystem) return;
  // Unexpected loss remains safety-first: actively hold neutral.
  uartCommandBytesWritten += sendOverride(1500);
  controlActive = false;
  safeHoldActive = true;
  lastSafeHoldMs = millis();
  lastSteer = 1500;
  lastDrive = 1500;
  ++failsafeCount;
  lastFailsafeReason = reason;
  Serial.printf("FAILSAFE neutral-hold reason=%s count=%lu\n",
                failsafeReasonName(reason),
                static_cast<unsigned long>(failsafeCount));
}

void releaseToReceiver() {
  if (!targetSystem) return;
  // Deliberate handover: neutral once, then release all RC override channels.
  // ArduRover can immediately resume the attached physical receiver.
  uartCommandBytesWritten += sendOverride(1500);
  uartCommandBytesWritten += sendOverride(0);
  controlActive = false;
  safeHoldActive = false;
  lastSteer = 1500;
  lastDrive = 1500;
  Serial.println("RC override released to physical receiver.");
}

// Keep Wi-Fi transport deliberately simple: start the SoftAP and UDP socket once
// in setup() and leave them running. Control safety is handled independently by
// the 500 ms watchdog and neutral-hold; normal phone association changes never
// restart the AP or rebind UDP.

uint16_t crcByte(uint16_t crc, uint8_t byte) {
  uint8_t tmp = byte ^ (crc & 0xff);
  tmp ^= tmp << 4;
  return (crc >> 8) ^ (uint16_t(tmp) << 8) ^ (uint16_t(tmp) << 3) ^ (tmp >> 4);
}

// Only explicitly whitelisted TeleRC MAVLink commands enter the FC.
// This filters malformed packets; CRC and source ID are not authentication.
bool validTeleRcCommand(const uint8_t *p, size_t n) {
  if (n != 26 || p[0] != 0xfe || p[1] != 18 || p[3] != 255 ||
      p[4] != 190 || p[5] != 70 || p[22] == 0 || p[22] == 255 || p[23] != 1)
    return false;
  uint16_t crc = 0xffff;
  for (size_t i = 1; i < 24; ++i) crc = crcByte(crc, p[i]);
  // Accept legacy TeleRC 0.8.9 frames, then send valid CRC 124 to ArduRover.
  const uint16_t standardCrc = crcByte(crc, 124);
  const uint16_t legacyCrc = crcByte(crc, 50);
  const uint16_t receivedCrc = uint16_t(p[24]) | (uint16_t(p[25]) << 8);
  if (receivedCrc != standardCrc && receivedCrc != legacyCrc) return false;
  bool release = true;
  for (int channel = 0; channel < 4; ++channel) {
    uint16_t value = uint16_t(p[6 + channel * 2]) |
                     (uint16_t(p[7 + channel * 2]) << 8);
    if (value != 0) release = false;
    if (value != 0 && (value < 1000 || value > 2000)) return false;
  }
  for (int channel = 4; channel < 8; ++channel) {
    if (p[6 + channel * 2] != 0xff || p[7 + channel * 2] != 0xff)
      return false;
  }
  // Release must release every channel, never a mixed command.
  if (release) return true;
  for (int channel = 0; channel < 4; ++channel) {
    if (p[6 + channel * 2] == 0 && p[7 + channel * 2] == 0) return false;
  }
  return true;
}

bool validTeleRcArmCommand(const uint8_t *p, size_t n) {
  if (n != 41 || p[0] != 0xfe || p[1] != 33 || p[3] != 255 ||
      p[4] != 190 || p[5] != 76 || p[36] == 0 || p[36] == 255 || p[37] != 1)
    return false;
  // Whitelist only MAV_CMD_COMPONENT_ARM_DISARM (400).
  if ((uint16_t(p[34]) | (uint16_t(p[35]) << 8)) != 400) return false;
  // param1 must be exactly float 0.0 or 1.0; param2..param7 must be zero.
  const bool disarm = p[6] == 0 && p[7] == 0 && p[8] == 0 && p[9] == 0;
  const bool arm = p[6] == 0 && p[7] == 0 && p[8] == 0x80 && p[9] == 0x3f;
  if (!disarm && !arm) return false;
  for (int i = 10; i < 34; ++i) if (p[i] != 0) return false;
  uint16_t crc = 0xffff;
  for (size_t i = 1; i < 39; ++i) crc = crcByte(crc, p[i]);
  crc = crcByte(crc, 152); // COMMAND_LONG CRC extra
  const uint16_t receivedCrc = uint16_t(p[39]) | (uint16_t(p[40]) << 8);
  return receivedCrc == crc;
}

// Recompute the outgoing CRC so ArduRover decodes legacy app packets.
size_t writeFcCommand(uint8_t *p, size_t n) {
  uint16_t crc = 0xffff;
  for (size_t i = 1; i < 24; ++i) crc = crcByte(crc, p[i]);
  crc = crcByte(crc, 124);
  p[24] = uint8_t(crc);
  p[25] = uint8_t(crc >> 8);
  return fc.write(p, n);
}

size_t sendOverride(uint16_t value) {
  uint8_t p[26] = {0xfe, 18, bridgeSequence++, 255, 190, 70};
  for (int i = 0; i < 4; ++i) {
    p[6 + i * 2] = uint8_t(value);
    p[7 + i * 2] = uint8_t(value >> 8);
  }
  for (int i = 4; i < 8; ++i) {
    p[6 + i * 2] = 0xff;
    p[7 + i * 2] = 0xff;
  }
  p[22] = targetSystem;
  p[23] = 1;
  uint16_t crc = 0xffff;
  for (size_t i = 1; i < 24; ++i) crc = crcByte(crc, p[i]);
  crc = crcByte(crc, 124);
  p[24] = uint8_t(crc);
  p[25] = uint8_t(crc >> 8);
  return fc.write(p, sizeof(p));
}

void toPhone(const uint8_t *frame, size_t length) {
  // TeleRC sends nothing before a heartbeat. Broadcast full frames until we
  // learn its address from a command, then use that address for five seconds.
  IPAddress destination =
      (phone != IPAddress(0, 0, 0, 0) && millis() - lastPhonePacketMs < 5000)
          ? phone : AP_BROADCAST;
  if (udp.beginPacket(destination, UDP_PORT)) {
    udp.write(frame, length);
    udp.endPacket(); // UDP source port is also 14550 (the bound socket).
  }
}

void consumeFcByte(uint8_t b) {
  serialBytesSeen++;
  if (serialSize && millis() - serialByteMs > 100) {
    serialSize = 0;
    serialExpected = 0;
  }
  serialByteMs = millis();
  if (!serialSize && b != 0xfe && b != 0xfd) return;
  serialFrame[serialSize++] = b;
  if (serialSize == 3) {
    bool v2 = serialFrame[0] == 0xfd;
    serialExpected = (v2 ? 10 : 6) + serialFrame[1] + 2 +
                     ((v2 && (serialFrame[2] & 1)) ? 13 : 0);
    if (serialExpected > sizeof(serialFrame)) {
      serialSize = 0;
      serialExpected = 0;
      return;
    }
  }
  if (serialExpected && serialSize == serialExpected) {
    serialFramesSeen++;

    const bool v2 = serialFrame[0] == 0xfd;
    const uint32_t msgId = v2
        ? (uint32_t(serialFrame[7]) |
           (uint32_t(serialFrame[8]) << 8) |
           (uint32_t(serialFrame[9]) << 16))
        : uint32_t(serialFrame[5]);
    const uint8_t componentId = v2 ? serialFrame[6] : serialFrame[4];

    if (msgId == 0 && componentId == 1) {
      const uint32_t now = millis();
      if (lastFcHeartbeatMs != 0) {
        lastFcHeartbeatGapMs = now - lastFcHeartbeatMs;
        if (lastFcHeartbeatGapMs > maxFcHeartbeatGapMs)
          maxFcHeartbeatGapMs = lastFcHeartbeatGapMs;
      }
      lastFcHeartbeatMs = now;
      ++fcHeartbeatCount;
    }

    toPhone(serialFrame, serialSize);
    serialSize = 0;
    serialExpected = 0;
  }
}

void setup() {
  Serial.begin(115200); // USB diagnostics, never the FC UART
  delay(1200); // let the ESP32-S3 USB CDC monitor attach after reset
  Serial.println("TeleRC bridge starting...");
  WiFi.mode(WIFI_AP);
  if (!settings.begin("telerc-ap", false)) {
    Serial.println("ERROR: Wi-Fi password storage unavailable. AP disabled.");
    while (true) delay(1000);
  }
  String saved;
  const size_t personalLength = strlen(PERSONAL_AP_PASSWORD);
  if (personalLength != 0) {
    if (personalLength < 12 || personalLength > 63) {
      Serial.println("ERROR: Personal Wi-Fi password must be 12-63 characters. AP disabled.");
      settings.end();
      while (true) delay(1000);
    }
    saved = PERSONAL_AP_PASSWORD;
  } else {
    saved = settings.getString("password", "");
  }
  if (personalLength == 0 && saved.length() != 16) {
    char generated[17];
    snprintf(generated, sizeof(generated), "%08lX%08lX",
             static_cast<unsigned long>(esp_random()),
             static_cast<unsigned long>(esp_random()));
    saved = generated;
    if (settings.putString("password", saved) != saved.length()) {
      Serial.println("ERROR: Could not save Wi-Fi password. AP disabled.");
      settings.end();
      while (true) delay(1000);
    }
  }
  saved.toCharArray(apPassword, sizeof(apPassword));
  settings.end();
  fc.setRxBufferSize(4096);
  fc.begin(FC_BAUD, SERIAL_8N1, FC_RX_GPIO, FC_TX_GPIO);
  if (!WiFi.softAPConfig(AP_IP, AP_IP, IPAddress(255, 255, 255, 0))) {
    Serial.println("ERROR: Wi-Fi AP IP configuration failed.");
    while (true) delay(1000);
  }
  if (!WiFi.softAP(AP_SSID, apPassword)) {
    Serial.println("ERROR: Wi-Fi AP failed to start.");
    while (true) delay(1000);
  }
  if (!udp.begin(UDP_PORT)) {
    Serial.println("ERROR: UDP port unavailable.");
    while (true) delay(1000);
  }
  Serial.printf("Wi-Fi name: %s\nWi-Fi password: %s\n", AP_SSID, apPassword);
  Serial.print("Bridge ready at ");
  Serial.println(WiFi.softAPIP());
}

void loop() {
  int packetSize = udp.parsePacket();
  if (packetSize > 0) {
    const IPAddress sender = udp.remoteIP();
    const uint16_t senderPort = udp.remotePort();
    uint8_t p[280];
    int count = udp.read(p, sizeof(p));
    while (udp.available()) udp.read();
    bool local = sender[0] == 192 && sender[1] == 168 && sender[2] == 4 &&
                 sender[3] > 1 && sender[3] < 255;
    bool paired = phone == IPAddress(0, 0, 0, 0) || phone == sender ||
                  millis() - lastPhonePacketMs >= 5000;
    if (packetSize == sizeof(DISCONNECT) - 1 && count == packetSize &&
        senderPort == UDP_PORT && local && paired &&
        memcmp(p, DISCONNECT, sizeof(DISCONNECT) - 1) == 0) {
      releaseToReceiver();
      lastFailsafeReason = FailsafeReason::EXPLICIT_DISCONNECT;
      phone = IPAddress(0, 0, 0, 0);
      lastPhonePacketMs = 0;
      Serial.println("TeleRC explicit disconnect; pairing cleared, neutral hold retained.");
    } else if (packetSize == sizeof(DISCOVERY) - 1 && count == packetSize &&
        senderPort == UDP_PORT && local && paired &&
        memcmp(p, DISCOVERY, sizeof(DISCOVERY) - 1) == 0) {
      discoveryCount++;
      phone = sender;
      lastPhonePacketMs = millis();
    } else if (packetSize == 26) {
      commandCandidates++;
      if (count != 26 || senderPort != UDP_PORT || !local || !paired) {
        commandsRejected++; lastReject = "source/port/length";
      } else if (!validTeleRcCommand(p, count)) {
        commandsRejected++; lastReject = "MAVLink frame/CRC";
      } else {
        phone = sender;
        lastPhonePacketMs = millis();
        targetSystem = p[22];
        bool release = p[6] == 0 && p[7] == 0;
        lastSteer = release ? 1500 : uint16_t(p[6]) | (uint16_t(p[7]) << 8);
        lastDrive = release ? 1500 : uint16_t(p[8]) | (uint16_t(p[9]) << 8);
        commandsAccepted++;
        if (release) {
          // All-zero override is an explicit operator handover request.
          releaseToReceiver();
        } else {
          if (lastDrive != 1500) {
            if (lastDrive < driveMin) driveMin = lastDrive;
            if (lastDrive > driveMax) driveMax = lastDrive;
            driveChangedCount++;
          }
          safeHoldActive = false;
          uartCommandBytesWritten += writeFcCommand(p, count);
          controlActive = true;
          lastControlMs = millis();
        }
      }
    } else if (packetSize == 41) {
      commandCandidates++;
      if (count != 41 || senderPort != UDP_PORT || !local || !paired) {
        commandsRejected++; lastReject = "arm source/port/length";
      } else if (!validTeleRcArmCommand(p, count)) {
        commandsRejected++; lastReject = "arm MAVLink frame/CRC";
      } else {
        phone = sender;
        lastPhonePacketMs = millis();
        targetSystem = p[36];
        uartCommandBytesWritten += fc.write(p, count);
        commandsAccepted++;
        Serial.printf("ARM/DISARM command forwarded to system %u\n", targetSystem);
      }
    }
  }
  for (int n = 0; n < 512 && fc.available(); ++n)
    consumeFcByte(uint8_t(fc.read()));
  if (controlActive && targetSystem &&
      millis() - lastControlMs >= CONTROL_WATCHDOG_MS) {
    triggerFailsafe(FailsafeReason::CONTROL_TIMEOUT);
  }
  if (safeHoldActive && targetSystem &&
      millis() - lastSafeHoldMs >= SAFE_HOLD_REFRESH_MS) {
    uartCommandBytesWritten += sendOverride(1500);
    lastSafeHoldMs = millis();
  }
  if (millis() - lastDiagnosticMs >= 1000) {
    lastDiagnosticMs = millis();
    if (phone != IPAddress(0, 0, 0, 0) && millis() - lastPhonePacketMs < 5000) {
      char report[176];
      int length = snprintf(report, sizeof(report), "TELERC_STATUS_V1,%lu,%lu,%lu,%lu,%lu,%u,%u,%u,%u,%lu,%lu,%lu,%lu",
                            static_cast<unsigned long>(serialBytesSeen),
                            static_cast<unsigned long>(serialFramesSeen),
                            static_cast<unsigned long>(commandsAccepted),
                            static_cast<unsigned long>(commandsRejected),
                            static_cast<unsigned long>(uartCommandBytesWritten),
                            lastSteer, lastDrive, driveMin, driveMax,
                            static_cast<unsigned long>(driveChangedCount),
                            static_cast<unsigned long>(fcHeartbeatCount),
                            static_cast<unsigned long>(lastFcHeartbeatGapMs),
                            static_cast<unsigned long>(maxFcHeartbeatGapMs));
      if (length > 0 && length < int(sizeof(report)) && udp.beginPacket(phone, UDP_PORT)) {
        udp.write(reinterpret_cast<const uint8_t *>(report), size_t(length));
        udp.endPacket();
      }
    }
    Serial.printf("FC UART bytes=%lu frames=%lu Wi-Fi clients=%d phone=%s\n",
                  static_cast<unsigned long>(serialBytesSeen),
                  static_cast<unsigned long>(serialFramesSeen),
                  WiFi.softAPgetStationNum(), phone.toString().c_str());
    Serial.printf("Commands discovery=%lu received=%lu accepted=%lu rejected=%lu "
                  "UART_TX_bytes=%lu CH1=%u CH2=%u CH2_min=%u CH2_max=%u CH2_changed=%lu "
                  "failsafe_count=%lu last_failsafe=%s FC_HB_count=%lu FC_HB_gap=%lums FC_HB_max=%lums reject=%s\n",
                  static_cast<unsigned long>(discoveryCount),
                  static_cast<unsigned long>(commandCandidates),
                  static_cast<unsigned long>(commandsAccepted),
                  static_cast<unsigned long>(commandsRejected),
                  static_cast<unsigned long>(uartCommandBytesWritten),
                  lastSteer, lastDrive, driveMin, driveMax,
                  static_cast<unsigned long>(driveChangedCount),
                  static_cast<unsigned long>(failsafeCount),
                  failsafeReasonName(lastFailsafeReason),
                  static_cast<unsigned long>(fcHeartbeatCount),
                  static_cast<unsigned long>(lastFcHeartbeatGapMs),
                  static_cast<unsigned long>(maxFcHeartbeatGapMs),
                  lastReject);
  }
  delay(1);
}

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
constexpr uint32_t AP_RETRY_MS = 5000;
constexpr uint32_t CONTROL_WATCHDOG_MS = 500; // 5 missed 10 Hz control frames
constexpr uint32_t SAFE_HOLD_REFRESH_MS = 100; // keep neutral override alive at 10 Hz
const char DISCOVERY[] = "TELERC_DISCOVER_V1"; // routing only; never sent to the FC
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
volatile bool apStopped = false;
volatile bool stationDisconnected = false;
uint32_t lastApRetryMs = 0;
uint32_t apRestartCount = 0;

enum class FailsafeReason : uint8_t {
  NONE = 0,
  APP_STOP,
  CONTROL_TIMEOUT,
  STATION_DISCONNECT,
  AP_STOP,
};
uint32_t failsafeCount = 0;
FailsafeReason lastFailsafeReason = FailsafeReason::NONE;

size_t sendOverride(uint16_t value);

const char *failsafeReasonName(FailsafeReason reason) {
  switch (reason) {
    case FailsafeReason::APP_STOP: return "app-stop";
    case FailsafeReason::CONTROL_TIMEOUT: return "control-timeout";
    case FailsafeReason::STATION_DISCONNECT: return "wifi-station-disconnect";
    case FailsafeReason::AP_STOP: return "wifi-ap-stop";
    default: return "none";
  }
}

void triggerFailsafe(FailsafeReason reason) {
  if (!targetSystem) return;
  // Do not release RC override here. Releasing can immediately hand throttle back
  // to a still-connected physical receiver. Hold neutral until TeleRC explicitly
  // starts a fresh live-control stream.
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

// Wi-Fi events run on another task. Only set flags here; repair the socket in loop().
void onWiFiEvent(WiFiEvent_t event) {
  if (event == ARDUINO_EVENT_WIFI_AP_STOP) apStopped = true;
  if (event == ARDUINO_EVENT_WIFI_AP_STADISCONNECTED)
    stationDisconnected = true;
}

bool startAccessPoint() {
  WiFi.mode(WIFI_AP);
  if (!WiFi.softAPConfig(AP_IP, AP_IP, IPAddress(255, 255, 255, 0)) ||
      !WiFi.softAP(AP_SSID, apPassword)) return false;
  // The UDP socket is tied to the network interface; rebind after an AP restart.
  udp.stop();
  if (!udp.begin(UDP_PORT)) {
    WiFi.softAPdisconnect(true);
    return false;
  }
  apStopped = false;
  Serial.print("TeleRC AP ready at ");
  Serial.println(WiFi.softAPIP());
  return true;
}

void serviceWiFi() {
  if (stationDisconnected) {
    stationDisconnected = false;
    // Safety-first: any station departure invalidates the active controller session.
    triggerFailsafe(FailsafeReason::STATION_DISCONNECT);
    phone = IPAddress(0, 0, 0, 0);
    lastPhonePacketMs = 0;
    Serial.println("Phone left ESP32 AP; control neutralized and pairing cleared.");
  }

  if (!apStopped) return;

  // Neutralize immediately when the AP itself stops. AP_RETRY_MS throttles only
  // network recovery; it must never delay the stop command to the flight controller.
  triggerFailsafe(FailsafeReason::AP_STOP);
  phone = IPAddress(0, 0, 0, 0);
  lastPhonePacketMs = 0;

  const uint32_t now = millis();
  if (now - lastApRetryMs < AP_RETRY_MS) return;
  lastApRetryMs = now;
  Serial.println("ESP32 AP stopped; restarting Wi-Fi and UDP...");
  udp.stop();
  if (startAccessPoint()) ++apRestartCount;
  else {
    apStopped = true;
    Serial.println("AP restart failed; retrying in five seconds.");
  }
}

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
    toPhone(serialFrame, serialSize);
    serialSize = 0;
    serialExpected = 0;
  }
}

void setup() {
  Serial.begin(115200); // USB diagnostics, never the FC UART
  delay(1200); // let the ESP32-S3 USB CDC monitor attach after reset
  Serial.println("TeleRC bridge starting...");
  WiFi.onEvent(onWiFiEvent);
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
  if (!startAccessPoint()) {
    Serial.println("ERROR: Wi-Fi AP failed to start; retrying.");
    apStopped = true;
  }
  Serial.printf("Wi-Fi name: %s\nWi-Fi password: %s\n", AP_SSID, apPassword);
}

void loop() {
  serviceWiFi();
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
    if (packetSize == sizeof(DISCOVERY) - 1 && count == packetSize &&
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
        lastDrive = release ? 1500 : uint16_t(p[10]) | (uint16_t(p[11]) << 8);
        commandsAccepted++;
        if (release) {
          // Older TeleRC APKs used all-zero RC override as "Stop control".
          // Convert that request to neutral-hold instead of forwarding a release.
          triggerFailsafe(FailsafeReason::APP_STOP);
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
      char report[160];
      int length = snprintf(report, sizeof(report), "TELERC_STATUS_V1,%lu,%lu,%lu,%lu,%lu,%u,%u,%u,%u,%lu",
                            static_cast<unsigned long>(serialBytesSeen),
                            static_cast<unsigned long>(serialFramesSeen),
                            static_cast<unsigned long>(commandsAccepted),
                            static_cast<unsigned long>(commandsRejected),
                            static_cast<unsigned long>(uartCommandBytesWritten),
                            lastSteer, lastDrive, driveMin, driveMax,
                            static_cast<unsigned long>(driveChangedCount));
      if (length > 0 && length < int(sizeof(report)) && udp.beginPacket(phone, UDP_PORT)) {
        udp.write(reinterpret_cast<const uint8_t *>(report), size_t(length));
        udp.endPacket();
      }
    }
    Serial.printf("FC UART bytes=%lu frames=%lu Wi-Fi clients=%d phone=%s AP_restarts=%lu\n",
                  static_cast<unsigned long>(serialBytesSeen),
                  static_cast<unsigned long>(serialFramesSeen),
                  WiFi.softAPgetStationNum(), phone.toString().c_str(),
                  static_cast<unsigned long>(apRestartCount));
    Serial.printf("Commands discovery=%lu received=%lu accepted=%lu rejected=%lu "
                  "UART_TX_bytes=%lu CH1=%u CH3=%u CH3_min=%u CH3_max=%u CH3_changed=%lu "
                  "failsafe_count=%lu last_failsafe=%s reject=%s\n",
                  static_cast<unsigned long>(discoveryCount),
                  static_cast<unsigned long>(commandCandidates),
                  static_cast<unsigned long>(commandsAccepted),
                  static_cast<unsigned long>(commandsRejected),
                  static_cast<unsigned long>(uartCommandBytesWritten),
                  lastSteer, lastDrive, driveMin, driveMax,
                  static_cast<unsigned long>(driveChangedCount),
                  static_cast<unsigned long>(failsafeCount),
                  failsafeReasonName(lastFailsafeReason), lastReject);
  }
  delay(1);
}

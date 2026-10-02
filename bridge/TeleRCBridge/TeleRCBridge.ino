#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <esp_system.h>
#include <cstring>

// TeleRC minimal ESP32-S3 MAVLink bridge.
// FC T3 -> GPIO18 (RX), FC R3 <- GPIO17 (TX), common GND.
constexpr int FC_RX_GPIO = 18;
constexpr int FC_TX_GPIO = 17;
constexpr uint32_t FC_BAUD = 115200;
constexpr uint16_t UDP_PORT = 14550;
constexpr uint32_t CONTROL_TIMEOUT_MS = 500;
constexpr uint32_t NEUTRAL_REFRESH_MS = 100;

const char AP_SSID[] = "TeleRC-Rover";
const char DISCOVERY[] = "TELERC_DISCOVER_V1";
const char DISCONNECT[] = "TELERC_DISCONNECT_V1";

const IPAddress AP_IP(192, 168, 4, 1);
const IPAddress AP_BROADCAST(192, 168, 4, 255);

HardwareSerial fc(1);
WiFiUDP udp;

IPAddress phone;
uint32_t lastPhoneMs = 0;
uint32_t lastControlMs = 0;
uint32_t lastNeutralMs = 0;
bool controlActive = false;
bool neutralHold = false;
uint8_t targetSystem = 0;
uint8_t sequence = 0;

uint8_t frame[280];
uint16_t frameSize = 0;
uint16_t frameExpected = 0;
uint32_t lastSerialByteMs = 0;
uint32_t serialBytes = 0;
uint32_t serialFrames = 0;
uint32_t acceptedCommands = 0;
uint32_t rejectedCommands = 0;
uint32_t uartCommandBytes = 0;
uint32_t armCommands = 0;
uint32_t lastStatusMs = 0;

uint16_t crcByte(uint16_t crc, uint8_t value) {
  uint8_t tmp = value ^ (crc & 0xff);
  tmp ^= tmp << 4;
  return (crc >> 8) ^ (uint16_t(tmp) << 8) ^ (uint16_t(tmp) << 3) ^ (tmp >> 4);
}

bool localPhone(const IPAddress &ip) {
  return ip[0] == 192 && ip[1] == 168 && ip[2] == 4 && ip[3] > 1 && ip[3] < 255;
}

bool pairedPhone(const IPAddress &ip) {
  return phone == IPAddress(0, 0, 0, 0) || phone == ip || millis() - lastPhoneMs >= 5000;
}

bool validRcOverride(const uint8_t *p, size_t n) {
  if (n != 26 || p[0] != 0xfe || p[1] != 18 || p[3] != 255 ||
      p[4] != 190 || p[5] != 70 || p[22] == 0 || p[22] == 255 || p[23] != 1)
    return false;

  uint16_t crc = 0xffff;
  for (size_t i = 1; i < 24; ++i) crc = crcByte(crc, p[i]);
  const uint16_t received = uint16_t(p[24]) | (uint16_t(p[25]) << 8);
  if (received != crcByte(crc, 124)) return false;

  bool release = true;
  for (int ch = 0; ch < 4; ++ch) {
    const uint16_t value = uint16_t(p[6 + ch * 2]) | (uint16_t(p[7 + ch * 2]) << 8);
    if (value != 0) release = false;
    if (value != 0 && (value < 1000 || value > 2000)) return false;
  }
  for (int ch = 4; ch < 8; ++ch) {
    if (p[6 + ch * 2] != 0xff || p[7 + ch * 2] != 0xff) return false;
  }
  if (release) return true;

  for (int ch = 0; ch < 4; ++ch) {
    if (p[6 + ch * 2] == 0 && p[7 + ch * 2] == 0) return false;
  }
  return true;
}

bool validArmCommand(const uint8_t *p, size_t n) {
  if (n != 41 || p[0] != 0xfe || p[1] != 33 || p[3] != 255 ||
      p[4] != 190 || p[5] != 76 || p[36] == 0 || p[36] == 255 || p[37] != 1)
    return false;

  if ((uint16_t(p[34]) | (uint16_t(p[35]) << 8)) != 400) return false;

  const bool disarm = p[6] == 0 && p[7] == 0 && p[8] == 0 && p[9] == 0;
  const bool arm = p[6] == 0 && p[7] == 0 && p[8] == 0x80 && p[9] == 0x3f;
  if (!disarm && !arm) return false;
  for (int i = 10; i < 34; ++i) if (p[i] != 0) return false;

  uint16_t crc = 0xffff;
  for (size_t i = 1; i < 39; ++i) crc = crcByte(crc, p[i]);
  crc = crcByte(crc, 152);
  return (uint16_t(p[39]) | (uint16_t(p[40]) << 8)) == crc;
}

size_t writeRcOverride(uint8_t *p, size_t n) {
  uint16_t crc = 0xffff;
  for (size_t i = 1; i < 24; ++i) crc = crcByte(crc, p[i]);
  crc = crcByte(crc, 124);
  p[24] = uint8_t(crc);
  p[25] = uint8_t(crc >> 8);
  const size_t written = fc.write(p, n);
  uartCommandBytes += written;
  return written;
}

size_t sendOverride(uint16_t value) {
  if (!targetSystem) return 0;

  uint8_t p[26] = {0xfe, 18, sequence++, 255, 190, 70};
  for (int ch = 0; ch < 4; ++ch) {
    p[6 + ch * 2] = uint8_t(value);
    p[7 + ch * 2] = uint8_t(value >> 8);
  }
  for (int ch = 4; ch < 8; ++ch) {
    p[6 + ch * 2] = 0xff;
    p[7 + ch * 2] = 0xff;
  }
  p[22] = targetSystem;
  p[23] = 1;

  uint16_t crc = 0xffff;
  for (size_t i = 1; i < 24; ++i) crc = crcByte(crc, p[i]);
  crc = crcByte(crc, 124);
  p[24] = uint8_t(crc);
  p[25] = uint8_t(crc >> 8);
  const size_t written = fc.write(p, sizeof(p));
  uartCommandBytes += written;
  return written;
}

void holdNeutral() {
  if (!targetSystem) return;
  sendOverride(1500);
  controlActive = false;
  neutralHold = true;
  lastNeutralMs = millis();
}

void releaseReceiver() {
  if (!targetSystem) return;
  sendOverride(1500);
  sendOverride(0);
  controlActive = false;
  neutralHold = false;
  Serial.println("RC override released.");
}

void sendToPhone(const uint8_t *data, size_t length) {
  const IPAddress destination =
      (phone != IPAddress(0, 0, 0, 0) && millis() - lastPhoneMs < 5000)
          ? phone : AP_BROADCAST;

  if (udp.beginPacket(destination, UDP_PORT)) {
    udp.write(data, length);
    udp.endPacket();
  }
}

void consumeFcByte(uint8_t b) {
  ++serialBytes;

  if (frameSize && millis() - lastSerialByteMs > 100) {
    frameSize = 0;
    frameExpected = 0;
  }
  lastSerialByteMs = millis();

  if (!frameSize && b != 0xfe && b != 0xfd) return;
  frame[frameSize++] = b;

  if (frameSize == 3) {
    const bool v2 = frame[0] == 0xfd;
    frameExpected = (v2 ? 10 : 6) + frame[1] + 2 +
                    ((v2 && (frame[2] & 1)) ? 13 : 0);
    if (frameExpected > sizeof(frame)) {
      frameSize = 0;
      frameExpected = 0;
    }
  }

  if (frameExpected && frameSize == frameExpected) {
    ++serialFrames;
    sendToPhone(frame, frameSize);
    frameSize = 0;
    frameExpected = 0;
  }
}

void makeBoardPassword(char *out, size_t size) {
  const uint64_t chipId = ESP.getEfuseMac();
  snprintf(out, size, "TR-%04lX-%08lX",
           static_cast<unsigned long>((chipId >> 32) & 0xFFFFUL),
           static_cast<unsigned long>(chipId & 0xFFFFFFFFUL));
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println();
  Serial.println("=== TeleRC ESP32-S3 bridge boot ===");

  char password[32] = {};
  makeBoardPassword(password, sizeof(password));

  fc.setRxBufferSize(4096);
  fc.begin(FC_BAUD, SERIAL_8N1, FC_RX_GPIO, FC_TX_GPIO);

  // Known-good ESP32-S3 AP startup. Keep transport startup simple: do not cycle
  // WIFI_OFF, restart the AP, or tie Wi-Fi lifetime to phone/control state.
  WiFi.persistent(false);
  WiFi.mode(WIFI_AP);

  if (!WiFi.softAPConfig(AP_IP, AP_IP, IPAddress(255, 255, 255, 0)) ||
      !WiFi.softAP(AP_SSID, password) ||
      !udp.begin(UDP_PORT)) {
    Serial.println("FATAL: TeleRC bridge startup failed.");
    while (true) delay(1000);
  }

  Serial.printf("TeleRC bridge ready: %s @ %s:%u\\n",
                AP_SSID, WiFi.softAPIP().toString().c_str(), UDP_PORT);
  Serial.printf("Wi-Fi password: %s\\n", password);
  Serial.printf("FC UART: RX GPIO%d / TX GPIO%d / %lu baud\\n",
                FC_RX_GPIO, FC_TX_GPIO, static_cast<unsigned long>(FC_BAUD));
}

void loop() {
  const int packetSize = udp.parsePacket();
  if (packetSize > 0) {
    const IPAddress sender = udp.remoteIP();
    const uint16_t senderPort = udp.remotePort();
    uint8_t p[280];
    const int count = udp.read(p, sizeof(p));
    while (udp.available()) udp.read();

    const bool sourceOk = senderPort == UDP_PORT && localPhone(sender) && pairedPhone(sender);

    if (sourceOk && packetSize == int(sizeof(DISCOVERY) - 1) && count == packetSize &&
        memcmp(p, DISCOVERY, sizeof(DISCOVERY) - 1) == 0) {
      phone = sender;
      lastPhoneMs = millis();
    }
    else if (sourceOk && packetSize == int(sizeof(DISCONNECT) - 1) && count == packetSize &&
             memcmp(p, DISCONNECT, sizeof(DISCONNECT) - 1) == 0) {
      // Explicit Disconnect is the only bridge-side receiver handover path.
      // Normal tab/app-background safety uses neutral hold instead.
      releaseReceiver();
      phone = IPAddress(0, 0, 0, 0);
      lastPhoneMs = 0;
      ++acceptedCommands;
    }
    else if (sourceOk && packetSize == 26 && count == 26 && validRcOverride(p, count)) {
      phone = sender;
      lastPhoneMs = millis();
      targetSystem = p[22];
      ++acceptedCommands;

      const bool release = p[6] == 0 && p[7] == 0;
      const uint16_t ch1 = uint16_t(p[6]) | (uint16_t(p[7]) << 8);
      const uint16_t ch2 = uint16_t(p[8]) | (uint16_t(p[9]) << 8);
      const bool neutral = !release && ch1 == 1500 && ch2 == 1500;

      if (release) {
        releaseReceiver();
      } else {
        writeRcOverride(p, count);
        controlActive = !neutral;
        neutralHold = neutral;
        lastControlMs = millis();
        if (neutral) lastNeutralMs = millis();
      }
    }
    else if (sourceOk && packetSize == 41 && count == 41 && validArmCommand(p, count)) {
      phone = sender;
      lastPhoneMs = millis();
      targetSystem = p[36];
      const size_t written = fc.write(p, count);
      uartCommandBytes += written;
      ++acceptedCommands;
      ++armCommands;
      Serial.printf("%s command forwarded.\n", p[8] == 0x80 ? "ARM" : "DISARM");
    }
    else if (localPhone(sender) && senderPort == UDP_PORT) {
      ++rejectedCommands;
    }
  }

  for (int i = 0; i < 512 && fc.available(); ++i)
    consumeFcByte(uint8_t(fc.read()));

  if (controlActive && targetSystem && millis() - lastControlMs >= CONTROL_TIMEOUT_MS)
    holdNeutral();

  if (neutralHold && targetSystem && millis() - lastNeutralMs >= NEUTRAL_REFRESH_MS) {
    sendOverride(1500);
    lastNeutralMs = millis();
  }

  if (millis() - lastStatusMs >= 1000) {
    lastStatusMs = millis();
    if (phone != IPAddress(0, 0, 0, 0) && millis() - lastPhoneMs < 5000) {
      char status[112];
      const int n = snprintf(status, sizeof(status), "TELERC_STATUS_V1,%lu,%lu,%lu,%lu,%lu,%lu",
                             static_cast<unsigned long>(serialBytes),
                             static_cast<unsigned long>(serialFrames),
                             static_cast<unsigned long>(acceptedCommands),
                             static_cast<unsigned long>(rejectedCommands),
                             static_cast<unsigned long>(uartCommandBytes),
                             static_cast<unsigned long>(armCommands));
      if (n > 0 && n < int(sizeof(status)) && udp.beginPacket(phone, UDP_PORT)) {
        udp.write(reinterpret_cast<const uint8_t *>(status), size_t(n));
        udp.endPacket();
      }
    }
  }

  delay(1);
}

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>

// ESP32-S3 DevKitC-1 example. Change GPIOs if your S3 board exposes different pins.
// FC T3 -> GPIO18 (RX); FC R3 <- GPIO17 (TX); FC GND <-> ESP GND.
constexpr int FC_RX_GPIO = 18;
constexpr int FC_TX_GPIO = 17;
constexpr uint32_t FC_BAUD = 115200; // ArduRover SERIAL3_BAUD = 115
constexpr uint16_t UDP_PORT = 14550;
const char AP_SSID[] = "TeleRC-Rover";
const char AP_PASSWORD[] = "CHANGE_TO_PRIVATE_PASSWORD";
const IPAddress AP_IP(192, 168, 4, 1);
const IPAddress AP_BROADCAST(192, 168, 4, 255);

HardwareSerial fc(1);
WiFiUDP udp;
IPAddress phone(0, 0, 0, 0);
uint32_t lastPhonePacketMs = 0;
uint32_t lastControlMs = 0;
bool controlActive = false;
uint8_t targetSystem = 0;
uint8_t bridgeSequence = 0;

uint8_t serialFrame[280];
uint16_t serialSize = 0;
uint16_t serialExpected = 0;
uint32_t serialByteMs = 0;

uint16_t crcByte(uint16_t crc, uint8_t byte) {
  uint8_t tmp = byte ^ (crc & 0xff);
  tmp ^= tmp << 4;
  return (crc >> 8) ^ (uint16_t(tmp) << 8) ^ (uint16_t(tmp) << 3) ^ (tmp >> 4);
}

// Only TeleRC's complete MAVLink 1 RC_CHANNELS_OVERRIDE commands enter the FC.
// This filters malformed packets; CRC and source ID are not authentication.
bool validTeleRcCommand(const uint8_t *p, size_t n) {
  if (n != 26 || p[0] != 0xfe || p[1] != 18 || p[3] != 255 ||
      p[4] != 190 || p[5] != 70 || p[22] == 0 || p[22] == 255 || p[23] != 1)
    return false;
  uint16_t crc = 0xffff;
  for (size_t i = 1; i < 24; ++i) crc = crcByte(crc, p[i]);
  crc = crcByte(crc, 50); // RC_CHANNELS_OVERRIDE CRC extra
  if (p[24] != uint8_t(crc) || p[25] != uint8_t(crc >> 8)) return false;
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

void sendOverride(uint16_t value) {
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
  crc = crcByte(crc, 50);
  p[24] = uint8_t(crc);
  p[25] = uint8_t(crc >> 8);
  fc.write(p, sizeof(p));
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
    toPhone(serialFrame, serialSize);
    serialSize = 0;
    serialExpected = 0;
  }
}

void setup() {
  Serial.begin(115200); // USB diagnostics, never the FC UART
  if (strcmp(AP_PASSWORD, "CHANGE_TO_PRIVATE_PASSWORD") == 0) {
    Serial.println("Set a private Wi-Fi password in the sketch before use.");
    while (true) delay(1000);
  }
  fc.setRxBufferSize(4096);
  fc.begin(FC_BAUD, SERIAL_8N1, FC_RX_GPIO, FC_TX_GPIO);
  WiFi.mode(WIFI_AP);
  WiFi.softAPConfig(AP_IP, AP_IP, IPAddress(255, 255, 255, 0));
  if (!WiFi.softAP(AP_SSID, AP_PASSWORD)) {
    Serial.println("Wi-Fi AP failed to start.");
    while (true) delay(1000);
  }
  if (!udp.begin(UDP_PORT)) {
    Serial.println("UDP port unavailable.");
    while (true) delay(1000);
  }
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
    if (packetSize == 26 && count == 26 && senderPort == UDP_PORT && local &&
        paired && validTeleRcCommand(p, count)) {
      phone = sender;
      lastPhonePacketMs = millis();
      targetSystem = p[22];
      bool release = p[6] == 0 && p[7] == 0;
      fc.write(p, count);
      controlActive = !release;
      lastControlMs = millis();
    }
  }
  for (int n = 0; n < 512 && fc.available(); ++n)
    consumeFcByte(uint8_t(fc.read()));
  if (controlActive && millis() - lastControlMs > 500 && targetSystem) {
    sendOverride(1500); // best effort neutral
    sendOverride(0);    // release to calibrated Flysky receiver
    controlActive = false;
  }
  delay(1);
}

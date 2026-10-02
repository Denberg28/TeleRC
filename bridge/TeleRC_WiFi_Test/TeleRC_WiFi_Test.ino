#include <Arduino.h>
#include <WiFi.h>

// TeleRC ESP32-S3 Wi-Fi-only diagnostic.
// If this sketch is running, "TeleRC-Rover-TEST" must be visible to nearby 2.4 GHz clients.

const char *SSID = "TeleRC-Rover-TEST";
const char *PASSWORD = "TeleRC-Test-2026";

void setup() {
  Serial.begin(115200);
  delay(1500);

  Serial.println();
  Serial.println("=== TeleRC Wi-Fi-only test ===");
  Serial.printf("Chip model: %s\\n", ESP.getChipModel());
  Serial.printf("Chip revision: %d\\n", ESP.getChipRevision());
  Serial.printf("CPU MHz: %u\\n", ESP.getCpuFreqMHz());

  WiFi.persistent(false);
  WiFi.mode(WIFI_OFF);
  delay(250);
  WiFi.mode(WIFI_AP);
  delay(250);

  const IPAddress ip(192, 168, 4, 1);
  const IPAddress mask(255, 255, 255, 0);

  if (!WiFi.softAPConfig(ip, ip, mask)) {
    Serial.println("FAIL: softAPConfig()");
    return;
  }

  if (!WiFi.softAP(SSID, PASSWORD, 6, false, 4)) {
    Serial.println("FAIL: WiFi.softAP()");
    return;
  }

  delay(500);

  Serial.println("PASS: Wi-Fi access point started.");
  Serial.printf("SSID: %s\\n", SSID);
  Serial.printf("Password: %s\\n", PASSWORD);
  Serial.printf("IP: %s\\n", WiFi.softAPIP().toString().c_str());
  Serial.printf("BSSID: %s\\n", WiFi.softAPmacAddress().c_str());
  Serial.printf("Channel: %d\\n", WiFi.channel());
}

void loop() {
  static uint32_t last = 0;
  if (millis() - last >= 2000) {
    last = millis();
    Serial.printf("AP alive | clients=%d | IP=%s\\n",
                  WiFi.softAPgetStationNum(),
                  WiFi.softAPIP().toString().c_str());
  }
  delay(10);
}

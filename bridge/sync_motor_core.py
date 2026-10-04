#!/usr/bin/env python3
"""Derive the UART motor core from the existing, standalone Wi-Fi hybrid sketch.

The legacy .ino stays independently flashable. --check detects drift in CI.
"""
import argparse
from pathlib import Path


def generate(source):
    source = source.replace('#include <WiFi.h>\n#include <WiFiUdp.h>', '#ifdef TELERC_UART_LINK\n#include <TeleRCSerialDatagram.h>\n#else\n#include <WiFi.h>\n#include <WiFiUdp.h>\n#endif')
    source = source.replace('WiFiUDP udp;', '#ifdef TELERC_UART_LINK\nHardwareSerial controlUart(2);\nTeleRCSerialDatagram udp(controlUart);\n#else\nWiFiUDP udp;\n#endif')
    source = source.replace('      WiFi.softAPgetStationNum());', '      transportStationCount());')
    pos = source.index('const char *modeName(ControlMode mode) {')
    source = source[:pos] + '''uint8_t transportStationCount() {
#ifdef TELERC_UART_LINK
  return 1; // UART presence never renews control freshness.
#else
  return WiFi.softAPgetStationNum();
#endif
}

''' + source[pos:]
    start = source.index('  const String password = loadApPassword();', source.index('void setup()'))
    end = source.index('  Serial.printf("Requested mode at boot:', start)
    source = source[:start] + '''#ifdef TELERC_UART_LINK
  controlUart.setRxBufferSize(1024);
  controlUart.setTxBufferSize(1024);
  controlUart.begin(115200, SERIAL_8N1, 21, 39); // RX21 <- T3 TX43; TX39 -> T3 RX44
  udp.begin(UDP_PORT);
  Serial.println("Dedicated motor controller: UART RX21/TX39; Wi-Fi disabled.");
#else
''' + source[start:end] + '#endif\n' + source[end:]
    source = source.replace('const uint8_t stations = WiFi.softAPgetStationNum();', 'const uint8_t stations = transportStationCount();')
    return '#pragma once\n' + source


def main():
    args = argparse.ArgumentParser()
    args.add_argument('--check', action='store_true')
    options = args.parse_args()
    root = Path(__file__).parent
    expected = generate((root / 'TeleRCHybridController/TeleRCHybridController.ino').read_text())
    target = root / 'libraries/TeleRCLink/src/TeleRCHybridCore.h'
    if options.check:
        if target.read_text() != expected:
            raise SystemExit('Motor core is stale; run python bridge/sync_motor_core.py')
    else:
        target.write_text(expected)


if __name__ == '__main__':
    main()

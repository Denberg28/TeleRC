#pragma once
#include "Arduino.h"
#include <deque>
#define SERIAL_8N1 0x800001c
struct HardwareSerial {
  explicit HardwareSerial(int) {}
  std::deque<uint8_t> incoming;
  bool ready=false; int rx=-1,tx=-1; unsigned baud=0;
  size_t setRxBufferSize(size_t n) { return n; }
  void begin(unsigned b,uint32_t,int r,int t,bool) { ready=true; baud=b; rx=r; tx=t; }
  explicit operator bool() const { return ready; }
  void setRxFIFOFull(uint8_t) {}
  void setRxTimeout(uint8_t) {}
  int available() { return incoming.size(); }
  int read() { if(incoming.empty()) return -1; int v=incoming.front(); incoming.pop_front(); return v; }
};

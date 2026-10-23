#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <deque>
#include <string>
using std::isfinite;
constexpr int LOW=0, HIGH=1, OUTPUT=1, INPUT_PULLUP=2;
inline uint64_t fakeUs=0;
inline bool stopLow=false, attachOK=true, writeOK=true;
inline int duty[64]={};
inline uint32_t micros(){ return (uint32_t)fakeUs; }
inline uint32_t millis(){ return (uint32_t)(fakeUs/1000); }
inline void delay(unsigned ms){fakeUs += (uint64_t)ms*1000;}
inline void delayMicroseconds(unsigned us){fakeUs+=us;}
inline void pinMode(int,int){}
inline void digitalWrite(int pin,int value){duty[pin]=value;}
inline int digitalRead(int){return stopLow?LOW:HIGH;}
inline bool ledcAttach(int,unsigned,int){return attachOK;}
inline bool ledcAttachChannel(int,unsigned,int,unsigned){return attachOK;}
inline uint32_t ledcRead(int pin){return uint32_t(duty[pin]);}
inline bool ledcDetach(int){return true;}
inline bool ledcWrite(int pin,unsigned value){duty[pin]=(int)value;return writeOK;}
struct FakeSerial {
  std::deque<char> input;
  std::string output;
  void begin(int){}
  void println(const char*){}
  int available(){return (int)input.size();}
  int read(){char c=input.front();input.pop_front();return c;}
  int availableForWrite(){return 64;}
  size_t write(const uint8_t*data,size_t n){output.append(reinterpret_cast<const char *>(data),n);return n;}
  void push(const std::string& s){for(char c:s)input.push_back(c);}
};
inline FakeSerial Serial;

struct FakeESP { unsigned restarts=0; void restart(){++restarts;} };
inline FakeESP ESP;

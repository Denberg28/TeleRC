#pragma once
#include "Arduino.h"
constexpr int WIFI_AP=2;
struct IPAddress {
  uint8_t b[4];
  IPAddress(uint8_t a=0,uint8_t c=0,uint8_t d=0,uint8_t e=0):b{a,c,d,e}{}
  uint8_t operator[](int i)const{return b[i];}
};
struct FakeWiFi {
  bool modeOK=true, ipOK=true, apOK=true;
  void persistent(bool){}
  bool mode(int){return modeOK;}
  bool setSleep(bool){return true;}
  bool softAPConfig(IPAddress,IPAddress,IPAddress){return ipOK;}
  bool softAP(const char*,const char*,int,int,int){return apOK;}
  unsigned softAPgetStationNum(){return 1;}
};
inline FakeWiFi WiFi;

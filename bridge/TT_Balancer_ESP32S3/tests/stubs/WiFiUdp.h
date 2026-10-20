#pragma once
#include "WiFi.h"
#include <vector>
struct WiFiUDP {
  std::vector<uint8_t> rx,lastTx;
  size_t cursor=0;
  IPAddress source{192,168,4,2};
  uint16_t port=14550;
  bool beginOK=true;
  int parsePacket(){return int(rx.size());}
  IPAddress remoteIP(){return source;}
  uint16_t remotePort(){return port;}
  int read(uint8_t *out,size_t count){
    size_t n=std::min(count,rx.size()-cursor);
    memcpy(out,rx.data()+cursor,n);cursor+=n;return int(n);
  }
  int available(){return int(rx.size()-cursor);}
  void flush(){cursor=rx.size();}
  bool begin(int){return beginOK;}
  bool beginPacket(IPAddress,uint16_t){lastTx.clear();return true;}
  size_t write(const uint8_t *b,size_t n){lastTx.insert(lastTx.end(),b,b+n);return n;}
  bool endPacket(){return true;}
};

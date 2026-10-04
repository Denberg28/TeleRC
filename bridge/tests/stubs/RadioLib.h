#pragma once
#include "Arduino.h"
#define RADIOLIB_ERR_NONE 0
struct Module {Module(int,int,int,int){}};
struct RadioMock {
 std::vector<uint8_t> rx;std::vector<std::vector<uint8_t>> tx;int result=0;
 RadioMock(Module*){}
 int startReceive(){return 0;}int standby(){rx.clear();return 0;}
 size_t getPacketLength(){return rx.size();}
 int readData(uint8_t*p,size_t n){if(n>rx.size())return -1;memcpy(p,rx.data(),n);rx.clear();return result;}
 int transmit(uint8_t*p,size_t n){tx.emplace_back(p,p+n);return result;}
};
struct SX1262:RadioMock {
 using RadioMock::RadioMock;
 int begin(float,float,uint8_t,uint8_t,uint8_t,int8_t,uint16_t,float=1.6,bool=false){return 0;}
 void setDio1Action(void(*)()){}
};
struct SX1276:RadioMock {
 using RadioMock::RadioMock;
 int begin(float,float,uint8_t,uint8_t,uint8_t,int8_t,uint16_t,uint8_t=0){return 0;}
 void setDio0Action(void(*)(),uint32_t){}
};

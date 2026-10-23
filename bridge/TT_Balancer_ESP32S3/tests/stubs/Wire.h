#pragma once
#include "Arduino.h"
#include <array>
struct FakeWire {
  std::array<uint8_t,256> regs{};
  std::array<unsigned,256> writes{};
  std::deque<uint8_t> tx,rx;
  uint8_t reg=0,address=0x68,lastReadAddress=0;
  bool ack68=true,ack69=false;
  uint8_t who69=0x68;
  int failReadReg=-1,failWriteReg=-1,mismatchReg=-1,transientWrites=0;
  bool busOK=true, ready=true, beginOK=true;
  unsigned readDelay=0;
  int whoAfterReset=-1;
  void (*frameHook)(unsigned)=nullptr;
  unsigned framesRead=0;
  FakeWire(){reset();}
  void reset(){regs.fill(0);writes.fill(0);whoAfterReset=-1;frameHook=nullptr;framesRead=0; regs[0x75]=0x68; busOK=ready=beginOK=true;
    ack68=true;ack69=false;who69=0x68;address=0x68;lastReadAddress=0;
    failReadReg=failWriteReg=mismatchReg=-1;transientWrites=0;
    readDelay=0; tx.clear(); rx.clear(); frame(0,0,1,0);}
  void frame(float ax,float ay,float az,float gy){
    auto word=[&](int index,float val,float scale){
      auto v=(uint16_t)(int16_t)std::lround(val*scale);
      regs[index]=(uint8_t)(v>>8); regs[index+1]=(uint8_t)v;
    };
    word(0x3B,ax,16384);word(0x3D,ay,16384);word(0x3F,az,16384);
    word(0x41,0,1);word(0x43,0,131);word(0x45,gy,131);word(0x47,0,131);
  }
  bool begin(int,int,unsigned){return beginOK;}
  void setTimeOut(int){}
  void beginTransmission(uint8_t a){address=a;tx.clear();}
  size_t write(uint8_t b){tx.push_back(b);return 1;}
  int endTransmission(bool=true){
    if(!busOK)return 4;
    if((address==0x68&&!ack68)||(address==0x69&&!ack69))return 2;
    if(tx.empty())return 0; // Address-only scanner probe.
    reg=tx.front();tx.pop_front();
    if(!tx.empty()) {
      if(reg==failWriteReg)return 3;
      if(reg==0x1A&&transientWrites>0){--transientWrites;return 3;}
      ++writes[reg];
      if(reg==0x6B&&tx.front()==0x80&&whoAfterReset>=0) {
        if(address==0x69)who69=(uint8_t)whoAfterReset;
        else regs[0x75]=(uint8_t)whoAfterReset;
      }
      if(reg!=mismatchReg)regs[reg]=tx.front();
    }
    return 0;
  }
  uint8_t requestFrom(uint16_t a,uint8_t n,bool){
    lastReadAddress=(uint8_t)a;
    fakeUs+=readDelay;rx.clear();if(!busOK||reg==failReadReg)return 0;
    if(reg==0x3B&&n==14) { if(frameHook)frameHook(framesRead); ++framesRead; }
    for(unsigned i=0;i<n;++i)
      rx.push_back(reg==0x3A?(ready?1:0):reg==0x75&&a==0x69?who69:regs[(uint8_t)(reg+i)]);
    return n;
  }
  int read(){auto v=rx.front();rx.pop_front();return v;}
};
inline FakeWire Wire;

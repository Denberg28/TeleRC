#pragma once
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <math.h>

// Restricted, unsigned MAVLink profile. CRC is integrity, not authentication.
namespace field {
enum class Kind { REJECT, HEARTBEAT, READ, HOLD, DESTINATION, SERVO };
inline uint16_t u16(const uint8_t*p){return p[0]|uint16_t(p[1])<<8;}
inline uint32_t u32(const uint8_t*p){return u16(p)|uint32_t(u16(p+2))<<16;}
inline float real(const uint8_t*p){float f;memcpy(&f,p,4);return f;}
inline uint16_t acc(uint16_t c,uint8_t b){uint8_t t=b^uint8_t(c);t^=t<<4;return(c>>8)^(uint16_t(t)<<8)^(uint16_t(t)<<3)^(t>>4);}
inline bool crc(const uint8_t*p,size_t n,uint8_t extra){uint16_t c=65535;for(size_t i=1;i<n-2;++i)c=acc(c,p[i]);return acc(c,extra)==u16(p+n-2);}
inline bool zeros(const uint8_t*p,size_t n){for(size_t i=0;i<n;++i)if(p[i])return false;return true;}
inline bool destination(const uint8_t*p,uint8_t sys,size_t at){return p[at]==sys&&p[at+1]==1;}
inline Kind classify(const uint8_t*p,size_t n,uint8_t sys){
 if(!sys||sys==255||n<8||p[0]!=0xfe||n!=size_t(p[1])+8||p[3]!=255||p[4]!=190)return Kind::REJECT;
 const uint8_t*q=p+6;
 if(p[5]==0&&p[1]==9&&crc(p,n,50)&&q[4]==6&&q[5]==8&&q[8]==3)return Kind::HEARTBEAT;
 if(p[5]==20&&p[1]==20&&crc(p,n,214)&&u16(q)==65535&&destination(q,sys,2)){
  bool text=false,end=false;
  for(unsigned i=4;i<20;++i){if(!q[i])end=true;else{if(end||q[i]<32||q[i]>126)return Kind::REJECT;text=true;}}
  return text?Kind::READ:Kind::REJECT;
 }
 if(p[5]==75&&p[1]==35&&crc(p,n,158)&&destination(q,sys,30)&&u16(q+28)==192&&q[32]==6&&q[33]==0&&q[34]==0){
  int32_t lat=int32_t(u32(q+16)),lon=int32_t(u32(q+20));
  if(real(q)==0.5f&&real(q+4)==1&&zeros(q+8,8)&&zeros(q+24,4)&&lat>=-850000000&&lat<=850000000&&lon>=-1800000000&&lon<=1800000000)return Kind::DESTINATION;
 }
 if(p[5]!=76||p[1]!=33||!crc(p,n,152)||!destination(q,sys,30)||q[32]>2)return Kind::REJECT;
 for(unsigned i=0;i<7;++i)if(!isfinite(real(q+i*4)))return Kind::REJECT;
 const uint16_t cmd=u16(q+28);
 if(cmd==176&&real(q)==1&&real(q+4)==4&&zeros(q+8,20))return Kind::HOLD;
 if(cmd==511&&zeros(q+8,20)&&real(q+4)>=200000&&real(q+4)<=10000000){
  float id=real(q);if(id==1||id==24||id==33||id==36||id==65||id==87||id==193||id==242)return Kind::READ;
 }
 if(cmd==183&&zeros(q+8,20)){
  float ch=real(q),pwm=real(q+4);
  if(ch>=1&&ch<=16&&ch==floorf(ch)&&!(ch>=5&&ch<=8)&&pwm>=1000&&pwm<=2000)return Kind::SERVO;
 }
 return Kind::REJECT;
}
struct Authority {
 uint8_t system=0;uint32_t heartbeatAt=0,rcAt=0,boot=0;bool seen=false,rcSeen=false,allowed=false;
 bool fresh(uint32_t now)const{return seen&&uint32_t(now-heartbeatAt)<4000;}
 bool phoneAllowed(uint32_t now)const{return fresh(now)&&rcSeen&&allowed&&uint32_t(now-rcAt)<750;}
 void observe(const uint8_t*p,size_t n,uint32_t now){
  if(n<8||(p[0]!=0xfe&&p[0]!=0xfd))return;
  bool v2=p[0]==0xfd;size_t h=v2?10:6;
  if(n<h+2||n!=h+p[1]+2||(v2&&(p[2]||p[8]||p[9])))return;
  uint8_t src=p[v2?5:3],comp=p[v2?6:4],id=p[v2?7:5];const auto*q=p+h;
  if(!src||src==255||comp!=1||(system&&src!=system))return;
  if(id==0&&p[1]==9&&crc(p,n,50)&&q[4]==10&&q[5]==3){system=src;heartbeatAt=now;seen=true;}
  if(id==65&&p[1]>=41&&p[1]<=42&&crc(p,n,118)&&system==src&&q[40]>=7){
   uint32_t next=u32(q);
   if(rcSeen&&int32_t(next-boot)<=0){if(next<boot){rcSeen=false;allowed=false;}return;}
   boot=next;rcAt=now;rcSeen=true;allowed=u16(q+16)>1800&&u16(q+16)<=2200;
  }
 }
};
}

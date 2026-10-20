#pragma once
#include <stdint.h>
#include <stddef.h>
namespace go_rf {
inline uint8_t crc8(const uint8_t *data,size_t n){uint8_t c=0;while(n--){c^=*data++;for(int i=0;i<8;i++)c=(c&0x80)?uint8_t((c<<1)^0xd5):uint8_t(c<<1);}return c;}
struct Channels {uint16_t us[16]={};};
/** Only complete checksum-valid packed RC frames can refresh control age. */
class Parser {
 uint8_t bytes[64]={};size_t count=0;uint32_t last=0;
 public:
 bool feed(uint8_t value,uint32_t now,Channels &out){
  if(count && uint32_t(now-last)>10)count=0;
  last=now;
  if(count==0){if(value!=0xc8)return false;bytes[count++]=value;return false;}
  if(count==1){if(value<2 || value>62){count=0;return false;}bytes[count++]=value;return false;}
  bytes[count++]=value;
  if(count<size_t(bytes[1])+2)return false;
  const size_t n=count;count=0;
  if(n!=26 || bytes[1]!=24 || bytes[2]!=0x16 || crc8(bytes+2,23)!=bytes[25])return false;
  for(unsigned channel=0;channel<16;channel++){
   const unsigned bit=channel*11, index=3+bit/8, shift=bit%8;
   uint32_t packed=bytes[index];if(index+1<25)packed|=uint32_t(bytes[index+1])<<8;if(index+2<25)packed|=uint32_t(bytes[index+2])<<16;
   const uint16_t raw=(packed>>shift)&0x7ff;
   if((channel==0||channel==1||channel==4||channel==5) && (raw<160 || raw>1840))return false;
   int us=1500+(int(raw)-992)*5/8;out.us[channel]=us<1000?1000:us>2000?2000:us;
  }
  return true;
 }
};
struct Authority {
 enum Owner:uint8_t{STOPPED=0,PHONE=1,TRANSMITTER=2};
 Owner owner=STOPPED;
 bool stop=false,haveRF=false,low=false,previousHigh=true;
 uint32_t rfAt=0,lowAt=0;
 bool fresh(uint32_t now)const{return haveRF && uint32_t(now-rfAt)<300;}
 void halt(){owner=STOPPED;low=false;previousHigh=true;}
 bool takePhone(){if(stop)return false;halt();owner=PHONE;return true;}
 bool observe(const Channels &c,uint32_t now){
  const bool wasFresh=fresh(now);haveRF=true;rfAt=now;
  stop=c.us[5]>1700;
  if(stop){halt();return false;}
  const bool neutral=c.us[0]>=1475&&c.us[0]<=1525&&c.us[1]>=1475&&c.us[1]<=1525;
  const bool high=c.us[4]>1700;
  if(owner==PHONE){low=false;previousHigh=high;return false;}
  if(!wasFresh){low=false;previousHigh=true;}
  if(c.us[4]<1300 && neutral){if(!low){low=true;lowAt=now;}}
  else if(!high)low=false;
  const bool arm=high&&!previousHigh&&low&&uint32_t(now-lowAt)>=300&&neutral;
  if(high)low=false;
  previousHigh=high;return arm;
 }
};
}

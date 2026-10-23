#pragma once
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <stdio.h>
namespace balance_admin {
constexpr char PREFIX[]="TELERC_BALANCE_V1|";
enum Action:uint8_t { NONE=0,CALIBRATE=1,LEFT=2,RIGHT=3,BOTH=4,VERIFY=5 };
struct Request { bool query=false,stop=false; uint32_t challenge=0,id=0; Action action=NONE; };
inline bool number(const char *&p,uint32_t &out,bool end=false) {
  if(*p<'0'||*p>'9')return false;
  out=0;
  for(unsigned n=0;*p>='0'&&*p<='9';++n,++p) {
    unsigned digit=unsigned(*p-'0');
    if(n>=10 || out>(UINT32_MAX-digit)/10)return false;
    out=out*10+digit;
  }
  if(end)return *p==0;
  if(*p!='|')return false;
  ++p;return true;
}
inline bool parse(const uint8_t *bytes,size_t n,Request &r) {
  r=Request{};
  if(n<sizeof(PREFIX)||n>96||memcmp(bytes,PREFIX,sizeof(PREFIX)-1))return false;
  char text[97];memcpy(text,bytes,n);text[n]=0;
  // Reject embedded NUL/non-printable bytes rather than parsing a valid prefix.
  for(size_t i=0;i<n;++i)if(bytes[i]<32||bytes[i]>126)return false;
  const char *p=text+sizeof(PREFIX)-1;
  if(!strcmp(p,"Q")){r.query=true;return true;}
  if(!strcmp(p,"S")){r.stop=true;return true;}
  uint32_t action=0;
  if(!number(p,r.challenge)||!number(p,r.id)||!number(p,action,true) ||
     !r.challenge||!r.id||action<1||action>5)return false;
  r.action=Action(action);return true;
}
struct Lease {
  uint32_t challenge=0,at=0,lastRequest=0;
  void reset(){challenge=at=lastRequest=0;}
  void issue(uint32_t random,uint32_t now){challenge=random?random:1;at=now;}
  bool consume(const Request &r,uint32_t now) {
    if(!challenge||r.challenge!=challenge||uint32_t(now-at)>1500||r.id==lastRequest)return false;
    challenge=0;lastRequest=r.id;return true;
  }
};
}

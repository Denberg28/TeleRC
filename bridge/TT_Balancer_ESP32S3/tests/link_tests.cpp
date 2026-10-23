#include <cassert>
#include <cstdio>
#include <vector>
#include "../LinkCore.h"
using namespace telerc;
int checks=0;
void check(bool b){assert(b);++checks;}
Packet rc(uint8_t sequence,uint16_t steer=1500,uint16_t drive=1500) {
  Packet p;p.message.id=70;p.message.sequence=sequence;
  p.message.system=255;p.message.component=190;p.message.length=18;
  auto &m=p.message;
  for(int i=0;i<8;++i)mini_mav::put16(m.payload+2*i,i==0?steer:i==1?drive:65535);
  m.payload[16]=1;m.payload[17]=1;return p;
}
Packet command(bool arm,uint8_t sequence=200) {
  Packet p;p.message.id=76;p.message.system=255;p.message.component=190;
  p.message.sequence=sequence;p.message.length=33;
  float value=arm?1:0;memcpy(p.message.payload,&value,4);
  mini_mav::put16(p.message.payload+28,400);
  p.message.payload[30]=p.message.payload[31]=1;return p;
}
std::vector<uint8_t> encode(const Packet &p) {
  std::vector<uint8_t> b(64);
  auto &m=p.message;
  size_t n=mini_mav::encode(b.data(),m.sequence,m.system,m.component,m.id,
    m.payload,m.length,m.id==70?124:m.id==76?152:50);
  b.resize(n);return b;
}
void bind(Core &core,uint32_t now=0) {
  Datagram d;d.count=1;d.packets[0].kind=Kind::DISCOVER;
  check(core.claim(0xC0A80402,14550,d,now));
  core.apply(d.packets[0],now,Status{});
}
void center(Core &c,Status s,uint32_t start=0) {
  for(uint8_t i=0;i<4;++i)c.apply(rc(i),start+i*100,s);
  check(c.input.motion&&c.input.fresh);
}
int main() {
  Datagram d;
  auto p=rc(7);mini_mav::put16(p.message.payload+4,1000);mini_mav::put16(p.message.payload+6,1500);
  auto bytes=encode(p);
  // Independent TeleRC Android MavlinkTest reference vector.
  check(bytes.size()==26&&bytes[24]==0x65&&bytes[25]==0x28);
  check(decode(bytes.data(),bytes.size(),d));
  for(size_t n=0;n<bytes.size();++n)check(!decode(bytes.data(),n,d));
  auto bad=bytes;bad.back()^=1;check(!decode(bad.data(),bad.size(),d));
  bad=bytes;bad.push_back(0);check(!decode(bad.data(),bad.size(),d));
  bad.clear();for(int i=0;i<5;++i)bad.insert(bad.end(),bytes.begin(),bytes.end());
  check(!decode(bad.data(),bad.size(),d));
  p=rc(1);p.message.system=5;bad=encode(p);check(!decode(bad.data(),bad.size(),d));
  p=rc(1);p.message.payload[16]=2;bad=encode(p);check(!decode(bad.data(),bad.size(),d));
  p=rc(1,999);bad=encode(p);check(!decode(bad.data(),bad.size(),d));
  auto arm=encode(command(true));check(decode(arm.data(),arm.size(),d));
  // Minimal valid MAVLink 2 command, with target component/confirmation truncated.
  std::vector<uint8_t> v2={0xfd,31,0,0,9,255,190,76,0,0};
  v2.insert(v2.end(),arm.begin()+6,arm.begin()+37);
  uint16_t crc=0xffff;for(size_t i=1;i<v2.size();++i)crc=mini_mav::accumulate(crc,v2[i]);
  crc=mini_mav::accumulate(crc,152);v2.push_back(uint8_t(crc));v2.push_back(uint8_t(crc>>8));
  check(decode(v2.data(),v2.size(),d));
  v2[2]=1;check(!decode(v2.data(),v2.size(),d)); // Reject signed/unsupported incompat flags.

  Status status;status.ready=true;
  Core c;bind(c);
  Datagram driveOnly;driveOnly.count=1;driveOnly.packets[0]=rc(10,1500,1900);
  Core unbound;check(!unbound.claim(0xC0A80402,14550,driveOnly,0));
  check(!c.claim(0xC0A80403,14550,driveOnly,0));
  check(!c.claim(0xC0A80402,14551,driveOnly,0));
  c.apply(rc(1,1500,1900),0,status);check(!c.input.fresh&&!c.input.motion);
  check(c.apply(command(true),0,status).result==2);
  center(c,status);
  c.apply(rc(4,1600,1700),400,status);check(c.input.drive==1700&&c.input.lastRC==400);
  c.apply(rc(4,1600,1900),450,status);check(c.input.lastRC==400); // Duplicate rejected.
  c.apply(rc(3,1600,1900),460,status);check(c.input.lastRC==400); // Reordered rejected.
  c.apply(rc(0),470,status);check(c.input.drive==1500); // Fixed-sequence safety neutral accepted.
  c.apply(rc(5,1600,1700),480,status);check(c.input.drive==1700);
  c.watchdog(480+CONTROL_TIMEOUT_MS,true);check(!c.input.motion&&!c.input.fresh);
  c.apply(rc(6,1500,1900),840,status);check(!c.input.fresh); // Recovered moving packets cannot resume.
  center(c,status,900);
  c.apply(rc(4,1500,1800),1210,status);check(c.input.drive==1800);
  // Heartbeat/discovery never refresh lastRC and cannot mask a motion timeout.
  Packet hello;hello.kind=Kind::DISCOVER;c.apply(hello,1500,status);
  c.watchdog(1210+CONTROL_TIMEOUT_MS,true);check(!c.input.fresh);
  c.watchdog(1510,false);check(!c.peer&&!c.input.linked);

  Core a;bind(a);a.apply(rc(0),10,status);
  auto reply=a.apply(command(true,200),11,status);
  check(reply.ack&&reply.result==5&&a.input.armToken==1);
  a.apply(command(true,201),51,status);a.apply(command(true,202),91,status);
  check(a.input.armToken==1&&a.input.armAt==11); // Retries never extend request age.
  a.armFinished(100);check(a.apply(command(true),120,status).result==2);
  a.watchdog(701,true);a.apply(rc(0),702,status);
  check(a.apply(command(true),703,status).result==5&&a.input.armToken==2);
  a.apply(command(false),704,status);check(a.input.disarmToken==1&&!a.input.fresh&&!a.input.armPending);
  Packet forced=command(true);float force=21196;memcpy(forced.message.payload+4,&force,4);
  check(a.apply(forced,710,status).result==2);
  auto unsupported=command(true);mini_mav::put16(unsupported.message.payload+28,183);
  check(a.apply(unsupported,711,status).result==3); // No servo output or mission execution.
  a.watchdog(800,true);check(!a.input.fresh);
  a.apply(rc(4,65535,1500),801,status);check(!a.input.fresh); // Ignore values cannot refresh lease.
  a.apply(rc(4,0,0),802,status);check(!a.input.fresh&&!a.input.motion);
  Packet bye;bye.kind=Kind::DISCONNECT;a.apply(bye,803,status);check(!a.peer);

  Core wrap;bind(wrap,0xffffff00);center(wrap,status,0xffffff00);
  wrap.apply(rc(250),50,status);wrap.movingSequence=250;wrap.sequenceKnown=true;
  wrap.apply(rc(2,1500,1700),60,status);check(wrap.input.drive==1700);
  wrap.watchdog(60+CONTROL_TIMEOUT_MS,true);check(!wrap.input.fresh);
  check(axis(1100)==-1&&axis(1900)==1&&axis(1500)==0);
  using B=ArmBlock;
  Core reasons;bind(reasons);Status gated;gated.physicalBlock=B::VERIFY_DIRECTIONS;
  auto denied=reasons.apply(command(true),1,gated);
  check(denied.result==2&&denied.block==B::VERIFY_DIRECTIONS);
  gated.ready=true;gated.physicalBlock=B::NONE;
  check(reasons.apply(command(true),2,gated).block==B::NO_RC);
  center(reasons,gated,10);
  reasons.apply(rc(4,1600,1700),320,gated);
  check(reasons.apply(command(true),321,gated).block==B::CENTER_STICKS);
  reasons.watchdog(700,true);
  check(reasons.apply(command(true),701,gated).block==B::RC_STALE);
  gated.ready=false;gated.physicalBlock=B::HOLD_UPRIGHT;
  check(reasons.apply(command(true),702,gated).block==B::HOLD_UPRIGHT);
  gated.fault=true;gated.physicalBlock=B::FAULT;
  check(reasons.apply(command(true),703,gated).block==B::FAULT);
  // Deterministic malformed-datagram fuzz: bounded decode, memory sanitizers active.
  uint32_t seed=1234567;uint8_t fuzz[300];
  for(int i=0;i<20000;++i) {
    seed=seed*1664525+1013904223;size_t n=seed%sizeof(fuzz);
    for(size_t j=0;j<n;++j){seed=seed*1664525+1013904223;fuzz[j]=uint8_t(seed>>24);}
    decode(fuzz,n,d);
  }
  std::printf("TeleRC protocol/session: %d assertions + 20000 malformed packets PASS\n",checks);
}

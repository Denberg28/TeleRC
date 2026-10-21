#include <cassert>
#include <cmath>
#include <fstream>
#include <iterator>
#include <iostream>
#define TELERC_HOST_TEST 1
#include "../TeleRC_Go_Rover/TeleRC_Go_Rover.ino"
const IPAddress phone(192,168,4,2), other(192,168,4,3);
std::vector<uint8_t> frame(uint8_t id,const uint8_t *p,uint8_t len,uint8_t extra) {
  uint8_t out[80]; static uint8_t seq=0;
  size_t n=mini_mav::encode(out,seq++,255,190,id,p,len,extra);
  return {out,out+n};
}
void receive(std::vector<uint8_t> bytes,IPAddress source=phone,uint16_t port=14550) {
  udp.incoming.push_back({source,port,bytes}); watchdog(fakeTime); receiveOne(); watchdog(fakeTime); updateMotors(fakeTime);
}
void discover(IPAddress source=phone) { receive({DISCOVERY,DISCOVERY+sizeof(DISCOVERY)-1},source); }
void rc(uint16_t s=1500,uint16_t d=1500) {
  uint8_t p[18]={}; mini_mav::put16(p,s);mini_mav::put16(p+2,d);p[16]=1;p[17]=1;receive(frame(70,p,18,124));
}
void command(bool arm) {
  uint8_t p[33]={};float f=arm?1.f:0.f;memcpy(p,&f,4);mini_mav::put16(p+28,400);p[30]=p[31]=1;receive(frame(76,p,33,152));
}
void advance(uint32_t ms){fakeTime+=ms;watchdog(fakeTime);updateMotors(fakeTime);}
void neutral(){for(int i=0;i<4;++i){rc();advance(100);}}
void zero(){for(unsigned p:{4,5,6,7,8,9,10,11})assert(duties[p]==0);}
int main(int argc,char **argv){
  assert(rover::axis(1900)==1.0f && rover::axis(1100)==-1.0f);
  assert(rover::axis(2000)==1.0f && rover::axis(1000)==-1.0f);
  assert(rover::axis(1525)==0 && rover::axis(1475)==0);
  fakeTime=100;setup();assert(pwmReady&&wifiReady&&!state.armed);zero();
  command(true);assert(!state.armed); // No discovery and no neutral.
  // A competing Android controller may own 14550; a fresh source port is valid.
  receive({DISCOVERY,DISCOVERY+sizeof(DISCOVERY)-1},phone,49152);
  assert(peer==phone&&peerPort==49152&&!state.armed);
  receive({DISCONNECT,DISCONNECT+sizeof(DISCONNECT)-1},phone,49152);
  assert(peer==ZERO_IP);
  discover();command(true);assert(!state.armed);
  rc(1900,1900);command(true);assert(!state.armed);zero();
  neutral();command(true);assert(state.armed);rc(1500,1900);advance(5);
  for(unsigned pin:{4,6,8,10}) assert(duties[pin]==242); // Full MX1508 drive reaches the 95% ceiling.
  for(unsigned pin:{5,7,9,11}) assert(duties[pin]==0);
  assert(hardware::MOTORS[0].a==4 && hardware::MOTORS[0].b==5);
  assert(hardware::MOTORS[1].a==6 && hardware::MOTORS[1].b==7);
  assert(hardware::MOTORS[2].a==8 && hardware::MOTORS[2].b==9);
  assert(hardware::MOTORS[3].a==10 && hardware::MOTORS[3].b==11);
  assert(hardware::PWM_HZ==20000);
  // Sweep the full accepted range: scaling stays monotonic and never exceeds 95%.
  int16_t previousDemand=-242;
  for (int channel=1000;channel<=2000;++channel) {
    rover::State scaled; scaled.armed=true; scaled.inhibited=false; scaled.haveRC=true;
    scaled.steer=1500; scaled.drive=channel; int16_t l=0,r=0; scaled.mix(l,r);
    assert(l==r && std::abs(l)<=242 && l>=previousDemand); previousDemand=l;
    if(channel==1500) assert(l==0);
  }
  rc(1500,1880);advance(5); // App Drive sensitivity 95% multiplies the sketch cap.
  for(unsigned pin:{4,6,8,10}) assert(duties[pin]==229);
  rc(1500,1600);advance(5); // Full app stick at retained 25% sensitivity.
  for(unsigned pin:{4,6,8,10}) assert(duties[pin]==48); // Sensitivity still scales the command.
  rc(1500,1700);advance(5);
  for(unsigned pin:{4,6,8,10}) assert(duties[pin]==113); // Proportional drive, no minimum-duty jump.
  rc(1900,1500);advance(5);advance(30); // Full steering pivots at the same 95% ceiling.
  for(unsigned pin:{4,6,9,11}) assert(duties[pin]==242);
  for(unsigned pin:{5,7,8,10}) assert(duties[pin]==0);
  rc(1100,1500);advance(5);zero();advance(30); // Opposite pivot preserves reversal coast.
  for(unsigned pin:{5,7,8,10}) assert(duties[pin]==242);
  for(unsigned pin:{4,6,9,11}) assert(duties[pin]==0);
  rc(1900,1900);advance(5);advance(30); // Combined input is normalized, never wraps duty.
  for(unsigned pin:{4,6}) assert(duties[pin]==242);
  for(unsigned pin:{5,7,8,9,10,11}) assert(duties[pin]==0);
  rc();advance(5);zero(); // Neutral remains exactly zero, with no launch boost.
  rc(1500,1900);advance(5);

  auto oldRC=state.lastRC;advance(200);discover();assert(state.lastRC==oldRC); // Discovery cannot feed motor watchdog.
  advance(300);assert(!state.armed);zero();
  rc(1900,1900);command(true);assert(!state.armed);zero(); // Recovery needs fresh centered dwell.
  neutral();command(true);assert(state.armed);
  rc(1500,1000);advance(5);assert(duties[4]==0); // Reversal is coast first.
  advance(30);assert(duties[5]>0);
  command(false);assert(!state.armed);zero(); // Disarm overrides moving axes.
  neutral();command(true);assert(state.armed);rc(0,0);assert(!state.armed);zero(); // Release disarms too.
  neutral();command(true);assert(state.armed);
  receive({DISCONNECT,DISCONNECT+sizeof(DISCONNECT)-1},other);assert(state.armed); // Non-owner cannot inject.
  receive({DISCONNECT,DISCONNECT+sizeof(DISCONNECT)-1});assert(!state.armed&&peer==ZERO_IP);zero();
  discover();neutral();command(true);rc(1500,1900);advance(5);
  WiFi.stations=0;watchdog(fakeTime);assert(!state.armed&&peer==ZERO_IP);zero();WiFi.stations=1;
  discover();neutral();command(true);pins[0]=LOW;watchdog(fakeTime);assert(!state.armed);zero();
  neutral();command(true);assert(!state.armed);pins[0]=HIGH;watchdog(fakeTime);neutral();command(true);assert(state.armed);
  command(false);auto acceptedBefore=accepted;
  uint8_t p[33]={};float f=1;memcpy(p,&f,4);mini_mav::put16(p+28,400);p[30]=p[31]=1;p[4]=1;
  receive(frame(76,p,33,152));assert(!state.armed); // Force or extra params rejected.
  neutral();uint8_t r[18]={};mini_mav::put16(r,1500);mini_mav::put16(r+2,1500);r[16]=r[17]=1;
  auto good=frame(70,r,18,124);auto corrupt=good;corrupt.back()^=1;
  auto before=state.lastRC;advance(10);receive(corrupt);assert(state.lastRC==before);
  good.push_back(0xfe);before=state.lastRC;receive(good);assert(state.lastRC==before); // Entire datagram validated first.
  for(size_t n=1;n<42;++n)receive(std::vector<uint8_t>(n,0xfd)); // Truncated decoder/bounds coverage.
  receive(std::vector<uint8_t>(300,0xfe));assert(!state.armed);zero();
  // Millis wrap still expires. Direct core check avoids unrelated peer lease.
  rover::State wrap;wrap.rc(1500,1500,0xffffff00u);wrap.rc(1500,1500,0xfffffffau);assert(wrap.arm(0xfffffffau));wrap.tick(0x200u);assert(!wrap.armed);
  if(argc==2) { // Consume exact command frames emitted by Kotlin unit tests.
    for(const char *name:{"neutral","drive","release","arm","disarm","hold"}){
      std::ifstream in(std::string(argv[1])+"/"+name+".bin",std::ios::binary);assert(in.good());
      std::vector<uint8_t>b{std::istreambuf_iterator<char>(in),{}};mini_mav::Message m;size_t used=0;
      assert(mini_mav::decode(b.data(),b.size(),m,used)&&used==b.size());assert(m.system==255&&m.component==190);
      if(m.id==70)assert(overrideOK(m));else assert(m.id==76&&(mini_mav::u16(m.payload+28)==400||mini_mav::u16(m.payload+28)==176));
    }
  }
  // Exact accepted ARM -> all four outputs; a PWM write error must revoke ARM,
  // detach every output to GPIO-low, report a fault and reject subsequent ARM.
  neutral();command(true);assert(state.armed);rc(1500,1900);advance(40);
  Serial.txSpace=0;advance(1000);serviceDiagnostics(fakeTime);assert(!state.armed);zero();
  Serial.txSpace=64;
  for(unsigned i=0;i<30;++i) serviceDiagnostics(fakeTime);
  assert(Serial.written.find("CH1=")!=std::string::npos && Serial.written.find("PWM=")!=std::string::npos);
  neutral();command(true);assert(state.armed);rc(1500,1900);advance(40);
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  failWritePin=4;rc(1500,1800);advance(5);
  assert(motorBank.fault && !motorBank.ready && !pwmReady && !state.armed);
  assert(motorBank.attached==0 && motorBank.writeErrors>0);zero();
  for(unsigned pin:{4,5,6,7,8,9,10,11}) assert(pins[pin]==LOW);
  failWritePin=-1;neutral();command(true);assert(!state.armed);zero();
#endif
  assert(accepted>acceptedBefore);std::cout<<"Mini Wi-Fi safety and protocol checks passed\n";
}

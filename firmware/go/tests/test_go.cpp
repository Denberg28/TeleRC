#include <cassert>
#include <vector>
#include <iostream>
#include <random>
#include "../TeleRC_Go_Rover/CrsfReceiver.h"
#define TELERC_HOST_TEST 1
#include "../TeleRC_Go_Rover/TeleRC_Go_Rover.ino"
static std::vector<uint8_t> rf(uint16_t arm=172,uint16_t stop=172,uint16_t drive=992,uint16_t source=172){
 std::vector<uint8_t> b(26);b[0]=0xc8;b[1]=24;b[2]=0x16;
 for(unsigned c=0;c<16;c++){unsigned raw=c==4?arm:c==5?stop:c==1?drive:c==6?source:992;for(unsigned bit=0;bit<11;bit++)if(raw&(1u<<bit))b[3+(c*11+bit)/8]|=1u<<((c*11+bit)%8);}
 b[25]=go_rf::crc8(b.data()+2,23);return b;
}
static void serialFrame(uint16_t arm,uint16_t stop,uint16_t drive,uint32_t now,uint16_t source=172){fakeTime=now;for(auto b:rf(arm,stop,drive,source))rfSerial.incoming.push_back(b);serviceReceiver(now);watchdog(now);updateMotors(now);}
static void zero(){for(unsigned pin:{4,5,6,7,8,9,10,11})assert(duties[pin]==0);}
int main(){
 go_rf::Parser p;go_rf::Channels channels;unsigned acceptedCount=0;
 for(auto b:rf())if(p.feed(b,1,channels))acceptedCount++;
 assert(acceptedCount==1&&channels.us[0]==1500&&channels.us[1]==1500&&channels.us[4]==1000);
 auto corrupt=rf();corrupt.back()^=1;for(auto b:corrupt)assert(!p.feed(b,2,channels));
 auto truncated=rf();for(unsigned i=0;i<10;i++)assert(!p.feed(truncated[i],3,channels));
 for(auto b:rf())p.feed(b,30,channels); // inter-byte gap recovers framing
 go_rf::Authority a;channels.us[0]=channels.us[1]=1500;channels.us[4]=1000;channels.us[5]=1000;channels.us[6]=1000;
 assert(!a.observe(channels,100));assert(!a.observe(channels,250));assert(!a.observe(channels,400));channels.us[4]=2000;assert(a.observe(channels,450));a.owner=go_rf::Authority::TRANSMITTER;assert(a.fresh(749));assert(!a.fresh(750));a.halt();assert(!a.observe(channels,760));
 channels.us[6]=2000;a.observe(channels,780);assert(a.takePhone());channels.us[5]=2000;assert(!a.observe(channels,800));assert(a.stop&&a.owner==go_rf::Authority::STOPPED);assert(!a.takePhone());
 fakeTime=100;setup();assert(rfSerial.baud==420000&&rfSerial.rx==39);zero();
 for(uint32_t t=200;t<=600;t+=100)serialFrame(172,172,992,t);
 serialFrame(1811,172,992,650);assert(state.armed&&authority.owner==go_rf::Authority::TRANSMITTER);
 serialFrame(1811,172,1811,700);assert(state.armed);assert(duties[4]>0&&duties[6]>0&&duties[8]>0&&duties[10]>0);
 // Phone observer loss must not stop the independent transmitter.
 peer=IPAddress(192,168,4,2);peerPort=14550;lastPeer=700;WiFi.stations=0;watchdog(710);assert(state.armed&&authority.owner==go_rf::Authority::TRANSMITTER);WiFi.stations=1;
 serviceReceiver(1000);assert(!state.armed&&authority.owner==go_rf::Authority::STOPPED);zero();
 serialFrame(1811,172,1811,1010);assert(!state.armed);zero();
 for(uint32_t t=1100;t<=1500;t+=100)serialFrame(172,172,992,t);
 serialFrame(1811,172,992,1550);assert(state.armed);
 serialFrame(1811,1811,1811,1600);assert(!state.armed&&authority.stop);zero();
 serialFrame(1811,172,992,1650);assert(!state.armed);zero();
 // Explicit phone takeover disarms; RF cannot arm or steer while phone owns.
 stopRover(true,"TEST");serialFrame(172,172,992,1680,1811);assert(authority.takePhone());
 state.rc(1500,1500,1700);state.rc(1500,1500,2000);assert(state.arm(2000));
 serialFrame(1811,172,1811,2010,1811);assert(authority.owner==go_rf::Authority::PHONE&&state.drive==1500);
 serialFrame(1811,1811,1811,2020,1811);assert(!state.armed);zero();
 // UART overload is discarded, never replayed into current motion.
 for(unsigned i=0;i<100;i++)rfSerial.incoming.push_back(0);
 serviceReceiver(2100);assert(rfSerial.incoming.empty());assert(!state.armed);zero();

 // SA can revoke an armed phone, but never transfers a moving command or held ARM.
 serialFrame(172,172,992,2200,1811);assert(authority.takePhone());
 state.rc(1500,1500,2210);state.rc(1500,1500,2510);assert(state.arm(2510));
 serialFrame(1811,172,1811,2520,172);assert(!state.armed&&authority.owner==go_rf::Authority::STOPPED);zero();
 assert(!authority.takePhone()); // Switch still selects radio.
 serialFrame(1811,172,992,2530,172);assert(!state.armed);
 for(uint32_t t=2600;t<=3000;t+=100)serialFrame(172,172,992,t,172);
 serialFrame(1811,172,992,3050,172);assert(state.armed&&authority.owner==go_rf::Authority::TRANSMITTER);
 // Stale phone packets (neutral, release, TAKE, DISARM, disconnect) cannot stop RF.
 uint8_t payload[33]={};float disarm=0;memcpy(payload,&disarm,4);mini_mav::put16(payload+28,400);payload[30]=payload[31]=1;
 mini_mav::Message delayed;delayed.id=76;delayed.system=255;delayed.component=190;memcpy(delayed.payload,payload,33);
 dispatch(delayed,3060);assert(state.armed&&authority.owner==go_rf::Authority::TRANSMITTER);
 mini_mav::Message release;release.id=70;release.system=255;release.component=190;memset(release.payload,0,sizeof(release.payload));release.payload[16]=release.payload[17]=1;
 assert(!dispatch(release,3070));assert(state.armed);
 float take=1;memcpy(delayed.payload,&take,4);mini_mav::put16(delayed.payload+28,31010);dispatch(delayed,3080);assert(state.armed&&authority.owner==go_rf::Authority::TRANSMITTER);
 peer=IPAddress(192,168,4,2);peerPort=14550;lastPeer=3080;fakeTime=3090;
 udp.incoming.push_back({peer,peerPort,std::vector<uint8_t>(DISCONNECT,DISCONNECT+sizeof(DISCONNECT)-1)});receiveOne();assert(state.armed&&authority.owner==go_rf::Authority::TRANSMITTER&&peer==ZERO_IP);
 serialFrame(1811,172,1811,3100,172);assert(state.armed);
 serialFrame(1811,172,992,3110,1811);assert(!state.armed&&authority.owner==go_rf::Authority::STOPPED);zero();
 assert(authority.takePhone());assert(!state.armed); // Returning SA never automatically arms phone.
 // A middle/invalid selection blocks both control sources.
 serialFrame(172,172,992,3200,992);assert(!authority.takePhone());zero();
 std::mt19937 rng(12);for(unsigned i=0;i<200000;i++)p.feed(uint8_t(rng()),i,channels);
 std::cout<<"PASS: CRSF CRC/framing, RF arm dwell, receiver loss, STOP, source exclusion, peer loss and overload\n";
}

#include <cassert>
#include <vector>
#include <iostream>
#include <random>
#include "../TeleRC_Go_Rover/CrsfReceiver.h"
#define TELERC_HOST_TEST 1
#include "../TeleRC_Go_Rover/TeleRC_Go_Rover.ino"
static std::vector<uint8_t> rf(uint16_t arm=172,uint16_t stop=172,uint16_t drive=992){
 std::vector<uint8_t> b(26);b[0]=0xc8;b[1]=24;b[2]=0x16;
 for(unsigned c=0;c<16;c++){unsigned raw=c==4?arm:c==5?stop:c==1?drive:992;for(unsigned bit=0;bit<11;bit++)if(raw&(1u<<bit))b[3+(c*11+bit)/8]|=1u<<((c*11+bit)%8);}
 b[25]=go_rf::crc8(b.data()+2,23);return b;
}
static void serialFrame(uint16_t arm,uint16_t stop,uint16_t drive,uint32_t now){fakeTime=now;for(auto b:rf(arm,stop,drive))rfSerial.incoming.push_back(b);serviceReceiver(now);watchdog(now);updateMotors(now);}
static void zero(){for(unsigned pin:{4,5,6,7,8,9,10,11})assert(duties[pin]==0);}
int main(){
 go_rf::Parser p;go_rf::Channels channels;unsigned acceptedCount=0;
 for(auto b:rf())if(p.feed(b,1,channels))acceptedCount++;
 assert(acceptedCount==1&&channels.us[0]==1500&&channels.us[1]==1500&&channels.us[4]==1000);
 auto corrupt=rf();corrupt.back()^=1;for(auto b:corrupt)assert(!p.feed(b,2,channels));
 auto truncated=rf();for(unsigned i=0;i<10;i++)assert(!p.feed(truncated[i],3,channels));
 for(auto b:rf())p.feed(b,30,channels); // inter-byte gap recovers framing
 go_rf::Authority a;channels.us[0]=channels.us[1]=1500;channels.us[4]=1000;channels.us[5]=1000;
 assert(!a.observe(channels,100));assert(!a.observe(channels,250));assert(!a.observe(channels,400));channels.us[4]=2000;assert(a.observe(channels,450));a.owner=go_rf::Authority::TRANSMITTER;assert(a.fresh(749));assert(!a.fresh(750));a.halt();assert(!a.observe(channels,760));
 assert(a.takePhone());channels.us[5]=2000;assert(!a.observe(channels,800));assert(a.stop&&a.owner==go_rf::Authority::STOPPED);assert(!a.takePhone());
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
 stopRover(true,"TEST");assert(authority.takePhone());
 state.rc(1500,1500,1700);state.rc(1500,1500,2000);assert(state.arm(2000));
 serialFrame(1811,172,1811,2010);assert(authority.owner==go_rf::Authority::PHONE&&state.drive==1500);
 serialFrame(1811,1811,1811,2020);assert(!state.armed);zero();
 // UART overload is discarded, never replayed into current motion.
 for(unsigned i=0;i<100;i++)rfSerial.incoming.push_back(0);
 serviceReceiver(2100);assert(rfSerial.incoming.empty());assert(!state.armed);zero();
 std::mt19937 rng(12);for(unsigned i=0;i<200000;i++)p.feed(uint8_t(rng()),i,channels);
 std::cout<<"PASS: CRSF CRC/framing, RF arm dwell, receiver loss, STOP, source exclusion, peer loss and overload\n";
}

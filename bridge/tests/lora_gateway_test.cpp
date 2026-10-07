#include <cassert>
#include <iostream>
#define TELERC_RADIO_PROVISIONED 1
#define TELERC_RADIO_FREQUENCY 433.0f
#define TELERC_RADIO_KEY {1}
#ifdef TEST_BASE_GATEWAY
#include "../TeleRCLoRaBase/TeleRCLoRaBase.ino"
#else
#include "../TeleRCLoRaRover/TeleRCLoRaRover.ino"
#endif
void inject(uint8_t kind,uint64_t token,const uint8_t*body,size_t n){uint8_t out[telerc::AUTH_MAX];size_t count=telerc::sealRadio(kind,token,body,n,radioKey,out);radio.rx.assign(out,out+count);radioPacketReady=true;}
int main(){
 setup();
#ifdef TEST_BASE_GATEWAY
 const uint8_t discover[]="TELERC_DISCOVER_V1";
 uint8_t wire[telerc::UART_MAX+6];size_t size=telerc::encodeUart(discover,sizeof(discover)-1,wire);
 Serial.rx.assign(wire,wire+size);lastValidPollAt=millis();inject(1,123,nullptr,0);loop();
 assert(radio.tx.size()==1);uint64_t token;const uint8_t*body;size_t n;auto &frame=radio.tx.back();
 assert(telerc::openRadio(frame.data(),frame.size(),radioKey,2,token,body,n));assert(token==123&&n==sizeof(discover));
 inject(1,124,nullptr,0);loop();auto &second=radio.tx.back();assert(telerc::openRadio(second.data(),second.size(),radioKey,2,token,body,n)&&n==0);
 auto txCount=radio.tx.size();inject(1,124,nullptr,0);loop();assert(radio.tx.size()==txCount);
#else
 loop();assert(challenge.open);uint64_t token=challenge.token;
 const uint8_t body[]={19,'T','E','L','E','R','C','_','D','I','S','C','O','N','N','E','C','T','_','V','1'};
 // malformed length is rejected before any part reaches motor UART.
 inject(2,token,body,sizeof(body));loop();assert(motorPort.tx.empty());assert(challenge.open);
 uint8_t valid[21];valid[0]=20;memcpy(valid+1,body+1,20);
 inject(2,token,valid,sizeof(valid));loop();assert(!motorPort.tx.empty());auto motorSize=motorPort.tx.size();assert(!challenge.open);
 inject(2,token,valid,sizeof(valid));loop();assert(motorPort.tx.size()==motorSize);
 // UART congestion drops the whole bundle, never a partial ARM/control combination.
 challenge.issue(999,millis());motorPort.capacity=0;inject(2,999,valid,sizeof(valid));loop();assert(motorPort.tx.size()==motorSize&&uartDrop==1);
 // Oversized RF packet is discarded without writing beyond receive buffer.
 radio.rx.assign(255,0);radioPacketReady=true;loop();assert(motorPort.tx.size()==motorSize);
#endif
 // A failed RX restart invalidates radio-active state and drops pending authority.
 const uint8_t pending[]="TELERC_DISCOVER_V1";
 assert(commands.accept(pending,sizeof(pending)-1,millis()));challenge.issue(1001,millis());
 radio.receiveResult=-1;assert(!transmitRadio(1,1002,nullptr,0));
 assert(!radioActive&&!challenge.open&&commands.eventN==0);
 // Failure during reception must also inhibit a due poll in the same loop iteration.
 radioActive=true;challenge.issue(1003,millis());lastPollAt=0;
 auto failedTxCount=radio.tx.size();
#ifdef TEST_BASE_GATEWAY
 inject(1,1003,nullptr,0);
#else
 inject(2,1003,nullptr,0);
#endif
 loop();assert(!radioActive&&!challenge.open&&radio.tx.size()==failedTxCount);
 radio.receiveResult=0;loop();assert(!radioActive&&radio.tx.size()==failedTxCount);
 std::cout<<"Gateway forwarding regression checks passed\n";
}

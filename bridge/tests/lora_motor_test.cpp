#include <cassert>
#include <iostream>
#include <TeleRCRadioAuth.h>
#include "../TeleRCMotorController/TeleRCMotorController.ino"

std::vector<uint8_t> overrideFrame(uint16_t steer,uint16_t drive){
 uint8_t p[18];memset(p,255,sizeof(p));p[0]=steer;p[1]=steer>>8;p[2]=drive;p[3]=drive>>8;p[16]=p[17]=1;
 uint8_t f[26];makeMavlinkV1(70,p,18,124,f,sizeof(f));f[3]=255;f[4]=190;telerc::CommandMailbox::checksumRc(f);return {f,f+26};
}
std::vector<uint8_t> armFrame(bool armed){
 uint8_t p[33]={};if(armed){p[2]=0x80;p[3]=0x3f;}p[28]=0x90;p[29]=p[30]=p[31]=1;
 uint8_t f[41];makeMavlinkV1(76,p,33,152,f,sizeof(f));f[3]=255;f[4]=190;
 uint16_t c=0xffff;for(size_t i=1;i<39;++i)c=crcByte(c,f[i]);c=crcByte(c,152);f[39]=c;f[40]=c>>8;return {f,f+41};
}
telerc::CommandMailbox mailbox;
telerc::ChallengeWindow radioWindow;
uint8_t key[32]={1};uint64_t token=0;
void time(uint32_t ms){testMs+=ms;testUs+=ms*1000;}
void stopped(){for(int i=0;i<4;++i){assert(currentCmd[i]==0);assert(duties[RPWM_PIN[i]]==0);assert(duties[LPWM_PIN[i]]==0);}}
void radioToMotor(const uint8_t*wire,size_t wireN){
 uint64_t receivedToken;const uint8_t*body=nullptr;size_t n=0;
 if(!telerc::openRadio(wire,wireN,key,2,receivedToken,body,n)||!telerc::validBundle(body,n)||!radioWindow.consume(receivedToken,millis()))return;
 // Exercise the real bounded UART frame encoder/decoder and motor MAVLink parser.
 controlUart.rx.clear();controlUart.cursor=0;
 for(size_t i=0;i<n;){size_t length=body[i++];uint8_t frame[telerc::UART_MAX+6];size_t count=telerc::encodeUart(body+i,length,frame);
   controlUart.rx.insert(controlUart.rx.end(),frame,frame+count);i+=length;}
 while(controlUart.available())loop();
}
void slot(){
 uint8_t body[telerc::RADIO_MAX],wire[telerc::AUTH_MAX];size_t n=mailbox.take(body,millis());
 radioWindow.issue(++token,millis());size_t size=telerc::sealRadio(2,token,body,n,key,wire);radioToMotor(wire,size);
}
void command(const std::vector<uint8_t>&f){assert(mailbox.accept(f.data(),f.size(),millis()));slot();}
int main(){
 setup();stopped();assert(!directArmed&&activeMode==ControlMode::FAILSAFE);
 const uint8_t discover[]="TELERC_DISCOVER_V1";
 assert(mailbox.accept(discover,sizeof(discover)-1,millis()));slot();
 for(int i=0;i<5;++i){command(overrideFrame(1500,1500));time(100);loop();}
 assert(activeMode==ControlMode::DIRECT&&!directArmed);
 assert(mailbox.accept(overrideFrame(1500,1500).data(),26,millis()));
 command(armFrame(true));assert(directArmed);
 command(overrideFrame(1500,1800));time(6);loop();
 for(int i=0;i<4;++i)assert(currentCmd[i]>0);
 command(overrideFrame(1800,1500));
 for(int i=0;i<40;++i){time(6);loop();}
 assert(currentCmd[0]>0&&currentCmd[1]>0&&currentCmd[2]<0&&currentCmd[3]<0);
 for(int i=0;i<4;++i)assert(!(duties[RPWM_PIN[i]]&&duties[LPWM_PIN[i]]));
 // Noise, wrong key, and stale/duplicate challenges cannot renew motor-axis freshness.
 auto at=axisUpdatedMs[1];uint8_t body[telerc::RADIO_MAX],wire[telerc::AUTH_MAX];
 auto move=overrideFrame(1500,1800);assert(mailbox.accept(move.data(),move.size(),millis()));size_t n=mailbox.take(body,millis());
 radioWindow.issue(++token,millis());uint8_t wrong[32]={2};size_t size=telerc::sealRadio(2,token,body,n,wrong,wire);
 radioToMotor(wire,size);assert(axisUpdatedMs[1]==at);
 size=telerc::sealRadio(2,token,body,n,key,wire);time(91);radioToMotor(wire,size);assert(axisUpdatedMs[1]==at);
 time(501);loop();stopped();assert(!directArmed);
 radioWindow.issue(++token,millis());size=telerc::sealRadio(2,token,body,n,key,wire);
 radioToMotor(wire,size);assert(!directArmed);radioToMotor(wire,size);assert(!directArmed);
 // Recovery requires neutral dwell and a new ARM. Discovery cannot refresh a moving axis.
 for(int i=0;i<5;++i){command(overrideFrame(1500,1500));time(100);loop();}
 command(overrideFrame(1500,1500));command(armFrame(true));assert(directArmed);
 command(overrideFrame(1500,1800));time(6);loop();assert(currentCmd[0]>0);
 time(500);assert(mailbox.accept(discover,sizeof(discover)-1,millis()));slot();loop();stopped();assert(!directArmed);
 // Mixed release/DISARM frames survive a radio slot and leave all four drivers off.
 assert(mailbox.accept(overrideFrame(0,0).data(),26,millis()));command(armFrame(false));stopped();
 std::cout<<"LoRa framing/authentication to four-driver motor integration checks passed\n";
}

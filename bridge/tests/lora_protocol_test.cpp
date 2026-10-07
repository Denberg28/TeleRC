#include <cassert>
#include <iostream>
#include <vector>
#include <TeleRCRadioAuth.h>
std::vector<uint8_t> mav(uint8_t id,uint8_t len,uint8_t extra){
 std::vector<uint8_t> f(len+8);f[0]=0xfe;f[1]=len;f[3]=255;f[4]=190;f[5]=id;
 if(id==70){for(int i=0;i<8;++i){f[6+2*i]=0xff;f[7+2*i]=0xff;}f[6]=f[8]=0xdc;f[7]=f[9]=5;f[22]=f[23]=1;}
 if(id==76){f[8]=0x80;f[9]=0x3f;f[34]=0x90;f[35]=f[36]=f[37]=1;}
 uint16_t c=0xffff;for(size_t i=1;i<f.size()-2;++i)c=telerc::mavCrcByte(c,f[i]);c=telerc::mavCrcByte(c,extra);f[f.size()-2]=uint8_t(c);f.back()=uint8_t(c>>8);return f;
}
void checksum(std::vector<uint8_t>&f){uint16_t c=0xffff;for(size_t i=1;i<f.size()-2;++i)c=telerc::mavCrcByte(c,f[i]);c=telerc::mavCrcByte(c,124);f[f.size()-2]=c;f.back()=c>>8;}
int main(){
 uint8_t wire[telerc::UART_MAX+6],key[32]={1},radio[telerc::AUTH_MAX],body[96];telerc::UartParser parser;
 auto rc=mav(70,18,124),arm=mav(76,33,152),hb=mav(0,9,50);
 size_t count=telerc::encodeUart(rc.data(),rc.size(),wire);bool ready=false;
 for(size_t i=0;i<count;++i){ready=parser.feed(wire[i],100);}
 assert(ready);assert(parser.length()==rc.size());assert(!memcmp(parser.data+4,rc.data(),rc.size()));
 wire[5]^=1;for(size_t i=0;i<count;++i)ready=parser.feed(wire[i],101);assert(!ready);
 parser.feed(0xa5,102);parser.feed(0x5a,102);assert(!parser.feed(26,200));
 std::vector<uint8_t> huge(281);assert(!telerc::encodeUart(huge.data(),huge.size(),wire));
 telerc::CommandMailbox mail;assert(mail.accept(rc.data(),rc.size(),100));rc[8]=0x08;rc[9]=7;checksum(rc);assert(mail.accept(rc.data(),rc.size(),120));
 assert(mail.accept(arm.data(),arm.size(),120));assert(mail.accept(hb.data(),hb.size(),120));size_t n=mail.take(body,130);assert(n==87);assert(telerc::validBundle(body,n));assert(body[9]==0x08);assert(!mail.take(body,131));
 assert(mail.accept(rc.data(),rc.size(),200));assert(!mail.take(body,401));
 auto release=rc;release[6]=release[7]=release[8]=release[9]=0;checksum(release);
 assert(mail.accept(arm.data(),arm.size(),500));assert(mail.accept(release.data(),release.size(),501));assert(!mail.accept(rc.data(),rc.size(),502));n=mail.take(body,503);assert(n==27&&body[1+6]==0&&body[1+8]==0);
 const char stop[]="TELERC_DISCONNECT_V1";mail.accept(rc.data(),rc.size(),600);assert(mail.accept((const uint8_t*)stop,sizeof(stop)-1,601));n=mail.take(body,602);assert(n==sizeof(stop));assert(!memcmp(body+1,stop,sizeof(stop)-1));
 uint8_t malformed[]={50,1};assert(!telerc::validBundle(malformed,2));assert(!telerc::validBundle(body,n-1));
 size_t r=telerc::sealRadio(2,0x123456789abcdef0ULL,body,n,key,radio);uint64_t token;const uint8_t*opened=nullptr;size_t openedN;
 assert(telerc::openRadio(radio,r,key,2,token,opened,openedN));assert(token==0x123456789abcdef0ULL&&openedN==n);
 assert(!telerc::openRadio(radio,r,key,1,token,opened,openedN));radio[15]^=1;assert(!telerc::openRadio(radio,r,key,2,token,opened,openedN));radio[15]^=1;key[0]=2;assert(!telerc::openRadio(radio,r,key,2,token,opened,openedN));
 telerc::ChallengeWindow window;window.issue(7,1000);assert(!window.consume(8,1001));assert(window.consume(7,1002));assert(!window.consume(7,1003));window.issue(9,2000);assert(!window.consume(9,2091));window.issue(10,0xfffffff0u);assert(window.consume(10,20));
 // Exercise bounded parser behavior under arbitrary serial noise.
 uint32_t noise=17;telerc::UartParser fuzz;
 for(uint32_t i=0;i<100000;++i){noise=noise*1664525u+1013904223u;fuzz.feed(uint8_t(noise>>24),i/32);assert(fuzz.used<sizeof(fuzz.data));}
 // Pending disconnect cannot be replaced by later control or ARM.
 mail.accept((const uint8_t*)stop,sizeof(stop)-1,800);assert(!mail.accept(rc.data(),rc.size(),801));assert(!mail.accept(arm.data(),arm.size(),802));
 // Independent sparse joystick packets must both survive one radio slot.
 mail.clear();auto steer=rc,drive=rc;
 steer[6]=0x08;steer[7]=7;steer[8]=steer[9]=0xff;checksum(steer);
 drive[6]=drive[7]=0xff;drive[8]=0xdc;drive[9]=5;checksum(drive);
 assert(mail.accept(steer.data(),steer.size(),1000));assert(mail.accept(drive.data(),drive.size(),1010));
 n=mail.take(body,1020);assert(n==27);assert(body[7]==0x08&&body[8]==7);assert(body[9]==0xdc&&body[10]==5);
 assert(telerc::mavV1(body+1,26,70,18,124));assert(!mail.take(body,1021));
 // Fresh steering must not renew a cached, expired throttle value.
 assert(mail.accept(drive.data(),drive.size(),1100));assert(mail.accept(steer.data(),steer.size(),1290));
 n=mail.take(body,1310);assert(n==27&&body[9]==0xff&&body[10]==0xff);
 // DISARM cancels queued drive and cannot be followed by drive or ARM in that slot.
 auto disarm=arm;memset(disarm.data()+6,0,4);
 uint16_t disarmCrc=0xffff;for(size_t i=1;i<disarm.size()-2;++i)disarmCrc=telerc::mavCrcByte(disarmCrc,disarm[i]);
 disarmCrc=telerc::mavCrcByte(disarmCrc,152);disarm[39]=disarmCrc;disarm[40]=disarmCrc>>8;
 assert(mail.accept(rc.data(),rc.size(),1400));assert(mail.accept(disarm.data(),disarm.size(),1401));
 assert(!mail.accept(rc.data(),rc.size(),1402));assert(!mail.accept(arm.data(),arm.size(),1403));
 n=mail.take(body,1404);assert(n==42&&body[1+5]==76&&body[1+8]==0);
 // Release and DISARM are both preserved, in either arrival order.
 for(bool releaseFirst:{false,true}){
   mail.clear();
   auto &first=releaseFirst?release:disarm;auto &second=releaseFirst?disarm:release;
   assert(mail.accept(first.data(),first.size(),1500));assert(mail.accept(second.data(),second.size(),1501));
   n=mail.take(body,1502);assert(n==69&&body[1+5]==70&&body[28+5]==76);
 }
 std::cout<<"LoRa authentication, replay, mailbox and UART regression checks passed\n";
}

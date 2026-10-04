#include <cassert>
#include <iostream>
#ifdef TEST_UART_MOTOR
#include "../TeleRCMotorController/TeleRCMotorController.ino"
#else
#include "../TeleRCHybridController/TeleRCHybridController.ino"
#endif
std::vector<uint8_t> rc(uint16_t a=1500,uint16_t b=1500) {
 uint8_t p[18];memset(p,255,sizeof(p));p[0]=a;p[1]=a>>8;p[2]=b;p[3]=b>>8;p[16]=1;p[17]=1;
 uint8_t f[26];makeMavlinkV1(70,p,18,124,f,26);f[3]=255;f[4]=190;
 uint16_t crc=65535;for(int i=1;i<24;i++)crc=crcByte(crc,f[i]);crc=crcByte(crc,124);f[24]=crc;f[25]=crc>>8;
 return {f,f+26};
}
void packet(std::vector<uint8_t> p){
#ifdef TEST_UART_MOTOR
 uint8_t wire[telerc::UART_MAX+6];size_t n=telerc::encodeUart(p.data(),p.size(),wire);
 if(n){controlUart.rx.assign(wire,wire+n);controlUart.cursor=0;handleUdpPacket();controlUart.rx.clear();controlUart.cursor=0;}
#else
 udp.rx=p;udp.cursor=0;handleUdpPacket();udp.rx.clear();
#endif
}
void tick(uint32_t ms){testMs+=ms;testUs+=ms*1000;}
void stopped(){for(int i=0;i<4;i++){assert(currentCmd[i]==0);assert(duties[RPWM_PIN[i]]==0);assert(duties[LPWM_PIN[i]]==0);}}
int main(){
 motorPwmReady=true;controllerIp={192,168,4,2};lastControllerMs=millis();activeMode=ControlMode::DIRECT;
 assert(!safeToDirectArm());auto neutral=rc();assert(validRcOverride(neutral.data(),neutral.size()));packet(neutral);assert(safeToDirectArm());handleArmCommand(true);assert(directArmed);
 packet(rc(1500,1800));updateMotors(micros());assert(currentCmd[0]>0);
 tick(400);packet(rc(1500,65535));tick(101);updateModeArbiter(micros());assert(activeMode==ControlMode::FAILSAFE);stopped();assert(!directArmed);
 activeMode=ControlMode::DIRECT;packet(rc());handleArmCommand(true);packet(rc(0,0));assert(!directArmed);stopped();
 #ifndef TEST_UART_MOTOR
 auto oversized=rc();oversized.resize(300,0);auto rejected=rejectedCommands;packet(oversized);assert(rejectedCommands==rejected+1);
#endif
 requestedMode=ControlMode::AUTOPILOT;pins[16]=HIGH;lastRawRequestedMode=requestedMode;activeMode=ControlMode::AUTOPILOT;
 for(int i=0;i<4;i++){fcLastValidUs[i]=micros();fcPulseUs[i]=1500;}snapshotFcInputs();assert(autopilotSourceNeutral(micros()));
 packet(rc(1500,1800));assert(fcOverrideOwnedMask==3);tick(501);serviceFcOverrideSafety();assert(fcNeutralHold);assert(activeMode==ControlMode::FAILSAFE);stopped();assert(validRcOverride(fcMav.tx.data()+fcMav.tx.size()-26,26));assert(rcValue(fcMav.tx.data()+fcMav.tx.size()-26,1)==1500);
 fcMav.capacity=0;tick(101);serviceFcOverrideSafety();assert(disconnectLatched);stopped();fcMav.capacity=1024;
 fcOverrideOwnedMask=0;disconnectLatched=false;for(int i=0;i<4;i++)fcLastValidUs[i]=micros();snapshotFcInputs();activeMode=ControlMode::AUTOPILOT;tick(151);snapshotFcInputs();updateModeArbiter(micros());assert(activeMode==ControlMode::FAILSAFE);stopped();
 testUs=2000000;pins[4]=LOW;fcRiseSeen[0]=false;captureFcEdge(0);assert(fcLastValidUs[0]==0);
 pins[4]=HIGH;captureFcEdge(0);testUs+=1500;pins[4]=LOW;captureFcEdge(0);snapshotFcInputs();assert(fcSnapshotPulseUs[0]==1500);assert(fcChannelFresh(0,micros()));
 pins[4]=HIGH;captureFcEdge(0);testUs+=3000;pins[4]=LOW;captureFcEdge(0);snapshotFcInputs();assert(!fcChannelFresh(0,micros()));
 attachOk=false;assert(!attachMotorPwm());attachOk=true;assert(attachMotorPwm());
 activeMode=ControlMode::AUTOPILOT;requestedMode=activeMode;lastRawRequestedMode=activeMode;pins[16]=LOW;updateModeArbiter(micros());assert(activeMode==ControlMode::FAILSAFE);stopped();
 assert(slewToward(4,-100,4,16)==0);assert(slewToward(0,-100,4,16)==-4);
 packet(std::vector<uint8_t>(DISCONNECT,DISCONNECT+strlen(DISCONNECT)));assert(disconnectLatched);updateModeArbiter(micros());stopped();
 auto corrupt=rc();corrupt[8]^=1;assert(!validRcOverride(corrupt.data(),corrupt.size()));
 uint8_t v2[12]={0xfd,0,0,0,1,255,190,0,0,0,0,0};assert(validControllerMavlinkDatagram(v2,12));v2[2]=2;assert(!validControllerMavlinkDatagram(v2,12));
 requestedMode=ControlMode::DIRECT;lastRawRequestedMode=requestedMode;pins[16]=LOW;disconnectLatched=false;activeMode=ControlMode::DIRECT;motorPwmReady=true;
 testMs=0xfffffff0u;packet(rc());assert(directAxesFresh());testMs=20;assert(directAxesFresh());testMs=600;assert(!directAxesFresh());
#ifdef TEST_UART_MOTOR
 auto beforeAxis=axisUpdatedMs[1];auto input=rc();uint8_t wire[telerc::UART_MAX+6];size_t wireN=telerc::encodeUart(input.data(),input.size(),wire);
 wire[12]^=1;controlUart.rx.assign(wire,wire+wireN);controlUart.cursor=0;handleUdpPacket();assert(axisUpdatedMs[1]==beforeAxis);
 controlUart.rx.clear();controlUart.cursor=0;
#endif
 std::cout<<"Hybrid safety regression checks passed\n";
}

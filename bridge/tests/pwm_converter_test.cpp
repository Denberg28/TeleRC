#include <cassert>
#include <iostream>
#include "../BTS7960PWMConverter/TeleRC_4x_BTS7960_PWM_Converter.ino"
void pulse(uint8_t ch,uint16_t width){
  pins[RC_IN_PIN[ch]]=HIGH;captureEdge(ch);testUs+=width;
  pins[RC_IN_PIN[ch]]=LOW;captureEdge(ch);testMs=testUs/1000;
}
void input(uint16_t value){for(uint8_t i=0;i<(PAIRED_SKID_STEER?2:4);++i)pulse(i,value);}
void zero(){for(int i=0;i<4;++i){assert(currentCmd[i]==0);assert(duties[RPWM_PIN[i]]==0);assert(duties[LPWM_PIN[i]]==0);}}
void step(){testUs+=6000;testMs=testUs/1000;loop();}
int main(){
  setup();zero();
  // Displaced input at boot must not start any motor.
  input(1800);step();zero();
  // A fresh neutral dwell admits the PWM source; then paired/four-wheel commands work.
  for(int i=0;i<40;++i){input(1500);step();}
  input(1800);step();assert(currentCmd[0]>0);
  if(PAIRED_SKID_STEER){assert(currentCmd[0]==currentCmd[1]);assert(currentCmd[2]==currentCmd[3]);}
  // Timeout hard-stops current duty rather than ramping it down.
  testUs+=RC_TIMEOUT_US+1;testMs=testUs/1000;loop();zero();
  input(1800);step();zero(); // displaced recovery cannot resume motion
  for(int i=0;i<40;++i){input(1500);step();}
  input(1800);step();assert(currentCmd[0]>0);
  // A malformed required input immediately invalidates the source.
  pulse(0,3000);step();zero();
  // A falling edge without a rising edge must not manufacture a valid pulse.
  testUs+=1500;pins[RC_IN_PIN[0]]=LOW;captureEdge(0);step();zero();
  assert(slewToward(4,-100)==0);assert(slewToward(0,-100)==-ACCEL_STEP);
  // Snapshot/elapsed arithmetic survives micros wrap and a valid timestamp of zero.
  testUs=0xfffffa24u;input(1500);step();zero();
  for(int i=0;i<40;++i){input(1500);step();}
  input(1800);step();assert(currentCmd[0]>0);
  // PWM resource failure leaves the converter inhibited despite fresh inputs.
  attachOk=false;setup();input(1800);step();zero();
  for(int i=0;i<40;++i){input(1500);step();}
  input(1800);step();zero();
  // Diagnostics are dropped under backpressure, never block the motor loop.
  Serial.capacity=0;auto before=Serial.tx.size();lastDiagMs=0;printDiagnostics(micros());assert(Serial.tx.size()==before);
  std::cout<<"PWM capture, neutral recovery, hard-stop and resource regression checks passed\n";
}

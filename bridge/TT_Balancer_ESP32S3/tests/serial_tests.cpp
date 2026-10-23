#include <cassert>
#include <cstring>
#include "../TT_Balancer_ESP32S3.ino"
int checks=0;
void check(bool b){assert(b);++checks;}
void off(){check(motorCommand==0&&duty[4]==0&&duty[5]==0&&duty[6]==0&&duty[7]==0);}
void boot(){
  resetPreferences();Wire.reset();Serial.input.clear();Serial.output.clear();
  fakeUs=0;stopLow=false;writeOK=attachOK=true;
  mode=Mode::DISARMED;pwmReady=calibrated=false;gyroBias=angle=rate=integral=0;
  remoteArming=false;seenArm=seenDisarm=completedArm=0;
  setup();loop();check(mode==Mode::DISARMED&&commandsEnabled);off();
}
void input(const char *s){Serial.push(s);loop();}
void tick(){fakeUs+=4000;loop();}
int main(){
  boot();check(serialBytes==0&&!strcmp(serialResult,"IDLE"));
  input("?");check(serialBytes==1&&serialLastByte=='?'&&serialLastCommand=='?');
  check(!strcmp(serialResult,"RX_OK_NO_MOTOR_ACTION"));off();
  for(int i=0;i<30;++i)tick();
  check(Serial.output.find("SERIAL bytes=1")!=std::string::npos);
  check(Serial.output.find("result=RX_OK_NO_MOTOR_ACTION")!=std::string::npos);
  check(Serial.output.find("testPWM=140 testMs=300 pwmReady=1")!=std::string::npos);
  input(" \r\n\tT\r\n");check(mode==Mode::MOTOR_TEST&&motorCommand==TEST_PWM);
  check(serialLastCommand=='t'&&serialLastByte=='\n');
  check(!strcmp(serialResult,"TEST_STARTED"));
  for(int i=0;i<int(TEST_MS/4+2);++i)tick();
  check(mode==Mode::DISARMED&&wheelTestComplete&&!directionsVerified);off();
  check(!strcmp(serialResult,"TEST_FINISHED_CHECK_WHEELS_BEFORE_V"));
  input(" V\r\n");check(directionsVerified&&!strcmp(serialResult,"DIRECTIONS_CONFIRMED"));off();
  input(" A\n");check(mode==Mode::ARMING&&!strcmp(serialResult,"ARM_REQUESTED"));
  input(" X\n");check(mode==Mode::DISARMED&&!strcmp(serialResult,"STOP_RECEIVED"));off();

  boot();input("\r\n tX\r\n");check(mode==Mode::DISARMED&&serialLastCommand=='x');
  check(!strcmp(serialResult,"STOP_RECEIVED")&&!wheelTestComplete);off();
  input("?X");check(!strcmp(serialResult,"STOP_RECEIVED"));off();
  boot();input("\r\n\t ");check(serialBytes==4&&!strcmp(serialResult,"IDLE"));off();
  input("Z\r\n");check(serialLastCommand=='z'&&!strcmp(serialResult,"UNKNOWN_SEND_?_T_V_A_X_C"));off();
  input("A");check(!strcmp(serialResult,"ARM_NEEDS_DIRECTION_CHECK"));off();
  input("V");check(!strcmp(serialResult,"VERIFY_NEEDS_COMPLETED_TEST"));off();
  boot();testInput.linked=true;input("T");
  check(!strcmp(serialResult,"REJECTED_DISCONNECT_TELERC")&&mode==Mode::DISARMED);off();
  testInput.linked=false;input("T");check(mode==Mode::MOTOR_TEST);
  input("V");check(!strcmp(serialResult,"REJECTED_NOT_DISARMED")&&!directionsVerified);
  input("X");off();input("V");check(!directionsVerified);
  boot();commandsEnabled=false;input("t");
  check(serialBytes==1&&serialBootDiscard==1&&!strcmp(serialResult,"BOOT_INPUT_DISCARDED_RESEND_AFTER_READY"));off();
  loop();input("T");check(mode==Mode::MOTOR_TEST);input("X");off();
  boot();Serial.push(std::string(40,' '));loop();
  check(serialBytes==32&&Serial.input.size()==8);off();loop();check(serialBytes==40);off();
  boot();fault("test");input("T");
  check(!strcmp(serialResult,"REJECTED_FAULT_STOP_OR_IMU")&&mode==Mode::FAULT);off();
  input("?");check(!strcmp(serialResult,"RX_OK_NO_MOTOR_ACTION")&&mode==Mode::FAULT);off();
  boot();writeOK=false;input("T");check(mode==Mode::FAULT);
  check(!strcmp(serialResult,"TEST_ABORTED_FAULT"));off();
  boot();auto restarts=ESP.restarts;input(" C\r\n");
  check(ESP.restarts==restarts+1&&preferenceBlob.empty());off();
  std::printf("Serial parser/diagnostics: %d assertions PASS\n",checks);
}

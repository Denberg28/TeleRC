// Real sketch logic under deterministic host fakes, not an ESP32 simulation.
#include <cassert>
#include <cstring>
#include "../TT_Balancer_ESP32S3.ino"
int checks=0;
void check(bool result){assert(result);++checks;}
void off(){check(motorCommand==0);check(duty[4]==0&&duty[5]==0&&duty[6]==0&&duty[7]==0);}
void boot(){
  resetPreferences();
  fakeUs=0; stopLow=false; attachOK=writeOK=true;
  std::fill(std::begin(duty),std::end(duty),0);
  Wire.reset();Serial.input.clear();
  mode=Mode::DISARMED;pwmReady=calibrated=false;gyroBias=angle=rate=integral=0;
  linkInput=telerc::Input{};seenArm=seenDisarm=completedArm=0;armResult=2;
  remoteArming=false;requestedLean=requestedYaw=0;
  completedBlock=telerc::ArmBlock::NONE;
  motorCommand=0;tickUs=sampleUs=uprightSince=saturationSince=testUntil=reportMs=0;
  uprightTiming=saturationTiming=false;setup();
  loop(); // Observe empty input buffer before accepting a new command.
  check(mode==Mode::DISARMED&&calibrated);off();
}
void tick(unsigned us=4000){fakeUs+=us;loop();}
void command(char c){Serial.input.push_back(c);loop();}
void arm(){
  command('a');check(mode==Mode::ARMING);
  for(int i=0;i<260;++i)tick();
  check(mode==Mode::BALANCING);off();
}
int main(){
  boot();
  command('a');
  if(!DIRECTIONS_VERIFIED){
    check(mode==Mode::DISARMED);off();
    command('t');check(mode==Mode::MOTOR_TEST);check(motorCommand==TEST_PWM);
    command('x');check(mode==Mode::DISARMED);off();
    // v is not a bypass: a full timed test is required before manual confirmation.
    boot();command('v');check(!directionsVerified);off();
    command('t');command('x');command('v');check(!directionsVerified);off();
    command('t');for(int i=0;i<40;++i){tick();}
    check(wheelTestComplete&&!directionsVerified);off();
    command('v');check(directionsVerified);check(mode==Mode::DISARMED);off();
    publishStatus();check(testStatus.ready);
    command('a');check(mode==Mode::ARMING);command('x');off();
    // The runtime confirmation also unlocks the real TeleRC arm path, not just Serial.
    testInput.linked=testInput.fresh=testInput.armPending=true;
    testInput.lastRC=testInput.armAt=millis();testInput.armToken=1;
    loop();check(mode==Mode::ARMING);
    for(int i=0;i<260;++i){testInput.lastRC=millis();tick();}
    check(mode==Mode::BALANCING&&completedArm==1&&armResult==0);off();
    boot();check(!directionsVerified&&!wheelTestComplete);off(); // No hidden persistence.
    std::printf("Default arming lock: %d assertions PASS\n",checks);return 0;
  }
  // Fresh upright hold, positive feedback direction and mirrored motor mapping.
  boot();arm(); Wire.frame(0,0,1,10);tick();
  check(motorCommand>0);check(duty[4]>0&&duty[5]==0&&duty[6]==0&&duty[7]>0);
  Wire.frame(0,0,1,-100);tick();
  check(motorCommand<0);check(duty[4]==0&&duty[5]>0&&duty[6]>0&&duty[7]==0);
  command('x');check(mode==Mode::DISARMED);off();

  boot();command('t');check(mode==Mode::MOTOR_TEST);check(motorCommand==TEST_PWM);
  for(int i=0;i<40;++i){tick();}check(mode==Mode::DISARMED);off();
  boot();command('t');stopLow=true;loop();check(mode==Mode::FAULT);off();
  stopLow=false;command('a');command('x');check(mode==Mode::FAULT);off();

  boot();arm();Wire.busOK=false;tick();check(mode==Mode::FAULT);off();
  boot();arm();Wire.ready=false;tick();tick();tick();check(mode==Mode::FAULT);off();
  boot();arm();tick(9000);check(mode==Mode::FAULT);off();
  boot();arm();Wire.readDelay=1700;tick();check(mode==Mode::FAULT);off();
  boot();arm();Wire.frame(0,0,0,0);tick();check(mode==Mode::FAULT);off();
  boot();arm();Wire.frame(0,0,1,250);tick();check(mode==Mode::FAULT);off();

  boot();arm();angle=35;Wire.frame(-0.5736f,0,0.8192f,0);tick();
  check(mode==Mode::FAULT);off();
  boot();arm();Wire.frame(0,0.8f,0.6f,0);tick();check(mode==Mode::FAULT);off();
  boot();arm();Wire.frame(0,0,0.1f,0);tick();check(mode==Mode::FAULT);off();
  boot();arm();angle=NAN;tick();check(mode==Mode::FAULT);off();

  // Sustained limit trips instead of driving indefinitely; output stays bounded.
  boot();arm();angle=12;Wire.frame(-0.2079f,0,0.9781f,0);
  for(int i=0;i<80;++i){tick();check(std::abs(motorCommand)<=MAX_PWM);}
  check(mode==Mode::FAULT);off();
  boot();command('a');Wire.frame(0,0,1,30);tick();
  check(mode==Mode::DISARMED);off();
  boot();arm();Serial.push("ax");loop();check(mode==Mode::DISARMED);off();

  // micros() rollover must not cause spurious timing failure.
  boot();fakeUs=0xFFFFFF00ULL;tickUs=sampleUs=micros();
  for(int i=0;i<5;++i){tick();}check(mode==Mode::DISARMED);off();
  // millis() rollover must still terminate the timed motor test.
  boot();fakeUs=0xFFFFFFF0ULL*1000;tickUs=sampleUs=micros();command('t');
  for(int i=0;i<40;++i){tick();}check(mode==Mode::DISARMED);off();

  boot();arm();writeOK=false;tick();check(mode==Mode::FAULT);off();
  boot();attachOK=false;setup();check(mode==Mode::FAULT);off();
  boot();Wire.regs[0x75]=0;setup();check(mode==Mode::FAULT);off();
  boot();Wire.frame(0.4f,0,0.9f,0);check(!calibrate());off();
  boot();commandsEnabled=false;Serial.push("at");loop();
  check(mode==Mode::DISARMED);off();loop();check(commandsEnabled);
  // Actual controller consumes a TeleRC mailbox; ACK completion tracks balance.
  boot();testInput.linked=testInput.fresh=testInput.armPending=true;
  testInput.lastRC=testInput.armAt=millis();testInput.armToken=1;
  loop();check(mode==Mode::ARMING);check(remoteArming);
  for(int i=0;i<260;++i){testInput.lastRC=millis();tick();}
  check(mode==Mode::BALANCING);check(completedArm==1&&armResult==0);
  testInput.motion=true;testInput.drive=1900;testInput.steer=1900;tick();
  check(requestedLean>0&&requestedLean<=MAX_LEAN_DEG);
  check(requestedYaw>0&&requestedYaw<=MAX_YAW_PWM);
  check(abs(duty[4])<=MAX_PWM&&abs(duty[5])<=MAX_PWM);
  // Link/task loss zeros travel immediately while the local balancing mode stays active.
  testInput.lastRC=millis()-CONTROL_TIMEOUT_MS;tick();
  check(requestedLean==0&&requestedYaw==0);check(mode==Mode::BALANCING);
  testInput.disarmToken=1;loop();check(mode==Mode::DISARMED);off();
  // No stale arm can start after 500ms just because a fresh RC snapshot arrived.
  boot();testInput.linked=testInput.fresh=testInput.armPending=true;
  testInput.armToken=1;testInput.armAt=millis()-501;testInput.lastRC=millis();
  loop();check(mode==Mode::DISARMED);check(completedArm==1&&armResult==2);off();
  // Pending arm loses its authority as soon as centered RC expires.
  boot();testInput.linked=testInput.fresh=testInput.armPending=true;
  testInput.armToken=1;testInput.armAt=testInput.lastRC=millis();loop();
  check(mode==Mode::ARMING);
  testInput.fresh=false;loop();check(mode==Mode::DISARMED);check(armResult==2);off();
  // Yaw never steals the common-mode balance correction's headroom.
  boot();driveMixed(MAX_PWM,MAX_YAW_PWM);
  check(duty[4]==MAX_PWM&&duty[7]==MAX_PWM);motorsOff();off();
  // End-to-end Core -> snapshot -> real controller, at the app's 10 Hz RC rate.
  boot();telerc::Core session;telerc::Datagram hello;hello.count=1;
  hello.packets[0].kind=telerc::Kind::DISCOVER;
  check(session.claim(0xc0a80402,14550,hello,millis()));
  session.apply(hello.packets[0],millis(),testStatus);
  uint8_t liveSequence=0;
  auto centeredPacket=[&]() {
    telerc::Packet p;p.message.id=70;p.message.sequence=liveSequence++;
    mini_mav::put16(p.message.payload,1500);mini_mav::put16(p.message.payload+2,1500);
    return p;
  };
  for(int i=0;i<80;++i) {
    session.watchdog(millis(),true);
    if(i%25==0)session.apply(centeredPacket(),millis(),testStatus);
    testInput=session.input;testInput.ready=true;tick();
  }
  telerc::Packet armPacket;armPacket.message.id=76;
  float armValue=1;memcpy(armPacket.message.payload,&armValue,4);
  mini_mav::put16(armPacket.message.payload+28,400);
  check(session.apply(armPacket,millis(),testStatus).result==5);
  for(int i=0;i<260;++i) {
    session.watchdog(millis(),true);
    if(i%25==0)session.apply(centeredPacket(),millis(),testStatus);
    testInput=session.input;testInput.ready=true;tick();
  }
  check(mode==Mode::BALANCING&&testStatus.armed&&armResult==0);
  auto moving=centeredPacket();mini_mav::put16(moving.message.payload+2,1600);
  session.apply(moving,millis(),testStatus);testInput=session.input;testInput.ready=true;tick();
  check(requestedLean>0);
  for(int i=0;i<90;++i){session.watchdog(millis(),true);testInput=session.input;testInput.ready=true;tick();}
  check(mode==Mode::BALANCING&&requestedLean==0&&requestedYaw==0);
  moving.message.sequence=liveSequence++;
  session.apply(moving,millis(),testStatus);check(!session.input.fresh);
  off();
  // Regression: WiFi starts even when boot exits before sensor readiness.
  boot();auto starts=testNetworkStarts;Wire.regs[0x75]=0;setup();
  check(testNetworkStarts==starts+1&&testInput.ready);check(mode==Mode::FAULT);off();
  loop();check(testStatus.fault&&!testStatus.ready&&!testStatus.armed);
  boot();starts=testNetworkStarts;Wire.busOK=false;setup();
  check(testNetworkStarts==starts+1&&testInput.ready);check(mode==Mode::FAULT);off();
  loop();check(testStatus.fault&&!testStatus.ready);
  boot();starts=testNetworkStarts;Wire.frame(0.4f,0,0.9f,0);setup();
  check(testNetworkStarts==starts+1&&testInput.ready);check(mode==Mode::FAULT);off();
  loop();check(testStatus.fault&&!testStatus.ready);
  boot();starts=testNetworkStarts;Wire.beginOK=false;setup();
  check(testNetworkStarts==starts+1&&testInput.ready);check(mode==Mode::FAULT);off();
  boot();starts=testNetworkStarts;attachOK=false;setup();
  check(testNetworkStarts==starts+1&&testInput.ready);check(mode==Mode::FAULT);off();
  // Specific physical and transport gates, rather than a generic centered-stick error.
  using B=telerc::ArmBlock;
  boot();directionsVerified=false;publishStatus();
  check(!testStatus.ready&&testStatus.physicalBlock==B::VERIFY_DIRECTIONS);
  testInput.linked=true;command('v');check(!directionsVerified); // Remote peer owns authority.
  boot();angle=8;publishStatus();check(testStatus.physicalBlock==B::HOLD_UPRIGHT);
  angle=0;rate=20;publishStatus();check(testStatus.physicalBlock==B::HOLD_STILL);
  rate=0;fakeUs+=NO_SAMPLE_US+1;publishStatus();check(testStatus.physicalBlock==B::SENSOR_STALE);
  boot();testInput.linked=testInput.fresh=testInput.armPending=true;
  testInput.armToken=1;testInput.armAt=testInput.lastRC=millis();
  directionsVerified=false;loop();check(completedBlock==B::VERIFY_DIRECTIONS);off();
  boot();testInput.linked=testInput.fresh=testInput.armPending=true;
  testInput.armToken=1;testInput.armAt=millis()-501;testInput.lastRC=millis();
  loop();check(completedBlock==B::ARM_EXPIRED);off();
  // Sensor discovery and identity/config diagnostics isolate the user's actual fault.
  boot();Wire.ack68=false;Wire.ack69=true;
  check(initMPU()&&mpuAddress==0x69&&mpuDiag.stage==MpuStage::READY);
  Sample addressSample;check(readSample(addressSample)==1&&Wire.lastReadAddress==0x69);off();
  boot();Wire.ack69=true;check(initMPU()&&mpuAddress==MPU_ADDRESS);off();
  boot();Wire.ack68=false;Wire.ack69=false;
  check(!initMPU()&&mpuDiag.stage==MpuStage::NO_ACK);
  check(!mpuDiag.probe[0].ack&&!mpuDiag.probe[1].ack);off();
  boot();check(mpuDiag.chipID==0x68&&Wire.writes[0x1D]==0);off();
  // Reproduce reported identity: supported MPU6500 profile must calibrate,
  // retain the direction lock and preserve runtime bus-fault motor cutoff.
  boot();Wire.regs[0x75]=0x70;setup();loop();
  check(mode==Mode::DISARMED&&calibrated&&mpuDiag.stage==MpuStage::READY);
  check(mpuDiag.chipID==0x70&&!strcmp(mpuProfileName(),"MPU6500_COMPAT"));
  check(Wire.regs[0x1D]==3&&Wire.writes[0x1D]==1);
  check(Wire.regs[0x1B]==0&&Wire.regs[0x1C]==0&&Wire.regs[0x19]==0);
  Sample mpu6500Sample;check(readSample(mpu6500Sample)==1&&mpu6500Sample.az==1);
  directionsVerified=false;check(physicalArmBlock()==B::VERIFY_DIRECTIONS);off();
  directionsVerified=true;arm();Wire.busOK=false;tick();check(mode==Mode::FAULT);off();
  boot();Wire.ack68=false;Wire.ack69=true;Wire.who69=0x70;
  check(initMPU()&&mpuAddress==0x69&&mpuDiag.chipID==0x70);off();
  boot();Wire.regs[0x75]=0x70;Wire.failWriteReg=0x1D;setup();
  check(mode==Mode::FAULT&&!calibrated&&mpuDiag.stage==MpuStage::CONFIG_WRITE_FAILED);off();
  boot();Wire.regs[0x75]=0x70;Wire.failReadReg=0x1D;setup();
  check(mode==Mode::FAULT&&!calibrated&&mpuDiag.stage==MpuStage::CONFIG_READ_FAILED);off();
  boot();Wire.regs[0x75]=0x70;Wire.mismatchReg=0x1D;setup();
  check(mode==Mode::FAULT&&!calibrated&&mpuDiag.stage==MpuStage::CONFIG_MISMATCH);
  check(mpuDiag.reg==0x1D&&mpuDiag.expected==3&&mpuDiag.actual==0);off();
  boot();Wire.regs[0x75]=0x70;Wire.whoAfterReset=0x68;setup();
  check(mode==Mode::FAULT&&!calibrated&&mpuDiag.stage==MpuStage::IDENTITY_CHANGED);off();
  for(uint8_t unknown : {0x00,0xFF,0x71,0x73}) {
    boot();Wire.regs[0x75]=unknown;setup();
    check(mode==Mode::FAULT&&!calibrated&&mpuDiag.stage==MpuStage::UNSUPPORTED_ID);off();
  }
  boot();Wire.regs[0x75]=0x71;
  check(!initMPU()&&mpuDiag.stage==MpuStage::UNSUPPORTED_ID);
  check(mpuDiag.probe[0].ack&&mpuDiag.probe[0].who==0x71);off();
  boot();Wire.failReadReg=0x75;
  check(!initMPU()&&mpuDiag.stage==MpuStage::WHO_READ_FAILED);off();
  boot();Wire.failWriteReg=0x1A;
  check(!initMPU()&&mpuDiag.stage==MpuStage::CONFIG_WRITE_FAILED&&mpuDiag.reg==0x1A);off();
  boot();Wire.failReadReg=0x1A;
  check(!initMPU()&&mpuDiag.stage==MpuStage::CONFIG_READ_FAILED&&mpuDiag.reg==0x1A);off();
  boot();Wire.regs[0x1A]=0;Wire.mismatchReg=0x1A;
  check(!initMPU()&&mpuDiag.stage==MpuStage::CONFIG_MISMATCH);
  check(mpuDiag.expected==3&&mpuDiag.actual==0);off();
  boot();Wire.transientWrites=2;
  check(initMPU()&&mpuDiag.stage==MpuStage::READY&&Wire.transientWrites==0);off();
  boot();Wire.busOK=false;
  check(readSample(addressSample)<0&&mpuDiag.stage==MpuStage::READ_FAILED);
  check(mpuAddress==MPU_ADDRESS);off();
  // Calibration failure snapshots distinguish mounting/movement/data failures.
  boot();check(!strcmp(calDiag.stage,"CAL_READY")&&calDiag.count==600&&calDiag.sampleValid);
  Wire.frame(0,0,-1,0);check(!calibrate());
  check(!strcmp(calDiag.stage,"CAL_Z_NOT_UP")&&calDiag.sample.az==-1);off();
  boot();Wire.frame(0,0,0.5f,0);check(!calibrate());
  check(!strcmp(calDiag.stage,"CAL_ACCEL_MAGNITUDE"));off();
  boot();Wire.frame(0,0.2f,0.98f,0);check(!calibrate());
  check(!strcmp(calDiag.stage,"CAL_SIDE_TILT"));off();
  boot();Wire.frame(-0.20f,0,0.98f,0);check(!calibrate());
  check(!strcmp(calDiag.stage,"CAL_FORWARD_TILT"));off();
  boot();Wire.frame(0,0,1,10);check(!calibrate());
  check(!strcmp(calDiag.stage,"CAL_GYRO_MOVEMENT_OR_BIAS")&&calDiag.sample.gy==10);off();
  boot();Wire.ready=false;check(!calibrate());
  check(!strcmp(calDiag.stage,"CAL_NO_FRESH_SAMPLE")&&!calDiag.sampleValid);off();
  boot();Wire.failReadReg=0x3B;check(!calibrate());
  check(!strcmp(calDiag.stage,"SAMPLE_FRAME_BUS_ERROR")&&!sampleDiag.rawValid);off();
  boot();Wire.failReadReg=0x3A;Sample snapshot;check(readSample(snapshot)<0);
  check(!strcmp(sampleDiag.stage,"SAMPLE_STATUS_BUS_ERROR"));off();
  boot();Wire.frame(0,0,0,0);check(!calibrate());
  check(!strcmp(calDiag.stage,"SAMPLE_ALL_ZERO")&&sampleDiag.rawValid);off();
  boot();for(int i=0x3B;i<=0x48;++i)Wire.regs[i]=0xFF;
  check(!calibrate()&&!strcmp(calDiag.stage,"SAMPLE_ALL_FF"));off();
  boot();Wire.frame(0,0,1,250);check(!calibrate());
  check(!strcmp(calDiag.stage,"SAMPLE_AXIS_CLIPPED")&&sampleDiag.clippedAxis==4);
  check(sampleDiag.rawValid&&sampleDiag.raw[4]==32750);off();
  boot();Wire.frameHook=[](unsigned n){Wire.frame(0,0,1,n%2?1:-1);};
  check(!calibrate()&&!strcmp(calDiag.stage,"CAL_GYRO_VARIANCE"));
  check(calDiag.count==600&&calDiag.variance>0.25f);off();
  boot();Wire.frameHook=[](unsigned n){Wire.frame(n%2?0.01745f:-0.01745f,0,0.99985f,0);};
  check(!calibrate()&&!strcmp(calDiag.stage,"CAL_TILT_VARIATION"));
  check(calDiag.span>1.5f);off();
  boot();Wire.readDelay=22000;
  check(!calibrate()&&!strcmp(calDiag.stage,"CAL_COLLECTION_TIMEOUT"));off();
  boot();stopLow=true;check(!calibrate()&&!strcmp(calDiag.stage,"CAL_STOP_SWITCH"));off();
  // Startup movement must be allowed to settle, without accepting partial
  // windows or weakening runtime sensor faults and explicit arming.
  boot();Wire.framesRead=0;
  Wire.frameHook=[](unsigned n){Wire.frame(0,0,1,n<100?12:0);};
  check(calibrate()&&!strcmp(calDiag.stage,"CAL_READY"));
  check(calDiag.count==600&&Wire.framesRead==700&&gyroBias==0);
  check(mode==Mode::DISARMED);off();
  boot();Wire.framesRead=0;
  Wire.frameHook=[](unsigned n){Wire.frame(n==300?0.3f:0,0,n==300?0.95f:1,0);};
  check(calibrate()&&calDiag.count==600&&Wire.framesRead==901);off();
  boot();Wire.framesRead=0;
  Wire.frameHook=[](unsigned n){Wire.frame(0,0,1,n%500==0?12:0);};
  check(!calibrate()&&calDiag.count<600);
  check(!strcmp(calDiag.stage,"CAL_COLLECTION_TIMEOUT"));
  check(!strcmp(calDiag.lastReject,"CAL_GYRO_MOVEMENT_OR_BIAS"));off();
  boot();Sample invalid{0,0,1,NAN,0,0};
  check(!strcmp(calibrationSampleBlock(invalid,1,0),"CAL_NONFINITE"));off();
  std::printf("Control/fault integration: %d assertions PASS\n",checks);
}

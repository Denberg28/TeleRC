// Persisted NVS survives simulated resets; real sketch startup and serial paths.
#include <cassert>
#include <cstring>
#include "../TT_Balancer_ESP32S3.ino"
int checks=0;
void check(bool b){assert(b);++checks;}
void off(){check(motorCommand==0&&duty[4]==0&&duty[5]==0&&duty[6]==0&&duty[7]==0);}
void prepare(float gy=0,float ax=0) {
  fakeUs=0;stopLow=false;attachOK=writeOK=true;
  Wire.reset();Wire.frame(ax,0,1,gy);Serial.input.clear();
  mode=Mode::DISARMED;pwmReady=calibrated=false;
  gyroBias=angle=rate=integral=0;
  linkInput=telerc::Input{};seenArm=seenDisarm=completedArm=0;
  remoteArming=false;completedBlock=telerc::ArmBlock::NONE;
}
void boot(float gy=0,float ax=0){prepare(gy,ax);setup();loop();}
void command(char c){Serial.input.push_back(c);loop();}
int main(){
  resetPreferences();boot(1.25f);
  check(calibrated&&mode==Mode::DISARMED&&calDiag.count==600);
  check(!strcmp(calStoreState,"SAVED")&&preferenceWrites==1);off();
  auto original=preferenceBlob;float saved=gyroBias;
  boot(1.25f,-0.05f);
  check(calibrated&&mode==Mode::DISARMED&&calDiag.count==SAVED_CHECK_SAMPLES);
  check(!strcmp(calDiag.stage,"CAL_SAVED_READY")&&!strcmp(calStoreState,"REUSED"));
  check(gyroBias==saved&&angle>2&&angle<3&&rate==0); // Fresh gravity angle, never zeroed.
  check(preferenceWrites==1&&preferenceBlob==original&&!directionsVerified);off();
  check(millis()<1500); // Successful reuse takes short window, not a full calibration.
  for(int i=0;i<3;++i){boot(1.25f);check(preferenceWrites==1);off();}

  boot(1.25f,0.12f);
  check(calibrated&&angle<-6&&calDiag.count==SAVED_CHECK_SAMPLES);
  directionsVerified=true;
  check(physicalArmBlock()==telerc::ArmBlock::HOLD_UPRIGHT);off();
  preferenceBlob=original;prepare();Wire.framesRead=0;
  Wire.frameHook=[](unsigned n){Wire.frame(0,0,1,n%100==0?12:1.25f);};setup();
  check(mode==Mode::FAULT&&!strcmp(calDiag.stage,"CAL_SAVED_CHECK_TIMEOUT"));
  check(!strcmp(calDiag.lastReject,"CAL_GYRO_MOVEMENT_OR_BIAS")&&preferenceBlob==original);off();

  // Corruption, wrong size, profile/mount/bus context and invalid bias cause full calibration.
  for(int test=0;test<6;++test){
    preferenceBlob=original;
    SavedCalibration r;memcpy(&r,original.data(),sizeof(r));
    if(test==0)preferenceBlob[0]^=1;
    if(test==1)preferenceBlob.resize(5);
    if(test>=2){
      if(test==2)++r.schema;
      if(test==3)r.context^=1; // Changed mounting.
      if(test==4)r.bias=NAN;
      if(test==5)r.bias=9;
      r.checksum=calibrationChecksum(r);memcpy(preferenceBlob.data(),&r,sizeof(r));
    }
    boot();check(calibrated&&calDiag.count==600&&!strcmp(calStoreState,"SAVED"));off();
  }
  preferenceBlob=original;prepare();Wire.regs[0x75]=0x70;setup();
  check(calibrated&&calDiag.count==600&&mpuDiag.chipID==0x70);off();
  preferenceBlob=original;prepare();Wire.ack68=false;Wire.ack69=true;setup();
  check(calibrated&&calDiag.count==600&&mpuAddress==0x69);off();

  // Stable residual >1 dps triggers full calibration once and stores its new result.
  preferenceBlob=original;boot(3);
  check(calibrated&&calDiag.count==600&&gyroBias>2.9f&&!strcmp(calStoreState,"SAVED"));off();
  // Moving/invalid saved checks never overwrite a previous calibration.
  preferenceBlob=original;boot(12);
  check(!calibrated&&mode==Mode::FAULT&&preferenceBlob==original);
  check(!strcmp(calDiag.stage,"CAL_GYRO_MOVEMENT_OR_BIAS"));off();
  preferenceBlob=original;prepare();Wire.failReadReg=0x3B;setup();
  check(!calibrated&&mode==Mode::FAULT&&preferenceBlob==original);off();
  prepare();Wire.frame(0,0,1,250);setup();check(mode==Mode::FAULT&&!calibrated);off();
  prepare();Wire.ready=false;setup();check(mode==Mode::FAULT&&!calibrated);off();
  prepare();stopLow=true;setup();check(mode==Mode::FAULT&&!calibrated);off();
  prepare();Wire.frameHook=[](unsigned n){Wire.frame(0,0,1,n%2?1:-1);};setup();
  check(mode==Mode::FAULT&&!strcmp(calDiag.stage,"CAL_GYRO_VARIANCE"));off();
  prepare();Wire.frameHook=[](unsigned n){Wire.frame(n%2?0.01745f:-0.01745f,0,0.99985f,0);};setup();
  check(mode==Mode::FAULT&&!strcmp(calDiag.stage,"CAL_TILT_VARIATION"));off();
  prepare();Wire.framesRead=0;
  Wire.frameHook=[](unsigned n){Wire.frame(0,0,1,n<100?12:1.25f);};setup();
  check(calibrated&&calDiag.count==SAVED_CHECK_SAMPLES&&preferenceBlob==original);off();
  // Report timeout instead of an old motion rejection after valid partial collection.
  resetPreferences();prepare();Wire.framesRead=0;
  Wire.frameHook=[](unsigned n){Wire.frame(0,0,1,n%500==0?12:0);};setup();
  check(!calibrated&&mode==Mode::FAULT&&!strcmp(calDiag.stage,"CAL_COLLECTION_TIMEOUT"));
  check(!strcmp(calDiag.lastReject,"CAL_GYRO_MOVEMENT_OR_BIAS")&&calDiag.count>0);off();

  // Persistence errors do not falsely claim a save or invalidate good live calibration.
  resetPreferences();preferenceOpen=false;boot();
  check(calibrated&&!strcmp(calStoreState,"SAVE_FAILED")&&preferenceBlob.empty());off();
  resetPreferences();preferenceWrite=false;boot();
  check(calibrated&&!strcmp(calStoreState,"SAVE_FAILED")&&preferenceBlob.empty());off();

  // c requires safe state + no peer; x in a batch has precedence over c.
  resetPreferences();boot();auto restarts=ESP.restarts;
  testInput.linked=true;loop();command('c');check(ESP.restarts==restarts&&!preferenceBlob.empty());off();
  testInput.linked=false;loop();mode=Mode::ARMING;command('c');check(ESP.restarts==restarts);
  mode=Mode::BALANCING;command('c');check(ESP.restarts==restarts);
  mode=Mode::MOTOR_TEST;command('c');check(ESP.restarts==restarts);
  mode=Mode::DISARMED;Serial.push("cx");loop();check(ESP.restarts==restarts&&!preferenceBlob.empty());off();
  preferenceRemove=false;command('c');check(ESP.restarts==restarts&&!preferenceBlob.empty());off();
  preferenceRemove=true;command('c');check(ESP.restarts==restarts+1&&preferenceBlob.empty());off();
  boot();check(calDiag.count==600&&calibrated&&!preferenceBlob.empty());
  // Calibration-only boot fault can request c; runtime and stop faults cannot.
  boot(12);check(mode==Mode::FAULT&&!calibrated);restarts=ESP.restarts;
  command('c');check(ESP.restarts==restarts+1&&preferenceBlob.empty());off();
  boot();fault("runtime");restarts=ESP.restarts;command('c');check(ESP.restarts==restarts);off();
  boot();stopLow=true;command('c');check(ESP.restarts==restarts);off();
  std::printf("Saved calibration: %d assertions PASS\n",checks);
}

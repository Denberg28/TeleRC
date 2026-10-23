#include <cassert>
#include <cstring>
#include "../TT_Balancer_ESP32S3.ino"
int checks=0;
void check(bool b){assert(b);++checks;}
void off(){check(motorCommand==0&&duty[4]==0&&duty[5]==0&&duty[6]==0&&duty[7]==0);}
void boot(){
 resetPreferences();Wire.reset();Serial.input.clear();fakeUs=0;stopLow=false;attachOK=writeOK=true;
 mode=Mode::DISARMED;pwmReady=calibrated=false;gyroBias=angle=rate=integral=0;
 seenArm=seenDisarm=completedArm=0;remoteArming=false;setup();loop();
 testInput.linked=true;loop();check(calibrated&&mode==Mode::DISARMED);off();
}
void tick(unsigned us=4000){fakeUs+=us;loop();}
void request(balance_admin::Action a){testInput.setupAction=a;testInput.setupAt=millis();testInput.setupRequest++;testInput.setupToken++;loop();}
int main(){
 using namespace balance_admin;Request r;
 auto parse=[&](const char *s){return balance_admin::parse((const uint8_t *)s,strlen(s),r);};
 check(parse("TELERC_BALANCE_V1|Q")&&r.query);
 check(parse("TELERC_BALANCE_V1|S")&&r.stop);
 check(parse("TELERC_BALANCE_V1|123|456|4")&&r.action==BOTH&&r.id==456&&r.challenge==123);
 for(auto s:{"TELERC_BALANCE_V1|0|1|4","TELERC_BALANCE_V1|1|0|4","TELERC_BALANCE_V1|1|2|6","TELERC_BALANCE_V1|1|2|4|1","TELERC_BALANCE_V1|4294967296|2|4","TELERC_BALANCE_V1|1|2|-1","TELERC_BALANCE_V1| Q","TELERC_BALANCE_V1|Q\n"})check(!parse(s));
 const uint8_t nul[]="TELERC_BALANCE_V1|Q\0oops";check(!balance_admin::parse(nul,sizeof(nul)-1,r));
 Lease lease;check(parse("TELERC_BALANCE_V1|123|456|4"));
 lease.issue(123,10);check(lease.consume(r,100));check(!lease.consume(r,100));
 lease.issue(123,10);check(!lease.consume(r,100)); // Same request ID never replays.
 r.id++;lease.issue(123,10);check(!lease.consume(r,1600));
 lease.issue(123,0xfffffff0);check(lease.consume(r,20)); // Wrap-safe expiry.
 lease.issue(123,0);lease.reset();check(!lease.consume(r,1));
 boot();request(LEFT);check(mode==Mode::MOTOR_TEST&&duty[4]==TEST_PWM&&duty[5]==0&&duty[6]==0&&duty[7]==0);
 check(TEST_PWM==140&&TEST_MS==300&&testOutputValid);
 check(testLeft==140&&testRight==0&&testDuty[0]==140&&testDuty[1]==0&&testDuty[2]==0&&testDuty[3]==0);
 for(int i=0;i<40;i++){tick();}check(mode==Mode::MOTOR_TEST&&motorCommand==140); // Old 150ms deadline must not finish the new test.
 for(int i=0;i<int(TEST_MS/4+2);i++){tick();}check(!wheelTestComplete&&!strcmp(setupResult,"TEST_FINISHED"));off();
 request(VERIFY);check(!directionsVerified&&!strcmp(setupResult,"VERIFY_NEEDS_BOTH_MOTOR_TEST"));off();
 request(RIGHT);check(mode==Mode::MOTOR_TEST&&duty[4]==0&&duty[5]==0&&duty[6]==0&&duty[7]==TEST_PWM);
 check(testOutputValid&&testLeft==0&&testRight==-140&&testDuty[3]==140);
 for(int i=0;i<int(TEST_MS/4+2);i++){tick();}check(!wheelTestComplete);off();
 request(BOTH);check(mode==Mode::MOTOR_TEST&&duty[4]==TEST_PWM&&duty[7]==TEST_PWM);
 for(int i=0;i<int(TEST_MS/4+2);i++){tick();}check(wheelTestComplete&&!directionsVerified);off();
 publishStatus();check(testStatus.testOutputValid&&testStatus.testLeft==140&&testStatus.testRight==-140);
 check(testStatus.testDuty[0]==140&&testStatus.testDuty[3]==140); // Last-test evidence survives electrical zero.
 request(VERIFY);check(directionsVerified&&mode==Mode::DISARMED&&!strcmp(setupResult,"DIRECTIONS_CONFIRMED"));off();
 boot();request(BOTH);testInput.setupStop++;testInput.disarmToken++;loop();
 check(mode==Mode::DISARMED&&!wheelTestComplete&&!strcmp(setupResult,"STOPPED"));off();
 boot();testInput.setupAt=millis()-501;testInput.setupAction=BOTH;testInput.setupRequest=99;testInput.setupToken++;loop();
 check(mode==Mode::DISARMED&&!strcmp(setupResult,"STALE_REQUEST"));off();
 boot();testInput.setupAction=BOTH;testInput.setupAt=millis();testInput.setupToken++;testInput.disarmToken++;loop();
 check(mode==Mode::DISARMED&&!strcmp(setupResult,"CANCELLED_BY_STOP_OR_RESTART"));off();
 boot();mode=Mode::BALANCING;request(BOTH);check(mode==Mode::BALANCING&&!strcmp(setupResult,"REJECTED_STATE_OR_STOP"));off();
 boot();mode=Mode::ARMING;request(BOTH);check(mode==Mode::ARMING&&!strcmp(setupResult,"REJECTED_STATE_OR_STOP"));off();
 boot();fault("runtime");request(CALIBRATE);check(mode==Mode::FAULT&&!recalibratePending);off();
 boot();request(BOTH);writeOK=false;tick(); // During test no drive rewrite; trigger explicit write failure.
 drive(TEST_PWM);check(mode==Mode::FAULT&&!strcmp(setupResult,"TEST_ABORTED_FAULT"));off();
 boot();request(CALIBRATE);check(recalibratePending&&!calibrated&&preferenceBlob.empty());off();
 auto restarts=ESP.restarts;
 for(int i=0;i<63;i++){tick();}check(ESP.restarts>restarts);off();
 boot();request(BOTH);for(int i=0;i<int(TEST_MS/4+2);i++){tick();}angle=8;request(VERIFY);
 check(!directionsVerified&&!strcmp(setupResult,"HOLD_UPRIGHT_AND_STILL"));off();
 boot();stopLow=true;request(BOTH);check(mode==Mode::FAULT);off();
 boot();request(BOTH);for(int i=0;i<10;++i)tick();
 testInput.setupStop++;testInput.disarmToken++;loop();check(mode==Mode::DISARMED&&!wheelTestComplete);off();
 boot();request(BOTH);Wire.busOK=false;tick();check(mode==Mode::FAULT&&!wheelTestComplete);off();
 boot();fakeUs=0xFFFFFFF0ULL*1000;tickUs=sampleUs=micros();request(BOTH);
 for(int i=0;i<int(TEST_MS/4+2);++i){tick();}
 check(mode==Mode::DISARMED&&wheelTestComplete);off();
 std::printf("Remote balancer admin/controller: %d assertions PASS\n",checks);
}

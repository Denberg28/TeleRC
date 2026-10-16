#include <cassert>
#include <cmath>
#define TELERC_HOST_TEST 1
#include "../TeleRC_MiniRover_WiFi/TeleRC_MiniRover_WiFi.ino"
int main() {
  // Only MX1508 receives the 95% drive/steering ceiling in this release.
  assert(config::MAX_PWM==(TELERC_DRIVER==1 ? 242 : 180));
  assert(config::STEER_GAIN==(TELERC_DRIVER==1 ? 1.0f : 0.5f));
  assert(hardware::PWM_HZ==(TELERC_DRIVER==5 ? 10000 : 20000));
  for(unsigned i=0;i<4;++i) assert(config::MOTOR_DUTY_CAP[i]==config::MAX_PWM);
  setup();assert(pwmReady && wifiReady && !state.armed);
  int16_t demand[4]={100,-100,100,-100};
  motorBank.apply(demand,100);motorBank.apply(demand,110);
  motorBank.stopAll();
  if(hardware::GLOBAL_ENABLE>=0)assert(pins[hardware::GLOBAL_ENABLE]==LOW);
  for(uint8_t i=0;i<4;++i)if(hardware::active(i)){
    const auto&p=hardware::MOTORS[i];
    if(hardware::dualPWM()){assert(duties[p.a]==0 && duties[p.b]==0);}
    else {assert(duties[p.extra]==0 && pins[p.a]==LOW && pins[p.b]==LOW);}
    if(TELERC_DRIVER==4)assert(pins[p.extra]==LOW);
  }
}

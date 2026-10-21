/* TT Balancer 0.3.1 -- TeleRC Wi-Fi bench firmware, Arduino-ESP32 3.x.
 * No external MPU library. Wire + LEDC are supplied by the ESP32 board package.
 * READ README.md BEFORE CONNECTING MOTOR POWER. Defaults forbid arming.
 */
#include <Arduino.h>
#include <Wire.h>
#include <esp_arduino_version.h>
#include <math.h>
#include <string.h>
#include "Config.h"
#include "BalanceTypes.h"
#include "SensorDiagnostics.h"
#include "NetworkLink.h"
#include "MpuDiagnostics.h"
#include "CalibrationStore.h"
#if ESP_ARDUINO_VERSION_MAJOR < 3
#error "Install Espressif Arduino-ESP32 3.x (this sketch uses pin-based LEDC)."
#endif

Mode mode = Mode::DISARMED;
bool pwmReady = false, calibrated = false;
bool commandsEnabled = false;
uint32_t serialBytes=0, serialBootDiscard=0;
uint8_t serialLastByte=0, serialLastCommand=0;
const char *serialResult="IDLE";
bool serialReportRequested=false;

int readSerialByte() {
  int value=Serial.read();
  if(value>=0) { ++serialBytes; serialLastByte=(uint8_t)value; }
  return value;
}
bool serialWhitespace(int c) { return c==' ' || c=='\t' || c=='\r' || c=='\n'; }
void serialOutcome(const char *result) {
  serialResult=result; serialReportRequested=true;
}
bool balanceTaskStarted=false;
bool directionsVerified=DIRECTIONS_VERIFIED, wheelTestComplete=false;
const char *reason = "boot";
float gyroBias = 0, angle = 0, rate = 0, integral = 0;
int motorCommand = 0;
uint8_t testMotorMask=3;
bool testOutputValid=false;
int16_t testLeft=0,testRight=0;
uint16_t testDuty[4]{};
uint32_t seenSetup=0,setupRequest=0,seenSetupStop=0,recalibrateAt=0;
bool recalibratePending=false,remoteTest=false;
const char *setupResult="IDLE";
uint32_t tickUs = 0, sampleUs = 0, uprightSince = 0;
uint32_t saturationSince = 0, testUntil = 0, reportMs = 0;
bool uprightTiming = false, saturationTiming = false;
telerc::Input linkInput;
uint32_t seenArm=0, seenDisarm=0, completedArm=0;
uint8_t armResult=2;
telerc::ArmBlock completedBlock=telerc::ArmBlock::NONE;
bool remoteArming=false;
float requestedLean=0, requestedYaw=0;

void clearMotionRequest() { requestedLean=requestedYaw=0; }
void finishRemoteArm(uint8_t result) {
  if(remoteArming) {
    completedArm=seenArm; armResult=result; remoteArming=false;
    completedBlock=result==0 ? telerc::ArmBlock::NONE : telerc::ArmBlock::CANCELLED;
  }
}

float limitf(float x, float low, float high) {
  return fminf(high, fmaxf(low, x));
}
bool stopped() { return digitalRead(STOP_PIN) == LOW; }

void motorsOff() {
  motorCommand = 0;
  if (pwmReady) {
    ledcWrite(LEFT_IN1, 0); ledcWrite(LEFT_IN2, 0);
    ledcWrite(RIGHT_IN1, 0); ledcWrite(RIGHT_IN2, 0);
  } else {
    digitalWrite(LEFT_IN1, LOW); digitalWrite(LEFT_IN2, LOW);
    digitalWrite(RIGHT_IN1, LOW); digitalWrite(RIGHT_IN2, LOW);
  }
}

void disarm(const char *why) {
  if(mode==Mode::MOTOR_TEST) {wheelTestComplete=false;if(remoteTest)setupResult="TEST_INTERRUPTED";remoteTest=false;}
  motorsOff(); integral = 0; clearMotionRequest(); finishRemoteArm(2);
  uprightTiming = saturationTiming = false;
  mode = Mode::DISARMED; reason = why;
}

void fault(const char *why) {
  if(mode==Mode::MOTOR_TEST) serialOutcome("TEST_ABORTED_FAULT");
  if(remoteTest)setupResult="TEST_ABORTED_FAULT";
  remoteTest=false;wheelTestComplete=false;
  motorsOff(); integral = 0; clearMotionRequest(); finishRemoteArm(2);
  // Faults require reset: release PWM routing and explicitly force GPIO LOW.
  const uint8_t pins[] = {LEFT_IN1, LEFT_IN2, RIGHT_IN1, RIGHT_IN2};
  for (uint8_t pin : pins) {
    if (pwmReady) ledcDetach(pin);
    pinMode(pin, OUTPUT); digitalWrite(pin, LOW);
  }
  pwmReady = false;
  uprightTiming = saturationTiming = false;
  mode = Mode::FAULT; reason = why; completedBlock=telerc::ArmBlock::FAULT; // Latched: reset board to clear.
}

// Clear the inactive input BEFORE enabling the active one on every update.
// Both inputs LOW = coast. No active electrical braking is requested.
bool oneMotor(uint8_t a, uint8_t b, int command) {
  if (command >= 0) {
    if (!ledcWrite(b, 0)) return false;
    return ledcWrite(a, command);
  }
  if (!ledcWrite(a, 0)) return false;
  return ledcWrite(b, -command);
}

void driveMixed(int command,float yaw) {
  if (mode == Mode::FAULT || stopped()) { motorsOff(); return; }
  command = (int)limitf(command, -MAX_PWM, MAX_PWM);
  // Balance has priority; steering only consumes the remaining PWM headroom.
  int room=MAX_PWM-abs(command);
  int turn=(int)lroundf(limitf(yaw,-room,room));
  if (!oneMotor(LEFT_IN1, LEFT_IN2, (mode==Mode::MOTOR_TEST && !(testMotorMask&1) ? 0 : command+turn) * LEFT_SIGN) ||
      !oneMotor(RIGHT_IN1, RIGHT_IN2, (mode==Mode::MOTOR_TEST && !(testMotorMask&2) ? 0 : command-turn) * RIGHT_SIGN)) {
    fault("PWM write"); return;
  }
  motorCommand = command;
}

void drive(int command) { driveMixed(command,0); }

// Retain output evidence after the short pulse, so the next app poll can show it.
// Peripheral duty readback cannot establish motor voltage/current or movement.
void captureTestOutput() {
  testOutputValid=mode==Mode::MOTOR_TEST && pwmReady;
  if(!testOutputValid)return;
  testLeft=(testMotorMask&1) ? TEST_PWM*LEFT_SIGN : 0;
  testRight=(testMotorMask&2) ? TEST_PWM*RIGHT_SIGN : 0;
  const uint8_t pins[]={LEFT_IN1,LEFT_IN2,RIGHT_IN1,RIGHT_IN2};
  for(uint8_t i=0;i<4;++i)testDuty[i]=uint16_t(ledcRead(pins[i]));
}

int16_t signedWord(const uint8_t *b) {
  return (int16_t)(((uint16_t)b[0] << 8) | b[1]);
}

// Returns 1=new sample, 0=no data ready, -1=bus/data error.
int readSample(Sample &s) {
  sampleDiag=SampleDiagnostic{};
  uint8_t status = 0, b[14];
  if (!readRegs(0x3A, &status, 1)) { sampleDiag.stage="SAMPLE_STATUS_BUS_ERROR"; return -1; }
  if (!(status & 1)) { sampleDiag.stage="SAMPLE_WAITING"; return 0; }
  if (!readRegs(0x3B, b, 14)) { sampleDiag.stage="SAMPLE_FRAME_BUS_ERROR"; return -1; }
  bool allZero = true, allFF = true;
  for (uint8_t v : b) { allZero &= v == 0; allFF &= v == 255; }
  int16_t values[6] = {signedWord(b), signedWord(b+2), signedWord(b+4),
                       signedWord(b+8), signedWord(b+10), signedWord(b+12)};
  sampleDiag.rawValid=true;
  for(uint8_t i=0;i<6;++i) sampleDiag.raw[i]=values[i];
  if(allZero || allFF) {
    sampleDiag.stage=allZero ? "SAMPLE_ALL_ZERO" : "SAMPLE_ALL_FF"; return -1;
  }
  // A clipped sensor cannot provide trustworthy feedback.
  for(uint8_t i=0;i<6;++i) if(values[i]>32700 || values[i]<-32700) {
    sampleDiag.clippedAxis=(int8_t)i; sampleDiag.stage="SAMPLE_AXIS_CLIPPED"; return -1;
  }
  s.ax = values[0] / 16384.0f; s.ay = values[1] / 16384.0f;
  s.az = values[2] / 16384.0f; s.gx = values[3] / 131.0f;
  s.gy = values[4] / 131.0f; s.gz = values[5] / 131.0f;
  s = robotFrame(s, BALANCER_IMU_MOUNT==1);
  sampleDiag.stage="SAMPLE_VALID";
  return 1;
}

float accelAngle(const Sample &s) {
  return atan2f(-s.ax, s.az) * 57.2957795f;
}

bool calibrate() {
  // Motors stay off. Hold chassis still, upright, +Z up, for ~3 seconds.
  motorsOff();
  calDiag=CalibrationDiagnostic{}; calDiag.stage="CAL_COLLECTING";
  float sumY = 0, sumY2 = 0, sumAngle = 0, minA = 999, maxA = -999;
  uint16_t n = 0;
  
  uint32_t start = millis(), last = micros();
  while (n < 600 && (uint32_t)(millis() - start) < CALIBRATION_TIMEOUT_MS) {
    if (stopped()) { calDiag.stage="CAL_STOP_SWITCH"; return false; }
    Sample s;
    int result = readSample(s);
    if (result < 0) { calDiag.stage=sampleDiag.stage; return false; }
    if (!result) {
      if ((uint32_t)(micros() - last) > NO_SAMPLE_US) {
        calDiag.stage="CAL_NO_FRESH_SAMPLE"; return false;
      }
      delay(1); continue;
    }
    last = micros();
    float magnitude = sqrtf(s.ax*s.ax + s.ay*s.ay + s.az*s.az);
    float a = accelAngle(s);
    calDiag.sample=s; calDiag.sampleValid=true;
    calDiag.magnitude=magnitude; calDiag.tilt=a;
    if(const char *block=calibrationSampleBlock(s,magnitude,a)) {
      calDiag.stage=block;
      // Allow settling, then require a whole new 600-sample window.
      // Bus errors, clipped/invalid raw frames and STOP still fail immediately.
      if(!strcmp(block,"CAL_NONFINITE")) return false;
      calDiag.lastReject=block;
      n=calDiag.count=0; sumY=sumY2=sumAngle=0; minA=999; maxA=-999;
      delay(4); continue;
    }
    calDiag.stage="CAL_COLLECTING";
    sumY += s.gy; sumY2 += s.gy*s.gy; sumAngle += a;
    minA = fminf(minA, a); maxA = fmaxf(maxA, a);
    ++n; calDiag.count=n; delay(4);
  }
  if (n != 600) {
    calDiag.stage=n ? "CAL_COLLECTION_TIMEOUT" : calDiag.lastReject;
    if(!strcmp(calDiag.stage,"NONE")) calDiag.stage="CAL_COLLECTION_TIMEOUT";
    return false;
  }
  float mean = sumY/n;
  calDiag.variance=sumY2/n-mean*mean; calDiag.span=maxA-minA;
  if(calDiag.variance>0.25f) { calDiag.stage="CAL_GYRO_VARIANCE"; return false; }
  if(calDiag.span>1.5f) { calDiag.stage="CAL_TILT_VARIATION"; return false; }
  gyroBias = mean; angle = sumAngle/n; rate = 0;
  calDiag.stage="CAL_READY";
  // No automatic angle zeroing: physical upright remains the reference.
  return true;
}

// Reuse bias only after a new stable sensor window. Do not reuse an old angle,
// zero gravity tilt, restore an armed state, or write flash during balancing.
bool checkSavedCalibration() {
  motorsOff(); calDiag=CalibrationDiagnostic{};
  calDiag.stage="CAL_SAVED_CHECK";
  float sum=0,sum2=0,sumAngle=0,minA=999,maxA=-999;
  uint16_t n=0;
  uint32_t start=millis(),last=micros();
  while(n<SAVED_CHECK_SAMPLES && uint32_t(millis()-start)<SAVED_CHECK_TIMEOUT_MS) {
    if(stopped()) { calDiag.stage="CAL_STOP_SWITCH"; return false; }
    Sample s; int result=readSample(s);
    if(result<0) { calDiag.stage=sampleDiag.stage; return false; }
    if(!result) {
      if(uint32_t(micros()-last)>NO_SAMPLE_US) { calDiag.stage="CAL_NO_FRESH_SAMPLE"; return false; }
      delay(1); continue;
    }
    last=micros();
    float magnitude=sqrtf(s.ax*s.ax+s.ay*s.ay+s.az*s.az),a=accelAngle(s);
    calDiag.sample=s; calDiag.sampleValid=true; calDiag.magnitude=magnitude; calDiag.tilt=a;
    if(const char *block=calibrationSampleBlock(s,magnitude,a)) {
      calDiag.stage=block; calDiag.lastReject=block;
      if(!strcmp(block,"CAL_NONFINITE")) return false;
      n=calDiag.count=0; sum=sum2=sumAngle=0; minA=999; maxA=-999;
      delay(4); continue;
    }
    calDiag.stage="CAL_SAVED_CHECK";
    sum+=s.gy; sum2+=s.gy*s.gy; sumAngle+=a;
    minA=fminf(minA,a); maxA=fmaxf(maxA,a); calDiag.count=++n;
    delay(4);
  }
  if(n!=SAVED_CHECK_SAMPLES) {
    calDiag.stage=n ? "CAL_SAVED_CHECK_TIMEOUT" : calDiag.lastReject; return false;
  }
  float mean=sum/n;
  calDiag.variance=fmaxf(0,sum2/n-mean*mean); calDiag.span=maxA-minA;
  if(calDiag.variance>0.25f) { calDiag.stage="CAL_GYRO_VARIANCE"; return false; }
  if(calDiag.span>1.5f) { calDiag.stage="CAL_TILT_VARIATION"; return false; }
  if(fabsf(mean-gyroBias)>SAVED_BIAS_RESIDUAL_DPS) { calDiag.stage="CAL_SAVED_BIAS_DRIFT"; return false; }
  angle=sumAngle/n; rate=mean-gyroBias;
  calDiag.stage="CAL_SAVED_READY"; calStoreState="REUSED"; return true;
}

bool startupCalibration() {
  if(loadCalibration(mpuDiag.chipID,mpuAddress,gyroBias)) {
    Serial.println("Saved gyro calibration: hold still upright for brief check (~0.6s)");
    if(checkSavedCalibration()) return true;
    // Only stable bias drift triggers a full new calibration automatically.
    // Bad frames, movement, bus and STOP failures retain the saved record.
    if(strcmp(calDiag.stage,"CAL_SAVED_BIAS_DRIFT")) return false;
  }
  Serial.println("Hold still upright: full gyro calibration (~3s; up to 15s to settle)");
  if(!calibrate()) return false;
  saveCalibration(mpuDiag.chipID,mpuAddress,gyroBias);
  // Storage failure does not invalidate a good live calibration; diagnostics
  // clearly show SAVE_FAILED and next boot will need full calibration again.
  return true;
}

const char *modeName() {
  switch (mode) {
    case Mode::DISARMED: return "DISARMED";
    case Mode::ARMING: return "ARMING";
    case Mode::BALANCING: return "BALANCING";
    case Mode::MOTOR_TEST: return "MOTOR_TEST";
    default: return "FAULT";
  }
}

telerc::ArmBlock physicalArmBlock() {
  using B=telerc::ArmBlock;
  if(stopped()) return B::STOP_SWITCH;
  if(mode==Mode::FAULT) return B::FAULT;
  if(!calibrated) return B::IMU_NOT_READY;
  if(!pwmReady) return B::PWM_NOT_READY;
  if(mode==Mode::MOTOR_TEST) return B::MOTOR_TEST;
  if(!directionsVerified) return B::VERIFY_DIRECTIONS;
  if(uint32_t(micros()-sampleUs)>NO_SAMPLE_US) return B::SENSOR_STALE;
  if(mode==Mode::BALANCING) return B::NONE;
  if(fabsf(angle-TRIM_DEG)>ARM_DEG) return B::HOLD_UPRIGHT;
  if(fabsf(rate)>ARM_RATE_DPS) return B::HOLD_STILL;
  return B::NONE;
}

telerc::ArmBlock remoteArmBlock() {
  using B=telerc::ArmBlock;
  B physical=physicalArmBlock(); if(physical!=B::NONE) return physical;
  if(!linkInput.ready) return B::WIFI_NOT_READY;
  if(!linkInput.linked) return B::NO_PEER;
  if(!linkInput.fresh) return linkInput.rcCount ? B::RC_STALE : B::NO_RC;
  if(uint32_t(millis()-linkInput.lastRC)>=CONTROL_TIMEOUT_MS) return B::RC_STALE;
  if(!telerc::centered(linkInput.steer,linkInput.drive)) return B::CENTER_STICKS;
  return B::NONE;
}

void serialCommands() {
  // Bounded input work; ignore line endings/leading whitespace, accept case
  // variants, execute at most one action. x anywhere in the batch wins.
  if (!commandsEnabled) {
    if (!Serial.available()) commandsEnabled = true;
    else {
      for (int i=0;i<32 && Serial.available();++i) {
        if(readSerialByte()>=0) ++serialBootDiscard;
      }
      serialOutcome("BOOT_INPUT_DISCARDED_RESEND_AFTER_READY");
    }
    return;
  }
  char command=0;
  bool stopRequested=false;
  for(int i=0;i<32 && Serial.available();++i) {
    int raw=readSerialByte();
    if(raw<0 || serialWhitespace(raw)) continue;
    if(raw>='A' && raw<='Z') raw+='a'-'A';
    if(!command) command=(char)raw;
    stopRequested |= raw=='x';
  }
  if(!command) return;
  serialLastCommand=(uint8_t)(stopRequested ? 'x' : command);
  if(command=='?') {
    serialOutcome("RX_OK_NO_MOTOR_ACTION");
    if(!stopRequested) return;
  }
  if (stopRequested) {
    serialOutcome("STOP_RECEIVED");
    if (mode != Mode::FAULT) disarm("serial stop");
    else motorsOff();
    return;
  }
  if(command!='a' && command!='t' && command!='v' && command!='c') {
    serialOutcome("UNKNOWN_SEND_?_T_V_A_X_C"); return;
  }
  if(command=='c') {
    bool calibrationFault=mode==Mode::FAULT && !calibrated && mpuDiag.stage==MpuStage::READY;
    if((mode!=Mode::DISARMED && !calibrationFault) || stopped() || linkInput.linked) {
      serialOutcome("RECAL_REJECTED_STATE_STOP_OR_PEER");
      if(mode!=Mode::FAULT) reason="recalibrate: disarm and disconnect TeleRC first";
      return;
    }
    motorsOff();
    if(!eraseCalibration()) { serialOutcome("ERASE_FAILED"); if(mode!=Mode::FAULT) reason="calibration erase failed; no restart"; return; }
    // Explicit reset avoids blocking the real-time task and clears queued arms.
    serialOutcome("RECAL_ERASED_RESTART");
    commandsEnabled=false; ESP.restart(); return;
  }
  if (mode == Mode::FAULT || stopped() || !calibrated) { serialOutcome("REJECTED_FAULT_STOP_OR_IMU"); return; }
  if (mode != Mode::DISARMED) { serialOutcome("REJECTED_NOT_DISARMED"); return; }
  if((command=='t' || command=='v' || command=='a') && linkInput.linked) {
    serialOutcome("REJECTED_DISCONNECT_TELERC");
    reason="disconnect TeleRC before local bench commands"; return;
  }
  if(command=='v' && mode==Mode::DISARMED) {
    if(!wheelTestComplete) { serialOutcome("VERIFY_NEEDS_COMPLETED_TEST"); reason="verify: suspend wheels, t, inspect directions, then v"; return; }
    if(uint32_t(micros()-sampleUs)>NO_SAMPLE_US ||
       fabsf(angle-TRIM_DEG)>ARM_DEG || fabsf(rate)>ARM_RATE_DPS) {
      serialOutcome("VERIFY_NEEDS_UPRIGHT_STILL_FRESH");
      reason="verify: hold upright/still with fresh IMU"; return;
    }
    serialOutcome("DIRECTIONS_CONFIRMED");
    directionsVerified=true; reason="directions confirmed for this boot; connect TeleRC";
    return;
  }
  if (command == 'a' && mode == Mode::DISARMED && !linkInput.linked) {
    if (!directionsVerified) { serialOutcome("ARM_NEEDS_DIRECTION_CHECK"); reason = "suspend wheels: t, check directions/axes, then v"; return; }
    if ((uint32_t)(micros() - sampleUs) > NO_SAMPLE_US ||
        fabsf(angle - TRIM_DEG) > ARM_DEG || fabsf(rate) > ARM_RATE_DPS) {
      serialOutcome("ARM_NEEDS_UPRIGHT_STILL_FRESH");
      reason = "hold upright before arm"; return;
    }
    serialOutcome("ARM_REQUESTED");
    mode = Mode::ARMING; reason = "hold upright 1s"; uprightTiming = false;
  }
  if (command == 't' && mode == Mode::DISARMED && !linkInput.linked) {
    // Suspended-wheel direction check; never run this on the floor.
    wheelTestComplete=false; testMotorMask=3; remoteTest=false;
    serialOutcome("TEST_STARTED");
    testOutputValid=false;
    mode = Mode::MOTOR_TEST; reason = "bounded forward motor test";
    testUntil = millis() + TEST_MS; drive(TEST_PWM); captureTestOutput();
  }
}

void updateControl(const Sample &s, float dt) {
  rate = s.gy - gyroBias;
  float predicted = angle + rate * dt;
  float norm = sqrtf(s.ax*s.ax + s.ay*s.ay + s.az*s.az);
  if (norm < 0.25f || norm > 1.8f) { fault("impact/freefall"); return; }
  // Limit accelerometer correction during wheel acceleration and vibration.
  if (norm > 0.85f && norm < 1.15f) {
    float alpha = FILTER_TAU / (FILTER_TAU + dt);
    angle = alpha * predicted + (1-alpha) * accelAngle(s);
  } else angle = predicted;
  if (!isfinite(angle) || !isfinite(rate)) { fault("invalid attitude"); return; }
  if (mode == Mode::MOTOR_TEST) return;
  if (mode == Mode::ARMING) {
    // Use BOTH filtered and raw gravity angles to reject a tilted startup.
    bool upright = fabsf(angle - TRIM_DEG) < ARM_DEG &&
                   fabsf(accelAngle(s) - TRIM_DEG) < ARM_DEG &&
                   fabsf(s.ay) < 0.15f && s.az > 0.94f &&
                   fabsf(rate) < ARM_RATE_DPS && norm > 0.90f && norm < 1.1f;
    if (!upright) {
      disarm("arm cancelled: movement/tilt");
      completedBlock=fabsf(rate)>ARM_RATE_DPS ? telerc::ArmBlock::HOLD_STILL : telerc::ArmBlock::HOLD_UPRIGHT;
      return;
    }
    if (!uprightTiming) { uprightTiming = true; uprightSince = millis(); }
    if ((uint32_t)(millis() - uprightSince) < UPRIGHT_MS) return;
    integral = 0; mode = Mode::BALANCING; reason = "active";
    finishRemoteArm(0);
  }
  if (mode != Mode::BALANCING) return;
  if (fabsf(angle) > FALL_DEG || fabsf(s.ay) > 0.7f || s.az < 0.3f) {
    fault("fall/sideways"); return;
  }
  // Positive tilt -> wheels move FORWARD underneath the falling chassis.
  bool fresh=linkInput.ready && linkInput.linked && linkInput.fresh &&
             uint32_t(millis()-linkInput.lastRC)<CONTROL_TIMEOUT_MS;
  if(fresh && linkInput.motion) {
    float desiredLean=telerc::axis(linkInput.drive)*MAX_LEAN_DEG;
    float desiredYaw=telerc::axis(linkInput.steer)*MAX_YAW_PWM;
    requestedLean += limitf(desiredLean-requestedLean,-LEAN_SLEW_DPS*dt,LEAN_SLEW_DPS*dt);
    requestedYaw += limitf(desiredYaw-requestedYaw,-YAW_SLEW_PWM_PS*dt,YAW_SLEW_PWM_PS*dt);
  } else clearMotionRequest(); // Loss/release cancels travel immediately; local balance continues.
  float error = angle - (TRIM_DEG+requestedLean);
  float pd = KP * error + KD * rate;
  float candidate = limitf(integral + KI*error*dt, -INTEGRAL_LIMIT, INTEGRAL_LIMIT);
  float candidateOutput = pd + candidate;
  if (fabsf(candidateOutput) <= MAX_PWM || candidateOutput*error < 0) integral = candidate;
  float raw = pd + integral;
  if (!isfinite(raw)) { fault("invalid controller"); return; }
  if (fabsf(raw) >= MAX_PWM) {
    if (!saturationTiming) { saturationSince = millis(); saturationTiming = true; }
    if ((uint32_t)(millis() - saturationSince) >= SATURATION_MS) {
      fault("sustained output limit"); return;
    }
  } else saturationTiming = false;
  int output = (int)lroundf(limitf(raw, -MAX_PWM, MAX_PWM));
  if (output && abs(output) < MIN_PWM) output = output > 0 ? MIN_PWM : -MIN_PWM;
  driveMixed(output,requestedYaw);
}

void handleSetupRequests(bool stopChanged) {
  bool setupStop=seenSetupStop!=linkInput.setupStop;
  seenSetupStop=linkInput.setupStop;
  if(setupStop) {
    remoteTest=false;wheelTestComplete=false;setupResult="STOPPED";
    if(mode!=Mode::FAULT)disarm("Balance pre-test stop");
  }
  if(recalibratePending) {
    motorsOff();
    if(uint32_t(millis()-recalibrateAt)>=250){ESP.restart();return;}
  }
  if(seenSetup==linkInput.setupToken)return;
  seenSetup=linkInput.setupToken;setupRequest=linkInput.setupRequest;
  if(stopChanged||setupStop||recalibratePending){setupResult="CANCELLED_BY_STOP_OR_RESTART";return;}
  if(!linkInput.linked||uint32_t(millis()-linkInput.setupAt)>500){setupResult="STALE_REQUEST";return;}
  auto action=linkInput.setupAction;
  bool calibrationFault=mode==Mode::FAULT && !calibrated && mpuDiag.stage==MpuStage::READY;
  if(stopped() || (mode!=Mode::DISARMED && !(action==balance_admin::CALIBRATE && calibrationFault))) {
    setupResult="REJECTED_STATE_OR_STOP";return;
  }
  if(action==balance_admin::CALIBRATE) {
    motorsOff();
    testOutputValid=false;
    if(!eraseCalibration()){setupResult="ERASE_FAILED";return;}
    calibrated=false;directionsVerified=DIRECTIONS_VERIFIED;wheelTestComplete=false;
    recalibratePending=true;recalibrateAt=millis();setupResult="RESTARTING_FOR_CALIBRATION";return;
  }
  if(!calibrated||!pwmReady||uint32_t(micros()-sampleUs)>NO_SAMPLE_US){setupResult="IMU_OR_PWM_NOT_READY";return;}
  if(action==balance_admin::VERIFY) {
    if(!wheelTestComplete){setupResult="VERIFY_NEEDS_BOTH_MOTOR_TEST";return;}
    if(fabsf(angle-TRIM_DEG)>ARM_DEG||fabsf(rate)>ARM_RATE_DPS){setupResult="HOLD_UPRIGHT_AND_STILL";return;}
    directionsVerified=true;setupResult="DIRECTIONS_CONFIRMED";reason="directions confirmed in TeleRC";return;
  }
  if(action<balance_admin::LEFT||action>balance_admin::BOTH){setupResult="UNSUPPORTED_ACTION";return;}
  wheelTestComplete=false;
  testOutputValid=false;
  testMotorMask=action==balance_admin::LEFT?1:action==balance_admin::RIGHT?2:3;
  remoteTest=true;mode=Mode::MOTOR_TEST;testUntil=millis()+TEST_MS;
  setupResult="TEST_RUNNING";reason="TeleRC bounded motor test";drive(TEST_PWM);captureTestOutput();
}

void handleNetworkRequests() {
  linkInput=networkInput();
  bool fresh=linkInput.ready && linkInput.linked && linkInput.fresh &&
             uint32_t(millis()-linkInput.lastRC)<CONTROL_TIMEOUT_MS;
  bool neutral=telerc::centered(linkInput.steer,linkInput.drive);
  bool stopChanged=seenDisarm!=linkInput.disarmToken;
  if(stopChanged) {
    seenDisarm=linkInput.disarmToken;
    if(mode!=Mode::FAULT) disarm("TeleRC disarm");
    // A stop and arm delivered together cannot re-arm on this controller pass.
    seenArm=linkInput.armToken;
  }
  handleSetupRequests(stopChanged);
  if(mode==Mode::ARMING && remoteArming &&
      (!fresh || !neutral || !linkInput.armPending)) {
    auto block=!fresh ? telerc::ArmBlock::RC_STALE :
      !neutral ? telerc::ArmBlock::CENTER_STICKS : telerc::ArmBlock::CANCELLED;
    disarm("arm cancelled: link/control"); completedBlock=block;
  }
  if(!stopChanged && seenArm!=linkInput.armToken) {
    seenArm=linkInput.armToken;
    auto block=remoteArmBlock();
    if(block==telerc::ArmBlock::NONE && recalibratePending)block=telerc::ArmBlock::IMU_NOT_READY;
    if(block==telerc::ArmBlock::NONE && uint32_t(millis()-linkInput.armAt)>500)
      block=telerc::ArmBlock::ARM_EXPIRED;
    if(block==telerc::ArmBlock::NONE && (!linkInput.armPending || mode!=Mode::DISARMED))
      block=telerc::ArmBlock::CANCELLED;
    if(block==telerc::ArmBlock::NONE) {
      mode=Mode::ARMING; remoteArming=true; uprightTiming=false;
      reason="TeleRC arm: hold centered/upright";
    } else {
      completedArm=seenArm; armResult=2; completedBlock=block;
      if(mode!=Mode::FAULT) reason=telerc::armName(block);
    }
  }
}

void publishStatus() {
  telerc::Status s;
  s.pwmReady=pwmReady;s.testOutputValid=testOutputValid;
  s.testLeft=testLeft;s.testRight=testRight;s.testMask=testOutputValid?testMotorMask:0;
  for(uint8_t i=0;i<4;++i)s.testDuty[i]=testDuty[i];
  s.setupToken=seenSetup;s.setupRequest=setupRequest;s.mode=(uint8_t)mode;
  s.calibrated=calibrated;s.verified=directionsVerified;s.testDone=wheelTestComplete;s.gyroBias=gyroBias;
  snprintf(s.setupResult,sizeof(s.setupResult),"%s",setupResult);
  snprintf(s.calStage,sizeof(s.calStage),"%s",calDiag.stage);
  snprintf(s.store,sizeof(s.store),"%s",calStoreState);
  snprintf(s.reason,sizeof(s.reason),"%s",reason);
  s.physicalBlock=physicalArmBlock(); s.ready=s.physicalBlock==telerc::ArmBlock::NONE;
  s.armed=mode==Mode::BALANCING; s.arming=mode==Mode::ARMING;
  s.fault=mode==Mode::FAULT; s.angle=angle; s.rate=rate; s.at=millis();
  s.completedArm=completedArm; s.armResult=s.fault ? 2 : armResult;
  s.completedBlock=s.fault ? telerc::ArmBlock::FAULT : completedBlock;
  networkStatus(s);
}

#ifndef TELERC_HOST_TEST
void controlStep();
void balanceTask(void *) {
  TickType_t wake=xTaskGetTickCount();
  for(;;) { controlStep(); vTaskDelayUntil(&wake,pdMS_TO_TICKS(4)); }
}
#endif

void setup() {
  commandsEnabled = false;
  seenSetup=setupRequest=seenSetupStop=0;recalibratePending=remoteTest=false;setupResult="IDLE";testMotorMask=3;
  testOutputValid=false;testLeft=testRight=0;for(auto &v:testDuty)v=0;
  serialBytes=serialBootDiscard=0; serialLastByte=serialLastCommand=0;
  serialResult="IDLE"; serialReportRequested=false;
  calibrated = false; directionsVerified=DIRECTIONS_VERIFIED; wheelTestComplete=false;
  const uint8_t pins[] = {LEFT_IN1, LEFT_IN2, RIGHT_IN1, RIGHT_IN2};
  for (uint8_t pin : pins) { pinMode(pin, OUTPUT); digitalWrite(pin, LOW); }
  pinMode(STOP_PIN, INPUT_PULLUP);
  Serial.begin(115200); // Do not wait for a host connection.
  balanceTaskStarted=false;
  // AP visibility is independent of motor/IMU readiness. Never allow a failed
  // calibration or sensor check to prevent connection and fault diagnostics.
  if(!networkBegin()) { fault("network task creation"); return; }
  // Attach sequentially. If any attach fails, detach all and force GPIO LOW.
  bool attached = true;
  uint8_t channel=0;
  for (uint8_t pin : pins) attached = ledcAttachChannel(pin, PWM_HZ, 8,channel++) && attached;
  if (!attached) {
    for (uint8_t pin : pins) { ledcDetach(pin); pinMode(pin, OUTPUT); digitalWrite(pin, LOW); }
    fault("PWM attach"); return;
  }
  pwmReady = true; motorsOff();
  if (!Wire.begin(MPU_SDA, MPU_SCL, MPU_I2C_HZ)) { fault("I2C begin"); return; }
  Wire.setTimeOut(2); // Short bus timeout; no unbounded sensor retries.
  if (!initMPU()) { fault(mpuStageName(mpuDiag.stage)); return; }
  calibrated = startupCalibration();
  if (!calibrated) { fault(calDiag.stage); return; }
  tickUs = sampleUs = micros();
  disarm("ready: connect TeleRC / local a,x,t,c when unpaired");
  tickUs=sampleUs=micros();
  publishStatus();
#ifndef TELERC_HOST_TEST
  TaskHandle_t balanceHandle=nullptr;
  if(xTaskCreatePinnedToCore(balanceTask,"balance",6144,nullptr,4,&balanceHandle,1)!=pdPASS) {
    fault("balance task creation");
  } else balanceTaskStarted=true;
#endif
}

void controlStep() {
  handleNetworkRequests();
  if (mode != Mode::FAULT && stopped()) fault("stop switch");
  if(!recalibratePending)serialCommands();
  if (mode == Mode::MOTOR_TEST && (int32_t)(millis() - testUntil) >= 0) {
    bool wasRemote=remoteTest;
    disarm("wheel test finished; inspect both wheel directions, then v");
    wheelTestComplete=testMotorMask==3;
    if(wasRemote)setupResult="TEST_FINISHED";
    remoteTest=false;
    serialOutcome("TEST_FINISHED_CHECK_WHEELS_BEFORE_V");
  }
  uint32_t now = micros();
  if (!recalibratePending && mode != Mode::FAULT && (uint32_t)(now - tickUs) >= PERIOD_US-500) {
    uint32_t elapsed = now - tickUs; tickUs = now;
    if (elapsed > 2*PERIOD_US) { fault("loop timing overrun"); }
    else {
      Sample s;
      uint32_t readStart = micros();
      int result = readSample(s);
      uint32_t end = micros();
      if (result < 0) fault(sampleDiag.stage);
      else if ((uint32_t)(end - readStart) > 3000) fault("slow sensor bus");
      else if (result > 0) {
        float dt = (end - sampleUs) * 0.000001f;
        sampleUs = end;
        if (dt < 0.001f || dt > 0.008f) fault("sample timing");
        else updateControl(s, dt);
      } else if ((uint32_t)(end - sampleUs) >= NO_SAMPLE_US) fault("no fresh MPU sample");
    }
  }
  // Emit bounded pieces only when buffer space is available. Never wait for USB.
  static char telemetry[320];
  static size_t pending = 0, sent = 0;
  static uint32_t mpuReportMs=0;
  static uint8_t sensorReport=0;
  if (pending == sent && (serialReportRequested || (uint32_t)(millis() - reportMs) >= 100)) {
    reportMs = millis(); sent = 0;
    int n;
    bool periodicSensor=uint32_t(millis()-mpuReportMs)>=1000;
    if(serialReportRequested || (periodicSensor && sensorReport==3)) {
      if(periodicSensor && !serialReportRequested) { mpuReportMs=millis(); sensorReport=0; }
      serialReportRequested=false;
      n=snprintf(telemetry,sizeof(telemetry),
        "SERIAL bytes=%lu bootDiscard=%lu lastByte=0x%02X command=0x%02X result=%s enabled=%u testDone=%u testPWM=%d testMs=%lu pwmReady=%u outputValid=%u L=%d R=%d duty=%u,%u/%u,%u\n",
        (unsigned long)serialBytes,(unsigned long)serialBootDiscard,
        serialLastByte,serialLastCommand,serialResult,(unsigned)commandsEnabled,
        (unsigned)wheelTestComplete,TEST_PWM,(unsigned long)TEST_MS,(unsigned)pwmReady,
        (unsigned)testOutputValid,testLeft,testRight,testDuty[0],testDuty[1],testDuty[2],testDuty[3]);
    } else if(periodicSensor) {
      mpuReportMs=millis();
      ++sensorReport;
      if(sensorReport==1) n=snprintf(telemetry,sizeof(telemetry),
        "CAL stage=%s store=%s lastReject=%s mount=%u n=%u valid=%u ax=%.3f ay=%.3f az=%.3f gx=%.2f gy=%.2f gz=%.2f norm=%.3f tilt=%.2f variance=%.4f span=%.2f\n",
        calDiag.stage,calStoreState,calDiag.lastReject,(unsigned)BALANCER_IMU_MOUNT,calDiag.count,(unsigned)calDiag.sampleValid,
        calDiag.sample.ax,calDiag.sample.ay,calDiag.sample.az,
        calDiag.sample.gx,calDiag.sample.gy,calDiag.sample.gz,
        calDiag.magnitude,calDiag.tilt,calDiag.variance,calDiag.span);
      else if(sensorReport==2) n=snprintf(telemetry,sizeof(telemetry),
        "SAMPLE stage=%s rawValid=%u clippedAxis=%d AX=%d AY=%d AZ=%d GX=%d GY=%d GZ=%d\n",
        sampleDiag.stage,(unsigned)sampleDiag.rawValid,sampleDiag.clippedAxis,
        sampleDiag.raw[0],sampleDiag.raw[1],sampleDiag.raw[2],
        sampleDiag.raw[3],sampleDiag.raw[4],sampleDiag.raw[5]);
      else n=snprintf(telemetry,sizeof(telemetry),
        "MPU stage=%s profile=%s id=0x%02X selected=0x%02X SDA=%u SCL=%u hz=%lu ack68=%u read68=%u who68=0x%02X err68=%u ack69=%u read69=%u who69=0x%02X err69=%u reg=0x%02X expected=0x%02X actual=0x%02X bus=%u wanted=%u got=%u\n",
        mpuStageName(mpuDiag.stage),mpuProfileName(),mpuDiag.chipID,mpuAddress,MPU_SDA,MPU_SCL,(unsigned long)MPU_I2C_HZ,
        (unsigned)mpuDiag.probe[0].ack,(unsigned)mpuDiag.probe[0].read,mpuDiag.probe[0].who,mpuDiag.probe[0].error,
        (unsigned)mpuDiag.probe[1].ack,(unsigned)mpuDiag.probe[1].read,mpuDiag.probe[1].who,mpuDiag.probe[1].error,
        mpuDiag.reg,mpuDiag.expected,mpuDiag.actual,mpuDiag.error,mpuDiag.wanted,mpuDiag.got);
    } else n = snprintf(telemetry, sizeof(telemetry),
                     "v0.3.1 %s wifi=%s prearm=%s last=%s verified=%u CH1=%u CH2=%u age=%lu rx=%lu reject=%lu angle=%.2f rate=%.2f pwm=%d | %s\n",
                     modeName(), telerc::wifiName(linkInput.wifi), telerc::armName(remoteArmBlock()),
                     telerc::armName(linkInput.lastArmBlock), (unsigned)directionsVerified,
                     linkInput.observedSteer,linkInput.observedDrive,
                     (unsigned long)(linkInput.rcCount ? uint32_t(millis()-linkInput.lastRC) : 999999),
                     (unsigned long)linkInput.rcCount,(unsigned long)linkInput.rejectCount,
                     angle,rate,motorCommand,reason);
    pending = n > 0 ? (size_t)n : 0;
    if (pending >= sizeof(telemetry)) pending = sizeof(telemetry)-1;
  }
  int available = Serial.availableForWrite();
  if (available > 0 && pending > sent) {
    size_t chunk = pending - sent;
    if (chunk > (size_t)available) chunk = (size_t)available;
    if (chunk > 32) chunk = 32;
    sent += Serial.write((const uint8_t *)telemetry + sent, chunk);
  }
  publishStatus();
}

void loop() {
#ifdef TELERC_HOST_TEST
  controlStep();
#else
  if(balanceTaskStarted) delay(1000); // Dedicated task owns hardware/Serial.
  else { controlStep(); delay(1); } // Early boot faults still publish diagnostics.
#endif
}

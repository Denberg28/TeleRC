#pragma once
#include <Arduino.h>
#include <Wire.h>
#include "Config.h"

// Called by the hardware-owning task only. Boot discovery never drives motors.
enum class MpuStage : uint8_t {
  NOT_STARTED, READY, NO_ACK, WHO_READ_FAILED, UNSUPPORTED_ID, ADDRESS_MISMATCH,
  RESET_FAILED, IDENTITY_CHANGED, CONFIG_WRITE_FAILED, CONFIG_READ_FAILED, CONFIG_MISMATCH, READ_FAILED
};
inline const char *mpuStageName(MpuStage stage) {
  switch(stage) {
    case MpuStage::READY: return "MPU_READY";
    case MpuStage::NO_ACK: return "MPU_NO_I2C_ACK";
    case MpuStage::WHO_READ_FAILED: return "MPU_WHO_READ_FAILED";
    case MpuStage::UNSUPPORTED_ID: return "MPU_UNSUPPORTED_CHIP_ID";
    case MpuStage::ADDRESS_MISMATCH: return "MPU_ADDRESS_CONFIG_MISMATCH";
    case MpuStage::RESET_FAILED: return "MPU_RESET_WRITE_FAILED";
    case MpuStage::IDENTITY_CHANGED: return "MPU_IDENTITY_CHANGED_AFTER_RESET";
    case MpuStage::CONFIG_WRITE_FAILED: return "MPU_CONFIG_WRITE_FAILED";
    case MpuStage::CONFIG_READ_FAILED: return "MPU_CONFIG_READ_FAILED";
    case MpuStage::CONFIG_MISMATCH: return "MPU_CONFIG_READBACK_MISMATCH";
    case MpuStage::READ_FAILED: return "MPU_SAMPLE_READ_FAILED";
    default: return "MPU_NOT_STARTED";
  }
}
struct MpuProbe { bool ack=false, read=false; uint8_t who=0, error=0; };
struct MpuDiagnostic {
  MpuStage stage=MpuStage::NOT_STARTED;
  MpuProbe probe[2];
  uint8_t address=MPU_ADDRESS, chipID=0, reg=0, expected=0, actual=0, error=0, wanted=0, got=0;
};
inline MpuDiagnostic mpuDiag;
inline uint8_t mpuAddress=MPU_ADDRESS;
inline const char *mpuProfileName() {
  switch(mpuDiag.chipID) {
    case 0x68: return "MPU6050_COMPAT";
    case 0x70: return "MPU6500_COMPAT";
    default: return "UNKNOWN";
  }
}

inline bool readRegs(uint8_t reg,uint8_t *data,uint8_t count) {
  mpuDiag.address=mpuAddress; mpuDiag.reg=reg; mpuDiag.wanted=count; mpuDiag.got=0;
  Wire.beginTransmission(mpuAddress); Wire.write(reg);
  mpuDiag.error=Wire.endTransmission(false);
  if(mpuDiag.error) { mpuDiag.stage=MpuStage::READ_FAILED; return false; }
  mpuDiag.got=Wire.requestFrom((uint16_t)mpuAddress,count,true);
  if(mpuDiag.got!=count) { mpuDiag.stage=MpuStage::READ_FAILED; return false; }
  for(uint8_t i=0;i<count;++i)data[i]=(uint8_t)Wire.read();
  return true;
}
inline bool writeReg(uint8_t reg,uint8_t value) {
  mpuDiag.address=mpuAddress; mpuDiag.reg=reg; mpuDiag.expected=value;
  Wire.beginTransmission(mpuAddress); Wire.write(reg); Wire.write(value);
  mpuDiag.error=Wire.endTransmission(true);
  return mpuDiag.error==0;
}
inline bool checkedReg(uint8_t reg,uint8_t value) {
  mpuDiag.actual=0;
  // Bounded boot-only retries for settling/transient bus failures. Runtime
  // sample reads never retry, change address, or reset the sensor in motion.
  for(uint8_t attempt=0;attempt<3;++attempt) {
    if(!writeReg(reg,value)) mpuDiag.stage=MpuStage::CONFIG_WRITE_FAILED;
    else {
      if(reg==0x6B) delay(30); // Allow sleep exit / clock source settling.
      uint8_t actual=0;
      if(!readRegs(reg,&actual,1)) mpuDiag.stage=MpuStage::CONFIG_READ_FAILED;
      else {
        mpuDiag.actual=actual; mpuDiag.expected=value;
        if(actual==value) return true;
        mpuDiag.stage=MpuStage::CONFIG_MISMATCH;
      }
    }
    if(attempt<2) delay(2);
  }
  return false;
}
inline bool initMPU() {
  mpuDiag=MpuDiagnostic{}; mpuAddress=MPU_ADDRESS;
  delay(100); // Board/sensor power settling, with motors disabled and AP active.
  for(uint8_t i=0;i<2;++i) {
    mpuAddress=uint8_t(0x68+i);
    auto &probe=mpuDiag.probe[i];
    Wire.beginTransmission(mpuAddress);
    probe.error=Wire.endTransmission(true); probe.ack=probe.error==0;
    if(probe.ack) {
      probe.read=readRegs(0x75,&probe.who,1);
      probe.error=mpuDiag.error;
    }
  }
  // Explicit supported profiles only; an ACK alone never establishes identity.
  auto supported=[](const MpuProbe &p) {
    return p.ack && p.read && (p.who==0x68 || p.who==0x70);
  };
  mpuAddress=MPU_ADDRESS; mpuDiag.address=MPU_ADDRESS;
  uint8_t configured=MPU_ADDRESS-0x68, chosen=configured;
  if(!supported(mpuDiag.probe[chosen])) {
    uint8_t other=configured^1;
    if(supported(mpuDiag.probe[other])) {
      if(!MPU_AUTO_ADDRESS) { mpuDiag.stage=MpuStage::ADDRESS_MISMATCH; return false; }
      chosen=other;
    } else {
      bool ack=mpuDiag.probe[0].ack || mpuDiag.probe[1].ack;
      bool read=mpuDiag.probe[0].read || mpuDiag.probe[1].read;
      mpuDiag.stage=!ack ? MpuStage::NO_ACK : !read ? MpuStage::WHO_READ_FAILED : MpuStage::UNSUPPORTED_ID;
      return false;
    }
  }
  mpuAddress=uint8_t(0x68+chosen); mpuDiag.address=mpuAddress;
  mpuDiag.chipID=mpuDiag.probe[chosen].who;
  if(!writeReg(0x6B,0x80)) { mpuDiag.stage=MpuStage::RESET_FAILED; return false; }
  delay(100);
  uint8_t afterReset=0;
  mpuDiag.expected=mpuDiag.chipID;
  if(!readRegs(0x75,&afterReset,1)) {
    mpuDiag.stage=MpuStage::WHO_READ_FAILED; return false;
  }
  mpuDiag.actual=afterReset;
  if(afterReset!=mpuDiag.chipID) {
    mpuDiag.stage=MpuStage::IDENTITY_CHANGED; return false;
  }
  // Shared scale: +/-2g (16384 LSB/g), +/-250dps (131 LSB/dps).
  // GYRO_CONFIG=0 selects DLPF on MPU6500. CONFIG=3 gives ~41Hz
  // (MPU6500) / ~42Hz gyro (MPU6050), with 1kHz sample output.
  bool configuredOK=checkedReg(0x6B,0x01) && checkedReg(0x6C,0x00) &&
      checkedReg(0x1A,0x03) && checkedReg(0x19,0x00) &&
      checkedReg(0x1B,0x00) && checkedReg(0x1C,0x00);
  if(!configuredOK) return false;
  // MPU6500 has a separate accelerometer filter: 41Hz, 1kHz output.
  // Register 0x1D is not ACCEL_CONFIG2 on MPU6050; never write it there.
  if(mpuDiag.chipID==0x70 && !checkedReg(0x1D,0x03)) return false;
  if(!checkedReg(0x38,0x01)) return false;
  delay(100); mpuDiag.stage=MpuStage::READY; return true;
}

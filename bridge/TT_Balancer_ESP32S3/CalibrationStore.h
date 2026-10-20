#pragma once
#include <Preferences.h>
#include <stdint.h>
#include <stddef.h>
#include <math.h>
#include "Config.h"

// Only the active pitch gyro offset is persisted. Attitude is always measured
// afresh. A versioned, checksummed single NVS blob avoids mixed partial fields.
struct SavedCalibration {
  uint32_t magic=0x54424331, schema=1, context=0;
  float bias=0;
  uint32_t checksum=0;
};
static_assert(sizeof(SavedCalibration)==20, "Unexpected calibration record layout");
inline uint32_t calibrationContext(uint8_t id,uint8_t address) {
  return (uint32_t(id)<<24)|(uint32_t(address)<<16)|
         (uint32_t(MPU_SDA)<<10)|(uint32_t(MPU_SCL)<<4)|BALANCER_IMU_MOUNT;
}
inline uint32_t calibrationChecksum(const SavedCalibration &r) {
  const auto *bytes=reinterpret_cast<const uint8_t *>(&r);
  uint32_t hash=2166136261u;
  for(size_t i=0;i<offsetof(SavedCalibration,checksum);++i) hash=(hash^bytes[i])*16777619u;
  return hash;
}
inline const char *calStoreState="NOT_READ";
inline bool loadCalibration(uint8_t id,uint8_t address,float &bias) {
  Preferences prefs;
  if(!prefs.begin("tt-balance",true)) { calStoreState="MISSING_OR_UNAVAILABLE"; return false; }
  SavedCalibration r;
  bool ok=prefs.getBytesLength("gyro")==sizeof(r) &&
          prefs.getBytes("gyro",&r,sizeof(r))==sizeof(r);
  prefs.end();
  ok=ok && r.magic==0x54424331 && r.schema==1 &&
     r.context==calibrationContext(id,address) && isfinite(r.bias) &&
     fabsf(r.bias)<=8 && r.checksum==calibrationChecksum(r);
  calStoreState=ok ? "LOADED" : "MISSING_OR_INVALID";
  if(ok) bias=r.bias;
  return ok;
}
inline bool saveCalibration(uint8_t id,uint8_t address,float bias) {
  if(!isfinite(bias)||fabsf(bias)>8) { calStoreState="SAVE_INVALID"; return false; }
  SavedCalibration r; r.context=calibrationContext(id,address); r.bias=bias;
  r.checksum=calibrationChecksum(r);
  Preferences prefs;
  if(!prefs.begin("tt-balance",false)) { calStoreState="SAVE_FAILED"; return false; }
  bool ok=prefs.putBytes("gyro",&r,sizeof(r))==sizeof(r);
  prefs.end(); calStoreState=ok ? "SAVED" : "SAVE_FAILED"; return ok;
}
inline bool eraseCalibration() {
  Preferences prefs;
  if(!prefs.begin("tt-balance",false)) { calStoreState="ERASE_FAILED"; return false; }
  bool ok=!prefs.isKey("gyro") || prefs.remove("gyro");
  prefs.end(); calStoreState=ok ? "ERASED" : "ERASE_FAILED"; return ok;
}

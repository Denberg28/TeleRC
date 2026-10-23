#pragma once
#include <math.h>
#include "BalanceTypes.h"

// These snapshots belong to the balance task and freeze on a latched fault.
struct SampleDiagnostic {
  const char *stage="SAMPLE_NOT_READ";
  bool rawValid=false;
  int16_t raw[6]{};
  int8_t clippedAxis=-1; // AX,AY,AZ,GX,GY,GZ; -1 means none.
};
inline SampleDiagnostic sampleDiag;
struct CalibrationDiagnostic {
  const char *stage="CAL_NOT_STARTED";
  const char *lastReject="NONE";
  bool sampleValid=false;
  uint16_t count=0;
  Sample sample{};
  float magnitude=0, tilt=0, variance=0, span=0;
};
inline CalibrationDiagnostic calDiag;

inline Sample robotFrame(const Sample &s, bool yForward) {
  if(!yForward) return s;
  return Sample{s.ay, -s.ax, s.az, s.gy, -s.gx, s.gz};
}

inline const char *calibrationSampleBlock(const Sample &s,float magnitude,float tilt) {
  if(!isfinite(magnitude)||!isfinite(tilt)||!isfinite(s.ax)||!isfinite(s.ay)||
     !isfinite(s.az)||!isfinite(s.gx)||!isfinite(s.gy)||!isfinite(s.gz)) return "CAL_NONFINITE";
  if(magnitude<0.90f || magnitude>1.10f) return "CAL_ACCEL_MAGNITUDE";
  if(s.az<0.94f) return "CAL_Z_NOT_UP";
  if(fabsf(s.ay)>0.15f) return "CAL_SIDE_TILT";
  if(fabsf(tilt)>10) return "CAL_FORWARD_TILT";
  if(fabsf(s.gx)>8 || fabsf(s.gy)>8 || fabsf(s.gz)>8) return "CAL_GYRO_MOVEMENT_OR_BIAS";
  return nullptr;
}

#pragma once
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <math.h>
#include "Config.h"
#include "MiniMavlink.h"
#include "BalanceAdmin.h"

// Network-owned state. No GPIO, heap allocation, Arduino or cross-thread access.
namespace telerc {
enum class Kind : uint8_t { MAV, DISCOVER, DISCONNECT };
enum class ArmBlock : uint8_t {
  NONE, FAULT, NOT_READY, IMU_NOT_READY, PWM_NOT_READY, VERIFY_DIRECTIONS,
  STOP_SWITCH, SENSOR_STALE, HOLD_UPRIGHT, HOLD_STILL, MOTOR_TEST,
  WIFI_NOT_READY, NO_PEER, NO_RC, RC_STALE, CENTER_STICKS, COOLDOWN,
  ARM_EXPIRED, CANCELLED, INVALID_REQUEST, CONTROLLER_STALE
};
inline const char *armName(ArmBlock b) {
  switch(b) {
    case ArmBlock::NONE: return "READY";
    case ArmBlock::FAULT: return "FAULT_RESET_REQUIRED";
    case ArmBlock::IMU_NOT_READY: return "IMU_NOT_READY";
    case ArmBlock::PWM_NOT_READY: return "PWM_NOT_READY";
    case ArmBlock::VERIFY_DIRECTIONS: return "VERIFY_DIRECTIONS_T_THEN_V";
    case ArmBlock::STOP_SWITCH: return "RELEASE_STOP_SWITCH";
    case ArmBlock::SENSOR_STALE: return "SENSOR_STALE";
    case ArmBlock::HOLD_UPRIGHT: return "HOLD_UPRIGHT_WITHIN_4_DEG";
    case ArmBlock::HOLD_STILL: return "HOLD_STILL";
    case ArmBlock::MOTOR_TEST: return "MOTOR_TEST_RUNNING";
    case ArmBlock::WIFI_NOT_READY: return "WIFI_NOT_READY";
    case ArmBlock::NO_PEER: return "CONNECT_TELERC";
    case ArmBlock::NO_RC: return "NO_COMPLETE_RC_RECEIVED";
    case ArmBlock::RC_STALE: return "RC_EXPIRED_ENABLE_CONTROL";
    case ArmBlock::CENTER_STICKS: return "CENTER_CH1_CH2";
    case ArmBlock::COOLDOWN: return "WAIT_THEN_TAP_ARM_AGAIN";
    case ArmBlock::ARM_EXPIRED: return "ARM_REQUEST_EXPIRED";
    case ArmBlock::CANCELLED: return "ARM_CANCELLED";
    case ArmBlock::INVALID_REQUEST: return "INVALID_FORCE_ARM_REJECTED";
    case ArmBlock::CONTROLLER_STALE: return "CONTROLLER_STATUS_EXPIRED";
    default: return "CONTROLLER_NOT_READY";
  }
}
enum class WifiStage : uint8_t { STARTING, READY, MODE_FAILED, IP_FAILED, AP_FAILED, UDP_FAILED };
inline const char *wifiName(WifiStage s) {
  switch(s) {
    case WifiStage::READY: return "READY";
    case WifiStage::MODE_FAILED: return "MODE_FAILED";
    case WifiStage::IP_FAILED: return "IP_FAILED";
    case WifiStage::AP_FAILED: return "AP_FAILED";
    case WifiStage::UDP_FAILED: return "UDP_FAILED";
    default: return "STARTING";
  }
}
struct Packet {
  Kind kind = Kind::MAV;
  mini_mav::Message message;
};
struct Datagram { Packet packets[4]; uint8_t count=0; };
struct Input {
  uint32_t setupToken=0,setupAt=0,setupRequest=0,setupStop=0;
  balance_admin::Action setupAction=balance_admin::NONE;
  WifiStage wifi=WifiStage::STARTING;
  ArmBlock lastArmBlock=ArmBlock::NONE;
  uint32_t armToken=0, disarmToken=0, lastRC=0, armAt=0;
  uint32_t rcCount=0, rejectCount=0;
  uint16_t steer=1500, drive=1500;
  uint16_t observedSteer=0, observedDrive=0;
  bool linked=false, fresh=false, motion=false, armPending=false, ready=false;
};
struct Status {
  uint32_t setupToken=0,setupRequest=0;
  uint8_t mode=0;
  bool calibrated=false,verified=false,testDone=false;
  float gyroBias=0;
  char setupResult[48]="IDLE",calStage[48]="CAL_NOT_STARTED",store[32]="NOT_READ",reason[64]="boot";
  bool ready=false, armed=false, arming=false, fault=false;
  ArmBlock physicalBlock=ArmBlock::NONE, completedBlock=ArmBlock::NONE;
  float angle=0, rate=0;
  uint32_t at=0;
  uint32_t completedArm=0;
  uint8_t armResult=2;
};
struct Reply {
  bool ack=false; uint16_t command=0; uint8_t result=0;
  ArmBlock block=ArmBlock::NONE;
};

inline bool target(uint8_t sys,uint8_t component) {
  return sys==SYSTEM_ID && (component==0 || component==COMPONENT_ID);
}
inline bool rcValue(uint16_t v) { return v==0 || v==65535 || (v>=1000 && v<=2000); }
inline bool validate(const mini_mav::Message &m) {
  if(m.system!=255 || m.component!=190) return false;
  if(m.id==0) return m.payload[4]==6 && m.payload[5]==8 && m.payload[8]==3;
  if(m.id==70) return target(m.payload[16],m.payload[17]) &&
      rcValue(mini_mav::u16(m.payload)) && rcValue(mini_mav::u16(m.payload+2));
  if(m.id==76) return target(m.payload[30],m.payload[31]);
  return false;
}
// Whole datagram validation before applying any effects. Reject tails/overflow.
inline bool decode(const uint8_t *bytes,size_t n,Datagram &out) {
  out=Datagram{};
  const char hello[]="TELERC_DISCOVER_V1", bye[]="TELERC_DISCONNECT_V1";
  if(n==sizeof(hello)-1 && !memcmp(bytes,hello,n)) {
    out.count=1; out.packets[0].kind=Kind::DISCOVER; return true;
  }
  if(n==sizeof(bye)-1 && !memcmp(bytes,bye,n)) {
    out.count=1; out.packets[0].kind=Kind::DISCONNECT; return true;
  }
  size_t offset=0;
  while(offset<n) {
    if(out.count==4) return false;
    size_t used=0;
    auto &p=out.packets[out.count];
    if(!mini_mav::decode(bytes+offset,n-offset,p.message,used) || !validate(p.message)) return false;
    offset+=used; ++out.count;
  }
  return out.count>0;
}
inline float axis(uint16_t pwm) {
  int d=int(pwm)-1500;
  if(d>=-RC_DEADBAND_US && d<=RC_DEADBAND_US) return 0;
  float value=float(d)/RC_FULL_SCALE_US;
  return fmaxf(-1,fminf(1,value));
}
inline bool centered(uint16_t s,uint16_t d) { return axis(s)==0 && axis(d)==0; }

struct Core {
  Input input;
  uint32_t peer=0, peerAt=0, centerAt=0, lastMovingRC=0;
  uint8_t movingSequence=0;
  bool centerTiming=false, sequenceKnown=false, haveRC=false;
  bool armCooling=false;
  uint32_t armCoolingAt=0;
  uint32_t accepted=0, rejected=0, timeouts=0;

  void inhibit() {
    input.motion=false; input.fresh=false; input.armPending=false;
    input.steer=input.drive=1500; centerTiming=false; haveRC=false;
  }
  void losePeer() { inhibit(); peer=0; input.linked=false; }
  void armFinished(uint32_t now) {
    input.armPending=false; armCooling=true; armCoolingAt=now;
  }
  void watchdog(uint32_t now,bool station) {
    if(haveRC && uint32_t(now-input.lastRC)>=CONTROL_TIMEOUT_MS) {
      inhibit(); ++timeouts;
    }
    if(peer && (!station || uint32_t(now-peerAt)>=PEER_TIMEOUT_MS)) losePeer();
    if(input.armPending && uint32_t(now-input.armAt)>2000) input.armPending=false;
    if(armCooling && uint32_t(now-armCoolingAt)>=600) armCooling=false;
  }
  // A reconnect/discovery never supplies RC, grants arm or clears inhibition.
  bool claim(uint32_t source,uint16_t port,const Datagram &d,uint32_t now) {
    if(port!=UDP_PORT || !source) return false;
    if(peer) return peer==source;
    if(d.count!=1) return false;
    const auto &p=d.packets[0];
    if(p.kind!=Kind::DISCOVER && !(p.kind==Kind::MAV && p.message.id==0)) return false;
    inhibit(); peer=source; peerAt=now; input.linked=true;
    sequenceKnown=false; return true;
  }
  ArmBlock prearm(const Status &s,uint32_t now) const {
    if(s.fault) return s.physicalBlock!=ArmBlock::NONE ? s.physicalBlock : ArmBlock::FAULT;
    if(!s.ready) return s.physicalBlock!=ArmBlock::NONE ? s.physicalBlock : ArmBlock::NOT_READY;
    if(!peer || !input.linked) return ArmBlock::NO_PEER;
    if(!haveRC) return input.rcCount ? ArmBlock::RC_STALE : ArmBlock::NO_RC;
    if(!input.fresh || uint32_t(now-input.lastRC)>=CONTROL_TIMEOUT_MS) return ArmBlock::RC_STALE;
    if(!centered(input.steer,input.drive)) return ArmBlock::CENTER_STICKS;
    if(!s.armed && armCooling) return ArmBlock::COOLDOWN;
    return ArmBlock::NONE;
  }
  Reply apply(const Packet &p,uint32_t now,const Status &status) {
    Reply reply;
    if(p.kind==Kind::DISCOVER || (p.kind==Kind::MAV && p.message.id==0)) {
      peerAt=now; ++accepted; return reply;
    }
    if(p.kind==Kind::DISCONNECT) { losePeer(); ++accepted; return reply; }
    const auto &m=p.message;
    if(m.id==70) {
      uint16_t s=mini_mav::u16(m.payload), d=mini_mav::u16(m.payload+2);
      ++input.rcCount; input.observedSteer=s; input.observedDrive=d;
      if(s==0 || d==0) { inhibit(); peerAt=now; ++accepted; return reply; }
      // Sparse updates may preserve one axis, but cannot establish a new session
      // or refresh its lease: require both explicit axes for this balancer.
      if(s==65535 || d==65535) { ++rejected; return reply; }
      bool neutral=centered(s,d);
      if(!neutral) {
        uint8_t delta=uint8_t(m.sequence-movingSequence);
        // Neutral handover uses fixed 0/1/2 and ARM fixed 200/201/202 in TeleRC.
        // Guard moving frames only; safety-lowering neutral/release always wins.
        if(!input.motion || !sequenceKnown || (delta==0 || delta>=128)) {
          ++rejected; return reply;
        }
        movingSequence=m.sequence; lastMovingRC=now;
      } else {
        if(!centerTiming) { centerAt=now; centerTiming=true; }
        if(uint32_t(now-centerAt)>=CENTER_HOLD_MS) {
          input.motion=true;
          // Establish/reset sequence only during a sustained neutral hold.
          if(!sequenceKnown || uint32_t(now-lastMovingRC)>=CONTROL_TIMEOUT_MS) {
            movingSequence=m.sequence; sequenceKnown=true;
          }
        }
      }
      if(!neutral) centerTiming=false;
      input.steer=s; input.drive=d; input.lastRC=now; input.fresh=true; haveRC=true;
      peerAt=now; ++accepted; return reply;
    }
    if(m.id!=76) { ++rejected; return reply; }
    reply.ack=true; reply.command=mini_mav::u16(m.payload+28);
    reply.result=3; // MAV_RESULT_UNSUPPORTED, including servo/mission commands.
    if(reply.command!=400) return reply;
    float arm=0; memcpy(&arm,m.payload,sizeof(arm));
    bool plain=true;
    for(int i=4;i<28;++i) plain &= m.payload[i]==0;
    if(!plain || !isfinite(arm) || (arm!=0 && arm!=1)) {
      reply.result=2; reply.block=input.lastArmBlock=ArmBlock::INVALID_REQUEST; return reply;
    }
    peerAt=now;
    if(arm==0) {
      ++input.disarmToken; inhibit(); armFinished(now);
      input.lastArmBlock=ArmBlock::NONE;
      reply.result=0; ++accepted; return reply;
    }
    reply.block=prearm(status,now); input.lastArmBlock=reply.block;
    if(reply.block!=ArmBlock::NONE) { reply.result=2; ++rejected; return reply; }
    if(status.armed) { reply.result=0; return reply; }
    if(!input.armPending) { input.armPending=true; input.armAt=now; ++input.armToken; }
    reply.result=5; ++accepted; return reply; // IN_PROGRESS; final result comes from actual controller state.
  }
};
}

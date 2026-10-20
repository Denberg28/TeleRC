#pragma once
#include "LinkCore.h"

#ifdef TELERC_HOST_TEST
// Tests exercise the actual controller using an explicit snapshot mailbox.
inline telerc::Input testInput;
inline telerc::Status testStatus;
inline unsigned testNetworkStarts=0;
inline bool networkBegin() {
  ++testNetworkStarts; testInput=telerc::Input{}; testInput.ready=true;
  testInput.wifi=telerc::WifiStage::READY; return true;
}
inline telerc::Input networkInput() { return testInput; }
inline void networkStatus(const telerc::Status &s) { testStatus=s; }
#else
#include <WiFi.h>
#include <esp_random.h>
#include <WiFiUdp.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#if !defined(CONFIG_IDF_TARGET_ESP32S3) || CONFIG_FREERTOS_UNICORE
#error "This firmware requires a dual-core ESP32-S3 target."
#endif

namespace transport {
// Every WiFi/UDP operation belongs to core 0, never the balance task.
inline portMUX_TYPE mux=portMUX_INITIALIZER_UNLOCKED;
inline telerc::Input input;
inline telerc::Status status;
inline telerc::Core core;
inline WiFiUDP udp;
inline uint8_t txSequence=0;
inline bool initialized=false;
inline telerc::WifiStage wifiStage=telerc::WifiStage::STARTING;
inline uint32_t lastHeartbeat=0, lastAttitude=0, completedSent=0;
inline bool previouslyArmed=false;
inline balance_admin::Lease setupLease;
inline uint32_t setupSequence=0,setupPeer=0,setupBoot=0;
inline telerc::Status getStatus() {
  portENTER_CRITICAL(&mux); auto s=status; portEXIT_CRITICAL(&mux); return s;
}
inline void publishInput() {
  core.input.ready=initialized;
  core.input.wifi=wifiStage;
  core.input.rejectCount=core.rejected;
  portENTER_CRITICAL(&mux); input=core.input; portEXIT_CRITICAL(&mux);
}
inline IPAddress address(uint32_t ip) {
  return IPAddress(uint8_t(ip>>24),uint8_t(ip>>16),uint8_t(ip>>8),uint8_t(ip));
}
inline uint32_t number(const IPAddress &ip) {
  return uint32_t(ip[0])<<24 | uint32_t(ip[1])<<16 | uint32_t(ip[2])<<8 | ip[3];
}
inline bool local(const IPAddress &ip) {
  return ip[0]==192 && ip[1]==168 && ip[2]==4 && ip[3]>1 && ip[3]<255;
}
inline void send(uint8_t id,const uint8_t *p,uint8_t len,uint8_t extra,bool broadcast=false) {
  if(!initialized || (!core.peer && !broadcast)) return;
  uint8_t bytes[64];
  size_t n=mini_mav::encode(bytes,txSequence++,SYSTEM_ID,COMPONENT_ID,id,p,len,extra);
  IPAddress dst=core.peer ? address(core.peer) : IPAddress(192,168,4,255);
  if(udp.beginPacket(dst,UDP_PORT)) { udp.write(bytes,n); udp.endPacket(); }
}
inline void ack(uint16_t command,uint8_t result) {
  uint8_t p[3]; mini_mav::put16(p,command); p[2]=result; send(77,p,3,143);
}
inline void statusText(telerc::ArmBlock block,uint8_t result) {
  static bool sent=false;
  static telerc::ArmBlock previous=telerc::ArmBlock::NONE;
  static uint8_t previousResult=255;
  static uint32_t sentAt=0;
  if(sent && previous==block && previousResult==result && uint32_t(millis()-sentAt)<500) return;
  sent=true; previous=block; previousResult=result; sentAt=millis();
  uint8_t p[51]={}; p[0]=result==2 ? 4 : 6;
  const char *text=result==5 ? "ARMING_HOLD_UPRIGHT_CENTERED" : telerc::armName(block);
  // Standard MAVLink 1 STATUSTEXT: severity byte + 50-byte text, CRC extra 83.
  size_t n=strlen(text); if(n>50) n=50;
  memcpy(p+1,text,n); send(253,p,51,83);
}
inline void heartbeat(const telerc::Status &s) {
  uint8_t p[9]={};
  p[4]=10; p[5]=0; // GROUND_ROVER / GENERIC, never impersonate ArduPilot.
  p[6]=64 | (s.armed ? 128 : 0);
  p[7]=s.fault ? 5 : (s.armed ? 4 : (s.ready ? 3 : 2)); p[8]=3;
  send(0,p,9,50,true); lastHeartbeat=millis();
}
inline void attitude(const telerc::Status &s) {
  uint8_t p[28]={};
  uint32_t ms=millis(); memcpy(p,&ms,4);
  float pitch=s.angle*0.01745329252f, speed=s.rate*0.01745329252f;
  memcpy(p+8,&pitch,4); memcpy(p+20,&speed,4);
  send(30,p,28,39); lastAttitude=ms;
}
inline void setupReply(const telerc::Status &s,uint32_t now,uint32_t request=0,const char *result=nullptr) {
  char bytes[480];
  const char *block=uint32_t(now-s.at)>50 ? "CONTROLLER_STATUS_EXPIRED" : telerc::armName(s.physicalBlock);
  int n=snprintf(bytes,sizeof(bytes),
    "TELERC_BALANCE_V1|STATUS|%lu|%lu|%lu|%lu|%s|%u|%u|%s|%u|%u|%.3f|%.3f|%s|%s|%s|%.4f|%d|150",
    (unsigned long)setupBoot,(unsigned long)setupLease.challenge,(unsigned long)++setupSequence,
    (unsigned long)(request?request:s.setupRequest),result?result:s.setupResult,
    s.mode,(unsigned)s.calibrated,s.store,(unsigned)s.verified,(unsigned)s.testDone,
    s.angle,s.rate,block,s.calStage,s.reason,s.gyroBias,TEST_PWM);
  if(n>0 && size_t(n)<sizeof(bytes) && core.peer && udp.beginPacket(address(core.peer),UDP_PORT)) {
    udp.write(reinterpret_cast<const uint8_t *>(bytes),size_t(n));udp.endPacket();
  }
}
inline bool setupReceive(const uint8_t *bytes,size_t n,uint32_t ip,uint16_t port,uint32_t now,const telerc::Status &s) {
  if(n<sizeof(balance_admin::PREFIX)-1 || memcmp(bytes,balance_admin::PREFIX,sizeof(balance_admin::PREFIX)-1)) return false;
  balance_admin::Request r;
  if(port!=UDP_PORT || !core.peer || ip!=core.peer || !balance_admin::parse(bytes,n,r)) {++core.rejected;return true;}
  core.peerAt=now; // Setup keeps pairing alive, but NEVER refreshes RC motion.
  if(setupPeer!=core.peer){setupLease.reset();setupPeer=core.peer;}
  if(r.stop) {
    ++core.input.setupStop; ++core.input.disarmToken; core.inhibit();
    setupLease.reset();setupReply(s,now,0,"STOP_REQUESTED");return true;
  }
  if(r.query) {if(!setupBoot)setupBoot=esp_random();setupLease.issue(esp_random(),now);setupReply(s,now);return true;}
  if(!setupLease.consume(r,now)){setupReply(s,now,r.id,"CHALLENGE_EXPIRED_REFRESH");return true;}
  if(uint32_t(now-s.at)>50){setupReply(s,now,r.id,"CONTROLLER_STATUS_EXPIRED");return true;}
  if(s.setupToken!=core.input.setupToken){setupReply(s,now,r.id,"BUSY_REFRESH");return true;}
  // Single bounded mailbox. Controller rechecks state, age and stop priority.
  core.input.setupAction=r.action;core.input.setupRequest=r.id;core.input.setupAt=now;
  ++core.input.setupToken;
  setupReply(s,now,r.id,"QUEUED");return true;
}
inline void receive(uint32_t now,const telerc::Status &s) {
  int size=udp.parsePacket(); if(size<=0) return;
  IPAddress ip=udp.remoteIP(); uint16_t port=udp.remotePort();
  uint8_t bytes[256]; int n=udp.read(bytes,sizeof(bytes));
  // flush() only discards this packet, and runs outside the motor task.
  if(udp.available()) udp.flush();
  if(size==n && local(ip) && setupReceive(bytes,(size_t)n,number(ip),port,now,s))return;
  telerc::Datagram d;
  if(size!=n || !local(ip) || !telerc::decode(bytes,(size_t)n,d) ||
      !core.claim(number(ip),port,d,now)) { ++core.rejected; return; }
  bool safetyOnly=false;
  for(uint8_t i=0;i<d.count;++i) {
    const auto &p=d.packets[i]; const auto &m=p.message;
    float arm=1; if(m.id==76) memcpy(&arm,m.payload,4);
    safetyOnly |= p.kind==telerc::Kind::DISCONNECT ||
      (m.id==70 && (!mini_mav::u16(m.payload) || !mini_mav::u16(m.payload+2))) ||
      (m.id==76 && mini_mav::u16(m.payload+28)==400 && arm==0);
  }
  for(uint8_t i=0;i<d.count;++i) {
    const auto &p=d.packets[i]; const auto &m=p.message;
    float arm=1; if(m.id==76) memcpy(&arm,m.payload,4);
    bool safety=p.kind==telerc::Kind::DISCONNECT ||
      (m.id==70 && (!mini_mav::u16(m.payload) || !mini_mav::u16(m.payload+2))) ||
      (m.id==76 && mini_mav::u16(m.payload+28)==400 && arm==0);
    if(safetyOnly && !safety) continue;
    auto reply=core.apply(p,now,s);
    if(reply.ack) {
      ack(reply.command,reply.result);
      if(reply.command==400 && (reply.result==2 || reply.result==5)) statusText(reply.block,reply.result);
    }
  }
}
inline telerc::WifiStage startAccessPoint() {
  WiFi.persistent(false);
  if(!WiFi.mode(WIFI_AP)) return telerc::WifiStage::MODE_FAILED;
  WiFi.setSleep(false);
  IPAddress ap(192,168,4,1);
  if(!WiFi.softAPConfig(ap,ap,IPAddress(255,255,255,0))) return telerc::WifiStage::IP_FAILED;
  if(!WiFi.softAP(WIFI_SSID,WIFI_PASSWORD,6,0,1)) return telerc::WifiStage::AP_FAILED;
  if(!udp.begin(UDP_PORT)) return telerc::WifiStage::UDP_FAILED;
  return telerc::WifiStage::READY;
}
inline void task(void *) {
  wifiStage=startAccessPoint();
  initialized=wifiStage==telerc::WifiStage::READY;
  publishInput();
  // Initialization failure is visible as ready=false. Do not restart WiFi in motion.
  for(;;) {
    auto s=getStatus(); uint32_t now=millis();
    core.watchdog(now,initialized && WiFi.softAPgetStationNum()>0);
    if(!core.peer){setupLease.reset();setupPeer=0;}
    if(initialized) {
      if(uint32_t(now-s.at)<=50) {
        if(previouslyArmed && !s.armed) { core.inhibit(); core.armFinished(now); }
        previouslyArmed=s.armed;
        // Process actual completion before another ARM retry can be considered.
        if(s.completedArm && s.completedArm!=completedSent) {
          core.armFinished(now);
          core.input.lastArmBlock=s.completedBlock;
          ack(400,s.armResult); statusText(s.completedBlock,s.armResult);
          completedSent=s.completedArm;
        }
        receive(now,s); // At most one <=256 byte / <=4 frame datagram per tick.
        if(uint32_t(now-lastHeartbeat)>=250) heartbeat(s);
        if(core.peer && uint32_t(now-lastAttitude)>=100) attitude(s);
      } else {
        // Frozen control task: never advertise a fresh healthy heartbeat or accept ARM.
        core.inhibit();
        telerc::Status unavailable=s; unavailable.ready=false;
        unavailable.physicalBlock=telerc::ArmBlock::CONTROLLER_STALE;
        receive(now,unavailable); // DISARM remains available; movement stays inhibited.
        core.inhibit();
      }
    }
    publishInput();
    vTaskDelay(pdMS_TO_TICKS(2));
  }
}
}
inline bool networkBegin() {
  TaskHandle_t handle=nullptr;
  return xTaskCreatePinnedToCore(transport::task,"telerc-wifi",6144,nullptr,1,&handle,0)==pdPASS;
}
inline telerc::Input networkInput() {
  portENTER_CRITICAL(&transport::mux); auto in=transport::input;
  portEXIT_CRITICAL(&transport::mux); return in;
}
inline void networkStatus(const telerc::Status &s) {
  portENTER_CRITICAL(&transport::mux); transport::status=s;
  portEXIT_CRITICAL(&transport::mux);
}
#endif

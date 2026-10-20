#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <esp_arduino_version.h>
#include <esp_system.h>
#include "Config.h"
#include "RoverCore.h"
#include "MiniMavlink.h"
#include "MotorOutputs.h"
#include "CrsfReceiver.h"
#ifndef TELERC_HOST_TEST
#include <esp_random.h>
#endif
uint32_t bootNonce=1;
#if TELERC_GO_ELRS
#include <HardwareSerial.h>
HardwareSerial rfSerial(1);
#endif
go_rf::Parser rfParser;
go_rf::Channels rfChannels;
go_rf::Authority authority;

#if !defined(CONFIG_IDF_TARGET_ESP32S3) && !defined(TELERC_HOST_TEST)
#error "Select ESP32S3 Dev Module: this release targets ESP32-S3 hardware profiles."
#endif

const IPAddress AP_IP(192,168,4,1), ZERO_IP(0,0,0,0);
const char DISCOVERY[]="TELERC_DISCOVER_V1";
const char DISCONNECT[]="TELERC_DISCONNECT_V1";
WiFiUDP udp;
IPAddress peer=ZERO_IP;
uint16_t peerPort=0;
uint32_t lastPeer=0, lastHeartbeat=0, lastMotorTick=0, lastLog=0;
uint32_t accepted=0, rejected=0, failsafes=0, rcFrames=0, movingFrames=0;
const char *lastStop="BOOT";
uint8_t txSequence=0;
bool pwmReady=false, bootHeld=false, wifiReady=false;
rover::State state;
rover::Motor motors[4];
motor_output::Bank motorBank;

void coastAll() {
  motorBank.stopAll();
  for(auto &motor:motors) motor.output=0;
}
void stopRover(bool disarm,const char *reason=nullptr) {
  state.stop(disarm); coastAll();
  if(disarm) authority.halt();
  if(reason) lastStop=reason;
}
bool localIP(const IPAddress &ip) {
  return ip[0]==192 && ip[1]==168 && ip[2]==4 && ip[3]>1 && ip[3]<255;
}
bool isPeer(const IPAddress &ip,uint16_t port) { return peer==ip && peerPort==port; }
void sendMessage(uint8_t id,const uint8_t *payload,uint8_t len,uint8_t extra,bool announce) {
  if(!wifiReady || (peer==ZERO_IP && !announce)) return;
  uint8_t frame[48];
  const size_t n=mini_mav::encode(frame,txSequence++,config::SYSTEM_ID,
                         config::COMPONENT_ID,id,payload,len,extra);
  const IPAddress destination=peer==ZERO_IP ? IPAddress(192,168,4,255) : peer;
  const uint16_t port=peer==ZERO_IP ? config::UDP_PORT : peerPort;
  if(udp.beginPacket(destination,port)) { udp.write(frame,n); udp.endPacket(); }
}
void heartbeat() {
  uint8_t payload[9]={};mini_mav::put32(payload,bootNonce);
  payload[4]=10; // MAV_TYPE_GROUND_ROVER
  payload[5]=0;  // MAV_AUTOPILOT_GENERIC: a real custom controller, not ArduPilot.
  payload[6]=64 | (state.armed ? 128 : 0); // manual input, armed bit
  payload[7]=state.armed ? 4 : 3; payload[8]=3;
  sendMessage(0,payload,9,50,true); lastHeartbeat=millis();
#if TELERC_GO_ELRS
  uint8_t own[18]={};mini_mav::put32(own,millis());
  mini_mav::put32(own+4,uint32_t(authority.owner)|(authority.fresh(millis())?256:0)|(authority.stop?512:0));
  memcpy(own+8,"TELERC_OWN",10);sendMessage(252,own,18,44,true);
#endif
}
void commandAck(uint16_t command,uint8_t result) {
  uint8_t payload[3]; mini_mav::put16(payload,command); payload[2]=result;
  sendMessage(77,payload,3,143,false);
}
bool targetOK(uint8_t system,uint8_t component) {
  return system==config::SYSTEM_ID && (component==0 || component==config::COMPONENT_ID);
}
bool overrideOK(const mini_mav::Message &m) {
  if(!targetOK(m.payload[16],m.payload[17])) return false;
  const uint16_t s=mini_mav::u16(m.payload), d=mini_mav::u16(m.payload+2);
  const auto valid=[](uint16_t v) { return v==0 || (v>=1000 && v<=2000); };
  if(!valid(s) || !valid(d)) return false;
  // No auxiliary outputs. Ignore CH3..18; they never control motors or arm.
  return true;
}
bool dispatch(const mini_mav::Message &m,uint32_t now) {
  if(m.system!=config::GCS_SYSTEM_ID || m.component!=config::GCS_COMPONENT_ID) return false;
  if(m.id==0) return m.payload[4]==6 && m.payload[8]==3;
  if(m.id==70) {
    if(!overrideOK(m))return false;
#if !TELERC_GO_ELRS
    if(authority.owner==go_rf::Authority::STOPPED && mini_mav::u16(m.payload)>=1475 && mini_mav::u16(m.payload)<=1525 && mini_mav::u16(m.payload+2)>=1475 && mini_mav::u16(m.payload+2)<=1525)authority.owner=go_rf::Authority::PHONE;
#endif
    if(authority.owner!=go_rf::Authority::PHONE)return false;
    if(bootHeld || !pwmReady || !motorBank.ready) return false;
    const bool ok=state.rc(mini_mav::u16(m.payload),mini_mav::u16(m.payload+2),now);
    if(ok) {
      ++rcFrames;
      if(!state.haveRC) {lastStop="RELEASE";authority.halt();}
      else if(rover::axis(state.steer)!=0 || rover::axis(state.drive)!=0) ++movingFrames;
    }
    if(state.inhibited || !state.armed) coastAll();
    return ok;
  }
  if(m.id==76) {
    if(!targetOK(m.payload[30],m.payload[31])) return false;
    const uint16_t command=mini_mav::u16(m.payload+28);
#if TELERC_GO_ELRS
    if(command==31010) {
      float take;memcpy(&take,m.payload,4);bool plain=true;for(int i=4;i<28;i++)if(m.payload[i]!=0)plain=false;
      if(take!=1.0f||!plain||authority.stop||bootHeld||!pwmReady){commandAck(command,2);return true;}
      stopRover(true,"PHONE_TAKE");authority.takePhone();commandAck(command,0);heartbeat();return true;
    }
#endif
    if(command!=400) { commandAck(command,3); return true; } // unsupported
    float param1; memcpy(&param1,m.payload,sizeof(param1));
    bool plain=true;
    for(int i=4;i<28;++i) if(m.payload[i]!=0) plain=false; // rejects force-arm
    if(!plain || !isfinite(param1) || (param1!=0.0f && param1!=1.0f)) {
      commandAck(command,2); return true;
    }
    if(param1==0) { stopRover(true,"DISARM"); commandAck(command,0); }
    else if(authority.owner==go_rf::Authority::PHONE && !bootHeld && pwmReady && motorBank.ready && state.arm(now)) {
      lastStop="ARMED"; commandAck(command,0);
    }
    else commandAck(command,2); // denied: send fresh centered CH1/CH2 first
    heartbeat(); return true;
  }
  return false;
}
void watchdog(uint32_t now) {
  const bool pressed=digitalRead(config::BOOT_STOP_PIN)==LOW;
  if(pressed) stopRover(true,"BOOT_STOP");
  if(motorBank.fault) {
    pwmReady=false;
    if(state.armed || state.haveRC) stopRover(true,"PWM_ERROR");
    lastStop="PWM_ERROR";
  }
  bootHeld=pressed;
  if(state.haveRC &&
     rover::age(now,state.lastRC)>=config::CONTROL_TIMEOUT_MS) {
    stopRover(true,"COMMAND_TIMEOUT"); ++failsafes;
  }
  // Discovery and GCS heartbeat do not refresh the control watchdog.
  if(peer!=ZERO_IP && (rover::age(now,lastPeer)>=config::PEER_TIMEOUT_MS ||
                       WiFi.softAPgetStationNum()==0)) {
    if(authority.owner==go_rf::Authority::PHONE)stopRover(true,"PEER_LOSS");
    peer=ZERO_IP; peerPort=0; ++failsafes;
  }
}
void receiveOne() {
  if(!wifiReady) return;
  const int size=udp.parsePacket(); if(size<=0) return;
  const IPAddress source=udp.remoteIP(); const uint16_t port=udp.remotePort();
  uint8_t data[256]; const int count=udp.read(data,sizeof(data));
  while(udp.available()) udp.read();
  if(size!=count || !localIP(source) || port!=config::UDP_PORT) { ++rejected; return; }
  const uint32_t now=millis();
  // Discovery never grants ARM or refreshes motion commands.
  if(size==int(sizeof(DISCOVERY)-1) && !memcmp(data,DISCOVERY,sizeof(DISCOVERY)-1)) {
    if(peer!=ZERO_IP && !isPeer(source,port)) { ++rejected; return; }
    if(peer==ZERO_IP) {
#if !TELERC_GO_ELRS
      stopRover(true,"NEW_PEER");authority.owner=go_rf::Authority::PHONE;
#endif
      peer=source; peerPort=port;
    }
    lastPeer=now; heartbeat(); ++accepted; return;
  }
  if(peer==ZERO_IP) {
    // Register
    // only one complete, correctly identified GCS heartbeat, never a drive
    // packet or a heartbeat followed by an ARM/override in the same datagram.
    mini_mav::Message hello; size_t length=0;
    if(!mini_mav::decode(data,size_t(count),hello,length) || length!=size_t(count) ||
       hello.id!=0 || hello.system!=config::GCS_SYSTEM_ID ||
       hello.component!=config::GCS_COMPONENT_ID || hello.payload[4]!=6 ||
       hello.payload[5]!=8 || hello.payload[8]!=3) { ++rejected; return; }
#if !TELERC_GO_ELRS
    stopRover(true,"NEW_PEER");authority.owner=go_rf::Authority::PHONE;
#endif
    peer=source; peerPort=port; lastPeer=now; heartbeat(); ++accepted; return;
  }
  if(!isPeer(source,port)) { ++rejected; return; }
  if(size==int(sizeof(DISCONNECT)-1) && !memcmp(data,DISCONNECT,sizeof(DISCONNECT)-1)) {
    stopRover(true,"DISCONNECT");
    heartbeat(); peer=ZERO_IP; peerPort=0; ++accepted; return;
  }
  // Validate the complete datagram before any command side effects.
  size_t offset=0, used=0; mini_mav::Message m;
  while(offset<size_t(count)) {
    if(!mini_mav::decode(data+offset,count-offset,m,used) ||
        m.system!=config::GCS_SYSTEM_ID || m.component!=config::GCS_COMPONENT_ID ||
        (m.id==70 && !overrideOK(m))) { ++rejected; return; }
    offset+=used;
  }
  offset=0;
  while(offset<size_t(count)) {
    mini_mav::decode(data+offset,count-offset,m,used);
    if(dispatch(m,now)) { lastPeer=now; ++accepted; } else ++rejected;
    offset+=used;
  }
}
void updateMotors(uint32_t now) {
  if(rover::age(now,lastMotorTick)<config::MOTOR_TICK_MS) return;
  lastMotorTick=now;
  if(!state.armed || state.inhibited || !state.haveRC || bootHeld) { coastAll(); return; }
  int16_t left=0,right=0; state.mix(left,right);
  int16_t demands[4]={};
  for(uint8_t i=0;i<4;++i) {
    if(!hardware::active(i)) { motors[i].output=0; continue; }
    const int16_t target=motor_output::limitedDemand(i,i<2 ? left : right);
    demands[i]=motors[i].update(target,now);
  }
  motorBank.apply(demands,now);
  if(!motorBank.ready) { pwmReady=false; stopRover(true,"PWM_ERROR"); }
}
void setup() {
#ifdef TELERC_HOST_TEST
  ++bootNonce;
#else
  bootNonce=esp_random();if(!bootNonce)bootNonce=1;
#endif
  // Validate pin allocation before touching any configured output.
  if(!motorBank.prepare()) {
    Serial.begin(115200); Serial.println("FATAL invalid hardware pin profile; no outputs initialized");
    while(true) delay(1000);
  }
  pinMode(config::BOOT_STOP_PIN,INPUT_PULLUP);
  if(config::STATUS_LED_PIN>=0) { pinMode(config::STATUS_LED_PIN,OUTPUT); digitalWrite(config::STATUS_LED_PIN,LOW); }
  Serial.begin(115200); // Never wait for a USB serial host.
  Serial.printf("TeleRC MiniRover %s; reset reason=%d\n",config::VERSION,int(esp_reset_reason()));
  if(!motorBank.begin()) {
    Serial.println("FATAL PWM setup; driver outputs disabled");
    while(true) delay(1000);
  }
  pwmReady=true; coastAll();
  Serial.printf("profile=%s motors=%u PWM=%luHz declared supply=%lumV (not measured)\n",
    hardware::driverName(),hardware::MOTOR_COUNT,(unsigned long)hardware::PWM_HZ,
    (unsigned long)hardware::SUPPLY_MAX_MV);
#if TELERC_GO_ELRS
  rfSerial.setRxBufferSize(128);rfSerial.begin(420000,SERIAL_8N1,39,-1,false);
  rfSerial.setRxFIFOFull(26);rfSerial.setRxTimeout(1);
#endif
  WiFi.persistent(false); WiFi.mode(WIFI_AP); WiFi.setSleep(false);
  wifiReady=WiFi.softAPConfig(AP_IP,AP_IP,IPAddress(255,255,255,0)) &&
    WiFi.softAP(config::WIFI_SSID,config::WIFI_PASSWORD,6,0,1) && udp.begin(config::UDP_PORT);
  if(!wifiReady) Serial.println("Wi-Fi unavailable; outputs remain disabled");
  Serial.printf("AP=%s IP=192.168.4.1 UDP=%u; CH1=steer CH2=drive\n",config::WIFI_SSID,config::UDP_PORT);
}
// Bounded serial output. An absent/slow USB terminal cannot feed or block the
// control watchdog. Values are command and LEDC-register state, not motor feedback.
char diagnosticLine[512];
uint16_t diagnosticLength=0, diagnosticOffset=0;
void serviceDiagnostics(uint32_t now) {
  if(rover::age(now,lastLog)>=1000 && diagnosticOffset>=diagnosticLength) {
    lastLog=now;
    const int n=snprintf(diagnosticLine,sizeof(diagnosticLine),
      "v=%s drv=%s hz=%lu arm=%u rc=%lu move=%lu age=%lu CH1=%u CH2=%u OUT=%d/%d/%d/%d PWM=%lu,%lu/%lu,%lu/%lu,%lu/%lu,%lu ready=%u err=%lu reason=%s\n",
      config::VERSION,hardware::driverName(),(unsigned long)hardware::PWM_HZ,state.armed,
      (unsigned long)rcFrames,(unsigned long)movingFrames,
      (unsigned long)(state.haveRC ? rover::age(now,state.lastRC) : 999999),state.steer,state.drive,
      motors[0].output,motors[1].output,motors[2].output,motors[3].output,
      (unsigned long)motorBank.readDuty(0,0),(unsigned long)motorBank.readDuty(0,1),
      (unsigned long)motorBank.readDuty(1,0),(unsigned long)motorBank.readDuty(1,1),
      (unsigned long)motorBank.readDuty(2,0),(unsigned long)motorBank.readDuty(2,1),
      (unsigned long)motorBank.readDuty(3,0),(unsigned long)motorBank.readDuty(3,1),
      motorBank.ready,(unsigned long)motorBank.writeErrors,lastStop);
    diagnosticOffset=0;
    diagnosticLength=n<0 ? 0 : (n<int(sizeof(diagnosticLine)) ? n : sizeof(diagnosticLine)-1);
  }
  if(diagnosticOffset>=diagnosticLength) return;
  const int space=Serial.availableForWrite(); if(space<=0) return;
  unsigned n=diagnosticLength-diagnosticOffset;
  if(n>32) n=32;
  if(n>unsigned(space)) n=unsigned(space);
  diagnosticOffset+=Serial.write(reinterpret_cast<const uint8_t *>(diagnosticLine+diagnosticOffset),n);
}
void serviceReceiver(uint32_t now) {
#if TELERC_GO_ELRS
  if(authority.owner==go_rf::Authority::TRANSMITTER && !authority.fresh(now))stopRover(true,"ELRS_TIMEOUT");
  // Discard accumulated old input rather than refreshing age from a UART backlog.
  if(rfSerial.available()>64){unsigned budget=128;while(budget-- && rfSerial.available())rfSerial.read();stopRover(true,"ELRS_BACKLOG");rfParser=go_rf::Parser();return;}
  unsigned budget=64;
  while(budget-- && rfSerial.available()){
    const int value=rfSerial.read();if(value<0)break;
    if(!rfParser.feed(uint8_t(value),now,rfChannels))continue;
    const bool request=authority.observe(rfChannels,now);
    if(authority.stop){stopRover(true,"ELRS_STOP");continue;}
    if(authority.owner==go_rf::Authority::PHONE)continue;
    const bool low=rfChannels.us[4]<1300;
    if(low && state.armed)stopRover(true,"ELRS_DISARM");
    if(authority.owner==go_rf::Authority::TRANSMITTER || low || request)state.rc(rfChannels.us[0],rfChannels.us[1],now);
    if(request && !bootHeld && pwmReady && motorBank.ready && state.arm(now)){authority.owner=go_rf::Authority::TRANSMITTER;lastStop="ELRS_ARMED";}
  }
#else
  (void)now;
#endif
}
void loop() {
  watchdog(millis()); // Expire old drive BEFORE accepting any queued packet.
  serviceReceiver(millis());
  receiveOne();       // One bounded datagram per iteration.
  const uint32_t now=millis(); watchdog(now); updateMotors(now);
  if(wifiReady && rover::age(now,lastHeartbeat)>=250) heartbeat();
  if(config::STATUS_LED_PIN>=0) {
    const bool led=state.armed ? !state.inhibited || ((now/100)%2) : ((now/500)%2);
    digitalWrite(config::STATUS_LED_PIN,led ? HIGH : LOW);
  }
  serviceDiagnostics(now);
  delay(1);
}

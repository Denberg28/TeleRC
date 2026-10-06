#pragma once
#include <Arduino.h>
#include <SPI.h>
#include <RadioLib.h>
#include <esp_system.h>
#include <Preferences.h>
#include <cstdio>
#include <TeleRCLoRaConfig.h>
#include <TeleRCRadioAuth.h>
#if TELERC_LORA_BASE && TELERC_BASE_WIFI
#include <WiFi.h>
#include <WiFiUdp.h>
#include <Preferences.h>
#endif
// LILYGO T3-S3 V1.2/V1.3 manufacturer pin map. GPIO43/44 are UART, not GPIO17/18.
#if TELERC_RADIO_VARIANT == 1262
Module radioModule(7,33,8,34);
SX1262 radio(&radioModule);
#elif TELERC_RADIO_VARIANT == 1276
Module radioModule(7,9,8,33);
SX1276 radio(&radioModule);
#else
#error Unsupported radio: use the exact SX1262 or SX1276 board; SX1278/SX1280 need a separate profile.
#endif
uint8_t radioKey[32]=TELERC_RADIO_KEY;
struct RadioSettings { uint32_t magic; uint32_t khz; int32_t power; uint8_t key[32]; };
RadioSettings settings={0x544c5231,uint32_t(TELERC_RADIO_FREQUENCY*1000),TELERC_RADIO_POWER,TELERC_RADIO_KEY};
bool radioActive=false;
bool validSettings(const RadioSettings &v){
  uint8_t nonzero=0;for(auto b:v.key)nonzero|=b;
  // Device capability bounds, not permission to use a frequency in your country.
  return v.magic==0x544c5231 && v.khz>=150000 && v.khz<=960000 && v.power>=2 && v.power<=17 && nonzero;
}
telerc::UartParser adminParser;
volatile bool radioPacketReady=false;
void IRAM_ATTR radioInterrupt(){radioPacketReady=true;}
uint32_t radioRx=0,radioReject=0,radioTxFail=0,uartDrop=0;
uint32_t lastPollAt=0,lastValidPollAt=0;
uint64_t lastBaseToken=0;
telerc::ChallengeWindow challenge;
telerc::CommandMailbox commands;
telerc::UartParser hostParser;
#if !TELERC_LORA_BASE
HardwareSerial motorPort(1);
// Three priority mailboxes: ACK > heartbeat > latest small state. Never queue telemetry history.
struct TelemetrySlot {uint8_t data[telerc::RADIO_MAX]={};size_t n=0;uint32_t at=0;};
TelemetrySlot telemetry[3];
#endif
#if TELERC_LORA_BASE && TELERC_BASE_WIFI
WiFiUDP baseUdp;
IPAddress basePeer;
uint32_t basePeerAt=0;
uint8_t hostSource=0; // 1 USB or 2 local UDP; no simultaneous controller inheritance
uint32_t hostAt=0;
#endif

bool writeSerialFrame(Stream &stream,const uint8_t*p,size_t n,int capacity){
  uint8_t f[telerc::UART_MAX+6];size_t k=telerc::encodeUart(p,n,f);
  if(!k||capacity<int(k)){++uartDrop;return false;}return stream.write(f,k)==k;
}
bool receiveRadio(uint8_t expected,uint64_t &token,const uint8_t*&body,size_t &n,uint8_t*buffer){
  if(!radioPacketReady){return false;}
  radioPacketReady=false;
  size_t count=radio.getPacketLength();
  if(count>telerc::AUTH_MAX||count<telerc::AUTH_HEADER+telerc::AUTH_TAG){radio.standby();radio.startReceive();++radioReject;return false;}
  int state=radio.readData(buffer,count);radio.startReceive();
  if(state!=RADIOLIB_ERR_NONE||!telerc::openRadio(buffer,count,radioKey,expected,token,body,n)){++radioReject;return false;}
  ++radioRx;return true;
}
bool transmitRadio(uint8_t kind,uint64_t token,const uint8_t*p,size_t n){
  uint8_t f[telerc::AUTH_MAX];size_t k=telerc::sealRadio(kind,token,p,n,radioKey,f);if(!k)return false;
  // Blocking radio work is confined to communications boards, never the motor ESP32.
  radioPacketReady=false;int result=radio.transmit(f,k);radio.startReceive();
  if(result!=RADIOLIB_ERR_NONE){++radioTxFail;return false;}return true;
}
void adminReply(const char *message){writeSerialFrame(Serial,reinterpret_cast<const uint8_t*>(message),strlen(message),Serial.availableForWrite());}
bool adminCommand(const uint8_t *p,size_t n){
  if(n>telerc::UART_MAX)return false;
  if(n<15 || memcmp(p,"TELERC_LORA_",12)!=0)return false;
  if(telerc::textEquals(p,n,"TELERC_LORA_GET_V1")){
    char info[180];
    // Only a non-secret CRC fingerprint is exposed, never the pairing key.
    snprintf(info,sizeof(info),"TELERC_LORA_INFO_V1,%s,%d,%d,%lu,%ld,%04x,%lu,%lu,%lu,%lu",
      TELERC_LORA_BASE?"BASE":"ROVER",TELERC_RADIO_VARIANT,radioActive?1:0,
      (unsigned long)settings.khz,(long)settings.power,telerc::crc16(settings.key,32),
      (unsigned long)radioRx,(unsigned long)radioReject,(unsigned long)radioTxFail,(unsigned long)uartDrop);
    adminReply(info);return true;
  }
  // Provision only an inactive radio. Active links must be disconnected and boards restarted
  // with a local blank configuration before re-pairing; USB cannot silently change a running link.
  if(radioActive){adminReply("TELERC_LORA_RESULT_V1,ACTIVE_DISCONNECT_AND_ERASE_NVS_FIRST");return true;}
  char request[telerc::UART_MAX+1];memcpy(request,p,n);request[n]=0;
  unsigned long khz=0;int power=0;char key[65]={};int end=0;
  RadioSettings next={};next.magic=0x544c5231;
  if(sscanf(request,"TELERC_LORA_SET_V1,%lu,%d,%64[0123456789abcdefABCDEF]%n",&khz,&power,key,&end)!=3 ||
      size_t(end)!=n || strlen(key)!=64 || khz>960000){adminReply("TELERC_LORA_RESULT_V1,INVALID");return true;}
  next.khz=uint32_t(khz);next.power=power;
  for(size_t i=0;i<32;++i){unsigned int b=0;char pair[3]={key[2*i],key[2*i+1],0};sscanf(pair,"%x",&b);next.key[i]=uint8_t(b);}
  if(!validSettings(next)){adminReply("TELERC_LORA_RESULT_V1,INVALID");return true;}
  Preferences prefs;
  if(!prefs.begin("telerc-radio",false)){adminReply("TELERC_LORA_RESULT_V1,STORAGE_FAILED");return true;}
  bool ok=prefs.putBytes("settings",&next,sizeof(next))==sizeof(next);prefs.end();
  if(ok)settings=next;
  adminReply(ok?"TELERC_LORA_RESULT_V1,SAVED_RESTART_BOARD":"TELERC_LORA_RESULT_V1,STORAGE_FAILED");
  return true;
}
#if !TELERC_LORA_BASE
void serviceAdmin(){
  for(size_t i=0;i<320&&Serial.available();++i)if(adminParser.feed(uint8_t(Serial.read()),millis()))
    adminCommand(adminParser.data+4,adminParser.length());
}
#endif
#if TELERC_LORA_BASE
bool acceptHost(const uint8_t*p,size_t n,uint8_t source){
#if TELERC_BASE_WIFI
  if(hostSource&&hostSource!=source&&uint32_t(millis()-hostAt)<5000)return false;
  if(hostSource!=source){commands.clear();hostSource=source;}
  bool ok=commands.accept(p,n,millis());if(ok)hostAt=millis();return ok;
#else
  (void)source;return commands.accept(p,n,millis());
#endif
}
void serviceHost(){
  for(size_t i=0;i<320&&Serial.available();++i)if(hostParser.feed(uint8_t(Serial.read()),millis()))
    {if(!adminCommand(hostParser.data+4,hostParser.length()) && radioActive)acceptHost(hostParser.data+4,hostParser.length(),1);}
#if TELERC_BASE_WIFI
  int size=baseUdp.parsePacket();if(size<=0)return;
  uint8_t p[telerc::UART_MAX];int n=baseUdp.read(p,sizeof(p));while(baseUdp.available())baseUdp.read();
  auto ip=baseUdp.remoteIP();
  bool local=ip[0]==192&&ip[1]==168&&ip[2]==4&&ip[3]>1&&ip[3]<255;
  if(size!=n||size>int(sizeof(p))||!local||baseUdp.remotePort()!=14550)return;
  if(basePeer!=IPAddress()&&basePeer!=ip&&uint32_t(millis()-basePeerAt)<5000)return;
  if(basePeer!=ip){commands.clear();basePeer=ip;}
  if(acceptHost(p,size_t(n),2))basePeerAt=millis();
#endif
}
void forwardTelemetry(const uint8_t*p,size_t n){
  if(!n)return;
#if TELERC_BASE_WIFI
  if(hostSource==2){
    if(uint32_t(millis()-basePeerAt)<5000&&baseUdp.beginPacket(basePeer,14550)){baseUdp.write(p,n);baseUdp.endPacket();}
    return;
  }
#endif
  writeSerialFrame(Serial,p,n,Serial.availableForWrite());
}
#else
void serviceMotorTelemetry(){
  for(size_t i=0;i<512&&motorPort.available();++i)if(hostParser.feed(uint8_t(motorPort.read()),millis())){
    auto p=hostParser.data+4;size_t n=hostParser.length();if(n>telerc::RADIO_MAX){++uartDrop;continue;}
    int slot=-1;
    bool v1=n>=8&&p[0]==0xfe&&n==size_t(p[1])+8;
    bool v2=n>=12&&p[0]==0xfd&&n==size_t(p[1])+12+((p[2]&1)?13:0);
    uint32_t id=v1?p[5]:v2?(uint32_t(p[7])|(uint32_t(p[8])<<8)|(uint32_t(p[9])<<16)):0xffffffff;
    if(id==77)slot=0;
    else if(id==0)slot=1;
    else if(id==1||id==24||id==33)slot=2;
    else if(n>=16&&memcmp(p,"TELERC_STATUS_V1",16)==0)slot=2;
    if(slot>=0){memcpy(telemetry[slot].data,p,n);telemetry[slot].n=n;telemetry[slot].at=millis();}
  }
}
size_t takeTelemetry(uint8_t*p){
  for(auto &slot:telemetry)if(slot.n){size_t n=slot.n;slot.n=0;if(uint32_t(millis()-slot.at)>1200)continue;memcpy(p,slot.data,n);return n;}
  return 0;
}
bool forwardCommands(const uint8_t*p,size_t n){
  if(!telerc::validBundle(p,n))return false;
  // One atomic UART write prevents forwarding an ARM without its preceding neutral override.
  uint8_t out[3*(telerc::UART_MAX+6)];size_t used=0;
  for(size_t i=0;i<n;){size_t k=p[i++];size_t written=telerc::encodeUart(p+i,k,out+used);used+=written;i+=k;}
  if(!used)return true;
  if(motorPort.availableForWrite()<int(used)){++uartDrop;return false;}
  return motorPort.write(out,used)==used;
}
#endif

void setup(){
  Serial.begin(115200);
#if !TELERC_LORA_BASE
  motorPort.setRxBufferSize(1024);motorPort.setTxBufferSize(1024);motorPort.begin(115200,SERIAL_8N1,44,43);
#endif
  Preferences config;
  bool provisioned=TELERC_RADIO_PROVISIONED;
  if(config.begin("telerc-radio",true)){
    RadioSettings stored={};
    if(config.getBytesLength("settings")==sizeof(stored) && config.getBytes("settings",&stored,sizeof(stored))==sizeof(stored) && validSettings(stored)){
      settings=stored;provisioned=true;
    }
    config.end();
  }
  if(!provisioned || !validSettings(settings))return; // USB setup stays available on a blank board.
  memcpy(radioKey,settings.key,32);
  SPI.begin(5,3,6,7);
  // SF7/BW500/CR4:5 is an initial low-latency test profile, not a validated range claim.
  int state=radio.begin(settings.khz/1000.0f,500.0,7,5,0x12,settings.power,8);
  if(state!=RADIOLIB_ERR_NONE){return;}
#if TELERC_RADIO_VARIANT == 1262
  radio.setDio1Action(radioInterrupt);
#else
  radio.setDio0Action(radioInterrupt, RISING);
#endif
  radioActive=radio.startReceive()==RADIOLIB_ERR_NONE;
#if TELERC_LORA_BASE && TELERC_BASE_WIFI
  Preferences prefs;String password=TELERC_BASE_PASSWORD;
  if(password.length()<12||password.length()>63){
    if(prefs.begin("telerc-lora",false)){
      password=prefs.getString("ap-pass","");
      if(password.length()<12||password.length()>63){char generated[33];const char alphabet[]="0123456789abcdef";for(int i=0;i<32;++i)generated[i]=alphabet[esp_random()&15];generated[32]=0;password=generated;prefs.putString("ap-pass",password);}prefs.end();
    }else {Serial.println("Wi-Fi credential storage failed.");while(true)delay(1000);}
  }
  WiFi.persistent(false);WiFi.mode(WIFI_AP);
  if(!WiFi.softAPConfig(IPAddress(192,168,4,1),IPAddress(192,168,4,1),IPAddress(255,255,255,0))||
      !WiFi.softAP("TeleRC-LoRa-Base",password.c_str())||!baseUdp.begin(14550)){
    Serial.println("Base local link startup failed.");while(true)delay(1000);
  }
  Serial.printf("Local TeleRC-LoRa-Base password: %s\n",password.c_str());
#endif
}
void loop(){
  uint8_t buffer[telerc::AUTH_MAX];uint64_t token=0;const uint8_t*body=nullptr;size_t n=0;
#if TELERC_LORA_BASE
  serviceHost();
  if(!radioActive){delay(1);return;}
  if(uint32_t(millis()-lastValidPollAt)>250)commands.clear();
  if(receiveRadio(1,token,body,n,buffer)&&token!=lastBaseToken){
    lastBaseToken=token;lastValidPollAt=millis();forwardTelemetry(body,n);
    uint8_t command[telerc::RADIO_MAX];size_t count=commands.take(command,millis());
    transmitRadio(2,token,command,count);
  }
#else
  serviceAdmin();
  serviceMotorTelemetry();
  if(!radioActive){delay(1);return;}
  if(receiveRadio(2,token,body,n,buffer)){
    if(telerc::validBundle(body,n)&&challenge.consume(token,millis()))forwardCommands(body,n);
    else ++radioReject;
  }
  if(uint32_t(millis()-lastPollAt)>=100){
    uint8_t telemetryBody[telerc::RADIO_MAX];size_t count=takeTelemetry(telemetryBody);
    token=(uint64_t(esp_random())<<32)|esp_random();
    if(transmitRadio(1,token,telemetryBody,count))challenge.issue(token,millis());
    else challenge.open=false;
    lastPollAt=millis();
  }
#endif
  delay(1);
}

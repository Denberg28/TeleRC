#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <Preferences.h>
#include <esp_system.h>
#include <TeleRCWire.h>
HardwareSerial motor(1);
WiFiUDP udp;
IPAddress peer;
uint32_t peerAt=0;
telerc::CommandMailbox commands;
telerc::UartParser telemetry;
bool ready=false;
void setup(){
 Serial.begin(115200);
 motor.setRxBufferSize(1024);motor.setTxBufferSize(1024);motor.begin(115200,SERIAL_8N1,21,39);
 Preferences prefs;if(!prefs.begin("telerc-gateway",false))return;
 String password=prefs.getString("ap-pass","");
 if(password.length()<12||password.length()>63){
  char key[33];const char alphabet[]="0123456789abcdef";for(int i=0;i<32;++i)key[i]=alphabet[esp_random()&15];key[32]=0;
  password=key;prefs.putString("ap-pass",password);
 }
 prefs.end();WiFi.persistent(false);WiFi.mode(WIFI_AP);
 ready=WiFi.softAPConfig(IPAddress(192,168,4,1),IPAddress(192,168,4,1),IPAddress(255,255,255,0)) &&
   WiFi.softAP("TeleRC-Rover",password.c_str()) && udp.begin(14550);
 if(ready)Serial.printf("TeleRC-Rover password: %s\n",password.c_str());
}
void loop(){
 if(!ready){delay(1);return;}
 if(peer!=IPAddress() && uint32_t(millis()-peerAt)>5000){peer=IPAddress();commands.clear();}
 int count=udp.parsePacket();
 if(count>0){
  uint8_t p[telerc::UART_MAX];int n=udp.read(p,sizeof(p));while(udp.available())udp.read();
  auto ip=udp.remoteIP();bool local=ip[0]==192&&ip[1]==168&&ip[2]==4&&ip[3]>1&&ip[3]<255;
  if(n==count && local && udp.remotePort()==14550 && (peer==IPAddress()||peer==ip)){
   if(commands.accept(p,size_t(n),millis())){peer=ip;peerAt=millis();}
  }
 }
 uint8_t bundle[telerc::RADIO_MAX];size_t n=commands.take(bundle,millis());
 uint8_t out[3*(telerc::UART_MAX+6)];size_t used=0;
 for(size_t i=0;i<n;){size_t k=bundle[i++];used+=telerc::encodeUart(bundle+i,k,out+used);i+=k;}
 // Drop on congestion; never preserve a backlog or regenerate control freshness.
 if(used && motor.availableForWrite()>=int(used))motor.write(out,used);
 for(size_t i=0;i<512&&motor.available();++i)if(telemetry.feed(uint8_t(motor.read()),millis())){
  if(peer!=IPAddress() && udp.beginPacket(peer,14550)){udp.write(telemetry.data+4,telemetry.length());udp.endPacket();}
 }
 delay(1);
}

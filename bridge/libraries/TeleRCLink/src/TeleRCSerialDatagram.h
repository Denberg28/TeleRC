#pragma once
#include <Arduino.h>
#include <IPAddress.h>
#include <TeleRCWire.h>
// Compatibility adapter for the shared motor parser. Fixed logical peer is the physically wired gateway.
class TeleRCSerialDatagram {
  HardwareSerial &port;telerc::UartParser parser;
  uint8_t incoming[telerc::UART_MAX]={},outgoing[telerc::UART_MAX]={};size_t count=0,cursor=0,outCount=0;
public:
  explicit TeleRCSerialDatagram(HardwareSerial&p):port(p){}
  bool begin(uint16_t){return true;}
  int parsePacket(){
    if(cursor<count)return int(count);
    count=cursor=0;
    for(size_t i=0;i<320 && port.available();++i)if(parser.feed(uint8_t(port.read()),millis())){
      count=parser.length();memcpy(incoming,parser.data+4,count);return int(count);
    }
    return 0;
  }
  IPAddress remoteIP(){return IPAddress(192,168,4,2);}
  uint16_t remotePort(){return 14550;}
  int available(){return int(count-cursor);}
  int read(){return cursor<count?incoming[cursor++]:-1;}
  int read(uint8_t*p,size_t n){if(n>count-cursor)n=count-cursor;memcpy(p,incoming+cursor,n);cursor+=n;return int(n);}
  bool beginPacket(IPAddress,uint16_t){outCount=0;return true;}
  size_t write(const uint8_t*p,size_t n){if(n>sizeof(outgoing)-outCount)return 0;memcpy(outgoing+outCount,p,n);outCount+=n;return n;}
  bool endPacket(){uint8_t frame[telerc::UART_MAX+6];size_t n=telerc::encodeUart(outgoing,outCount,frame);if(!n||port.availableForWrite()<int(n))return false;return port.write(frame,n)==n;}
};

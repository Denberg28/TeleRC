#pragma once
#include <cstdint>
#include <cstddef>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>
#include <algorithm>
using std::max;
#define IRAM_ATTR
#define LOW 0
#define HIGH 1
#define INPUT_PULLDOWN 0
#define OUTPUT 1
#define CHANGE 2
#define SERIAL_8N1 0
using portMUX_TYPE = int;
#define portMUX_INITIALIZER_UNLOCKED 0
#define portENTER_CRITICAL(x) ((void)0)
#define portEXIT_CRITICAL(x) ((void)0)
#define portENTER_CRITICAL_ISR(x) ((void)0)
#define portEXIT_CRITICAL_ISR(x) ((void)0)
inline uint32_t testMs=1000, testUs=1000000;
inline int pins[64]={}, duties[64]={};
inline bool attachOk=true;
inline uint32_t millis(){return testMs;}
inline uint32_t micros(){return testUs;}
inline void delay(int){}
inline int digitalRead(int p){return pins[p];}
inline void digitalWrite(int p,int v){pins[p]=v;}
inline void pinMode(int,int){}
inline int digitalPinToInterrupt(int p){return p;}
inline void attachInterrupt(int,void(*)(),int){}
template<class T> T constrain(T x,T a,T b){return std::clamp(x,a,b);}
struct String:std::string {using std::string::string; String(std::string s):std::string(s){} };
struct SerialMock {int availableForWrite(){return 1024;}size_t write(const uint8_t*,size_t n){return n;}void begin(int){} void println(const char* = ""){} template<class... A>void printf(const char*,A...){} };
inline SerialMock Serial;
struct HardwareSerial {int capacity=1024;std::vector<uint8_t> tx; HardwareSerial(int){} void setRxBufferSize(int){} void setTxBufferSize(int){} void begin(int,int,int,int){} int available(){return 0;}int read(){return 0;}int availableForWrite(){return capacity;}size_t write(const uint8_t*p,size_t n){tx.insert(tx.end(),p,p+n);return n;}};
inline bool ledcAttach(int,int,int){return attachOk;}
inline bool ledcWrite(int p,int v){duties[p]=v;return true;}
inline double ledcSetup(int,int,int){return attachOk?20000:0;}
inline void ledcAttachPin(int,int){}

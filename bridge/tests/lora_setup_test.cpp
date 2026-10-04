#include <cassert>
#include <iostream>
#define TELERC_LORA_BASE 1
#include <TeleRCLoRaCore.h>
void request(const char*s){assert(adminCommand(reinterpret_cast<const uint8_t*>(s),strlen(s)));}
int main(){
 setup();assert(!radioActive);loop();assert(radio.tx.empty());
 request("TELERC_LORA_GET_V1");assert(!Serial.tx.empty());
 request("TELERC_LORA_SET_V1,433000,2,0000000000000000000000000000000000000000000000000000000000000000");assert(Preferences::saved.empty());
 request("TELERC_LORA_SET_V1,433000,2,0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20junk");assert(Preferences::saved.empty());
 request("TELERC_LORA_SET_V1,433000,18,0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");assert(Preferences::saved.empty());
 request("TELERC_LORA_SET_V1,433000,2,0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");assert(Preferences::saved.size()==sizeof(RadioSettings));assert(!radioActive);
 setup();assert(radioActive && radioKey[31]==0x20);auto saved=Preferences::saved;
 request("TELERC_LORA_SET_V1,868000,2,ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");assert(Preferences::saved==saved);
 std::cout<<"USB provisioning, restart and active-link protection checks passed\n";
}

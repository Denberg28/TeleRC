#include <cassert>
#include <iostream>
#include "../TeleRCWiFiGateway/TeleRCWiFiGateway.ino"
int main(){
 setup();assert(ready);
 const uint8_t discover[]="TELERC_DISCOVER_V1";
 udp.rx.assign(discover,discover+sizeof(discover)-1);loop();assert(!motor.tx.empty());auto count=motor.tx.size();
 udp.rx.clear();udp.cursor=0;loop();assert(motor.tx.size()==count);
 udp.peer=IPAddress(192,168,4,3);udp.rx.assign(discover,discover+sizeof(discover)-1);loop();assert(motor.tx.size()==count);
 testMs+=5001;udp.cursor=0;loop();assert(motor.tx.size()>count);
 count=motor.tx.size();udp.rx.clear();udp.cursor=0;motor.capacity=0;
 udp.rx.assign(discover,discover+sizeof(discover)-1);loop();assert(motor.tx.size()==count);
 std::cout<<"Swappable Wi-Fi gateway lease and forwarding checks passed\n";
}

#include <cassert>
#include <cstdio>
#include "../NetworkLink.h"
int checks=0;
void check(bool b){assert(b);++checks;}
std::vector<uint8_t> frame(bool arm,uint8_t seq=200) {
  uint8_t p[33]={},out[64];float value=arm?1:0;memcpy(p,&value,4);
  mini_mav::put16(p+28,400);p[30]=p[31]=1;
  size_t n=mini_mav::encode(out,seq,255,190,76,p,33,152);
  return {out,out+n};
}
void receive(const std::vector<uint8_t> &bytes,uint32_t now,telerc::Status s) {
  transport::udp.rx=bytes;transport::udp.cursor=0;
  transport::receive(now,s);
}
int main() {
  check(transport::startAccessPoint()==telerc::WifiStage::READY);
  WiFi.modeOK=false;check(transport::startAccessPoint()==telerc::WifiStage::MODE_FAILED);
  WiFi.modeOK=true;WiFi.ipOK=false;check(transport::startAccessPoint()==telerc::WifiStage::IP_FAILED);
  WiFi.ipOK=true;WiFi.apOK=false;check(transport::startAccessPoint()==telerc::WifiStage::AP_FAILED);
  WiFi.apOK=true;transport::udp.beginOK=false;
  check(transport::startAccessPoint()==telerc::WifiStage::UDP_FAILED);
  transport::udp.beginOK=true;
  transport::initialized=true;
  telerc::Status s;s.ready=true;
  std::string hello="TELERC_DISCOVER_V1";
  receive({hello.begin(),hello.end()},0,s);check(transport::core.peer==0xc0a80402);
  auto packet=frame(true);packet.push_back(0);
  receive(packet,1,s);check(transport::core.input.armToken==0); // No partial datagram effects.
  transport::core.haveRC=true;transport::core.input.fresh=true;transport::core.input.lastRC=1;
  auto stop=frame(false),arm=frame(true);
  packet=stop;packet.insert(packet.end(),arm.begin(),arm.end());
  receive(packet,2,s);
  check(transport::core.input.disarmToken==1&&transport::core.input.armToken==0);
  check(!transport::core.input.fresh&&!transport::core.input.armPending);
  packet=arm;packet.insert(packet.end(),stop.begin(),stop.end());
  receive(packet,3,s);check(transport::core.input.disarmToken==2&&transport::core.input.armToken==0);
  transport::udp.source=IPAddress(192,168,4,3);
  receive(stop,4,s);check(transport::core.input.disarmToken==2);
  transport::udp.source=IPAddress(192,168,4,2);transport::udp.port=14551;
  receive(stop,5,s);check(transport::core.input.disarmToken==2);
  transport::udp.port=14550;
  receive(std::vector<uint8_t>(300,0),6,s);check(transport::core.input.disarmToken==2);
  transport::publishInput();auto in=networkInput();check(in.ready&&in.linked);
  networkStatus(s);check(transport::getStatus().ready);
  transport::heartbeat(s);check(transport::udp.lastTx.size()==17&&transport::udp.lastTx[5]==0);
  transport::ack(400,5);check(transport::udp.lastTx.size()==11&&transport::udp.lastTx[8]==5);
  transport::attitude(s);check(transport::udp.lastTx.size()==36&&transport::udp.lastTx[5]==30);
  transport::statusText(telerc::ArmBlock::VERIFY_DIRECTIONS,2);
  auto text=transport::udp.lastTx;
  check(text.size()==59&&text[5]==253&&text[6]==4);
  check(!memcmp(text.data()+7,"VERIFY_DIRECTIONS",17));
  uint16_t crc=0xffff;
  for(size_t i=1;i<57;++i)crc=mini_mav::accumulate(crc,text[i]);
  check(mini_mav::accumulate(crc,83)==mini_mav::u16(text.data()+57));
  transport::udp.lastTx.clear();transport::statusText(telerc::ArmBlock::VERIFY_DIRECTIONS,2);
  check(transport::udp.lastTx.empty()); // Repeated diagnostics are rate limited.
  // Real production admin transport: paired endpoint, one-use challenge,
  // bounded mailbox, stop priority and independent RC freshness.
  auto admin=[&](const std::string &request,uint32_t now) {
    s.at=now;receive({request.begin(),request.end()},now,s);
  };
  transport::core.input.lastRC=7;
  admin("TELERC_BALANCE_V1|Q",20);
  std::string status(transport::udp.lastTx.begin(),transport::udp.lastTx.end());
  check(status.find("TELERC_BALANCE_V1|STATUS|")==0&&transport::setupLease.challenge!=0&&transport::setupBoot!=0);
  check(status.find("|140|300|0|0|0|0|0|0|0|0|0")!=std::string::npos);
  check(std::count(status.begin(),status.end(),'|')==28); // Paired app parses exactly 29 fields.
  check(transport::core.input.lastRC==7); // Query never refreshes drive lease.
  uint32_t challenge=transport::setupLease.challenge;
  std::string action="TELERC_BALANCE_V1|"+std::to_string(challenge)+"|33|4";
  admin(action,21);
  check(transport::core.input.setupToken==1&&transport::core.input.setupAction==balance_admin::BOTH);
  check(transport::core.input.setupRequest==33&&transport::core.input.setupAt==21);
  admin(action,22);check(transport::core.input.setupToken==1); // Duplicate is not executed.
  admin("TELERC_BALANCE_V1|Q",23);
  challenge=transport::setupLease.challenge;
  admin("TELERC_BALANCE_V1|"+std::to_string(challenge)+"|34|2",24);
  check(transport::core.input.setupToken==1); // Busy until controller consumes previous mailbox.
  s.setupToken=1;
  admin("TELERC_BALANCE_V1|Q",25);challenge=transport::setupLease.challenge;
  transport::udp.source=IPAddress(192,168,4,3);
  admin("TELERC_BALANCE_V1|"+std::to_string(challenge)+"|35|2",26);
  check(transport::core.input.setupToken==1&&transport::setupLease.challenge==challenge);
  transport::udp.source=IPAddress(192,168,4,2);
  admin("TELERC_BALANCE_V1|"+std::to_string(challenge)+"|35|2",27);
  check(transport::core.input.setupToken==2&&transport::core.input.setupAction==balance_admin::LEFT);
  auto stopToken=transport::core.input.disarmToken;
  admin("TELERC_BALANCE_V1|S",28);
  check(transport::core.input.disarmToken==stopToken+1&&transport::core.input.setupStop==1&&transport::setupLease.challenge==0);
  check(!transport::core.input.motion);
  admin("TELERC_BALANCE_V1|Q",29);challenge=transport::setupLease.challenge;
  s.at=29;
  std::string stale="TELERC_BALANCE_V1|"+std::to_string(challenge)+"|36|4";
  receive({stale.begin(),stale.end()},100,s);
  check(transport::core.input.setupToken==2); // Stale controller cannot accept action.
  std::printf("Network adapter: %d assertions PASS\n",checks);
}

#include <cassert>
#include <vector>
#include <cstdio>
#include "../TeleRCBridge/FieldProtocol.h"
using Bytes=std::vector<uint8_t>;
Bytes frame(uint8_t id,Bytes q,uint8_t extra,uint8_t sys=255,uint8_t comp=190){
 Bytes p={254,uint8_t(q.size()),1,sys,comp,id};p.insert(p.end(),q.begin(),q.end());uint16_t c=65535;
 for(size_t i=1;i<p.size();++i){c=field::acc(c,p[i]);}
 c=field::acc(c,extra);p.push_back(c);p.push_back(c>>8);return p;
}
void real(Bytes&q,size_t at,float f){memcpy(q.data()+at,&f,4);}
Bytes command(unsigned cmd,float a,float b){Bytes q(33);real(q,0,a);real(q,4,b);q[28]=cmd;q[29]=cmd>>8;q[30]=1;q[31]=1;return frame(76,q,152);}
int main(){
 auto kind=[](const Bytes&p){return field::classify(p.data(),p.size(),1);};
 auto hold=command(176,1,4);assert(kind(hold)==field::Kind::HOLD);
 assert(kind(command(176,1,15))==field::Kind::REJECT);
 assert(kind(command(400,1,0))==field::Kind::REJECT); // ARM uses the existing normal-only path.
 assert(kind(command(183,9,1500))==field::Kind::SERVO);
 for(int ch=5;ch<=8;++ch)assert(kind(command(183,ch,1500))==field::Kind::REJECT);
 assert(kind(command(183,9,NAN))==field::Kind::REJECT);
 assert(kind(command(511,65,200000))==field::Kind::READ);
 assert(kind(command(511,65,1000))==field::Kind::REJECT);
 Bytes dest(35);real(dest,0,0.5f);real(dest,4,1);int32_t lat=136000000,lon=1232000000;
 memcpy(dest.data()+16,&lat,4);memcpy(dest.data()+20,&lon,4);dest[28]=192;dest[30]=dest[31]=1;dest[32]=6;
 assert(kind(frame(75,dest,158))==field::Kind::DESTINATION);
 real(dest,0,5);assert(kind(frame(75,dest,158))==field::Kind::REJECT);
 Bytes param(20);param[0]=param[1]=255;param[2]=param[3]=1;memcpy(param.data()+4,"FS_GCS_ENABLE",13);
 assert(kind(frame(20,param,214))==field::Kind::READ);
 param[2]=2;assert(kind(frame(20,param,214))==field::Kind::REJECT);
 hold.back()^=1;assert(kind(hold)==field::Kind::REJECT);
 auto other=command(176,1,4);assert(field::classify(other.data(),other.size(),2)==field::Kind::REJECT);
 field::Authority a;Bytes hb(9);hb[4]=10;hb[5]=3;hb[8]=3;
 auto f=frame(0,hb,50,1,1);a.observe(f.data(),f.size(),100);assert(a.fresh(100)&&!a.phoneAllowed(100));
 Bytes rc(42);rc[0]=1;rc[16]=2000&255;rc[17]=2000>>8;rc[40]=7;
 f=frame(65,rc,118,1,1);a.observe(f.data(),f.size(),101);assert(a.phoneAllowed(101));
 a.observe(f.data(),f.size(),500);assert(!a.phoneAllowed(851)); // Duplicate boot timestamp cannot refresh ownership.
 rc[0]=2;rc[16]=1000&255;rc[17]=1000>>8;f=frame(65,rc,118,1,1);a.observe(f.data(),f.size(),900);assert(!a.phoneAllowed(900));
 puts("PASS: field command bounds, CRC/target isolation and fresh physical CH7 gate");
}

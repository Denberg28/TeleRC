#pragma once
#include <TeleRCWire.h>
#include <mbedtls/md.h>
namespace telerc {
constexpr size_t AUTH_HEADER=12,AUTH_TAG=16,AUTH_MAX=AUTH_HEADER+RADIO_MAX+AUTH_TAG;
// magic/version, direction (1 rover poll / 2 base command), uint64 challenge LE, uint16 length LE, body, HMAC-SHA256/128.
inline size_t sealRadio(uint8_t kind,uint64_t token,const uint8_t*p,size_t n,const uint8_t*key,uint8_t*out){
  if(n>RADIO_MAX){return 0;}
  out[0]=0xd1;out[1]=kind;
  for(int i=0;i<8;++i){out[2+i]=uint8_t(token>>(i*8));}
  out[10]=uint8_t(n);out[11]=uint8_t(n>>8);
  if(n){memcpy(out+AUTH_HEADER,p,n);}
  uint8_t tag[32];
  if(mbedtls_md_hmac(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256),key,32,out,AUTH_HEADER+n,tag))return 0;
  memcpy(out+AUTH_HEADER+n,tag,AUTH_TAG);return AUTH_HEADER+n+AUTH_TAG;
}
inline bool openRadio(const uint8_t*p,size_t n,const uint8_t*key,uint8_t expectedKind,uint64_t &token,const uint8_t*&body,size_t &length){
  if(n<AUTH_HEADER+AUTH_TAG||n>AUTH_MAX||p[0]!=0xd1||p[1]!=expectedKind)return false;
  length=size_t(p[10])|(size_t(p[11])<<8);if(length>RADIO_MAX||n!=AUTH_HEADER+length+AUTH_TAG)return false;
  uint8_t tag[32];if(mbedtls_md_hmac(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256),key,32,p,AUTH_HEADER+length,tag))return false;
  uint8_t difference=0;for(size_t i=0;i<AUTH_TAG;++i)difference|=tag[i]^p[AUTH_HEADER+length+i];if(difference)return false;
  token=0;for(int i=0;i<8;++i)token|=uint64_t(p[2+i])<<(i*8);body=p+AUTH_HEADER;return true;
}
}

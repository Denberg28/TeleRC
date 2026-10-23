#pragma once
#include <vector>
#include <cstring>
#include <algorithm>
inline std::vector<unsigned char> preferenceBlob;
inline bool preferenceOpen=true,preferenceWrite=true,preferenceRemove=true;
inline unsigned preferenceWrites=0;
inline void resetPreferences() {
  preferenceBlob.clear(); preferenceOpen=preferenceWrite=preferenceRemove=true; preferenceWrites=0;
}
class Preferences {
public:
  bool begin(const char*,bool=false){return preferenceOpen;}
  void end(){}
  size_t getBytesLength(const char*){return preferenceBlob.size();}
  size_t getBytes(const char*,void *out,size_t n){
    size_t size=std::min(n,preferenceBlob.size()); memcpy(out,preferenceBlob.data(),size); return size;
  }
  size_t putBytes(const char*,const void *in,size_t n){
    if(!preferenceWrite)return 0;
    auto *data=static_cast<const unsigned char *>(in); preferenceBlob.assign(data,data+n); ++preferenceWrites; return n;
  }
  bool isKey(const char*){return !preferenceBlob.empty();}
  bool remove(const char*){if(!preferenceRemove)return false; preferenceBlob.clear(); return true;}
};

#pragma once
#include "Arduino.h"
struct Preferences {bool begin(const char*,bool){return true;}String getString(const char*,const char*){return "test-password-123";}void putString(const char*,String){}void end(){}};

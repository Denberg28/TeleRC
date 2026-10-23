#pragma once
#include "Arduino.h"
using TickType_t=uint32_t;
using TaskHandle_t=void*;
using portMUX_TYPE=int;
constexpr int portMUX_INITIALIZER_UNLOCKED=0, pdPASS=1;
#define portENTER_CRITICAL(x) ((void)(x))
#define portEXIT_CRITICAL(x) ((void)(x))
#define pdMS_TO_TICKS(x) (x)

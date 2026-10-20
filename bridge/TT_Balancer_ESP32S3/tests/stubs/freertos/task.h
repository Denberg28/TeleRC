#pragma once
#include "FreeRTOS.h"
inline int xTaskCreatePinnedToCore(void(*)(void*),const char*,unsigned,void*,int,TaskHandle_t*,int){return pdPASS;}
inline TickType_t xTaskGetTickCount(){return millis();}
inline void vTaskDelay(TickType_t ticks){delay(ticks);}
inline void vTaskDelayUntil(TickType_t *wake,TickType_t ticks){*wake+=ticks;delay(ticks);}

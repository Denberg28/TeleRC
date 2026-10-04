#pragma once
// Compileable, transmission-disabled defaults. Override with a sketch-local TeleRCLoRaConfigLocal.h.
#ifndef TELERC_RADIO_VARIANT
#define TELERC_RADIO_VARIANT 1262 // supported: SX1262=1262, SX1276=1276; SX1280 is not implemented
#endif
#ifndef TELERC_RADIO_PROVISIONED
#define TELERC_RADIO_PROVISIONED 0
#endif
#ifndef TELERC_RADIO_FREQUENCY
#define TELERC_RADIO_FREQUENCY 0.0f // Explicitly configure a permitted frequency for your installation.
#endif
#ifndef TELERC_RADIO_POWER
#define TELERC_RADIO_POWER 2 // low-power bench starting point, dBm
#endif
#ifndef TELERC_RADIO_KEY
#define TELERC_RADIO_KEY {0} // 32 independent random bytes, identical on both gateways
#endif
#ifndef TELERC_BASE_WIFI
#define TELERC_BASE_WIFI 0 // USB joystick input by default; LoRa is the rover control link
#endif
#ifndef TELERC_BASE_PASSWORD
#define TELERC_BASE_PASSWORD "" // blank => random password saved in NVS, printed once at startup
#endif

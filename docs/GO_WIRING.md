# TeleRC Go four-wheel firmware

Target: ESP32-S3 Dev Module, Arduino ESP32 core3.3.2, USB CDC on boot. Not an
ESP32-C3/ESP8266 or a LilyGO T3-S3 pinout. Four motor slots: LF, LR, RF, RR.

| Motor | MX1508 input pair | BTS7960 RPWM / LPWM | BTS R_EN + L_EN (same module) |
| --- | --- | --- | --- |
| Left front | GPIO4 /5 | GPIO4 /5 | GPIO16 |
| Left rear | GPIO6 /7 | GPIO6 /7 | GPIO17 |
| Right front | GPIO8 /9 | GPIO8 /9 | GPIO18 |
| Right rear | GPIO10 /11 | GPIO10 /11 | GPIO47 |

MX1508: two dual-channel boards for four motors. Use the MX1508 image only with
regulated motor power at or below5V and within the actual motor/driver ratings.
**Do not connect a fully charged2S or3S battery directly to the MX1508/TT motors.**
The profile's242/255 PWM cap is not voltage regulation. Reverse a motor through
the MOTOR_INVERT entry in Config.h after a raised-wheel direction check.

BTS7960: one module per motor. Packaged BTS images declare a12V maximum supply
and12V motors; use only appropriately rated motors/drivers and wiring. They
assume verified3.3V-compatible module inputs or installed level conversion. The
compile-time `TELERC_BTS_LOGIC_VERIFIED=1` is a build declaration, not a physical
measurement. Rebuild for other supply/motor ratings. Never tie enable outputs
between different modules. Common logic ground, suitable fuse/cutoff and a
separate regulated logic supply are required; do not power motors from the ESP32.

Optional ELRS: receiver TX → GPIO39; common ground; receiver-rated power and
3.3V UART. Normal non-inverted CRSF420000 baud. GPIO39 is reserved in ELRS builds.
CH1 steer, CH2 centered drive, CH5 ARM, CH6 STOP(high). BOOT/GPIO0 is local stop;
status LED/GPIO21 indicates arm/stop state. Test with wheels restrained.

Binary folders: `MX1508-ELRS0`, `MX1508-ELRS1`, `BTS7960-ELRS0`, `BTS7960-ELRS1`.
ELRS0 is phone-only Wi-Fi. ELRS1 adds receiver control and explicit phone takeover.
All builds use Wi-Fi AP TeleRC-MiniRover, default192.168.4.1 UDP14550, one peer.
Set a private password before deployment. There is no LoRa or mission firmware.

Use Arduino IDE to compile/upload the included sketch with the matching flags,
or flash the generated **merged.bin at0x0** if that file is present in the selected
folder. Otherwise use its ESP32-S3 image set: bootloader.bin at0x0,
partitions.bin at0x8000, boot_app0.bin at0xe000 (if present), and application.bin at
0x10000. Check actual filenames in the folder and do not substitute images from
a different build. Firmware version is1.0.0-go-four-wheel. Hardware/pin/power and
physical stop behavior remain unmeasured here; compilation is software evidence.

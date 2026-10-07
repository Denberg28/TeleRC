# TeleRC 0.8.53 firmware installation

The firmware ZIP contains ten compiled configurations, `MANIFEST.json` with the exact source commit/FQBN/flags/image offsets and hashes, and internal `SHA256SUMS.txt`. The matching bridge ZIP contains sketches, shared library and wiring/provisioning guides. Use the APK and firmware from the same candidate; actual USB/RF/driver behavior still requires restrained-wheel acceptance.

## Choose the correct folder

| Folder | Device / purpose |
| --- | --- |
| `motor-esp32s3` | Fixed motor ESP32-S3; four BTS7960 outputs, F405 M5–M8 inputs and LoRa gateway UART; use this for the combined installation |
| `lora-base-sx1262` / `lora-rover-sx1262` | LILYGO T3-S3 with actual SX1262 radio; BASE connects to phone USB, ROVER connects to motor UART |
| `lora-base-sx1276` / `lora-rover-sx1276` | Same roles for actual SX1276 boards |
| `pwm-converter-paired` | Separate FC-PWM-only companion; two LEFT/RIGHT inputs; no LoRa UART decoder |
| `pwm-converter-four-input` | Separate FC-PWM-only companion; independent M5–M8 inputs; no LoRa UART decoder |
| `wifi-gateway` | Removable dedicated Wi-Fi gateway; connect instead of the rover T3-S3 |
| `legacy-hybrid` / `legacy-bridge` | Retained combined Wi-Fi motor / FC bridge architectures; not the dedicated LoRa motor path |

Do not flash the PWM-only companion onto the combined motor MCU if you need LoRa UART commands. SX1278, SX1280 and LR1121 are not supported. Match the installed radio and antenna band before transmitting. BASE Wi-Fi is disabled in the packaged LoRa builds. No private key/frequency is compiled into the release: blank gateways remain radio-inactive until USB provisioning; an existing valid NVS profile is retained.

## Board profiles and source builds

Pinned tools: ESP32 Arduino core 3.3.2 and RadioLib 7.2.1. Motor/legacy/Wi-Fi/PWM binaries use ESP32S3 Dev Module, 4 MB default partitions, PSRAM disabled, DIO flash, hardware CDC/JTAG with USB CDC enabled. They require a compatible ESP32-S3 with at least 4 MB flash; a different flash/partition or USB/PSRAM requirement needs a source rebuild.

T3-S3 binaries use the core's **LilyGo T3-S3** board, matching SX1262/SX1276 Revision, 4 MB default partitions, QSPI PSRAM enabled, DIO flash and hardware CDC/JTAG with USB CDC enabled. The actual FQBN/flags are in the manifest. These are built for the reviewed ESP32-S3FH4R2 T3-S3 configuration, not every LILYGO product. GPIO43/44 remain the rover UART.

For source uploads, install `bridge/libraries/TeleRCLink` into the Arduino libraries folder and open the selected sketch folder. Select the same board/profile/revision and leave Erase All Flash disabled. Set `TELERC_RADIO_VARIANT=1276` for the SX1276 source builds (or use the provided example local configuration), and `TELERC_PWM_PAIRED=0` only for the four-input PWM companion. Never commit private configuration/keys. Frequency/power/key can be provisioned through the APK on blank radios.

## Flash the separate images without whole-chip erase

1. Download the APK, bridge source ZIP, firmware ZIP and top-level SHA256SUMS from the same release. Verify SHA-256 before flashing. After extraction, verify the firmware ZIP's internal checksums (`sha256sum -c SHA256SUMS.txt` on Linux, or compare `Get-FileHash` on Windows).
2. Disconnect motor battery power. Power only the board being flashed from a suitable USB connection; avoid backfeeding another supply. Close Serial Monitor, phone connection and relay tools. Verify the board's port and role.
3. Use Espressif esptool (v5 command names below). Change into the selected target folder. Each folder has its sketch application, bootloader, partition table and `boot_app0.bin`. Write these at the manifest offsets, without `erase-flash` or an erase-all flag.

Example for the **motor** on Windows; replace COM7 with its actual port:

```powershell
python -m esptool --chip esp32s3 --port COM7 --baud 460800 write-flash 0x0 TeleRCMotorController.ino.bootloader.bin 0x8000 TeleRCMotorController.ino.partitions.bin 0xe000 boot_app0.bin 0x10000 TeleRCMotorController.ino.bin
```

For BASE use `TeleRCLoRaBase.ino` in the selected BASE radio folder; for ROVER use `TeleRCLoRaRover.ino` in its ROVER folder. Replace the basename in all three sketch image filenames. Linux uses an observed port such as `/dev/ttyACM0`. If the board does not enter download mode automatically, use its documented BOOT/RESET procedure; retry at lower baud if needed.

This writes the default-partition boot/application regions and leaves NVS sectors untouched. It preserves pairing/settings **when the existing partition layout matches**. Back up needed settings first; if the old layout differs, rebuild/plan migration before flashing. Merged whole-flash images are deliberately excluded because they can overwrite pairing NVS. Do not use a merged image or full-chip erase as an ordinary update. These flash commands are documented procedures, not hardware-tested uploads in this session.

## Reconnect, provision and verify

Flash the dedicated motor and intended BASE/ROVER pair, keeping exactly one gateway attached to motor UART. Follow `LORA_INTEGRATION.md` and `docs/LORA_MOTOR_REVIEW.md` in the repository for wiring: rover TX43→motor RX21, rover RX44←motor TX39, common ground, 115200/3.3 V; F405 M5–M8→GPIO4–7; drivers GPIO8–15; GPIO16 selects DIRECT/AUTOPILOT.

Manually install the signed 0.8.53 APK over 0.8.52. Package/signing identity is retained and versionCode increases to 71. On blank radios, attach each board separately by native USB: read inactive settings, save the same permitted frequency/power/32-byte key, receive Save ACK, restart and read back. Active radios reject changes. Do not erase a working NVS profile just to update the app/firmware. Reattach BASE to the phone, verify local active state and fresh rover heartbeat, hold neutral, explicitly Enable/ARM, and test all four directions/loss/recovery with wheels raised.

## Rollback and evidence

Retain the 0.8.52 APK and matching bridge source. Rebuild/reflash its intended firmware with the correct physical board settings, motor power removed and NVS retained. Android normally blocks downgrade from versionCode 71 to 70; uninstalling deletes app data, so export routes/settings first. Preserve the existing signing identity and never use a different signer to force an update.

`BUILD_INFO.txt` binds the published APK/firmware to the final source/run and records signing continuity, test/lint and target-build results. These are reviewed, simulator-tested and build-verified artifacts. Phone installation, real native USB, radio timing, driver electrical behavior, stopped-wheel timing and field operation remain NOT TESTED/BLOCKED. Complete the executable bench procedure before ground operation or stable promotion.

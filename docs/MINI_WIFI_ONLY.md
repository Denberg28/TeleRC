# TeleRC Mini Rover: Wi-Fi only

Android 0.8.55 (versionCode 73) pairs with standalone ESP32-S3 firmware 1.2.0. This is a test candidate; physical stopping, installation, power/noise immunity and touch behavior require the bench checks below. It is an intentional Wi-Fi-only edition. Original multi-link functionality remains in release 0.8.53.

## Hardware and wiring

Path: Android TeleRC → Wi-Fi → ESP32-S3 → motor drivers → motors. No receiver, LilyGO, flight controller, UART bridge or PWM converter is required. Remove old receiver/control connections before installing this setup. One Wi-Fi phone is allowed. The protocol is a MAVLink subset, not an exclusive cryptographic identity for the Android app; other clients are outside this edition's tested scope.

Default: four separate motor channels using **two MX1508 dual driver boards**, compatible with the existing mini rover wiring. Edit `HardwareProfile.h` before upload for two-motor builds (`TELERC_MOTOR_COUNT=2`, only LF/RF slots active) or another driver. Profiles support MX1508, DRV8833, TB6612FNG, BTS7960 and L298N electrical interfaces; they do not identify the hardware automatically or validate its current rating. Other profiles use the extra pins in that header; the table here is the default MX1508 configuration only.

| Motor | ESP32-S3 GPIO → MX1508 inputs |
| --- | --- |
| Left front | 4 / 5 |
| Left rear | 6 / 7 |
| Right front | 8 / 9 |
| Right rear | 10 / 11 |
| Status LED (optional ordinary external LED) | 21 → 330 Ω resistor → LED anode; cathode → GND |
| Local STOP | Onboard BOOT button, GPIO0 to GND |

Supply motor drivers from a motor supply matched to the motors and drivers. Supply the ESP32-S3 through a regulated 5 V source; join grounds. Never connect motor battery voltage to ESP32 GPIO or the board's 5 V input. Default declared motor supply is 5 V maximum, motors rated 6 V; change profile declarations to match actual hardware. `MAX_PWM=180/255` limits duty, not current or supply voltage. Check wheel rotation on raised wheels; use `MOTOR_INVERT` per slot if needed. Default onboard steering gain is 50%; app sliders independently scale steering/drive from 25–100%.

## Upload and connect

1. Arduino IDE: install **esp32 by Espressif 3.3.2**, select **ESP32S3 Dev Module**, USB CDC On Boot **Enabled**. Open `TeleRC_MiniRover_WiFi/TeleRC_MiniRover_WiFi.ino`; keep all its header files together. No external sketch libraries are needed.
2. Upload with motor power disconnected. Connect regulated logic power; boot output is zero and state is disarmed. Firmware binaries in `firmware/` are for the default four-motor MX1508 profile; changed profiles require compiling the source again. Prefer Arduino IDE upload; do not flash an application binary at address zero.
3. Install `TeleRC-v0.8.55-WiFi-only.apk`. It retains TeleRC's application ID and signing identity, so it replaces the multi-link edition. Retained LoRa/GPS settings are unused. Keep the old installer for rollback.
4. On the phone join **TeleRC-MiniRover**, password **telerc123**. Accept “Stay connected” / “Use without internet”. The app binds its UDP socket to Wi-Fi, including when mobile data remains on.
5. Return to TeleRC and tap **Connect**. Wait for rover-confirmed **DISARMED**. Both centered control channels stream at 10 Hz. After fresh neutral dwell, **ARM** becomes available.
6. Tap **ARM**, wait for **ARMED**, then use left steer/right drive. Releasing a stick returns it to neutral. **DISARM** and **STOP / DISARM** revoke controls immediately and request rover disarm; confirmation comes from rover heartbeat.
7. Leaving/backgrounding the app closes the session and requests disarm. Wi-Fi/command loss disarms independently in the rover. Reconnect and ARM again explicitly. Holding BOOT is local software STOP; do not hold BOOT during power-up unless entering bootloader.

SSID/password are in `Config.h`; edit before upload if wanted. UDP is fixed at `192.168.4.1:14550`, with local phone port 14550. Keep other TeleRC apps closed. Arming requires a fresh centered CH1/CH2 stream for at least 250 ms; app uses 300 ms. No forced-arm bypass or ARM retries are used.

## Defined behavior and limits

- Rover control watchdog: 500 ms since last valid CH1/CH2 frame. Discovery/telemetry never renew axis freshness. Wi-Fi station loss, disconnect, release or BOOT stops and disarms. Every recovery needs explicit ARM.
- App heartbeat timeout: 1200 ms. Expiry revokes authority before accepting recovery telemetry and closes the session. No automatic reconnection or re-arm.
- Motor reversal inserts 30 ms at zero. Driver duty is forced to zero when disarmed, not a command to hold 1500 µs. The default MX1508 stops with both inputs low; actual coast/brake behavior and stop distance require measurement.
- WPA Wi-Fi password restricts association. Endpoint, target IDs and CRC are checked. MAVLink commands are unsigned and carry no secure creation timestamp; a trusted/on-network sender can forge or replay valid frames. This edition does not claim cryptographic command authentication or general replay prevention.
- Power sag, noise and wiring faults are not fixed by software. No brownout immunity or field reliability is claimed.
- Changes are reversible by reinstalling the retained 0.8.53 edition and its matched firmware. Android may require uninstalling the newer APK before a lower-version rollback; uninstalling removes app data.

## Raised-wheel verification

Keep an independent motor-power cutoff within reach. Record board/driver/motor/supply, app and sketch versions.

1. Boot/reset with no phone and with displaced sticks: all PWM remains zero until neutral then explicit ARM.
2. Connect with mobile data enabled; verify DISARMED confirmation, ARM and DISARM feedback, directions, two independent sliders, and spring return on lift/cancel/multitouch.
3. While driving, press DISARM and STOP separately. Check PWM goes zero, heartbeat confirms disarmed, and holding/moving sticks cannot resume motion.
4. While driving, turn Wi-Fi off, background, lock phone, force-stop the app and reset the ESP32 in separate trials. Measure watchdog output-zero threshold against the last command (provisional criterion ≤550 ms for rover command loss). Measure mechanical stopping separately.
5. Restore Wi-Fi with displaced sticks: reconnect remains disarmed; centering and explicit ARM are required. BOOT while driving stops; release alone does not restart.
6. Confirm one-phone ownership, rejection of malformed/partial/wrong-target commands, 100 connect/arm/disarm cycles and 30 minutes of restrained operation. Record any reset, lag or unexpected input.

Gate A source/build, B behavior, C regression, D integration and F package require final cloud build evidence. Host C++ tests include watchdog/no-rearm, arm gating, explicit disarm, release, peer loss, malformed datagrams, reversal, rollover and Kotlin-generated protocol vectors. Gate E hardware/installation remains BLOCKED until these procedures pass on the actual rover. Do not promote to stable on software checks alone.

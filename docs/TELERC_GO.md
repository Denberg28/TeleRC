# TeleRC Go 1.0.0

Setup, Controls and Test. Wi-Fi and ELRS. A separate app named **TeleRC Go**
(package `io.github.denberg28.telerc.go`); the full TeleRC installation and its
settings remain intact. No missions, Balance, LoRa, maps, location services,
camera SDKs or app installer permission. Offline Test retains twenty practice
levels and the same spring-return controls.

## Connect and operate

1. Power the matched four-wheel rover. Join **TeleRC-MiniRover** in Android Wi-Fi
   settings (default password **telerc123**); choose stay connected without
   internet. Use a private AP password in Config.h for deployment.
2. Setup → choose the link → Connect. Controls opens. Wait for the rover's
   heartbeat, then **Enable**, center both sticks until **ARM**, and tap ARM.
   Driving unlocks only after the rover reports ARMED. CH1 steers; CH2 drives.
3. Release a joystick to center its axis. **STOP / Disarm** clears app authority,
   sends neutral, disarm and release. Disconnect, page changes and backgrounding
   revoke driving. Recovery reconnects the transport; **Enable and ARM remain
   deliberate**. There is no background driving or replayed ARM command queue.

Connection details holds numeric IPv4, rover/phone ports and system ID. Normal
four-wheel defaults are 192.168.4.1, UDP14550 both ends, system1. Only one app or
PC may own that phone UDP port and the firmware peer lease. Close the other
controller first. Sensitivity is independent for steering and drive.

## Wi-Fi and ELRS profiles

| Profile | What controls the rover |
| --- | --- |
| Wi-Fi · four-wheel rover | Phone → standalone ESP32-S3 → four motor drivers |
| ELRS · four-wheel receiver | ELRS handset → CRSF receiver → ESP32-S3; phone uses the rover Wi-Fi to observe or deliberately take control |
| Wi-Fi · ArduRover | Phone → existing transparent FC Wi-Fi bridge → ArduRover; configure its RC override permission and independent failsafes |
| ELRS · TX Backpack | MAVLink telemetry via Backpack; the handset drives. Phone ARM/drive/STOP are intentionally unavailable on this handset-only link |

A phone has no native ELRS RF radio. The four-wheel ELRS receiver profile uses
normal non-inverted CRSF, receiver TX → **GPIO39**, common ground, receiver-rated
power and 3.3V UART. GPIO39 is RX-only at420000 baud. CH1 steer, CH2 centered
forward/reverse, CH5 ARM, CH6 STOP(high). Use100–250Hz ELRS packet rate. Bind the
handset/receiver with matching band, firmware and private binding phrase.

For transmitter arming, keep CH5 LOW and sticks centered for at least0.3s, then
switch HIGH. CH6 STOP or lost RC stops and disarms. Recovery requires fresh
centered LOW then HIGH; a held-high ARM switch cannot restart the rover. The
handset continues without a phone/Wi-Fi observer. Phone takeover first stops and
disarms; then Enable/neutral/ARM is deliberate. Phone release/loss never transfers
a moving command to the handset. CH6 STOP dominates phone and RF control while
received; the BOOT button is the independent local stop input.

For ArduRover Wi-Fi, use MANUAL mode for direct driving, CH1/CH2 mapping,
SYSID_MYGCS/MAV_GCS_SYSID255, finite RC_OVERRIDE_TIME and measured FC RC/GCS
failsafes. Keep the receiver override-enable switch available. For native
Backpack use MAVLink-capable matched ELRS TX/RX and Backpack telemetry Wi-Fi,
default10.0.0.1, send14555/listen14550. The receiver serial mode differs from the
standalone CRSF profile. See the full TeleRC ELRS guide for FC wiring/configuration.

## Reliability and limits

One socket worker owns sends, with no motion/ARM retry queue. The latest command
slot is bounded and expires within500ms. Valid source IP/port, vehicle system,
autopilot component, MAVLink checksum and ground-rover type are required. Wi-Fi
socket binding avoids mobile-data routing. Retry is bounded to one attempt per
second; stale heartbeats, changed Wi-Fi, pause and Test invalidate authority.
Neutral arming dwell counts successfully sent centered frames, not UI position.
The paired Go firmware advertises a per-boot nonce; a changed nonce cancels app
authority and old ARM intent. Legacy standalone firmware without this nonce
remains locked; flash the matching four-wheel image. ArduRover phone control
requires MANUAL and the configured ground-rover identity.
Receiver ownership must remain fresh before phone drive on ELRS firmware.

Standalone motor command loss disarms after500ms from the last accepted RC;
ELRS RC age expires at300ms. Heartbeat freshness in the app is1500ms. These are
software thresholds, not measured physical stopping distances or latencies.
Discovery/diagnostics never refresh motor commands. CRC/endpoint checks are not
cryptographic authentication: use the isolated trusted AP and bound ELRS link.
No internet-exposed control ports or shared organization Wi-Fi are qualified.

## Firmware and installation

Download the signed APK and the four-wheel firmware ZIP from **go-v1.0.0**.
Install Go alongside full TeleRC. The firmware ZIP includes four actual compiled
ESP32-S3 configurations, a sketch, binary hashes and wiring guidance. Select the
correct driver and Wi-Fi/ELRS mode before flashing; **GO_WIRING.md** is required.
The full app/old firmware are not overwritten automatically.

This release is **owner-selected stable distribution**, requested21 October2026.
Publication requires software/native/actual-target checks on the final source.
Physical phone, RF, driver power, motor direction and ground/field operation are
not tested here. Gate E remains BLOCKED; “stable” is the requested release
channel, not a claim of measured physical reliability or security certification.

Before ground use: disconnect motor power for wiring; verify driver supply and
logic levels; securely support the chassis with wheels off the floor. Check
individual wheel directions, small drive/steer commands, neutral, STOP, Wi-Fi
loss, transmitter loss, CH6, BOOT and no automatic rearm after recovery. Stop on
any unexpected output. Record phone/board/driver/configuration and stop times.

Rollback: retain full TeleRC0.8.89 and the previous firmware. Go is a separate
package, so uninstalling it does not delete full TeleRC data. Restore the previous
matching firmware image/configuration with power removed from the motor supply.

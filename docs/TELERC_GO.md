# TeleRC Go 1.0.3

Wi-Fi-only rover controller. One landscape control screen: **Phone connect**,
**Settings**, and one **ARM / DISARM** button. No Phone control button, STOP,
Use ELRS, setup/test tabs or transport dropdown. The upper-left **TeleRC Go v1.0.3** text checks for stable Go updates when tapped.
Status and ARM/DISARM sit at the lower center. Content-sized popups use equal
spacing, a two-column connection form and separate footer actions, with no scroll
containers. Settings contains only Wi-Fi
settings, Connection details, Sensitivity and Done. The supplied ship background
and transparent joystick panels remain; the navy power mark is inset in its icon.

## Connect and drive

1. Flash the matching **Go 1.0.2** standalone firmware from this release. Choose
   your driver and ELRS0 (no receiver) or ELRS1 (optional CRSF receiver) image.
   Standalone Go ARM requires the new ownership/capability telemetry. Older Go
   firmware may be observed but cannot arm from this app.
2. Join **TeleRC-MiniRover** Wi-Fi, default password **telerc123**. Tap Phone
   connect and wait for telemetry. For ArduRover, join its existing FC Wi-Fi bridge;
   the app detects the verified ground-rover heartbeat automatically.
3. Center both joysticks and tap ARM once. This explicit request confirms phone
   authority, transmits centered frames for at least 300 ms, then sends normal ARM.
   The complete transaction expires after 2 s. It never bypasses rover/FC checks.
   Driving unlocks only when telemetry confirms ARMED. During the transaction the
   same button becomes Cancel arming; after confirmation it becomes DISARM.
4. DISARM clears phone drive and sends neutral plus normal disarm/release on the
   standalone rover. ArduRover receives neutral, HOLD and normal disarm.
   Settings, backgrounding, disconnect, stale telemetry, reboot and radio takeover
   cancel pending ARM. Recovery never enables, arms or resumes driving automatically.

Connection details retains IPv4, remote/phone UDP ports and vehicle system ID.
Default 192.168.4.1, remote 14550, phone 14550, system 1. If another controller owns
that phone port, Go closes its failed socket and binds an independent temporary
port. New Go firmware replies to the actual source port, while retaining one
exclusive peer lease. This does not let two apps control one rover. Close full
TeleRC/other controllers before operation; an existing peer lease must expire.
Legacy FC Wi-Fi bridges may require source port 14550, so close the conflicting
app before connecting to those bridges. No socket reuse or cellular fallback.

## RadioMaster Pocket Switch A

A physical switch name does not identify an RC channel. In your EdgeTX model,
assign **SA to CH7**, using a two-position mix with endpoints near 1000/2000 µs.
Reverse the mix if necessary to obtain these observed outputs:

| CH7 / SA assignment | Authority |
| --- | --- |
| HIGH, >1800 µs | Phone permitted; tap ARM deliberately |
| LOW, <1300 µs | Transmitter selected; phone commands blocked |
| Middle / invalid | Standalone firmware inhibits both sources |

Optional standalone receiver: receiver TX → ESP32-S3 GPIO39, common ground,
receiver-rated power and 3.3 V UART. Use normal CRSF 420000 baud and the **ELRS1**
firmware. CH1 steer, CH2 centered forward/reverse, CH5 ARM, CH6 STOP(high), CH7 SA.
Use matching-band/bound ELRS TX/RX; the phone always connects through rover Wi-Fi.

Selecting radio stops and disarms the standalone rover before changing authority.
Center the handset axes, hold CH5 LOW for at least 0.3 s, then switch CH5 HIGH to
arm. A held-high ARM switch cannot restart motion at handover or after RF loss.
CH6 STOP and the rover BOOT stop remain available although the app STOP button
was removed. Returning SA to phone also stops/disarms; tap app ARM to return.
Phone pause/disconnect/settings while observing RF do not send actuator commands
that could interrupt radio operation. Delayed phone RC, TAKE, DISARM and disconnect
packets cannot override the receiver-side SA selection. Wi-Fi loss never hands
an old motion command to the transmitter.

## ArduRover via the same Wi-Fi screen

The existing FC Wi-Fi bridge must transparently forward MAVLink, including
HEARTBEAT, COMMAND_ACK and RC_CHANNELS. ArduRover must be in MANUAL for phone
steering. Configure CH1/CH2 mapping, GCS system255, finite override timeout and
independent RC/GCS failsafes for the installed vehicle.

Map SA to CH7 and set **RC7_OPTION=46 (RC Override Enable)**: high enables GCS RC
overrides; low disables them so the handset takes control. This is the FC's
hardware-side arbitration. Go does not silently set flight-controller parameters
or infer a physical SA mapping. Verify the channel and function in Mission Planner.
Official reference: https://ardupilot.org/rover/docs/common-auxiliary-functions.html

The app requests RC_CHANNELS at 5 Hz and requires fresh CH7 telemetry for phone
ARM/control. Low or unknown CH7 cancels phone authority and pending ARM, without
sending HOLD/DISARM into the observed transmitter handover. An FC source may
already be armed; center its handset before changing SA. FC takeover behavior and
its arming/failsafes differ from standalone firmware and must be measured on the
actual configuration. After app DISARM/HOLD, select MANUAL deliberately before
trying to arm again. No autonomous missions or TX Backpack phone transport.

## Manual Go updates

Tap the version text at the upper left while the phone has internet access.
Opening the update sheet stops phone control and cancels pending ARM. The check
reads only `go-vX.Y.Z` tags, skips draft/prerelease releases and matches the exact
TeleRC Go APK asset; the full TeleRC Latest release is never used. A newer
verified release offers Download or Cancel. Download disconnects phone control.
The app checks the SHA-256 digest, declared size, Go package identity, increasing
version code, matching version name and installed signing certificate before
opening Android's installer. Android requires install-from-this-source permission
and its normal installation confirmation. No silent installation or auto-rearm.

If rover Wi-Fi has no internet, connect the phone to an internet-capable network
and tap the version again. A check does not send rover commands over cellular.
Closing the sheet does not authorize a later download prompt. Keep TeleRC Go open
for the download/verification/installer flow; closing the activity cancels an
unfinished download. Physical phone permission/installer acceptance remains to
be checked on the actual device.

## Release evidence and limits

This is the **owner-requested stable distribution channel**. Software checks,
native emulator fault tests, firmware host/default sanitizer checks, four actual
ESP32-S3 builds, signing continuity, increasing code 4 and package/checksum checks
must pass before publication. Source is pinned in BUILD_INFO and the release tag.
Stable distribution is not a claim of completed physical phone, RF, motor, power
or field validation; those measurements remain NOT TESTED/BLOCKED.

Trusted isolated Wi-Fi only. Default AP password should be personalized before
shared deployment. MAVLink CRC/source checks are not cryptographic authentication
or replay protection. Software timing thresholds are not measured stop distances.
No control-source selection should be trusted before restrained-wheel testing.

Install APK over Go 1.0.2, retaining the same package/signer and sensitivity.
This app-only update retains standalone Go 1.0.2 firmware unchanged. When
upgrading older standalone firmware, use the paired Go 1.0.2 firmware first;
preserve pin, driver, motor supply and inversion configuration. ArduRover keeps its existing
firmware and requires the explicit SA/CH7 setup above. Full TeleRC is separate.

Before ground operation: support the chassis, use reduced sensitivity and a
reachable independent motor-power cutoff. Verify each wheel direction, ARM,
DISARM, cancel while arming, settings/background/reconnect, Wi-Fi loss, SA takeover
and return, CH5 rearm dwell, CH6, BOOT and receiver loss. Confirm no auto-rearm.
Record phone/FC/receiver/board/driver versions and actual stop times.

Rollback: retain Go 1.0.2 APK and its matching standalone firmware/configuration.
Android downgrade normally needs uninstall and loses Go data; export settings
first. Do not erase receiver bindings or motor configuration merely to roll back.

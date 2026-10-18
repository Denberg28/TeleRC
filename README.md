# Compact Setup and mission actions — 0.8.82

Top-bar Guide, fixed compact Setup, clear transmitter/phone handoff, Mission start with checks and confirmation, Waypoint F1–F3 assignment, and concise Help & safety. App-only test candidate; no reflash. [Scope, qualification and phone acceptance](docs/COMPACT_TABS_082.md).

# TeleRC 0.8.81 — ELRS recovery test candidate

Backpack registration continues through stale F405 telemetry, and removed Android Wi-Fi networks trigger bounded socket rebinding. UDP-open, live and stale telemetry states are distinct. Handset authority stays exclusive; native phone driving/arming/Waypoint commands remain locked. App-only; no firmware reflash. [Setup, verification gates, installation and raised-wheel checks](docs/ELRS_RECOVERY_081.md). Final workflow/BUILD_INFO supplies software evidence; physical phone/RF/FC HIL remains BLOCKED.

# F405 ELRS and clear Waypoint flow — 0.8.80 development

Setup distinguishes Mini Wi-Fi from ArduRover Wi-Fi, LoRa and ELRS TX Backpack. FC ELRS guides cover receiver/transmitter wiring and CRSF phone/handset handover. Native Backpack supports telemetry with separate send/listen ports and handset authority; phone commands are locked to avoid competing RC overrides. Waypoint uses Select → Check rover → Review & confirm, visible Undo/Clear and a fixed STOP. [Compatibility, gates and hardware checks](docs/ELRS_WAYPOINT_080.md). Development source; hardware validation remains blocked.

# Waypoint tab — 0.8.79

An isolated Controls-style Waypoint tab adds confirmed single destinations, saved multi waypoint routes and phone-supervised ArduRover Guided sequencing with fresh navigation/parameter checks and confirmed HOLD handling. Setup/Controls/Test bodies, manual protocol, FPV and firmware stay unchanged. Existing mini/filtering gateways are planning-only: live execution requires a separately validated compatible transparent MAVLink link and FC GPS/compass/EKF. [Scope, software gates, compatibility and restrained-wheel acceptance](docs/WAYPOINT_079.md). Physical navigation and full SITL integration remain NOT TESTED/BLOCKED; no stable promotion.

# Practice worlds and accessories — 0.8.78

Setup now keeps its version at lower center. Offline Test adds City/Forest/Desert/Space environments in five-level groups, land/sea/air/space vehicle silhouettes and stackable cosmetic accessories instead of purchased colors. Existing colors return their spent coins; vehicles, unique coin claims and course records remain. [Scope, validation, phone acceptance and wallet-compatible rollback](docs/PRACTICE_WORLDS_078.md). App-only test candidate; no firmware reflash.

# Stable theme transitions and compact navigation — 0.8.75

Light/Sepia/Dark now change in place without Activity recreation, connection teardown or reloading the cached wallpaper. Accent changes preserve endpoint drafts; reselecting the active option does nothing. Tab menus use compact 40 dp rows, scaled for system text and aligned to their button. [Release scope, A–F gates, evidence, installation and rollback](docs/STABLE_075.md). Owner-authorized stable channel; physical phone/Skydroid/rover validation remains BLOCKED. App-only; no reflash.

# Sharp labels, aligned tabs and wallpaper opacity — 0.8.74 test build

Replaces blurred joystick shadows with crisp text on compact caption strips, aligns menu widths/edges with tab buttons, and shares consistent gaps/top/footer sizing across all tabs. Setup's Opacity preview saves 0–100% wallpaper opacity for Controls/Test (default 70%); Setup remains clear. [Scope, gates, phone checks and rollback](docs/LABELS_LAYOUT_074.md). App-only, no reflash. Final workflow/BUILD_INFO supplies software evidence; phone/OEM/HIL remains BLOCKED. No stable promotion.

# Clear wallpaper and collapsible Setup — 0.8.73 test build

Removes the 53% wallpaper tint on all tabs and the rectangular joystick-panel fills in Controls/Test. Setup **− / +** hides/restores both panels, keeping the existing connection/update controls in the bottom corners and preserving draft inputs/state. Practice popups are sized/centered before opening with no window animation. [Scope, gates, phone checks and rollback](docs/WALLPAPER_SETUP_073.md). App-only, no reflash. Final workflow/BUILD_INFO supplies software results; physical phone/OEM/HIL checks remain BLOCKED. No stable promotion.

# Flat Test popups — 0.8.72 test build

Restart, Levels and Market now use small headings, flat rows, quiet dividers and short text actions. Market has Vehicles/Colors tabs, smaller previews and a compact balance; restart drops the boxed statistics and long instructions. All actions retain 48 dp touch targets. [Scope, A–F gates, phone checks and rollback](docs/MAZE_DIALOGS_072.md). Gameplay, wallet/prices, FPV/photo and firmware retained; no reflash. Final workflow/BUILD_INFO supplies software evidence; phone/HIL remains BLOCKED. No stable promotion.

# Practice dialogs and version — 0.8.71 test build

Levels and the three-bump restart popup now use padded, rounded themed layouts, scrolling content and fixed full-width actions. Level cards show current status and best times; restart separates time/bumps and confirms coins are kept. Test's bottom text is now the TeleRC version. [Scope, A–F gates, phone checks and rollback](docs/MAZE_DIALOGS_071.md). App-only, no reflash; Market/wallet, FPV/photo and game behavior retained. Final workflow/BUILD_INFO supplies software evidence; phone/HIL remains BLOCKED. No stable promotion.

# Maze Market and coins — 0.8.70 test build

Market replaces the Test page's OFFLINE control; its button shows the saved coin balance. Collect randomly placed gold coins to unlock cosmetic vehicles and maze palettes. Fixed lifetime quotas total 220 coins against 180 for all paid items; retry/reload cannot duplicate rewards. Opening Market pauses practice. [Use, prices, A–F gates, phone acceptance and rollback](docs/MAZE_MARKET_070.md). App-only, no reflash. Final workflow/BUILD_INFO supplies software results; phone/HIL remains BLOCKED. No stable promotion.

# Persistent wall-bump dots — 0.8.69 test build

Counted maze impacts leave opaque red dots with white outlines on the struck wall. They do not fade; pause/resume and navigation retain them for the current attempt. Restart/level changes clear them. [Scope, A–F evidence, phone checks and rollback](docs/MAZE_BUMPS_069.md). Android-only; fixed levels, FPV/photo and firmware retained. Final workflow/BUILD_INFO supplies software results; phone/HIL remains BLOCKED. No stable promotion.

# Fixed maze levels — 0.8.68 test build

Twenty repeatable levels replace random shuffle. The single Lv/time header and taller courses expand the maze; END is the farthest passage from START. Three separate wall bumps stop the run and prompt a restart. Saved unlocks, replay and level times preserve legacy records. [Use, A–F gates, phone acceptance and rollback](docs/MAZE_LEVELS_068.md). Android-only; FPV/photo features retained, no firmware reflash. Final workflow/BUILD_INFO supplies software evidence; phone/rover HIL remains BLOCKED. No stable promotion.

# FPV recovery and photo theme — 0.8.67 test build

Hold Connect FPV for advertised video formats (including YUYV compatibility) and diagnostics. Bounded USB recovery, explicit permission gating, single preview ownership and background resume preserve disabled control. Setup → Background → Choose image adds a private offline photo; Remove image restores the plain tone. See [use, gates, acceptance and rollback](docs/FPV_THEME_067.md). Physical Skydroid/video compatibility remains BLOCKED; source and software checks do not establish clean RF/video. Firmware unchanged; no reflash.

# Skydroid USB FPV fix — 0.8.66 test build

USB video attachment registration now allows Android to offer TeleRC as a default handler; cold/warm USB launches open FPV with control disabled. Hold Connect FPV for receiver VID/PID, interfaces and permissions. No USB enumeration and connected non-UVC devices have distinct messages. See [setup, evidence, phone checks and rollback](docs/SKYDROID_FPV_066.md). Firmware unchanged. Phone/Skydroid HIL remains BLOCKED; this is a review/test artifact, no stable promotion.

## 0.8.65 — thinner maze walls and a readable rover

Offline Test now uses thin continuous walls and a much larger top-view rover with four wheels, windshield, yellow front bar, white outline and rotating arrow. Heading is visible beside the maze time information. Collision geometry matches the drawn walls; existing Classic clear times are preserved separately from best times for the new geometry. [Setup, acceptance and rollback](docs/READABLE_ROVER_065.md). App-only candidate; no firmware reflash. v0.8.64 remains Latest stable.

## 0.8.64 navy, offline Test and mini 95% output

The Blue preference now displays as Navy. Open the page dropdown → Test for offline maze practice with the Controls joysticks, shuffle/retry and saved clear times. Test disconnects the live link and requires explicit Connect when returning. Mini rover 1.2.3 caps MX1508 PWM at 242/255; it must be flashed separately to apply that ceiling. App sensitivity still multiplies it. See [setup, acceptance and rollback](docs/NAVY_MAZE_064.md). Owner-requested Latest stable channel promotion: [release and qualification](docs/STABLE_064.md). Phone/rover HIL remains pending.

# TeleRC 0.8.62 — original colors, slightly deeper shades

Restore the pre-compact palette with strong colored action buttons, white labels and slightly deeper background shades. Keep the current compact Setup/Controls layout. Former Pastel becomes Light; explicit Light/Sepia/Dark settings persist. See [install and verify](docs/CLASSIC_COLORS_062.md). App-only signed test candidate; no firmware reflash.

# TeleRC 0.8.61 — pastel themes

Pastel backgrounds, softly colored buttons and light panels now cover Setup and Controls. The old Dark setting switches to Pastel once; later theme choices remain saved. Blue, mint Teal, lavender Violet, Slate, Light, warm Sepia and optional Dark remain available. See [installation and verification](docs/PASTEL_THEME_061.md). No firmware reflash needed; signed test candidate pending phone/rover acceptance.

# TeleRC 0.8.60 — muted themes and balanced Setup

Setup now uses two centered, equal columns sized to their contents, with aligned rows and subdued tonal buttons. Light, Sepia and Dark plus all four accents remain available. Controls, settings and firmware behavior are preserved. See [installation and verification](docs/MUTED_SETUP_060.md). Signed test candidate; physical phone/receiver/rover checks remain pending.

# TeleRC 0.8.59 — compact UI

Setup and Controls are the two pages. Choose Light, Sepia or Dark in Setup. Larger joysticks, central USB FPV/map swapping, separate sensitivity/expo, Mini rover Wi-Fi and USB LoRa operation are retained. Test drive/game and LoRa provisioning pages are removed; older descriptions below document historical versions. Route export is in Setup → Diagnose link. See [installation, validation and limits](docs/COMPACT_UI_059.md). No firmware update is needed. Signed test candidate; physical acceptance remains pending.

# TeleRC 0.8.58 — larger joysticks and USB FPV

Built on released 0.8.57/code75: independent steering/drive sensitivity and expo, Mini rover Wi-Fi and ARM gate, Wi-Fi/FC and LoRa USB choices are retained. Adds larger joystick controls and a USB UVC FPV preview with Show camera / Show map buttons below the center view. See [USB FPV setup and checks](docs/USB_FPV.md). Version 0.8.58/code76 is a development/test update; receiver/phone/hub HIL remains pending. No firmware reflash is required.

# TeleRC 0.8.57 — separate sensitivity and expo

Setup → **Control response** now provides independent Steering and Drive sensitivity plus Expo. Existing sensitivity migrates to both axes with expo 0%. See [usage, compatibility and checks](docs/CONTROL_RESPONSE_057.md). No firmware reflash is needed. The 0.8.56 restoration notes below are retained for context.

Standalone MX1508 mini rover: [firmware 1.2.2 response candidate](docs/MINI_MX1508_122.md) raises MX drive/steering duty and moves PWM to 20 kHz. Keep APK 0.8.56; upload sketch + all headers. The original 1.2.0 setup below is historical; use the candidate guide for current mini firmware.

Restores the pre-simplification TeleRC interface: Setup, Controls, Map/Test drive, LoRa setup, themes, diagnostics, servo assignments and normal connection choices. Adds the Mini rover Wi-Fi preset and a fresh-neutral ARM gate for the existing standalone mini rover Wi-Fi sketch 1.2.0. ARM/DISARM follows rover heartbeat confirmation. No other controls or workflows have been redesigned.

Install this signed code 74 update over 0.8.55/code 73; the app ID and signing identity are retained. Choose Setup → Mini rover Wi-Fi, join TeleRC-MiniRover (telerc123), Connect → Controls → ENABLE CONTROL → center until ARM · READY → ARM. The mini sketch does not change; no reflash is needed if 1.2.0 is already installed. See [mini Wi-Fi setup](docs/MINI_ROVER_WIFI.md) and [verification](docs/TESTING.md). Hardware/installation checks remain pending.

Offline Android rover controller using a native USB OTG LoRa base link or a legacy bidirectional MAVLink UDP bridge. Android 8+, IPv4. The app contains no cloud service or account. The first enabled craft profile is **Rover**; multirotor, fixed wing, watercraft, and rocket are named future profiles and have no active controls. The large control above DRIVE is a dedicated **ARM / DISARM** button whose label follows the armed state reported by MAVLink HEARTBEAT. F1/F2/F3 are reserved for external servo output assignments.

Setup and Controls share compact navigation and cards. Setup uses rounded connection inputs; Controls centers Enable/Stop between equal-width Steer and Drive panels. When live control is enabled, that center card shows a MapLibre dead reckoning map. Its cyan path estimates motion using only CH1/CH2 frames actually sent to the rover and the Test vehicle maximum speed and pivot turn rate. Purple marks measured rover GPS. The overlay reports estimated displacement from its anchor, heading, and speed. A current rover fix anchors the estimate once, and later GPS exposes model drift without silently correcting it. Before GPS, the map clearly labels its sample start; a precise phone fix establishes fixed Home. Stop, heartbeat loss, or app pause freezes the estimate and leaves the recovery map available; leaving Controls still stops command output. On reopening Controls, the last measured GPS and saved estimated path remain visible. The recenter button prioritizes the last measured rover GPS, then estimated position, then Home. Last GPS age is shown; an old fix is not a current rover location. It does not change MAVLink command output. The estimated path is saved separately from measured GPS telemetry and is cleared by Test Reset or when a new measured rover fix replaces an unconfirmed anchor.

## Rover setup

1. Configure the bridge to forward complete MAVLink packets in both directions on one configured UDP port, with HEARTBEAT at least once per second. The bridge must send from the same IP **and port** entered in TeleRC. Default is 192.168.4.1:14550. The phone binds the same UDP port locally.
2. Configure ArduRover default RC input mapping, steering CH1 and bidirectional throttle CH2, with 1500 µs neutral for each. Verify the vehicle's motor directions, deadband, `RC_OVERRIDE_TIME`, GCS/RC failsafe, and independent emergency stop before connecting. TeleRC does not configure these parameters.
3. Raise wheels off the floor for initial checks. Connect phone to bridge Wi-Fi. In portrait Setup & Config, enter the endpoint and tap Connect. Open landscape Controls, then tap **Enable control** after a checksum-valid heartbeat. Both joystick knobs move freely in a circle. Only the horizontal axis of the STEER stick feeds CH1; only the vertical axis of the DRIVE stick feeds CH2. Off-axis movement stays neutral, and both assigned channels return to 1500 when released.
4. Changing tabs, leaving the app, losing heartbeat, or losing fresh live-control packets revokes joystick authority and enters neutral hold. The ESP32-S3 bridge refreshes 1500 µs neutral at 10 Hz after a 500 ms live-control timeout. **STOP CONTROL** is a deliberate handover to the physical receiver. **Disconnect** first neutralizes, then asks the bridge to release RC override and clear phone pairing; if that final message is lost, the bridge remains in neutral hold. Vehicle and hardware failsafes remain essential.
5. Use the dedicated **ARM / DISARM** button only after a fresh heartbeat is present. TeleRC sends standard MAVLink arm/disarm commands and waits for HEARTBEAT to confirm the actual state before changing the label. ArduRover safety checks remain in force.

If Setup remains at **WAITING FOR HEARTBEAT**, tap **Diagnose link** four seconds after Connect. With the matching ESP32 bridge, Setup can identify a missing bridge reply, zero bytes from the F405 UART, incomplete MAVLink frames, or frames without a valid autopilot heartbeat. Diagnostics do not send rover commands. The previous bridge firmware does not provide these counters.
TeleRC binds the UDP socket to the connected Wi-Fi network so Android mobile data and no-internet network switching cannot silently route rover packets away from the ESP32. If Wi-Fi is unavailable, Connect fails instead of falling back to cellular. TeleRC keeps that bound UDP session alive across tab changes, temporary Android backgrounding, and Network-capability churn. The ESP32 bridge starts its SoftAP and UDP socket once and does not restart or rebind them for normal phone association changes. Heartbeat loss disables live control without forcing a reconnect. Tab changes and app backgrounding command neutral while preserving telemetry transport; the explicit Disconnect action additionally sends a bridge disconnect message, clears phone pairing, closes the Android UDP socket, and disables auto-reconnect.

## Offline test drive

The third, landscape **Test drive** page has Game and Test modes selected with the compact bottom switch. Game is an endless retro obstacle course with a looping melody controlled by the ♫ button, a motor effect while driving or steering, and a collision sound; use the phone media volume; its scrolling road continues smoothly when a run resets. In Test, the MapLibre map shows actual rover `GLOBAL_POSITION_INT` telemetry as a purple line and phone GPS fixes as a separate blue line. The top-down rover icon points in the direction reported by MAVLink heading, including while turning in place; when heading is unavailable, a regular marker is shown instead. The first precise phone GPS fix (50 m accuracy or better) becomes **Home** and never moves until Reset; later phone fixes extend only the blue branch. The Home button recenters the map. Pinch to zoom, drag to pan, rotate the map, or tap the MapLibre compass to return to north. Phone location is requested only while the Test map is visible and the app is foregrounded. Rover points are accepted only from the configured bridge endpoint and heartbeat system with valid MAVLink CRC and a fresh link. No rover GPS telemetry means no measured rover line or purple icon. A labeled cyan offline rover previews the Test joystick position and heading at fixed GPS Home. Until a precise fix arrives, the app centers a clearly marked sample map location in Naga without setting Home or recording any GPS position. Tap the map badge to enter a vehicle name, maximum speed (m/s), and pivot turn rate (degrees/s); the cyan estimated route uses those inputs and is never recorded as real rover telemetry. MapLibre renders OpenFreeMap Liberty street tiles using OpenStreetMap data; tiles require internet access, while recorded routes stay local and can be exported even when tiles cannot load.

The Controls page records CH1/CH2 **frames actually sent** while live control is enabled. Navigating to Test disables live control as before; Test joysticks never command the vehicle. The TEST SIM/MAP button restores the offline differential-drive preview; it remains an illustrative model, not measured rover motion. Test shows counts for phone positions, rover positions, and sent controls. **CSV** exports all three streams through Android's document picker. The route is retained in private app storage across restarts; **Reset** clears it and sets the next good phone fix as a new Home. The phone position is never used as a substitute for rover GPS.

**Private bench/SITL tests only.** UDP endpoint and checksum checks do not authenticate a vehicle. TeleRC disables live joystick authority on app/heartbeat loss, while the matching ESP32-S3 bridge enters and maintains neutral-hold after 500 ms without fresh live-control packets. These software layers are not a substitute for ArduRover's own failsafe/override timeout, receiver failsafe, or an independent hardware motor cutoff. TeleRC provides standard MAVLink ARM/DISARM control but no MAVLink signing, flight-mode control, or vehicle configuration. Bench-test every stop and arm/disarm path before powering the wheels.

## Architecture and future craft profiles

`CraftProfile` identifies available profiles. `RoverControls` owns the rover RC channel mapping and neutral values. `Mavlink` frames the transport-independent channel output; `MainActivity` owns transport lifecycle and UI. Each future craft requires its own mapping, neutral/failsafe behavior, interaction design, and bench/SITL evidence before enabling. Rocket control requires a separate safety design and is explicitly unavailable.

The LoRa architecture uses **phone joystick → USB OTG → base LILYGO T3-S3 → LoRa → rover T3-S3 → UART → dedicated motor ESP32-S3 → four BTS7960 drivers**. In Setup, select **LoRa via USB base** and grant USB permission. Both base and rover Wi-Fi are disabled in the default LoRa configuration. Native ESP32-S3 CDC ACM is supported; USB-UART adapters and BLE are not. PC TeleRC uses the included local USB relay. Read [the wiring/provisioning guide](bridge/LORA_INTEGRATION.md) before flashing. This is a bench-test integration; range and stopping performance are unverified.

Legacy Wi-Fi UDP remains selectable for existing bridge installations. ELRS itself is not a phone transport.

## Acceptance criteria

- Invalid IPv4 address and port are rejected; valid endpoint persists locally.
- No override is sent until a checksum-valid heartbeat arrives from the configured IP and source port **and** the user enables control.
- Default rover mapping sends CH1 steering, CH2 bidirectional drive, CH3/CH4 neutral; CH5–8 ignored; at most 10 Hz.
- Both joysticks center on touch release. Tab change and app pause revoke joystick authority into neutral hold. Normal heartbeat jitter below 4 seconds retains the session; a heartbeat timeout revokes it and clears both axes before processing recovery telemetry. A fresh verified heartbeat restores link status; driving requires another press of Enable Control. The bridge's 500 ms live-control timeout independently enters its safe state. STOP CONTROL deliberately hands authority back to the receiver. Explicit Disconnect neutralizes first and lets the bridge own the final receiver-release transition.
- Other craft profiles cannot be selected or operated. The Test map and offline test course cannot send vehicle commands; only actual transmitted controls enter the CSV command stream.

## Build and status

JDK 17 and Android SDK platform 35 plus Build Tools 35.0.0 are required. The checked-in Gradle wrapper pins 8.11.1 and verifies the distribution SHA-256. On Windows, install JDK 17 and Android Studio with Android SDK Command-line Tools, open PowerShell in the project, and run `powershell -ExecutionPolicy Bypass -File .\scripts\setup-windows.ps1 -InstallSdkPackages`. For an already installed SDK use `powershell -ExecutionPolicy Bypass -File .\scripts\setup-windows.ps1`. The script detects the standard `%LOCALAPPDATA%\Android\Sdk` location or `ANDROID_HOME`, checks the toolchain, and builds the debug APK. On Linux/macOS, set `ANDROID_HOME`, run `./scripts/check-android-env.sh`, then `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`. APK path: `app/build/outputs/apk/debug/app-debug.apk`.

The wrapper needs access to `services.gradle.org` and its GitHub release redirect; dependency resolution additionally needs Google Maven, Maven Central, and the Gradle Plugin Portal. GitHub Actions uses the same pinned wrapper and checks for SDK packages before building and publishing. This workspace's network blocks the Gradle distribution and lacks an Android SDK, so local compilation here requires infrastructure network and SDK access; the connected GitHub Actions runner already provides both. Debug APK signing is only suitable for private testing; retain a stable signing key for future upgrades. Preserve application ID `io.github.denberg28.telerc` and version progression. Test with SITL and a bridge, then raised-wheel bench testing, before any further vehicle use.

## Direct updates

Setup & Config has a manual Check for updates button. It reads public GitHub release metadata without a credential, verifies the downloaded APK digest, package, increasing version and installed signing certificate, then opens the system installer. Play Protect may recommend scanning an APK distributed outside Google Play; accept the scan and review its verdict. The map tile provider receives tile requests for the displayed area when internet is available, even though routes remain local. See [UPDATES.md](UPDATES.md) for signing secrets and first-install constraints. Signed releases are published by the repository workflow using existing signing secrets.

Setup includes a saved 25–100% joystick sensitivity slider for Controls and Test. It scales channel displacement around 1500 µs; at 100% the live rover command range is 1100–1900 µs to match the checked-in RC calibration. Reset clears route history and immediately requests a fresh phone GPS fix to establish Home again; stationary fixes are eligible. Recenter requests wait for map readiness. Map panning remains manual after recentering; measured rover GPS and phone Home remain separate.

## Swappable control link and LoRa setup

The dedicated **LoRa setup** tab provisions blank BASE/ROVER boards over native USB, reads their real local radio settings and counters, and retains a masked pairing key for the second board. Saving requires restart; active radios reject configuration changes. New installations retain Wi-Fi as their initial transport. The new `TeleRCWiFiGateway` lets a command ESP32-S3 use the same framed UART connector as the rover T3-S3 while the dedicated PWM ESP32-S3 remains fixed. See [the swappable gateway guide](bridge/SWAPPABLE_GATEWAYS.md) for the keyed harness, carrier compatibility, provisioning and bench checks.

## LoRa setup review (7 October 2026)

The reviewed tab follows a device → radio/pairing → link-check sequence inspired by Meshtastic's setup workflow. Connect exactly one native USB T3-S3; the app automatically reads its role and local settings. Set the permitted frequency/power and shared 64-digit hex key on each inactive board, save, restart, and read back. A BASE with an active radio and fresh rover heartbeat unlocks the Controls shortcut; Enable Control/ARM remain explicit. ROVER USB is for local provisioning only.

The compact tab uses centered, equal-width landscape columns (stacked below 640 dp), 12 dp horizontal/10 dp vertical card padding, 8 dp card gaps, persistent field labels, and 48 dp controls. Pairing-key Show/Hide preserves the caret; the key is hidden again on pause/navigation/disconnect. Generate asks before replacing an existing draft. Drafts remain in memory while navigating; only a board-confirmed Save updates the private phone pairing settings. Replies time out after 3 seconds; reconnect after a missing reply before retrying.

The profile remains fixed at SF7/500 kHz/CR4:5. Region tables, configurable modem presets, BLE, Meshtastic channel QR import and mesh routing are not implemented. The USB workflow needs no Wi-Fi password; the pairing key authenticates TeleRC's direct radio packets and does not encrypt payloads. Protocol and firmware are unchanged. This source review does not publish or change the installed 0.8.52 APK. See [review and device checks](docs/LORA_SETUP_REVIEW.md).


# Waypoint tab — 0.8.79

An isolated Controls-style Waypoint tab adds confirmed single destinations, saved multi waypoint routes and phone-supervised ArduRover Guided sequencing with fresh navigation/parameter checks and confirmed HOLD handling. Setup/Controls/Test bodies, manual protocol, FPV and firmware stay unchanged. Existing mini/filtering gateways are planning-only: live execution requires a separately validated compatible transparent MAVLink link and FC GPS/compass/EKF. [Scope, software gates, compatibility and restrained-wheel acceptance](WAYPOINT_079.md). Physical navigation and full SITL integration remain NOT TESTED/BLOCKED; no stable promotion.

# Practice worlds and accessories — 0.8.78

Vehicle categories and Armament/accessories are offline cosmetics; all retain rover maze handling and the same collision envelope. They do not enable real sea/air/space control or firing. v2 wallet saves cannot be read by an unmodified 0.8.77 parser; rollback needs a forward-version compatibility build that preserves v2 claims and refunds unsupported cosmetics. Phone installation, OEM/font/orientation rendering and rover/receiver HIL remain BLOCKED. See [acceptance/rollback](PRACTICE_WORLDS_078.md).

# Persistent FPV limits — 0.8.77

Android controls USB defaults and grants. TeleRC cannot set Always silently; choose it in the system chooser when offered. Automatic reconnect reuses actual grants and otherwise waits for explicit Connect. A physical detach, revoked/one-time CAMERA permission or OEM default handling can still require approval. VID/PID cannot distinguish two identical receivers without serial identity, so ambiguous matches are not opened. Native cleanup timing, power loss, actual UVC/RF stability and phone chooser behavior are unmeasured; run [acceptance](FPV_PERSISTENT_077.md). Retry frequency is bounded, not a guarantee of recovery or glitch-free RF.

# Stable theme transitions and compact navigation — 0.8.75

Light/Sepia/Dark now change in place without Activity recreation, connection teardown or reloading the cached wallpaper. Accent changes preserve endpoint drafts; reselecting the active option does nothing. Tab menus use compact 40 dp rows, scaled for system text and aligned to their button. [Release scope, A–F gates, evidence, installation and rollback](STABLE_075.md). Owner-authorized stable channel; physical phone/Skydroid/rover validation remains BLOCKED. App-only; no reflash.

# Sharp labels, aligned tabs and wallpaper opacity — 0.8.74 test build

Replaces blurred joystick shadows with crisp text on compact caption strips, aligns menu widths/edges with tab buttons, and shares consistent gaps/top/footer sizing across all tabs. Setup's Opacity preview saves 0–100% wallpaper opacity for Controls/Test (default 70%); Setup remains clear. [Scope, gates, phone checks and rollback](LABELS_LAYOUT_074.md). App-only, no reflash. Final workflow/BUILD_INFO supplies software evidence; phone/OEM/HIL remains BLOCKED. No stable promotion.

# Clear wallpaper and collapsible Setup — 0.8.73 test build

Removes the 53% wallpaper tint on all tabs and the rectangular joystick-panel fills in Controls/Test. Setup **− / +** hides/restores both panels, keeping the existing connection/update controls in the bottom corners and preserving draft inputs/state. Practice popups are sized/centered before opening with no window animation. [Scope, gates, phone checks and rollback](WALLPAPER_SETUP_073.md). App-only, no reflash. Final workflow/BUILD_INFO supplies software results; physical phone/OEM/HIL checks remain BLOCKED. No stable promotion.

# Flat Test popups — 0.8.72 test build

Restart, Levels and Market now use small headings, flat rows, quiet dividers and short text actions. Market has Vehicles/Colors tabs, smaller previews and a compact balance; restart drops the boxed statistics and long instructions. All actions retain 48 dp touch targets. [Scope, A–F gates, phone checks and rollback](MAZE_DIALOGS_072.md). Gameplay, wallet/prices, FPV/photo and firmware retained; no reflash. Final workflow/BUILD_INFO supplies software evidence; phone/HIL remains BLOCKED. No stable promotion.

# Practice dialogs and version — 0.8.71 test build

Levels and the three-bump restart popup now use padded, rounded themed layouts, scrolling content and fixed full-width actions. Level cards show current status and best times; restart separates time/bumps and confirms coins are kept. Test's bottom text is now the TeleRC version. [Scope, A–F gates, phone checks and rollback](MAZE_DIALOGS_071.md). App-only, no reflash; Market/wallet, FPV/photo and game behavior retained. Final workflow/BUILD_INFO supplies software evidence; phone/HIL remains BLOCKED. No stable promotion.

# Maze Market and coins — 0.8.70 test build

Market replaces the Test page's OFFLINE control; its button shows the saved coin balance. Collect randomly placed gold coins to unlock cosmetic vehicles and maze palettes. Fixed lifetime quotas total 220 coins against 180 for all paid items; retry/reload cannot duplicate rewards. Opening Market pauses practice. [Use, prices, A–F gates, phone acceptance and rollback](MAZE_MARKET_070.md). App-only, no reflash. Final workflow/BUILD_INFO supplies software results; phone/HIL remains BLOCKED. No stable promotion.

# Persistent wall-bump dots — 0.8.69 test build

Counted maze impacts leave opaque red dots with white outlines on the struck wall. They do not fade; pause/resume and navigation retain them for the current attempt. Restart/level changes clear them. [Scope, A–F evidence, phone checks and rollback](MAZE_BUMPS_069.md). Android-only; fixed levels, FPV/photo and firmware retained. Final workflow/BUILD_INFO supplies software results; phone/HIL remains BLOCKED. No stable promotion.

# Fixed maze levels — 0.8.68 test build

Twenty repeatable levels replace random shuffle. The single Lv/time header and taller courses expand the maze; END is the farthest passage from START. Three separate wall bumps stop the run and prompt a restart. Saved unlocks, replay and level times preserve legacy records. [Use, A–F gates, phone acceptance and rollback](MAZE_LEVELS_068.md). Android-only; FPV/photo features retained, no firmware reflash. Final workflow/BUILD_INFO supplies software evidence; phone/rover HIL remains BLOCKED. No stable promotion.

# FPV recovery and photo theme — 0.8.67 test build

Hold Connect FPV for advertised video formats (including YUYV compatibility) and diagnostics. Bounded USB recovery, explicit permission gating, single preview ownership and background resume preserve disabled control. Setup → Background → Choose image adds a private offline photo; Remove image restores the plain tone. See [use, gates, acceptance and rollback](FPV_THEME_067.md). Physical Skydroid/video compatibility remains BLOCKED; source and software checks do not establish clean RF/video. Firmware unchanged; no reflash.

# Skydroid USB FPV fix — 0.8.66 test build

USB video attachment registration now allows Android to offer TeleRC as a default handler; cold/warm USB launches open FPV with control disabled. Hold Connect FPV for receiver VID/PID, interfaces and permissions. No USB enumeration and connected non-UVC devices have distinct messages. See [setup, evidence, phone checks and rollback](SKYDROID_FPV_066.md). Firmware unchanged. Phone/Skydroid HIL remains BLOCKED; this is a review/test artifact, no stable promotion.

# Readable maze rover 0.8.65

Baseline stable 0.8.64/code82; candidate 0.8.65/code83. [Scope, A–F gates, acceptance and rollback](READABLE_ROVER_065.md). Thin wall drawing and collision share rectangles; the larger oriented rover uses the earlier top-view design. Old records remain Classic; new geometry has separate best times. Required cloud Android/native/host/sanitizer/parser/signing/package checks remain pending until the final workflow succeeds. Phone readability and physical HIL remain BLOCKED. App-only: firmware unchanged, no affected ESP32 target. Latest stable 0.8.64 stays retained. Next check: phone upgrade and four-direction/reverse/wall-contact practice in all backgrounds.

# v0.8.64 release-channel status

The owner requested Latest stable promotion of the already qualified v0.8.64 build. See [channel promotion, evidence and remaining limits](STABLE_064.md). The channel designation supersedes older candidate-only wording for this release. Physical phone installation, receiver/driver compatibility and restrained-wheel HIL remain pending; no field-validation claim. Existing APK/sketch/source/evidence/checksums and build tag remain unchanged.

# Navy / offline maze / 95% mini candidate

Baseline app 0.8.62/code80 `bc7e983b88f2deea28aaf7c81e5ecffc159da949`; candidate 0.8.64/code82 + standalone mini 1.2.3. Scope, acceptance, installation and rollback: [NAVY_MAZE_064.md](NAVY_MAZE_064.md).

A source/build: reviewed; local Android/actual ESP32 toolchains BLOCKED; cloud Android and six pinned ESP32-S3 builds REQUIRED. B behavior: maze generator/collision/timing/record and 95% PWM regressions plus native three-page lifecycle/theme checks REQUIRED. C regression: local host/profile/sync PASS; final cloud default sanitizers REQUIRED. D software integration: Android packets through mini parser REQUIRED; actual transport/driver integration NOT TESTED. E physical HIL/phone installation: BLOCKED; use the executable restrained-wheel sequence in the guide. F delivery: retained signer, code82, matching source/sketch/binaries/evidence/checksums and prerelease REQUIRED. A successful final workflow and BUILD_INFO supersede pending software statuses only. No physical/field/stable claim.

Test disables auto-connect and disconnects live authority; returning to Controls needs explicit Connect. Practice motion/time is approximate and active-time based. The firmware 242/255 ceiling multiplies app sensitivity: Drive100 gives OUT242; Drive95 gives OUT229. Other driver profiles retain their old limits. Physical stop/current/supply validation remains pending. Next check: install on phone, verify offline isolation with raised wheels, and measure PWM/stop/recovery.

# Verification limits and next work

- **Local Android environment — BLOCKED:** this workspace lacks the Android SDK and cannot download Gradle from `services.gradle.org`. Initial local unit/build/lint checks could not run; authorized cloud build evidence is recorded in the final release `BUILD_INFO.txt`. Verify API 26/33+ receiver behavior, queued ARM cancellation, and heartbeat recovery on devices even after cloud checks pass.
- **Local actual firmware toolchain — BLOCKED:** Arduino CLI/ESP32 toolchain is absent here. Host stubs do not establish target compilation. Updated pinned cloud CI/release gates compile seven primary sketches, two alternate LoRa builds and the four-input PWM converter. Review CI results are recorded on the draft PR; release results appear in `BUILD_INFO.txt` only after an authorized release.
- **BLOCKED — HIL:** no attached phone, T3-S3 pair, motor MCU, F405, drivers or motors. USB attachment, electrical levels, radio profile, timing, stopping, resets, power supply and repeated takeover need measured bench evidence.
- **NOT TESTED — PC integration:** preserve sparse MAVLink inputs, but validate the actual Windows client/relay, wheel removal and release-to-transmitter workflow.
- **Reviewed limitation — freshness/security:** Wi-Fi/legacy MAVLink lacks signing and a command timestamp/replay window. A trusted endpoint's duplicate/delayed valid frames can renew accepted-command freshness. LoRa's one-use challenge limits radio response replay, but USB/UART input is physically trusted. Do not characterize Wi-Fi as authenticated or full-stack replay protected.
- **Reviewed limitation — updater:** `/releases/latest` excludes prereleases. New test candidates require manual installation; candidate creation must not replace the latest retained stable artifact. The update receiver fix is source-reviewed; actual download/install/signing continuity remains untested.
- **Reviewed limitation — physical safe state:** zero PWM or neutral override does not prove coast/brake behavior, stop distance or an autonomous FC stop. Document each installed mode's expected output and measure it.

Next: verify the final release `BUILD_INFO.txt` and checksums, then run `docs/TESTING.md` on raised wheels. Package/publish a signed test candidate only after required automated and packaging checks pass. Stable promotion remains blocked by HIL and installation/compatibility checks.


0.8.53 software-build update: release source `0f7272da0d30b398dfe13c0a1008afca93f24c5c` passed run 37619553457, including retained signing identity, increased code 71, Android tests/debug/release lint/build, full host sanitizers and ten packaged firmware configurations. Actual T3-S3 builds use manufacturer SX1262/SX1276 revisions with QSPI PSRAM/native CDC. This resolves cloud software/packaging gates; local infrastructure limitations and installed-device, native USB, RF/driver timing, PC and HIL limitations remain. Download checksums and BUILD_INFO from v0.8.53; follow `bridge/FIRMWARE_FLASHING.md` before flashing.


0.8.58 USB FPV: based on released v0.8.57, retaining separate sensitivities/expo and Mini rover Wi-Fi/ARM behavior. Only USB UVC receivers are supported. Phone/receiver/powered-hub integration, physical installation, visual/device QA and RF/latency behavior remain NOT TESTED. USB frames do not prove healthy FPV RF reception. Default local leak inspection is blocked by the runtime; the cloud workflow retains default ASan/UBSan/leak checks before prerelease publishing. No stable promotion. See USB_FPV.md.

# TeleRC 0.8.60 — muted themes and compact Setup

Android versionCode 78, signed test candidate based on 0.8.59 commit `450fdb86331ffad27c8d8b2720abfe3702fa4dfc`.

## Changes

- Replace saturated solid fills with muted Blue, Teal, Violet and Slate tones; soften Light, Sepia and Dark surfaces. Text remains distinct and primary actions use a stronger tonal fill.
- Center a bounded Setup panel with two equal columns and matching row heights. Cards follow content height instead of stretching to fill the display.
- Pair transport/background, endpoint/accent, Connect/control response, link status/response summary, and diagnostics/update actions across aligned rows.
- Keep equal-width Setup/Controls navigation at the top. Reduce corner rounding and remove button/card shadows. Scroll on small windows or large fonts.
- Preserve Wi-Fi/USB LoRa, FPV/map swapping, control response settings, arming, authority loss and all firmware/protocols. No firmware reflash needed.

## Installation and acceptance

Install TeleRC-v0.8.60.apk over the existing signed app without uninstalling or clearing data. Light/Sepia/Dark, chosen accent, control response and connection preferences persist.

Inspect all 12 theme/accent combinations on the phone. Check that the two Setup panels have equal width/height, aligned rows and no large empty card area. At large font/display sizes confirm scrolling reaches every setting. Connect/disconnect, open diagnostics/export, edit response settings, then verify Controls → Enable → centered ARM with raised wheels and a motor-power cutoff available. Verify stick release, STOP, DISARM, tab change, background and link loss; reconnect must not re-enable control automatically. Confirm camera/map swap and actual receiver/hub behavior independently.

## Verification and limits

Required build gates: unit tests, debug/release lint and builds, firmware host/default sanitizer regressions, Android-to-mini parser integration, retained signer/version, native/ZIP 16 KB alignment and checksums. API 35 emulator captures 12 native Setup variants and asserts equal panel bounds and visible main actions; large-font capture is included for review. Screenshots/XML and reports are in the evidence ZIP. Emulation does not establish actual phone/USB/RF or rover behavior.

The Actions artifact storage quota affected only the redundant copy after successful 0.8.59 publication. This workflow publishes APK/source/evidence/checksums as release assets and omits that duplicate copy; no stored artifacts are deleted.

Phone installation/visual acceptance, receiver/hub integration and rover HIL are NOT TESTED by this build. No stable promotion. Firmware remains the installed matching version. Software source and APK are bound to the release commit; see BUILD_INFO.txt and SHA256SUMS.txt. Rollback must use a higher-code signed restoration to preserve data because Android rejects a normal downgrade. Next check: compare this candidate with the submitted Setup screenshots on the user's phone.

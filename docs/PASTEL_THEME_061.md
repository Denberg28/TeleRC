# TeleRC 0.8.61 — pastel themes

VersionCode 79, based on installed 0.8.60/code78, source `c3a7aff56912eed000066ed8f3a7f8cb80f31f7f`.

Pastel is the new default: powder-blue backgrounds, near-white panels, soft tonal buttons and slate text. Choose Blue, Teal (mint), Violet (lavender) or Slate accents; each also tints the pastel background. Light and warm Sepia are brighter. Idle joystick knobs are pastel; enabled knobs retain a stronger color. The launch background is light.

On first launch after upgrading, an existing Dark background switches to Pastel once. Existing Light/Sepia selections persist. Dark remains selectable; a later explicit Dark choice persists on restart. Accent, connection, route and response settings are retained. Setup geometry, map/FPV, CH1/CH2, arming, transport, watchdogs and all firmware remain compatible. No sketch upload is needed.

## Install and inspect

Install TeleRC-v0.8.61.apk over the current signed app without uninstalling or clearing data. Setup should show **Background · Pastel** for the old Dark setting shown in the user's screenshot. Try Blue, Teal, Violet and Slate; inspect Controls for the same light surface and soft joystick styling. Change the background, restart and confirm persistence. Recheck Setup symmetry, large-font scrolling, text readability and response settings.

With wheels raised and a motor cutoff available, connect the installed rover, enable control, center and arm; verify both axes and release, STOP, DISARM, tab change, background and link loss. Recovery must require explicit enable/arm. Check map/camera swap with the actual receiver separately.

## Verification

Required cloud gates: JVM tests (including migration and subsequent Dark persistence), debug/release lint/build, full firmware host/default sanitizer regression, actual mini-parser integration, signing continuity against 0.8.60, increasing versionCode, ZIP/native 16 KB alignment and checksums. Native API 35 checks attempt all 16 theme/accent combinations on Setup and Controls, equal Setup panel geometry, visible actions, light pastel screenshot brightness, one-time Dark migration, later explicit Dark persistence and large-font capture. Native app/assertion failure blocks publication; emulator startup failure is marked BLOCKED without a visual pass claim. Review BUILD_INFO.txt and the evidence ZIP for actual results.

Local sync and legacy/mini host checks pass. Local ASan/UBSan pass with leak inspection disabled because this runtime cannot inspect /proc tasks; the cloud run must pass the default leak-enabled suite. Local Android build is BLOCKED by unavailable Gradle network/SDK. A manual calculation checks pastel foreground/background contrast at 4.5:1 or higher; native captures and phone review remain separate checks.

Signed software test candidate; installed-phone visual acceptance, USB receiver integration and physical rover HIL remain pending. A higher-code signed restoration is needed for rollback without clearing data; normal APK downgrades are rejected. Retain 0.8.60 and export routes before any uninstall. Next check: compare the Pastel Setup and Controls screens on the user's phone with their submitted charcoal screenshots.

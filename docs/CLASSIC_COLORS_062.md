# TeleRC 0.8.62 — original colors, slightly deeper shades

Signed test candidate, code80, based on v0.8.61/code79 source `0428b6f08b895e53aeefc9c660a7fa1f4a54b900`.

Restore the palette from before the compact Setup update: v0.8.58 source `2228f1ea8315ef25311965230f693eebdc19a6ba`. Bring back strong Blue/Teal/Violet/Slate action fills with white labels, lightly tinted secondary buttons and the original joystick color treatment. Deepen light/sepia surfaces slightly and use deeper filled-button shades. Dark-mode text retains the earlier brighter accent for legibility against darker fills. Button text contrast checks across themes/accents remain at least 4.5:1.

Only appearance is restored. Keep the current compact Setup/Controls layout, dimensions, padding, buttons, map/FPV, sensitivity/expo and all control/transport behavior. Keep Light, Sepia and Dark; former Pastel becomes Light on upgrade. Existing explicit Light/Sepia/Dark and selected accents persist; no forced Dark-to-Light change remains. No firmware upload is needed.

## Install and check

Install TeleRC-v0.8.62.apk over 0.8.61 without uninstalling or clearing data. A former Pastel setting should show Background · Light. Compare strong action colors with the pre-compact version while checking that current Setup alignment and button sizes stay the same. Select/restart Light, Sepia and Dark; inspect all four accents on Setup and Controls and check large-font scrolling.

Required release gates: JVM theme migration tests, debug/release lint/build, host/default sanitizer regression, actual mini parser integration, native 12 theme/accent combinations on Setup/Controls, upgrade/restart persistence, equal Setup card geometry and large-font capture. Keep the verified native capture helper's fresh hierarchy, transition waits and exact external launcher handling. Native app/assertion failure blocks publishing. Final BUILD_INFO/evidence identify actual outcomes; phone/receiver/rover HIL remain pending.

With wheels raised and a motor cutoff available, check connect, Enable, neutral ARM, both axes, release, STOP/DISARM, tab/background/link loss, and explicit re-enable after recovery. This app-only color update changes no firmware targets or wire protocol. Stable promotion remains blocked by physical acceptance. Rollback without clearing data needs a higher-code signed restoration; retain prior APK/source and export routes before uninstalling.

#!/usr/bin/env bash
set -euo pipefail
PKG=io.github.denberg28.telerc
mkdir -p app/build/photo-evidence
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant "$PKG" android.permission.CAMERA
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION
adb shell am instrument -w "$PKG.test/$PKG.ThemeImageInstrumentation" | tee app/build/photo-evidence/result.txt
rg 'INSTRUMENTATION_CODE: -1' app/build/photo-evidence/result.txt
rg 'PASS: native photo' app/build/photo-evidence/result.txt
for image in photo-light-setup.png photo-light-controls.png photo-sepia-setup.png photo-sepia-controls.png photo-dark-setup.png photo-dark-controls.png photo-removed.png; do
  adb exec-out run-as "$PKG" cat "files/$image" > "app/build/photo-evidence/$image"
done
adb logcat -d -s AndroidRuntime > app/build/photo-evidence/runtime.txt
if rg 'FATAL EXCEPTION' app/build/photo-evidence/runtime.txt; then exit 1; fi

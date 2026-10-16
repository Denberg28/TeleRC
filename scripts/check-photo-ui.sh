#!/usr/bin/env bash
set -euo pipefail
PKG=io.github.denberg28.telerc
mkdir -p app/build/photo-evidence
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant "$PKG" android.permission.CAMERA
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION
adb shell am instrument -r -w "$PKG.test/$PKG.ThemeImageInstrumentation" | tee app/build/photo-evidence/result.txt
grep 'INSTRUMENTATION_CODE: -1' app/build/photo-evidence/result.txt
grep 'PASS: native photo' app/build/photo-evidence/result.txt
grep 'PASS: native maze' app/build/photo-evidence/result.txt
grep 'PASS: persistent red bump pixels' app/build/photo-evidence/result.txt
grep 'PASS: native market' app/build/photo-evidence/result.txt
grep 'PASS: flat practice dialogs' app/build/photo-evidence/result.txt
for image in photo-light-setup.png photo-light-controls.png photo-sepia-setup.png photo-sepia-controls.png photo-dark-setup.png photo-dark-controls.png photo-removed.png maze-three-bumps.png maze-level-cleared.png maze-level-two.png maze-bump-dot.png maze-bump-reset.png maze-coins.png market-vehicles.png market-colors.png maze-equipped.png maze-levels-dialog.png dialogs-light-levels.png dialogs-light-restart.png dialogs-sepia-levels.png dialogs-sepia-restart.png dialogs-dark-levels.png dialogs-dark-restart.png dialogs-light-market-vehicles.png dialogs-light-market-colors.png dialogs-sepia-market-vehicles.png dialogs-sepia-market-colors.png dialogs-dark-market-vehicles.png dialogs-dark-market-colors.png; do
  adb exec-out run-as "$PKG" cat "files/$image" > "app/build/photo-evidence/$image"
done
# Preserve one exact native frame in logs as well, for review without artifact storage.
python -c 'import base64,pathlib; print("MAZE_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/maze-level-two.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("LEVELS_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/dialogs-dark-levels.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("RESTART_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/dialogs-dark-restart.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("MARKET_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/dialogs-dark-market-vehicles.png").read_bytes()).decode())'
adb logcat -d -s AndroidRuntime > app/build/photo-evidence/runtime.txt
if grep 'FATAL EXCEPTION' app/build/photo-evidence/runtime.txt; then exit 1; fi

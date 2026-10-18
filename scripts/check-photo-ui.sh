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
grep 'PASS: native ELRS setup' app/build/photo-evidence/result.txt
grep 'PASS: waypoint planning' app/build/photo-evidence/result.txt
grep 'PASS: native photo' app/build/photo-evidence/result.txt
grep 'PASS: native maze' app/build/photo-evidence/result.txt
grep 'PASS: persistent red bump pixels' app/build/photo-evidence/result.txt
grep 'PASS: native market' app/build/photo-evidence/result.txt
grep 'PASS: flat practice dialogs' app/build/photo-evidence/result.txt
grep 'PASS: wallpaper fidelity' app/build/photo-evidence/result.txt
grep 'PASS: sharp joystick captions' app/build/photo-evidence/result.txt
grep 'PASS: live theme transitions' app/build/photo-evidence/result.txt
grep 'PASS: environment and accessory rendering' app/build/photo-evidence/result.txt
grep 'PASS: persistent FPV lifecycle' app/build/photo-evidence/result.txt
for image in environment-city.png environment-forest.png environment-desert.png environment-space.png vehicle-catalog.png fpv-default-help.png transition-photo-dark.png transition-photo-sepia.png transition-plain-dark.png transition-plain-sepia.png photo-light-setup.png photo-light-controls.png photo-sepia-setup.png photo-sepia-controls.png photo-dark-setup.png photo-dark-controls.png photo-removed.png maze-three-bumps.png maze-level-cleared.png maze-level-two.png maze-bump-dot.png maze-bump-reset.png maze-coins.png market-vehicles.png market-accessories.png maze-equipped.png maze-levels-dialog.png dialogs-light-levels.png dialogs-light-restart.png dialogs-sepia-levels.png dialogs-sepia-restart.png dialogs-dark-levels.png dialogs-dark-restart.png dialogs-light-market-vehicles.png dialogs-light-market-accessories.png dialogs-sepia-market-vehicles.png dialogs-sepia-market-accessories.png dialogs-dark-market-vehicles.png dialogs-dark-market-accessories.png photo-light-test.png photo-sepia-test.png photo-dark-test.png setup-light-minimized.png setup-sepia-minimized.png setup-dark-minimized.png setup-light-large-minimized.png setup-sepia-large-minimized.png setup-dark-large-minimized.png wallpaper-opacity-dialog.png wallpaper-0-controls.png wallpaper-0-test.png wallpaper-100-controls.png wallpaper-100-test.png tabs-light-setup.png tabs-light-large-setup.png tabs-light-controls.png tabs-light-large-controls.png tabs-light-test.png tabs-light-large-test.png labels-light-large-controls.png labels-light-large-test.png tabs-sepia-setup.png tabs-sepia-large-setup.png tabs-sepia-controls.png tabs-sepia-large-controls.png tabs-sepia-test.png tabs-sepia-large-test.png labels-sepia-large-controls.png labels-sepia-large-test.png tabs-dark-setup.png tabs-dark-large-setup.png tabs-dark-controls.png tabs-dark-large-controls.png tabs-dark-test.png tabs-dark-large-test.png labels-dark-large-controls.png labels-dark-large-test.png; do
  adb exec-out run-as "$PKG" cat "files/$image" > "app/build/photo-evidence/$image"
done
# Preserve one exact native frame in logs as well, for review without artifact storage.
python -c 'import base64,pathlib; print("MAZE_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/maze-level-two.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("LEVELS_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/dialogs-dark-levels.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("RESTART_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/dialogs-dark-restart.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("MARKET_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/dialogs-dark-market-vehicles.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("SETUP_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/setup-dark-large-minimized.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("CONTROLS_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/photo-light-controls.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("TABS_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/tabs-light-controls.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("OPACITY_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/wallpaper-opacity-dialog.png").read_bytes()).decode())'
adb logcat -d -s AndroidRuntime > app/build/photo-evidence/runtime.txt
if grep 'FATAL EXCEPTION' app/build/photo-evidence/runtime.txt; then exit 1; fi

python -c 'import base64,pathlib; print("TRANSITION_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/transition-photo-dark.png").read_bytes()).decode())'

python -c 'import base64,pathlib; print("ENVIRONMENT_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/environment-space.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("CATALOG_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/vehicle-catalog.png").read_bytes()).decode())'

for image in waypoint-light-menu.png waypoint-sepia-menu.png waypoint-dark-menu.png waypoint-light-plan.png waypoint-sepia-plan.png waypoint-dark-plan.png; do
  adb exec-out run-as "$PKG" cat "files/$image" > "app/build/photo-evidence/$image"
done
python -c 'import base64,pathlib; print("WAYPOINT_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/waypoint-dark-plan.png").read_bytes()).decode())'

for image in elrs-setup.png elrs-controls.png elrs-waypoint.png waypoint-light-plan.png waypoint-sepia-plan.png waypoint-dark-plan.png; do
  adb exec-out run-as "$PKG" cat "files/$image" > "app/build/photo-evidence/$image"
done
python -c 'import base64,pathlib; print("ELRS_SETUP_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/elrs-setup.png").read_bytes()).decode())'
python -c 'import base64,pathlib; print("WAYPOINT_UI_PNG="+base64.b64encode(pathlib.Path("app/build/photo-evidence/waypoint-dark-plan.png").read_bytes()).decode())'

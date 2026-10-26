#!/usr/bin/env bash
set -euo pipefail
mkdir -p core-ui-evidence
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant io.github.denberg28.telerc android.permission.ACCESS_FINE_LOCATION
adb shell pm grant io.github.denberg28.telerc android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant io.github.denberg28.telerc android.permission.CAMERA
adb shell am instrument -w io.github.denberg28.telerc.test/io.github.denberg28.telerc.CoreInstrumentation | tee core-ui-evidence/native-ui.txt
adb logcat -d > core-ui-evidence/logcat.txt
rg 'PASS: five mission-free pages' core-ui-evidence/native-ui.txt
! rg 'FAIL:|INSTRUMENTATION_FAILED|FATAL EXCEPTION' core-ui-evidence/native-ui.txt

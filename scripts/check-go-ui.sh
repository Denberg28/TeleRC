#!/usr/bin/env bash
set -euo pipefail
PKG=io.github.denberg28.telerc.go
mkdir -p go/build/ui-evidence
python3 scripts/go-udp-fixture.py > go/build/ui-evidence/udp.log 2>&1 &
fixture_pid=$!
cleanup() {
 for scale in 1.0 1.3; do
  for pane in controls settings; do
   name="go-$scale-$pane.png"
   adb exec-out run-as "$PKG" cat "files/$name" > "go/build/ui-evidence/$name" 2>/dev/null || true
  done
 done
 adb exec-out run-as "$PKG" cat files/go-icon.png > go/build/ui-evidence/go-icon.png 2>/dev/null || true
 adb exec-out run-as "$PKG" cat files/go-failed.png > go/build/ui-evidence/go-failed.png 2>/dev/null || true
 if [ -f go-fixture-events.json ]; then cp go-fixture-events.json go/build/ui-evidence/; fi
 kill "$fixture_pid" 2>/dev/null || true
}
trap cleanup EXIT
adb install -r go/build/outputs/apk/debug/go-debug.apk
adb install -r go/build/outputs/apk/androidTest/debug/go-debug-androidTest.apk
adb shell am instrument -r -w "$PKG.test/io.github.denberg28.telerc.GoInstrumentation" | tee go/build/ui-evidence/result.txt
grep 'INSTRUMENTATION_CODE: -1' go/build/ui-evidence/result.txt
grep 'PASS: Go Wi-Fi only' go/build/ui-evidence/result.txt
for scale in 1.0 1.3; do
 for pane in controls settings; do
  name="go-$scale-$pane.png"
  adb exec-out run-as "$PKG" cat "files/$name" > "go/build/ui-evidence/$name"
 done
done
wait "$fixture_pid"
cp go-fixture-events.json go/build/ui-evidence/
python3 - <<'PY'
import json
from pathlib import Path
v=json.loads(Path('go-fixture-events.json').read_text())
assert any(x.get('arm') is True for x in v)
assert any(x.get('arm') is False for x in v)
assert any(x.get('rc')==[1700,1800] for x in v)
assert any(x.get('hold') is True for x in v)
assert any(x.get('take') is True for x in v)
print('Independent UDP fixture: valid sparse motion, ARM, DISARM and live telemetry recovery observed.')
PY

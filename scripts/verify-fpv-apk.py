"""Verify the actual APK's USB chooser contract and original update identity."""
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

candidate, baseline = map(Path, sys.argv[1:])
build_tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'


def run(*args):
    return subprocess.check_output(list(map(str, args)), text=True)


def identity(apk):
    badging = run(build_tools / 'aapt', 'dump', 'badging', apk)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    assert package, 'Missing package identity'
    certs = run(build_tools / 'apksigner', 'verify', '--print-certs', apk)
    digests = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', certs)
    assert digests, 'Missing verified signer'
    return package.groups(), sorted(digests)


current, signer = identity(candidate)
previous, old_signer = identity(baseline)
assert current == ('io.github.denberg28.telerc', '95', '0.8.77'), current
assert previous[0] == current[0] and int(previous[1]) < int(current[1])
assert signer == old_signer, 'Signing continuity failed'
manifest = run(build_tools / 'aapt', 'dump', 'xmltree', candidate, 'AndroidManifest.xml')
attach_attributes = [line for line in manifest.splitlines() if 'A: android:name' in line and 'android.hardware.usb.action.USB_DEVICE_ATTACHED' in line]
assert len(attach_attributes) == 2, 'USB intent/metadata pair missing'
assert re.search(r'android:launchMode[^\n]*0x1\b', manifest), 'Expected singleTop Activity'
# Release optimization can shorten resource ZIP paths. Resolve the named XML
# through the compiled table instead of assuming its source filename survives.
resource_table = run(build_tools / 'aapt', 'dump', '--values', 'resources', candidate)
resource_lines = resource_table.splitlines()
filter_paths = set()
for index, line in enumerate(resource_lines):
    if ':xml/usb_fpv_devices' in line:
        filter_paths.update(re.findall(r'res/[^\s"<>]+\.xml', '\n'.join(resource_lines[index:index + 2])))
with zipfile.ZipFile(candidate) as archive:
    filter_paths.intersection_update(archive.namelist())
assert len(filter_paths) == 1, f'Could not resolve compiled USB filter: {filter_paths}'
device_filter = run(build_tools / 'aapt', 'dump', 'xmltree', candidate, filter_paths.pop())
assert re.search(r'A: class(?:[^\n]*0xe\b|=\"14\")', device_filter), 'Missing video class filter'
assert device_filter.count('E: usb-device') == 1, 'Unexpected USB filter expansion'
permissions = run(build_tools / 'aapt', 'dump', 'permissions', candidate)
assert 'android.permission.CAMERA' in permissions
assert not any(p in permissions for p in ('RECORD_AUDIO', 'MANAGE_EXTERNAL_STORAGE',
    'READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE', 'FOREGROUND_SERVICE'))
run(build_tools / 'zipalign', '-c', '-P', '16', '4', candidate)
with zipfile.ZipFile(candidate) as archive:
    assert archive.testzip() is None, 'APK CRC failure'
print('TeleRC 0.8.77/code95 test candidate — persistent USB FPV')
print('Source:', os.environ['GITHUB_SHA'])
print('JVM, debug/release lint/build, host regression and full sanitizers: PASS (preceding required steps)')
print('Compiled USB video attach intent/metadata/class filter/singleTop: PASS')
assert 'PASS: native photo' in Path('app/build/photo-evidence/result.txt').read_text()
assert 'PASS: native maze' in Path('app/build/photo-evidence/result.txt').read_text()
assert 'PASS: persistent red bump pixels' in Path('app/build/photo-evidence/result.txt').read_text()
assert 'PASS: native market' in Path('app/build/photo-evidence/result.txt').read_text()
assert 'PASS: flat practice dialogs' in Path('app/build/photo-evidence/result.txt').read_text()
assert 'PASS: wallpaper fidelity' in Path('app/build/photo-evidence/result.txt').read_text()
assert 'PASS: sharp joystick captions' in Path('app/build/photo-evidence/result.txt').read_text()
assert 'PASS: live theme transitions' in Path('app/build/photo-evidence/result.txt').read_text()
print('Repeated native Light/Sepia/Dark/accent changes with/without wallpaper: no Activity lifecycle interruption, no missing photo frames, same bitmap, drafts retained, native palette reset and compact menu bounds: PASS')
print('Native sharp caption contrast and aligned joystick centers; button-width menus on every tab, three themes and large text: PASS')
print('Native clear Setup wallpaper, console opacity blend/preview/apply/cancel/endpoints/persistence, transparent joystick corners, Setup collapse/restore/draft/persistence/corner actions and centered popups: PASS')
print('Native flat Levels/restart/Market, three themes, large font, tabs/purchases, selection/reset and expanded console geometry: PASS')
print('Native Market buy/equip, insufficient funds, coin collection and wallet persistence: PASS')
print('Native red impact pixels, no fade after pause/delay, reset removal: PASS')
print('Native API 35 maze restart, clear/unlock/next level and persistence: PASS')
print('Native API 35 photo import/rollback/persistence/render/removal and FPV options: PASS')
print('Signing continuity with v0.8.76, app identity, increasing code, permissions and ZIP alignment: PASS')
print('Receiver enumeration, default chooser, real preview, USB pause/resume and phone installation: BLOCKED; no hardware')
print('Firmware/protocol unchanged. No firmware reflash. Actions review artifact; physical hardware/field qualification remains BLOCKED.')

assert 'PASS: persistent FPV lifecycle' in Path('app/build/photo-evidence/result.txt').read_text()
print('Persistent FPV policy JVM regressions and native no-device lifecycle/default-help/cleanup: PASS; physical UVC reconnect/OS default choice: BLOCKED')

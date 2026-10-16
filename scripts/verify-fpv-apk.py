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
assert current == ('io.github.denberg28.telerc', '84', '0.8.66'), current
assert previous[0] == current[0] and int(previous[1]) < int(current[1])
assert signer == old_signer, 'Signing continuity failed'
manifest = run(build_tools / 'aapt', 'dump', 'xmltree', candidate, 'AndroidManifest.xml')
attach_attributes = [line for line in manifest.splitlines() if 'A: android:name' in line and 'android.hardware.usb.action.USB_DEVICE_ATTACHED' in line]
assert len(attach_attributes) == 2, 'USB intent/metadata pair missing'
assert re.search(r'android:launchMode[^\n]*0x1\b', manifest), 'Expected singleTop Activity'
device_filter = run(build_tools / 'aapt', 'dump', 'xmltree', candidate, 'res/xml/usb_fpv_devices.xml')
assert re.search(r'A: class(?:[^\n]*0xe\b|=\"14\")', device_filter), 'Missing video class filter'
assert device_filter.count('E: usb-device') == 1, 'Unexpected USB filter expansion'
permissions = run(build_tools / 'aapt', 'dump', 'permissions', candidate)
assert 'android.permission.CAMERA' in permissions
assert not any(p in permissions for p in ('RECORD_AUDIO', 'MANAGE_EXTERNAL_STORAGE',
    'READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE', 'FOREGROUND_SERVICE'))
run(build_tools / 'zipalign', '-c', '-P', '16', '4', candidate)
with zipfile.ZipFile(candidate) as archive:
    assert archive.testzip() is None, 'APK CRC failure'
print('TeleRC 0.8.66/code84 Skydroid USB FPV test build')
print('Source:', os.environ['GITHUB_SHA'])
print('JVM, debug/release lint/build, host regression and full sanitizers: PASS (preceding required steps)')
print('Compiled USB video attach intent/metadata/class filter/singleTop: PASS')
print('Signing continuity with v0.8.64, app identity, increasing code, permissions and ZIP alignment: PASS')
print('Receiver enumeration, default chooser, real preview, USB pause/resume and phone installation: BLOCKED; no hardware')
print('Firmware/protocol unchanged. No firmware reflash. Not stable/field qualified.')

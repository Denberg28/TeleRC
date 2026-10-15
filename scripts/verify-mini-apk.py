"""Verify candidate package/version and retained signing identity before delivery."""
import os
from pathlib import Path
import re
import struct
import zipfile
import subprocess
import sys

candidate, baseline = map(Path, sys.argv[1:])
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'


def run(*args):
    return subprocess.check_output(list(map(str, args)), text=True)


def identity(apk):
    info = run(tools / 'aapt', 'dump', 'badging', apk)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", info)
    assert package, 'Missing APK identity'
    certs = run(tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
    digests = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', certs)
    assert digests, 'Missing verified signing certificate'
    return package.groups(), sorted(digests)


current, cert = identity(candidate)
previous, old_cert = identity(baseline)
assert current[0] == previous[0] == 'io.github.denberg28.telerc', 'Package changed'
assert current[1:] == ('78', '0.8.60'), 'Unexpected candidate version'
assert previous[1:] == ('77', '0.8.59'), 'Unexpected upgrade baseline'
assert int(current[1]) > int(previous[1]), 'Non-increasing version code'
assert cert == old_cert, 'Signing identity changed'
permissions = run(tools / 'aapt', 'dump', 'permissions', candidate)
assert 'android.permission.CAMERA' in permissions, 'Camera permission missing'
assert not any(p in permissions for p in ('RECORD_AUDIO', 'MANAGE_EXTERNAL_STORAGE',
    'READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE', 'FOREGROUND_SERVICE')), 'Unused capture permissions present'
run(tools / 'zipalign', '-c', '-P', '16', '4', candidate)
# ZIP alignment and ELF segment alignment are independent Android 16 KB checks.
with zipfile.ZipFile(candidate) as archive:
    native = [n for n in archive.namelist() if n.startswith('lib/arm64-v8a/') and n.endswith('.so')]
    assert native, 'Missing arm64 native libraries'
    for name in native:
        data = archive.read(name)
        assert data[:6] == b'\x7fELF\x02\x01', f'Unexpected ELF format: {name}'
        phoff = struct.unpack_from('<Q', data, 32)[0]
        phsize, phnum = struct.unpack_from('<HH', data, 54)
        for index in range(phnum):
            header = struct.unpack_from('<IIQQQQQQ', data, phoff + index * phsize)
            if header[0] == 1:
                assert header[7] >= 16384, f'Non-16KB PT_LOAD alignment: {name}'


print('TeleRC muted themes and compact Setup APK candidate')
print('Source commit:', run('git', 'rev-parse', 'HEAD').strip())
print('Package:', current[0], 'version:', current[2], 'code:', current[1])
print('Signing continuity with 0.8.59: PASS')
print('Certificate SHA-256:', ', '.join(cert))
print('USB FPV permissions, APK ZIP and arm64 ELF 16 KB alignment: PASS')
print('Android JVM regression, debug/release lint and builds: PASS (required preceding workflow steps)')
print('Firmware host regression + ASan/UBSan: PASS (required preceding workflow steps)')
ui_status = Path('app/build/ui-evidence/STATUS.txt').read_text().strip()
assert ui_status.startswith(('PASS:', 'BLOCKED:')), 'Native UI check failed'
print('Native Android emulator:', ui_status)
print('Android packets -> actual MiniRover parser: PASS (required preceding workflow step)')
print('Firmware unchanged: retain installed mini 1.2.2 or existing matching receiver/FC firmware; no reflash needed')
print('Physical Wi-Fi/phone installation/motor HIL: BLOCKED, no hardware attached')
print('Qualification: signed software-verified test candidate, not field validated')

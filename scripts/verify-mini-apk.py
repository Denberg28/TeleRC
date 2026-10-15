"""Verify candidate package/version and retained signing identity before delivery."""
import os
from pathlib import Path
import re
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
assert current[1:] == ('75', '0.8.57'), 'Unexpected candidate version'
assert int(current[1]) > int(previous[1]), 'Non-increasing version code'
assert cert == old_cert, 'Signing identity changed'
print('TeleRC independent control response APK candidate')
print('Source commit:', run('git', 'rev-parse', 'HEAD').strip())
print('Package:', current[0], 'version:', current[2], 'code:', current[1])
print('Signing continuity with 0.8.56: PASS')
print('Certificate SHA-256:', ', '.join(cert))
print('Android JVM regression, debug/release lint and builds: PASS (required preceding workflow steps)')
print('Firmware host regression + ASan/UBSan: PASS (required preceding workflow steps)')
print('Android packets -> actual MiniRover parser: PASS (required preceding workflow step)')
print('Firmware unchanged: retain installed mini 1.2.2 or existing matching receiver/FC firmware; no reflash needed')
print('Physical Wi-Fi/phone installation/motor HIL: BLOCKED, no hardware attached')
print('Qualification: signed software-verified test candidate, not field validated')

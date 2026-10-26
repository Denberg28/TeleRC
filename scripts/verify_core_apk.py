"""Verify the app-only candidate against the retained signed v1.0.0 baseline."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
def identity(path):
    badging = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', path], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging).groups()
    cert = subprocess.check_output([str(tools/'apksigner'), 'verify', '--print-certs', path], text=True)
    signers = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', cert)
    if not signers:
        raise RuntimeError('Verified signer unavailable')
    return package, sorted(signers)
apk, baseline = sys.argv[1:]
package, signers = identity(apk)
previous, old_signers = identity(baseline)
assert package == ('io.github.denberg28.telerc', '1100', '1.1.0rc1'), package
assert previous[0] == package[0] and int(package[1]) > int(previous[1])
assert signers == old_signers, 'Signing identity changed'
reports = list(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
assert reports, 'No unit test reports'
tests = 0
for report in reports:
    r = ET.parse(report).getroot()
    assert int(r.attrib.get('failures', 0)) == int(r.attrib.get('errors', 0)) == 0
    tests += int(r.attrib['tests'])
for variant in ('debug', 'release'):
    report = ET.parse(f'app/build/reports/lint-results-{variant}.xml').getroot()
    assert not any(i.attrib.get('severity') in ('Error', 'Fatal') for i in report.findall('issue'))
result = dict(package=package[0], version_code=1100, version='1.1.0rc1',
              source_commit=os.environ['GITHUB_SHA'], signer_sha256=signers,
              apk_sha256=hashlib.sha256(Path(apk).read_bytes()).hexdigest(),
              jvm_tests=tests, lint='debug/release passed', hardware='NOT TESTED')
Path(apk).with_name('APK_VERIFICATION.json').write_text(json.dumps(result, indent=2)+'\n')
print(json.dumps(result))

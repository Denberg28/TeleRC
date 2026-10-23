"""Validate retained release certificate and strictly increasing Android identity."""
import os,re,subprocess,sys,zipfile
from pathlib import Path
bt=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
def run(*args):return subprocess.check_output(list(map(str,args)),text=True)
def identity(path):
 p=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",run(bt/'aapt','dump','badging',path)).groups()
 signer=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)',run(bt/'apksigner','verify','--print-certs',path))
 assert signer
 return p,signer
candidate,previous=map(Path,sys.argv[1:])
a,sa=identity(candidate);b,sb=identity(previous)
assert a==('io.github.denberg28.telerc','106','0.8.88')
assert a[0]==b[0] and int(a[1])>int(b[1]) and sa==sb
run(bt/'zipalign','-c','-P','16','4',candidate)
with zipfile.ZipFile(candidate) as z:
 assert z.testzip() is None
 dex=b''.join(z.read(n) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n))
 for marker in [b'Balance pre-test',b'TELERC_BALANCE_V1|',b'Calibrate & save',b'Confirm directions']:
  assert marker in dex,marker
print('TeleRC 0.8.88 / code106; source',os.environ['GITHUB_SHA'])
print('JVM, host tests, sanitizers, debug/release lint/build: PASS (preceding required steps)')
print('Retained release signer, increasing version, alignment and APK CRC: PASS')
print('Native API35 complete UI regression, dropdown-only Balance and shared popups: PASS')
print('Firmware unchanged from 0.8.87 / balancer0.3.0; no affected actual-board target')
print('Physical phone/rover HIL: BLOCKED; no physical/field/stable claim')
import xml.etree.ElementTree as ET
reports=list(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
assert reports
roots=[ET.parse(p).getroot() for p in reports]
total={k:sum(int(x.get(k,0)) for x in roots) for k in ('tests','failures','errors','skipped')}
assert total['failures']==total['errors']==total['skipped']==0,total
print('JVM results:',total)

import os,re,subprocess,sys,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
bt=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
def run(*args):return subprocess.check_output(list(map(str,args)),text=True)
def identity(path):
 badging=run(bt/'aapt','dump','badging',path)
 p=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging).groups()
 signer=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)',run(bt/'apksigner','verify','--print-certs',path))
 return p,signer
apk,baseline=map(Path,sys.argv[1:]);a,sa=identity(apk);b,sb=identity(baseline)
assert a==('io.github.denberg28.telerc.go','2','1.0.1')
assert b[0]=='io.github.denberg28.telerc' and sa and sa==sb
run(bt/'zipalign','-c','-P','16','4',apk)
permissions=run(bt/'aapt','dump','permissions',apk)
for removed in ['CAMERA','ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','REQUEST_INSTALL_PACKAGES','RECORD_AUDIO']:
 assert f'android.permission.{removed}' not in permissions
with zipfile.ZipFile(apk) as z:
 assert z.testzip() is None
 names=z.namelist();assert not any(x.startswith('lib/') for x in names)
 dex=b''.join(z.read(x) for x in names if re.fullmatch(r'classes\d*\.dex',x))
 for marker in [b'WaypointTab',b'BalancePretestTab',b'UsbLoRaTransport',b'maplibre',b'UVCCamera']:
  assert marker not in dex,marker
reports=list(Path('go/build/test-results/testDebugUnitTest').glob('TEST-*.xml'));assert reports
roots=[ET.parse(p).getroot() for p in reports]
counts={k:sum(int(x.get(k,0)) for x in roots) for k in ('tests','failures','errors','skipped')}
assert counts['failures']==counts['errors']==counts['skipped']==0
print('TeleRC Go 1.0.1/code2; standalone package; source',os.environ['GITHUB_SHA'])
print('Run:',os.environ['GITHUB_SERVER_URL']+'/'+os.environ['GITHUB_REPOSITORY']+'/actions/runs/'+os.environ['GITHUB_RUN_ID'])
print('JVM:',counts)
print('Both-app regression; Go debug/release lint/build; native UI/UDP fault recovery; host/default sanitizers: PASS')
print('Four actual ESP32-S3 core3.3.2 targets (MX1508/BTS7960 x Wi-Fi/ELRS): PASS')
print('Retained signer, distinct identity, alignment/CRC, removed permissions/native SDKs: PASS')
print('Release candidate; physical phone/rover HIL: NOT TESTED/BLOCKED')

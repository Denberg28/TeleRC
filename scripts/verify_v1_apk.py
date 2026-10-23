"""Verify identity, version progression, signature continuity and APK alignment."""
import json, os, re, subprocess, sys
from pathlib import Path
manifest=json.loads(Path('ecosystem-release.json').read_text())
bt=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
def identity(path):
    text=subprocess.check_output([str(bt/'aapt'),'dump','badging',path],text=True)
    values=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",text).groups()
    cert=subprocess.check_output([str(bt/'apksigner'),'verify','--print-certs',path],text=True)
    signers=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)',cert)
    if not signers: raise ValueError('Missing verified APK signer')
    return values,signers
new,signer=identity(sys.argv[1]);old,previous=identity(sys.argv[2])
expected='io.github.denberg28.telerc'+('.go' if manifest['tier']=='consumer' else '')
assert new==(expected,str(manifest['android_version_code']),manifest['application_version'])
assert old[0]==new[0] and signer==previous and int(new[1])>int(old[1])
subprocess.run([str(bt/'zipalign'),'-c','-P','16','4',sys.argv[1]],check=True)
Path('release-dist/APK_VERIFICATION.json').write_text(json.dumps(dict(package=new[0],version=new[2],version_code=int(new[1]),signer_sha256=signer,upgrade_from=old[2],source_commit=os.environ['GITHUB_SHA'],hardware_validation='BLOCKED',channel='candidate'),indent=2)+'\n')
print('PASS: package/version, retained signer, version-code progression and alignment')

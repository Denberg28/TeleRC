"""Package tracked source while excluding keys and generated/private provisioning."""
import hashlib,json,os,re,subprocess,zipfile
from pathlib import Path
out=Path('release-dist');out.mkdir(exist_ok=True)
m=json.loads(Path('ecosystem-release.json').read_text())
files=subprocess.check_output(['git','ls-files','-z']).decode().split('\0')
name=f"{m['product']}-v{m['application_version']}-source.zip"
with zipfile.ZipFile(out/name,'w',zipfile.ZIP_DEFLATED) as archive:
 for name in files:
  if not name: continue
  path=Path(name)
  if path.is_symlink(): raise ValueError('Symlink in source package')
  if path.suffix.lower() in {'.jks','.keystore','.key','.pem','.p12','.pfx'} or path.name in {'.env','Config.private.h','TeleRCLoRaConfigLocal.h'}: raise ValueError('Private material in tracked source')
  if path.suffix=='.h':
   text=path.read_text(encoding='utf-8')
   # Public mission examples must be explicitly unprovisioned.
   values=re.findall(r'^\s*#\s*define\s+PROVISIONED\s+(\d+)',text,re.M)
   if values and values!=['0']: raise ValueError('Private generated radio profile')
  # Legacy archives are retained in git, excluded from the clean source snapshot.
  if path.suffix.lower() in {'.zip','.apk','.bin','.elf','.map'}: continue
  archive.write(path,name)
 archive.writestr('SOURCE_COMMIT.txt',os.environ['GITHUB_SHA']+'\n')
for name in ('ecosystem-release.json','docs/REALIGNMENT_V1.md'):
 (out/Path(name).name).write_bytes(Path(name).read_bytes())
(out/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(out.iterdir()) if p.is_file() and p.name!='SHA256SUMS.txt'))

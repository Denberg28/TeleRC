"""Publish the unchanged, verified Android candidate; never promote stable."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

SOURCE = '03b5b69ff39487678d25a8023c5438a773ca3d51'
UI_SOURCE = '19ee9353867ee18ba114dc32794c029bd628c9a6'
ARCHIVE_HASH = '47b45210679fecefd5d18ed32d4e0c4fee932b957fb66f52ca82881d3fcc694e'
APK_HASH = 'd0800ad42de2a764f628dcf02c1c132bc36b421d1e1646a4250f5955a5753f9c'
SIGNER = '5e57bd9bb3d10d97e5abdad4c51dbf5282708db73f1844fac272403952f9f5ec'
APK = 'TeleRC-v1.1.0rc1.apk'
KIT = 'TeleRC-v1.1.0rc1-Release-Kit.zip'
OUT = Path('publication')

def digest(data):
    return hashlib.sha256(data).hexdigest()

def read_json(name):
    return json.loads(Path(name).read_text())

def provenance():
    build, ui, artifact = map(read_json, ('build-run.json', 'ui-run.json', 'build-artifact.json'))
    for run, run_id, sha in ((build, 40288365262, SOURCE), (ui, 40293580334, UI_SOURCE)):
        assert run['id'] == run_id and run['status'] == 'completed' and run['conclusion'] == 'success'
        assert run['head_sha'] == sha and run['repository']['full_name'].lower() == 'denberg28/telerc'
    assert artifact['id'] == 12634643769 and artifact['name'] == 'TeleRC-core-v1.1.0rc1'
    assert not artifact['expired'] and artifact['digest'] == 'sha256:' + ARCHIVE_HASH
    assert artifact['workflow_run']['id'] == build['id'] and artifact['workflow_run']['head_sha'] == SOURCE
    # Only the instrumentation runner may differ in the UI branch build configuration.
    def config(sha):
        return subprocess.check_output(['git', 'show', sha + ':app/build.gradle.kts'], text=True)
    assert config(SOURCE).replace('.ThemeImageInstrumentation', '.CoreInstrumentation') == config(UI_SOURCE)
    print('PASS: successful pinned build, native UI and artifact provenance')

def sums(files):
    return ''.join(digest(p.read_bytes()) + '  ' + p.name + '\n' for p in sorted(files))

def stage():
    archive = Path('verified-core.zip').read_bytes()
    assert digest(archive) == ARCHIVE_HASH
    expected = {APK, 'TeleRC-v1.1.0rc1-source.zip', 'APK_VERIFICATION.json', 'START_HERE.md', 'SHA256SUMS.txt'}
    OUT.mkdir(exist_ok=False)
    with zipfile.ZipFile('verified-core.zip') as z:
        assert len(z.namelist()) == len(expected) and set(z.namelist()) == expected
        assert sum(i.file_size for i in z.infolist()) < 150_000_000
        for name in expected:
            (OUT / name).write_bytes(z.read(name))
    covered = set()
    for line in (OUT / 'SHA256SUMS.txt').read_text().splitlines():
        sha, name = line.split('  ', 1)
        assert name in expected - {'SHA256SUMS.txt'} and name not in covered
        assert digest((OUT / name).read_bytes()) == sha
        covered.add(name)
    assert covered == expected - {'SHA256SUMS.txt'}
    assert digest((OUT / APK).read_bytes()) == APK_HASH
    v = read_json(OUT / 'APK_VERIFICATION.json')
    assert v['source_commit'] == SOURCE and v['apk_sha256'] == APK_HASH
    assert v['package'] == 'io.github.denberg28.telerc' and v['version_code'] == 1100 and v['version'] == '1.1.0rc1'
    assert v['signer_sha256'] == [SIGNER] and v['jvm_tests'] == 150 and v['lint'] == 'debug/release passed'
    report = Path('docs/ANDROID_CORE_1.1_EVIDENCE.md').read_text()
    report = report.replace('No GitHub release or stable promotion was performed.',
                            'This test candidate is published on GitHub; no stable promotion was performed.')
    (OUT / 'VALIDATION_NOTES.md').write_text(report)
    (OUT / 'PUBLICATION.json').write_text(json.dumps(dict(
        version='1.1.0rc1', source_commit=SOURCE, publication_commit=os.environ['GITHUB_SHA'],
        build_run=40288365262, ui_run=40293580334, ui_source_commit=UI_SOURCE,
        artifact_id=12634643769, artifact_sha256=ARCHIVE_HASH, apk_sha256=APK_HASH,
        prerelease=True, stable_qualified=False, hardware_validation='NOT TESTED'), indent=2) + '\n')
    (OUT / 'SHA256SUMS.txt').write_text(sums(p for p in OUT.iterdir() if p.name != 'SHA256SUMS.txt'))
    with zipfile.ZipFile(OUT / KIT, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for p in sorted(OUT.iterdir()):
            if p.name != KIT:
                z.write(p, p.name)
    with zipfile.ZipFile(OUT / KIT) as z:
        assert z.testzip() is None
    (OUT / 'SHA256SUMS.txt').write_text(sums(p for p in OUT.iterdir() if p.name != 'SHA256SUMS.txt'))
    print('PASS: exact APK/source bytes, original checksums and complete release kit')

def apk():
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
    certs = subprocess.check_output([str(tools/'apksigner'), 'verify', '--print-certs', str(OUT/APK)], text=True)
    assert re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)', certs) == [SIGNER]
    badging = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(OUT/APK)], text=True)
    p = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging).groups()
    assert p == ('io.github.denberg28.telerc', '1100', '1.1.0rc1')
    print('PASS: signed APK identity and version independently verified')

def release_check(path, draft):
    release = read_json(path)
    assert release['tag_name'] == 'v1.1.0rc1' and release['draft'] == draft and release['prerelease'] is True
    assert release['target_commitish'] == SOURCE
    files = {p.name: p for p in OUT.iterdir() if p.is_file()}
    assets = {a['name']: a for a in release['assets']}
    assert len(release['assets']) == len(files) and set(assets) == set(files)
    for name, p in files.items():
        a = assets[name]
        assert a['state'] == 'uploaded' and a['size'] == p.stat().st_size
        assert a['digest'] == 'sha256:' + digest(p.read_bytes()), name
    print('PASS: uploaded asset inventory, sizes and GitHub SHA-256 digests')

if __name__ == '__main__':
    action = sys.argv[1]
    if action == 'provenance': provenance()
    elif action == 'stage': stage()
    elif action == 'apk': apk()
    elif action == 'uploaded': release_check('uploaded-release.json', True)
    elif action == 'published':
        release_check('published-release.json', False)
        tag = read_json('published-tag.json')
        assert tag['object']['type'] == 'commit' and tag['object']['sha'] == SOURCE
        print('Published https://github.com/Denberg28/TeleRC/releases/tag/v1.1.0rc1')
    else: raise ValueError('Unknown action')

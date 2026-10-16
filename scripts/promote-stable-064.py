"""Verify immutable v0.8.64 artifacts, then perform the owner's explicit channel promotion."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

REPO = 'Denberg28/TeleRC'
TAG = 'v0.8.64'
SOURCE = 'cee84f40ad7ebf99bfd2152f7fd0c339ca423639'
RELEASE_ID = 414506182
RUN_ID = 38844598235
EXPECTED = {
    'BUILD_INFO.txt': 'a2cfd37d3f92f924105caa72e669a6b02aaac87803eee6e6688ae036a6e12cc6',
    'SHA256SUMS.txt': 'd05ecee788aebc1d7374c933385be74750f78d424872c4ef213d016eafb58b53',
    'START_HERE.md': 'de0947523ef0b10e0550f8354bdd4a8aa38caeb604559d50a2330d6cc2fd41a1',
    'TeleRC-MiniRover-MX1508-v1.2.3-firmware.zip': '14829ad663a9102cbe3914197eb9babbd51281fce8477cce6b7ebcb55eddfb87',
    'TeleRC-MiniRover-MX1508-v1.2.3-sketch.zip': 'ef2f118b1cf02fd7b1bceb333ba39ce0dedd18d8888f46bd2f03db0e4535339d',
    'TeleRC-v0.8.64-build-evidence.zip': 'c6b4b6ddb202242f739da9484ec4b70b832f9e59ca482486a17c75f5f8da2fb6',
    'TeleRC-v0.8.64-source.zip': '20be9f9cb064bc4568c8a9d10274400a61c78b9c69d42bc4fa75e6516914de4e',
    'TeleRC-v0.8.64.apk': '6e50188d63d160d48a9af2b231ea3459e075b2ef4ee481b5418e1c5a7299ddf7',
}


def run(*args):
    return subprocess.check_output(args, text=True)


def api(path):
    return json.loads(run('gh', 'api', f'repos/{REPO}/{path}'))


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def inventory(release):
    return sorted((a['id'], a['name'], a['size'], a['digest']) for a in release['assets'])


require(os.environ.get('GITHUB_ACTIONS') == 'true', 'Run only in the scoped promotion workflow')
require(os.environ.get('GITHUB_REPOSITORY') == REPO, 'Unexpected repository')
require(os.environ.get('GITHUB_REF') == 'refs/heads/work/stable-promote-064-20261016', 'Unexpected branch')
release = api(f'releases/tags/{TAG}')
require(release['id'] == RELEASE_ID and not release['draft'], 'Unexpected release identity/draft')
require(release['target_commitish'] == SOURCE, 'Release source changed')
require({a['name'] for a in release['assets']} == set(EXPECTED), 'Release asset set changed')
for asset in release['assets']:
    require(asset['digest'] == 'sha256:' + EXPECTED[asset['name']], f'Metadata digest changed: {asset["name"]}')
ref = api(f'git/ref/tags/{TAG}')
require(ref['object']['type'] == 'commit' and ref['object']['sha'] == SOURCE, 'Build tag changed')
qualification = api(f'actions/runs/{RUN_ID}')
require(qualification['head_sha'] == SOURCE and qualification['conclusion'] == 'success', 'Build qualification mismatch')
releases = api('releases?per_page=100')
for item in releases:
    match = re.fullmatch(r'v(\d+)\.(\d+)\.(\d+)', item['tag_name'])
    if match and not item['draft'] and not item['prerelease']:
        require(tuple(map(int, match.groups())) <= (0, 8, 64), 'A newer stable version exists; refusing to supersede it')
previous_latest = api('releases/latest')['tag_name']
root = Path(os.environ['RUNNER_TEMP']) / 'telerc-stable-064'
root.mkdir()
run('gh', 'release', 'download', TAG, '--repo', REPO, '--dir', str(root))
for name, expected in EXPECTED.items():
    path = root / name
    require(hashlib.sha256(path.read_bytes()).hexdigest() == expected, f'Artifact hash mismatch: {name}')
    if path.suffix in ('.zip', '.apk'):
        with zipfile.ZipFile(path) as archive:
            require(archive.testzip() is None, f'Archive CRC failure: {name}')
    print('VERIFIED', name, expected, flush=True)
subprocess.run(['sha256sum', '--strict', '--check', 'SHA256SUMS.txt'], cwd=root, check=True)
info = (root / 'BUILD_INFO.txt').read_text()
require(SOURCE in info and 'version: 0.8.64 code: 82' in info, 'Unexpected build identity')
require('Signing continuity with 0.8.63: PASS' in info, 'Signing continuity evidence absent')
require('Native Android emulator: PASS:' in info, 'Native UI evidence absent')
with zipfile.ZipFile(root / 'TeleRC-v0.8.64-build-evidence.zip') as evidence:
    require(evidence.read('ui-evidence/STATUS.txt').decode().startswith('PASS:'), 'Native UI did not pass')
    for accent in ('blue', 'teal', 'violet', 'slate'):
        for theme in ('light', 'sepia', 'dark'):
            require(f'ui-evidence/{theme}-{accent}-test.png' in evidence.namelist(), 'Missing Test page capture')
# Refetch immediately before mutation; metadata changes must not race our inspection.
current = api(f'releases/tags/{TAG}')
require(current['id'] == RELEASE_ID and current['target_commitish'] == SOURCE and inventory(current) == inventory(release), 'Release changed during qualification')
notes = Path('docs/STABLE_064.md').read_text()
notes += f'\nPromotion audit source: https://github.com/{REPO}/blob/{os.environ["GITHUB_SHA"]}/docs/STABLE_064.md\n'
notes_path = root / 'stable-notes.md'
notes_path.write_text(notes)
run('gh', 'release', 'edit', TAG, '--repo', REPO, '--prerelease=false', '--latest',
    '--title', 'TeleRC v0.8.64 — stable: navy, offline maze and 95% mini output', '--notes-file', str(notes_path))
after = api(f'releases/tags/{TAG}')
latest = api('releases/latest')
require(not after['prerelease'] and not after['draft'], 'Stable classification did not persist')
require(latest['id'] == RELEASE_ID and latest['tag_name'] == TAG, 'Latest endpoint does not select this release')
require(after['target_commitish'] == SOURCE and inventory(after) == inventory(release), 'Promotion changed artifacts/source')
require(api(f'git/ref/tags/{TAG}')['object']['sha'] == SOURCE, 'Promotion moved tag')
report = {
    'release': after['html_url'], 'latest': latest['tag_name'], 'prerelease': after['prerelease'],
    'source': SOURCE, 'qualification_run': RUN_ID, 'promotion_commit': os.environ['GITHUB_SHA'],
    'previous_latest': previous_latest, 'asset_digests': EXPECTED,
    'hardware_installation_HIL': 'BLOCKED; owner-requested channel promotion, no physical validation claim',
}
Path('stable-promotion-064.json').write_text(json.dumps(report, indent=2) + '\n')
print('LATEST_STABLE_VERIFIED', TAG, 'source', SOURCE, 'previous latest', previous_latest)
print('HARDWARE_HIL_BLOCKED; no physical readiness claim')
with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as output:
    output.write(f'Promoted [{TAG}]({after["html_url"]}) to Latest stable using unchanged verified artifacts.\n\n')
    output.write('Hardware/installation/field validation remains pending.\n')

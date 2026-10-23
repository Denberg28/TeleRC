"""Verify immutable v0.8.84 artifacts, then perform the owner's explicit channel promotion."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

REPO = 'Denberg28/TeleRC'
TAG = 'v0.8.84'
SOURCE = 'f3eba8b620ca49b1030434fae6bb7f8dc94a246c'
RELEASE_ID = 416685501
RUN_ID = 39161856095
EXPECTED = {
    'BUILD_INFO.txt': 'bbbc253458cba0383091f805ba65d65e39aa65148b06f0528634cfaaea70cc05',
    'SHA256SUMS.txt': '868e420041e7716cf1fce7470effde3f9328cc740e8b39c39a91c95ed996fe22',
    'START_HERE.md': '801dec3001287d2ff64e48e1e39cedd300c0b40ce8952b1ce17e866fc6cf1192',
    'TeleRC-v0.8.84-check-evidence.zip': 'd0d96153d054f4ec9c69e97ad44520adf65645d20d762b4b164045c6fafb5a58',
    'TeleRC-v0.8.84-source.zip': 'a31fcb00d7c0c9f90147e9d62c2c7b87b43f2b4ad30e833df55e3bcb1c2b70c3',
    'TeleRC-v0.8.84.apk': 'fe4b51fc44666d016802bf14f2761fbd4e4ab93d064dd665fdfe9caadc51a9b1',
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
require(os.environ.get('GITHUB_REF') == 'refs/heads/work/stable-promote-084-20261019', 'Unexpected branch')
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
require(len(releases) < 100, 'Release list needs pagination before promotion')
for item in releases:
    match = re.fullmatch(r'v(\d+)\.(\d+)\.(\d+)', item['tag_name'])
    if match and not item['draft'] and not item['prerelease']:
        require(tuple(map(int, match.groups())) <= (0, 8, 84), 'A newer stable version exists; refusing to supersede it')
latest_result = subprocess.run(['gh', 'api', f'repos/{REPO}/releases/latest'], text=True, capture_output=True)
if latest_result.returncode == 0:
    previous_latest = json.loads(latest_result.stdout)['tag_name']
else:
    require('HTTP 404' in latest_result.stderr, 'Could not inspect Latest stable')
    previous_latest = None
root = Path(os.environ['RUNNER_TEMP']) / 'telerc-stable-084'
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
require(SOURCE in info and 'TeleRC 0.8.84/code102' in info, 'Unexpected build identity')
require('Signing continuity with v0.8.83' in info, 'Signing continuity evidence absent')
require('Measured native compact messages/forms/short lists' in info, 'Native UI evidence absent')
with zipfile.ZipFile(root / 'TeleRC-v0.8.84-source.zip') as source:
    require(source.comment.decode().strip() == SOURCE, 'Source archive commit mismatch')
with zipfile.ZipFile(root / 'TeleRC-v0.8.84-check-evidence.zip') as evidence:
    result = evidence.read('app/build/photo-evidence/result.txt').decode()
    require('PASS: measured popup sizing' in result and 'INSTRUMENTATION_CODE: -1' in result, 'Native UI did not pass')
    for theme in ('light', 'sepia', 'dark'):
        for kind in ('list', 'form', 'message'):
            require(f'app/build/photo-evidence/shared-{theme}-{kind}.png' in evidence.namelist(), 'Missing popup capture')
# Refetch immediately before mutation; metadata changes must not race our inspection.
current = api(f'releases/tags/{TAG}')
require(current['id'] == RELEASE_ID and current['target_commitish'] == SOURCE and inventory(current) == inventory(release), 'Release changed during qualification')
notes = Path('docs/STABLE_084.md').read_text()
notes += f'\nPromotion audit source: https://github.com/{REPO}/blob/{os.environ["GITHUB_SHA"]}/docs/STABLE_084.md\n'
notes_path = root / 'stable-notes.md'
notes_path.write_text(notes)
if current['prerelease'] or previous_latest != TAG:
    run('gh', 'release', 'edit', TAG, '--repo', REPO, '--prerelease=false', '--latest',
        '--title', 'TeleRC v0.8.84 — stable: compact popups and consistent transitions', '--notes-file', str(notes_path))
else:
    print('Existing Latest stable promotion retained; verifying without further release mutation')
after = api(f'releases/tags/{TAG}')
latest = api('releases/latest')
require(not after['prerelease'] and not after['draft'], 'Stable classification did not persist')
require(latest['id'] == RELEASE_ID and latest['tag_name'] == TAG, 'Latest endpoint does not select this release')
require(after['target_commitish'] == SOURCE and inventory(after) == inventory(release), 'Promotion changed artifacts/source')
require(api(f'git/ref/tags/{TAG}')['object']['sha'] == SOURCE, 'Promotion moved tag')
report = {
    'release': after['html_url'], 'latest': latest['tag_name'], 'prerelease': after['prerelease'],
    'source': SOURCE, 'qualification_run': RUN_ID, 'promotion_commit': os.environ['GITHUB_SHA'],
    'latest_before_this_verification': previous_latest, 'asset_digests': EXPECTED,
    'hardware_installation_HIL': 'BLOCKED; owner-requested channel promotion, no physical validation claim',
}
Path('stable-promotion-084.json').write_text(json.dumps(report, indent=2) + '\n')
print('LATEST_STABLE_VERIFIED', TAG, 'source', SOURCE, 'previous latest', previous_latest)
print('HARDWARE_HIL_BLOCKED; no physical readiness claim')
with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as output:
    output.write(f'Promoted [{TAG}]({after["html_url"]}) to Latest stable using unchanged verified artifacts.\n\n')
    output.write('Hardware/installation/field validation remains pending.\n')

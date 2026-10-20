"""Publish only the immutable, software-qualified 0.8.87 build; never rebuild/sign."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

REPO = "Denberg28/TeleRC"
SOURCE = "359d10ec0facd6501119f13bd18ed8ab82776b44"
RUN = 39419210517
APK_SHA = "20f4b08041b06883cb0382b566ed8f7aaecc676c3c9ea4e67604f457b700a2e2"
TAG = "v0.8.87"
assert os.environ["GITHUB_REPOSITORY"] == REPO

def gh(*args):
    return subprocess.check_output(["gh", *args])

def api(path):
    return json.loads(gh("api", f"repos/{REPO}/{path}"))

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

run = api(f"actions/runs/{RUN}")
assert run["status"] == "completed" and run["conclusion"] == "success"
assert run["head_sha"] == SOURCE
# App and firmware executable trees must remain exactly as qualified.
subprocess.run(["git", "diff", "--exit-code", SOURCE, "--", "app", "bridge"], check=True)
assert not any(r["tag_name"] == TAG for r in api("releases?per_page=100")), "Release already exists; do not overwrite"
work = Path(os.environ["RUNNER_TEMP"]) / "publish-087"
work.mkdir()
assets = work / "assets"
assets.mkdir()
artifacts = [
    (12263572209, "app-build.zip", "98fad4226f31ce55501f0e168f340f90c3f599560b91f626e5fcf2ed93d0da54"),
    (12262843161, "TeleRC-v0.8.87-test-evidence.zip", "7fdca8e66853cdf1e75d0e6b9f8b120ef4929f16d5aff85827716e2f43721d08"),
]
for artifact_id, name, sha in artifacts:
    metadata = api(f"actions/artifacts/{artifact_id}")
    assert metadata["workflow_run"]["id"] == RUN and not metadata["expired"]
    path = work / name
    with path.open("wb") as output:
        subprocess.run(["gh", "api", f"repos/{REPO}/actions/artifacts/{artifact_id}/zip"], stdout=output, check=True)
    assert digest(path) == sha, f"Artifact digest mismatch: {name}"
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None

with zipfile.ZipFile(work / "app-build.zip") as archive:
    checks = archive.read("SHA256SUMS.txt").decode()
    for line in checks.splitlines():
        sha, name = line.split(maxsplit=1)
        assert hashlib.sha256(archive.read(name.lstrip("*"))).hexdigest() == sha
    for name in ("TeleRC-v0.8.87.apk", "TeleRC-v0.8.87-source.zip"):
        (assets / name).write_bytes(archive.read(name))
    firmware = work / "firmware-original.zip"
    firmware.write_bytes(archive.read("TT-Balancer-ESP32S3-TeleRC-v0.3.0.zip"))
assert digest(assets / "TeleRC-v0.8.87.apk") == APK_SHA
for name in ("START_HERE.md", "BUILD_INFO.txt"):
    (assets / name).write_bytes((Path("docs/release-087") / name).read_bytes())
with zipfile.ZipFile(assets / "TT-Balancer-ESP32S3-TeleRC-v0.3.0.zip", "w", zipfile.ZIP_DEFLATED) as output:
    with zipfile.ZipFile(firmware) as original:
        for item in original.infolist():
            if not item.is_dir() and item.filename.startswith("bridge/TT_Balancer_ESP32S3/"):
                output.writestr(item.filename.removeprefix("bridge/"), original.read(item))
    for name in ("START_HERE.md", "BUILD_INFO.txt"):
        output.write(assets / name, name)
(assets / "TeleRC-v0.8.87-test-evidence.zip").write_bytes((work / "TeleRC-v0.8.87-test-evidence.zip").read_bytes())
for path in assets.glob("*.zip"):
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
files = sorted(assets.iterdir())
(assets / "SHA256SUMS.txt").write_text("".join(f"{digest(p)}  {p.name}\n" for p in files))
files = sorted(assets.iterdir())
# Create a draft, verify every uploaded byte via GitHub asset digests, then publish.
gh("release", "create", TAG, "--repo", REPO, "--target", SOURCE,
   "--title", "TeleRC v0.8.87 — Balance pre-test (paired firmware v0.3.0)",
   "--notes-file", "docs/release-087/RELEASE_NOTES.md", "--draft", "--prerelease", "--latest=false",
   *map(str, files))
release = next(r for r in api("releases?per_page=100") if r["tag_name"] == TAG)
remote = {a["name"]: a for a in api(f'releases/{release["id"]}/assets')}
assert set(remote) == {p.name for p in files}
for path in files:
    assert remote[path.name]["size"] == path.stat().st_size
    assert remote[path.name]["digest"] == f"sha256:{digest(path)}", path.name
assert release["target_commitish"] == SOURCE
gh("release", "edit", TAG, "--repo", REPO, "--draft=false", "--prerelease", "--latest=false")
release = api(f"releases/tags/{TAG}")
assert not release["draft"] and release["prerelease"]
assert api(f"git/ref/tags/{TAG}")["object"]["sha"] == SOURCE
assert api("releases/latest")["tag_name"] != TAG
print(release["html_url"])
print("Published verified prerelease assets; stable channel unchanged.")

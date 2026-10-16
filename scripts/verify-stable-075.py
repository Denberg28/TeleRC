"""Guard and audit owner-authorized stable publication; never move an existing tag."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

REPO = "Denberg28/TeleRC"
TAG = "v0.8.75"
SOURCE = os.environ["GITHUB_SHA"]

def api(path):
    return json.loads(subprocess.check_output(["gh", "api", f"repos/{REPO}/{path}"], text=True))

def require(condition, message):
    if not condition:
        raise SystemExit(message)

require(os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("GITHUB_REPOSITORY") == REPO, "Unexpected publication context")
require(os.environ.get("GITHUB_REF_NAME", "").startswith("fix/maze-dialogs-theme-075"), "Unexpected stable release branch")
require(SOURCE in Path("BUILD_INFO.txt").read_text(), "Build source mismatch")
subprocess.run(["sha256sum", "--strict", "--check", "SHA256SUMS.txt"], check=True)
expected = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in Path(".").iterdir() if p.is_file()}
if sys.argv[1:] == ["--before"]:
    for release in api("releases?per_page=100"):
        match = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)", release["tag_name"])
        if match and not release["draft"] and not release["prerelease"]:
            require(tuple(map(int, match.groups())) < (0, 8, 75), "An equal/newer stable release exists; do not supersede it")
    print("STABLE_PUBLICATION_GUARD_PASS", SOURCE)
elif sys.argv[1:] == ["--after"]:
    release = api(f"releases/tags/{TAG}")
    require(not release["draft"] and not release["prerelease"], "Not published stable")
    require(release["target_commitish"] == SOURCE, "Published source mismatch")
    tag = api(f"git/ref/tags/{TAG}")["object"]
    require(tag["type"] == "commit" and tag["sha"] == SOURCE, "Tag/source mismatch")
    require(api("releases/latest")["id"] == release["id"], "Latest stable mismatch")
    require({a["name"] for a in release["assets"]} == set(expected), "Published asset inventory mismatch")
    for asset in release["assets"]:
        require(asset["digest"] == "sha256:" + expected[asset["name"]], "Published artifact digest mismatch")
        print("VERIFIED_STABLE_ASSET", asset["name"], expected[asset["name"]])
    print("LATEST_STABLE_VERIFIED", TAG, "source", SOURCE)
    print("HARDWARE_HIL_BLOCKED; owner-selected stable channel, no field validation claim")
else:
    raise SystemExit("Expected --before or --after")

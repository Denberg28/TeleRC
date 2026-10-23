#!/usr/bin/env python3
"""Stage the already-tested Base build without rebuilding or changing provenance."""
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import sys
import zipfile

SOURCE = "c845503175d1d48468ec0201c8992f379c059c52"
RUN = 39889664702
ARTIFACT = 12473858951
APK_SHA = "b8045f9ff0bd6f03f1aa8b7e6f70d1f92d87ea0bea63eb9e21ae166c1f3a2e81"
SIGNER = "5e57bd9bb3d10d97e5abdad4c51dbf5282708db73f1844fac272403952f9f5ec"
EXPECTED = {
    "APK_VERIFICATION.json", "REALIGNMENT_V1.md", "RELEASE_STATUS.json",
    "START_HERE.md", "TeleRC-v1-test-evidence.zip", "TeleRC-v1.0-firmware.zip",
    "TeleRC-v1.0.0-source.zip", "TeleRC-v1.0.0.apk", "ecosystem-release.json",
}
def require(condition, message):
    if not condition:
        raise ValueError(message)

def stage(archive, output):
    with zipfile.ZipFile(archive) as z:
        require(z.testzip() is None, "Artifact CRC failure")
        names=z.namelist()
        require(len(names)==len(set(names)), "Duplicate artifact names")
        require(set(names)==EXPECTED|{"SHA256SUMS.txt"}, "Unexpected artifact inventory")
        require(all(PurePosixPath(n).name==n for n in names), "Non-flat artifact path")
        sums={}
        for line in z.read("SHA256SUMS.txt").decode("ascii").splitlines():
            digest, name=line.split("  ",1)
            require(name not in sums, "Duplicate checksum entry")
            sums[name]=digest
        require(set(sums)==EXPECTED, "Incomplete checksum inventory")
        for name,digest in sums.items():
            require(hashlib.sha256(z.read(name)).hexdigest()==digest, "Checksum failure: "+name)
        apk=z.read("TeleRC-v1.0.0.apk")
        require(hashlib.sha256(apk).hexdigest()==APK_SHA, "APK differs from verified signed build")
        v=json.loads(z.read("APK_VERIFICATION.json"))
        require(v["package"]=="io.github.denberg28.telerc", "Wrong Android application ID")
        require(v["version"]=="1.0.0" and v["version_code"]==1000, "Wrong Android version")
        require(v["source_commit"]==SOURCE and v["signer_sha256"]==[SIGNER], "Wrong source/signer")
        status=json.loads(z.read("RELEASE_STATUS.json"))
        require(status["source_commit"]==SOURCE and int(status["workflow_run"])==RUN, "Wrong build provenance")
        require(status["software_build_gates"]=="PASS" and status["stable_qualified"] is False, "Wrong build status")
        require(status["hardware_validation"]=="BLOCKED", "Unexpected hardware status")
        with zipfile.ZipFile(io.BytesIO(z.read("TeleRC-v1.0-firmware.zip"))) as f:
            require(f.testzip() is None, "Firmware CRC failure")
            manifests=[n for n in f.namelist() if n.endswith("FIRMWARE_MANIFEST.json")]
            require(len(manifests)==1, "Firmware manifest missing/ambiguous")
            manifest=json.loads(f.read(manifests[0]))
            require(manifest["source_commit"]==SOURCE and len(manifest["builds"])==6, "Wrong firmware source/profile count")
            prefix=str(PurePosixPath(manifests[0]).parent)
            prefix="" if prefix=="." else prefix+"/"
            for profile in manifest["builds"]:
                for image in profile["images"]:
                    data=f.read(prefix+image["file"])
                    require(len(data)==image["bytes"] and hashlib.sha256(data).hexdigest()==image["sha256"], "Firmware image mismatch")
        output=Path(output)
        output.mkdir(parents=True,exist_ok=False)
        for name in sorted(EXPECTED):
            (output/name).write_bytes(z.read(name))
    publication=dict(release_tag="v1.0.0",channel="prerelease",stable_qualified=False,
                     source_commit=SOURCE,build_run=RUN,build_artifact=ARTIFACT,
                     publication_commit=os.environ.get("GITHUB_SHA"),hardware_validation="BLOCKED",
                     android_unit_tests=173,actual_firmware_profiles=6)
    (output/"PUBLICATION.json").write_text(json.dumps(publication,indent=2)+"\n")
    (output/"SHA256SUMS.txt").write_text("".join(
        hashlib.sha256(p.read_bytes()).hexdigest()+"  "+p.name+"\n"
        for p in sorted(output.iterdir()) if p.name!="SHA256SUMS.txt"),encoding="ascii")
    print("Verified signed APK, complete checksums, six firmware profiles and exact build provenance.")

if __name__=="__main__":
    stage(sys.argv[1],sys.argv[2])

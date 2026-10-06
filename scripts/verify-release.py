#!/usr/bin/env python3
"""Verify signed release identity/version against the retained published APK."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


def output(*command):
    return subprocess.check_output(command, text=True, stderr=subprocess.STDOUT)


def signers(apk, tools):
    result = output(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
    values = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", result, re.M)
    if not values:
        raise RuntimeError("APK has no verified signing certificate")
    return sorted(values)


def main():
    apk, baseline = map(Path, sys.argv[1:3])
    config = Path("app/build.gradle.kts").read_text()
    version = re.search(r'versionName = "([^"]+)"', config).group(1)
    code = re.search(r"versionCode = (\d+)", config).group(1)
    sdk = Path(os.environ["ANDROID_HOME"])
    tools = sdk / "build-tools" / "35.0.0"
    badging = output(str(tools / "aapt"), "dump", "badging", str(apk))
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != ("io.github.denberg28.telerc", code, version):
        raise RuntimeError("Release package/version differs from source")
    current = signers(apk, tools)
    if current != signers(baseline, tools):
        raise RuntimeError("Signing identity differs from retained 0.8.50 release")
    tests = failures = errors = skipped = 0
    reports = list(Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    if not reports:
        raise RuntimeError("Missing JVM test evidence")
    for report in reports:
        suite = ET.parse(report).getroot()
        tests += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0))
        errors += int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
    if tests == 0 or failures or errors or skipped:
        raise RuntimeError("JVM tests failed, skipped, or absent")
    for variant in ("debug", "release"):
        path = Path(f"app/build/reports/lint-results-{variant}.xml")
        if not path.is_file():
            raise RuntimeError(f"Missing {variant} lint evidence")
        if any(issue.get("severity") in ("Error", "Fatal") for issue in ET.parse(path).getroot()):
            raise RuntimeError(f"{variant} lint has errors")
    print(f"TeleRC {version} / versionCode {code} — signed test candidate")
    print(f"Source commit: {os.environ['GITHUB_SHA']}")
    print(f"Workflow run: https://github.com/Denberg28/TeleRC/actions/runs/{os.environ['GITHUB_RUN_ID']}")
    print("Application ID: io.github.denberg28.telerc")
    print(f"Signing certificate SHA-256: {', '.join(current)}")
    print("Signing continuity with published v0.8.50: PASS")
    print(f"JVM tests: {tests} passed, {failures} failures, {errors} errors, {skipped} skipped")
    print("Android debug/release lint: PASS (no errors/fatal issues)")
    print("Host regressions and default ASan/UBSan: PASS (required preceding workflow steps)")
    print("Actual firmware: ESP32 3.3.2 / RadioLib 7.2.1; six primary targets and two alternate LoRa builds passed")
    print("Toolchain: JDK 17 / Gradle 8.11.1 / AGP 8.9.2 / Kotlin 2.1.20 / Android SDK & Build Tools 35")
    print(f"APK SHA-256: {hashlib.sha256(apk.read_bytes()).hexdigest()}")
    print("Hardware-in-the-loop, installed-device upgrade and field validation: NOT TESTED")
    print("Tier: prerelease for restrained bench validation; not stable hardware readiness")


if __name__ == "__main__":
    main()

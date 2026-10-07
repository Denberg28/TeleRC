#!/usr/bin/env python3
"""Compile the release's exact roles/profiles and package NVS-preserving images."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

MOTOR_FQBN = "esp32:esp32:esp32s3:CDCOnBoot=cdc,USBMode=hwcdc,FlashSize=4M,PartitionScheme=default,PSRAM=disabled,FlashMode=dio"
T3_FQBN = "esp32:esp32:lilygo_t3s3:CDCOnBoot=cdc,USBMode=hwcdc,PartitionScheme=default,PSRAM=enabled,FlashMode=dio,Revision=Radio_SX{}"
TARGETS = [
    ("motor-esp32s3", "TeleRCMotorController", MOTOR_FQBN, ""),
    ("lora-base-sx1262", "TeleRCLoRaBase", T3_FQBN.format(1262), "-DTELERC_RADIO_VARIANT=1262 -DTELERC_BASE_WIFI=0"),
    ("lora-rover-sx1262", "TeleRCLoRaRover", T3_FQBN.format(1262), "-DTELERC_RADIO_VARIANT=1262 -DTELERC_BASE_WIFI=0"),
    ("lora-base-sx1276", "TeleRCLoRaBase", T3_FQBN.format(1276), "-DTELERC_RADIO_VARIANT=1276 -DTELERC_BASE_WIFI=0"),
    ("lora-rover-sx1276", "TeleRCLoRaRover", T3_FQBN.format(1276), "-DTELERC_RADIO_VARIANT=1276 -DTELERC_BASE_WIFI=0"),
    ("pwm-converter-paired", "BTS7960PWMConverter", MOTOR_FQBN, "-DTELERC_PWM_PAIRED=1"),
    ("pwm-converter-four-input", "BTS7960PWMConverter", MOTOR_FQBN, "-DTELERC_PWM_PAIRED=0"),
    ("legacy-bridge", "TeleRCBridge", MOTOR_FQBN, ""),
    ("legacy-hybrid", "TeleRCHybridController", MOTOR_FQBN, ""),
    ("wifi-gateway", "TeleRCWiFiGateway", MOTOR_FQBN, ""),
]
OFFSETS = {"bootloader": "0x0", "partitions": "0x8000", "boot_app0": "0xe000", "application": "0x10000"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def package(root, version, commit, core_path, guide):
    """Require all ten completed builds; exclude whole-flash/merged images."""
    members = []
    manifest = {"version": version, "source_commit": commit,
                "esp32_core": "3.3.2", "radiolib": "7.2.1", "builds": []}
    boot_app0 = core_path / "tools/partitions/boot_app0.bin"
    if not boot_app0.is_file() or not boot_app0.stat().st_size:
        raise RuntimeError("Missing pinned boot_app0 image")
    for target, sketch, fqbn, flags in TARGETS:
        directory = root / target
        images = {"bootloader": f"{sketch}.ino.bootloader.bin",
                  "partitions": f"{sketch}.ino.partitions.bin",
                  "application": f"{sketch}.ino.bin", "boot_app0": "boot_app0.bin"}
        # Validate before copying, so an incomplete build cannot appear complete.
        for kind, filename in images.items():
            if kind == "boot_app0":
                continue
            path = directory / filename
            if not path.is_file() or not path.stat().st_size:
                raise RuntimeError(f"Missing {target}/{filename}")
        shutil.copyfile(boot_app0, directory / "boot_app0.bin")
        record = {"target": target, "sketch": sketch, "fqbn": fqbn, "extra_flags": flags,
                  "radio_defaults": "unprovisioned; valid existing NVS profile retained" if target.startswith("lora-") else None,
                  "images": []}
        for kind, filename in images.items():
            path = directory / filename
            relative = path.relative_to(root).as_posix()
            members.append(path)
            record["images"].append({"kind": kind, "path": relative, "offset": OFFSETS[kind],
                                     "bytes": path.stat().st_size, "sha256": digest(path)})
        manifest["builds"].append(record)
    manifest_path = root / "MANIFEST.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    guide_path = root / "FLASHING.md"
    shutil.copyfile(guide, guide_path)
    members.extend([manifest_path, guide_path])
    sums = root / "SHA256SUMS.txt"
    sums.write_text("".join(f"{digest(p)}  {p.relative_to(root).as_posix()}\n" for p in sorted(members)))
    members.append(sums)
    archive = root.parent / f"TeleRC-firmware-v{version}.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        for path in sorted(members):
            bundle.write(path, path.relative_to(root).as_posix())
    return archive


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, default=Path("firmware-build"))
    parser.add_argument("--core-path", type=Path, default=Path.home() / ".arduino15/packages/esp32/hardware/esp32/3.3.2")
    args = parser.parse_args()
    if args.output.exists():
        raise RuntimeError("Use a fresh output directory; stale firmware must not be packaged")
    args.output.mkdir(parents=True)
    for target, sketch, fqbn, flags in TARGETS:
        print(f"Compiling {target}: {fqbn} {flags}", flush=True)
        command = ["arduino-cli", "compile", "--fqbn", fqbn, "--libraries", "bridge/libraries",
                   "--output-dir", str(args.output / target)]
        if flags:
            command += ["--build-property", f"compiler.cpp.extra_flags={flags}"]
        subprocess.run(command + [f"bridge/{sketch}"], check=True)
    print(package(args.output, args.version, args.commit, args.core_path, Path("bridge/FIRMWARE_FLASHING.md")))


if __name__ == "__main__":
    main()

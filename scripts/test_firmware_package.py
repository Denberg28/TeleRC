"""Package regressions: missing builds fail; merged flash never enters updates."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("firmware", Path(__file__).with_name("build-firmware.py"))
firmware = importlib.util.module_from_spec(spec)
spec.loader.exec_module(firmware)
verify_spec = importlib.util.spec_from_file_location("verify_release", Path(__file__).with_name("verify-release.py"))
verify_release = importlib.util.module_from_spec(verify_spec)
verify_spec.loader.exec_module(verify_release)


class PackageTests(unittest.TestCase):
    def fixture(self, base):
        root = base / "build"
        for target, sketch, _, _ in firmware.TARGETS:
            directory = root / target
            directory.mkdir(parents=True)
            for suffix in (".bin", ".bootloader.bin", ".partitions.bin", ".merged.bin"):
                (directory / f"{sketch}.ino{suffix}").write_bytes(b"test-image")
        core = base / "core"
        (core / "tools/partitions").mkdir(parents=True)
        (core / "tools/partitions/boot_app0.bin").write_bytes(b"boot-app")
        guide = base / "guide.md"
        guide.write_text("Installation guide\n")
        return root, core, guide

    def test_complete_archive_has_roles_offsets_and_matching_hashes_without_merged_images(self):
        with tempfile.TemporaryDirectory() as directory:
            root, core, guide = self.fixture(Path(directory))
            archive = firmware.package(root, "0.8.53", "a" * 40, core, guide)
            verify_release.verify_firmware(archive, "0.8.53", "a" * 40)
            with self.assertRaises(RuntimeError):
                verify_release.verify_firmware(archive, "0.8.52", "a" * 40)
            with zipfile.ZipFile(archive) as bundle:
                self.assertEqual(43, len(bundle.namelist()))
                self.assertFalse(any("merged" in name for name in bundle.namelist()))
                manifest = json.loads(bundle.read("MANIFEST.json"))
                self.assertEqual("a" * 40, manifest["source_commit"])
                self.assertEqual(10, len(manifest["builds"]))
                for build in manifest["builds"]:
                    for image in build["images"]:
                        self.assertEqual(firmware.OFFSETS[image["kind"]], image["offset"])
                        self.assertEqual(image["sha256"], firmware.hashlib.sha256(bundle.read(image["path"])).hexdigest())
                for line in bundle.read("SHA256SUMS.txt").decode().splitlines():
                    expected, name = line.split("  ", 1)
                    self.assertEqual(expected, firmware.hashlib.sha256(bundle.read(name)).hexdigest())

    def test_corrupt_image_is_rejected_even_with_unchanged_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root, core, guide = self.fixture(Path(directory))
            archive = firmware.package(root, "0.8.53", "a" * 40, core, guide)
            corrupt = Path(directory) / "corrupt.zip"
            with zipfile.ZipFile(archive) as original, zipfile.ZipFile(corrupt, "w") as changed:
                for name in original.namelist():
                    changed.writestr(name, b"corrupt" if name == "motor-esp32s3/TeleRCMotorController.ino.bin" else original.read(name))
            with self.assertRaises(RuntimeError):
                verify_release.verify_firmware(corrupt, "0.8.53", "a" * 40)

    def test_incomplete_target_cannot_produce_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            root, core, guide = self.fixture(Path(directory))
            (root / "lora-rover-sx1276/TeleRCLoRaRover.ino.bin").unlink()
            with self.assertRaises(RuntimeError):
                firmware.package(root, "0.8.53", "a" * 40, core, guide)
            self.assertFalse((root.parent / "TeleRC-firmware-v0.8.53.zip").exists())


if __name__ == "__main__":
    unittest.main()

"""Free completed firmware/native toolchains before downloading the emulator image."""
import os
from pathlib import Path
import shutil

if os.environ.get('GITHUB_ACTIONS') != 'true':
    raise SystemExit('This cleanup is restricted to the disposable GitHub Actions runner.')
sdk = Path(os.environ['ANDROID_HOME'])
print('Free bytes before UI tooling:', shutil.disk_usage(sdk).free)
# All Android APKs and all six firmware configurations have finished compiling.
# Keep deliverables, evidence, source, Gradle caches and the retained signer untouched.
for path in (sdk / 'ndk', Path.home() / '.arduino15', Path.home() / '.cache/arduino'):
    if path.is_dir():
        print('Removing completed toolchain/cache:', path)
        shutil.rmtree(path)
print('Free bytes before emulator image download:', shutil.disk_usage(sdk).free)
if shutil.disk_usage(sdk).free < 9 * 1024**3:
    raise SystemExit('Less than 9 GiB free for emulator image, AVD and capture evidence.')

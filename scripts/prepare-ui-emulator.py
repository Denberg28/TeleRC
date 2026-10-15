"""Normalize duplicate AVD properties and reclaim only disposable CI NDK tooling."""
import os
from pathlib import Path
import shutil

# APK assembly is complete. Native source compilation is not part of this UI smoke check.
# This SDK lives on an ephemeral GitHub runner, never on the user's workstation.
sdk = Path(os.environ['ANDROID_HOME'])
ndk = sdk / 'ndk'
if ndk.is_dir():
    shutil.rmtree(ndk)

config = Path(os.environ['ANDROID_AVD_HOME']) / 'test.avd' / 'config.ini'
values = {'hw.cpu.ncore': '4', 'hw.ramSize': '2048', 'disk.dataPartition.size': '2G'}
lines = [line for line in config.read_text().splitlines() if line.split('=', 1)[0].strip() not in values]
config.write_text('\n'.join(lines) + '\n' + ''.join(f'{key}={value}\n' for key,value in values.items()))
print('Normalized emulator storage/CPU/RAM properties:', values)
print('Available runner disk bytes:', shutil.disk_usage(config.parent).free)

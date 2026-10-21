"""Verify compiled video-only USB default registration and permission scope."""
import os,re,subprocess,sys,zipfile
from pathlib import Path
candidate=Path(sys.argv[1])
build_tools=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
def run(*args):return subprocess.check_output(list(map(str,args)),text=True)
manifest = run(build_tools / 'aapt', 'dump', 'xmltree', candidate, 'AndroidManifest.xml')
attach_attributes = [line for line in manifest.splitlines() if 'A: android:name' in line and 'android.hardware.usb.action.USB_DEVICE_ATTACHED' in line]
assert len(attach_attributes) == 2, 'USB intent/metadata pair missing'
assert re.search(r'android:launchMode[^\n]*0x1\b', manifest), 'Expected singleTop Activity'
# Release optimization can shorten resource ZIP paths. Resolve the named XML
# through the compiled table instead of assuming its source filename survives.
resource_table = run(build_tools / 'aapt', 'dump', '--values', 'resources', candidate)
resource_lines = resource_table.splitlines()
filter_paths = set()
for index, line in enumerate(resource_lines):
    if ':xml/usb_fpv_devices' in line:
        filter_paths.update(re.findall(r'res/[^\s"<>]+\.xml', '\n'.join(resource_lines[index:index + 2])))
with zipfile.ZipFile(candidate) as archive:
    filter_paths.intersection_update(archive.namelist())
assert len(filter_paths) == 1, f'Could not resolve compiled USB filter: {filter_paths}'
device_filter = run(build_tools / 'aapt', 'dump', 'xmltree', candidate, filter_paths.pop())
assert re.search(r'A: class(?:[^\n]*0xe\b|=\"14\")', device_filter), 'Missing video class filter'
assert device_filter.count('E: usb-device') == 1, 'Unexpected USB filter expansion'
permissions = run(build_tools / 'aapt', 'dump', 'permissions', candidate)
assert 'android.permission.CAMERA' in permissions
assert not any(p in permissions for p in ('RECORD_AUDIO', 'MANAGE_EXTERNAL_STORAGE',
    'READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE', 'FOREGROUND_SERVICE'))
run(build_tools / 'zipalign', '-c', '-P', '16', '4', candidate)
with zipfile.ZipFile(candidate) as archive:
    assert archive.testzip() is None, 'APK CRC failure'
print('PASS: compiled video-only USB attach/default registration, permissions, APK CRC/alignment')

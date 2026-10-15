"""Native emulator smoke/geometry checks and review screenshots; never connects a rover."""
import base64
import io
from pathlib import Path
import re
import shlex
import sys
import subprocess
import time
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

PKG = 'io.github.denberg28.telerc'
OUT = Path('app/build/ui-evidence')
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args, **kwargs):
    return subprocess.check_output(['adb', *args], timeout=45, **kwargs)

def shell(command, **kwargs):
    return adb('shell', command, **kwargs)

adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
shell('wm size 1536x720')
shell('wm density 320')
shell('settings put system font_scale 1.0')
shell('input keyevent 82')
def capture_failure(kind, value, traceback):
    try:
        print('SETUP_FAILURE_PNG_BASE64=' + base64.b64encode(adb('exec-out', 'screencap', '-p')).decode(), flush=True)
    finally:
        sys.__excepthook__(kind, value, traceback)
sys.excepthook = capture_failure
shots = []
for accent in ('Blue', 'Teal', 'Violet', 'Slate'):
    for theme in ('LIGHT', 'SEPIA', 'DARK'):
        shell(f'am force-stop {PKG}')
        prefs = f'<?xml version="1.0" encoding="utf-8" standalone="yes"?><map><string name="background">{theme}</string><string name="accent">{accent}</string></map>'
        encoded = base64.b64encode(prefs.encode()).decode()
        shell(f'run-as {PKG} sh -c ' + shlex.quote(f'mkdir -p shared_prefs; echo {encoded} | base64 -d > shared_prefs/appearance.xml'))
        shell(f'am start -W -n {PKG}/.MainActivity')
        time.sleep(1)
        assert shell(f'pidof {PKG}').strip(), 'App did not stay alive'
        shell('uiautomator dump /data/local/tmp/setup.xml')
        xml = shell('cat /data/local/tmp/setup.xml')
        name = f'{theme.lower()}-{accent.lower()}'
        (OUT / f'{name}.xml').write_bytes(xml)
        nodes = list(ET.fromstring(xml).iter('node'))
        def bounds(node):
            return tuple(map(int, re.findall(r'\d+', node.attrib['bounds'])))
        def find(key, value):
            return next(n for n in nodes if n.attrib.get(key) == value)
        left = bounds(find('content-desc', 'Connection settings'))
        right = bounds(find('content-desc', 'Preferences'))
        assert abs((left[2]-left[0]) - (right[2]-right[0])) <= 1, (name, left, right)
        assert left[1] == right[1] and left[3] == right[3], (name, left, right)
        for label in ('Controls ›', 'Connect', 'Diagnose link', 'Check for updates', 'Control response ›'):
            x1,y1,x2,y2 = bounds(find('text', label))
            assert x2>x1 and y2>y1 and 0<=y1<y2<=720, (name,label,(x1,y1,x2,y2))
        image = Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('RGB')
        image.save(OUT / f'{name}.png')
        shots.append((name, image))
        print('NATIVE_UI_PASS', name, 'equal panel bounds', left, right, flush=True)
# Large fonts use vertical scrolling rather than clipping the panel.
shell('settings put system font_scale 1.3')
shell(f'am force-stop {PKG}')
shell(f'am start -W -n {PKG}/.MainActivity')
time.sleep(1)
(OUT / 'large-font.png').write_bytes(adb('exec-out', 'screencap', '-p'))
shell('settings put system font_scale 1.0')
shell(f'am force-stop {PKG}')
# A compact review sheet is recoverable through text logs when Actions storage is full.
sheet = Image.new('RGB', (1536, 1040), '#303638')
draw = ImageDraw.Draw(sheet)
for index, (name, shot) in enumerate(shots):
    x = index % 3 * 512
    y = index // 3 * 260
    draw.text((x+8, y+4), name, fill='#e0e4e4')
    sheet.paste(shot.resize((512, 240)), (x, y+20))
sheet.save(OUT / 'setup-contact-sheet.png')
buffer = io.BytesIO()
sheet.save(buffer, format='PNG')
print('SETUP_REVIEW_PNG_BASE64=' + base64.b64encode(buffer.getvalue()).decode(), flush=True)

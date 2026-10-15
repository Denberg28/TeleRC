"""Native UI checks: theme migration/persistence, both pages, geometry and brightness."""
import base64
import io
from pathlib import Path
import re
import shlex
import sys
import subprocess
import time
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageStat

PKG = 'io.github.denberg28.telerc'
OUT = Path('app/build/ui-evidence')
OUT.mkdir(parents=True, exist_ok=True)
(OUT / 'ATTEMPT_STARTED.txt').write_text('Native capture script reached; subsequent failures block packaging.\n')


def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=45)


def shell(command):
    return adb('shell', command)


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.attrib['bounds'])))


def nodes():
    # A cold API 35 emulator can surface its own launcher ANR over the app.
    # Only dismiss that exact external dialog; TeleRC ANRs/crashes still fail.
    for attempt in range(3):
        shell('uiautomator dump /data/local/tmp/telerc-ui.xml')
        xml = shell('cat /data/local/tmp/telerc-ui.xml')
        items = list(ET.fromstring(xml).iter('node'))
        launcher_anr = any(n.attrib.get('package') == 'android' and
                           n.attrib.get('text') == "Pixel Launcher isn't responding" for n in items)
        if not launcher_anr:
            return xml, items
        close = next(n for n in items if n.attrib.get('package') == 'android' and
                     n.attrib.get('resource-id') == 'android:id/aerr_close')
        tap(close)
        print('EXTERNAL_EMULATOR_DIALOG_DISMISSED Pixel Launcher', attempt + 1, flush=True)
    raise AssertionError('Emulator launcher ANR repeatedly obstructed the app')


def find(items, key, value):
    return next(n for n in items if n.attrib.get(key) == value)


def tap(node):
    x1, y1, x2, y2 = bounds(node)
    shell(f'input tap {(x1+x2)//2} {(y1+y2)//2}')
    time.sleep(.5)


def launch():
    shell(f'am start -W -n {PKG}/.MainActivity')
    time.sleep(.5)
    assert shell(f'pidof {PKG}').strip(), 'App did not stay alive'


def set_prefs(body):
    shell(f'am force-stop {PKG}')
    prefs = f'<?xml version="1.0" encoding="utf-8" standalone="yes"?><map>{body}</map>'
    encoded = base64.b64encode(prefs.encode()).decode()
    shell(f'run-as {PKG} sh -c ' + shlex.quote(f'mkdir -p shared_prefs; echo {encoded} | base64 -d > shared_prefs/appearance.xml'))
    launch()


def screenshot(name, theme):
    image = Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('RGB')
    if theme == 'PASTEL':
        # Most of either page must be visibly light, rather than the old charcoal.
        mean = ImageStat.Stat(image).mean
        assert min(mean) > 190, (name, 'pastel unexpectedly dark', mean)
    image.save(OUT / f'{name}.png')
    return image


adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
shell(f'pm grant {PKG} android.permission.ACCESS_COARSE_LOCATION')
shell(f'pm grant {PKG} android.permission.ACCESS_FINE_LOCATION')
shell('wm size 1536x720')
shell('wm density 320')
shell('settings put system font_scale 1.0')
shell('input keyevent 82')


def capture_failure(kind, value, traceback):
    try:
        print('SETUP_FAILURE_PNG_BASE64=' + base64.b64encode(adb('exec-out', 'screencap', '-p')).decode(), flush=True)
        shell('uiautomator dump /data/local/tmp/failure.xml')
        print('SETUP_FAILURE_XML=' + shell('cat /data/local/tmp/failure.xml').decode(), flush=True)
    finally:
        sys.__excepthook__(kind, value, traceback)


sys.excepthook = capture_failure
# Upgrade the exact dark preference represented by the user's screenshot.
set_prefs('<string name="background">DARK</string><boolean name="dark" value="true"/>')
xml, items = nodes()
find(items, 'text', 'Background · Pastel ▾')
screenshot('upgrade-dark-to-pastel', 'PASTEL')
shell(f'am force-stop {PKG}')
launch()
xml, items = nodes()
find(items, 'text', 'Background · Pastel ▾')
# A later explicit Dark choice must survive activity recreation and process restart.
tap(find(items, 'text', 'Background · Pastel ▾'))
_, items = nodes()
# The native theme menu is scrollable on short landscape windows.
for attempt in range(3):
    dark = next((n for n in items if n.attrib.get('text') == 'Dark'), None)
    if dark is not None:
        x1, y1, x2, y2 = bounds(dark)
        if y2 - y1 >= 40:
            tap(dark)
            break
    menu = next(n for n in items if n.attrib.get('scrollable') == 'true')
    x1, y1, x2, y2 = bounds(menu)
    x = (x1+x2)//2
    shell(f'input swipe {x} {y2-24} {x} {y1+24} 300')
    time.sleep(.5)
    _, items = nodes()
else:
    raise AssertionError('Dark theme could not be reached by scrolling the native menu')
shell(f'am force-stop {PKG}')
launch()
_, items = nodes()
find(items, 'text', 'Background · Dark ▾')
print('NATIVE_UI_PASS one-time dark-to-pastel migration and later Dark persistence', flush=True)

shots = []
for accent in ('Blue', 'Teal', 'Violet', 'Slate'):
    for theme in ('PASTEL', 'LIGHT', 'SEPIA', 'DARK'):
        set_prefs(f'<string name="background">{theme}</string><string name="accent">{accent}</string><boolean name="pastel_theme_v1" value="true"/>')
        xml, items = nodes()
        name = f'{theme.lower()}-{accent.lower()}'
        (OUT / f'{name}-setup.xml').write_bytes(xml)
        find(items, 'text', f'Background · {theme.title()} ▾')
        left = bounds(find(items, 'content-desc', 'Connection settings'))
        right = bounds(find(items, 'content-desc', 'Preferences'))
        assert abs((left[2]-left[0]) - (right[2]-right[0])) <= 1, (name, left, right)
        assert left[1] == right[1] and left[3] == right[3], (name, left, right)
        for label in ('Controls ›', 'Connect', 'Diagnose link', 'Check for updates', 'Control response ›'):
            x1, y1, x2, y2 = bounds(find(items, 'text', label))
            assert x2>x1 and 0<=y1<y2<=720, (name,label,(x1,y1,x2,y2))
        image = screenshot(name+'-setup', theme)
        if theme == 'PASTEL':
            shots.append((name+' setup', image))
        tap(find(items, 'text', 'Controls ›'))
        xml, items = nodes()
        (OUT / f'{name}-controls.xml').write_bytes(xml)
        for label in ('STEER', 'DRIVE', 'ARM / DISARM', 'ENABLE CONTROL', 'Show camera', 'Connect FPV', 'F1', 'F2', 'F3'):
            x1, y1, x2, y2 = bounds(find(items, 'text', label))
            assert x2>x1 and 0<=y1<y2<=720, (name,label,(x1,y1,x2,y2))
        image = screenshot(name+'-controls', theme)
        if theme == 'PASTEL':
            shots.append((name+' controls', image))
        print('NATIVE_UI_PASS', name, 'Setup + Controls; equal panels', left, right, flush=True)

# Large-font capture starts in Setup on the preferred Pastel background.
set_prefs('<string name="background">PASTEL</string><boolean name="pastel_theme_v1" value="true"/>')
shell('settings put system font_scale 1.3')
shell(f'am force-stop {PKG}')
launch()
screenshot('large-font-pastel', 'PASTEL')
shell('settings put system font_scale 1.0')
shell(f'am force-stop {PKG}')
# Eight native pastel views in one compact review sheet; raw captures stay in evidence.
sheet = Image.new('RGB', (1536, 1600), '#e8eff8')
draw = ImageDraw.Draw(sheet)
for index, (name, shot) in enumerate(shots):
    x = index % 2 * 768
    y = index // 2 * 400
    draw.text((x+12, y+8), name, fill='#344256')
    sheet.paste(shot.resize((768, 360)), (x, y+30))
sheet.save(OUT / 'pastel-contact-sheet.png')
buffer = io.BytesIO()
sheet.save(buffer, format='PNG')
print('SETUP_REVIEW_PNG_BASE64=' + base64.b64encode(buffer.getvalue()).decode(), flush=True)

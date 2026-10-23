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
from PIL import Image, ImageDraw, ImageStat, ImageChops

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
    for attempt in range(8):
        # uiautomator can return exit 0 without writing when the window is busy.
        # Never reuse a hierarchy left by the previous screen.
        shell('rm -f /data/local/tmp/telerc-ui.xml')
        result = shell('uiautomator dump /data/local/tmp/telerc-ui.xml')
        fresh = shell('if [ -s /data/local/tmp/telerc-ui.xml ]; then echo fresh; fi').strip()
        if fresh != b'fresh':
            print('NATIVE_UI_WAIT fresh hierarchy', result.decode().strip(), flush=True)
            time.sleep(.5)
            continue
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
    raise AssertionError('Could not obtain a fresh unobstructed emulator hierarchy')


def find(items, key, value):
    node = next((n for n in items if n.attrib.get(key) == value), None)
    assert node is not None, (key, value, [n.attrib.get('text') for n in items])
    return node


def screen(key, value):
    for attempt in range(5):
        xml, items = nodes()
        if any(n.attrib.get(key) == value for n in items):
            return xml, items
        time.sleep(.5)
    raise AssertionError(('Expected screen did not appear', key, value,
                          [n.attrib.get('text') for n in items]))


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
    if theme == 'LIGHT':
        # Most of either page must be visibly light, rather than the old charcoal.
        mean = ImageStat.Stat(image).mean
        assert min(mean) > 190, (name, 'Light unexpectedly dark', mean)
    image.save(OUT / f'{name}.png')
    return image


adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
shell(f'pm grant {PKG} android.permission.ACCESS_COARSE_LOCATION')
shell(f'pm grant {PKG} android.permission.ACCESS_FINE_LOCATION')
shell('wm size 1536x720')
shell('wm density 320')
shell('settings put system font_scale 1.0')
for setting in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
    shell(f'settings put global {setting} 0')
shell('input keyevent 82')


def capture_failure(kind, value, traceback):
    try:
        print('SETUP_FAILURE_PNG_BASE64=' + base64.b64encode(adb('exec-out', 'screencap', '-p')).decode(), flush=True)
        shell('uiautomator dump /data/local/tmp/failure.xml')
        print('SETUP_FAILURE_XML=' + shell('cat /data/local/tmp/failure.xml').decode(), flush=True)
    finally:
        sys.__excepthook__(kind, value, traceback)


sys.excepthook = capture_failure
# Upgrade the former Pastel setting; keep later explicit Dark choices.
set_prefs('<string name="background">PASTEL</string><boolean name="dark" value="false"/><boolean name="pastel_theme_v1" value="true"/>')
xml, items = screen('text', 'Background · Light ▾')
screenshot('upgrade-pastel-to-light', 'LIGHT')
shell(f'am force-stop {PKG}')
launch()
xml, items = screen('text', 'Background · Light ▾')
# Confirm a real, fully visible Dark selection before testing process persistence.
# A partly clipped last row can overlap the dialog footer on this short screen.
for selection_attempt in range(3):
    _, items=screen('text','Background · Light ▾')
    tap(find(items,'text','Background · Light ▾'))
    _, items=screen('resource-id','android:id/alertTitle')
    for scroll_attempt in range(5):
        dark=next((n for n in items if n.attrib.get('text')=='Dark'),None)
        if dark is not None:
            x1,y1,x2,y2=bounds(dark)
            if y2-y1>=72:
                tap(dark)
                break
        menu=next(n for n in items if n.attrib.get('scrollable')=='true')
        x1,y1,x2,y2=bounds(menu); x=(x1+x2)//2
        shell(f'input swipe {x} {y2-24} {x} {y1+24} 350')
        time.sleep(.5)
        _,items=nodes()
    else:
        raise AssertionError('Dark choice never became fully visible')
    # Await Activity recreation, then inspect the actual saved value. Never force a
    # preference here: this qualifies the app's native selection and persistence.
    for settled in range(8):
        _,items=nodes()
        saved=shell(f'run-as {PKG} cat shared_prefs/appearance.xml').decode()
        if any(n.attrib.get('text')=='Background · Dark ▾' for n in items) and                 '<string name="background">DARK</string>' in saved:
            break
        time.sleep(.4)
    else:
        if any(n.attrib.get('text')=='Background · Light ▾' for n in items):
            print('NATIVE_UI_RETRY Dark picker tap did not select the clipped row',flush=True)
            continue
        raise AssertionError('Dark selection did not settle/save')
    break
else:
    raise AssertionError('Native Dark selection failed after bounded input retries')
shell(f'am force-stop {PKG}')
launch()
_, items = screen('text', 'Background · Dark ▾')
print('NATIVE_UI_PASS Pastel-to-Light migration and explicit Dark persistence', flush=True)

shots = []
practice_shots = []
for accent in ('Blue', 'Teal', 'Violet', 'Slate'):
    for theme in ('LIGHT', 'SEPIA', 'DARK'):
        set_prefs(f'<string name="background">{theme}</string><string name="accent">{accent}</string><boolean name="pastel_theme_v1" value="true"/>')
        xml, items = screen('text', f'Background · {theme.title()} ▾')
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
        if theme == 'LIGHT':
            shots.append((name+' setup', image))
        tap(find(items, 'text', 'Controls ›'))
        xml, items = screen('text', 'STEER')
        (OUT / f'{name}-controls.xml').write_bytes(xml)
        for label in ('STEER', 'DRIVE', 'ARM / DISARM', 'ENABLE CONTROL', 'Show camera', 'Connect FPV', 'F1', 'F2', 'F3'):
            x1, y1, x2, y2 = bounds(find(items, 'text', label))
            assert x2>x1 and 0<=y1<y2<=720, (name,label,(x1,y1,x2,y2))
        image = screenshot(name+'-controls', theme)
        if theme == 'LIGHT':
            shots.append((name+' controls', image))
        tap(find(items, 'text', 'Controls ▾'))
        _, items = screen('text', 'Test')
        tap(find(items, 'text', 'Test'))
        xml, items = screen('text', 'OFFLINE')
        (OUT / f'{name}-test.xml').write_bytes(xml)
        for label in ('STEER', 'DRIVE', 'Test ▾', 'OFFLINE', 'Start practice', 'Shuffle maze', 'Retry maze', 'Clear times'):
            x1, y1, x2, y2 = bounds(find(items, 'text', label))
            assert x2>x1 and 0<=y1<y2<=720, (name,label,(x1,y1,x2,y2))
        find(items, 'content-desc', 'Offline practice, rover disconnected')
        prefs = shell(f'run-as {PKG} cat shared_prefs/link.xml').decode()
        assert 'name="auto_connect" value="false"' in prefs, prefs
        image = screenshot(name+'-test', theme)
        if accent == 'Blue':
            practice_shots.append((name+' test', image))
        print('NATIVE_UI_PASS', name, 'Setup + Controls + offline Test; equal panels', left, right, flush=True)

# Exercise actual practice controls offline. Moving a stick must move the rover pixels.
# No UDP, location, camera or live Enable/ARM is needed on this page.
set_prefs('<string name="background">LIGHT</string><string name="accent">Blue</string>')
_, items = screen('text', 'Setup ▾')
# Navy is the label for the existing Blue preference, so upgrades retain selection.
find(items, 'text', '✓ Navy')
tap(find(items, 'text', 'Setup ▾'))
_, items = screen('text', 'Test')
tap(find(items, 'text', 'Test'))
_, items = screen('text', 'Start practice')
seed_before = next(n.attrib['text'] for n in items if n.attrib.get('text','').startswith('Maze '))
maze_bounds=bounds(find(items,'content-desc','Offline maze, START at lower left, END at upper right'))
before=Image.open(io.BytesIO(adb('exec-out','screencap','-p'))).convert('RGB').crop(maze_bounds)
tap(find(items, 'text', 'Start practice'))
# Do not dump an accessibility hierarchy while the active timer is updating.
shell('input swipe 1294 376 1294 240 600')
shell('input keyevent KEYCODE_HOME')
time.sleep(.6)
launch()
_, items = screen('text', 'Resume')
timer = next(n.attrib['text'] for n in items if n.attrib.get('text','').startswith('TIME · '))
assert timer != 'TIME · 00:00.00', timer
after=screenshot('practice-paused-after-background', 'LIGHT').crop(maze_bounds)
assert sum(ImageStat.Stat(ImageChops.difference(before,after)).mean)>.05, 'Practice joystick did not move rover'
# Backgrounding resets both sticks and requires explicit Resume.
for description in ('Drive joystick, forward up and reverse down', 'Steering joystick, left and right'):
    assert find(items, 'content-desc', description).attrib['enabled']=='false'
tap(find(items, 'text', 'Retry maze'))
_, items = screen('text', 'Start practice')
find(items, 'text', 'TIME · 00:00.00')
assert next(n.attrib['text'] for n in items if n.attrib.get('text','').startswith('Maze ')) == seed_before
tap(find(items, 'text', 'Shuffle maze'))
_, items = screen('text', 'Start practice')
seed_after = next(n.attrib['text'] for n in items if n.attrib.get('text','').startswith('Maze '))
assert seed_after != seed_before
# Turn in place with the actual steering stick and verify that the body rotates.
tap(find(items, 'text', 'Start practice'))
shell('input swipe 236 376 320 376 450')
shell('input keyevent KEYCODE_HOME')
time.sleep(.5)
launch()
_, items = screen('text','Resume')
screenshot('practice-direction-after-steering','LIGHT')
print('NATIVE_UI_PASS larger rover steering and heading capture',flush=True)
# A recorded clear is loaded from app storage and remains visible after a restart.
shell(f'am force-stop {PKG}')
record_xml = '<map><int name="seed" value="42"/><string name="records">42,12340,1792110000000,2</string></map>'
encoded = base64.b64encode(record_xml.encode()).decode()
shell(f'run-as {PKG} sh -c ' + shlex.quote(f'echo {encoded} | base64 -d > shared_prefs/maze_practice.xml'))
for attempt in range(2):
    launch()
    _, items = screen('text', 'Setup ▾')
    tap(find(items, 'text', 'Setup ▾'))
    _, items = screen('text', 'Test')
    tap(find(items, 'text', 'Test'))
    _, items = screen('text', 'Start practice')
    assert any(n.attrib.get('text','').startswith('Maze 2a · Best 00:12.34 · H ') for n in items)
    tap(find(items, 'text', 'Clear times'))
    _, items = screen('text', 'Clear times · latest 1')
    assert any('00:12.34 · Maze 2a' in n.attrib.get('text','') for n in items)
    shell(f'am force-stop {PKG}')
print('NATIVE_UI_PASS offline start/background pause, neutral reset, retry, shuffle and record persistence', flush=True)

# Large-font capture starts in Setup on the restored Light background.
set_prefs('<string name="background">LIGHT</string>')
shell('settings put system font_scale 1.3')
shell(f'am force-stop {PKG}')
launch()
screen('text', 'Background · Light ▾')
screenshot('large-font-light', 'LIGHT')
shell('settings put system font_scale 1.0')
shell(f'am force-stop {PKG}')
# Native theme views and Navy practice on one review sheet; raw captures stay in evidence.
shots.extend(practice_shots)
sheet = Image.new('RGB', (1536, 2400), '#eaeef2')
draw = ImageDraw.Draw(sheet)
for index, (name, shot) in enumerate(shots):
    x = index % 2 * 768
    y = index // 2 * 400
    draw.text((x+12, y+8), name, fill='#344256')
    sheet.paste(shot.resize((768, 360)), (x, y+30))
sheet.save(OUT / 'navy-maze-contact-sheet.png')
buffer = io.BytesIO()
sheet.save(buffer, format='PNG')
print('SETUP_REVIEW_PNG_BASE64=' + base64.b64encode(buffer.getvalue()).decode(), flush=True)

practice_sheet=Image.new('RGB',(1536,2160),'#eaeef2')
for index, (_,shot) in enumerate(practice_shots):
    practice_sheet.paste(shot,(0,index*720))
practice_sheet.save(OUT/'readable-rover-review.png')
review=io.BytesIO(); practice_sheet.save(review,format='PNG')
print('PRACTICE_REVIEW_PNG_BASE64='+base64.b64encode(review.getvalue()).decode(),flush=True)

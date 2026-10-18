"""Limit this release to offline replay rewards/restart and Waypoint workflow presentation."""
import pathlib
import subprocess
BASE = 'f3eba8b620ca49b1030434fae6bb7f8dc94a246c'
root = pathlib.Path(__file__).resolve().parent.parent

def baseline(path):
    return subprocess.check_output(['git', 'show', f'{BASE}:{path}'], cwd=root)

def method(text, name):
    start = text.index('    private fun ' + name + '(')
    end = text.index('\n    }', start) + 6
    return text[start:end]

main = 'app/src/main/java/io/github/denberg28/telerc/MainActivity.kt'
old, new = baseline(main).decode(), (root/main).read_text()
# Test implementation is authorized; Waypoint may change only explanatory hints.
old_hint = method(old, 'renderWaypoint')
new_hint = method(new, 'renderWaypoint')
for before, after in [
    ('Planning only. Use FC Wi-Fi to navigate.', 'ELRS handset link is planning only. Use ArduRover Wi-Fi + GPS.'),
    ('Planning only. Needs ArduRover + GPS.', 'Mini rover cannot navigate GPS routes. Use ArduRover Wi-Fi + GPS.'),
    ('Planning only on this LoRa gateway.', 'This LoRa gateway is planning only. Use a compatible ArduRover Wi-Fi link.'),
    ('Connect in Setup, then start.', 'Connect to ArduRover Wi-Fi in Setup, then tap Mission start.')]:
    old_hint = old_hint.replace(before, after)
assert old_hint == new_hint, 'Waypoint dispatch/authority changed beyond hints'
for name in ['renderTest', 'renderWaypoint']:
    old = old.replace(method(old, name), '')
    new = new.replace(method(new, name), '')
assert old == new, 'MainActivity changed outside Test and Waypoint hints'
for name in ['Mavlink.kt', 'WaypointNavigation.kt', 'WaypointProtocol.kt', 'WaypointMap.kt',
             'RouteSession.kt', 'RouteMapView.kt', 'UsbFpvView.kt', 'FpvReconnectPolicy.kt', 'RoverKinematics.kt',
             'AppUpdater.kt', 'UpdateMetadata.kt', 'LiveControlGate.kt', 'BackpackRecovery.kt',
             'MazePracticeDialogs.kt', 'AppDialog.kt', 'PopupLayout.kt']:
    path = 'app/src/main/java/io/github/denberg28/telerc/' + name
    assert (root/path).read_bytes() == baseline(path), f'Protected source changed: {name}'
practice = 'app/src/main/java/io/github/denberg28/telerc/MazePractice.kt'
old, new = baseline(practice).decode(), (root/practice).read_text()
assert old.split('/** Local input')[0] == new.split('/** Local input')[0], 'Fixed levels, geometry or progress changed'
market = 'app/src/main/java/io/github/denberg28/telerc/MazeMarket.kt'
def catalog(text):
    return text.split('internal object MazeMarket {')[1].split('\n}')[0]
assert catalog(baseline(market).decode()) == catalog((root/market).read_text()), 'Catalog/prices/base quotas changed'
subprocess.run(['git','diff','--exit-code',BASE,'HEAD','--','bridge','tests'],cwd=root,check=True)
print('PASS: offline reward/restart scope; connection, authority, navigation engine, fixed courses/physics, catalog, popups, camera and firmware preserved')

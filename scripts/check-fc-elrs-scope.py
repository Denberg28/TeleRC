"""Protect existing offline practice, navigation safety engine, codecs and camera behavior."""
import pathlib
import subprocess
BASE = '86ef96319b6fe97147ca4b071de510cf3bb1ac4e'
root = pathlib.Path(__file__).resolve().parent.parent

def baseline(path):
    return subprocess.check_output(['git', 'show', f'{BASE}:{path}'], cwd=root)

main = 'app/src/main/java/io/github/denberg28/telerc/MainActivity.kt'
def method(text, name):
    start = text.index('    private fun ' + name + '(')
    end = text.index('\n    }', start) + 6
    return text[start:end]
old, new = baseline(main).decode(), (root/main).read_text()
assert method(old, 'renderTest') == method(new, 'renderTest'), 'Offline Test changed'
for name in ['Mavlink.kt', 'WaypointNavigation.kt', 'WaypointProtocol.kt', 'WaypointMap.kt',
             'RouteSession.kt', 'RouteMapView.kt', 'UsbFpvView.kt', 'FpvReconnectPolicy.kt']:
    path = 'app/src/main/java/io/github/denberg28/telerc/' + name
    assert (root/path).read_bytes() == baseline(path), f'Protected source changed: {name}'
subprocess.run(['git','diff','--exit-code',BASE,'HEAD','--','bridge','tests'],cwd=root,check=True)
print('PASS: Test, navigation safety engine/protocol, manual codec, route session/map and FPV preserved')

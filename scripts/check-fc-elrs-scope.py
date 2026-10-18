"""Protect existing offline practice, navigation safety engine, codecs and camera behavior."""
import pathlib
import subprocess
BASE = '307eb4b7072abd383b2e845070fe93716f864e25'
root = pathlib.Path(__file__).resolve().parent.parent

def baseline(path):
    return subprocess.check_output(['git', 'show', f'{BASE}:{path}'], cwd=root)

def method(text, name):
    prefix = '    fun ' if ('    fun ' + name + '(') in text else '    private fun '
    start = text.index(prefix + name + '(')
    end = text.index('\n    }', start) + 6
    return text[start:end]

maze = 'app/src/main/java/io/github/denberg28/telerc/MazePracticeDialogs.kt'
old, new = baseline(maze).decode(), (root/maze).read_text()
# Permit only sheet sizing/show and scaled action-height substitutions in the
# otherwise byte-identical selection, restart and wallet/category callbacks.
for name in ['levels', 'restart', 'market', 'close']:
    expected = method(old, name).replace('sheet.dialog.show()', 'sheet.show()').replace('dp(48)', 'rowHeight')
    expected = expected.replace('sheet("Levels",420,minOf(340,68+progress.unlocked*53))', 'sheet("Levels",420)')
    expected = expected.replace('sheet("Restart level $level?",360,184,false)', 'sheet("Restart level $level?",360,false)')
    expected = expected.replace('sheet("Market",440,328)', 'sheet("Market",440)')
    assert expected == method(new, name), f'Practice behavior changed beyond popup layout: {name}'
for name in ['Mavlink.kt', 'WaypointNavigation.kt', 'WaypointProtocol.kt', 'WaypointMap.kt',
             'RouteSession.kt', 'RouteMapView.kt', 'UsbFpvView.kt', 'FpvReconnectPolicy.kt', 'MainActivity.kt', 'WaypointTab.kt', 'AppUpdater.kt', 'UpdateMetadata.kt', 'LiveControlGate.kt', 'BackpackRecovery.kt']:
    path = 'app/src/main/java/io/github/denberg28/telerc/' + name
    assert (root/path).read_bytes() == baseline(path), f'Protected source changed: {name}'
subprocess.run(['git','diff','--exit-code',BASE,'HEAD','--','bridge','tests'],cwd=root,check=True)
print('PASS: popup-only practice presentation; Test, navigation safety engine/protocol, manual codec, route session/map and FPV preserved')

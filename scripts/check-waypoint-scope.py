"""Reject changes to existing tab bodies, manual codec and all firmware."""
import pathlib
import subprocess
BASE = '29ee8d5cd85746d8f1816dcd72a2a70b9ea5c2b2'
MAIN = 'app/src/main/java/io/github/denberg28/telerc/MainActivity.kt'
old = subprocess.check_output(['git','show',f'{BASE}:{MAIN}'], text=True)
new = pathlib.Path(MAIN).read_text()
# Private top-level methods have a stable four-space closing brace; nested blocks do not.
for method in ['renderSetup','renderControls','renderTest','toggleArmDisarm','disableControlState','sendControlHandoverBlocking']:
    def body(text):
        start=text.index('    private fun '+method+'(')
        end=text.index('\n    }',start)+6
        return text[start:end]
    assert body(old)==body(new),f'Existing behavior changed: {method}'
subprocess.run(['git','diff','--exit-code',BASE,'HEAD','--','bridge','tests',
    'app/src/main/java/io/github/denberg28/telerc/Mavlink.kt',
    'app/src/main/java/io/github/denberg28/telerc/RouteMapView.kt',
    'app/src/main/java/io/github/denberg28/telerc/RouteSession.kt',
    'app/src/main/AndroidManifest.xml'],check=True)
print('PASS: Setup/Controls/Test bodies, manual/arm behavior, map/session, manifest, firmware and bridge tests unchanged')

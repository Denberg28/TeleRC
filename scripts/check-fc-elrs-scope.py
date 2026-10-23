"""Limit changes to Waypoint planning/navigation/functions and explicit manual handover guards."""
import pathlib
import subprocess
BASE='82bfbfce36d144dd3d16134b44f175b00665a1ba'
root=pathlib.Path(__file__).resolve().parent.parent

def baseline(path):
    return subprocess.check_output(['git','show',f'{BASE}:{path}'],cwd=root)

def method(text,name):
    start=text.index('    private fun '+name+'(')
    end=text.index('\n    }',start)+6
    return text[start:end]

main='app/src/main/java/io/github/denberg28/telerc/MainActivity.kt'
old,new=baseline(main).decode(),(root/main).read_text()
expected=method(old,'activatePhoneControl').replace('    private fun activatePhoneControl() {','    private fun activatePhoneControl() {\n        if(waypointTab?.navigation?.let { it.busy || it.functionsPendingOff }==true) {\n            Toast.makeText(this,"Wait for Waypoint HOLD and function OFF confirmation.",Toast.LENGTH_LONG).show(); return\n        }')
assert expected==method(new,'activatePhoneControl'),'Manual enable changed beyond pending-HOLD/OFF gate'
expected=method(old,'toggleArmDisarm').replace('        val armed = vehicleArmed','        val armed = vehicleArmed\n        if(armed==false && waypointTab?.navigation?.let { it.busy || it.functionsPendingOff }==true) {\n            Toast.makeText(this,"Confirm Waypoint HOLD and function OFF before arming.",Toast.LENGTH_LONG).show(); return\n        }')
assert expected==method(new,'toggleArmDisarm'),'ARM changed beyond pending-HOLD/OFF gate'
for name in ['activatePhoneControl','toggleArmDisarm']:
    old=old.replace(method(old,name),'');new=new.replace(method(new,name),'')
assert old==new,'MainActivity changed outside the explicit handover gates'
for name in ['Mavlink.kt','RouteSession.kt','RouteMapView.kt','UsbFpvView.kt','FpvReconnectPolicy.kt','RoverKinematics.kt',
             'AppUpdater.kt','UpdateMetadata.kt','LiveControlGate.kt','BackpackRecovery.kt','MazePractice.kt','MazeMarket.kt',
             'MazePracticeView.kt','MazePracticeDialogs.kt','AppDialog.kt','PopupLayout.kt']:
    path='app/src/main/java/io/github/denberg28/telerc/'+name
    assert (root/path).read_bytes()==baseline(path),f'Protected source changed: {name}'
subprocess.run(['git','diff','--exit-code',BASE,'HEAD','--','bridge','tests'],cwd=root,check=True)
print('PASS: Waypoint-only feature and explicit pending-HOLD/OFF guards; offline game/wallet, manual codec, connections, FPV, shared popups and firmware preserved')

from pathlib import Path
import sys
root=Path(__file__).resolve().parent.parent
for name in (root/'go/shared-core.txt').read_text().splitlines():
 source=root/f'app/src/main/java/io/github/denberg28/telerc/{name}.kt'
 target=root/f'go/src/main/java/io/github/denberg28/telerc/{name}.kt'
 if '--check' in sys.argv:
  assert source.read_bytes()==target.read_bytes(),name
 else:target.write_bytes(source.read_bytes())
print('Go shared core matches the full app.')

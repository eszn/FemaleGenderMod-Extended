"""Check the installed graphics path without starting a game or downloading libraries."""
from pathlib import Path
import os, subprocess

ROOT = Path(__file__).resolve().parents[1]
cache = Path.home()/'.gradle/caches/modules-2/files-2.1/org.lwjgl'
libraries = []
for module in ['lwjgl', 'lwjgl-glfw', 'lwjgl-opengl']:
    for suffix in ['', '-natives-windows']:
        matches = list((cache/module/'3.3.3').glob(f'*/{module}-3.3.3{suffix}.jar'))
        if len(matches) != 1:
            raise RuntimeError(f'Expected one existing {module}{suffix} library')
        libraries.append(str(matches[0]))
output = ROOT/'build/graphics-probe'
output.mkdir(parents=True, exist_ok=True)
java = Path(os.environ['JAVA_HOME'])/'bin'
cp = os.pathsep.join(libraries+[str(output)])
flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
subprocess.run([str(java/'javac'), '-cp', cp, '-d', str(output), str(ROOT/'tools/GraphicsProbe.java')], check=True, creationflags=flags)
try:
    result = subprocess.run([str(java/'java'), '-cp', cp, 'GraphicsProbe'], capture_output=True, text=True, timeout=20, creationflags=flags)
    (output/'result.txt').write_text(result.stdout+result.stderr)
    print(result.stdout+result.stderr)
    raise SystemExit(result.returncode)
except subprocess.TimeoutExpired as error:
    def as_text(value):
        return value.decode(errors='replace') if isinstance(value, bytes) else value or ''
    captured = as_text(error.stdout)+as_text(error.stderr)
    (output/'result.txt').write_text(captured+'\nFAIL: graphics context startup timed out\n')
    print(captured+'\nFAIL: graphics context startup timed out; probe process stopped')
    raise SystemExit(1)

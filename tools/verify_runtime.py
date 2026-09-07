"""Run an isolated dedicated server and two scripted clients, without desktop automation."""
from pathlib import Path
import json
import os
import subprocess
import time
from source_fingerprint import fingerprint

ROOT = Path(__file__).resolve().parents[1]
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
GRADLE = ['cmd.exe', '/c', str(ROOT / 'gradlew.bat')] if os.name == 'nt' else [str(ROOT / 'gradlew')]
LOGS = ROOT / 'build' / 'verification'
LOGS.mkdir(parents=True, exist_ok=True)
server_dir = ROOT / 'runs' / 'verification-server'
server_dir.mkdir(parents=True, exist_ok=True)
(server_dir / 'eula.txt').write_text('eula=true\n')
(server_dir / 'server.properties').write_text('''server-ip=127.0.0.1
server-port=25589
online-mode=false
level-name=body-verification
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
max-players=4
allow-flight=true
sync-chunk-writes=false
''')
for client in ['a', 'b']:
    run = ROOT / 'runs' / f'verification-{client}'
    run.mkdir(parents=True, exist_ok=True)
    (run / 'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nguiScale:2\ntutorialStep:none\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\n')
    (run / 'config').mkdir(exist_ok=True)
    (run / 'config' / 'fml.toml').write_text('earlyWindowControl=false\nversionCheck=false\n')

subprocess.run(GRADLE + ['compileGameTestJava', 'prepareVerificationServerRun', 'prepareVerificationARun', 'prepareVerificationBRun', '--no-daemon'],
               cwd=ROOT, check=True, creationflags=FLAGS)
started = time.time()
source_sha256=fingerprint(ROOT)
success=False
(LOGS/'result.json').write_text(json.dumps({'result':'RUNNING','source_sha256':source_sha256}))
processes = []
handles = []
try:
    for task, label in [('runVerificationServer', 'server'), ('runVerificationA', 'a'), ('runVerificationB', 'b')]:
        handle = (LOGS / f'{label}.log').open('w')
        handles.append(handle)
        process = subprocess.Popen(GRADLE + [task, '--no-daemon'], cwd=ROOT, stdout=handle, stderr=subprocess.STDOUT, creationflags=FLAGS)
        processes.append(process)
        if label == 'server':
            deadline = time.monotonic() + 120
            while time.monotonic() < deadline:
                handle.flush()
                if 'For help, type' in (LOGS / 'server.log').read_text(errors='replace'):
                    break
                if process.poll() is not None:
                    raise RuntimeError('Test server exited before accepting clients; see build/verification/server.log')
                time.sleep(1)
            else:
                raise TimeoutError('Test server did not start')
        elif label == 'a':
            # Stagger native graphics initialization; both clients still run together for the test.
            deadline = time.monotonic() + 120
            while time.monotonic() < deadline:
                if 'BodyLabA joined the game' in (LOGS / 'server.log').read_text(errors='replace'):
                    break
                if process.poll() is not None:
                    raise RuntimeError('First client exited during startup; see build/verification/a.log')
                time.sleep(1)
            else:
                raise TimeoutError('First client did not finish graphics initialization and connect')
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        if any(p.poll() is not None and p.returncode != 0 for p in processes):
            raise RuntimeError('A verification process failed; see build/verification logs')
        if all(p.poll() is not None for p in processes):
            break
        time.sleep(1)
    else:
        raise TimeoutError('Scripted clients did not complete; see build/verification logs')
    if any(p.returncode != 0 for p in processes):
        raise RuntimeError('A verification process failed; see build/verification logs')
    for client in ['a', 'b']:
        result = ROOT / 'runs' / f'verification-{client}' / 'verification' / 'result.txt'
        if not result.exists() or result.stat().st_mtime < started or 'PASS' not in result.read_text():
            raise RuntimeError(f'Client {client} has no fresh PASS result')
        log = (LOGS / f'{client}.log').read_text(errors='replace')
        if 'Failed to render' in log or 'Critical injection failure' in log:
            raise RuntimeError(f'Client {client} logged a rendering failure')
        if (ROOT / 'local-models/resources/assets/wildfire_gender/body/jenny-mesh.json').exists() and 'BODY_VERIFY_AUTHORED: jenny' not in log:
            raise RuntimeError(f'Client {client} did not load the imported Jenny model')
    for mode in range(3):
        report=ROOT/f'runs/verification-a/verification/physics-{mode}.txt'
        assert report.exists() and report.stat().st_mtime>=started and 'PASS' in report.read_text(), 'No fresh physics report'
        for frame in range(160):
            path=ROOT/f'runs/verification-a/verification/physics-{mode}/{frame:03d}.png'
            assert path.exists() and path.stat().st_mtime>=started, f'Missing fresh framebuffer: {path}'
    assert fingerprint(ROOT)==source_sha256, 'Source changed during runtime verification'
    (LOGS / 'result.json').write_text(json.dumps({'result': 'PASS','source_sha256':source_sha256, 'clients': 2, 'dedicated_server': True, 'physics_capture_frames':480,
        'desktop_automation': False,'graphics': 'system OpenGL', 'elapsed_seconds': round(time.time()-started, 2)}, indent=2))
    success=True
    print('PASS: real dedicated server, two clients, profile/tracking synchronization, mesh and armor rendering.')
finally:
    if not success: (LOGS/'result.json').write_text(json.dumps({'result':'FAIL','source_sha256':source_sha256}))
    for process in processes:
        if process.poll() is None:
            if os.name == 'nt':
                subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True, creationflags=FLAGS)
            else:
                process.terminate()
    for handle in handles:
        handle.close()

"""Run an isolated dedicated server and two scripted clients, without desktop automation."""
from pathlib import Path
import json
import os
import subprocess
import time

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

subprocess.run(GRADLE + ['compileGameTestJava', 'prepareVerificationServerRun', 'prepareVerificationARun', 'prepareVerificationBRun', '--no-daemon'],
               cwd=ROOT, check=True, creationflags=FLAGS)
started = time.time()
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
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
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
    (LOGS / 'result.json').write_text(json.dumps({'result': 'PASS', 'clients': 2, 'dedicated_server': True,
        'desktop_automation': False, 'elapsed_seconds': round(time.time()-started, 2)}, indent=2))
    print('PASS: real dedicated server, two clients, profile/tracking synchronization, mesh and armor rendering.')
finally:
    for process in processes:
        if process.poll() is None:
            if os.name == 'nt':
                subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True, creationflags=FLAGS)
            else:
                process.terminate()
    for handle in handles:
        handle.close()

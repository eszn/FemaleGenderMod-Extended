"""Create a reproducible source archive and audited local distribution from a clean commit."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET
from source_fingerprint import fingerprint

ROOT=Path(__file__).resolve().parents[1]
DIST=ROOT/'dist'
DIST.mkdir(exist_ok=True)
if subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip():
    raise SystemExit('Commit the reviewed source before packaging.')
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
version=re.search(r'^mod_version\s*=\s*(\S+)',(ROOT/'gradle.properties').read_text(),re.M)[1]
base=f'Female-Gender-Extended-neoforge-1.21.1-{version}'
jar=ROOT/'build'/'libs'/f'{base}.jar'
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    names=z.namelist()
    assert 'LICENSE' in names and 'LICENSE-GPL' in names
    assert not any(n.startswith('com/wildfire/test/') for n in names), 'Test classes must not ship'
    assert 'wildfire_gender_test' not in z.read('META-INF/neoforge.mods.toml').decode()
    assert '21.1.248' in z.read('META-INF/neoforge.mods.toml').decode()
    assert len(json.loads(z.read('wildfire_gender.mixins.json'))['client'])==4
    assert 'com/wildfire/client/render/RoundedBreastRenderer.class' not in names, 'Obsolete overlapping breast layer must not ship'
    for required in ['BodyPhysics','BreastSurface','DampedSpring']:
        assert f'com/wildfire/physics/{required}.class' in names
    local_model='assets/wildfire_gender/body/jenny-mesh.json' in names
    model_info=json.loads(z.read('assets/wildfire_gender/body/jenny-mesh.json')) if local_model else None
    assert 'assets/wildfire_gender/body/jenny.json' not in names, 'Obsolete fitted model must not ship'
    if local_model:
        assert 'assets/wildfire_gender/body/ATTRIBUTION.txt' in names
        assert 'com/wildfire/client/render/DirectBodyRenderer.class' in names
    for name in names:
        assert not name.endswith(('.env','.log')), f'Unexpected private/runtime file: {name}'
reports=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
assert reports and all(int(r.attrib['failures'])==0 and int(r.attrib['errors'])==0 for r in reports)
runtime=json.loads((ROOT/'build/verification/result.json').read_text())
assert runtime['result']=='PASS' and runtime.get('physics_capture_frames')==480
assert runtime['source_sha256']==fingerprint(ROOT), 'Runtime verification does not match current source'
recordings=json.loads((DIST/'previews/recordings.json').read_text())
assert len(recordings)==3 and all(item.get('source_sha256')==runtime['source_sha256'] and item['duration_ms']==8000 for item in recordings), 'Physics GIFs must match this build'
shutil.copy2(jar,DIST/jar.name)
source=DIST/f'{base}-full-source.zip'
subprocess.run(['git','archive','--format=zip',f'--output={source}','HEAD'],cwd=ROOT,check=True)
with zipfile.ZipFile(source) as z:
    assert z.testzip() is None
    assert 'build.gradle' in z.namelist() and 'tools/verify_runtime.py' in z.namelist()
    assert not any(n.startswith(('runs/','logs/','.git/','local-models/','dist/','build/')) for n in z.namelist())
game_log=(ROOT/'runs/gametest/logs/latest.log').read_text(errors='replace')
game_tests=int(re.search(r'All (\d+) required tests passed',game_log)[1])
extras=[]
if local_model:
    # This sidecar is part of the user's local bundle, never the public Git archive.
    data_zip=DIST/f'{base}-local-model-data.zip'
    with zipfile.ZipFile(data_zip,'w',zipfile.ZIP_DEFLATED) as z:
        for path in sorted((ROOT/'local-models/resources').rglob('*')):
            if path.is_file(): z.write(path,path.relative_to(ROOT))
    extras.append(data_zip)
for mode in range(3):
    report=ROOT/f'runs/verification-a/verification/physics-{mode}.txt'
    assert 'PASS' in report.read_text()
manifest={'commit':commit,'minecraft':'1.21.1','neoforge_tested':'21.1.248','java':21,
          'unit_tests':sum(int(r.attrib['tests'])-int(r.attrib['skipped']) for r in reports),'game_tests':game_tests,'runtime':runtime,
          'local_model':{'character':model_info['character'],'source':model_info['source'],'reference_quads':len(model_info['quads'])} if model_info else None,
          'sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [DIST/jar.name,source,*extras]}}
(DIST/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(DIST/'SHA256SUMS.txt').write_text(''.join(f'{value}  {name}\n' for name,value in manifest['sha256'].items()))
bundle=DIST/f'Female-Gender-Extended-1.21.1-{version}.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as z:
    for p in [DIST/jar.name,source,*extras,DIST/'manifest.json',DIST/'SHA256SUMS.txt',ROOT/'README.md',ROOT/'LICENSE',ROOT/'LICENSE-GPL']:
        z.write(p,p.name)
    for p in sorted((ROOT/'docs').rglob('*')):
        if p.is_file(): z.write(p,p.relative_to(ROOT))
    for p in [*(Path(item['path']) for item in recordings),DIST/'previews/recordings.json']:
        z.write(p,'previews/'+p.name)
with zipfile.ZipFile(bundle) as z: assert z.testzip() is None
print(json.dumps({'bundle':str(bundle),'jar':str(DIST/jar.name),'sha256':manifest['sha256'],'bytes':bundle.stat().st_size},indent=2))

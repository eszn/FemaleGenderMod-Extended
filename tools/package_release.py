"""Create a reproducible source archive and audited local distribution from a clean commit."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
DIST=ROOT/'dist'
DIST.mkdir(exist_ok=True)
if subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip():
    raise SystemExit('Commit the reviewed source before packaging.')
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
base='Female-Gender-Extended-neoforge-1.21.1-4.3.0-extended.1'
jar=ROOT/'build'/'libs'/f'{base}.jar'
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    names=z.namelist()
    assert 'LICENSE' in names and 'LICENSE-GPL' in names
    assert not any(n.startswith('com/wildfire/test/') for n in names), 'Test classes must not ship'
    assert 'wildfire_gender_test' not in z.read('META-INF/neoforge.mods.toml').decode()
    assert '21.1.248' in z.read('META-INF/neoforge.mods.toml').decode()
    assert len(json.loads(z.read('wildfire_gender.mixins.json'))['client'])==3
    for required in ['BodyPhysics','BreastSurface','DampedSpring']:
        assert f'com/wildfire/physics/{required}.class' in names
    for name in names:
        assert not name.endswith(('.env','.log')), f'Unexpected private/runtime file: {name}'
shutil.copy2(jar,DIST/jar.name)
source=DIST/f'{base}-full-source.zip'
subprocess.run(['git','archive','--format=zip',f'--output={source}','HEAD'],cwd=ROOT,check=True)
with zipfile.ZipFile(source) as z:
    assert z.testzip() is None
    assert 'build.gradle' in z.namelist() and 'tools/verify_runtime.py' in z.namelist()
    assert not any(n.startswith(('runs/','logs/','.git/')) for n in z.namelist())
reports=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
assert reports and all(int(r.attrib['failures'])==0 and int(r.attrib['errors'])==0 for r in reports)
manifest={'commit':commit,'minecraft':'1.21.1','neoforge_tested':'21.1.248','java':21,
          'unit_tests':sum(int(r.attrib['tests'])-int(r.attrib['skipped']) for r in reports),'game_tests':5,'runtime':json.loads((ROOT/'build/verification/result.json').read_text()),
          'sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [DIST/jar.name,source]}}
(DIST/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(DIST/'SHA256SUMS.txt').write_text(''.join(f'{value}  {name}\n' for name,value in manifest['sha256'].items()))
bundle=DIST/'Female-Gender-Extended-1.21.1-extended.1.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as z:
    for p in [DIST/jar.name,source,DIST/'manifest.json',DIST/'SHA256SUMS.txt',ROOT/'README.md',ROOT/'LICENSE',ROOT/'LICENSE-GPL']:
        z.write(p,p.name)
    for p in sorted((ROOT/'docs').rglob('*')):
        if p.is_file(): z.write(p,p.relative_to(ROOT))
with zipfile.ZipFile(bundle) as z: assert z.testzip() is None
print(json.dumps({'bundle':str(bundle),'jar':str(DIST/jar.name),'sha256':manifest['sha256'],'bytes':bundle.stat().st_size},indent=2))

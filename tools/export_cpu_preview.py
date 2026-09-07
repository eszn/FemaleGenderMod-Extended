"""Compile/export only pure model and physics sources using already cached dependencies."""
from pathlib import Path
import os,subprocess,json
from source_fingerprint import fingerprint

ROOT=Path(__file__).resolve().parents[1]
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
libraries=[]
for group,module in [('org.joml','joml'),('com.google.code.gson','gson')]:
    matches=sorted(p for p in (cache/group/module).glob('*/*/*.jar') if not any(x in p.name for x in ['sources','javadoc']))
    if not matches: raise RuntimeError('Missing cached dependency: '+module)
    libraries.append(str(matches[-1]))
output=ROOT/'build/cpu-preview';output.mkdir(parents=True,exist_ok=True)
java=Path(os.environ['JAVA_HOME'])/'bin';flags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0
cp=os.pathsep.join(libraries+[str(output)])
names=['AuthoredBodyMesh','BodyDeformation','DampedSpring','MotionInput','SecondaryMotion']
sources=[str(ROOT/f'src/main/java/com/wildfire/physics/{name}.java') for name in names]
sources += [str(ROOT/'src/main/java/com/wildfire/main/entitydata/BodySettings.java'),str(ROOT/'tools/ExportBodyPreview.java')]
identity=fingerprint(ROOT)
subprocess.run([str(java/'javac'),'-cp',cp,'-d',str(output)]+sources,check=True,creationflags=flags)
subprocess.run([str(java/'java'),'-Xmx1G','-cp',cp,'ExportBodyPreview',str(ROOT)],check=True,creationflags=flags)
assert identity==fingerprint(ROOT),'Source changed during mesh export'
(output/'source.json').write_text(json.dumps({'source_sha256':identity,'renderer':'CPU mesh preview; not Minecraft framebuffer','frames_per_mode':160},indent=2))

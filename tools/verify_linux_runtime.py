"""Run the built verification mod under an isolated Linux virtual display.

Uses Ubuntu's installed Java/OpenGL packages and cached Minecraft libraries. It never
reads or executes the abandoned Windows graphics archive and uses no desktop control.
Invoke through xvfb-run after the normal Windows build/prepare run tasks finish.
"""
from pathlib import Path
import hashlib,json,os,re,shutil,subprocess,tempfile,time
from source_fingerprint import fingerprint

ROOT=Path(__file__).resolve().parents[1]
LOGS=ROOT/'build/verification-linux';LOGS.mkdir(parents=True,exist_ok=True)
WORK=Path(tempfile.mkdtemp(prefix='fge-verification-'))
LIB=WORK/'lib';LIB.mkdir()
CONFIG=WORK/'config';CONFIG.mkdir()
source_sha256=fingerprint(ROOT)
started=time.time()
(LOGS/'result.json').write_text(json.dumps({'result':'RUNNING','source_sha256':source_sha256,'work':str(WORK)}))

def mounted(value):
    value=value.replace('\\\\','/').replace('\\','/')
    return re.sub(r'([A-Za-z]):/',lambda match:'/mnt/'+match[1].lower()+'/',value)

copied={}
def library(value):
    source=Path(mounted(value))
    if '-natives-' in source.name and '-natives-linux.jar' not in source.name:return None
    if source.name in copied:
        assert copied[source.name]==source,'Conflicting dependency filenames'
        return str(LIB/source.name)
    target=LIB/source.name
    shutil.copy2(source,target);copied[source.name]=source
    return str(target)

# Copy only compiled output and configuration, never downloads/build scratch directories.
for relative in ['build/classes/java/main','build/classes/java/api','build/classes/java/gameTest',
                 'build/resources/main','build/resources/api','build/resources/gameTest']:
    source=ROOT/relative;target=WORK/relative
    if source.exists():shutil.copytree(source,target)
    else:target.mkdir(parents=True,exist_ok=True)

base=ROOT/'build/moddev'
commands={}
for label,name in [('server','verificationServer'),('a','verificationA'),('b','verificationB')]:
    legacy=[library(str(base/'artifacts/neoforge-21.1.248.jar'))]
    original_legacy=(base/f'{name}LegacyClasspath.txt').read_text().splitlines()
    for line in original_legacy:
        if line.strip():
            path=library(line.strip())
            if path:legacy.append(path)
    if label!='server':
        caches={Path(mounted(line)).parents[3] for line in original_legacy if '/org.lwjgl/' in mounted(line)}
        for cache in caches:
            for native in cache.glob('*/3.3.3/*/*-natives-linux.jar'):
                legacy.append(library(str(native)))
    legacy_file=CONFIG/f'{name}LegacyClasspath.txt';legacy_file.write_text('\n'.join(dict.fromkeys(legacy))+'\n')
    log_config=CONFIG/f'{name}Log4j2.xml';shutil.copy2(base/log_config.name,log_config)
    vm=[]
    for raw in (base/f'{name}RunVmArgs.txt').read_text().splitlines():
        raw=raw.strip()
        if not raw or raw.startswith('#'):continue
        if ';' in raw and '.jar' in raw:vm.append(':'.join(library(item) for item in raw.split(';')))
        elif raw.startswith('-DlegacyClassPath.file='):vm.append('-DlegacyClassPath.file='+str(legacy_file))
        elif raw.startswith('-Dlog4j2.configurationFile='):vm.append('-Dlog4j2.configurationFile='+str(log_config))
        else:vm.append(mounted(raw))
    programs=[mounted(line.strip()) for line in (base/f'{name}RunProgramArgs.txt').read_text().splitlines() if line.strip() and not line.startswith('#')]
    # The generated program file includes BootstrapLauncher as its first argument.
    folders=[]
    for mod,parts in [('wildfire_gender',['main','api']),('wildfire_gender_test',['gameTest'])]:
        for part in parts:
            for directory in [WORK/f'build/classes/java/{part}',WORK/f'build/resources/{part}']:
                folders.append(mod+'%%'+str(directory))
    commands[label]=['java','-Xmx3G','-XX:ActiveProcessorCount=6','-Dfml.modFolders='+':'.join(folders),
                     '-Djava.awt.headless=true','-cp',':'.join(dict.fromkeys(legacy)),*vm,*programs]
    run=WORK/label;run.mkdir();(run/'config').mkdir()
    (run/'config/fml.toml').write_text('earlyWindowControl=false\nversionCheck=false\n')
    (run/'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nguiScale:2\ntutorialStep:none\nsoundCategory_master:0.0\nskipMultiplayerWarning:true\n')
(WORK/'server/eula.txt').write_text('eula=true\n')
shutil.copy2(ROOT/'runs/verification-server/server.properties',WORK/'server/server.properties')

processes=[];handles=[];success=False
env=os.environ.copy();env.update({'LIBGL_ALWAYS_SOFTWARE':'true','GALLIUM_DRIVER':'llvmpipe','LP_NUM_THREADS':'6','ALSOFT_DRIVERS':'null'})
try:
    for label in ['server','a','b']:
        handle=(LOGS/f'{label}.log').open('w');handles.append(handle)
        p=subprocess.Popen(commands[label],cwd=WORK/label,stdout=handle,stderr=subprocess.STDOUT,env=env);processes.append(p)
        print('Started',label,'PID',p.pid,flush=True)
        if label=='b':break
        token='For help, type' if label=='server' else 'BodyLabA joined the game'
        deadline=time.monotonic()+180
        while token not in (LOGS/'server.log').read_text(errors='replace'):
            if p.poll() is not None:raise RuntimeError(label+' exited during startup')
            if time.monotonic()>deadline:raise TimeoutError(label+' startup timed out')
            time.sleep(1)
    deadline=time.monotonic()+480
    while any(p.poll() is None for p in processes):
        if any(p.poll() not in [None,0] for p in processes):raise RuntimeError('Verification process failed')
        if time.monotonic()>deadline:raise TimeoutError('Minecraft verification did not finish')
        time.sleep(1)
    for label in ['a','b']:
        report=WORK/label/'verification/result.txt'
        assert report.exists() and 'PASS' in report.read_text(),'Missing client pass: '+label
        log=(LOGS/f'{label}.log').read_text(errors='replace')
        assert 'BODY_VERIFY_AUTHORED: jenny' in log and 'BODY_VERIFY_GRAPHICS: llvmpipe' in log
        assert 'Failed to render' not in log and 'Critical injection failure' not in log
    for mode in range(3):
        report=WORK/f'a/verification/physics-{mode}.txt'
        assert report.exists() and 'PASS' in report.read_text(),'Missing physics pass'
        for frame in range(160):assert (WORK/f'a/verification/physics-{mode}/{frame:03d}.png').exists()
    assert fingerprint(ROOT)==source_sha256,'Source changed during verification'
    for label in ['a','b']:
        shutil.copytree(WORK/label/'verification',ROOT/f'runs/verification-{label}/verification',dirs_exist_ok=True)
    result={'result':'PASS','source_sha256':source_sha256,'clients':2,'dedicated_server':True,'physics_capture_frames':480,
            'desktop_automation':False,'graphics':'Ubuntu packaged llvmpipe in Xvfb','elapsed_seconds':round(time.time()-started,2)}
    (ROOT/'build/verification/result.json').write_text(json.dumps(result,indent=2))
    (LOGS/'result.json').write_text(json.dumps(result,indent=2));success=True
    print('PASS: actual Minecraft renderer, two clients and 480 physics frames',flush=True)
finally:
    if not success:(LOGS/'result.json').write_text(json.dumps({'result':'FAIL','source_sha256':source_sha256,'work':str(WORK)}))
    for p in processes:
        if p.poll() is None:p.terminate()
    for p in processes:
        try:p.wait(timeout=10)
        except subprocess.TimeoutExpired:p.kill();p.wait()
    for handle in handles:handle.close()
    print('Isolated work directory:',WORK,flush=True)

"""Identity of the source/resources used by the scripted Minecraft verification."""
from pathlib import Path
import hashlib

def fingerprint(root):
    files=[]
    for folder in ['src/main','src/api','src/gameTest','local-models/resources']:
        files.extend(p for p in (root/folder).rglob('*') if p.is_file())
    files.extend(root/p for p in ['build.gradle','gradle.properties'])
    digest=hashlib.sha256()
    for path in sorted(files,key=lambda path:path.relative_to(root).as_posix()):
        digest.update(path.relative_to(root).as_posix().encode()); digest.update(b'\0'); digest.update(path.read_bytes())
    return digest.hexdigest()

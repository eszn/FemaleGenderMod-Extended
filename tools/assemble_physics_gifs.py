"""Encode the game's framebuffer captures at their original 20-tick-per-second playback rate."""
from pathlib import Path
import json
from PIL import Image
from source_fingerprint import fingerprint

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'dist/previews'
OUT.mkdir(parents=True,exist_ok=True)
verification=json.loads((ROOT/'build/verification/result.json').read_text())
assert verification['result']=='PASS' and verification.get('physics_capture_frames')==480,'A complete client capture is required'
assert verification['source_sha256']==fingerprint(ROOT),'Client captures are from an older source revision'
results=[]
for mode,name in enumerate(['jenny-physics','jenny-leather-physics','jenny-diamond-physics']):
    folder=ROOT/f'runs/verification-a/verification/physics-{mode}'
    paths=[folder/f'{i:03d}.png' for i in range(160)]
    if not all(p.exists() for p in paths): raise RuntimeError(f'Incomplete capture sequence: {folder}')
    frames=[Image.open(p).convert('RGB') for p in paths]
    sheet=Image.new('RGB',(960,720*4))
    for i,index in enumerate([0,45,88,112]): sheet.paste(frames[index],(0,i*720))
    palette=sheet.quantize(colors=256)
    encoded=[frame.quantize(palette=palette,dither=Image.Dither.NONE) for frame in frames]
    target=OUT/f'{name}.gif'
    encoded[0].save(target,save_all=True,append_images=encoded[1:],duration=50,loop=0,disposal=2,optimize=False)
    with Image.open(target) as gif:
        duration=0
        for i in range(gif.n_frames): gif.seek(i); duration+=gif.info.get('duration',0)
        assert duration==8000
    results.append({'path':str(target),'source_sha256':verification['source_sha256'],'renderer':verification['graphics'],
                    'source_frames':160,'duration_ms':duration,'bytes':target.stat().st_size})
(OUT/'recordings.json').write_text(json.dumps(results,indent=2)+'\n')
print(json.dumps(results,indent=2))

"""Smooth CPU rasterization of exported production geometry; no native graphics driver."""
from pathlib import Path
import argparse,json,math,struct
import numpy as np
from numba import njit
from PIL import Image,ImageDraw,ImageFont
from source_fingerprint import fingerprint

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'dist/previews';OUT.mkdir(parents=True,exist_ok=True)
WIDTH,HEIGHT=1200,680

@njit(cache=True)
def rasterize(vertices,colors,image,depth):
    for triangle in range(vertices.shape[0]):
        v=vertices[triangle]
        ax,ay=v[0,0],v[0,1];bx,by=v[1,0],v[1,1];cx,cy=v[2,0],v[2,1]
        denominator=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy)
        if abs(denominator)<1e-7: continue
        xmin=max(0,int(math.floor(min(ax,bx,cx))));xmax=min(image.shape[1]-1,int(math.ceil(max(ax,bx,cx))))
        ymin=max(0,int(math.floor(min(ay,by,cy))));ymax=min(image.shape[0]-1,int(math.ceil(max(ay,by,cy))))
        for y in range(ymin,ymax+1):
            for x in range(xmin,xmax+1):
                u=((by-cy)*(x+.5-cx)+(cx-bx)*(y+.5-cy))/denominator
                w=((cy-ay)*(x+.5-cx)+(ax-cx)*(y+.5-cy))/denominator
                t=1-u-w
                if min(u,w,t)<-1e-6: continue
                z=u*v[0,2]+w*v[1,2]+t*v[2,2]
                if z>=depth[y,x]: continue
                nx=u*v[0,3]+w*v[1,3]+t*v[2,3]
                ny=u*v[0,4]+w*v[1,4]+t*v[2,4]
                nz=u*v[0,5]+w*v[1,5]+t*v[2,5]
                length=math.sqrt(nx*nx+ny*ny+nz*nz)
                if length<1e-8: continue
                nx/=length;ny/=length;nz/=length
                light=.28+.62*max(0,-.45*nx-.65*ny-.61*nz)+.1*max(0,.8*nx-.2*ny+.56*nz)
                specular=.10*max(0,-.24*nx-.35*ny-.905*nz)**32
                for channel in range(3):
                    value=colors[triangle,channel]*light+255*specular
                    image[y,x,channel]=min(255,max(0,int(value)))
                depth[y,x]=z

def box(low,high,pivot=None,angle=0):
    low=np.array(low);high=np.array(high)
    corners=np.array([[high[0] if i&1 else low[0],high[1] if i&2 else low[1],high[2] if i&4 else low[2]] for i in range(8)],dtype=np.float32)
    faces=[([0,2,3,1],[0,0,-1]),([4,5,7,6],[0,0,1]),([0,4,6,2],[-1,0,0]),([1,3,7,5],[1,0,0]),([0,1,5,4],[0,-1,0]),([2,6,7,3],[0,1,0])]
    result=[]
    for ids,normal in faces:
        for ix in [[0,1,2],[0,2,3]]:
            result.append([list(corners[ids[i]])+normal for i in ix])
    result=np.array(result,dtype=np.float32)
    if pivot is not None:
        rotation=np.array([[1,0,0],[0,math.cos(angle),-math.sin(angle)],[0,math.sin(angle),math.cos(angle)]],dtype=np.float32)
        result[:,:,:3]=(result[:,:,:3]-pivot)@rotation.T+pivot
        result[:,:,3:]=result[:,:,3:]@rotation.T
    return result

def font(size):
    path=Path('C:/Windows/Fonts/segoeui.ttf')
    return ImageFont.truetype(str(path),size) if path.exists() else ImageFont.load_default(size=size)

def render(mesh,frame,mode,scale=1):
    image=np.full((HEIGHT*scale,WIDTH*scale,3),(24,32,42),dtype=np.uint8)
    depth=np.full((HEIGHT*scale,WIDTH*scale),np.inf,dtype=np.float32)
    walking=20<=frame<70
    walk_position=max(0,min(50,frame-19))*.65
    swing=math.cos(walk_position*.6662)*.65 if walking else 0
    attachments=np.concatenate([box([-4,-8,-4],[4,0,4]),box([4,0,-2],[8,12,2],[5,2,0],-swing),box([-8,0,-2],[-4,12,2],[-5,2,0],swing)])
    vertices=np.concatenate([mesh,attachments])
    body_color=[(205,213,221),(151,111,82),(91,183,202)][mode]
    colors=np.array([body_color]*len(mesh)+[(156,167,180)]*len(attachments),dtype=np.float32)
    turn=max(0,min(20,frame-70))*.018
    for view,angle in enumerate([.35,math.pi/2,math.pi-.45]):
        angle+=turn
        rotation=np.array([[math.cos(angle),0,math.sin(angle)],[0,1,0],[-math.sin(angle),0,math.cos(angle)]],dtype=np.float32)
        projected=vertices.copy()
        projected[:,:,:3]=vertices[:,:,:3]@rotation.T
        projected[:,:,3:]=vertices[:,:,3:]@rotation.T
        projected[:,:,0]=projected[:,:,0]*(15*scale)+(view*400+200)*scale
        projected[:,:,1]=projected[:,:,1]*(15*scale)+235*scale
        rasterize(projected,colors,image,depth)
    result=Image.fromarray(image)
    if scale!=1: result=result.resize((WIDTH,HEIGHT),Image.Resampling.LANCZOS)
    draw=ImageDraw.Draw(result)
    title=['Jenny geometry • revised sides and thighs','Leather support • neutral garment envelope','Rigid armor support • neutral garment envelope'][mode]
    draw.text((WIDTH/2,22),title,font=font(24),fill='#edf4fb',anchor='mt')
    phase='Rest' if frame<20 else 'Walking' if frame<70 else 'Turning' if frame<95 else 'Jump / landing' if frame<114 else 'Settling'
    draw.text((WIDTH/2,59),phase+'  |  1× speed',font=font(17),fill='#a8c4d6',anchor='mt')
    for view,label in enumerate(['Front / three-quarter','Side','Rear / three-quarter']): draw.text((view*400+200,90),label,font=font(16),fill='#a8c4d6',anchor='mt')
    draw.text((WIDTH/2,633),'CPU mesh preview • production geometry + spring forces • scripted vanilla joint motion',font=font(16),fill='#a8c4d6',anchor='mt')
    draw.text((WIDTH/2,656),'Neutral material; this is not a Minecraft framebuffer capture.',font=font(13),fill='#8195a8',anchor='mt')
    return result

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--stills',action='store_true');args=parser.parse_args()
    provenance=json.loads((ROOT/'build/cpu-preview/source.json').read_text())
    assert provenance['source_sha256']==fingerprint(ROOT),'Mesh export is stale'
    reports=[]
    for mode,name in enumerate(['jenny-cpu-physics','jenny-cpu-leather','jenny-cpu-diamond']):
        source=ROOT/f'build/cpu-preview/motion-{mode}.bin'
        with source.open('rb') as stream: count,triangles=struct.unpack('>ii',stream.read(8))
        assert source.stat().st_size==8+count*triangles*3*6*4
        frames=np.memmap(source,dtype='>f4',mode='r',offset=8,shape=(count,triangles,3,6))
        still=render(np.asarray(frames[0],dtype=np.float32),0,mode,2)
        still.save(OUT/f'{name}-rest.png')
        if args.stills:
            render(np.asarray(frames[40],dtype=np.float32),40,mode,2).save(OUT/f'{name}-walk.png');print('Stills:',name,flush=True);continue
        palette=still.quantize(colors=256)
        images=[]
        for index in range(count):
            result=render(np.asarray(frames[index],dtype=np.float32),index,mode,2)
            images.append(result.quantize(palette=palette,dither=Image.Dither.NONE))
            if index%40==0: print(name,index,'/',count,flush=True)
        target=OUT/f'{name}.gif'
        images[0].save(target,save_all=True,append_images=images[1:],duration=50,loop=0,disposal=2,optimize=False)
        with Image.open(target) as gif:
            duration=0
            for index in range(gif.n_frames):gif.seek(index);duration+=gif.info['duration']
            assert duration==8000
        reports.append({'path':str(target),'duration_ms':duration,'bytes':target.stat().st_size})
    if reports:(OUT/'cpu-recordings.json').write_text(json.dumps({**provenance,'recordings':reports},indent=2))

if __name__=='__main__':main()

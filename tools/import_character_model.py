"""Convert selected, user-supplied Bedrock body surfaces into a local player-mesh resource.

Reads geometry as data; no code from the supplied archive is executed. Original model assets
remain local and retain their original attribution, independently of this converter's license.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import zipfile

ROOT=Path(__file__).resolve().parents[1]
MODELS={'jenny':'jenny/jennynude','ellie':'ellie/dressed','bia':'bia/biadressed'}

def rotate(p,pivot,angles):
    x,y,z=[p[i]-pivot[i] for i in range(3)]
    # GeoBuilder mirrors X, negates rotation X/Y, then applies Rz*Ry*Rx.
    # Conjugating back into the original file coordinates gives these signs.
    a,b,c=map(math.radians,[-angles[0],angles[1],-angles[2]])
    y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
    x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b)
    x,y=x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c)
    return [x+pivot[0],y+pivot[1],z+pivot[2]]

def normal(q):
    a=[q[1][i]-q[0][i] for i in range(3)]; b=[q[2][i]-q[0][i] for i in range(3)]
    n=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
    length=math.sqrt(sum(v*v for v in n))
    return [v/length for v in n] if length>1e-8 else None

def convert(archive,model,_chest_only=False):
    path=('jenny/jennynude' if _chest_only else 'jenny/jennydressed') if model=='jenny' else MODELS[model]
    member='assets/sexmod/geo/'+path+'.geo.json'
    with zipfile.ZipFile(archive) as z: raw=z.read(member)
    data=json.loads(raw)['minecraft:geometry'][0]
    bones={b['name']:b for b in data['bones']}
    def ancestry(name):
        result=[]
        while name is not None:
            if name in result: raise ValueError('Cyclic bone hierarchy')
            result.append(name); name=bones[name].get('parent')
        return result
    # Import ordinary body contours for the player's existing clothed skin and armor UVs.
    direct={'torso','hipbend','upperBodyR','upperBodyL','hip','sideR','sideL','legL','legR','curvesL','curvesR','shinL','shinR','neck'}
    roots={'fleshL':'buttock','fleshR':'buttock','kneeL':'thigh','kneeR':'thigh'} | ({} if model=='jenny' else {'bra':'breast'})
    if _chest_only: direct=set(); roots={'boobL':'breast','boobR':'breast'}
    quads=[]; selected=[]; volumes=[]
    for name,bone in bones.items():
        chain=ancestry(name)
        if any(n.lower().startswith('nipple') for n in chain): continue
        group=next((roots[n] for n in chain if n in roots),None)
        if group is None and name not in direct: continue
        if group is None: group='thigh' if name.startswith(('leg','curves','shin')) else 'body'
        if not bone.get('cubes'): continue
        selected.append(name)
        for cube in bone.get('cubes',[]):
            inflate=cube.get('inflate',0)
            x0,y0,z0=[v-inflate for v in cube['origin']]
            x1,y1,z1=[cube['origin'][i]+cube['size'][i]+inflate for i in range(3)]
            def transform(point):
                if 'rotation' in cube: point=rotate(point,cube.get('pivot',[0,0,0]),cube['rotation'])
                for n in chain:
                    b=bones[n]
                    if 'rotation' in b: point=rotate(point,b.get('pivot',[0,0,0]),b['rotation'])
                return list(point)
            center=[(x0+x1)/2,(y0+y1)/2,(z0+z1)/2]
            transformed_center=transform(center)
            axes=[]
            for axis in range(3):
                offset=center.copy();offset[axis]+=1
                axes.append([v-transformed_center[i] for i,v in enumerate(transform(offset))])
            volumes.append({'group':group,'bone':name,'center':transformed_center,'axes':axes,'half_size':[(x1-x0)/2,(y1-y0)/2,(z1-z0)/2]})
            faces={
                'north':[(x0,y0,z0),(x0,y1,z0),(x1,y1,z0),(x1,y0,z0)],
                'south':[(x1,y0,z1),(x1,y1,z1),(x0,y1,z1),(x0,y0,z1)],
                'east':[(x1,y0,z0),(x1,y1,z0),(x1,y1,z1),(x1,y0,z1)],
                'west':[(x0,y0,z1),(x0,y1,z1),(x0,y1,z0),(x0,y0,z0)],
                'up':[(x0,y1,z0),(x0,y1,z1),(x1,y1,z1),(x1,y1,z0)],
                'down':[(x0,y0,z1),(x0,y0,z0),(x1,y0,z0),(x1,y0,z1)]}
            uv=cube.get('uv',{})
            for face,points in faces.items():
                if isinstance(uv,dict) and face not in uv: continue
                transformed=[]
                for point in points:
                    transformed.append(transform(point))
                if normal(transformed) is not None:
                    rig='left_leg' if 'legL' in chain else 'right_leg' if 'legR' in chain else 'torso'
                    quads.append({'group':group,'bone':name,'rig':rig,'points':transformed,'source_uv':uv.get(face) if isinstance(uv,dict) else uv})
    result={'format':1,'character':model,'source':{'author':'SchnurriTV and original model contributors',
            'archive_sha256':hashlib.sha256(Path(archive).read_bytes()).hexdigest(),
            'member':member,'geometry_sha256':hashlib.sha256(raw).hexdigest()},'selected_bones':selected,'quads':quads,'volumes':volumes,
            'bones':[{k:b[k] for k in ('name','parent','pivot','rotation') if k in b} for b in bones.values() if any(b['name'] in ancestry(n) for n in selected)],
            'landmarks':{'shoulder':bones.get('head',{}).get('pivot',[0,24,0])[1],
                         'hip':bones['legL']['pivot'][1], 'knee':bones['shinL']['pivot'][1]}}
    if model=='jenny' and not _chest_only:
        chest=convert(archive,model,True); result['quads'].extend(chest['quads'])
        result['volumes'].extend(chest['volumes'])
        result['selected_bones'].extend(chest['selected_bones']); result['source']['chest']=chest['source']
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive',type=Path); parser.add_argument('--model',choices=MODELS,default='jenny')
    args=parser.parse_args(); result=convert(args.archive,args.model)
    out=ROOT/'local-models'/'raw'/f'{args.model}.json'; out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result,separators=(',',':')))
    resource=ROOT/'local-models/resources/assets/wildfire_gender/body/jenny-mesh.json'
    resource.parent.mkdir(parents=True,exist_ok=True)
    # Raw authored panels remain available as provenance; optional surface cleanup is separate.
    resource.write_text(json.dumps(result,separators=(',',':')))
    (resource.parent/'ATTRIBUTION.txt').write_text('Local character geometry supplied by the user.\nOriginal model: Jenny, by SchnurriTV and original model contributors.\nGeometry retains its original authorship; this fork does not relicense it.\nThe archive contains no redistribution license. The public repository excludes this local asset.\nOnly selected body geometry was imported; original code, textures, audio and animations are not included.\nArchive SHA-256: '+result['source']['archive_sha256']+'\n')
    print(json.dumps({'output':str(out),'quads':len(result['quads']),'selected_bones':result['selected_bones'],'landmarks':result['landmarks']},indent=2))

if __name__=='__main__': main()

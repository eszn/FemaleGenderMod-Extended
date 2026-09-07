"""Close overlapping authored soft-surface panels and remove subpixel ridges.

The source panel transforms/proportions remain the input. Surface nets create one closed,
smoothly shaded boundary per group; no replacement ellipsoid or anatomy template is used.
"""
from pathlib import Path
import json
import numpy as np
from scipy.ndimage import gaussian_filter,map_coordinates
from scipy.spatial import ConvexHull

ROOT=Path(__file__).resolve().parents[1]
STEP=.32

def surface_nets(field,origin):
    offsets=np.array([[x,y,z] for x in [0,1] for y in [0,1] for z in [0,1]],dtype=int)
    shape=np.array(field.shape)-1
    corners=np.stack([field[x:x+shape[0],y:y+shape[1],z:z+shape[2]] for x,y,z in offsets],axis=-1)
    active=(corners.min(axis=-1)<0)&(corners.max(axis=-1)>=0)
    cells=np.column_stack(np.nonzero(active));values=corners[active]
    positions=np.zeros((len(cells),3));counts=np.zeros(len(cells))
    for a in range(8):
        for b in range(a+1,8):
            if np.abs(offsets[a]-offsets[b]).sum()!=1:continue
            crossing=(values[:,a]<0)!=(values[:,b]<0)
            t=values[crossing,a]/(values[crossing,a]-values[crossing,b])
            positions[crossing]+=cells[crossing]+offsets[a]+t[:,None]*(offsets[b]-offsets[a]);counts[crossing]+=1
    positions/=counts[:,None]
    gradients=np.gradient(field,STEP)
    normals=np.stack([map_coordinates(g,positions.T,order=1,mode='nearest') for g in gradients],axis=-1)
    normals/=np.linalg.norm(normals,axis=1)[:,None]
    lookup=np.full(tuple(shape),-1,dtype=int);lookup[active]=np.arange(len(cells))
    faces=[]
    for axis in range(3):
        start=[slice(None)]*3;end=start.copy();start[axis]=slice(None,-1);end[axis]=slice(1,None)
        edges=np.column_stack(np.nonzero((field[tuple(start)]<0)!=(field[tuple(end)]<0)))
        other=[i for i in range(3) if i!=axis]
        valid=np.all(edges>=0,axis=1)&np.all(edges<shape,axis=1)&(edges[:,other[0]]>0)&(edges[:,other[1]]>0)
        edges=edges[valid]
        ids=[]
        for a,b in [(0,0),(-1,0),(-1,-1),(0,-1)]:
            neighbor=edges.copy();neighbor[:,other[0]]+=a;neighbor[:,other[1]]+=b
            ids.append(lookup[tuple(neighbor.T)])
        quads=np.stack(ids,axis=-1);quads=quads[(quads>=0).all(axis=1)]
        faces.extend([quads[:,[0,1,2]],quads[:,[0,2,3]]])
    faces=np.concatenate(faces)
    points=origin+positions*STEP
    cross=np.cross(points[faces[:,1]]-points[faces[:,0]],points[faces[:,2]]-points[faces[:,0]])
    reverse=(cross*normals[faces].mean(axis=1)).sum(axis=1)<0
    faces[reverse]=faces[reverse][:,[0,2,1]]
    return points,normals,faces

def build(data,group):
    # Jenny's soft surfaces are zero-thickness strips, not solid boxes. Closing each
    # authored side with its convex envelope fills tiny panel gaps before smoothing.
    # Use every original panel position rather than an ellipsoid or radial template.
    panels=[np.array(q['points']) for q in data['quads'] if q['group']==group]
    sides=[]
    for sign in [-1,1]:
        points=np.concatenate([q for q in panels if (1 if q[:,0].mean()>=0 else -1)==sign])
        points[:,1]=24-points[:,1]
        if group=='breast':
            # Tuck the hidden rear attachment into the player ribcage; leave projection
            # and outer breast contours intact. The source character has a wider root.
            t=np.clip((points[:,2]+3.1)/3.1,0,1);t=t*t*(3-2*t)
            points[:,0]*=1-.45*t
        points=np.unique(np.round(points,6),axis=0);sides.append(points)
    bounds=np.concatenate(sides);origin=np.floor((bounds.min(axis=0)-1)/STEP)*STEP
    maximum=np.ceil((bounds.max(axis=0)+1)/STEP)*STEP
    grid=np.stack(np.meshgrid(*[np.arange(origin[i],maximum[i]+STEP*.5,STEP) for i in range(3)],indexing='ij'),axis=-1)
    field=np.full(grid.shape[:-1],100,dtype=np.float32)
    for points in sides:
        hull=ConvexHull(points)
        distance=np.full(grid.shape[:-1],-100,dtype=np.float32)
        for plane in hull.equations:
            np.maximum(distance,grid@plane[:3]+plane[3],out=distance)
        distance=gaussian_filter(distance,.30/STEP)
        np.minimum(field,distance,out=field)
    points,normals,faces=surface_nets(field,origin)
    print(group,'vertices',len(points),'triangles',len(faces),'bounds',points.min(axis=0).round(2),points.max(axis=0).round(2),flush=True)
    return {'group':group,'vertices':np.round(np.concatenate([points,normals],axis=1),6).tolist(),'faces':faces.tolist()}

def main():
    source=ROOT/'local-models/raw/jenny.json';resource=ROOT/'local-models/resources/assets/wildfire_gender/body/jenny-mesh.json'
    data=json.loads(source.read_text())
    data['smooth_surfaces']=[build(data,group) for group in ['breast','buttock']]
    data['surface_cleanup']={'method':'authored side convex closure, implicit union and surface nets','grid_model_pixels':STEP,'smoothing_model_pixels':.30,'retains_raw_quads':True}
    resource.write_text(json.dumps(data,separators=(',',':')))

if __name__=='__main__':main()

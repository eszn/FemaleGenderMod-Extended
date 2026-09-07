"""Headless, neutral-material inspection of imported model data (numpy/matplotlib)."""
from pathlib import Path
import argparse,json
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection

parser=argparse.ArgumentParser(); parser.add_argument('model',type=Path); args=parser.parse_args()
data=json.loads(args.model.read_text()); quads=[np.array(q['points']) for q in data['quads']]
fig=plt.figure(figsize=(14,7),facecolor='#18202a')
for group in ['body','breast','buttock','thigh']:
    p=np.concatenate([np.array(q['points']) for q in data['quads'] if q['group']==group])
    print(group,'bounds',p.min(axis=0).round(3),p.max(axis=0).round(3))
for i,(label,azim) in enumerate([('Front',-90),('Side',0),('Rear',90),('Rear 3/4',45)]):
    ax=fig.add_subplot(1,4,i+1,projection='3d',facecolor='#18202a')
    a=np.radians(azim); light=np.array([.55*np.cos(a),.8,.55*np.sin(a)]); light/=np.linalg.norm(light)
    colors=[]
    for q in quads:
        n=np.cross(q[1]-q[0],q[2]-q[0]); n/=np.linalg.norm(n)
        b=.28+.55*max(0,n@light); colors.append([b,b,b,1])
    ax.add_collection3d(Poly3DCollection([q[:,[0,2,1]] for q in quads],facecolors=colors,edgecolors='none',zsort='average'))
    ax.set(xlim=(-6,6),ylim=(-6,6),zlim=(5,24)); ax.set_box_aspect((12,12,19))
    ax.view_init(elev=0,azim=azim); ax.set_proj_type('ortho'); ax.set_axis_off(); ax.set_title(label,color='white')
fig.suptitle(data['character'].title()+' | imported authored body surfaces | original rest geometry',color='white')
fig.subplots_adjust(left=0,right=1,bottom=0,top=.9,wspace=0)
out=args.model.with_suffix('.png'); fig.savefig(out,dpi=130,facecolor=fig.get_facecolor()); print(out)

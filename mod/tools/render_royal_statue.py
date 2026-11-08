from pathlib import Path
import json,zipfile,io,math
import numpy as np
from PIL import Image,ImageDraw,ImageFont
R=Path('src/main/resources/assets/halloween');out=Path('preview/statue');out.mkdir(parents=True,exist_ok=True)
jar=next(Path('.gradle/loom-cache/minecraftMaven').rglob('minecraft-merged-*.jar'));archive=zipfile.ZipFile(jar)
def texture(ref):
 namespace,path=ref.split(':');local=Path('src/main/resources/assets')/namespace/'textures'/(path+'.png')
 return np.asarray(Image.open(local if local.exists() else io.BytesIO(archive.read(f'assets/{namespace}/textures/{path}.png'))).convert('RGBA'))
faces={'north':([0,1,2,3],[0,0,-1]),'south':([5,4,7,6],[0,0,1]),'west':([4,0,3,7],[-1,0,0]),'east':([1,5,6,2],[1,0,0]),'up':([3,2,6,7],[0,1,0]),'down':([4,5,1,0],[0,-1,0])}
mesh=[]
for suffix,offset in [('',0),('_top',32)]:
 d=json.loads((R/f'models/block/royal_villager_statue{suffix}.json').read_text());textures={k:texture(v) for k,v in d['textures'].items() if k!='particle'}
 for e in d['elements']:
  a=e['from'];b=e['to'];x,y,z=a;X,Y,Z=b
  points=np.array([[x,y,z],[X,y,z],[X,Y,z],[x,Y,z],[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z]],dtype=float)
  rotation=np.eye(3)
  if 'rotation' in e:
   r=e['rotation'];theta=math.radians(r['angle']);c,s=math.cos(theta),math.sin(theta);rotation=np.array([[1,0,0],[0,c,-s],[0,s,c]]);origin=np.array(r['origin']);points=(points-origin)@rotation.T+origin
  points[:,1]+=offset
  for face,(indices,normal) in faces.items():
   if face not in e['faces']:continue
   f=e['faces'][face];uv=f.get('uv',[0,0,16,16]);u,v,U,V=uv
   # Vertices are bottom-left, bottom-right, top-right, top-left for vertical faces.
   coords=np.array([[u,V],[U,V],[U,v],[u,v]],dtype=float)/16
   mesh.append((points[indices],coords,textures[f['texture'][1:]],np.array(normal)@rotation.T))
def render(eye):
 W,H=900,1050;background=np.array([238,233,224],float);rgb=np.tile(background,(H,W,1));depth=np.full((H,W),-1e9)
 forward=np.array(eye,float);forward/=np.linalg.norm(forward);right=np.cross(np.array([0,1,0]),forward);right/=np.linalg.norm(right);up=np.cross(forward,right)
 scale=19.;center=np.array([8,21,8]);sun=np.array([-0.5,1,-.8]);sun/=np.linalg.norm(sun)
 for p,uv,tex,normal in mesh:
  if np.dot(normal,forward)<0.001:continue
  q=p-center;screen=np.column_stack((W/2+q@right*scale,H/2-q@up*scale));zz=q@forward
  shade=.54+.46*max(0,float(normal@sun))
  for idx in [(0,1,2),(0,2,3)]:
   xy=screen[list(idx)];zv=zz[list(idx)];uvv=uv[list(idx)];mn=np.maximum(np.floor(xy.min(axis=0)).astype(int),[0,0]);mx=np.minimum(np.ceil(xy.max(axis=0)).astype(int),[W-1,H-1])
   if (mx<mn).any():continue
   gx,gy=np.meshgrid(np.arange(mn[0],mx[0]+1)+.5,np.arange(mn[1],mx[1]+1)+.5)
   x0,y0=xy[0];x1,y1=xy[1];x2,y2=xy[2];den=(y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
   if abs(den)<1e-8:continue
   aa=((y1-y2)*(gx-x2)+(x2-x1)*(gy-y2))/den;bb=((y2-y0)*(gx-x2)+(x0-x2)*(gy-y2))/den;cc=1-aa-bb
   z=aa*zv[0]+bb*zv[1]+cc*zv[2];area=(slice(mn[1],mx[1]+1),slice(mn[0],mx[0]+1));mask=(aa>=-1e-6)&(bb>=-1e-6)&(cc>=-1e-6)&(z>depth[area])
   us=aa*uvv[0,0]+bb*uvv[1,0]+cc*uvv[2,0];vs=aa*uvv[0,1]+bb*uvv[1,1]+cc*uvv[2,1];pixels=tex[np.clip((vs*tex.shape[0]).astype(int),0,tex.shape[0]-1),np.clip((us*tex.shape[1]).astype(int),0,tex.shape[1]-1)]
   mask&=pixels[:,:,3]>128;rgb[area][mask]=pixels[:,:,:3][mask]*shade;depth[area][mask]=z[mask]
 return Image.fromarray(np.uint8(np.clip(rgb,0,255)))
a=render([.9,.5,-1.5]);b=render([-.9,.35,-1.5]);a.save(out/'royal-villager-preview.png');b.save(out/'royal-villager-other-side.png')

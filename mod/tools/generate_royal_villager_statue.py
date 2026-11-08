"""Static block export of vanilla VillagerResemblingModel cuboids (64x64 entity UV)."""
from pathlib import Path
import json,zipfile,io
from PIL import Image,ImageOps
R=Path('src/main/resources/assets/halloween')
jar=next(Path('.gradle/loom-cache/minecraftMaven').rglob('minecraft-merged-*.jar'))
with zipfile.ZipFile(jar) as z:
 im=Image.open(io.BytesIO(z.read('assets/minecraft/textures/entity/villager/villager.png'))).convert('RGBA')
 alpha=im.getchannel('A'); gray=ImageOps.grayscale(im); stone=ImageOps.colorize(gray,'#444747','#c4c8c6').convert('RGBA');stone.putalpha(alpha)
out=R/'textures/block';out.mkdir(parents=True,exist_ok=True);stone.save(out/'royal_villager_stone.png')
# Cuboids and UV offsets taken from VillagerResemblingModel.getModelData.
# Entity coordinates are Y-down; the block export is Y-up with feet on y=4.
parts=[]
def entity_box(name,x,y,z,w,h,d,u,v,pivot=(0,0,0),rotation=None):
 ox,oy,oz=pivot;a=[8+x+ox,28-y-h-oy,8+z+oz];b=[a[0]+w,a[1]+h,a[2]+d]
 uv={'west':[u,v+d,u+d,v+d+h],'north':[u+d,v+d,u+d+w,v+d+h],'east':[u+d+w,v+d,u+2*d+w,v+d+h],'south':[u+2*d+w,v+d,u+2*d+2*w,v+d+h],'up':[u+d,v,u+d+w,v+d],'down':[u+d+w,v,u+d+2*w,v+d]}
 parts.append((name,a,b,{f:{'texture':'#villager','uv':[q/4 for q in rect]} for f,rect in uv.items()},rotation))
entity_box('head',-4,-10,-4,8,10,8,0,0)
entity_box('nose',-1,-3,-6,2,4,2,24,0)
entity_box('body',-4,0,-3,8,12,6,16,20)
entity_box('robe',-4,0,-3,8,20,6,0,38)
parts[-1][1][:]=[v-.5 for v in parts[-1][1]];parts[-1][2][:]=[v+.5 for v in parts[-1][2]]
entity_box('right leg',-2,0,-2,4,12,4,0,22,(-2,12,0))
entity_box('left leg',-2,0,-2,4,12,4,0,22,(2,12,0))
rot={'origin':[8,25,7],'axis':'x','angle':45,'rescale':False}
entity_box('folded right arm',-8,-2,-2,4,8,4,44,22,(0,3,-1),rot)
entity_box('folded left arm',4,-2,-2,4,8,4,44,22,(0,3,-1),rot)
entity_box('folded hands',-4,2,-2,8,4,4,40,38,(0,3,-1),rot)
def plain(name,a,b,texture):parts.append((name,a,b,{f:{'texture':'#'+texture,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')},None))
plain('pedestal',[-7,0,-7],[23,4,23],'base')
# Crown is the sole addition to the vanilla villager silhouette.
for a,b in [([3.5,37.5,3.5],[12.5,39,4.5]),([3.5,37.5,11.5],[12.5,39,12.5]),([3.5,37.5,4.5],[4.5,39,11.5]),([11.5,37.5,4.5],[12.5,39,11.5])]:plain('crown band',a,b,'crown')
for x in (3.5,7.5,11.5):
 for z in (3.5,11.5):plain('crown point',[x,39,z],[x+1,41,z+1],'crown')
bottom=[];upper=[]
for name,a,b,faces,rot in parts:
 for lo,hi,target,shift in [(a[1],min(b[1],32),bottom,0),(max(a[1],32),b[1],upper,32)]:
  if hi<=lo:continue
  q={'name':name,'from':[a[0],lo-shift,a[2]],'to':[b[0],hi-shift,b[2]],'faces':json.loads(json.dumps(faces))}
  # Crop side UVs when the head crosses the model boundary.
  for face in ('north','south','east','west'):
   uv=q['faces'][face]['uv'];v0,v1=uv[1],uv[3];height=b[1]-a[1];uv[1]=v0+(b[1]-hi)/height*(v1-v0);uv[3]=v0+(b[1]-lo)/height*(v1-v0)
  if rot:q['rotation']=rot
  target.append(q)
textures={'villager':'halloween:block/royal_villager_stone','base':'minecraft:block/stone_bricks','crown':'minecraft:block/chiseled_stone_bricks','particle':'minecraft:block/stone'}
for name,elements in [('royal_villager_statue',bottom),('royal_villager_statue_top',upper)]:
 (R/'models/block'/f'{name}.json').write_text(json.dumps({'textures':textures,'elements':elements},indent=2))
 (R/'blockstates'/f'{name}.json').write_text(json.dumps({'variants':{'':{'model':'halloween:block/'+name}}}))
(R/'items/royal_villager_statue.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':'halloween:block/royal_villager_statue'}}))
for lang in ('ru_ru','en_us'):
 p=R/'lang'/f'{lang}.json';data=json.loads(p.read_text(encoding='utf8'));data['block.halloween.villager_statue']='Каменный мутант (шутка)' if lang=='ru_ru' else 'Stone mutant (joke)';data['block.halloween.royal_villager_statue']='Статуя жителя с короной' if lang=='ru_ru' else 'Crowned villager statue';p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

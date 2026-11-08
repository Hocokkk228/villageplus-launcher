"""Author the flintlock mesh, original material atlas and an orthographic review render."""
import json, random, math
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/halloween'
rng=random.Random(1760)
atlas=Image.new('RGB',(32,32)); px=atlas.load()
for y in range(32):
 for x in range(32):
  if x<16:
   grain=int(8*math.sin(y*.8+x*.18))+rng.randrange(-4,5); base=(92,49,26)
  elif y<16: grain=rng.randrange(-5,6);base=(49,54,59)
  else: grain=rng.randrange(-5,6);base=(153,117,54)
  px[x,y]=tuple(max(0,min(255,c+grain)) for c in base)
atlas.save(ASSETS/'textures/item/musket_materials.png')
parts=[]
def box(name,a,b,mat='wood',rotation=None):
 uv={'wood':[0,0,8,16],'steel':[8,0,16,8],'brass':[8,8,16,16]}[mat]
 e={'name':name,'from':a,'to':b,'faces':{f:{'texture':'#atlas','uv':uv} for f in ['north','south','east','west','up','down']}}
 if rotation:e['rotation']=rotation
 parts.append(e)
# Forty-six model units long, from the shoulder to the open muzzle.
box('butt stock',[6.3,2.4,22],[9.7,8.2,30])
box('stock shoulder',[6.6,5.3,17],[9.4,9.1,24])
box('wrist grip',[6.9,6.1,12],[9.1,9.1,19])
box('full walnut forestock',[7,7.6,-12],[9,9.6,13])
box('butt plate',[6.15,2.3,29.5],[9.85,8.3,30.2],'brass')
box('receiver',[7,9,7],[9,10.5,13],'steel')
# Four strips leave a visible dark bore at the muzzle, without a capped front face.
for name,a,b in [('barrel top',[7.3,10.2,-15.8],[8.7,10.6,8]),('barrel bottom',[7.3,9.2,-15.8],[8.7,9.6,8]),('barrel left',[7.05,9.6,-15.8],[7.45,10.2,8]),('barrel right',[8.55,9.6,-15.8],[8.95,10.2,8])]:box(name,a,b,'steel')
box('breech plug',[7.1,9.3,7.3],[8.9,10.5,8.4],'steel')
for z in [-11.8,-2,6]:
 box('barrel band',[6.85,7.45,z],[9.15,10.7,z+.5],'brass')
box('ramrod',[7.7,7,-14.8],[8.15,7.45,11],'steel')
for z in [-9,1]:box('ramrod pipe',[7.45,6.9,z],[8.4,7.6,z+1],'brass')
box('side lock plate',[9.1,7.9,8.5],[9.6,9.7,14],'steel')
box('flash pan',[9.35,9.5,7.9],[10.3,10,9.6],'brass')
box('hammer stem',[9.5,9.4,11.5],[10,11.4,12.3],'steel')
box('flint jaws',[9.35,11,10.4],[10.2,11.7,12.1],'steel')
box('flint',[9.45,11.2,10],[10.1,11.55,10.8],'brass')
box('trigger',[7.8,5.9,13.1],[8.2,7.7,13.6],'steel')
box('guard front',[7.5,5.2,11.7],[8.5,7.7,12.2],'brass')
box('guard bottom',[7.5,5.1,11.7],[8.5,5.55,16.8],'brass')
box('guard rear',[7.5,5.3,16.3],[8.5,7.6,16.8],'brass')
box('front sight',[7.8,10.6,-14],[8.2,11.2,-13.2],'steel')
box('rear sight',[7.2,10.5,7],[7.8,11,7.5],'steel')
box('rear sight',[8.2,10.5,7],[8.8,11,7.5],'steel')
model={'credit':'Original Halloween/VillagePlus flintlock mesh','textures':{'atlas':'halloween:item/musket_materials','particle':'halloween:item/musket_materials'},'elements':parts,'display':{
 'firstperson_righthand':{'rotation':[0,0,0],'translation':[1.2,-1.4,-3],'scale':[1,1,1]},
 'firstperson_lefthand':{'rotation':[0,0,0],'translation':[1.2,-1.4,-3],'scale':[1,1,1]},
 'thirdperson_righthand':{'rotation':[-90,0,0],'translation':[0,1,-1],'scale':[.95,.95,.95]},
 'thirdperson_lefthand':{'rotation':[-90,0,0],'translation':[0,1,-1],'scale':[.95,.95,.95]},
 'gui':{'rotation':[25,135,0],'translation':[0,0,0],'scale':[.46,.46,.46]},
 'ground':{'rotation':[0,0,90],'translation':[0,3,0],'scale':[.6,.6,.6]},
 'fixed':{'rotation':[0,90,0],'scale':[.5,.5,.5]}}}
(ASSETS/'models/item/musket.json').write_text(json.dumps(model,indent=2)+'\n')
# Review mesh itself, independently of Minecraft's viewmodel transforms.
img=Image.new('RGB',(1400,650),(24,27,32));d=ImageDraw.Draw(img)
def project(v):
 x,y,z=v;return (780+(x-8)*22-z*22,390-y*22+z*3+(x-8)*9)
faces=[]
for e in parts:
 a,b=e['from'],e['to'];x,y,z=a;X,Y,Z=b
 material=e['faces']['north']['uv'];col=(116,65,34) if material[0]==0 else (65,73,82) if material[1]==0 else (179,139,69)
 for verts,k in [([(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],1.2), ([(X,y,z),(X,Y,z),(X,Y,Z),(X,y,Z)],.75), ([(x,y,z),(X,y,z),(X,Y,z),(x,Y,z)],1)]:
  depth=sum(v[2]-v[0] for v in verts)/4;faces.append((depth,verts,tuple(min(255,int(c*k)) for c in col)))
for _,verts,c in sorted(faces,reverse=True):d.polygon([project(v) for v in verts],fill=c,outline=(24,23,22))
d.text((45,40),'VILLAGEPLUS / FLINTLOCK MUSKET',fill=(225,218,198))
d.text((45,65),'Original 3D mesh | walnut stock / steel barrel / brass fittings',fill=(150,157,166))
out=ROOT/'preview/musket-0.40.2.png';out.parent.mkdir(exist_ok=True);img.save(out)
print(f'{len(parts)} elements; preview: {out}')

"""Original volumetric period weapons; item JSON and renders share the same mesh."""
from pathlib import Path
import json,math,sys
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/halloween';sys.path.insert(0,str(ROOT/'tools'))
from render_pumpkin import render
atlas=Image.new('RGB',(32,32));d=ImageDraw.Draw(atlas)
for y in range(32):
 for x in range(32):
  base=(107,57,32)if x<16 else(176,187,193)if y<16 else(181,135,56)
  grain=((x*13+y*7)%9)-4;atlas.putpixel((x,y),tuple(max(0,min(255,c+grain))for c in base))
atlas.save(A/'textures/item/period_weapons.png')
for name in ['revolver','rapier','sabre']:
 elements=[]
 def box(label,a,b,mat='metal',rotation=None):
  uv={'wood':[0,0,8,16],'metal':[8,0,16,8],'gold':[8,8,16,16]}[mat]
  e={'name':label,'from':a,'to':b,'faces':{f:{'texture':'#atlas','uv':uv}for f in ['north','south','east','west','up','down']}}
  if rotation:e['rotation']=rotation
  elements.append(e)
 if name=='revolver':
  box('frame',[6.3,8,2],[9.7,11,9]);box('walnut grip',[6.6,2,7],[9.4,8,12],'wood',{'origin':[8,7,8],'axis':'x','angle':22.5})
  box('grip cap',[6.5,1.8,8.8],[9.5,2.4,12.4],'gold')
  box('octagonal barrel upper',[7.25,10,-12],[8.75,11.3,2]);box('octagonal barrel lower',[7.25,9,-12],[8.75,10,2]);box('barrel left',[6.8,9.5,-12],[7.3,10.8,2]);box('barrel right',[8.7,9.5,-12],[9.2,10.8,2])
  # Muzzle face has an actual opening between four strips.
  for i in range(8):
   a=i*math.pi/4;x=8+1.8*math.cos(a);y=9.6+1.8*math.sin(a)
   box('cylinder flute',[x-.65,y-.65,2.4],[x+.65,y+.65,6.9])
  box('cylinder axle',[7.7,9.3,1],[8.3,9.9,8],'gold');box('top strap',[6.5,11.5,1.5],[9.5,12,8])
  box('hammer',[7.5,11,8],[8.5,13,9]);box('front sight',[7.7,11.3,-11],[8.3,12,-10.3])
  box('trigger',[7.7,6.8,5.4],[8.3,8.3,6.1],'gold');box('trigger guard front',[7.5,5.7,3.7],[8.5,8.4,4.2],'gold');box('trigger guard bottom',[7.5,5.5,3.7],[8.5,6,7.5],'gold');box('trigger guard rear',[7.5,6,7],[8.5,7.2,7.6],'gold')
  first={'rotation':[0,0,0],'translation':[1,-1,-2],'scale':[.95,.95,.95]};third={'rotation':[-90,0,0],'translation':[0,1,0],'scale':[.8,.8,.8]}
 else:
  box('leather wrapped grip',[7.3,2,7.25],[8.7,8,8.75],'wood')
  for y in range(2,8):box('grip binding',[7.2,y,7.15],[8.8,y+.18,8.85],'gold')
  box('pommel',[7,1,7],[9,2.2,9],'gold');box('blade collar',[7.25,8,7.2],[8.75,10.4,8.8])
  if name=='rapier':
   box('long straight blade',[7.7,10,7.7],[8.3,30,8.3]);box('needle point',[7.85,30,7.85],[8.15,32,8.15]);box('crossguard',[4.5,8.8,7.5],[11.5,9.4,8.5],'gold')
   for i in range(12):
    a=i*math.pi*2/12;xx=8+3*math.cos(a);zz=8+2*math.sin(a)
    box('swept basket guard',[xx-.3,8.3,zz-.3],[xx+.3,8.8,zz+.3],'gold')
   box('knuckle bow',[10.6,2.5,7.7],[11.1,9,8.3],'gold')
  else:
   for i in range(11):
    y=10+i*2;curve=(i/10)**2*4
    box('curved blade',[7+curve,y,7.7],[8.8+curve,y+2,8.3]);box('blade ridge',[8.2+curve,y,7.55],[8.55+curve,y+2,8.45])
   box('sabre guard',[5.5,8.7,7.4],[11.5,9.4,8.6],'gold');box('D guard',[10.8,2.5,7.6],[11.4,9,8.4],'gold');box('guard base',[8.5,2.3,7.6],[11.4,2.9,8.4],'gold')
  first={'rotation':[0,-90,20],'translation':[1,0,-1],'scale':[.85,.85,.85]};third={'rotation':[0,0,0],'translation':[0,2,0],'scale':[.85,.85,.85]}
 model={'textures':{'atlas':'halloween:item/period_weapons','particle':'halloween:item/period_weapons'},'elements':elements,'display':{'firstperson_righthand':first,'firstperson_lefthand':first,'thirdperson_righthand':third,'thirdperson_lefthand':third,'gui':{'rotation':[25,135,-25 if name!='revolver'else 0],'scale':[.65,.65,.65]},'ground':{'scale':[.5,.5,.5]}}}
 (A/'models/item'/f'{name}.json').write_text(json.dumps(model,indent=2)+'\n');(A/'items'/f'{name}.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':'halloween:item/'+name}}))
 render(35,18,f'{name}-043.png',model_data=model,scale=22 if name=='revolver'else 19,center_y=9 if name=='revolver'else 17)
 print(name,len(elements))

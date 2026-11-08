#!/usr/bin/env python3
"""Original vanilla-textured wooden vessels. Rendering and collision use the same cuboids."""
import json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
MODELS=ROOT/'src/main/resources/data/halloween/rail_models';MODELS.mkdir(parents=True,exist_ok=True)
def build(name,sx,sz,sy):
 parts=[]
 def transform(x,y,z):return round(x*sx,4),round(y if y<=7 else 7+(y-7)*sy,4),round(z*sz,4)
 def box(block,x0,y0,z0,x1,y1,z1,solid=True,group='hull',door=False):
  a=transform(x0,y0,z0);b=transform(x1,y1,z1)
  p={'block':'minecraft:'+block,'box':list(a+b),'solid':solid,'group':group}
  if door:p.update(door=True,hinged=True,hinge=list(transform(-.8,3,-7.02)))
  parts.append(p)
 def line(block,a,b,thick=.07,group='rigging'):
  a=transform(*a);b=transform(*b);d=[b[i]-a[i] for i in range(3)];length=math.sqrt(sum(x*x for x in d))
  if length<.001:return
  d=[x/length for x in d];q=[d[2],0,-d[0],1+d[1]]
  if q[3]<1e-6:q=[1,0,0,0]
  norm=math.sqrt(sum(x*x for x in q));q=[round(x/norm,7) for x in q];r=thick*min(sx,sz)/2
  parts.append({'block':'minecraft:'+block,'box':[-r,-length/2,-r,r,length/2,r],'solid':False,'group':group,'rotation':q,'pivot':[(a[i]+b[i])/2 for i in range(3)]})
 def width(z):
  return max(.25,4*(max(0,1-(abs((z+1)/16))**2))**.55)*(1 if z>-10 else .90)
 hull='dark_oak_planks' if name=='ship_brig' else 'spruce_planks'
 trim='stripped_spruce_wood' if name=='ship_brig' else 'stripped_dark_oak_wood'
 # Bent ribs and plank shell; the keel is beneath the waterline, the hold stays dry above it.
 for z in range(-14,15):
  w=width(z+.5)
  for row in range(6):
   y=-2+row*.82;wr=max(.18,w*(.35+.65*(row/5)**.55))
   for side in (-1,1):
    # Each plank band bridges the change in beam from the row below and
    # neighboring slices. Thin vertical ribs alone leave daylight between rows.
    lower=max(.18,w*(.35+.65*(max(0,row-1)/5)**.55))
    adjacent=[max(.18,width(zz)*(.35+.65*(row/5)**.55)) for zz in (z-.5,z+1.5)]
    inner=min(wr,lower,*adjacent)-.17;outer=max(wr,*adjacent)+.17
    xa,xb=sorted((side*max(0,inner),side*outer))
    box(hull,xa,y-.025,z-.025,xb,y+.86,z+1.025)
    if row in (3,5):box(trim,xa-.18,y+.62,z-.01,xa+.18,y+.76,z+1.01,False)
  box('stripped_dark_oak_wood',-.24,-2.2,z,.24,-1.45,z+1)
  box(hull,-max(.18,w*.35)-.18,-2.12,z-.025,max(.18,w*.35)+.18,-1.80,z+1.025)
  if z in (-14,14):
   for row in range(6):
    wr=max(.18,w*(.35+.65*(row/5)**.55))
    box(hull,-wr-.17,-2+row*.82,z-.04,wr+.17,-2+row*.82+.87,z+.15 if z==-14 else z+1.04)
  box('spruce_planks',-w+.05,.72,z-.025,w-.05,.95,z+1.025)
  # Main deck, with a real open companionway and stairs into the hold.
  if -4<=z<=0:
   box('oak_planks',-w+.1,2.86,z,-1.15,3,z+1);box('oak_planks',1.15,2.86,z,w-.1,3,z+1)
  else:box('oak_planks',-w+.1,2.86,z,w-.1,3,z+1)
  for side in (-1,1):
   if -7<=z<=-4:continue # Boarding/quarterdeck stair openings.
   x=side*w
   box(trim,x-.13,3,z,x+.13,3.75,z+1)
   box('dark_oak_planks',x-.19,3.68,z-.02,x+.19,3.83,z+1.02,False)
   if z%3==0:box('stripped_oak_wood',x-.08,3.8,z+.42,x+.08,4.17,z+.58,False)
 # Companionway: wide physical treads, not a decorative ladder texture.
 for i in range(4):
  floor=2.5-i*.5;z=-4+i*1.15
  box('spruce_planks',-1.1,floor-.22,z,1.1,floor,z+1.19)
 for x in (-1.28,1.28):
  box(trim,x-.08,3,-4.2,x+.08,4.0,.8)
  box('dark_oak_planks',x-.11,3.93,-4.3,x+.11,4.08,.9,False)
 # Stern cabin, mullioned windows, functional centre door, and a walkable quarterdeck.
 for side in (-1,1):
  x=side*2.75
  box(hull,x-.14,3,-13,x+.14,4,-7)
  box(trim,x-.14,5.45,-13,x+.14,6,-7)
  for z in (-12.8,-10.2,-7.2):box(trim,x-.17,4,z-.12,x+.17,5.5,z+.12)
  for za,zb in ((-12.63,-10.37),(-10.03,-7.37)):box('light_blue_stained_glass',x-.045,4,za,x+.045,5.45,zb)
 for xa,xb in ((-2.8,-.82),(.82,2.8)):box(hull,xa,3,-7.1,xb,5.4,-6.87)
 box(trim,-2.9,5.4,-7.15,2.9,6,-6.84)
 # Framed cabin door: all moving pieces share one hinge, including the handle.
 for x in (-.88,.88):box(trim,x-.075,3,-7.19,x+.075,5.45,-6.80)
 box(trim,-.95,5.3,-7.19,.95,5.45,-6.80)
 for x in (-.72,.72):box('dark_oak_planks',x-.065,3.05,-7.075,x+.065,5.28,-6.925,True,door=True)
 for y in (3.05,4.30,5.16):box('dark_oak_planks',-.72,y,-7.075,.72,y+.12,-6.925,True,door=True)
 box('spruce_planks',-.65,3.18,-7.045,.65,4.28,-6.955,True,door=True)
 box('light_blue_stained_glass',-.64,4.43,-7.015,.64,5.15,-6.985,True,door=True)
 for x in (-.23,.23):box(trim,x-.025,4.42,-6.98,x+.025,5.16,-6.92,True,door=True)
 for y in (3.35,4.9):box('blackstone',-.79,y,-6.92,-.57,y+.13,-6.87,False,door=True)
 box('gold_block',.47,4.08,-6.925,.57,4.35,-6.86,False,door=True)
 box('gold_block',.48,4.13,-6.86,.74,4.21,-6.79,False,door=True)
 box('spruce_planks',-2.9,3,-13.25,2.9,4,-13.0)
 for xa,xb in ((-2.82,-.96),(-.8,.8),(.96,2.82)):box('light_blue_stained_glass',xa,4,-13.17,xb,5.5,-13.06)
 box(trim,-2.98,5.5,-13.32,2.98,5.77,-12.97)
 for x in (-2.9,-.88,.88,2.9):box(trim,x-.08,4,-13.32,x+.08,5.55,-12.97)
 box('oak_planks',-3.05,5.77,-13.5,3.05,6.0,-6.8)
 for side in (-1,1):
  box(trim,side*3.05-.12,6,-13.5,side*3.05+.12,6.85,-11.35)
  for i in range(6):
   floor=3.5+i*.5;z=-6.9-i*.72
   xa,xb=(2.8,4.3) if side>0 else (-4.3,-2.8)
   box('spruce_planks',xa,floor-.22,z-.73,xb,floor,z)
  xa,xb=(2.5,4.3) if side>0 else (-4.3,-2.5)
  box('oak_planks',xa,5.76,-11.2,xb,6,-10.4)
 box(trim,-3.15,6,-13.5,3.15,6.85,-13.28)
 # Foredeck and bow ornaments.
 for z in range(9,15):
  w=width(z+.5);box('oak_planks',-w,3.72,z,w,4,z+1)
 for i in range(2):box('spruce_planks',-1.5,3.5+i*.5,8+i*.5,1.5,3.72+i*.5,8.5+i*.5)
 box('stripped_dark_oak_wood',-.17,4.05,12,.17,4.4,19.5)
 for side in (-1,1):line('brown_terracotta',(side*2.5,3.9,10),(0,4.4,19.5),.08)
 # Helm, with an eight-spoke wooden wheel and brass hub; the control chair is behind it.
 box('stripped_spruce_wood',-.15,6,-10.85,.15,6.8,-10.55)
 for i in range(16):
  a=i*math.tau/16;x=math.cos(a)*.55;y=6.98+math.sin(a)*.55
  box('dark_oak_planks',x-.10,y-.10,-10.79,x+.10,y+.10,-10.64,True,'wheel')
 for i in range(8):
  a=i*math.tau/8;line('oak_planks',(0,6.98,-10.72),(math.cos(a)*.56,6.98+math.sin(a)*.56,-10.72),.07,'wheel')
 box('gold_block',-.12,6.86,-10.83,.12,7.1,-10.62,True,'wheel')
 def seat(x,y,z,upholstery):
  box('spruce_planks',x-.4,y,z-.4,x+.4,y+.28,z+.4)
  box(upholstery,x-.34,y+.28,z-.35,x+.34,y+.39,z+.35,False)
  box(trim,x-.43,y+.25,z-.5,x+.43,y+1.1,z-.35)
 seat(0,6,-11.9,'red_wool')
 for x in (-2.2,2.2):
  for z in (-4.9,1.8,5.5):seat(x,3,z,'brown_wool')
 # Furniture visible through the cabin windows and cargo below the main deck.
 box('dark_oak_planks',-.7,3,-10.7,.7,3.15,-8.1)
 box('oak_planks',-.95,4,-10.8,.95,4.2,-8.0)
 box('white_wool',-.83,4.21,-10.4,.83,4.24,-8.3,False)
 for x in (-1.9,1.9):
  for z in (3.2,4.6,6):
   box('barrel',x-.48,.95,z-.48,x+.48,1.95,z+.48)
   if z==4.6:box('barrel',x-.45,1.95,z-.45,x+.45,2.65,z+.45)
 for x,z,y in ((-2.6,-7.2,5.2),(2.6,-7.2,5.2),(-1.2,5,2.2),(1.2,5,2.2)):
  box('dark_oak_planks',x-.19,y-.1,z-.19,x+.19,y+.35,z+.19,False)
  box('glowstone',x-.13,y,z-.13,x+.13,y+.23,z+.13,False)
 # Two masts, curved panel sails, yards, stays and shrouds.
 for z,top in (((1.8,23),(8.0,18.8)) if name=='ship_brig' else ((3.5,20),)):
  for y in range(3,int(top)):
   radius=.25 if y<10 else .18
   box('spruce_log',-radius,y,z-radius,radius,y+1.05,z+radius)
  for y,half,height in (((top-1,4.5 if top>20 else 3.3,4.3),(top-7,5.8 if top>20 else 4.4,5.5)) if name=='ship_brig' else ()):
   box('dark_oak_planks',-half-.4,y,z-.13,half+.4,y+.2,z+.13,False)
   for col in range(10):
    xa=-half+2*half*col/10;xb=-half+2*half*(col+1)/10
    mid=(xa+xb)/2;bulge=.6*(1-(mid/half)**2)
    for row in range(4):
     yy=y-height+height*row/4
     bends=[.6*(1-(xx/half)**2)*math.sin(math.pi*rr/4) for xx in (xa,xb) for rr in (row,row+1)]
     box('white_wool',xa,yy,z+.2+min(bends),xb,yy+height/4,z+.25+max(bends),False,'sails')
   for side in (-1,1):line('brown_terracotta',(side*half,y,z),(side*3.0,3.85,z-3),.055)
  for side in (-1,1):line('brown_terracotta',(0,top-1,z),(side*3.3,3.85,z-2),.065)
  box('red_wool',0,top-.05,z,2.0,top+.50,z+.055,False,'sails')
 if name=='ship_cutter':
  line('dark_oak_planks',(0,5,-8),(0,5,3.5),.18)
  line('dark_oak_planks',(0,14,-8),(0,19,3.5),.14)
  for row in range(10):
   y=5+row*1.25;back=-8+max(0,row-6)*1.0
   for panel in range(4):
    za=back+(3.25-back)*panel/4;zb=back+(3.25-back)*(panel+1)/4;bends=[.35*math.sin(p*math.pi/4) for p in (panel,panel+1)]
    box('white_wool',-.12-max(bends),y,za,-.07-min(bends),y+1.25,zb,False,'sails')
 line('brown_terracotta',(0,18,8 if name=='ship_brig' else 3.5),(0,4.4,19.5),.07)
 # Triangular forward jib, built from thin fabric strips.
 for i in range(9):
  y=5.3+i*1.25;z0=9.3 if name=='ship_brig' else 3.8;z1=18.5-i*.92
  if z1>z0:box('white_wool',.05,y,z0,.11,y+1.25,z1,False,'sails')
 # Broadside cannon carriages leave the centre walkway and boarding stairs clear.
 for idx in range(4 if name=='ship_brig' else 2):
  side=-1 if idx%2==0 else 1;x=side*2.85;z=7.6 if name=='ship_cutter' or idx>=2 else 2.9
  box('dark_oak_planks',x-.7,3,z-.56,x+.7,3.30,z+.56)
  for xx in (-.50,.50):
   for zz in (-.60,.60):box('stripped_dark_oak_wood',x+xx-.19,3,z+zz-.10,x+xx+.19,3.4,z+zz+.10)
  # Layered octagonal barrel, open dark bore, brass trunnions and breech.
  for xa,xb,ry,rz in ((-.82,.92,.21,.21),(-.72,.8,.28,.14),(-.72,.8,.14,.28)):
   a,b=sorted((x+side*xa,x+side*xb));box('polished_deepslate',a,3.85-ry,z-rz,b,3.85+ry,z+rz,True,'cannon_barrel'+str(idx))
  for zz in (-.32,.32):box('iron_block',x-.12,3.57,z+zz-.08,x+.12,3.85,z+zz+.08,True,'cannon_barrel'+str(idx))
  a,b=sorted((x+side*.88,x+side*1.07));box('black_concrete',a,3.68,z-.17,b,4.02,z+.17,True,'cannon_barrel'+str(idx))
  box('gold_block',x-side*.8-.11,3.75,z-.1,x-side*.8+.11,3.95,z+.1,True,'cannon_barrel'+str(idx))
 # Deployable gangways, both sides; all steps are real collision geometry.
 for side in (-1,1):
  for i in range(6):
   x=side*(3.8+i*.6);xa,xb=sorted((x,x+side*.63));floor=3-i*.5
   box('spruce_planks',xa,floor-.18,-6.2,xb,floor,-4.7,True,'gangway')
   for z in (-6.25,-4.65):box('dark_oak_planks',xa,floor+.75,z-.055,xb,floor+.87,z+.055,False,'gangway')
 # Rudder and anchor ornaments under the stern/bow.
 box('dark_oak_planks',-.14,-1.9,-15.0,.14,.6,-13.1)
 for side in (-1,1):
  x=side*2.3;line('blackstone',(x,3.5,10),(x,1.1,10.2),.1)
  box('polished_blackstone',x-.35,.9,10.1,x+.35,1.1,10.35,False)
 target=MODELS/(name+'.json');target.write_text(json.dumps(parts,separators=(',',':')))
 # A closed, purpose-built inventory model. Never subsample hull planks.
 item=name.replace('ship_','sailing_');assets=ROOT/'src/main/resources/assets/halloween'
 (assets/'models/item'/f'{item}.json').write_text(json.dumps(miniature(name),separators=(',',':')))
 (assets/'items'/f'{item}.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':'halloween:item/'+item}}))
 print(name,len(parts),'parts',sum(p['solid'] for p in parts),'solid; size',round(16*sx,1),round(35*sz,1))
 return parts
def miniature(name):
 brig=name=='ship_brig';wood='dark_oak_planks' if brig else 'spruce_planks'
 elements=[]
 def cuboid(texture,a,b):
  elements.append({'from':a,'to':b,'faces':{f:{'texture':'#'+texture} for f in ('north','south','east','west','up','down')}})
 # Solid stepped hull with a continuous deck, pointed bow, stern cabin and keel.
 for z in range(14):
  beam=max(.35,2.25*(max(0,1-((z-6.1)/7.7)**2))**.55)
  for row in range(3):
   w=beam*(.55+.225*row)
   cuboid(wood,[8-w,1.8+row*.75,1+z],[8+w,2.57+row*.75,2+z])
  cuboid('oak_planks',[8-beam,4.05,1+z],[8+beam,4.28,2+z])
  for side in (-1,1):
   x=8+side*beam
   cuboid('stripped_spruce_wood',[x-.11,4.25,1+z],[x+.11,4.7,2+z])
 cuboid(wood,[6.3,4.28,1.4],[9.7,6.0,4.8])
 cuboid('oak_planks',[6.1,5.95,1.2],[9.9,6.2,5.0])
 for x in (6.27,9.58):cuboid('light_blue_stained_glass',[x,4.8,2],[x+.15,5.65,4.1])
 cuboid('stripped_dark_oak_wood',[7.88,1.55,1],[8.12,2.2,14.8])
 cuboid('stripped_spruce_wood',[7.9,4.65,12.7],[8.1,4.9,15.9])
 for z,top in ([(5.8,14.7),(10,12.6)] if brig else [(6.9,14.7)]):
  cuboid('stripped_spruce_wood',[7.88,4.28,z-.12],[8.12,top,z+.12])
  for y,half,h in ([(top-1,2.25,2.8),(top-4.4,2.8,3)] if brig else [(top-1,2.5,5.7)]):
   cuboid('stripped_dark_oak_wood',[8-half-.25,y+.02,z-.08],[8+half+.25,y+.17,z+.08])
   for stripe in range(8):
    u=stripe/8;v=(stripe+1)/8;bends=[.35*math.sin(t*math.pi) for t in (u,v)]
    width=half*(1-.12*u) if brig else half*(1-.6*u)
    cuboid('white_wool',[8-width,y-h*v,z-.055-max(bends)],[8+width,y-h*u,z+.055-min(bends)])
  cuboid('red_wool' if brig else 'blue_wool',[8.13,top-.5,z-.06],[9.1,top-.12,z+.06])
 textures={t:'minecraft:block/'+t.replace('_wood','_log') for t in (wood,'oak_planks','stripped_spruce_wood','stripped_dark_oak_wood','light_blue_stained_glass','white_wool','red_wool' if brig else 'blue_wool')}
 textures['particle']='minecraft:block/'+wood
 hand={'rotation':[5,-35,0],'translation':[0,8.5,0],'scale':[.65,.65,.65]}
 return {'textures':textures,'elements':elements,'display':{'gui':{'rotation':[20,145,0],'translation':[0,0,0],'scale':[.95,.95,.95]},'firstperson_righthand':hand,'firstperson_lefthand':{**hand,'rotation':[5,35,0]},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.6,.6,.6]},'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.6,.6,.6]},'ground':{'scale':[.6,.6,.6]},'fixed':{'rotation':[0,90,0]}}}

if __name__=='__main__':
 build('ship_brig',1,1,1)
 build('ship_cutter',.78,.70,.80)

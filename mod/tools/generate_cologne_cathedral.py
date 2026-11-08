"""Original voxel interpretation of Cologne Cathedral, fitted to the 65x88 castle plot.
Reference proportions: koelner-dom.de/erleben/der-dom-in-zahlen.
Not a survey/1:1 replica. No third-party model geometry is copied.
"""
from pathlib import Path
import math,json,zipfile,io
import nbtlib as n
from PIL import Image,ImageDraw,ImageEnhance
ROOT=Path(__file__).resolve().parents[1];R=ROOT/'src/main/resources/data/halloween';W,H,D=65,100,88;b={}
STONE='stone_bricks';RIB='polished_andesite';DARK='polished_deepslate'
def put(x,y,z,v,**p):
 if 0<=x<W and 0<=y<H and 0<=z<D:b[x,y,z]=('minecraft:'+v,p)
def box(x0,y0,z0,x1,y1,z1,v,**p):
 for x in range(x0,x1+1):
  for y in range(y0,y1+1):
   for z in range(z0,z1+1):put(x,y,z,v,**p)
def line(a,c,v):
 length=max(abs(c[i]-a[i]) for i in range(3))*2
 for j in range(length+1):
  t=j/max(1,length);put(*(round(a[i]+(c[i]-a[i])*t) for i in range(3)),v)
def pier(x,z,y=30):
 box(x-1,1,z-1,x+1,y,z+1,STONE)
 for dx,dz in [(2,0),(-2,0),(0,2),(0,-2)]:box(x+dx,1,z+dz,x+dx,y,z+dz,RIB)
 box(x-2,0,z-2,x+2,1,z+2,RIB);box(x-2,y,z-2,x+2,y,z+2,'chiseled_stone_bricks')
def lancet(axis,constant,center,bottom,width,height):
 for u in range(-width,width+1):
  tip=bottom+height-abs(u)*2
  for y in range(bottom,tip+1):
   v='light_blue_stained_glass' if y%5 else 'blue_stained_glass'
   if u in (-width,width) or y==tip or u==0:v=RIB
   if axis=='x':put(constant,y,center+u,v)
   else:put(center+u,y,constant,v)
def rose(z,cx,cy,r):
 for dx in range(-r-1,r+2):
  for dy in range(-r-1,r+2):
   dist=math.hypot(dx,dy)
   if dist>r+1:continue
   edge=dist>=r-.5
   spoke=abs(dx)<=.3 or abs(dy)<=.3 or abs(abs(dx)-abs(dy))<.4
   put(cx+dx,cy+dy,z,RIB if edge or spoke else ('red_stained_glass' if (dx+dy)%3==0 else 'light_blue_stained_glass'))
def finial(x,y,z,h=7):
 for k in range(h):
  rad=1 if k<2 else 0
  box(x-rad,y+k,z-rad,x+rad,y+k,z+rad,RIB if k%3 else STONE)
 put(x,y+h,z,'stone_brick_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
# Paved plinth, five-aisled longitudinal church and projecting transept.
for x in range(W):
 for z in range(D):put(x,0,z,'polished_andesite' if (x+z)%8==0 else 'stone_bricks')
box(12,1,5,52,28,74,'air');box(3,1,39,61,28,56,'air')
for x in (12,52):
 box(x,1,6,x,16,74,STONE)
 for z in range(17,73,7):lancet('x',x,z,4,2,11)
for x in (24,40):
 box(x,17,14,x,30,72,STONE)
 for z in range(18,72,7):lancet('x',x,z,20,2,8)
# Three sets of clustered piers and transverse/diagonal stone vault ribs.
for z in range(17,73,7):
 for x in (24,40):pier(x,z,22)
 for x in (18,46):pier(x,z,12)
 for k in range(17):
  x=24+k;y=23+round(7*math.sin(math.pi*k/16))
  put(x,y,z,RIB)
  if z+7<=74:
   put(x,y,z+round(k*7/16),RIB);put(x,y,z+7-round(k*7/16),RIB)
 for side in (-1,1):
  outer=32+side*26;inner=32+side*8
  box(outer-1,1,z-1,outer+1,16,z+1,STONE);finial(outer,17,z)
  for k in range(19):
   t=k/18;x=round(outer+(inner-outer)*t);y=round(16+12*t+2*math.sin(math.pi*t));put(x,y,z,RIB)
  # Lower aisle buttress tier.
  line((outer,12,z),(32+side*20,18,z),RIB)
# Steep slate main roof and lower aisle roofs; continuous ridge caps.
for z in range(14,76):
 for x in range(24,41):put(x,31+8-abs(x-32),z,'deepslate_tiles')
 for side in (-1,1):
  for k in range(12):put(32+side*(9+k),24-k//2,z,'deepslate_tiles')
 put(32,40,z,'deepslate_tile_slab',type='bottom',waterlogged='false')
# Transept facades with rose windows and elaborate pointed gables.
for x in (3,61):
 box(x,1,39,x,30,56,STONE)
 for z in (42,47,52):lancet('x',x,z,3,2,15)
 for k in range(9):
  for z in range(39+k,57-k):put(x,31+k,z,STONE)
for x in range(3,62):
 for z in range(39,57):put(x,31+8-abs(z-47),z,'deepslate_tiles')
# Polygonal choir and seven radiating chapel suggestions, kept inside the plot.
for deg in range(0,181):
 a=math.radians(deg);x=round(32+20*math.cos(a));z=round(71+15*math.sin(a))
 box(x,1,z,x,18,z,STONE)
 if deg%20==0:finial(x,19,z,5)
for z in range(70,87):
 for x in range(12,53):
  if ((x-32)/20)**2+((z-71)/15)**2<=1:
   put(x,19,z,'deepslate_tiles')
for deg in range(15,181,25):
 a=math.radians(deg);x=round(32+18*math.cos(a));z=round(71+13*math.sin(a));pier(x,z,15)
for z in range(70,81):
 for x in range(24,41):put(x,31+8-abs(x-32),z,'deepslate_tiles')
# Western front: deep portals, broad central window, two tower blocks.
box(12,1,5,52,30,5,STONE)
for cx in (18,32,46):
 for depth in range(3):
  z=5+depth
  for dx in range(-4+depth,5-depth):
   for y in range(1,11-abs(dx)):
    put(cx+dx,y,z,'air')
  for dx in range(-4+depth,5-depth):put(cx+dx,11-abs(dx),z,RIB)
rose(5,32,22,6)
for k in range(9):
 for x in range(24+k,41-k):put(x,31+k,5,STONE)
for cx in (18,46):
 box(cx-6,1,5,cx+6,48,17,STONE);box(cx-4,1,7,cx+4,47,15,'air')
 for y in (12,25,38,48):box(cx-7,y,4,cx+7,y,18,RIB)
 for z in (5,17):
  for y in (5,17,30,41):lancet('z',z,cx,y,3,7)
 for x in (cx-6,cx+6):
  for y in (17,30,41):lancet('x',x,11,y,3,7)
 for dx in (-6,6):
  for dz in (0,12):
   box(cx+dx,1,5+dz,cx+dx,51,5+dz,RIB);finial(cx+dx,52,5+dz,8)
 # Openwork octagonal spire: eight tapering ribs with horizontal stone tracery.
 for y in range(49,95):
  radius=max(0,round(6*(95-y)/46))
  for octant in range(8):
   a=math.pi*octant/4;x=round(cx+radius*math.cos(a));z=round(11+radius*math.sin(a));put(x,y,z,RIB)
  if y%6==0:
   for angle in range(0,360,8):
    a=math.radians(angle);put(round(cx+radius*math.cos(a)),y,round(11+radius*math.sin(a)),STONE)
 finial(cx,95,11,3)
 # Bell loft, accessible inner staircase.
 box(cx-4,37,7,cx+4,37,15,'spruce_planks')
 put(cx,39,11,'bell',facing='north',attachment='ceiling',powered='false')
 for y in range(1,38):
  t=y%16;ring=[(cx-3+i,8) for i in range(6)]+[(cx+3,8+i)for i in range(4)]+[(cx+3-i,12)for i in range(6)]
  x,z=ring[t];put(x,y,z,'stone_brick_slab',type='top',waterlogged='false')
# Re-open all three west portals after tower masonry; tower halls connect to the nave.
for cx in (18,32,46):
 for z in range(5,19):
  for dx in range(-3,4):
   for y in range(1,9-abs(dx)):put(cx+dx,y,z,'air')
  for dx in range(-4,5):put(cx+dx,9-abs(dx),z,RIB)
# Crossing lantern and royal choir/sanctuary.
for y in range(39,53):
 for x,z in [(30,45),(34,45),(30,49),(34,49)]:put(x,y,z,RIB)
for k in range(10):put(32,52+k,47,DARK)
box(27,1,73,37,1,79,'polished_diorite');box(30,2,76,34,2,77,'quartz_bricks');put(32,3,77,'gold_block')
# Furnished nave: clear centre aisle, benches, light standards.
for z in range(21,69,3):
 if 39<=z<=56:continue
 for x in list(range(26,30))+list(range(35,39)):put(x,1,z,'spruce_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
for z in range(18,72,7):
 for x in (22,42):
  box(x,1,z,x,3,z,'stone_brick_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false');put(x,4,z,'lantern',hanging='false',waterlogged='false')
# NBT structure and the runtime palette format used by builders.
palette=[];lookup={};blocks=[]
for pos,(v,p) in sorted(b.items()):
 key=(v,tuple(sorted(p.items())))
 if key not in lookup:lookup[key]=len(palette);palette.append({'name':v,'properties':p})
 blocks.append(list(pos)+[lookup[key]])
data={'size':[W,H,D],'palette':palette,'blocks':blocks}
(R/'era_templates/cologne_cathedral.json').write_text(json.dumps(data,separators=(',',':')))
npal=[n.Compound({'Name':n.String(p['name']),**({'Properties':n.Compound({k:n.String(v)for k,v in p['properties'].items()})}if p['properties']else{})})for p in palette]
nb=n.Compound({'DataVersion':n.Int(4671),'size':n.List[n.Int]([W,H,D]),'palette':n.List[n.Compound](npal),'blocks':n.List[n.Compound]([n.Compound({'pos':n.List[n.Int](p[:3]),'state':n.Int(p[3])})for p in blocks]),'entities':n.List[n.Compound]()})
dest=R/'structure/gothic/cologne_cathedral.nbt';dest.parent.mkdir(parents=True,exist_ok=True);n.File(nb,gzipped=True).save(dest)
print('Cathedral',len(blocks),'blocks,',len(palette),'states')
# Textured isometric projections from the actual exported block data.
archive=zipfile.ZipFile(Path.home()/'.gradle/caches/fabric-loom/1.21.11/minecraft-client.jar')
cache={}
def texture(v):
 aliases={'polished_andesite':'polished_andesite','deepslate_tile_slab':'deepslate_tiles','stone_brick_wall':'stone_bricks','spruce_stairs':'spruce_planks','stone_brick_slab':'stone_bricks','bell':'gold_block','lantern':'gold_block','quartz_bricks':'quartz_bricks'}
 if v not in cache:
  try:
   im=Image.open(io.BytesIO(archive.read('assets/minecraft/textures/block/'+aliases.get(v,v)+'.png'))).convert('RGB');im=im.crop((0,0,im.width,im.width))
  except KeyError:im=Image.new('RGB',(16,16),(118,116,110))
  cache[v]=im.resize((16,16),Image.Resampling.NEAREST)
 return cache[v]
def render(back=False):
 solids={(W-1-x if not back else x,y,D-1-z if not back else z):v[0].split(':')[1]for(x,y,z),v in b.items()if v[0]!='minecraft:air'}
 scale=9;im=Image.new('RGB',(1900,1750),(201,216,220));ox=950;oy=950
 def pt(x,y,z):return(ox+(x-z)*scale,oy+(x+z)*scale*.5-y*scale)
 for(x,y,z),v in sorted(solids.items(),key=lambda a:(a[0][0]+a[0][2],a[0][1])):
  faces=[]
  if(x+1,y,z)not in solids:faces.append(([pt(x+1,y+1,z),pt(x+1,y+1,z+1),pt(x+1,y,z+1),pt(x+1,y,z)],.7))
  if(x,y,z+1)not in solids:faces.append(([pt(x,y+1,z+1),pt(x+1,y+1,z+1),pt(x+1,y,z+1),pt(x,y,z+1)],.86))
  if(x,y+1,z)not in solids:faces.append(([pt(x,y+1,z),pt(x+1,y+1,z),pt(x+1,y+1,z+1),pt(x,y+1,z+1)],1.02))
  for vv,light in faces:
   a,bb,cc,dd=vv;ux=(bb[0]-a[0])/16;uy=(bb[1]-a[1])/16;vx=(dd[0]-a[0])/16;vy=(dd[1]-a[1])/16;det=ux*vy-uy*vx
   if not det:continue
   left=math.floor(min(p[0]for p in vv));top=math.floor(min(p[1]for p in vv));right=math.ceil(max(p[0]for p in vv));bottom=math.ceil(max(p[1]for p in vv));dx=left-a[0];dy=top-a[1]
   patch=ImageEnhance.Brightness(texture(v)).enhance(light).transform((right-left,bottom-top),Image.Transform.AFFINE,(vy/det,-vx/det,(vy*dx-vx*dy)/det,-uy/det,ux/det,(-uy*dx+ux*dy)/det),Image.Resampling.NEAREST)
   mask=Image.new('L',patch.size);ImageDraw.Draw(mask).polygon([(p[0]-left,p[1]-top)for p in vv],fill=255);im.paste(patch,(left,top),mask)
 out=ROOT/'preview/cathedral';out.mkdir(exist_ok=True);im.save(out/('cologne-rear.png'if back else'cologne-front.png'))
render();render(True)

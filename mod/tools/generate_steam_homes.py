"""Original furnished steam-era houses, JSON + Minecraft structure NBT + textured preview."""
from pathlib import Path
import json,math,zipfile,io
import nbtlib as n
from PIL import Image,ImageDraw,ImageFont,ImageEnhance
ROOT=Path(__file__).resolve().parents[1];R=ROOT/'src/main/resources/data/halloween';OUT=ROOT/'preview/steam_homes';OUT.mkdir(parents=True,exist_ok=True)
JAR=Path.home()/'.gradle/caches/fabric-loom/1.21.11/minecraft-client.jar'
texzip=zipfile.ZipFile(JAR)
styles=[('steam_townhouse',13,13,2,'stone_bricks','polished_deepslate'),('steam_villa',17,15,2,'smooth_sandstone','quartz_bricks'),('steam_apartments',17,17,9,'light_gray_concrete','deepslate_bricks')]
fontpath='/usr/share/fonts/TTF/DejaVuSans.ttf'
font=ImageFont.truetype(fontpath,19) if Path(fontpath).exists() else ImageFont.load_default()
for name,W,D,F,wall,trim in styles:
 b={};H=F*4+(W//2+3 if F<3 else 6)
 def put(x,y,z,v,**props):
  if 0<=x<W and 0<=z<D and 0<=y<H:b[x,y,z]=('minecraft:'+v,props)
 def box(x0,y0,z0,x1,y1,z1,v,**props):
  for x in range(x0,x1+1):
   for y in range(y0,y1+1):
    for z in range(z0,z1+1):put(x,y,z,v,**props)
 box(0,0,0,W-1,H-1,D-1,'air');box(0,0,0,W-1,0,D-1,'stone_bricks')
 for f in range(F):
  y=f*4+1
  box(1,y-1,2,W-2,y-1,D-2,'spruce_planks')
  for x in range(1,W-1):
   for z in range(2,D-1):
    if x in (1,W-2) or z in (2,D-2):
     for k in range(4):put(x,y+k,z,trim if k==3 or x in (1,W-2) and z in (2,D-2) else wall)
  for x in range(3,W-3,3):
   for z in (2,D-2):
    for k in (1,2):put(x,y+k,z,'glass');put(x+1,y+k,z,'glass')
    for dx in (0,1):put(x+dx,y,z,'polished_andesite')
  for z in range(4,D-3,3):
   for x in (1,W-2):
    for k in (1,2):put(x,y+k,z,'glass')
  # Stairs in the central hall: four rises, solid full-block supports, clear headroom.
  if f<F-1:
   for k in range(4):
    for lane in (W//2-1,W//2):
     for sy in range(y-1,y+k):put(lane,sy,5+k,'stone_bricks')
     put(lane,y+k,5+k,'stone_brick_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
     for head in (1,2,3):put(lane,y+k+head,5+k,'air')
   for lane in (W//2-1,W//2):put(lane,y+3,9,'spruce_planks')
  for x in (3,W-4):
   put(x,y,4,'red_bed',facing='south',part='foot',occupied='false');put(x,y,5,'red_bed',facing='south',part='head',occupied='false')
   put(x,y,D-4,'barrel',facing='up',open='false');put(x,y+1,D-4,'bookshelf')
  put(W-4,y,D-6,'spruce_stairs',facing='west',half='bottom',shape='straight',waterlogged='false')
  put(3,y,D-6,'crafting_table');put(W//2+(2 if F<3 else 0),y+(1 if F<3 else 2),3,'lantern',hanging='false',waterlogged='false');put(W//2+(2 if F<3 else 0),y+(0 if F<3 else 1),3,'stone_brick_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
 # Re-open the stairwell after upper floors were laid, preserving each tread and landing.
 for f in range(F-1):
  y=f*4+1
  for k in range(4):
   for lane in (W//2-1,W//2):
    put(lane,y+k,5+k,'stone_brick_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
    for head in (1,2,3) if F<3 else (1,2):put(lane,y+k+head,5+k,'air')
 # Front balcony with stone cornice and iron balustrade.
 for x in range(3,W-3):
  for z in (0,1):put(x,4,z,'stone_brick_slab',type='top',waterlogged='false')
  put(x,5,0,'iron_bars',north='false',south='false',east='true',west='true',waterlogged='false')
 for x in (2,W-3):
  for y in (1,2,3):put(x,y,1,'polished_andesite')
 # Door, two clear entrance blocks, front steps and porch.
 cx=W//2
 for half,y in [('lower',1),('upper',2)]:put(cx,y,2,'spruce_door',facing='south',half=half,hinge='left',open='false',powered='false')
 for z in (0,1):put(cx,0,z,'stone_bricks');put(cx,1,z,'air');put(cx,2,z,'air')
 # Layered pitched wooden roof on homes; nine-storey block gets a parapet and service roof.
 roofy=F*4+1
 if F<3:
  for layer in range(W//2+1):
   for z in range(1,D):
    for x,face in [(layer,'east'),(W-1-layer,'west')]:
     if layer==W-1-layer:put(x,roofy+layer,z,'dark_oak_slab',type='bottom',waterlogged='false')
     else:put(x,roofy+layer,z,'dark_oak_stairs',facing=face,half='bottom',shape='straight',waterlogged='false')
   if layer<=3:
    for x in range(layer+1,W-layer-1):
     for z in (2,D-2):put(x,roofy+layer,z,wall)
 else:
  box(1,roofy-1,2,W-2,roofy-1,D-2,'smooth_stone')
  for x in range(1,W-1):
   for z in (2,D-2):put(x,roofy,z,'stone_brick_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
 # III-era architectural identities, keeping the same IDs for existing saves.
 if name=='steam_townhouse':
  # Lancet window tips and dark carved surrounds; a pointed central gable.
  for f in range(F):
   y=f*4+1
   for x in (3,6,9):
    for z in (2,D-2):
     put(x,y+1,z,'light_blue_stained_glass');put(x+1,y+1,z,'light_blue_stained_glass')
     put(x,y+2,z,'polished_deepslate');put(x+1,y+2,z,'light_blue_stained_glass')
     put(x+1,y+3,z,'polished_deepslate')
  for z in (2,D-2):
   for x,y in [(W//2,11),(W//2-1,12),(W//2+1,12),(W//2,13)]:put(x,y,z,'light_blue_stained_glass')
  for x in (1,W-2):
   for y in range(9,12):put(x,y,2,'deepslate_brick_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
   put(x,12,2,'polished_deepslate')
 if name=='steam_villa':
  # Classical stone portico, entablature and open-roof atrium.
  for x in range(W):
   for z in range(D):
    for y in range(9,H):put(x,y,z,'air')
  box(1,9,2,W-2,9,D-2,'smooth_quartz')
  for x in range(6,11):
   for z in range(5,10):put(x,9,z,'air')
  for x in (3,6,10,13):
   put(x,1,0,'chiseled_quartz_block')
   for y in range(2,9):put(x,y,0,'quartz_pillar',axis='y')
   put(x,8,0,'chiseled_quartz_block')
  for x in range(2,W-2):
   for z in (0,1):put(x,9,z,'quartz_bricks');put(x,10,z,'smooth_quartz_slab',type='bottom',waterlogged='false')
  for step in range(6):
   for x in range(2+step,W-2-step):put(x,10+step,0,'smooth_sandstone')
  for x in range(2,W-2):
   for z in (2,D-2):put(x,10,z,'smooth_quartz_slab',type='bottom',waterlogged='false')
 # Keep both entrance halves after the facade glazing pass.
 for half,y in [('lower',1),('upper',2)]:put(cx,y,2,'spruce_door',facing='south',half=half,hinge='left',open='false',powered='false')
 # Chimney and courtyard greenery.
 for y in range(F*4-1,min(H,F*4+5)):put(W-4,y,D-4,'bricks')
 for x in (2,W-3):put(x,1,0,'flowering_azalea_leaves',persistent='true',distance='1',waterlogged='false')
 # Include all changed cells and explicit air; palettes are reusable by runtime construction.
 palette=[];lookup={};blocks=[]
 for pos,(v,p) in sorted(b.items()):
  key=(v,tuple(sorted(p.items())))
  if key not in lookup:lookup[key]=len(palette);palette.append({'Name':v,**({'Properties':p} if p else {})})
  blocks.append({'pos':list(pos),'state':lookup[key]})
 # Dynamic height includes the full roof (no cropped roofs).
 size=[W,max(p[1] for p in b)+1,D];data={'size':size,'palette':[{'name':v['Name'],'properties':v.get('Properties',{})} for v in palette],'blocks':[v['pos']+[v['state']] for v in blocks]}
 (R/'era_templates'/f'{name}.json').write_text(json.dumps(data,separators=(',',':')))
 npal=[n.Compound({'Name':n.String(v['Name']),**({'Properties':n.Compound({k:n.String(t) for k,t in v['Properties'].items()})} if 'Properties'in v else {})}) for v in palette]
 nb=n.Compound({'DataVersion':n.Int(4671),'size':n.List[n.Int](size),'palette':n.List[n.Compound](npal),'blocks':n.List[n.Compound]([n.Compound({'pos':n.List[n.Int](v['pos']),'state':n.Int(v['state'])}) for v in blocks]),'entities':n.List[n.Compound]()})
 dest=R/'structure/steam'/f'{name}.nbt';dest.parent.mkdir(parents=True,exist_ok=True);n.File(nb,gzipped=True).save(dest)
 # Isometric texture projection: real vanilla block textures, affine-mapped per visible voxel face.
 solids={(W-1-p[0],p[1],D-1-p[2]):v[0].split(':')[1] for p,v in b.items() if v[0]!='minecraft:air'}
 scale=(24 if W==13 else 20) if F<3 else 18; iw=1100;ih=780 if F<3 else 1120;ox=iw//2;oy=ih-100-(W+D)*scale*.5
 def point(x,y,z):return (ox+(x-z)*scale,oy+(x+z)*scale*.5-y*scale)
 im=Image.new('RGB',(iw,ih),(203,219,219))
 def texture(v):
  aliases={'smooth_quartz':'quartz_block_side','smooth_quartz_slab':'quartz_block_top','dark_oak_slab':'dark_oak_planks','smooth_sandstone':'sandstone_top','polished_deepslate':'polished_deepslate','deepslate_brick_wall':'deepslate_bricks','spruce_door':'spruce_door_bottom','lantern':'iron_block','barrel':'barrel_side','red_bed':'red_wool','stone_brick_wall':'stone_bricks','dark_oak_stairs':'dark_oak_planks','stone_brick_stairs':'stone_bricks','spruce_stairs':'spruce_planks','flowering_azalea_leaves':'flowering_azalea_leaves'}
  try:
   t=Image.open(io.BytesIO(texzip.read('assets/minecraft/textures/block/'+aliases.get(v,v)+'.png'))).convert('RGB');return t.crop((0,0,t.width,t.width)).resize((16,16),Image.Resampling.NEAREST)
  except KeyError:return Image.new('RGB',(16,16),(115,107,95))
 cache={v:texture(v) for v in set(solids.values())}
 for (x,y,z),v in sorted(solids.items(),key=lambda a:(sum((a[0][0],a[0][2])),a[0][1])):
  faces=[]
  if (x+1,y,z)not in solids:faces.append(([point(x+1,y+1,z),point(x+1,y+1,z+1),point(x+1,y,z+1),point(x+1,y,z)],.72))
  if (x,y,z+1)not in solids:faces.append(([point(x,y+1,z+1),point(x+1,y+1,z+1),point(x+1,y,z+1),point(x,y,z+1)],.9))
  if (x,y+1,z)not in solids:faces.append(([point(x,y+1,z),point(x+1,y+1,z),point(x+1,y+1,z+1),point(x,y+1,z+1)],1.05))
  for verts,light in faces:
   a,bb,cc,dd=verts;ux=(bb[0]-a[0])/16;uy=(bb[1]-a[1])/16;vx=(dd[0]-a[0])/16;vy=(dd[1]-a[1])/16;det=ux*vy-uy*vx
   if not det:continue
   left=math.floor(min(q[0]for q in verts));top=math.floor(min(q[1]for q in verts));right=math.ceil(max(q[0]for q in verts));bottom=math.ceil(max(q[1]for q in verts));dx=left-a[0];dy=top-a[1]
   patch=ImageEnhance.Brightness(cache[v]).enhance(light).transform((right-left,bottom-top),Image.Transform.AFFINE,(vy/det,-vx/det,(vy*dx-vx*dy)/det,-uy/det,ux/det,(-uy*dx+ux*dy)/det),Image.Resampling.NEAREST)
   mask=Image.new('L',patch.size);ImageDraw.Draw(mask).polygon([(q[0]-left,q[1]-top)for q in verts],fill=255);im.paste(patch,(left,top),mask)
 ImageDraw.Draw(im).text((30,25),{'steam_townhouse':'III / GOTHIC TOWNHOUSE','steam_villa':'III / ROMAN VILLA','steam_apartments':'IV / APARTMENTS'}[name],font=font,fill=(35,48,55));im.save(OUT/f'{name}.png')
 print(name,size,len(blocks))

"""Original vanilla-textured rolling stock, terminal station and separate rail foundry."""
from pathlib import Path
import json,math,zipfile,io
import nbtlib as n
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1];DATA=ROOT/'src/main/resources/data/halloween';ASSETS=ROOT/'src/main/resources/assets/halloween';OUT=ROOT/'preview/railway';OUT.mkdir(parents=True,exist_ok=True)
(DATA/'rail_models').mkdir(parents=True,exist_ok=True)
for kind in ('locomotive','coach'):
 p=[]
 def box(block,x0,y0,z0,x1,y1,z1,solid=True):p.append(dict(block=('minecraft:'+block if ':' not in block else block),box=[x0,y0,z0,x1,y1,z1],solid=solid))
 def floor(x0,x1,z0,z1,y,material):
  for x in range(x0,x1):
   for z in range(z0,z1):box(material,x,y,z,x+1,y+.25,z+1)
 floor(-1,1,-7,7,1.25,'dark_oak_planks')
 for z in range(-7,7):
  box('dark_oak_planks',-1.5,1.25,z,-1,1.5,z+1);box('dark_oak_planks',1,1.25,z,1.5,1.5,z+1)
 for side in (-1,1):
  box('polished_blackstone',side*1.5-.15,.85,-6.8,side*1.5+.15,1.45,6.8)
  for z in (-4,-1,2,4.8):
   for iy in range(8):
    y=-.6+(iy+.5)*.15;half=math.sqrt(max(0,.36-y*y));box('blackstone',side*.86-.13,.72+y-.075,z-half,side*.86+.13,.72+y+.075,z+half,False)
   box('iron_block',side*.86-.16,.56,z-.16,side*.86+.16,.88,z+.16,False)
 for z in (-7,7):
  box('red_terracotta',-1.75,1.05,z-.13,1.75,1.4,z+.13)
  for x in (-1.1,1.1):box('polished_blackstone',x-.20,.83,z-.38,x+.20,1.15,z+.38,False)
  box('iron_block',-.18,.80,z-.58,.18,1.0,z+.58,False)
 if kind=='coach':
  for x in (-1.75,1.5):
   box('dark_oak_planks',x,1.5,-6.7,x+.25,2.25,6.7)
   box('red_terracotta',x,3.5,-6.7,x+.25,4.15,6.7)
   box('gold_block',x-.015,2.15,-6.7,x+.265,2.22,6.7,False)
   for z in range(-6,7,2):
    box('stripped_dark_oak_wood',x,2.25,z-.1,x+.25,3.5,z+.1)
    if z<6:box('light_blue_stained_glass',x+.08,2.28,z+.12,x+.17,3.48,z+1.88)
   for z in range(-4,5,2):
    sx=1 if x>0 else -1
    box('red_wool',sx*1.1-.42,1.5,z-.65,sx*1.1+.42,1.88,z+.5)
    box('dark_oak_planks',sx*1.1-.45,1.88,z+.40,sx*1.1+.45,2.65,z+.57)
    box('red_wool',sx*1.1-.4,1.88,z+.35,sx*1.1+.4,2.57,z+.42,False)
    box('oak_planks',sx*1.1-.42,3.55,z-.65,sx*1.1+.42,3.65,z+.5,False)
  for z in (-6.7,6.45):
   for xa,xb in ((-1.75,-.55),(.55,1.75)):box('dark_oak_planks',xa,1.5,z,xb,4.15,z+.25)
   box('dark_oak_planks',-.55,3.6,z,.55,4.15,z+.25)
  for x in range(-3,4):
   xx=x*.5;h=4.15+(.6 if abs(x)<2 else .35 if abs(x)==2 else 0)
   box('polished_deepslate',xx-.25,h,-6.95,xx+.25,h+.18,6.95)
  for z in (-5,-1,3):box('glowstone',-.18,3.91,z-.18,.18,4.05,z+.18,False)
  for z in (-7.7,7):
   box('dark_oak_planks',-.55,1.25,z,.55,1.5,z+.7)
   for x in (-.65,.55):box('black_wool',x,1.5,z,x+.1,3.6,z+.7)
   box('black_wool',-.65,3.6,z,.65,3.72,z+.7)
 else:
  # Stepped circular boiler, copper bands, working cab and tender bunker.
  for layer in range(10):
   yy=(layer+.5)*.25-1.25;half=math.sqrt(max(0,1.55-yy*yy))
   if half>0:box('polished_blackstone',-half,2.7+yy-.125,-1.8,half,2.7+yy+.125,5.3)
  for z in (-1.6,.5,2.6,4.8):
   box('waxed_cut_copper',-1.15,1.67,z-.07,-1.05,3.55,z+.07,False);box('waxed_cut_copper',1.05,1.67,z-.07,1.15,3.55,z+.07,False)
  box('iron_block',-.8,2.0,5.25,.8,3.3,5.42)
  box('sea_lantern',-.23,3.45,5.28,.23,3.9,5.60,False)
  box('polished_blackstone',-.4,3.7,3.9,.4,5.1,4.7)
  box('polished_deepslate',-.58,5.0,3.72,.58,5.35,4.88)
  box('waxed_cut_copper',-.45,3.7,.2,.45,4.25,1.1)
  for x in (-1.75,1.5):
   box('green_terracotta',x,1.5,-5.7,x+.25,2.5,-2)
   box('green_terracotta',x,3.6,-5.7,x+.25,4.5,-2)
   for z in (-5.7,-2.2):box('waxed_cut_copper',x,2.5,z,x+.25,3.6,z+.2)
   box('light_blue_stained_glass',x+.08,2.5,-5.5,x+.17,3.6,-2.2)
   box('iron_block',x,1.1,-6.3,x+.25,1.28,-5.7)
  box('green_terracotta',-1.75,1.5,-2.25,1.75,2.3,-2)
  box('light_blue_stained_glass',-.9,2.5,-2.20,.9,3.65,-2.08)
  box('polished_blackstone',-1.95,4.48,-5.9,1.95,4.72,-1.8)
  box('blackstone',-.8,1.5,-6.6,.8,2.4,-5.8)
  box('coal_block',-.75,2.4,-6.55,.75,2.6,-5.85)
  for x in (-1.11,1.01):box('waxed_cut_copper',x,.73,-4.1,x+.10,.9,4.85,False)
  box('magma_block',-.45,1.65,-2.37,.45,2.3,-2.25,False)
 if kind=='locomotive':
  # A visible control chair facing the boiler, not an invisible mount point.
  box('dark_oak_planks',-.46,1.5,-4.85,.46,1.87,-3.95)
  box('red_wool',-.42,1.87,-4.8,.42,1.98,-4.0,False)
  box('dark_oak_planks',-.46,1.85,-4.94,.46,2.73,-4.78)
  box('waxed_cut_copper',-.55,1.6,-3.9,.55,2.3,-3.75)
  box('iron_block',-.08,2.3,-3.95,.08,2.65,-3.80,False)
  revised=[]
  for item in p:
   b=item['box'];side=(b[3]<=-1.45 or b[0]>=1.45) and b[1]>=1.45 and b[4]<=4.5
   if not side or b[5]<=-5.15 or b[2]>=-3.85:revised.append(item);continue
   if b[2]<-5.15:q=dict(item,box=list(b));q['box'][5]=-5.15;revised.append(q)
   if b[5]>-3.85:q=dict(item,box=list(b));q['box'][2]=-3.85;revised.append(q)
  p=revised
 if kind=='coach':
  # Four real side entrances; trim wall/window spans instead of drawing doors over solid walls.
  for lo,hi in [(-6.15,-4.85),(4.85,6.15)]:
   revised=[]
   for item in p:
    b=item['box']
    side=(b[3]<=-1.45 or b[0]>=1.45) and b[1]>=1.45 and b[4]<=4.2
    if not side or b[5]<=lo or b[2]>=hi:revised.append(item);continue
    if b[2]<lo:
     q=dict(item,box=list(b));q['box'][5]=lo;revised.append(q)
    if b[5]>hi:
     q=dict(item,box=list(b));q['box'][2]=hi;revised.append(q)
   p=revised
  for side in (-1,1):
   for z in (-5.5,5.5):
    x=side*1.62
    box('dark_oak_planks',x-.09,1.5,z-.65,x+.09,2.45,z+.65);p[-1]['door']=True
    box('light_blue_stained_glass',x-.075,2.45,z-.5,x+.075,3.5,z+.5);p[-1]['door']=True
    box('dark_oak_planks',x-.09,3.5,z-.65,x+.09,4.05,z+.65);p[-1]['door']=True
    box('gold_block',x-.12,2.2,z+.38,x+.12,2.3,z+.53,False);p[-1]['door']=True
    for level in range(2):
     xa,xb=sorted([side*(1.5+level*.32),side*(1.82+level*.32)])
     box('iron_block',xa,1.24-level*.25,z-.65,xb,1.36-level*.25,z+.65)
 (DATA/'rail_models'/f'{kind}.json').write_text(json.dumps(p,separators=(',',':')))
# Full furnished buildings, in both runtime palette and vanilla structure NBT.
def export(name,W,H,D,b):
 palette=[];lookup={};blocks=[];nb=[]
 for (x,y,z),(block,props) in b.items():
  key=(block,tuple(sorted(props.items())))
  if key not in lookup:lookup[key]=len(palette);palette.append(dict(name=block,properties=props))
  i=lookup[key];blocks.append([x,y,z,i]);nb.append(n.Compound(pos=n.List[n.Int]([x,y,z]),state=n.Int(i)))
 (DATA/'era_templates'/f'{name}.json').write_text(json.dumps(dict(size=[W,H,D],palette=palette,blocks=blocks),separators=(',',':')))
 folder=DATA/'structure';folder.mkdir(exist_ok=True)
 n.File(dict(DataVersion=n.Int(4671),size=n.List[n.Int]([W,H,D]),palette=n.List[n.Compound]([n.Compound(Name=n.String(p['name']),**({'Properties':n.Compound({k:n.String(v) for k,v in p['properties'].items()})} if p['properties'] else {})) for p in palette]),blocks=n.List[n.Compound](nb),entities=n.List[n.Compound]([]))).save(folder/f'{name}.nbt',gzipped=True)
for name,W,D,H in [('railway_station',43,65,29),('rail_foundry',19,27,14)]:
 b={}
 def put(x,y,z,block,**props):
  if 0<=x<W and 0<=z<D and 0<=y<H:b[x,y,z]=(block if ':' in block else 'minecraft:'+block,props)
 def box(x0,y0,z0,x1,y1,z1,block,**props):
  for x in range(x0,x1+1):
   for y in range(y0,y1+1):
    for z in range(z0,z1+1):put(x,y,z,block,**props)
 box(0,0,0,W-1,H-1,D-1,'air');box(0,0,0,W-1,0,D-1,'stone_bricks')
 if name=='railway_station':
  for x0,x1 in [(3,5),(11,13),(19,22)]:
   box(x0,1,1,x1,1,62,'stone_bricks');box(x0,2,1,x1,2,62,'smooth_stone')
   for z in range(3,62,2):put(x0,2,z,'polished_andesite');put(x1,2,z,'polished_andesite')
  for x in (8,16):
   for z in range(D):put(x,0,z,'gravel')
  for z in range(5,61,8):
   for x in (3,22):
    box(x,3,z,x,8,z,'polished_deepslate');put(x,8,z,'lantern',hanging='false',waterlogged='false')
  for x in range(3,23):
   dy=8+round(5.5*math.sqrt(max(0,1-((x-12.5)/10)**2)))
   for z in range(4,61):put(x,dy,z,'polished_deepslate' if z%8==5 else 'light_blue_stained_glass')
  box(23,1,4,23,1,45,'stone_bricks');box(23,2,4,23,2,45,'smooth_stone')
  # Main concourse: tall arched windows, galleries, ticket office and waiting salon.
  box(24,1,4,41,2,45,'smooth_sandstone');box(24,3,4,41,12,45,'bricks');box(25,3,5,40,11,44,'air')
  for y in (2,7,12):
   for x in range(24,42):
    for z in range(4,46):
     if x in (24,41) or z in (4,45):put(x,y,z,'cut_sandstone')
  for z in range(7,45,5):
   for x in (24,41):
    for y in range(4,11):put(x,y,z,'glass');put(x,y,z+1,'glass')
    put(x,10,z,'sandstone_stairs',facing='east' if x==24 else 'west',half='top',shape='straight',waterlogged='false')
  for x in range(24,42):
   roof=13+min(x-24,41-x)//2
   for z in range(3,47):put(x,roof,z,'deepslate_tile_slab',type='bottom',waterlogged='false')
  for z in (5,15,25,35,44):
   for y in range(3,13):put(23,y,z,'smooth_sandstone');put(42,y,z,'smooth_sandstone')
  for z in (12,30):
   for y in range(3,7):put(24,y,z,'air');put(24,y,z+1,'air')
   box(21,2,z,24,2,z+1,'smooth_stone')
  for z in range(8,43,6):
   put(26,3,z,'halloween:gothic_chair',facing='east');put(39,3,z,'halloween:gothic_chair',facing='west');put(32,10,z,'lantern',hanging='true',waterlogged='false');put(32,11,z,'iron_bars')
  box(30,3,37,38,3,37,'dark_oak_planks');box(30,4,38,38,5,38,'iron_bars')
  for x in (31,34,37):put(x,3,39,'lectern');put(x,3,41,'barrel',facing='up',open='false')
  for z in range(7,62,8):
   for x in (5,19):put(x,3,z,'halloween:gothic_chair',facing='south');put(x,3,z+1,'halloween:gothic_chair',facing='north')
  # Two staircases to a bridge above both tracks; track clearance remains at least six blocks.
  for step in range(6):
   for lane in (0,1):put(19+lane,3+step,49+step,'stone_brick_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
  box(3,8,55,22,8,57,'smooth_sandstone');box(3,9,55,22,9,55,'iron_bars');box(3,9,57,22,9,57,'iron_bars')
  # Matching stairs on the opposite platform and on the island platform.
  for base in (3,11):
   for step in range(6):
    for lane in (0,1):put(base+lane,3+step,49+step,'stone_brick_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
  # Platform edge barriers with interlocked boarding gates.
  for x in (5,11,13,19):
   for z in range(2,63):
    gate=11<=z<=14 or 22<=z<=29 or 35<=z<=42 or 50<=z<=53
    if gate:put(x,3,z,'dark_oak_fence_gate',facing='east',open='false',powered='false',in_wall='false')
    else:put(x,3,z,'iron_bars',north='true',south='true',east='false',west='false',waterlogged='false')
  # Keep all three staircase mouths and the bridge exits unobstructed.
  for x0 in (3,11,19):
   box(x0,3,48,x0+1,5,48,'air')
   for step in range(6):
    box(x0,4+step,49+step,x0+1,6+step,49+step,'air')
   box(x0,9,55,x0+1,11,57,'air')
   for step in range(6):
    for lane in (0,1):put(x0+lane,3+step,49+step,'stone_brick_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')

  # Clock tower, entrance portico and crenellated cornice.
  box(29,13,4,36,20,11,'sandstone');box(30,14,5,35,19,10,'air')
  for x in range(29,37):
   for z in range(4,12):put(x,21,z,'cut_sandstone');put(x,22,z,'deepslate_tile_slab',type='bottom',waterlogged='false')
  for x in range(30,36):
   for y in range(15,20):put(x,y,3,'white_concrete')
  for y in range(16,20):put(33,y,3,'black_concrete')
  for x in range(30,34):put(x,17,3,'black_concrete')
  for layer in range(4):
   for x in range(28+layer,38-layer):
    for z in range(3+layer,13-layer):put(x,22+layer,z,'deepslate_tile_slab',type='bottom',waterlogged='false')
  for x in range(30,36):
   for y in range(15,21):
    d=(x-32.5)**2+(y-17.5)**2
    put(x,y,3,'polished_blackstone' if d>6 else 'smooth_quartz')
  for y in range(18,21):put(33,y,3,'black_concrete')
  for x in range(30,34):put(x,17,3,'black_concrete')
  for x in (26,39):box(x,3,0,x,9,3,'smooth_sandstone')
  box(25,10,0,40,10,3,'cut_sandstone')
  for layer in range(5):box(25+layer,11+layer,0,40-layer,11+layer,0,'smooth_sandstone')
  for z in (18,28):box(31,16,z,34,16,z+3,'glass')
 else:
  box(1,1,1,17,7,25,'bricks');box(2,1,2,16,6,24,'air')
  for x in range(W):
   for z in range(D):
    if x in (0,W-1) or z in (0,D-1):put(x,0,z,'polished_andesite')
  for z in range(4,25,4):
   for x in (1,17):box(x,3,z,x,5,z+1,'glass')
  for z in range(D):
   for x in range(W):put(x,8+min(x,W-1-x)//2,z,'deepslate_tile_slab',type='bottom',waterlogged='false')
  box(8,1,1,10,4,1,'air')
  for z in (8,16):
   box(3,1,z,5,3,z+2,'polished_blackstone_bricks');put(4,1,z-1,'blast_furnace',facing='north',lit='true');put(4,2,z-1,'magma_block')
   for y in range(4,14):put(4,y,z+1,'bricks')
   put(8,1,z,'anvil',facing='north');put(8,3,z,'iron_block');put(8,4,z,'chain',axis='y',waterlogged='false')
   for x in range(7,11):put(x,6,z,'dark_oak_planks')
  for x,z in [(12,4),(14,4),(12,20),(14,20)]:put(x,1,z,'barrel',facing='up',open='false')
  for z in (4,12,20):put(9,6,z,'lantern',hanging='true',waterlogged='false')
  put(15,1,12,'smithing_table');put(15,1,14,'grindstone')
 export(name,W,H,D,b)
# Item/block resource assets reuse original vanilla material textures.
for name,texture in [('iron_sleeper','iron_block'),('railway_track','iron_block')]:
 (ASSETS/'blockstates').mkdir(exist_ok=True);(ASSETS/'models/block').mkdir(parents=True,exist_ok=True)
 model=dict(textures={'all':'minecraft:block/'+texture,'particle':'minecraft:block/'+texture},elements=[dict(**{'from':[0,0,0],'to':[16,2,16]},faces={f:dict(texture='#all') for f in ['up','down','north','south','east','west']})])
 (ASSETS/'models/block'/f'{name}.json').write_text(json.dumps(model));(ASSETS/'blockstates'/f'{name}.json').write_text(json.dumps({'variants':{'':{'model':'halloween:block/'+name}}}))
 (ASSETS/'models/item'/f'{name}.json').write_text(json.dumps({'parent':'halloween:block/'+name}))
for name,texture in [('rail_profile','iron_ingot'),('rail_hammer','iron_axe'),('steam_train','minecart')]:
 (ASSETS/'models/item'/f'{name}.json').write_text(json.dumps({'parent':'minecraft:item/handheld' if 'hammer' in name else 'minecraft:item/generated','textures':{'layer0':'minecraft:item/'+texture}}))
for name in ('railway_track','iron_sleeper','rail_profile','rail_hammer','steam_train'):(ASSETS/'items'/f'{name}.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':'halloween:item/'+name}}))
for language,labels in [('ru_ru',{'block.halloween.railway_track':'Широкая железная дорога','block.halloween.iron_sleeper':'Кованая железная шпала','item.halloween.rail_profile':'Рельсовый профиль','item.halloween.rail_hammer':'Рельсовый молот','item.halloween.steam_train':'Паровоз с пассажирскими вагонами','entity.halloween.rail_vehicle':'Пассажирский состав'}),('en_us',{'block.halloween.railway_track':'Full-size railway track','block.halloween.iron_sleeper':'Forged iron sleeper','item.halloween.rail_profile':'Rail profile','item.halloween.rail_hammer':'Rail hammer','item.halloween.steam_train':'Steam passenger train','entity.halloween.rail_vehicle':'Passenger train'})]:
 path=ASSETS/'lang'/f'{language}.json';data=json.loads(path.read_text());data.update(labels);path.write_text(json.dumps(data,ensure_ascii=False,indent=2))
print('Generated original locomotive, furnished coach, station, foundry, structure files and assets')
# Original three-dimensional hammer and I-beam profile; no borrowed tool icon.
def element(lo,hi,tex):return {'from':lo,'to':hi,'faces':{f:{'texture':'#'+tex} for f in ['up','down','north','south','east','west']}}
hammer={'textures':{'particle':'minecraft:block/iron_block','head':'minecraft:block/iron_block','shaft':'minecraft:block/stripped_dark_oak_log','band':'minecraft:block/copper_block'},'elements':[element([7,0,7],[9,14,9],'shaft'),element([2,12,5],[14,17,11],'head'),element([6,11.5,6],[10,13,10],'band')],'display':{'gui':{'rotation':[25,135,0],'scale':[.8,.8,.8]},'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1,0,0],'scale':[.7,.7,.7]},'thirdperson_righthand':{'rotation':[0,-90,45],'translation':[0,2,0],'scale':[.6,.6,.6]}}}
(ASSETS/'models/item/rail_hammer.json').write_text(json.dumps(hammer))
profile={'textures':{'particle':'minecraft:block/iron_block','iron':'minecraft:block/iron_block'},'elements':[element([1,1,5],[15,3,11],'iron'),element([1,3,7],[15,7,9],'iron'),element([1,7,5],[15,9,11],'iron')],'display':{'gui':{'rotation':[25,135,0],'scale':[.85,.85,.85]}}}
(ASSETS/'models/item/rail_profile.json').write_text(json.dumps(profile))
parts=json.loads((DATA/'rail_models/locomotive.json').read_text());textures={'particle':'minecraft:block/iron_block'};elements=[]
for part in parts:
 name=part['block'].split(':')[-1];texture={'waxed_cut_copper':'cut_copper','stripped_dark_oak_wood':'stripped_dark_oak_log','magma_block':'magma'}.get(name,name);textures[name]='minecraft:block/'+texture
 a=part['box'];elements.append(element([8+a[0],a[1]+2,8+a[2]],[8+a[3],a[4]+2,8+a[5]],name))
(ASSETS/'models/item/steam_train.json').write_text(json.dumps({'textures':textures,'elements':elements,'display':{'gui':{'rotation':[25,140,0],'scale':[.9,.9,.9]}}}))

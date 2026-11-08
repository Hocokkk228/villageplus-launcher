from pathlib import Path
import json, random
import nbtlib as n
from PIL import Image, ImageDraw
R=Path('src/main/resources'); random.seed(65)
A=R/'assets/halloween'; skin=Image.new('RGBA',(64,64)); d=ImageDraw.Draw(skin)
d.rectangle((40,0,47,7),fill='#c99a31');d.rectangle((41,1,46,6),fill='#f4d56a')
for x in range(32,64):
 d.line((x,12,x,16),fill='#bd8928'); d.line((x,10,x,11),fill='#f7cf56')
 if x%4 in (0,1): d.line((x,7,x,10),fill='#ffd85a')
for x in (35,43,51,59):d.point((x,13),fill='#c52844')
for a,b in ((16,21),(22,29),(30,35),(36,43)):
 d.rectangle((a,26,b,37),fill='#632462');d.rectangle((a,27,b,29),fill='#a5273c');d.line((a,32,b,32),fill='#e7bb4d');d.line((a,36,b,36),fill='#341b39')
d.rectangle((24,29,27,34),fill='#92273c'); d.line((25,30,25,34),fill='#ebc457')
d.rectangle((40,39,63,46),fill='#622759');d.line((40,44,63,44),fill='#daa941')
p=A/'textures/entity/villager/profession/king.png';p.parent.mkdir(parents=True,exist_ok=True);skin.save(p);p.with_name('king.png.mcmeta').write_text('{"villager":{"hat":"full"}}')
for lang,name in [('ru_ru','Король'),('en_us','King')]:
 p=A/f'lang/{lang}.json';data=json.loads(p.read_text(encoding='utf8'));data['entity.minecraft.villager.halloween.king']=name;p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
S=n.String; I=n.Int; C=n.Compound; L=n.List
palette=[]; palidx={}; blocks={}
def put(x,y,z,name,props=None,extra=None):
 key=(name,tuple(sorted((props or {}).items())))
 if key not in palidx:
  palidx[key]=len(palette); state=C({'Name':S('minecraft:'+name)})
  if props:state['Properties']=C({k:S(v) for k,v in props.items()})
  palette.append(state)
 entry=C({'pos':L[I]([x,y,z]),'state':I(palidx[key])})
 if extra:entry['nbt']=C(extra)
 blocks[(x,y,z)]=entry
def stone(x,y,z):
 v=(x*7+y*13+z*3)%17
 return 'tuff_bricks' if v==0 else 'cracked_stone_bricks' if v==1 else 'mossy_stone_bricks' if v==2 else 'stone_bricks'
for biome in ('plains','desert','savanna','snowy','taiga'):
 palette.clear();palidx.clear();blocks.clear()
 # Foundation and open space: the entrance faces north towards the village road.
 for x in range(25):
  for z in range(25):
   for y in range(1,21):put(x,y,z,'air')
   put(x,0,z,'polished_andesite' if (x+z)%5==0 else stone(x,0,z))
 # Curtain walls with crenellations, carved footing and narrow arrow slits.
 for x in range(2,23):
  for z in range(2,23):
   if x in (2,22) or z in (2,22):
    for y in range(1,8):put(x,y,z,stone(x,y,z))
    put(x,1,z,'polished_tuff');put(x,7,z,'chiseled_stone_bricks')
    if (x+z)%2==0:put(x,8,z,'stone_brick_wall')
 # The four towers have projected corner buttresses, bands and crenellations.
 for tx,tz in ((2,2),(18,2),(2,18),(18,18)):
  for x in range(tx,tx+5):
   for z in range(tz,tz+5):
    edge=x in (tx,tx+4) or z in (tz,tz+4)
    if edge:
     for y in range(1,14):
      material=stone(x,y,z)
      if y in (1,6,11):material='polished_tuff'
      if x in (tx,tx+4) and z in (tz,tz+4):material='stone_bricks' if y%3 else 'chiseled_stone_bricks'
      put(x,y,z,material)
    for y in (1,7,13):put(x,y,z,'spruce_planks' if y==7 else 'stone_bricks')
  # Corbels project out from the parapet and make the silhouettes less boxy.
  for k in range(5):
   for x,z in ((tx-1,tz+k),(tx+5,tz+k),(tx+k,tz-1),(tx+k,tz+5)):
    put(x,12,z,'stone_brick_stairs',{'facing':'north','half':'top','shape':'straight','waterlogged':'false'})
    put(x,13,z,'stone_brick_slab',{'type':'bottom','waterlogged':'false'})
    if k%2==0:put(x,14,z,'stone_bricks')
  for x in range(tx+1,tx+4):
   for z in range(tz+1,tz+4):put(x,14,z,'dark_oak_planks')
  put(tx+2,15,tz+2,'polished_blackstone_brick_wall')
  # Arrow slits and simple tower interiors with a ladder between floors.
  for y in (4,5,9,10):
   for x,z in ((tx+2,tz),(tx+2,tz+4),(tx,tz+2),(tx+4,tz+2)):put(x,y,z,'air')
  for y in range(2,13):put(tx+1,y,tz+1,'ladder',{'facing':'south','waterlogged':'false'})
  for y in range(2,13):put(tx+1,y,tz,'stone_bricks')
  for y in (1,2,8,9):put(tx+2,y,tz+4 if tz==2 else tz,'air')
  put(tx+3,2,tz+2,'lantern',{'hanging':'false','waterlogged':'false'})
 # Arched entrance, gatehouse trim, side lanterns and a paved approach.
 for x in range(10,15):
  for z in range(0,8):put(x,0,z,'polished_andesite' if (x+z)%3 else 'cobblestone')
 for x in range(11,14):
  for y in range(1,5):put(x,y,2,'air')
 for x in (10,14):
  for y in range(1,7):put(x,y,1,'polished_tuff')
  put(x,4,0,'lantern',{'hanging':'false','waterlogged':'false'})
 for x in range(10,15):put(x,6,1,'chiseled_stone_bricks')
 # Grand hall: timber-framed walls, stained-glass windows and a real pitched roof.
 for x in range(7,18):
  for z in range(7,19):
   wall=x in (7,17) or z in (7,18)
   if wall:
    for y in range(1,9):
     mat='stripped_spruce_log' if x in (7,17) and z in (7,18) else stone(x,y,z)
     if y in (1,6):mat='polished_tuff'
     put(x,y,z,mat)
   else:put(x,0,z,'spruce_planks' if (x+z)%4 else 'dark_oak_planks')
 # Tall leaded purple windows on both side elevations.
 for x in (7,17):
  for z in (10,14,16):
   for y in (3,4,5):put(x,y,z,'purple_stained_glass_pane')
   put(x,2,z,'chiseled_stone_bricks');put(x,6,z,'stone_bricks')
 # Front arch and oak frame.
 for x in range(11,14):
  for y in range(1,5):put(x,y,7,'air')
 for x in (10,14):
  for y in range(1,6):put(x,y,7,'stripped_spruce_log')
 for x in range(10,15):put(x,6,7,'spruce_planks')
 # Sloped dark-oak roof, with triangular gables at the front and rear.
 for z in range(6,20):
  for x in range(6,19):
   y=8+min(x-6,18-x)
   put(x,y,z,'dark_oak_stairs',{'facing':'east' if x<12 else 'west','half':'bottom','shape':'straight','waterlogged':'false'})
 for x in range(7,18):
  height=8+min(x-6,18-x)
  for z in (7,18):
   for y in range(9,height):put(x,y,z,'spruce_planks' if (x+y)%3 else 'stripped_spruce_log')
 for z in range(6,20):put(12,15,z,'dark_oak_slab',{'type':'bottom','waterlogged':'false'})
 # Throne, carpet, braziers, tables and bookshelves inside the hall.
 for z in range(2,17):put(12,1,z,'purple_carpet')
 for x in range(10,15):
  for z in (15,16):put(x,1,z,'red_carpet')
 for x in range(11,14):put(x,1,17,'polished_blackstone_bricks')
 put(12,2,17,'gold_block');put(12,3,17,'red_wool')
 for x in (8,16):
  for z in (10,13):
   put(x,1,z,'bookshelf');put(x,2,z,'bookshelf')
 for x in (9,15):
  for z in (10,14):put(x,1,z,'spruce_fence');put(x,2,z,'lantern',{'hanging':'false','waterlogged':'false'})
 for x in (9,15):
  for z in (11,12):put(x,1,z,'spruce_slab',{'type':'top','waterlogged':'false'})
 # Courtyard landscaping and wall lights.
 for x,z in ((8,4),(16,4),(4,11),(20,11),(4,15),(20,15)):
  put(x,1,z,'cobblestone_wall');put(x,2,z,'lantern',{'hanging':'false','waterlogged':'false'})
 # Four jigsaw connections to native roads.
 for x,z,orientation in ((0,12,'west_up'),(12,0,'north_up'),(12,24,'south_up'),(24,12,'east_up')):
  put(x,1,z,'jigsaw',{'orientation':orientation},{'id':S('minecraft:jigsaw'),'name':S('minecraft:street'),'target':S('minecraft:street'),'pool':S(f'minecraft:village/{biome}/streets'),'final_state':S('minecraft:structure_void'),'joint':S('aligned')})
 for x,z in ((5,11),(19,11),(12,20)):
  put(x,1,z,'jigsaw',{'orientation':'up_north'},{'id':S('minecraft:jigsaw'),'name':S('minecraft:bottom'),'target':S('minecraft:bottom'),'pool':S(f'minecraft:village/{biome}/villagers'),'final_state':S('minecraft:stone_bricks'),'joint':S('rollable')})
 entity=C({'pos':L[n.Double]([12.5,2.0,14.5]),'blockPos':L[I]([12,2,14]),'nbt':C({'id':S('minecraft:villager'),'PersistenceRequired':n.Byte(1),'VillagerData':C({'type':S('minecraft:'+biome),'profession':S('halloween:king'),'level':I(2)}),'Xp':I(1),'Health':n.Float(20.0)})})
 root=n.File({'DataVersion':I(4671),'size':L[I]([25,21,25]),'palette':L[C](palette),'blocks':L[C](list(blocks.values())),'entities':L[C]([entity])},gzipped=True)
 p=R/f'data/halloween/structure/village/{biome}/royal_castle.nbt';p.parent.mkdir(parents=True,exist_ok=True);root.save(p,gzipped=True)
 pool={'elements':[{'element':{'element_type':'minecraft:single_pool_element','location':f'halloween:village/{biome}/royal_castle','processors':'minecraft:empty','projection':'rigid'},'weight':1}],'fallback':'minecraft:empty'}
 p=R/f'data/minecraft/worldgen/template_pool/village/{biome}/town_centers.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(pool,indent=2)+'\n')
print('Generated royal villager and five biome-specific castle town centers')

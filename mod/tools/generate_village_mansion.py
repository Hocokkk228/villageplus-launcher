from pathlib import Path
import nbtlib as n,json
R=Path('src/main/resources');S=n.String;I=n.Int;C=n.Compound;L=n.List
for biome in ('plains','desert','savanna','snowy','taiga'):
 pal=[];ids={};b={}
 def put(x,y,z,name,props=None,extra=None):
  key=(name,tuple(sorted((props or {}).items())))
  if key not in ids:
   st=C({'Name':S('minecraft:'+name)})
   if props:st['Properties']=C({k:S(v) for k,v in props.items()})
   ids[key]=len(pal);pal.append(st)
  v=C({'pos':L[I]([x,y,z]),'state':I(ids[key])})
  if extra:v['nbt']=C(extra)
  b[x,y,z]=v
 stone='cut_sandstone' if biome in ('desert','savanna') else 'stone_bricks'
 for x in range(15):
  for z in range(17):
   for y in range(1,13):put(x,y,z,'air')
   put(x,0,z,stone)
   if 1<=x<=13 and 1<=z<=15:
    put(x,5,z,'spruce_planks')
    if x in (1,13) or z in (1,15):
     for y in range(1,10):put(x,y,z,'stripped_spruce_log' if x in (1,13) and z in (1,15) else stone)
     for y in (1,5):put(x,y,z,'polished_andesite' if biome not in ('desert','savanna') else 'smooth_sandstone')
 # Entrance and paired front/back windows.
 for y in (1,2,3):put(7,y,1,'air')
 for x in (3,5,9,11):
  for z in (1,15):
   for y in (2,3,7,8):put(x,y,z,'glass')
 for z in (4,7,10,13):
  for x in (1,13):
   for y in (2,3,7,8):put(x,y,z,'glass')
 # Pitched overhanging roof.
 for x in range(15):
  for z in range(17):
   y=9+min(x,14-x)
   put(x,y,z,'spruce_stairs',{'facing':'east' if x<7 else 'west','half':'bottom','shape':'straight','waterlogged':'false'})
 for z in range(17):put(7,16,z,'spruce_slab',{'type':'bottom','waterlogged':'false'})
 # Lower hall furnishings and staircase to the upper floor.
 for x,z in ((3,3),(11,3),(3,13),(11,13)):put(x,2,z,'lantern',{'hanging':'false','waterlogged':'false'})
 for z in range(4,9):
  height=z-3
  for y in range(1,height):put(3,y,z,'spruce_planks')
  put(3,height,z,'spruce_stairs',{'facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
  if height>=3:put(3,5,z,'air')
 for x in (10,11,12):put(x,1,12,'bookshelf');put(x,2,12,'bookshelf')
 for z in (4,5,6):put(10,1,z,'spruce_fence');put(10,2,z,'spruce_slab',{'type':'bottom','waterlogged':'false'})
 for z,part in ((11,'foot'),(12,'head')):put(7,6,z,'purple_bed',{'facing':'south','occupied':'false','part':part})
 for x,z in ((5,12),(9,12),(5,5),(9,5)):put(x,6,z,'lantern',{'hanging':'false','waterlogged':'false'})
 put(0,0,8,'jigsaw',{'orientation':'west_up'},{'id':S('minecraft:jigsaw'),'name':S('minecraft:building_entrance'),'target':S('minecraft:building_entrance'),'pool':S(f'minecraft:village/{biome}/streets'),'joint':S('aligned'),'final_state':S('minecraft:stone_bricks')})
 marker=C({'pos':L[n.Double]([7.5,6.,12.5]),'blockPos':L[I]([7,6,12]),'nbt':C({'id':S('minecraft:marker'),'Tags':L[S]([S('halloween_mansion_bed')])})})
 f=n.File({'DataVersion':I(4671),'size':L[I]([15,17,17]),'palette':L[C](pal),'blocks':L[C](list(b.values())),'entities':L[C]([marker])},gzipped=True)
 path=R/f'data/halloween/structure/village/{biome}/houses/mansion.nbt';f.save(path,gzipped=True)
 path=R/f'data/minecraft/worldgen/template_pool/village/{biome}/houses.json';pool=json.loads(path.read_text());pool['elements'].append({'element':{'element_type':'minecraft:single_pool_element','location':f'halloween:village/{biome}/houses/mansion','processors':'minecraft:empty','projection':'rigid'},'weight':2});path.write_text(json.dumps(pool,indent=2))

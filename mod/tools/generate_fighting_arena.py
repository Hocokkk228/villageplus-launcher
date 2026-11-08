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
 # Sand ring, four stepped stands, timber supports and lanterns.
 for x in range(23):
  for z in range(23):
   put(x,0,z,'sand' if 6<=x<=16 and 6<=z<=16 else stone)
   for y in range(1,7):put(x,y,z,'air')
   d=min(x,z,22-x,22-z)
   if d in (1,2,3,4) and (x<6 or x>16 or z<6 or z>16):
    height=5-d
    for y in range(1,height):put(x,y,z,'spruce_planks')
    facing=('east' if x<6 else 'west') if x<6 or x>16 else ('south' if z<6 else 'north')
    put(x,height,z,'spruce_stairs',{'facing':facing,'half':'bottom','shape':'straight','waterlogged':'false'})
   if d==0:
    put(x,1,z,'spruce_fence');put(x,2,z,'spruce_fence')
 for x in (0,22):
  for z in (0,22):
   for y in range(1,5):put(x,y,z,'stripped_spruce_log')
   put(x,5,z,'lantern',{'hanging':'false','waterlogged':'false'})
 # Entrances and walkways to opposite ring corners.
 for z in (10,11,12):
  for x in range(0,7):
   for y in range(1,5):put(x,y,z,'air')
  for x in range(16,23):
   for y in range(1,5):put(x,y,z,'air')
 for x in range(7,16):
  if x%2==0:put(x,1,6,'spruce_fence');put(x,1,16,'spruce_fence')
 put(0,0,11,'jigsaw',{'orientation':'west_up'},{'id':S('minecraft:jigsaw'),'name':S('minecraft:building_entrance'),'target':S('minecraft:building_entrance'),'pool':S(f'minecraft:village/{biome}/streets'),'joint':S('aligned'),'final_state':S('minecraft:stone_bricks')})
 markers=[]
 for x,z,tag in ((11,11,'halloween_arena'),(8,11,'halloween_fight_a'),(14,11,'halloween_fight_b')):
  markers.append(C({'pos':L[n.Double]([x+.5,1.,z+.5]),'blockPos':L[I]([x,1,z]),'nbt':C({'id':S('minecraft:marker'),'Tags':L[S]([S(tag)])})}))
 f=n.File({'DataVersion':I(4671),'size':L[I]([23,7,23]),'palette':L[C](pal),'blocks':L[C](list(b.values())),'entities':L[C](markers)},gzipped=True)
 path=R/f'data/halloween/structure/village/{biome}/houses/fighting_arena.nbt';path.parent.mkdir(parents=True,exist_ok=True);f.save(path,gzipped=True)
 path=R/f'data/minecraft/worldgen/template_pool/village/{biome}/houses.json';pool=json.loads(path.read_text());pool['elements']=[e for e in pool['elements'] if not str(e['element'].get('location','')).endswith('/fighting_arena')];pool['elements'].append({'element':{'element_type':'minecraft:single_pool_element','location':f'halloween:village/{biome}/houses/fighting_arena','processors':'minecraft:empty','projection':'rigid'},'weight':1});path.write_text(json.dumps(pool,indent=2))

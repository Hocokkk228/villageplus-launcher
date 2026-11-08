from pathlib import Path
import nbtlib as n,json
R=Path('src/main/resources');S=n.String;I=n.Int;C=n.Compound;L=n.List
for biome in ('plains','desert','savanna','snowy','taiga'):
 p=R/f'data/halloween/structure/village/{biome}/royal_castle.nbt';f=n.load(p);pal=f['palette'];blocks={tuple(map(int,b['pos'])):b for b in f['blocks']}
 def castleput(x,y,z,name,props=None,extra=None):
  st=C({'Name':S('minecraft:'+name)})
  if props:st['Properties']=C({k:S(v) for k,v in props.items()})
  try:i=pal.index(st)
  except ValueError:i=len(pal);pal.append(st)
  b=C({'pos':L[I]([x,y,z]),'state':I(i)})
  if extra:b['nbt']=C(extra)
  blocks[x,y,z]=b
 # Remove the former above-ground cage; the entrance is now a floor hatch.
 for x in range(5,10):
  for z in range(18,24):
   for y in range(1,6):castleput(x,y,z,'air')
 castleput(7,0,21,'jigsaw',{'orientation':'down_south'}, {'id':S('minecraft:jigsaw'),'name':S('halloween:dungeon'),'target':S('halloween:dungeon'),'pool':S(f'halloween:village/{biome}/dungeon'),'joint':S('aligned'),'final_state':S('minecraft:air')})
 castleput(7,1,20,'stone_bricks');castleput(7,1,21,'ladder',{'facing':'south','waterlogged':'false'})
 # A paved meeting area with a central bell and two modest spectator stands.
 for x in range(17,26):
  for z in range(3,9):castleput(x,0,z,'polished_andesite' if (x+z)%3 else 'stone_bricks')
 for x in (17,25):
  for z in range(4,8):castleput(x,1,z,'spruce_stairs',{'facing':'east' if x==17 else 'west','half':'bottom','shape':'straight','waterlogged':'false'})
 castleput(21,1,4,'bell',{'attachment':'floor','facing':'north','powered':'false'})
 f['blocks']=L[C](list(blocks.values()));f['entities']=L[C]([e for e in f['entities'] if 'halloween_jail' not in [str(t) for t in e['nbt'].get('Tags',[])]])
 f.save(p,gzipped=True)
 # Independent rigid jigsaw piece extends below ground level.
 palette=[];by={};bd={}
 def put(x,y,z,name,props=None,extra=None):
  key=(name,tuple(sorted((props or {}).items())))
  if key not in by:
   st=C({'Name':S('minecraft:'+name)})
   if props:st['Properties']=C({k:S(v) for k,v in props.items()})
   by[key]=len(palette);palette.append(st)
  b=C({'pos':L[I]([x,y,z]),'state':I(by[key])})
  if extra:b['nbt']=C(extra)
  bd[x,y,z]=b
 material='cut_sandstone' if biome in ('desert','savanna') else 'stone_bricks'
 for x in range(9):
  for y in range(8):
   for z in range(9):
    wall=x in (0,8) or z in (0,8) or y in (0,7)
    put(x,y,z,material if wall else 'air')
 # Pillars and iron-bar cell walls, with proper cardinal connections.
 for x in (1,2,3,5,6,7):
  for y in (1,2,3):put(x,y,3,'iron_bars',{'north':'false','south':'false','east':'true','west':'true','waterlogged':'false'})
 for y in (1,2,3):
  put(1,y,2,'iron_bars',{'north':'true','south':'true','east':'false','west':'false','waterlogged':'false'})
  put(7,y,2,'iron_bars',{'north':'true','south':'true','east':'false','west':'false','waterlogged':'false'})
 for x in range(1,8):
  for y in (1,2,3):put(x,y,1,'iron_bars',{'north':'false','south':'false','east':'true','west':'true','waterlogged':'false'})
 for y in range(1,8):put(4,y,4,'ladder',{'facing':'south','waterlogged':'false'});put(4,y,3,'stone_bricks')
 # Leave an entrance next to the ladder for escorts.
 for y in (1,2):put(5,y,3,'air')
 put(6,2,6,'lantern',{'hanging':'false','waterlogged':'false'})
 put(4,7,4,'jigsaw',{'orientation':'up_south'},{'id':S('minecraft:jigsaw'),'name':S('halloween:dungeon'),'target':S('halloween:dungeon'),'pool':S('minecraft:empty'),'joint':S('aligned'),'final_state':S('minecraft:air')})
 ent=C({'pos':L[n.Double]([4.5,1.,2.5]),'blockPos':L[I]([4,1,2]),'nbt':C({'id':S('minecraft:marker'),'Tags':L[S]([S('halloween_jail')])})})
 out=n.File({'DataVersion':I(4671),'size':L[I]([9,8,9]),'palette':L[C](palette),'blocks':L[C](list(bd.values())),'entities':L[C]([ent])},gzipped=True)
 path=R/f'data/halloween/structure/village/{biome}/dungeon.nbt';out.save(path,gzipped=True)
 path=R/f'data/halloween/worldgen/template_pool/village/{biome}/dungeon.json';path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps({'elements':[{'element':{'element_type':'minecraft:single_pool_element','location':f'halloween:village/{biome}/dungeon','processors':'minecraft:empty','projection':'rigid'},'weight':1}],'fallback':'minecraft:empty'},indent=2))

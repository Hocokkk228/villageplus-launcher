from pathlib import Path
import nbtlib as n
S=n.String;I=n.Int;C=n.Compound;L=n.List
for biome in ('plains','desert','savanna','snowy','taiga'):
 p=Path(f'src/main/resources/data/halloween/structure/village/{biome}/royal_castle.nbt');f=n.load(p);palette=f['palette'];blocks={tuple(map(int,b['pos'])):b for b in f['blocks']}
 def put(x,y,z,name,props=None):
  st=C({'Name':S('minecraft:'+name)})
  if props:st['Properties']=C({k:S(v) for k,v in props.items()})
  try:i=palette.index(st)
  except ValueError:i=len(palette);palette.append(st)
  blocks[x,y,z]=C({'pos':L[I]([x,y,z]),'state':I(i)})
 wall='cut_sandstone' if biome in ('desert','savanna') else 'stone_bricks'
 for x in range(5,10):
  for z in range(18,24):
   put(x,0,z,wall);put(x,5,z,wall)
   for y in range(1,5):
    if x in (5,9) or z in (18,23):put(x,y,z,'iron_bars')
    else:put(x,y,z,'air')
 for y in (1,2,3):put(9,y,21,'air')
 put(6,1,19,'lantern',{'hanging':'false','waterlogged':'false'})
 f['blocks']=L[C](list(blocks.values()))
 marker=C({'pos':L[n.Double]([7.5,1.,21.5]),'blockPos':L[I]([7,1,21]),'nbt':C({'id':S('minecraft:marker'),'Tags':L[S]([S('halloween_jail')])})})
 f['entities'].append(marker);f.save(p,gzipped=True)

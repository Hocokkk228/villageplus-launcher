from pathlib import Path
import nbtlib as n
S=n.String;I=n.Int;C=n.Compound;L=n.List
for biome in ('plains','desert','savanna','snowy','taiga'):
 p=Path(f'src/main/resources/data/halloween/structure/village/{biome}/royal_castle.nbt');f=n.load(p);pal=f['palette'];bs={tuple(map(int,b['pos'])):b for b in f['blocks']}
 def put(x,y,z,name,props=None):
  st=C({'Name':S('minecraft:'+name)})
  if props:st['Properties']=C({k:S(v) for k,v in props.items()})
  try:i=pal.index(st)
  except ValueError:i=len(pal);pal.append(st)
  bs[x,y,z]=C({'pos':L[I]([x,y,z]),'state':I(i)})
 for y in range(2,9):put(28,y,65,'air')
 for step in range(7):
  for x in (28,29):
   y=1+step;z=62+step
   for below in range(1,y):put(x,below,z,'spruce_planks')
   put(x,y,z,'spruce_stairs',{'facing':'south','half':'bottom','shape':'straight','waterlogged':'false'})
   for headroom in (1,2,3):put(x,y+headroom,z,'air')
 f['blocks']=L[C](list(bs.values()));f.save(p,gzipped=True)

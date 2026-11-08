from pathlib import Path
import nbtlib as n
S=n.String;I=n.Int;C=n.Compound;L=n.List
for biome in ('plains','desert','savanna','snowy','taiga'):
 root=Path('src/main/resources/data/halloween/structure/village')/biome
 for filename in ('dungeon.nbt','royal_castle.nbt'):
  p=root/filename;f=n.load(p);pal=f['palette'];blocks={tuple(map(int,b['pos'])):b for b in f['blocks']}
  def put(x,y,z,name,props=None):
   st=C({'Name':S('minecraft:'+name)})
   if props:st['Properties']=C({k:S(v) for k,v in props.items()})
   try:i=pal.index(st)
   except ValueError:i=len(pal);pal.append(st)
   blocks[x,y,z]=C({'pos':L[I]([x,y,z]),'state':I(i)})
  if filename=='dungeon.nbt':
   for pos,b in list(blocks.items()):
    if str(pal[int(b['state'])]['Name']) in ('minecraft:ladder','minecraft:cobblestone_stairs'):put(*pos,'air')
   material='cut_sandstone' if biome in ('desert','savanna') else 'stone_bricks'
   for x in range(9):
    for y in range(8):
     for z in range(9,13):put(x,y,z,material if x in (0,8) or y in (0,7) or z==12 else 'air')
   f['size']=L[I]([9,8,13])
   for i in range(7):
    for x in (3,4,5):
     y=6-i;z=4+i
     put(x,y,z,'cobblestone_stairs',{'facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
     for h in (1,2,3):
      if y+h<8 and (x,y+h,z)!=(4,7,4):put(x,y+h,z,'air')
   for x in (3,4,5):
    put(x,7,3,'cobblestone_stairs',{'facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
   for z in range(3,11):
    for y in (1,2,3):put(6,y,z,'air')
    put(6,0,z,'stone_bricks')
   for x in (4,5,6):
    for y in (1,2,3):put(x,y,3,'air')
   # Two side chambers; central x=4 corridor stays open to the stairs.
   for x in range(1,8):
    for z in range(1,4):
     for y in range(1,7):
      partition=x in (3,5)
      put(x,y,z,'iron_bars' if partition and not(z==2 and y<=2) else 'air')
   for x in (1,2,3,5,6,7):
    for y in range(1,7):
     old=blocks.get((x,y,4))
     if old is None or str(pal[int(old['state'])]['Name']) in ('minecraft:air','minecraft:iron_bars'):put(x,y,4,'iron_bars')
  else:
   for pos,b in list(blocks.items()):
    if 6<=pos[0]<=8 and 19<=pos[2]<=23 and str(pal[int(b['state'])]['Name'])=='minecraft:ladder':put(*pos,'air')
   for x in (6,7,8):
    put(x,0,19,'cobblestone_stairs',{'facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
    for y in (1,2,3):put(x,y,19,'air')
   for i in range(7):
    for x in (6,7,8):
     y=-2-i;z=21+i
     for h in (1,2,3):
      if y+h>=0:put(x,y+h,z,'air')
   for x in (6,7,8):
    for y in (0,1,2):put(x,y,20,'air')
   put(7,1,21,'air')
  if filename=='dungeon.nbt':
   for x in range(1,8):
    for z in range(0,5):put(x,4,z,material)
   for pos,b in list(blocks.items()):
    if str(pal[int(b['state'])]['Name'])!='minecraft:iron_bars':continue
    x,y,z=pos;props={'waterlogged':'false'}
    for direction,dx,dz in [('north',0,-1),('south',0,1),('east',1,0),('west',-1,0)]:
     nb=blocks.get((x+dx,y,z+dz));name=str(pal[int(nb['state'])]['Name']) if nb else 'minecraft:air'
     props[direction]='true' if name=='minecraft:iron_bars' or name in ('minecraft:stone_bricks','minecraft:cut_sandstone','minecraft:sandstone','minecraft:cobblestone') else 'false'
    put(x,y,z,'iron_bars',props)
  f['blocks']=L[C](list(blocks.values()));f.save(p,gzipped=True)

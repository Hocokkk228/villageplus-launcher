from pathlib import Path
import nbtlib as n
S=n.String;I=n.Int;C=n.Compound;L=n.List
R=Path('src/main/resources/data/halloween/structure/village')
for biome in ('plains','desert','savanna','snowy','taiga'):
 p=R/biome/'houses/mansion.nbt';f=n.load(p);pal=f['palette'];bs={tuple(map(int,b['pos'])):b for b in f['blocks']}
 def put(x,y,z,name,props=None):
  st=C({'Name':S('minecraft:'+name)})
  if props:st['Properties']=C({k:S(v) for k,v in props.items()})
  try:i=pal.index(st)
  except ValueError:i=len(pal);pal.append(st)
  bs[x,y,z]=C({'pos':L[I]([x,y,z]),'state':I(i)})
 # Layered masonry, keep all windows/doors and interior features.
 sandstone=biome in ('desert','savanna')
 for x in range(1,14):
  for z in range(1,16):
   for y in range(1,10):
    if x not in (1,13) and z not in (1,15):continue
    record=bs.get((x,y,z));old=str(pal[int(record['state'])]['Name']).split(':')[-1] if record else 'air'
    if old not in ('stone_bricks','cut_sandstone','polished_andesite','smooth_sandstone'):continue
    t=(x*31+y*11+z*17)%23
    if y==1:name='smooth_sandstone' if sandstone else 'deepslate_bricks'
    elif y==5:name='chiseled_sandstone' if sandstone else 'polished_tuff'
    elif t in (0,1):name='sandstone' if sandstone else 'tuff_bricks'
    elif t==2:name='chiseled_sandstone' if sandstone else 'cracked_stone_bricks'
    elif t==3:name='smooth_sandstone' if sandstone else 'mossy_stone_bricks'
    else:name='cut_sandstone' if sandstone else 'stone_bricks'
    put(x,y,z,name)
 # Decorative framing and projecting ledges over windows.
 for x in (3,5,9,11):
  for z in (0,16):
   put(x,4,z,'sandstone_slab' if sandstone else 'stone_brick_slab',{'type':'bottom','waterlogged':'false'})
   put(x,9,z,'sandstone_slab' if sandstone else 'stone_brick_slab',{'type':'bottom','waterlogged':'false'})
 for z in (4,7,10,13):
  for x in (0,14):
   put(x,4,z,'sandstone_slab' if sandstone else 'stone_brick_slab',{'type':'bottom','waterlogged':'false'})
   put(x,9,z,'sandstone_slab' if sandstone else 'stone_brick_slab',{'type':'bottom','waterlogged':'false'})
 # Entrance columns, canopy and path do not obstruct the doorway.
 for x in (5,9):
  for y in range(1,4):put(x,y,0,'stripped_spruce_log')
  put(x,4,0,'lantern',{'hanging':'false','waterlogged':'false'})
 for x in range(5,10):put(x,5,0,'spruce_slab',{'type':'bottom','waterlogged':'false'})
 # Wood-framed gables with a window beneath the ridge.
 for x in range(1,14):
  for z in (1,15):
   for y in range(10,9+min(x,14-x)):
    put(x,y,z,'stripped_spruce_log' if (x in (4,10) or y==10) else 'spruce_planks')
 for z in (1,15):
  for y in (11,12):put(7,y,z,'glass')
 f['blocks']=L[C](list(bs.values()));f.save(p,gzipped=True)
 # Extend the castle towards the road by 16 blocks for an actual assembly square.
 p=R/biome/'royal_castle.nbt';f=n.load(p);pal=f['palette'];bs={}
 def put(x,y,z,name,props=None,extra=None):
  st=C({'Name':S('minecraft:'+name)})
  if props:st['Properties']=C({k:S(v) for k,v in props.items()})
  try:i=pal.index(st)
  except ValueError:i=len(pal);pal.append(st)
  value=C({'pos':L[I]([x,y,z]),'state':I(i)})
  if extra:value['nbt']=C(extra)
  bs[x,y,z]=value
 for b in f['blocks']:
  x,y,z=map(int,b['pos']);is_north=(x==21 and z==0 and str(pal[int(b['state'])]['Name'])=='minecraft:jigsaw')
  z=0 if is_north else z+16;b['pos']=L[I]([x,y,z]);bs[x,y,z]=b
 for e in f['entities']:
  e['pos'][2]=n.Double(float(e['pos'][2])+16);e['blockPos'][2]=I(int(e['blockPos'][2])+16)
  if 'halloween_square' in [str(t) for t in e['nbt'].get('Tags',[])]:
   e['pos'][0]=n.Double(21.5);e['pos'][2]=n.Double(8.5);e['blockPos'][0]=I(21);e['blockPos'][2]=I(8)
 # Clear old bell marker at the entrance of the inner court.
 put(21,1,20,'air')
 sandstone=biome in ('desert','savanna');base='smooth_sandstone' if sandstone else 'polished_andesite';accent='cut_sandstone' if sandstone else 'stone_bricks'
 for x in range(43):
  for z in range(0,16):
   for y in range(1,8):put(x,y,z,'air')
   mat='deepslate_tiles' if not sandstone and (x==2 or x==40 or z in (1,14)) else accent if (x//3+z//3)%3==0 else base
   put(x,0,z,mat)
 # Restore relocated north street connector after laying the square.
 put(21,0,0,'jigsaw',{'orientation':'north_up'},{'id':S('minecraft:jigsaw'),'name':S('minecraft:street'),'target':S('minecraft:street'),'pool':S(f'minecraft:village/{biome}/streets'),'final_state':S('minecraft:structure_void'),'joint':S('aligned')})
 # Spacious timber grandstands around the assembly floor with aisle through the middle.
 for x0 in (4,28):
  for x in range(x0,x0+11):
   for z in (2,3,4):
    if x==x0+5:continue
    height=4-z
    for y in range(1,height):put(x,y,z,'spruce_planks')
    put(x,height,z,'spruce_stairs',{'facing':'south','half':'bottom','shape':'straight','waterlogged':'false'})
 # Platform, lectern, bell, planters and lamps.
 for x in range(18,25):
  for z in range(10,14):put(x,1,z,'spruce_planks')
 for x in (18,24):
  for z in (10,13):put(x,2,z,'spruce_fence')
 put(21,2,11,'lectern',{'facing':'south','has_book':'false','powered':'false'})
 put(21,1,6,'bell',{'attachment':'floor','facing':'north','powered':'false'})
 for x,z in ((3,7),(39,7),(16,4),(26,4)):
  put(x,1,z,'stone_brick_wall');put(x,2,z,'lantern',{'hanging':'false','waterlogged':'false'})
 for x,z in ((14,12),(28,12)):
  put(x,1,z,'moss_block');put(x,2,z,'oak_leaves',{'distance':'7','persistent':'true','waterlogged':'false'})
 f['size']=L[I]([43,21,59]);f['blocks']=L[C](list(bs.values()));f.save(p,gzipped=True)

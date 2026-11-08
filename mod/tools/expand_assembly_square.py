from pathlib import Path
import nbtlib as n
R=Path('src/main/resources/data/halloween/structure/village');S=n.String;I=n.Int;C=n.Compound;L=n.List
for biome in ('plains','desert','savanna','snowy','taiga'):
 p=R/biome/'royal_castle.nbt';f=n.load(p);pal=f['palette'];blocks={}
 def put(x,y,z,name,props=None,extra=None):
  st=C({'Name':S('minecraft:'+name)})
  if props:st['Properties']=C({k:S(v) for k,v in props.items()})
  try:i=pal.index(st)
  except ValueError:i=len(pal);pal.append(st)
  rec=C({'pos':L[I]([x,y,z]),'state':I(i)})
  if extra:rec['nbt']=C(extra)
  blocks[x,y,z]=rec
 # Shift the castle, preserving its dungeon connector and all structure entities.
 for b in f['blocks']:
  x,y,z=map(int,b['pos']);name=str(pal[int(b['state'])]['Name']);tag=b.get('nbt',{})
  street=name=='minecraft:jigsaw' and str(tag.get('name',''))=='minecraft:street'
  if z<16:
   if not street:continue
   if z==0:x,z=32,0
   else:continue
  else:
   if street and x==0:x=0
   elif street and x==42:x=64
   else:x+=11
   z+=29
  b['pos']=L[I]([x,y,z]);blocks[x,y,z]=b
 for ent in f['entities']:
  tags=[str(t) for t in ent['nbt'].get('Tags',[])]
  if 'halloween_square' in tags:
   ent['pos']=L[n.Double]([32.5,1.,24.5]);ent['blockPos']=L[I]([32,1,24])
  else:
   ent['pos'][0]=n.Double(float(ent['pos'][0])+11);ent['pos'][2]=n.Double(float(ent['pos'][2])+29)
   ent['blockPos'][0]=I(int(ent['blockPos'][0])+11);ent['blockPos'][2]=I(int(ent['blockPos'][2])+29)
 sandstone=biome in ('desert','savanna');light='smooth_sandstone' if sandstone else 'polished_andesite';mid='cut_sandstone' if sandstone else 'stone_bricks';dark='sandstone' if sandstone else 'deepslate_tiles'
 # Broad paved square with a central aisle and gently varied masonry.
 for x in range(65):
  for z in range(45):
   for y in range(1,10):put(x,y,z,'air')
   mat=dark if x in (2,62) or z in (2,42) else mid if (x//4+z//4)%3==0 else light
   put(x,0,z,mat)
 # Four grandstand sections, six stepped rows, with broad aisles between sections.
 for side in ('left','right'):
  for row in range(6):
   x=4+row*2 if side=='left' else 60-row*2
   height=6-row
   facing='east' if side=='left' else 'west'
   for z in range(5,37):
    if z in (14,15,16,25,26,27):continue
    for dx in (0,1):
     for y in range(1,height):put(x+dx,y,z,'spruce_planks')
     put(x+dx,height,z,'spruce_stairs',{'facing':facing,'half':'bottom','shape':'straight','waterlogged':'false'})
 # Side stairways up through the stands, preserve level paths into each aisle.
 for side in ('left','right'):
  for row in range(6):
   x=4+row*2 if side=='left' else 60-row*2; height=6-row
   for z in (15,26):
    for dx in (0,1):
     for y in range(1,height):put(x+dx,y,z,'spruce_planks')
     put(x+dx,height,z,'spruce_stairs',{'facing':'east' if side=='left' else 'west','half':'bottom','shape':'straight','waterlogged':'false'})
 # Central speaking platform and approach, with accessible steps and lectern.
 for x in range(27,38):
  for z in range(29,36):put(x,1,z,'spruce_planks')
 for x in range(30,35):put(x,1,28,'spruce_stairs',{'facing':'south','half':'bottom','shape':'straight','waterlogged':'false'})
 for x,z in ((27,29),(37,29),(27,35),(37,35)):put(x,2,z,'spruce_fence')
 put(32,2,32,'lectern',{'facing':'north','has_book':'false','powered':'false'})
 # Bell and landmark lamps around the open central floor.
 put(32,1,18,'bell',{'attachment':'floor','facing':'north','powered':'false'})
 for x,z in ((22,5),(42,5),(22,20),(42,20),(22,38),(42,38)):
  put(x,1,z,'stone_brick_wall');put(x,2,z,'lantern',{'hanging':'false','waterlogged':'false'})
 for x,z in ((24,31),(40,31),(24,11),(40,11)):
  put(x,1,z,'moss_block');put(x,2,z,'oak_leaves',{'distance':'7','persistent':'true','waterlogged':'false'})
 # Northern road connector, at the edge of the template.
 put(32,0,0,'jigsaw',{'orientation':'north_up'},{'id':S('minecraft:jigsaw'),'name':S('minecraft:street'),'target':S('minecraft:street'),'pool':S(f'minecraft:village/{biome}/streets'),'final_state':S('minecraft:structure_void'),'joint':S('aligned')})
 f['size']=L[I]([65,21,88]);f['blocks']=L[C](list(blocks.values()));f.save(p,gzipped=True)

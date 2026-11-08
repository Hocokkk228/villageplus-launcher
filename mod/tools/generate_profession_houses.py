"""Three furnished homes per profession, shared by world generation and builders."""
from pathlib import Path
import json,nbtlib as n
from house_interiors import decorate
R=Path('src/main/resources');S=n.String;I=n.Int;C=n.Compound;L=n.List
roles=['builder','farmer','armorer','weaponsmith','toolsmith','mason','fletcher','fisherman','shepherd','butcher','leatherworker','librarian','cartographer','cleric','guard','treasurer']
stations=dict(zip(roles,['halloween:builder_workbench','composter','blast_furnace','grindstone','smithing_table','stonecutter','fletching_table','barrel','loom','smoker','cauldron','lectern','cartography_table','brewing_stand','fletching_table','lectern']))
for ri,role in enumerate(roles):
 for variant in range(3):
  # Each role has one stable design; the three names remain for existing worlds.
  dimensions=[(11,11,False),(13,11,False),(11,13,False),(13,11,False),(11,11,True),(13,11,False),(9,13,True),(13,9,False),(11,13,False),(13,11,False),(11,11,False),(13,13,True),(11,11,True),(11,13,True),(11,11,True),(13,11,True)]
  blocks={};w,d,two=dimensions[ri];walltop=8 if two else 4
  timber='spruce';roof=['deepslate_tile','brick','tuff_brick','stone_brick','polished_blackstone_brick','deepslate_brick'][ri%6];wall=['stone_bricks','tuff_bricks','bricks','polished_andesite','deepslate_bricks','mossy_stone_bricks','polished_tuff','cut_sandstone','cobbled_deepslate','bricks','tuff_bricks','stone_bricks','deepslate_tiles','stone_bricks','polished_blackstone_bricks','polished_blackstone_bricks'][ri];accent=['chiseled_stone_bricks','polished_tuff','stone_bricks','bricks'][ri%4]
  def put(x,y,z,name,props=None):blocks[x,y,z]=(name if ':' in name else 'minecraft:'+name,props or {})
  for x in range(w):
   for z in range(d):
    put(x,0,z,'stone_bricks')
    for y in range(1,walltop+7):put(x,y,z,'air')
    if x in (1,w-2) or z in (1,d-2):
     if 1<=x<=w-2 and 1<=z<=d-2:
      for y in range(1,walltop+1):
       corner=x in (1,w-2) and z in (1,d-2)
       put(x,y,z,accent if corner else wall)
  for y in [2]+([6] if two else []):
   for x in [3,w-4]:
    for z in [1,d-2]:put(x,y,z,'glass_pane');put(x,y+1,z,'glass_pane')
   for x in [1,w-2]:
    for z in [4,d-5]:put(x,y,z,'glass_pane');put(x,y+1,z,'glass_pane')
  cx=w//2
  put(cx,1,1,timber+'_door',{'facing':'south','half':'lower','hinge':'left','open':'false','powered':'false'});put(cx,2,1,timber+'_door',{'facing':'south','half':'upper','hinge':'left','open':'false','powered':'false'})
  put(cx,0,0,'stone_brick_stairs',{'facing':'south','half':'bottom','shape':'straight','waterlogged':'false'})
  # Continuous beams, eaves, pitched roof and filled triangular gables.
  for x in range(1,w-1):
   for z in [1,d-2]:put(x,walltop,z,accent)
  for layer in range(w//2+1):
   y=walltop+1+layer
   for z in range(d):
    for x,face in [(layer,'east'),(w-1-layer,'west')]:
     put(x,y,z,roof+'_slab' if x==cx else roof+'_stairs',{'type':'bottom','waterlogged':'false'} if x==cx else {'facing':face,'half':'bottom','shape':'straight','waterlogged':'false'})
   for x in range(layer+1,w-layer-1):
    for z in [1,d-2]:put(x,y,z,wall)
  if two:
   for x in range(2,w-2):
    for z in range(2,d-2):put(x,4,z,timber+'_planks')
   for k in range(4):
    put(2,k+1,3+k,timber+'_stairs',{'facing':'south','half':'bottom','shape':'straight','waterlogged':'false'})
    for h in range(k+2,7):put(2,h,3+k,'air')
  by=5 if two else 1;bx=3 if two else 2;bz=d-5
  for z,part in [(bz,'foot'),(bz+1,'head')]:put(bx,by,z,'white_bed',{'facing':'south','part':part,'occupied':'false'})
  if False:
   for z,part in [(bz,'foot'),(bz+1,'head')]:put(w-4,by,z,'cyan_bed',{'facing':'south','part':part,'occupied':'false'})
  put(w-3,1,3,stations[role]);put(w-3,1,4,'chest',{'facing':'west','type':'single','waterlogged':'false'})
  put(cx,walltop,5,'lantern',{'hanging':'true','waterlogged':'false'});put(cx,walltop+1,5,wall)
  for x in [3,w-4]:put(x,1,3,'orange_carpet')
  put(3,1,2,timber+'_stairs',{'facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
  if role in ['armorer','weaponsmith','toolsmith','builder','mason']:
   put(w-3,1,d-4,'anvil',{'facing':'north'});put(w-3,1,d-3,'furnace',{'facing':'north','lit':'false'})
   for y in range(2,walltop+7):put(w-3,y,d-3,'bricks')
   put(w-3,walltop+7,d-3,'brick_wall',{'up':'true','north':'none','south':'none','east':'none','west':'none','waterlogged':'false'})
   put(w-3,2,d-4,'iron_bars',{'north':'false','south':'true','east':'false','west':'false','waterlogged':'false'})
  elif role in ['librarian','cartographer','cleric']:
   for z in range(5,d-3):put(w-3,1,z,'bookshelf');put(w-3,2,z,'bookshelf')
   put(w//2,walltop+2,1,'glass',{})
   if role=='cleric':
    for y in range(2,walltop+6):put(w//2,y,d-3,'chiseled_stone_bricks')
    put(w//2,walltop+6,d-3,'bell',{'attachment':'floor','facing':'north','powered':'false'})
  elif role=='farmer':
   put(w-3,1,d-4,'hay_block',{'axis':'y'});put(w-3,1,d-3,'barrel',{'facing':'up','open':'false'});put(w-4,1,d-3,'pumpkin',{})
  elif role in ['fisherman','butcher','leatherworker']:
   for z in range(5,d-3):put(w-3,1,z,'barrel',{'facing':'west','open':'false'})
   put(w-4,1,d-3,'cauldron')
  elif role=='shepherd':
   for y in range(1,3):put(w-3,y,d-3,'white_wool');put(w-4,y,d-3,'red_wool')
  elif role=='guard':put(w-3,1,d-3,'target');put(w-3,2,d-3,'target');put(3,1,d-3,'barrel',{'facing':'up','open':'false'})
  elif role=='treasurer':put(w-3,1,d-3,'gold_block');put(w-4,1,d-3,'barrel',{'facing':'up','open':'false'})
  else:put(w-3,1,d-3,'crafting_table')
  # Profession-specific silhouettes and recognizable stonework.
  if role in ['guard','treasurer','cartographer']:
   # Raised corner watchtower, with crenellations.
   for x in range(1,4):
    for z in range(d-4,d-1):
     for y in range(walltop+1,walltop+5):
      if x in (1,3) or z in (d-4,d-2): put(x,y,z,accent)
     put(x,walltop+5,z,accent)
     if (x+z)%2==0: put(x,walltop+6,z,wall)
  if role=='cleric':
   for y in range(walltop+1,walltop+8): put(cx,y,d-3,'stone_bricks')
   for dx in (-1,0,1): put(cx+dx,walltop+6,d-3,'chiseled_stone_bricks')
  if role in ['armorer','weaponsmith','toolsmith','mason','builder','butcher']:
   # Broad exterior chimney varies in width and position by trade.
   x=1 if ri%2 else w-2
   for y in range(2,walltop+5):
    for z in range(d-4,d-2): put(x,y,z,'bricks' if role!='mason' else 'stone_bricks')
   for z in range(d-4,d-2): put(x,walltop+5,z,'campfire',{'facing':'north','lit':'false','signal_fire':'false','waterlogged':'false'})
  if role in ['farmer','shepherd','fisherman','leatherworker','fletcher']:
   # Low stone side annex, its roof and a trade-coloured sign band.
   for x in range(1,5):
    for z in range(d-4,d):
     if x in (1,4) or z in (d-4,d-1):
      for y in range(1,4): put(x,y,z,accent)
     put(x,4,z,'stone_brick_slab',{'type':'bottom','waterlogged':'false'})
   put(2,4,d-1,['hay_block','blue_wool','white_wool','brown_wool','green_wool'][['farmer','fisherman','shepherd','leatherworker','fletcher'].index(role)])
  if role=='librarian':
   for x in range(3,w-3):
    for y in range(5,8): put(x,y,d-2,'blue_stained_glass')
  # Distinct rooflines, wings and stone details, kept within the template footprint.
  if role in ['guard','treasurer','mason','fisherman']:
   # Flat battlement roofs, each with a different building profile.
   for x in range(w):
    for z in range(d):
     for y in range(walltop+1,walltop+w//2+2): put(x,y,z,'air')
     if x in (0,w-1) or z in (0,d-1):put(x,walltop+1,z,'stone_bricks')
     else:put(x,walltop+1,z,'stone_brick_slab',{'type':'bottom','waterlogged':'false'})
     if (x in (0,w-1) or z in (0,d-1)) and (x+z)%2==0: put(x,walltop+2,z,accent)
  if role in ['guard','treasurer','cartographer','cleric','librarian']:
   # Tall roofed tower rises from the left front; decorative exterior spiral steps.
   tx,tz=2,3
   for x in range(0,5):
    for z in range(1,6):
     for y in range(1,walltop+7):
      if x in (0,4) or z in (1,5): put(x,y,z,accent)
     put(x,walltop+7,z,wall)
     if (x+z)%2==0 and (x in (0,4) or z in (1,5)): put(x,walltop+8,z,accent)
   for y in (3,6):
    put(0,y,3,'glass_pane');put(0,y+1,3,'glass_pane')
   for k in range(4):
    put(0,k+1,6+k,'stone_brick_stairs',{'facing':'south','half':'bottom','shape':'straight','waterlogged':'false'})
    if k<3: put(1,k+1,6+k,'stone_brick_wall')
   if role in ['cleric','librarian','cartographer']:
    for layer in range(3):
     for x in range(layer,5-layer):
      for z in range(1+layer,6-layer):
       if x in (layer,4-layer) or z in (1+layer,5-layer): put(x,walltop+8+layer,z,roof+'_stairs',{'facing':'north' if z==1+layer else 'south','half':'bottom','shape':'straight','waterlogged':'false'})
  if role in ['farmer','shepherd','fisherman','leatherworker']:
   # Open-front working porch with a sloping stone awning.
   for x in range(1,w-1):
    put(x,4,0,roof+'_slab',{'type':'bottom','waterlogged':'false'})
   for x in (1,w-2):
    for y in range(1,4): put(x,y,0,'stone_brick_wall')
  if role in ['armorer','weaponsmith','toolsmith','builder','butcher']:
   # Monumental forge vent and stone workshop canopy.
   for x in range(w-4,w-1):
    for z in range(d-4,d-1):
     for y in range(walltop+1,walltop+5):
      if x in (w-4,w-2) or z in (d-4,d-2): put(x,y,z,'bricks')
     if x in (w-4,w-2) or z in (d-4,d-2): put(x,walltop+5,z,'stone_brick_slab',{'type':'bottom','waterlogged':'false'})
   for x in range(2,w-2): put(x,4,0,'stone_brick_slab',{'type':'bottom','waterlogged':'false'})
   for x in (2,w-3):
    for y in range(1,4): put(x,y,0,'stone_brick_wall')
  if role=='fletcher':
   for y in range(walltop+1,walltop+6): put(w-2,y,d-3,'spruce_fence')
   for x in range(w-4,w): put(x,walltop+5,d-3,'green_wool')
  # The annexes and towers are added after the first interior pass: restore a complete bed in a safe spot.
  for pos,(block,props) in list(blocks.items()):
   if block.endswith('_bed'):put(*pos,'air')
  bx=3 if two else 2; bz=d-5 if two else 2; by=5 if two else 1
  for z,part in [(bz,'foot'),(bz+1,'head')]:
   put(bx,by,z,'white_bed',{'facing':'south','part':part,'occupied':'false'})
   put(bx,by+1,z,'air')
  # Furnish every usable level after the structural work, without overwriting doors or beds.
  def interior_item(x,y,z,block,props=None):
   if 1<x<w-2 and 1<z<d-2 and blocks.get((x,y,z),('minecraft:air',{}))[0]=='minecraft:air':put(x,y,z,block,props)
  # Compact living corner and storage on each floor, without touching the bed or workbench.
  for floor in ([1,5] if two else [1]):
   for x in (w-5,w-4):interior_item(x,floor,d-4,'spruce_stairs',{'facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
   interior_item(w-3,floor,d-4,'spruce_slab',{'type':'bottom','waterlogged':'false'})
   interior_item(w-3,floor,d-5,'bookshelf')
   interior_item(w-3,floor+1,d-5,'bookshelf')
   interior_item(w-3,floor,d-3,'barrel',{'facing':'west','open':'false'})
   interior_item(w-3,floor+1,d-3,'barrel',{'facing':'west','open':'false'})
   if floor==5:interior_item(w-4,floor,3,'chest',{'facing':'south','type':'single','waterlogged':'false'})
  decorate(blocks,put,w,d,1,role)
  if two:decorate(blocks,put,w,d,5,role)
  # Deterministic masonry variation: sparse wear, not random changes on reload.
  weathered={
   'stone_bricks':['cracked_stone_bricks','mossy_stone_bricks'],
   'deepslate_bricks':['cracked_deepslate_bricks','polished_deepslate'],
   'deepslate_tiles':['cracked_deepslate_tiles','deepslate_bricks'],
   'polished_blackstone_bricks':['cracked_polished_blackstone_bricks','polished_blackstone'],
   'cobblestone':['mossy_cobblestone','stone'],
   'tuff_bricks':['polished_tuff','chiseled_tuff_bricks']}
  for (x,y,z),(name,props) in list(blocks.items()):
   block=name.removeprefix('minecraft:'); pattern=(x*37+y*19+z*53+ri*11)%31
   if block in weathered and pattern in (0,1,2,3):
    put(x,y,z,weathered[block][pattern%2])
   elif block=='deepslate_tile_stairs' and pattern in (0,1,2):
    put(x,y,z,'deepslate_brick_stairs',props)
   elif block=='deepslate_brick_stairs' and pattern in (0,1,2):
    put(x,y,z,'polished_deepslate_stairs',props)
   elif block=='stone_brick_stairs' and pattern in (0,1,2):
    put(x,y,z,'mossy_stone_brick_stairs',props)
  palette=[];lookup={};bd=[]
  for (x,y,z),(name,props) in sorted(blocks.items(),key=lambda e:(e[0][1],e[0][0],e[0][2])):
   key=(name,tuple(sorted(props.items())))
   if key not in lookup:lookup[key]=len(palette);palette.append({'name':name,'properties':props})
   bd.append([x,y,z,lookup[key]])
  size=[w,max(p[1] for p in blocks)+1,d];name=f'profession_{role}_{variant}'
  out=R/'data/halloween/era_templates';out.mkdir(parents=True,exist_ok=True);(out/f'{name}.json').write_text(json.dumps({'size':size,'palette':palette,'blocks':bd},separators=(',',':')))
  pal=[]
  for st in palette:
   q=C({'Name':S(st['name'])})
   if st['properties']:q['Properties']=C({k:S(v) for k,v in st['properties'].items()})
   pal.append(q)
  nbt=n.File({'DataVersion':I(4671),'size':L[I](size),'palette':L[C](pal),'blocks':L[C]([C({'pos':L[I](b[:3]),'state':I(b[3])}) for b in bd]),'entities':L[C]([])},gzipped=True)
  out=R/'data/halloween/structure/village/shared/houses';out.mkdir(parents=True,exist_ok=True);nbt.save(out/f'{name}.nbt',gzipped=True)

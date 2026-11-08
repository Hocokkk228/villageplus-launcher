from pathlib import Path
import json, nbtlib as n
from house_interiors import decorate
R=Path('src/main/resources/data/halloween'); S=n.String; I=n.Int; C=n.Compound; L=n.List
styles=[('courtyard','stone_bricks','deepslate_tile'),('tower','deepslate_bricks','stone_brick'),('gable','bricks','tuff_brick'),('villa','tuff_bricks','brick')]
for idx,(name,wall,roof) in enumerate(styles):
 b={}; w=d=15
 def put(x,y,z,v,p=None):
  if 0<=x<w and 0<=z<d and y>=0:b[x,y,z]=(v if ':' in v else 'minecraft:'+v,p or {})
 def stair(x,y,z,material,face):put(x,y,z,material+'_stairs',{'facing':face,'half':'bottom','shape':'straight','waterlogged':'false'})
 def slab(x,y,z,material):put(x,y,z,material+'_slab',{'type':'bottom','waterlogged':'false'})
 def shell(x0,z0,x1,z1,height,mat):
  for x in range(x0,x1+1):
   for z in range(z0,z1+1):
    put(x,0,z,'stone_bricks')
    for y in range(1,height+1):
     if x in (x0,x1) or z in (z0,z1): put(x,y,z,mat)
     else:put(x,y,z,'air')
  for x in range(x0+2,x1,3):
   for z in (z0,z1):put(x,2,z,'glass_pane');put(x,3,z,'glass_pane')
  for z in range(z0+2,z1,3):
   for x in (x0,x1):put(x,2,z,'glass_pane');put(x,3,z,'glass_pane')
 def roofed(x0,z0,x1,z1,y):
  width=x1-x0
  for k in range(width//2+1):
   for z in range(z0-1,z1+2):
    if x0+k==x1-k:slab(x0+k,y+k,z,roof)
    else:stair(x0+k,y+k,z,roof,'east');stair(x1-k,y+k,z,roof,'west')
   for x in range(x0+k+1,x1-k):
    for z in (z0,z1):put(x,y+k,z,wall)
 # Filled foundation and clearance for the build volume.
 for x in range(w):
  for z in range(d):
   put(x,0,z,'stone_bricks')
   for y in range(1,17):put(x,y,z,'air')
 if idx==0: # courtyard cottage: transverse wing and entrance veranda
  shell(2,3,12,11,4,wall);roofed(2,3,12,11,5)
  for x in range(2,13):slab(x,4,1,'stone_brick');slab(x,4,2,'stone_brick')
  for x in (2,12):
   for y in range(1,4):put(x,y,1,'stone_brick_wall')
  for x in (3,4,10,11):put(x,1,2,'flower_pot')
 elif idx==1: # tall house with attached crenellated tower
  shell(2,3,12,12,8,wall);roofed(2,3,12,12,9)
  for x in range(1,6):
   for z in range(1,6):
    for y in range(1,12):
     if x in (1,5) or z in (1,5):put(x,y,z,'polished_deepslate')
    put(x,12,z,'deepslate_bricks')
    if (x in (1,5) or z in (1,5)) and (x+z)%2==0:put(x,13,z,'deepslate_bricks')
  for y in (3,6,9):put(1,y,3,'glass_pane');put(1,y+1,3,'glass_pane')
  for k in range(4):stair(0,k+1,6+k,'stone_brick','south')
 elif idx==2: # cross-gabled brick house with forward porch
  shell(2,3,12,11,5,wall);roofed(2,3,12,11,6)
  for x in range(5,10):
   for z in range(1,5):
    for y in range(1,5):
     if x in (5,9) or z==1:put(x,y,z,wall)
  roofed(5,1,9,4,5)
  for x in (3,11):
   for y in range(1,5):put(x,y,3,'chiseled_stone_bricks')
 elif idx==3: # two-storey villa with front balcony and open arcade
  shell(2,3,12,12,8,wall);roofed(2,3,12,12,9)
  for x in range(2,13):
   for z in (1,2):put(x,4,z,'stone_brick_slab',{'type':'bottom','waterlogged':'false'})
   if x not in (7,):put(x,5,1,'stone_brick_wall')
  for x in (2,5,9,12):
   for y in range(1,4):put(x,y,1,'stone_brick_wall')
 # Upper-storey windows on all four facades of the villa and tower house.
 if idx in (1,3):
  for x in (5,7,9):
   for z in (3,12):
    for y in (6,7):put(x,y,z,'glass_pane')
  for z in (6,9):
   for x in (2,12):
    for y in (6,7):put(x,y,z,'glass_pane')
 # Front doorway and porch floor.
 entrance_z=1 if idx==2 else 3; cx=7
 for y,half in ((1,'lower'),(2,'upper')):put(cx,y,entrance_z,'spruce_door',{'facing':'south','half':half,'hinge':'left','open':'false','powered':'false'})
 for z in range(0,entrance_z):put(cx,0,z,'stone_bricks');put(cx,1,z,'air');put(cx,2,z,'air')
 # Bedroom with a pair of beds, furnished sitting room; keep route unobstructed.
 for x,color in [(4,'white'),(10,'orange')]:
  for z,part in [(8,'foot'),(9,'head')]:put(x,1,z,color+'_bed',{'facing':'south','part':part,'occupied':'false'})
 put(3,1,6,'bookshelf');put(11,1,6,'barrel',{'facing':'up','open':'false'});put(10,1,10,'crafting_table')
 put(7,4,7,'lantern',{'hanging':'true','waterlogged':'false'})
 for x in (3,11):put(x,2,3,'lantern',{'hanging':'false','waterlogged':'false'})
 # Upper floor and an accessible stair in the taller homes.
 if idx in (1,3):
  for x in range(3,12):
   for z in range(4,12):
    if not (x==3 and 5<=z<=8):put(x,4,z,'spruce_planks')
  for k in range(4):
   stair(3,k+1,5+k,'stone_brick','south')
   for y in range(k+2,7):put(3,y,5+k,'air')
  put(9,5,9,'bookshelf');put(10,5,9,'bookshelf');put(10,5,10,'barrel',{'facing':'up','open':'false'})
 # Living-room furniture, grouped away from the central aisle and stairwell.
 def furnishing(x,y,z,block,props=None):
  if b.get((x,y,z),('minecraft:air',{}))[0]=='minecraft:air':put(x,y,z,block,props)
 for level in ([1,5] if idx in (1,3) else [1]):
  # Two adjacent stair blocks make a sofa; slabs at its end make a low side table.
  for x in (8,9):furnishing(x,level,5,'spruce_stairs',{'facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
  furnishing(10,level,5,'spruce_slab',{'type':'bottom','waterlogged':'false'})
  # A chest plus stacked barrels form storage and cupboard units.
  furnishing(11,level,10,'chest',{'facing':'west','type':'single','waterlogged':'false'})
  for y in (level,level+1):furnishing(11,y,8,'barrel',{'facing':'west','open':'false'})
  for y in (level,level+1):furnishing(11,y,9,'bookshelf')
  furnishing(10,level+1,5,'flower_pot')
 decorate(b,put,w,d,1,name)
 if idx in (1,3):decorate(b,put,w,d,5,name)
 # Stone wear adds texture while retaining the original silhouette.
 for pos,(block,props) in list(b.items()):
  x,y,z=pos; q=(x*37+z*29+y*17+idx*13)%37
  if block=='minecraft:stone_bricks' and q in (0,1,2):put(x,y,z,'mossy_stone_bricks' if q==0 else 'cracked_stone_bricks')
  if block=='minecraft:deepslate_bricks' and q in (0,1):put(x,y,z,'cracked_deepslate_bricks')
 # Coordinates remain within the declared structure volume.
 pal=[]; lookup={}; bd=[]
 for (x,y,z),(block,props) in sorted(b.items(),key=lambda a:(a[0][1],a[0][0],a[0][2])):
  key=(block,tuple(sorted(props.items())))
  if key not in lookup:lookup[key]=len(pal);pal.append({'name':block,'properties':props})
  bd.append([x,y,z,lookup[key]])
 size=[w,max(y for x,y,z in b)+1,d]; key='stone_home_'+name
 (R/'era_templates'/f'{key}.json').write_text(json.dumps({'size':size,'palette':pal,'blocks':bd},separators=(',',':')),encoding='utf8')
 np=[]
 for st in pal:
  tag=C({'Name':S(st['name'])})
  if st['properties']:tag['Properties']=C({k:S(v) for k,v in st['properties'].items()})
  np.append(tag)
 target=R/'structure/village/shared/houses';target.mkdir(parents=True,exist_ok=True)
 n.File({'DataVersion':I(4671),'size':L[I](size),'palette':L[C](np),'blocks':L[C]([C({'pos':L[I](v[:3]),'state':I(v[3])}) for v in bd]),'entities':L[C]([])},gzipped=True).save(target/f'{key}.nbt',gzipped=True)

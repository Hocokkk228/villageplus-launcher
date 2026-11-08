from pathlib import Path
import json,nbtlib as n
R=Path('src/main/resources'); biome='plains';p=R/'data/halloween/structure/village/plains/royal_castle.nbt';f=n.load(p)
S=n.String;I=n.Int;C=n.Compound;L=n.List
pal=f['palette'];keys={str(s['Name']):i for i,s in enumerate(pal) if 'Properties' not in s};blocks={}
def state(name):
 name='minecraft:'+name
 if name not in keys:keys[name]=len(pal);pal.append(C({'Name':S(name)}))
 return keys[name]
def put(x,y,z,name):blocks[x,y,z]=C({'pos':L[I]([x,y,z]),'state':I(state(name))})
# Build outer court first, preserving interior template, which is shifted into it.
for x in range(43):
 for z in range(43):
  put(x,0,z,'stone_bricks' if (x+z)%5 else 'polished_andesite')
  for y in range(1,15):put(x,y,z,'air')
for x in range(2,41):
 for z in range(2,41):
  if x in (2,40) or z in (2,40):
   for y in range(1,9):put(x,y,z,'mossy_stone_bricks' if (x*5+y*7+z)%31==0 else 'stone_bricks')
   if (x+z)%2==0:put(x,9,z,'stone_bricks')
for tx,tz in ((2,2),(36,2),(2,36),(36,36)):
 for x in range(tx,tx+5):
  for z in range(tz,tz+5):
   if x in (tx,tx+4) or z in (tz,tz+4):
    for y in range(1,13):put(x,y,z,'polished_tuff' if y in (1,7,11) else 'stone_bricks')
    if (x+z)%2==0:put(x,13,z,'chiseled_stone_bricks')
for x in range(20,23):
 for z in range(0,12):
  put(x,0,z,'polished_andesite')
  for y in range(1,5):put(x,y,z,'air')
# Preserve the original hall and its villagers, omit old street connectors.
streets=[]
for b in f['blocks']:
 x,y,z=map(int,b['pos']);name=str(pal[int(b['state'])]['Name'])
 if name=='minecraft:jigsaw' and str(b.get('nbt',{}).get('name',''))=='minecraft:street':streets.append(b);continue
 b['pos']=L[I]([x+9,y,z+9]);blocks[x+9,y,z+9]=b
# Remove the old gold-and-red stack; make a wide, low-backed throne.
for y in (1,2,3):put(21,y,26,'air')
for x in range(19,24):
 put(x,1,26,'polished_blackstone_bricks')
 for y in (2,3):put(x,y,27,'dark_oak_planks')
for x in range(20,23):put(x,2,26,'red_carpet')
for x in (19,23):put(x,2,26,'gold_block')
# Replace road connectors at the edge of the enlarged courtyard.
for b in streets:
 x,y,z=map(int,b['pos']);x=0 if x==0 else 42 if x==24 else 21;z=0 if z==0 else 42 if z==24 else 21
 b['pos']=L[I]([x,y,z]);blocks[x,y,z]=b
for ent in f['entities']:
 ent['pos'][0]=n.Double(float(ent['pos'][0])+9);ent['pos'][2]=n.Double(float(ent['pos'][2])+9)
 ent['blockPos'][0]=I(int(ent['blockPos'][0])+9);ent['blockPos'][2]=I(int(ent['blockPos'][2])+9)
f['size']=L[I]([43,21,43]);f['blocks']=L[C](list(blocks.values()));f.save(p,gzipped=True)
# Only plains villages use a castle center; restore vanilla town centers elsewhere.
for biome in ('desert','savanna','snowy','taiga'):
 p=R/f'data/minecraft/worldgen/template_pool/village/{biome}/town_centers.json'
 if p.exists():p.unlink()
for biome in ('plains','desert','savanna','snowy','taiga'):
 p=R/f'data/minecraft/worldgen/structure/village_{biome}.json';v=json.loads(p.read_text());v['size']=20;v['max_distance_from_center']=116;p.write_text(json.dumps(v,indent=2)+'\n')
# A small stone-house variant, retaining the native village's roof, doorway, bed and connectors.
p=R/'data/halloween/structure/village/plains/houses/builder_house.nbt';f=n.load(p)
for b in f['blocks']:
 st=f['palette'][int(b['state'])];name=str(st['Name'])
 if name.endswith('_planks'):
  if 'minecraft:stone_bricks' not in [str(x['Name']) for x in f['palette']]:f['palette'].append(C({'Name':S('minecraft:stone_bricks')}))
  b['state']=I(next(i for i,v in enumerate(f['palette']) if str(v['Name'])=='minecraft:stone_bricks'))
p=R/'data/halloween/structure/village/plains/houses/stone_house.nbt';f.save(p,gzipped=True)
p=R/'data/minecraft/worldgen/template_pool/village/plains/houses.json';v=json.loads(p.read_text());v['elements'].append({'element':{'element_type':'minecraft:legacy_single_pool_element','location':'halloween:village/plains/houses/stone_house','processors':'minecraft:empty','projection':'rigid'},'weight':5});p.write_text(json.dumps(v,indent=2)+'\n')

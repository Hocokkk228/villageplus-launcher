from pathlib import Path
import json, nbtlib as n
r=Path('src/main/resources/data/halloween'); blocks={}; W=D=19; H=15
S=n.String;I=n.Int;C=n.Compound;L=n.List

def put(x,y,z,name,props=None):
 if 0<=x<W and 0<=z<D and 0<=y<H:blocks[x,y,z]=(name if ':' in name else 'minecraft:'+name,props or {})
def stair(x,y,z,name,facing):put(x,y,z,name+'_stairs',{'facing':facing,'half':'bottom','shape':'straight','waterlogged':'false'})
def slab(x,y,z,name):put(x,y,z,name+'_slab',{'type':'bottom','waterlogged':'false'})
# A stone courtyard surrounds the main two-storey building.
for x in range(W):
 for z in range(D):
  put(x,0,z,'stone_bricks' if (x*13+z*17)%11 else 'mossy_stone_bricks')
  for y in range(1,H):put(x,y,z,'air')
for x in range(2,17):
 for z in range(4,17):
  put(x,0,z,'polished_andesite')
  for y in range(1,9):
   if x in (2,16) or z in (4,16):
    trim=x in (2,16) and z in (4,16) or y in (1,4,8)
    block='polished_andesite' if trim else ('cracked_stone_bricks' if (x*7+z*3+y*11)%31==0 else 'stone_bricks')
    put(x,y,z,block)
for y in (2,6):
 for x in (5,9,13):
  for z in (4,16):put(x,y,z,'glass_pane');put(x,y+1,z,'glass_pane')
 for z in (7,10,13):
  for x in (2,16):put(x,y,z,'glass_pane');put(x,y+1,z,'glass_pane')
# Front portico and framed entrance.
for x in (5,13):
 for y in range(1,6):put(x,y,2,'polished_andesite')
for x in range(4,15):
 for z in (1,2,3):slab(x,6,z,'stone_brick')
for y,half in ((1,'lower'),(2,'upper')):put(9,y,4,'spruce_door',{'facing':'south','half':half,'hinge':'left','open':'false','powered':'false'})
for x in (7,11):put(x,4,3,'lantern',{'hanging':'true','waterlogged':'false'});put(x,5,3,'stone_bricks')
# Wide pitched roof with overhangs and a central crest.
for layer in range(9):
 y=9+layer//2
 for z in range(3,18):
  for x,face in ((1+layer,'east'),(17-layer,'west')):
   if layer==8:slab(x,y,z,'deepslate_tile')
   elif layer%2:put(x,y,z,'deepslate_tiles')
   else:stair(x,y,z,'deepslate_tile',face)
 for x in range(2+layer,17-layer):
  for z in (4,16):put(x,y,z,'stone_bricks')
# Gable crest and two narrow corner turrets.
for x in range(7,12):
 for y in range(9,12):put(x,y,4,'chiseled_stone_bricks' if y==10 else 'stone_bricks')
put(9,11,4,'gold_block')
for x0 in (2,13):
 for x in range(x0,x0+4):
  for z in range(12,16):
   for y in range(8,12):
    if x in (x0,x0+3) or z in (12,15):put(x,y,z,'polished_andesite')
   put(x,12,z,'deepslate_tiles')
   if (x in (x0,x0+3) or z in (12,15)) and (x+z)%2==0:put(x,13,z,'stone_bricks')
# Mezzanine with stair opening; clear vertical headroom.
for x in range(3,16):
 for z in range(5,16):
  if not (x in (3,4) and 6<=z<=10):put(x,4,z,'spruce_planks')
for i in range(4):
 stair(3,1+i,6+i,'stone_brick','south')
 for y in range(2+i,7):put(3,y,6+i,'air')
# Reception desk, six storage barrels, treasury records and living quarters.
for x in (7,8,10,11):put(x,1,8,'spruce_slab',{'type':'bottom','waterlogged':'false'})
put(9,1,10,'lectern',{'facing':'south','has_book':'false','powered':'false'})
for side in (-5,-3,-1,1,3,5):put(9+side,1,14,'barrel',{'facing':'up','open':'false'})
for x in (5,6,12,13):put(x,1,12,'bookshelf')
for z,part in ((12,'foot'),(13,'head')):put(5,5,z,'white_bed',{'facing':'south','part':part,'occupied':'false'})
for x in (10,11,12,13):put(x,5,14,'bookshelf')
put(13,5,12,'chest',{'facing':'north','type':'single','waterlogged':'false'})
for x in (7,8):stair(x,5,7,'spruce','south')
for y in (3,7):
 for x in (6,12):put(x,y,10,'lantern',{'hanging':'true','waterlogged':'false'});put(x,y+1,10,'spruce_planks' if y==3 else 'stone_bricks')
# File as an actual Minecraft structure and as the construction system's template.
palette=[];lookup={};data=[]
for (x,y,z),(name,props) in sorted(blocks.items(),key=lambda b:(b[0][1],b[0][0],b[0][2])):
 key=(name,tuple(sorted(props.items())))
 if key not in lookup:lookup[key]=len(palette);palette.append({'name':name,'properties':props})
 data.append([x,y,z,lookup[key]])
(r/'era_templates/stone_treasury.json').write_text(json.dumps({'size':[W,H,D],'palette':palette,'blocks':data},separators=(',',':')),encoding='utf8')
np=[]
for state in palette:
 q=C({'Name':S(state['name'])})
 if state['properties']:q['Properties']=C({k:S(v) for k,v in state['properties'].items()})
 np.append(q)
target=r/'structure/village/shared';target.mkdir(parents=True,exist_ok=True)
n.File({'DataVersion':I(4671),'size':L[I]([W,H,D]),'palette':L[C](np),'blocks':L[C]([C({'pos':L[I](p[:3]),'state':I(p[3])}) for p in data]),'entities':L[C]([])},gzipped=True).save(target/'stone_treasury.nbt',gzipped=True)

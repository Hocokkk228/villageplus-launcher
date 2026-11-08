"""Seal the cathedral envelope and furnish two residential wings; preserve the central great hall."""
from pathlib import Path
import json
path=Path(__file__).resolve().parents[1]/'src/main/resources/data/halloween/era_templates/cologne_cathedral.json'
d=json.loads(path.read_text());palette=d['palette'];lookup={(p['name'],tuple(sorted(p['properties'].items()))):i for i,p in enumerate(palette)};blocks={tuple(b[:3]):b[3] for b in d['blocks']}
def put(x,y,z,name,**props):
 key=('minecraft:'+name,tuple(sorted(props.items())))
 if key not in lookup:lookup[key]=len(palette);palette.append({'name':key[0],'properties':props})
 blocks[x,y,z]=lookup[key]
def box(x0,y0,z0,x1,y1,z1,name,**props):
 for x in range(x0,x1+1):
  for y in range(y0,y1+1):
   for z in range(z0,z1+1):put(x,y,z,name,**props)
def door(x,y,z,facing='west'):
 for n,half in enumerate(('lower','upper')):put(x,y+n,z,'dark_oak_door',facing=facing,half=half,hinge='left',open='false',powered='false')
def bed(x,y,z,color='red'):
 put(x,y,z,color+'_bed',facing='south',part='foot',occupied='false');put(x,y,z+1,color+'_bed',facing='south',part='head',occupied='false')
# The nave roof ends used to be open to the sky.
for z in (14,75):
 for x in range(24,41):
  for y in range(30,40-abs(x-32)):
   put(x,y,z,'polished_andesite' if y==39-abs(x-32) else 'stone_bricks')
 for x in list(range(12,24))+list(range(41,53)):
  roof=24-(abs(x-32)-9)//2
  for y in range(17,roof+1):put(x,y,z,'light_blue_stained_glass' if y in (19,20) and x%4 in (1,2) else 'stone_bricks')
# Close the gaps between the high nave roof and both end walls.
for z in (14,75):
 for x in range(24,41):
  for y in range(17,39-abs(x-32)):
   if (x,y,z) not in blocks or palette[blocks[x,y,z]]['name']=='minecraft:air':
    put(x,y,z,'light_blue_stained_glass' if 21<=y<=28 and abs(x-32)%4 in (1,2) else 'stone_bricks')
# The cross roof projects beyond the nave. Close the exposed north and south walls below it.
for z in (39,56):
 for x in list(range(3,24))+list(range(41,62)):
  for y in range(20,39-abs(z-47)):
   if (x,y,z) not in blocks or palette[blocks[x,y,z]]['name']=='minecraft:air':
    put(x,y,z,'light_blue_stained_glass' if 22<=y<=27 and x%5 in (2,3) else 'stone_bricks')
# Fully connected private rooms, two upper floors and unobstructed longitudinal corridors.
for lo,hi,wall in ((13,23,20),(41,51,44)):
 # Clear the old nave buttresses before subdividing the wings; otherwise rooms retain solid masonry inside.
 box(lo,1,19,hi,21,70,'air')
 box(lo,21,19,hi,21,70,'stone_bricks')
 outer=lo if lo==13 else hi
 for z in range(19,71):
  for y in range(1,21):
   put(outer,y,z,'light_blue_stained_glass' if y%7 in (3,4) and z%5 in (2,3) else 'stone_bricks')
 for floor in (0,7,14):
  box(lo,floor,19,hi,floor,70,'spruce_planks')
  for z in (19,32,46,58,70):
   box(lo,floor+1,z,hi,floor+6,z,'stone_bricks')
   # Corridor openings link rooms at every floor.
   cx=22 if lo==13 else 42;box(cx-1,floor+1,z,cx+1,floor+3,z,'air')
  box(wall,floor+1,20,wall,floor+6,69,'stone_bricks')
  for z in (25,39,51,64):door(wall,floor+1,z,'east' if lo==13 else 'west')
  for z in (25,39,51,64):
   cx=16 if lo==13 else 48
   put(cx,floor+5,z,'air')
   put(cx,floor+1,z-3,'bookshelf');put(cx,floor+2,z-3,'bookshelf')
   put(cx,floor+1,z+3,'spruce_stairs',facing='north',half='bottom',shape='straight',waterlogged='false')
   if floor==0:put(cx+1,floor+1,z-3,'crafting_table')
   else:bed(cx,floor+1,z-1,'purple' if floor==7 and lo==41 and z==64 else 'red')
 # Straight stair flights inside each wing. Each tread has three blocks of headroom.
 cx=22 if lo==13 else 42
 for floor,z0 in ((0,20),(7,34)):
  for n in range(1,9):
   for x in ((22,23) if lo==13 else (41,42)):
    z=z0+n;put(x,floor+n-1,z,'stone_brick_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
    box(x,floor+n,z,x,floor+n+3,z,'air')
# Direct nave-to-wing passages and furnished private rooms on all three levels.
for x0,x1 in ((23,24),(40,41)):
 for z in (24,38,53,65):
  box(x0,1,z,x1,3,z+1,'air')
for x in (16,48):
 for floor in (0,7,14):
  for z in (25,39,51,64):
   for dz in (0,1):put(x-1,floor+1,z+dz,'red_carpet' if x==16 else 'blue_carpet')
   put(x+1,floor+1,z+2,'dark_oak_stairs',facing='west',half='bottom',shape='straight',waterlogged='false')
# King’s suite: bed head is (48,8,64), used by the HOME migration.
put(46,8,60,'bookshelf');put(46,9,60,'bookshelf');put(50,8,60,'cartography_table')
box(46,8,68,50,8,68,'dark_oak_stairs',facing='north',half='bottom',shape='straight',waterlogged='false')
# The nave chandelier hangs from the actual ridge, with two connected octagonal tiers.
def chain(x,y,z):put(x,y,z,'waxed_copper_chain',axis='y',waterlogged='false')
def lamp(x,y,z):put(x,y,z,'waxed_copper_lantern',hanging='true',waterlogged='false')
for y in range(9,39):chain(32,y,47)
for y,r in ((9,5),(13,3)):
 for dx in range(-r,r+1):
  for dz in range(-r,r+1):
   if (r<=abs(dx)+abs(dz)<=r+2 and (max(abs(dx),abs(dz))==r or abs(dx)+abs(dz)>=r+1)) or dx==0 or dz==0:
    put(32+dx,y,47+dz,'waxed_cut_copper' if dx==0 or dz==0 else 'waxed_cut_copper_slab',**({} if dx==0 or dz==0 else {'type':'double','waterlogged':'false'}))
    if (max(abs(dx),abs(dz))==r and (dx==0 or dz==0 or abs(dx)==2 or abs(dz)==2)):
     chain(32+dx,y-1,47+dz);lamp(32+dx,y-2,47+dz)
# Pendant lights in each occupied room and corridor, anchored to the next solid ceiling.
def pendant(x,y,z,max_y):
 for ceiling in range(y+2,max_y+1):
  state=palette[blocks.get((x,ceiling,z),0)]['name'] if (x,ceiling,z) in blocks else 'minecraft:air'
  if state in ('minecraft:spruce_planks','minecraft:stone_bricks','minecraft:deepslate_tiles','minecraft:polished_andesite'):
   for height in range(y+1,ceiling):chain(x,height,z)
   lamp(x,y,z)
   return
for x in (16,22,42,48):
 for floor in (0,7,14):
  for z in ((30,44,55,67) if x in (22,42) else (24,37,50,63)):
   pendant(x,floor+3,z,25)
# Low pendants along the nave and transept light the walking floor independently of the great chandelier.
for x in (27,37):
 for z in range(11,84,8):
  pendant(x,4,z,40)
for x in (7,15,23,41,49,57):
 pendant(x,4,47,40)
# Side aisles and the ends of the transept must also remain safe after sunset.
for x in (9,55):
 for z in (24,31,59,66):pendant(x,3,z,30)
for x in (13,51):pendant(x,3,71,30)
for x in (6,12,52,58):
 for z in (41,54):pendant(x,4,z,40)
# Courtyard and public square extend in front of the western portals.
for x in range(8,57):
 for z in range(-16,5):
  put(x,0,z,'polished_andesite' if x%8==0 or z%7==0 else 'stone_bricks')
for x in (10,54):
 for z in (-13,-3):
  for y in range(1,4):put(x,y,z,'stone_brick_wall',up='true',north='none',south='none',east='none',west='none',waterlogged='false')
  put(x,4,z,'lantern',hanging='false',waterlogged='false')
for x in (15,49):
 for z in range(-11,-5):put(x,1,z,'spruce_stairs',facing='east' if x==15 else 'west',half='bottom',shape='straight',waterlogged='false')
# Small central ornamental plinth leaves a wide route to all three doors.
box(30,1,-10,34,1,-6,'polished_andesite');box(31,2,-9,33,2,-7,'chiseled_stone_bricks');put(32,3,-8,'lantern',hanging='false',waterlogged='false')
box(30,52,45,34,52,49,'polished_deepslate')
d['blocks']=[list(pos)+[state] for pos,state in sorted(blocks.items())];d['palette']=palette;path.write_text(json.dumps(d,separators=(',',':')))
print('Palace template:',len(blocks),'positions, closed gables, furnished wings, stairs and square')
# Standalone structure export includes the forecourt, hence its origin is 16 blocks north of the runtime castle anchor.
import struct,gzip
S=lambda v:struct.pack('>H',len(v.encode()))+v.encode()
def tag(t,name,data):return bytes([t])+S(name)+data
def integer(v):return struct.pack('>i',v)
def ilist(values):return bytes([3])+integer(len(values))+b''.join(integer(v)for v in values)
np=[]
for p in palette:
 row=tag(8,'Name',S(p['name']))
 if p['properties']:row+=tag(10,'Properties',b''.join(tag(8,k,S(v))for k,v in p['properties'].items())+b'\0')
 np.append(row+b'\0')
nb=[]
for (x,y,z),state in sorted(blocks.items()):nb.append(tag(9,'pos',ilist([x,y,z+16]))+tag(3,'state',integer(state))+b'\0')
root=tag(3,'DataVersion',integer(4671))+tag(9,'size',ilist([65,100,104]))+tag(9,'palette',bytes([10])+integer(len(np))+b''.join(np))+tag(9,'blocks',bytes([10])+integer(len(nb))+b''.join(nb))+tag(9,'entities',bytes([10])+integer(0))+b'\0'
export=path.parent.parent/'structure/gothic/cologne_cathedral.nbt';export.parent.mkdir(parents=True,exist_ok=True);export.write_bytes(gzip.compress(tag(10,'',root),mtime=0))

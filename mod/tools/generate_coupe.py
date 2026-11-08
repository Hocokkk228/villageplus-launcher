"""Sleeper carriage within the existing coach envelope; full-height side corridor."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
path=root/'src/main/resources/data/halloween/rail_models'
coach=json.loads((path/'coach.json').read_text());parts=[]
for p in coach:
 b=p['box'];interior=not (b[3]<=-1.45 or b[0]>=1.45) and b[1]>=1.5 and b[4]<=3.8 and b[2]>-5 and b[5]<5
 if p['block']=='minecraft:light_blue_stained_glass':p['block']='minecraft:glass'
 if not interior and not (p['block']=='minecraft:polished_deepslate' and b[1]>=4):
  if b[1]>=1.25 and b[4]<=1.5 or p['block']=='minecraft:glowstone':p['group']='interior'
  parts.append(p)
def box(block,x,y,z,X,Y,Z,solid=True,group='interior'):
 parts.append(dict(block='minecraft:'+block,box=[x,y,z,X,Y,Z],solid=solid,group=group))
# Continuous weather-tight arch with an illuminated wooden ceiling below it.
box('oak_planks',-1.5,4.08,-6.7,1.5,4.15,6.7)
for i in range(-3,4):
 x=i*.5;top=4.15+(.6 if abs(i)<2 else .35 if abs(i)==2 else 0)
 box('polished_deepslate',x-.25,4.15,-6.95,x+.25,top+.18,6.95,True,'roof')
# Three private compartments, each with two facing lower berths and upper bunks.
# The open doorway is .95m wide; the corridor is .95m wide, with no low luggage shelf.
for i,c in enumerate((-3.15,0,3.15)):
 for z in (c-1.48,c+1.48):box('stripped_spruce_wood',-1.5,1.5,z-.04,.53,4.13,z+.04)
 for za,zb in ((c-1.45,c-.48),(c+.48,c+1.45)):
  box('spruce_planks',.48,1.5,za,.57,4.13,zb)
 box('spruce_planks',.48,3.58,c-.48,.57,4.13,c+.48)
 box('gold_block',.40,2.32,c+.35,.67,2.53,c+.45,False,'door_handle_'+str(i))
 # Recessed sliding leaf: closed until a player opens this compartment.
 box('spruce_planks',.49,1.52,c-.46,.55,2.62,c+.46,True,'compartment_'+str(i))
 box('light_blue_stained_glass',.50,2.62,c-.40,.54,3.49,c+.40,True,'compartment_'+str(i))
 box('gold_block',.55,2.35,c+.29,.62,2.5,c+.37,False,'compartment_'+str(i))
 for side in (-1,1):
  z=c+side*1.04
  box('dark_oak_planks',-1.46,1.5,z-.38,.43,1.78,z+.38)
  box('red_wool',-1.44,1.78,z-.36,.41,1.92,z+.36)
  box('white_wool',-1.43,1.925,z-.30,-1.05,1.99,z+.30,False)
  box('dark_oak_planks',-1.46,3.14,z-.38,.43,3.26,z+.38)
  box('red_wool',-1.44,3.26,z-.36,.41,3.38,z+.36)
  box('white_wool',-1.43,3.385,z-.3,-1.05,3.45,z+.3,False)
 # Small table beside the window leaves a gap between facing berths.
 box('dark_oak_planks',-1.46,2.24,c-.30,-.91,2.35,c+.30)
 box('glowstone',-.56,3.96,c-.14,-.30,4.10,c+.14,False)
 box('oak_planks',-.74,3.78,c-.25,-.14,3.84,c+.25,False)
 box('dark_oak_planks',.91,3.86,c-.03,.97,4.08,c+.03,False)
 box('gold_block',.80,3.72,c-.14,1.08,3.77,c+.14,False)
 box('glowstone',.84,3.78,c-.10,1.04,3.91,c+.10,False)
for z in (-5.0,5.0):box('glowstone',.89,3.96,z-.14,1.14,4.1,z+.14,False)
# Raise the compartment ceiling and upper berths without changing rail gauge.
# Widen the centre aisle between facing berths; two neighbours no longer share
# touching hitboxes. Geometry remains centred on the existing track path.
for p in parts:
 b=p['box'];zmid=(b[2]+b[5])/2
 # Thin inherited window spans must meet the enlarged walls and headers.
 for k in (1,4):
  if b[k]>=3.10:b[k]+=1.15
  elif b[k]>2.4:b[k]+=(b[k]-2.4)/.7*1.15
 if p.get('group','').startswith('compartment_'):
  # Door top follows full-height doorway; handle stays at hand height.
  pass
 # Lower/upper berths move apart by .13m, leaving a 1.55m standing aisle.
 if p['block'] in ('minecraft:red_wool','minecraft:white_wool') or p['block']=='minecraft:dark_oak_planks' and b[0]>=-1.47 and b[3]<=.44 and abs((b[5]-b[2])-.76)<.02:
  center=round(zmid/3.15)*3.15;shift=.13 if zmid>center else -.13;b[2]+=shift;b[5]+=shift
 # Remove inherited luggage shelves in the vestibule and hanging full walls
 # left by a seated coach layout; keep all circulation at least 1.8m clear.
parts=[p for p in parts if not (p.get('group')=='interior' and p['block']=='minecraft:oak_planks' and p['box'][1]>3.5 and p['box'][1]<4.1 and p['box'][0]<0 and p['box'][3]<1.5 and p['box'][5]-p['box'][2]>1)]
(path/'sleeper_coach.json').write_text(json.dumps(parts,separators=(',',':')))
print('Sleeper:',len(parts),'parts, 3 compartments, 12 berths')

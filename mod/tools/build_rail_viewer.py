from pathlib import Path
import json,math,zipfile,base64,io
from PIL import Image
R=Path(__file__).resolve().parents[1];D=R/'src/main/resources/data/halloween';A=R/'src/main/resources/assets/halloween'
names=[];models={}
def mat(block):
 if block not in names:names.append(block)
 return names.index(block)
def part(block,b,yaw=0):return [mat(block),*[round(float(v),4) for v in b],round(yaw,5)]
for name in ('locomotive','coach'):
 models[name]=[part(p['block'].split(':')[-1],p['box']) for p in json.loads((D/'rail_models'/f'{name}.json').read_text())]
models['train']=models['locomotive']+[p[:3]+[p[3]-15]+p[4:6]+[p[6]-15]+p[7:] for p in models['coach']]+[p[:3]+[p[3]-30]+p[4:6]+[p[6]-30]+p[7:] for p in models['coach']]
for name in ('railway_station','rail_foundry'):
 data=json.loads((D/'era_templates'/f'{name}.json').read_text());vox={(p[0],p[1],p[2]):p[3] for p in data['blocks'] if data['palette'][p[3]]['name']!='minecraft:air'};parts=[]
 for (x,y,z),idx in vox.items():
  if all((x+a,y+b,z+c) in vox for a,b,c in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))):continue
  entry=data['palette'][idx];block=entry['name'].split(':')[-1];props=entry['properties'];boxes=[[0,0,0,1,1,1]]
  if block.endswith('_slab'):boxes=[[0,.5 if props.get('type')=='top' else 0,0,1,1 if props.get('type')=='top' else .5,1]]
  if block.endswith('_stairs'):boxes=[[0,0,0,1,.5,1],[0,.5,.5,1,1,1]]
  if block=='lantern':boxes=[[.31,.25,.31,.69,.75,.69]]
  if block=='iron_bars':boxes=[[.46,0,0,.54,1,1],[0,0,.46,1,1,.54]]
  if block=='chain':boxes=[[.44,0,.44,.56,1,.56]]
  if block=='gothic_chair':
   chair=json.loads((A/'models/block/gothic_chair.json').read_text());boxes=[[v/16 for v in e['from']+e['to']] for e in chair.get('elements',[])];block='dark_oak_planks'
  for box in boxes:parts.append(part(block,[x+box[0],y+box[1],z+box[2],x+box[3],y+box[4],z+box[5]]))
 models[name]=parts
rail=[]
for i in range(50):
 a=i/50*math.pi/2;b=(i+1)/50*math.pi/2
 for lane in (-4,4):
  radius=22+lane;x0=radius*math.sin(a);z0=radius*(1-math.cos(a));x1=radius*math.sin(b);z1=radius*(1-math.cos(b));yaw=math.atan2(x1-x0,z1-z0);length=math.hypot(x1-x0,z1-z0)
  def rotated(material,lo,hi):
   cx=(lo[0]+hi[0])/2;cz=(lo[2]+hi[2])/2;cx,cz=x0+cx*math.cos(yaw)+cz*math.sin(yaw),z0-cx*math.sin(yaw)+cz*math.cos(yaw);w=(hi[0]-lo[0])/2;d=(hi[2]-lo[2])/2;rail.append(part(material,[cx-w,lo[1],cz-d,cx+w,hi[1],cz+d],yaw))
  rotated('gravel',[-1.6,-.3,0],[1.6,0,length+.01])
  if i%2==0:rotated('iron_block',[-1.3,0,-.14],[1.3,.10,.14])
  for side in (-.75,.75):rotated('iron_block',[side-.055,.1,0],[side+.055,.22,length+.03])
models['track']=rail
for name in ('rail_hammer','rail_profile'):
 data=json.loads((A/'models/item'/f'{name}.json').read_text());models[name]=[]
 for e in data['elements']:
  texture=next(iter(e['faces'].values()))['texture'].lstrip('#');texture=data['textures'].get(texture,'minecraft:block/iron_block').split('/')[-1];models[name].append(part(texture,[v/16 for v in e['from']+e['to']]))
models['sleeper']=[part('iron_block',[-1.3,0,-.15,1.3,.125,.15])]
alias={'waxed_cut_copper':'cut_copper','stripped_dark_oak_wood':'stripped_dark_oak_log','magma_block':'magma','barrel':'barrel_side','lantern':'lantern','anvil':'anvil_top','chain':'chain','gothic_chair':'dark_oak_planks','grindstone':'grindstone_side','blast_furnace':'blast_furnace_front','smithing_table':'smithing_table_top','lectern':'lectern_sides','smooth_sandstone':'sandstone_top','cut_sandstone':'cut_sandstone','smooth_quartz':'quartz_block_side','stone_brick_stairs':'stone_bricks','sandstone_stairs':'sandstone','deepslate_tile_slab':'deepslate_tiles'}
z=zipfile.ZipFile(Path.home()/'.gradle/caches/fabric-loom/1.21.11/minecraft-client.jar');textures=[]
for name in names:
 tex=alias.get(name,name)
 try:image=Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/'+tex+'.png'))).convert('RGBA');image=image.crop((0,0,image.width,image.width))
 except KeyError:image=Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/stone.png'))).convert('RGBA')
 out=io.BytesIO();image.save(out,format='PNG');textures.append('data:image/png;base64,'+base64.b64encode(out.getvalue()).decode())
DATA=json.dumps(dict(names=names,textures=textures,models=models),separators=(',',':'))
p=R/'preview/railway/railway-models.html';source=(R/'tools/railway-viewer-fragment.html').read_text();p.write_text(source.replace('__RAIL_DATA__',DATA))
assert p.stat().st_size<1000000,p.stat().st_size
print('Viewer bytes',p.stat().st_size,'parts',{k:len(v) for k,v in models.items()})

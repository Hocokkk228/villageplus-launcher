from pathlib import Path
import glob,zipfile,gzip,io,json,nbtlib as n
from PIL import Image
R=Path('src/main/resources'); jar=glob.glob('.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged*/*/*.jar')[0]
with zipfile.ZipFile(jar) as z:
 for biome in ('plains','desert','savanna','snowy','taiga'):
  candidates=[]
  for index in range(1,9):
   source=f'village/{biome}/houses/{biome}_small_house_{index}'
   try:data=z.read(f'data/minecraft/structure/{source}.nbt')
   except KeyError:continue
   f=n.File.parse(io.BytesIO(gzip.decompress(data)))
   states=[str(t['Name']) for t in f['palette']];blocks={tuple(map(int,b['pos'])):b for b in f['blocks']}; size=list(map(int,f['size']))
   for pos,b in blocks.items():
    x,y,zz=pos; name=states[int(b['state'])];below=blocks.get((x,y-1,zz));above=blocks.get((x,y+1,zz))
    if x<1 or zz<1 or x>=size[0]-1 or zz>=size[2]-1 or not below or not above:continue
    floor=states[int(below['state'])]; overhead=states[int(above['state'])]
    if name=='minecraft:air' and overhead=='minecraft:air' and (floor.endswith('_planks') or floor.endswith('terracotta') or 'sandstone' in floor or floor.endswith('snow_block') or floor.endswith('_log')):
     candidates.append(pos)
   if candidates:break
  if not candidates:
   print('No safe station space in',biome);continue
  pos=candidates[0];f['palette'].append(n.Compound({'Name':n.String('halloween:builder_workbench')}));blocks[pos]['state']=n.Int(len(f['palette'])-1)
  path=R/f'data/halloween/structure/village/{biome}/houses/builder_house.nbt';path.parent.mkdir(parents=True,exist_ok=True);f.save(path,gzipped=True)
  pool=json.loads(z.read(f'data/minecraft/worldgen/template_pool/village/{biome}/houses.json'))
  pool['elements'].append({'element':{'element_type':'minecraft:legacy_single_pool_element','location':f'halloween:village/{biome}/houses/builder_house','processors':'minecraft:empty','projection':'rigid'},'weight':5})
  p=R/f'data/minecraft/worldgen/template_pool/village/{biome}/houses.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(pool,indent=2)+'\n',encoding='utf8')
  print(biome,'station',pos)
Path('preview').mkdir(exist_ok=True)
im=Image.open(R/'assets/halloween/textures/entity/villager/profession/builder.png').convert('RGBA'); out=Image.new('RGBA',im.size,(65,66,74,255));out.alpha_composite(im);out.resize((768,768),Image.Resampling.NEAREST).convert('RGB').save('preview/builder-texture.png')

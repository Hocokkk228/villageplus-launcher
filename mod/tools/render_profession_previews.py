from pathlib import Path
from PIL import Image, ImageDraw
ns={}
src=Path('tools/preview_village_structures.py').read_text(encoding='utf8').split('for biome in')[0]
exec(src,ns)
colors=ns['colors']; colors.update({'brick_stairs':'#a65244','brick_slab':'#a65244','tuff_brick_stairs':'#777e70','tuff_brick_slab':'#777e70','deepslate_tile_stairs':'#4e5058','deepslate_tile_slab':'#4e5058','oak_planks':'#bb9660','oak_log':'#85663f','spruce_log':'#6c5039','dark_oak_log':'#49382c','glass_pane':'#b9d9dd'})
roles=['builder','farmer','armorer','weaponsmith','toolsmith','mason','fletcher','fisherman','shepherd','butcher','leatherworker','librarian','cartographer','cleric','guard','treasurer']
out=Path('preview/profession_houses');out.mkdir(parents=True,exist_ok=True)
ns['out']=out
for role in roles:
 for variant in range(3):
  name=f'{role}_{variant+1}.png'
  ns['draw'](Path(f'src/main/resources/data/halloween/structure/village/shared/houses/profession_{role}_{variant}.nbt'),name)
  im=Image.open(out/name); bg=Image.new('RGB', im.size, '#c5dce1'); from PIL import ImageChops
  box=ImageChops.difference(im,bg).getbbox(); im=im.crop(box); im.thumbnail((410,300)); canvas=Image.new('RGB',(430,330),'#c5dce1');canvas.paste(im,((430-im.width)//2,15));ImageDraw.Draw(canvas).text((12,310),f'{role} / {variant+1}',fill='#202830');canvas.save(out/name)
for page in range(4):
 sheet=Image.new('RGB',(1290,1320),'#c5dce1')
 for row,role in enumerate(roles[page*4:page*4+4]):
  for v in range(3):sheet.paste(Image.open(out/f'{role}_{v+1}.png'),(v*430,row*330))
 sheet.save(out/f'houses-{page+1}.png')

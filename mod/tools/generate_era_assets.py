"""Pixel-art statistics ledger and build palettes copied from vanilla house templates."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,zipfile,io,nbtlib
import shutil
root=Path('src/main/resources');assets=root/'assets/halloween'
for kind in ['villager','zombie_villager']:
 folder=assets/f'textures/entity/{kind}/profession'
 shutil.copy2(folder/'deputy.png',folder/'treasurer.png')
 if (folder/'deputy.png.mcmeta').exists():shutil.copy2(folder/'deputy.png.mcmeta',folder/'treasurer.png.mcmeta')
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
d.polygon([(5,6),(21,3),(27,7),(27,27),(11,30),(5,26)],fill='#252d35')
d.polygon([(7,7),(21,5),(25,8),(25,26),(11,28),(7,25)],fill='#335b58')
d.polygon([(10,8),(22,6),(24,8),(24,25),(11,27)],fill='#e6d6ab')
d.polygon([(11,10),(22,8),(22,23),(12,25)],fill='#f5e6bd')
d.line((8,8,8,25),fill='#81a39a');d.line((10,8,10,26),fill='#ba9650')
for y in (12,15,18,21):d.line((13,y,20,y-1),fill='#ad9873')
d.rectangle((14,16,15,21),fill='#688e75');d.rectangle((17,13,18,20),fill='#b68c40');d.rectangle((20,10,21,20),fill='#3e6967')
d.polygon([(21,23),(26,20),(29,23),(26,27),(22,27)],fill='#67502d');d.polygon([(22,23),(26,21),(28,23),(26,26),(23,26)],fill='#e4b95a');d.point((25,22),fill='#fff0b5')
d.line((6,25,10,28),fill='#152f31');d.line((12,27,22,25),fill='#b49a61')
(assets/'textures/item').mkdir(parents=True,exist_ok=True);im.save(assets/'textures/item/village_statistics.png')
for folder,data in [('models/item',{'parent':'minecraft:item/generated','textures':{'layer0':'halloween:item/village_statistics'}}),('items',{'model':{'type':'minecraft:model','model':'halloween:item/village_statistics'}})]:
 (assets/folder/'village_statistics.json').write_text(json.dumps(data,indent=2))
for lang,vals in [('ru_ru',{'item.halloween.village_statistics':'Статистика','entity.minecraft.villager.halloween.treasurer':'Казначей','entity.halloween.treasurer':'Казначей'}),('en_us',{'item.halloween.village_statistics':'Village Statistics','entity.minecraft.villager.halloween.treasurer':'Treasurer','entity.halloween.treasurer':'Treasurer'})]:
 p=assets/f'lang/{lang}.json';data=json.loads(p.read_text(encoding='utf8'));data.update(vals);p.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')
jar=next(Path('.gradle/loom-cache/minecraftMaven').rglob('*.jar'));out=root/'data/halloween/era_templates';out.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(jar) as z:
 for name in ['plains_medium_house_1','plains_medium_house_2','plains_big_house_1']:
  f=nbtlib.File.parse(io.BytesIO(__import__('gzip').decompress(z.read(f'data/minecraft/structure/village/plains/houses/{name}.nbt'))))
  palette=[{'name':str(s['Name']),'properties':{str(k):str(v) for k,v in s.get('Properties',{}).items()}} for s in f['palette']]
  data={'size':list(map(int,f['size'])),'palette':palette,'blocks':[[*map(int,b['pos']),int(b['state'])] for b in f['blocks']]}
  (out/f'{name}.json').write_text(json.dumps(data,separators=(',',':')))

from pathlib import Path
from PIL import Image,ImageDraw
import json,random
r=Path('src/main/resources/assets/halloween');p=r/'textures/item';p.mkdir(parents=True,exist_ok=True)
# Weathered surveillance monitor with glass reflections, signal bars and red recording lamp.
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
d.rectangle((3,4,28,23),fill='#252333');d.rectangle((4,3,27,24),fill='#474252');d.rectangle((5,4,26,22),fill='#716776');d.line((5,4,26,4),fill='#b5a1a2');d.line((5,5,5,21),fill='#978694')
d.rectangle((6,6,25,19),fill='#121c28');d.rectangle((7,7,24,18),fill='#244053');d.polygon([(7,7),(22,7),(13,18),(7,18)],fill='#31556b');d.line((8,8,17,8),fill='#6692a0');d.line((8,9,13,9),fill='#466d81')
for y in (10,12,14,16):d.line((7,y,24,y),fill='#203b49')
d.rectangle((9,11,11,15),fill='#b8c0a5');d.rectangle((10,10,12,10),fill='#d2d6bb');d.rectangle((16,12,18,16),fill='#bd7741');d.point((17,11),fill='#f5bc69');d.line((20,10,23,10),fill='#e16656');d.point((22,8),fill='#fb6053')
d.rectangle((7,20,23,21),fill='#302c39');d.rectangle((8,20,10,21),fill='#ed7a34');d.rectangle((13,20,15,21),fill='#8664a0');d.rectangle((18,20,20,21),fill='#77a395')
d.rectangle((12,25,19,26),fill='#383440');d.rectangle((10,27,21,28),fill='#514a54');d.line((9,29,22,29),fill='#27252b')
im.save(p/'village_monitor.png')
# Gauntleted fist: warm skin and worn dark leather cuff, with warm highlights.
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
d.polygon([(6,14),(6,9),(8,7),(11,7),(12,5),(15,5),(16,6),(19,5),(21,7),(23,8),(24,11),(24,17),(22,20),(21,23),(10,23),(8,20),(5,18)],fill='#31232b')
d.polygon([(7,14),(7,10),(9,8),(11,8),(12,6),(15,6),(16,8),(18,6),(20,7),(20,9),(22,9),(23,11),(23,16),(21,19),(20,22),(10,22),(9,19),(6,17)],fill='#b47148')
d.rectangle((8,10,11,14),fill='#e2aa78');d.rectangle((12,8,15,13),fill='#edb985');d.rectangle((17,8,20,13),fill='#df9c70');d.rectangle((21,10,22,14),fill='#c7835d')
d.line((8,14,11,14),fill='#935736');d.line((12,13,15,13),fill='#9b6142');d.line((17,13,20,13),fill='#96593d');d.polygon([(7,15),(10,14),(13,16),(14,19),(12,20),(9,18),(7,18)],fill='#d39367');d.line((15,17,21,17),fill='#9a5e3d')
d.rectangle((10,23,20,27),fill='#48333b');d.line((10,23,20,23),fill='#d09652');d.line((10,26,20,26),fill='#251f2b');d.rectangle((14,24,16,25),fill='#c2a46e')
im.save(p/'fight_event.png')
for name in ('fight_event','village_monitor'):
 q=r/f'models/item/{name}.json';q.parent.mkdir(parents=True,exist_ok=True);q.write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'halloween:item/{name}'}},indent=2))
 q=r/f'items/{name}.json';q.write_text(json.dumps({'model':{'type':'minecraft:model','model':f'halloween:item/{name}'}}))
for lang,vals in [('ru_ru',{'item.halloween.fight_event':'Кулак: созыв боя','item.halloween.village_monitor':'Монитор деревни','message.halloween.fight_start':'На площади собираются на кулачный бой!','message.halloween.fight_unavailable':'Нужна площадь поблизости и два взрослых жителя; возможно, бой уже идёт.'}),('en_us',{'item.halloween.fight_event':'Fist: Call a Fight','item.halloween.village_monitor':'Village Monitor','message.halloween.fight_start':'Villagers gather for a fistfight in the square!','message.halloween.fight_unavailable':'A nearby square and two adult villagers are needed, or a match is already underway.'})]:
 q=r/f'lang/{lang}.json';v=json.loads(q.read_text(encoding='utf8'));v.update(vals);q.write_text(json.dumps(v,ensure_ascii=False,indent=2),encoding='utf8')
for name in ('fight_event','village_monitor'):
 Image.open(p/f'{name}.png').resize((512,512),Image.Resampling.NEAREST).save(f'preview/{name}.png')

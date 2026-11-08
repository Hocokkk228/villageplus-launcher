"""Pixel art for the villager builder profession and its workbench."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,random
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/halloween';R=ROOT/'src/main/resources'
def savejson(path,obj):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
def pixeltexture(name,draw):
    im=Image.new('RGBA',(16,16));draw(ImageDraw.Draw(im));p=A/f'textures/block/{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
def station_side(d):
    d.rectangle((0,0,15,15),fill=(102,65,38));d.rectangle((0,1,15,3),fill=(175,116,60));d.rectangle((0,12,15,15),fill=(65,43,33))
    d.rectangle((1,4,3,12),fill=(157,98,50));d.rectangle((12,4,14,12),fill=(157,98,50));d.rectangle((4,4,11,11),fill=(53,42,38))
    d.line((4,5,11,11),fill=(157,125,80),width=2);d.line((11,5,4,11),fill=(157,125,80),width=2)
    d.rectangle((0,0,15,0),fill=(218,157,79));d.point((2,2),fill=(223,183,104));d.point((13,2),fill=(223,183,104))
def station_top(d):
    d.rectangle((0,0,15,15),fill=(155,100,52))
    for y in [0,4,8,12,15]:d.line((0,y,15,y),fill=(86,56,36))
    d.rectangle((2,3,13,11),fill=(194,137,76));d.line((3,4,12,4),fill=(227,177,105));d.line((3,10,12,10),fill=(111,74,48))
    d.line((4,8,10,4),fill=(73,53,48),width=2);d.rectangle((9,2,13,4),fill=(164,175,171));d.point((12,2),fill=(218,219,192))
    d.line((3,12,8,12),fill=(236,190,88),width=1)
pixeltexture('builder_workbench_side',station_side);pixeltexture('builder_workbench_top',station_top)
model={'parent':'minecraft:block/cube_bottom_top','textures':{'side':'halloween:block/builder_workbench_side','top':'halloween:block/builder_workbench_top','bottom':'minecraft:block/oak_planks'}}
savejson(A/'models/block/builder_workbench.json',model)
savejson(A/'blockstates/builder_workbench.json',{'variants':{'':{'model':'halloween:block/builder_workbench'}}})
savejson(A/'items/builder_workbench.json',{'model':{'type':'minecraft:model','model':'halloween:block/builder_workbench'}})
savejson(R/'data/halloween/recipe/builder_workbench.json',{'type':'minecraft:crafting_shaped','category':'building','pattern':[' PI','PPP',' S '],
 'key':{'P':'minecraft:oak_planks','I':'minecraft:iron_ingot','S':'minecraft:stick'},'result':{'id':'halloween:builder_workbench','count':1}})
savejson(R/'data/halloween/loot_table/blocks/builder_workbench.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'halloween:builder_workbench'}],
 'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
skin=Image.new('RGBA',(64,64),(0,0,0,0));d=ImageDraw.Draw(skin);rng=random.Random(311)
# Villager hat model: top (40..48,0..8) and four side panels (32..64,8..18).
d.rectangle((40,0,47,7),fill=(202,140,28,255));d.rectangle((41,1,46,6),fill=(237,184,50,255));d.line((42,1,42,6),fill=(255,215,95,255))
for x in range(32,64):
    for y in range(8,13):
        colors=[(192,126,21,255),(218,155,28,255),(241,184,45,255),(255,202,67,255)]
        skin.putpixel((x,y),colors[min(3,(x%8)//3+(1 if rng.random()<.15 else 0))])
d.line((32,12,63,12),fill=(123,81,27,255));d.line((33,9,62,9),fill=(255,211,74,255))
for x in [35,43,51,59]:d.rectangle((x,10,x+1,11),fill=(223,217,190,255))
# Jacket front/back/sides in the standard villager body UV layout.
for x0,x1 in [(16,21),(22,29),(30,35),(36,43)]:
    d.rectangle((x0,26,x1,37),fill=(137,66,28,255));d.rectangle((x0,26,x1,28),fill=(193,97,35,255))
    d.line((x0,33,x1,33),fill=(232,192,102,255));d.line((x0,36,x1,36),fill=(69,52,41,255))
d.rectangle((24,29,27,32),fill=(175,89,31,255));d.line((25,29,25,32),fill=(218,151,67,255))
d.rectangle((37,29,41,32),fill=(68,62,54,255));d.line((38,30,40,30),fill=(174,169,144,255))
# Folded arms: reinforced dark sleeves and reflective cuffs.
for x in range(40,64):
    for y in range(39,47):
        if (x+y)%5!=0:skin.putpixel((x,y),(105+((x%4)*5),60+(y%3)*4,36,255))
for x in [42,50,58]:d.line((x,44,min(x+5,63),44),fill=(228,183,91,255))
d.rectangle((21,48,24,52),fill=(62,60,56,255));d.line((22,48,22,51),fill=(207,191,150,255))
p=A/'textures/entity/villager/profession/builder.png';p.parent.mkdir(parents=True,exist_ok=True);skin.save(p)
savejson(p.with_name('builder.png.mcmeta'),{'villager':{'hat':'full'}})
for lang in ['ru_ru','en_us']:
    p=A/f'lang/{lang}.json';v=json.loads(p.read_text(encoding='utf8'))
    v['entity.minecraft.villager.halloween.builder']='Строитель' if lang=='ru_ru' else 'Builder'
    v['block.halloween.builder_workbench']='Верстак строителя' if lang=='ru_ru' else 'Builder Workbench'
    savejson(p,v)
print('Generated builder hardhat, detailed work clothes and workstation.')

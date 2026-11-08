"""Original pixel candy icon, hollow trick-or-treat bucket, candy fill and animation frames."""
from pathlib import Path
import json, math, random
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1];R=ROOT/'src/main/resources';A=R/'assets/halloween'
def write(p,d):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,indent=2,ensure_ascii=False)+'\n',encoding='utf8')

(A/'textures/item').mkdir(parents=True,exist_ok=True)
icon=Image.new('RGBA',(32,32));d=ImageDraw.Draw(icon)
outline=[(15,2),(18,4),(29,25),(29,28),(26,30),(5,30),(2,27),(3,24),(13,4)]
d.polygon(outline,fill=(83,40,24,255))
d.polygon([(15,4),(17,5),(27,25),(27,27),(25,28),(6,28),(4,26),(5,24)],fill=(255,187,35,255))
d.polygon([(11,12),(20,12),(25,23),(6,23)],fill=(247,110,17,255))
d.polygon([(15,4),(17,6),(20,11),(12,11)],fill=(255,244,210,255))
d.line([(14,7),(8,20)],fill=(255,215,109,255),width=2)
d.line([(6,25),(6,27),(24,27)],fill=(255,215,72,255),width=1)
d.line([(22,16),(26,24)],fill=(209,79,14,255),width=2)
icon.save(A/'textures/item/candy_corn.png')

palettes={'bucket_orange':[(183,63,13),(209,77,14),(227,92,19),(239,108,27)],
          'bucket_inner':[(93,41,24),(111,48,25),(128,55,25),(141,64,28)],
          'bucket_brass':[(100,65,25),(137,92,34),(177,128,51),(209,162,76)],
          'corn_white':[(246,227,184),(255,242,211)],'corn_orange':[(229,86,12),(255,131,20)],
          'corn_yellow':[(235,153,21),(255,198,37)]}
for name,p in palettes.items():
    rng=random.Random(name);im=Image.new('RGB',(16,16));draw=ImageDraw.Draw(im)
    for y in range(16):
        for x in range(16): im.putpixel((x,y),p[min(len(p)-1,(x//3)%len(p))] if rng.random()>.14 else rng.choice(p))
    im.save(A/f'textures/block/{name}.png')
im=Image.open(A/'textures/block/bucket_orange.png');d=ImageDraw.Draw(im)
d.polygon([(3,5),(6,3),(6,6)],fill=(47,29,24));d.polygon([(10,3),(13,5),(10,6)],fill=(47,29,24))
d.polygon([(3,9),(5,10),(11,10),(13,8),(12,12),(5,13)],fill=(47,29,24));d.rectangle((7,9,8,10),fill=(227,92,19))
im.save(A/'textures/block/bucket_face.png')

dirs=['north','south','east','west','up','down'];base=[]
def cube(es,n,lo,hi,t,front=None):
    x,y,z=lo;X,Y,Z=hi
    uv={'north':[16-X,16-Y,16-x,16-y],'south':[x,16-Y,X,16-y],
        'east':[16-Z,16-Y,16-z,16-y],'west':[z,16-Y,Z,16-y],
        'up':[x,z,X,Z],'down':[x,16-Z,X,16-z]}
    es.append({'name':n,'from':lo,'to':hi,'faces':{d:{'uv':([0,0,16,16] if d=='north' and front else uv[d]),'texture':'#'+(front if d=='north' and front else t)} for d in dirs}})
cube(base,'Octagonal bottom',[4,0,4],[12,1,12],'bucket_inner')
cube(base,'Front pumpkin face',[3.5,1,2.5],[12.5,9,3.5],'bucket_orange','bucket_face')
cube(base,'Back wall',[3.5,1,12.5],[12.5,9,13.5],'bucket_orange')
cube(base,'Left wall',[2.5,1,3.5],[3.5,9,12.5],'bucket_orange')
cube(base,'Right wall',[12.5,1,3.5],[13.5,9,12.5],'bucket_orange')
for x in [3,12]:
    for z in [3,12]:cube(base,'Chamfered corner',[x,1,z],[x+1,9,z+1],'bucket_orange')
for lo,hi in [([3,9,2],[13,10,3.5]),([3,9,12.5],[13,10,14]),([2,9,3.5],[3.5,10,12.5]),([12.5,9,3.5],[14,10,12.5])]:
    cube(base,'Brass rim',lo,hi,'bucket_brass')
for lo,hi in [([3.5,1.1,3.51],[12.5,8.9,3.56]),([3.5,1.1,12.44],[12.5,8.9,12.49]),([3.51,1.1,3.6],[3.56,8.9,12.4]),([12.44,1.1,3.6],[12.49,8.9,12.4])]:
    cube(base,'Dark inside lining',lo,hi,'bucket_inner')
for i in range(24):
    a,b=i*math.pi/24,(i+1)*math.pi/24
    x,y=8+5.7*math.cos(a),9+6.6*math.sin(a)
    X,Y=8+5.7*math.cos(b),9+6.6*math.sin(b)
    cube(base,'Arched brass handle',[min(x,X)-.22,min(y,Y)-.22,7.7],[max(x,X)+.22,max(y,Y)+.22,8.3],'bucket_brass')
for x in [2.2,12.8]:cube(base,'Handle hinge',[x,7.5,7.3],[x+1,9.2,8.7],'bucket_brass')

def corn(es,x,y,z):
    cube(es,'Candy yellow base',[x-.52,y,z-.42],[x+.52,y+.4,z+.42],'corn_yellow')
    cube(es,'Candy orange middle',[x-.38,y+.4,z-.32],[x+.38,y+.9,z+.32],'corn_orange')
    cube(es,'Candy white tip',[x-.18,y+.9,z-.19],[x+.18,y+1.22,z+.19],'corn_white')

textures={n:'halloween:block/'+n for n in [*palettes,'bucket_face']}
display={'gui':{'rotation':[25,155,0],'scale':[.85]*3},'ground':{'translation':[0,3,0],'scale':[.4]*3},
         'firstperson_righthand':{'rotation':[0,135,0],'scale':[.5]*3},'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.375]*3}}
for fill in range(5):
    es=json.loads(json.dumps(base));rng=random.Random(81)
    for layer in range(fill):
        for row in range(3):
            for col in range(3):corn(es,4.5+col*3+rng.uniform(-.3,.3),2+layer*2.3,4.5+row*3+rng.uniform(-.3,.3))
    for motion in range(16):
        lift=([0,1.5,3.5,5.5,7]+[7.5]*6+[6,4.5,3,1.5,0])[motion]
        dx=([-0.55,.55,-.35,.4,-.5,.5][motion-5] if 5<=motion<=10 else 0)
        dz=([.35,-.4,.5,-.5,.25,-.35][motion-5] if 5<=motion<=10 else 0)
        frame=json.loads(json.dumps(es))
        for e in frame:
            for key in ['from','to']:e[key]=[round(e[key][0]+dx,4),round(e[key][1]+lift,4),round(e[key][2]+dz,4)]
        write(A/f'models/block/candy_bucket_{fill}_{motion}.json',{'ambientocclusion':True,'textures':{**textures,'particle':textures['bucket_orange']},'display':display,'elements':frame})
write(A/'blockstates/candy_bucket.json',{'variants':{f'fill={f},motion={m}':{'model':f'halloween:block/candy_bucket_{f}_{m}'} for f in range(5) for m in range(16)}})
write(A/'items/candy_bucket.json',{'model':{'type':'minecraft:model','model':'halloween:block/candy_bucket_0_0'}})
write(A/'models/item/candy_corn.json',{'parent':'minecraft:item/generated','textures':{'layer0':'halloween:item/candy_corn'}})
write(A/'items/candy_corn.json',{'model':{'type':'minecraft:model','model':'halloween:item/candy_corn'}})
for lang,bucket,candy in [('ru_ru','Ведёрко для сладостей','Кэнди-корн'),('en_us','Trick-or-Treat Bucket','Candy Corn')]:
    p=A/f'lang/{lang}.json';v=json.loads(p.read_text(encoding='utf8'));v.update({'block.halloween.candy_bucket':bucket,'item.halloween.candy_corn':candy});write(p,v)
write(R/'data/halloween/recipe/candy_corn.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':['minecraft:sugar','minecraft:orange_dye'],'result':{'id':'halloween:candy_corn','count':4}})
write(R/'data/halloween/recipe/candy_bucket.json',{'type':'minecraft:crafting_shaped','category':'building','pattern':['S S','O O',' O '],
    'key':{'S':'minecraft:string','O':'minecraft:orange_terracotta'},'result':{'id':'halloween:candy_bucket','count':1}})
write(R/'data/halloween/loot_table/blocks/candy_bucket.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'halloween:candy_bucket'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
print('Generated candy icon, hollow bucket, four fill stages and sixteen animation poses.')

"""Original, low-element-count Minecraft decorations and portable Blockbench sources."""
from pathlib import Path
import base64
import json
import random
import uuid
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/halloween'
ART = ROOT / 'art'
DIRS = ['north', 'south', 'east', 'west', 'up', 'down']


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n', encoding='utf8')


colors = {'cloth': [(221,224,211), (231,233,222), (238,239,229), (244,244,234)],
          'cloth_hem': [(185,198,191), (198,209,200), (213,221,210), (228,233,221)],
          'bone': [(191,178,142), (211,199,163), (226,216,182), (237,228,197)],
          'bone_end': [(179,165,130), (203,189,151), (218,206,173), (231,220,188)],
          'socket': [(35,32,31), (42,38,34), (49,44,37), (57,51,42)]}
for name, palette in colors.items():
    rng = random.Random(name)
    im = Image.new('RGB',(16,16),palette[2])
    for y in range(16):
        for x in range(16):
            if rng.random()<.23: im.putpixel((x,y),rng.choice(palette))
    if name.startswith('cloth'):
        draw=ImageDraw.Draw(im)
        for x in [1,7,13]: draw.line((x,5,x,15),fill=palette[1])
    im.save(ASSETS / f'textures/block/{name}.png')

for name in ['ghost_face', 'little_ghost_face']:
    im=Image.open(ASSETS/'textures/block/cloth.png').copy()
    d=ImageDraw.Draw(im)
    ink=(36,43,46)
    d.rectangle((3,5,5,8),fill=ink)
    d.rectangle((10,5,12,8),fill=ink)
    d.point((3,5),fill=(77,87,83)); d.point((10,5),fill=(77,87,83))
    if name=='ghost_face':
        d.rectangle((7,10,8,12),fill=ink)
    else:
        d.line((6,10,7,11,8,11,9,10), fill=ink, width=1)
    im.save(ASSETS/f'textures/block/{name}.png')


def cube(name, lo, hi, tex='bone', front=None, rotation=None):
    faces={d:{'texture':'#'+(front if d=='north' and front else tex), 'uv':[0,0,16,16]} for d in DIRS}
    e={'name':name,'from':lo,'to':hi,'faces':faces}
    if rotation: e['rotation']=rotation
    return e


def ghost(small=False):
    if small:
        return [cube('Little ghost head',[5,6,5],[11,12,11],'cloth','little_ghost_face'),
                cube('Little sheet',[4,3,4.5],[12,6,11.5],'cloth_hem'),
                cube('Left waving arm',[2.5,5.5,6],[4.5,8,10],'cloth'),
                cube('Right waving arm',[11.5,5.5,6],[13.5,8,10],'cloth'),
                cube('Left hem',[4,2,4.5],[6,3,11.5],'cloth_hem'),
                cube('Middle hem',[7,1.5,4.5],[9,3,11.5],'cloth_hem'),
                cube('Right hem',[10,2,4.5],[12,3,11.5],'cloth_hem')]
    es=[cube('Ghost head',[4,7,4],[12,14,12],'cloth','ghost_face'),
        cube('Folded crown',[5,14,5],[11,15,11],'cloth'),
        cube('Draped sheet',[3,3,4],[13,7,12],'cloth_hem'),
        cube('Left outstretched arm',[1,7,5],[4,10,11],'cloth'),
        cube('Right outstretched arm',[12,7,5],[15,10,11],'cloth')]
    for i,y in enumerate([1,2,0.5,2,1]):
        es.append(cube('Ragged hem '+str(i),[3+i*2,y,4],[5+i*2,3,12],'cloth_hem'))
    return es


def bone(cx,cz,length=10,height=0,along='x'):
    es=[]
    def c(name,lo,hi,tex):
        if along=='z': lo=[lo[2],lo[1],lo[0]];hi=[hi[2],hi[1],hi[0]]
        lo=[lo[0]+cx,lo[1]+height,lo[2]+cz];hi=[hi[0]+cx,hi[1]+height,hi[2]+cz]
        es.append(cube(name,lo,hi,tex))
    c('Bone shaft',[-length/2+.8,.5,-.6],[length/2-.8,1.6,.6],'bone')
    for side in [-1,1]:
        x=side*(length/2-1)
        c('Joint lobe A',[x-.9,0,-1.4],[x+.9,2.2,0],'bone_end')
        c('Joint lobe B',[x-.9,0,0],[x+.9,2.2,1.4],'bone_end')
    return es


def skull(cx=8,cy=0,cz=8,scale=1):
    es=[cube('Skull cranium',[4,2.5,5.6],[12,8,12]),
        cube('Cranium top bevel',[4.5,8,5.6],[11.5,8.5,11.5]),
        cube('Dark recessed sockets',[4.1,2.6,5.53],[11.9,7.9,5.57],'socket')]
    rows=['########','#..##..#','#..##..#','########','###..###','##....##']
    for row,line in enumerate(rows):
        for x,v in enumerate(line):
            if v=='#':es.append(cube('Facial bone',[4+x,7.5-row,4],[5+x,8.5-row,5.6]))
    es.append(cube('Lower jaw',[4.5,.2,4.1],[11.5,1.2,8],'bone_end'))
    for i in range(5):
        es.append(cube('Jaw tooth',[4.6+i*1.35,1.2,4.1],[5.6+i*1.35,2.4,5.3]))
    es.extend([cube('Left jaw hinge',[4.5,1.2,7],[5.5,3,8]),cube('Right jaw hinge',[10.5,1.2,7],[11.5,3,8])])
    for e in es:
        for key in ['from','to']:
            x,y,z=e[key]; e[key]=[round(cx+(x-8)*scale,3),round(cy+y*scale,3),round(cz+(z-8)*scale,3)]
    return es


models={
    'sheet_ghost':ghost(),
    'little_ghost':ghost(True),
    'scattered_bones':bone(7,4,11)+bone(11.5,10,8,0,'z')+bone(5,11.5,6),
    'crossed_bones':bone(8,8,12)+bone(8,8,12,1.6,'z'),
    'weathered_skull':skull(),
    'skull_pile':skull(4.4,0,7.5,.8)+skull(11,0,10,.8)+skull(8,5.1,8,.65)+bone(8,4,12),
}
names={
    'sheet_ghost':('Призрак в простыне','Sheet Ghost'),
    'little_ghost':('Маленький призрак','Little Ghost'),
    'scattered_bones':('Разбросанные кости','Scattered Bones'),
    'crossed_bones':('Скрещённые кости','Crossed Bones'),
    'weathered_skull':('Старый череп','Weathered Skull'),
    'skull_pile':('Груда черепов','Skull Pile'),
}
for name,elements in models.items():
    textures=sorted({face['texture'][1:] for e in elements for face in e['faces'].values()})
    display={
        'gui':{'rotation':[25,155,0],'translation':[0,0 if name.endswith('ghost') else 2,0],'scale':[.85]*3},
        'ground':{'translation':[0,3,0],'scale':[.4]*3},
        'fixed':{'rotation':[0,180,0],'scale':[.7]*3},
        'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375]*3},
        'firstperson_righthand':{'rotation':[0,135,0],'scale':[.5]*3},
        'firstperson_lefthand':{'rotation':[0,225,0],'scale':[.5]*3}}
    model={'ambientocclusion':True,'textures':{t:f'halloween:block/{t}' for t in textures},'display':display,'elements':elements}
    model['textures']['particle']=f'halloween:block/{"cloth" if name.endswith("ghost") else "bone"}'
    write(ASSETS/f'models/block/{name}.json',model)
    write(ASSETS/f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'halloween:block/{name}'}})
    write(ASSETS/f'blockstates/{name}.json',{'variants':{f'facing={d}':{'model':f'halloween:block/{name}','y':angle} for d,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    write(RES/f'data/halloween/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'halloween:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    ingredients = {
        'sheet_ghost':['minecraft:white_wool','minecraft:white_wool','minecraft:string','minecraft:black_dye'],
        'little_ghost':['minecraft:white_wool','minecraft:string','minecraft:black_dye'],
        'scattered_bones':['minecraft:bone']*3,
        'crossed_bones':['minecraft:bone']*2,
        'weathered_skull':['minecraft:bone']*4+['minecraft:gray_dye'],
        'skull_pile':['halloween:weathered_skull']*3,
    }[name]
    write(RES/f'data/halloween/recipe/{name}.json',{'type':'minecraft:crafting_shapeless','category':'building','ingredients':ingredients,'result':{'id':f'halloween:{name}','count':1}})
    bb=[]
    for i,e in enumerate(elements):
        bb.append({**e,'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,f'halloween/{name}/{i}')),'type':'cube','origin':[8,8,8],'autouv':0,
                   'faces':{d:{**f,'texture':textures.index(f['texture'][1:])} for d,f in e['faces'].items()}})
    write(ART/f'{name}.bbmodel',{'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':names[name][1],
        'resolution':{'width':16,'height':16},'elements':bb,'outliner':[e['uuid'] for e in bb],'display':display,
        'textures':[{'name':t+'.png','id':t,'namespace':'halloween','folder':'block','width':16,'height':16,'uv_width':16,'uv_height':16,
                     'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'halloween/tex/'+t)),
                     'source':'data:image/png;base64,'+base64.b64encode((ASSETS/f'textures/block/{t}.png').read_bytes()).decode()} for t in textures]})
for lang,idx in [('ru_ru',0),('en_us',1)]:
    path=ASSETS/f'lang/{lang}.json'
    data=json.loads(path.read_text(encoding='utf8'))
    data.update({f'block.halloween.{n}':v[idx] for n,v in names.items()})
    write(path,data)
print('Generated decorations:', ', '.join(f'{n} ({len(e)} cubes)' for n,e in models.items()))

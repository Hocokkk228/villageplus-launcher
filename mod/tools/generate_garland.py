"""A sagging cord with alternating miniature pumpkins and sheet ghosts.
Five paths correspond to vanilla stair shapes; four facing rotations are in blockstates.
"""
from pathlib import Path
import base64, json, math, uuid
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
A=RES/'assets/halloween'

def write(p,v):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n',encoding='utf8')

cord=Image.new('RGB',(16,16),(65,49,28))
d=ImageDraw.Draw(cord)
for x in range(0,16,4): d.line((x,0,x,15), fill=(100,79,42))
cord.save(A/'textures/block/garland_cord.png')
face=Image.new('RGB',(16,16),(224,100,18));d=ImageDraw.Draw(face)
for x in [1,5,10,14]: d.line((x,0,x,15),fill=(183,66,10))
for rect in [(3,4,5,6),(10,4,12,6),(4,10,11,11),(6,12,9,12)]: d.rectangle(rect,fill=(255,182,48))
d.point((7,10),fill=(224,100,18)); d.point((9,11),fill=(224,100,18))
face.save(A/'textures/block/garland_pumpkin_face.png')

textures=['garland_cord','rind','stem','garland_pumpkin_face','cloth','cloth_hem','ghost_face']
mapping={n:f'halloween:block/{"pumpkin_"+n if n in ("rind","stem") else n}' for n in textures}
dirs=['north','south','west','east','up','down']

def cube(es,name,lo,hi,tex,front=None):
    es.append({'name':name,'from':[round(v,4) for v in lo],'to':[round(v,4) for v in hi],
               'faces':{d:{'uv':[0,0,16,16],'texture':'#'+(front if d=='north' and front else tex)} for d in dirs}})

def point(t,shape):
    ends={'outer_left':((0,8),(8,0)),'outer_right':((8,0),(16,8)),
          'inner_left':((0,8),(8,16)),'inner_right':((8,16),(16,8))}
    if shape=='straight': x,z=16*t,8
    else:
        (ax,az),(bx,bz)=ends[shape]
        x=(1-t)**2*ax+2*(1-t)*t*8+t*t*bx
        z=(1-t)**2*az+2*(1-t)*t*8+t*t*bz
    return x,15-4.5*math.sin(math.pi*t),z

for shape in ['straight','outer_left','outer_right','inner_left','inner_right']:
    es=[]
    for i in range(32):
        a,b=point(i/32,shape),point((i+1)/32,shape)
        cube(es,'Braided sagging cord',[max(0,min(a[j],b[j])-.13) for j in range(3)],
             [min(16,max(a[j],b[j])+.13) for j in range(3)],'garland_cord')
    for i,t in enumerate([.125,.375,.625,.875]):
        x,y,z=point(t,shape)
        cube(es,'Pendant string',[x-.09,y-1.3,z-.09],[x+.09,y,z+.09],'garland_cord')
        y-=2.5
        if i%2==0:
            cube(es,'Mini pumpkin',[x-1,y-1,z-.95],[x+1,y+1,z+.95],'rind','garland_pumpkin_face')
            cube(es,'Left pumpkin lobe',[x-1.35,y-.65,z-.65],[x-1,y+.65,z+.65],'rind')
            cube(es,'Right pumpkin lobe',[x+1,y-.65,z-.65],[x+1.35,y+.65,z+.65],'rind')
            cube(es,'Pumpkin stem',[x-.22,y+1,z-.2],[x+.22,y+1.6,z+.2],'stem')
            # Only the face glows; the rind and string retain normal shading.
            front=es[-4]['faces'].pop('north')
            es.append({'name':'Warm carved face','from':[round(x-1,4),round(y-1,4),round(z-.955,4)],
                       'to':[round(x+1,4),round(y+1,4),round(z-.951,4)],'shade':False,'light_emission':12,'faces':{'north':front}})
        else:
            cube(es,'Little ghost head',[x-.95,y-.2,z-.7],[x+.95,y+1.7,z+.7],'cloth','ghost_face')
            cube(es,'Flowing sheet',[x-1.2,y-1.1,z-.8],[x+1.2,y-.2,z+.8],'cloth_hem')
            for j in [0,2]: cube(es,'Ragged ghost hem',[x-1.2+j*.8,y-1.7,z-.8],[x-.4+j*.8,y-1.1,z+.8],'cloth_hem')
            cube(es,'Ghost arms',[x-1.5,y+.1,z-.45],[x+1.5,y+.65,z+.45],'cloth')
    display={'gui':{'rotation':[20,155,0],'translation':[0,-2,0],'scale':[.9]*3},
             'ground':{'translation':[0,0,0],'scale':[.4]*3},
             'firstperson_righthand':{'rotation':[0,135,0],'translation':[0,-2,0],'scale':[.5]*3},
             'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,0,0],'scale':[.375]*3}}
    model={'ambientocclusion':True,'textures':{**mapping,'particle':mapping['rind']},'display':display,'elements':es}
    name='halloween_garland_'+shape
    write(A/f'models/block/{name}.json',model)
    bb=[]
    for i,e in enumerate(es):
        bb.append({**e,'type':'cube','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name+str(i))),'origin':[8,8,8],'autouv':0,
                   'faces':{d:({**e['faces'][d],'texture':textures.index(e['faces'][d]['texture'][1:])} if d in e['faces'] else {'texture':None,'uv':[0,0,0,0]}) for d in dirs}})
    write(ROOT/f'art/{name}.bbmodel',{'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':name,
        'resolution':{'width':16,'height':16},'elements':bb,'outliner':[e['uuid'] for e in bb],'display':display,
        'textures':[{'name':n+'.png','id':n,'width':16,'height':16,'uv_width':16,'uv_height':16,
                     'source':'data:image/png;base64,'+base64.b64encode((A/('textures/'+mapping[n].split(':')[1]+'.png')).read_bytes()).decode()} for n in textures]})

write(A/'blockstates/halloween_garland.json',{'variants':{
    f'facing={d},shape={s}':{'model':f'halloween:block/halloween_garland_{s}','y':angle}
    for d,angle in [('north',0),('east',90),('south',180),('west',270)]
    for s in ['straight','outer_left','outer_right','inner_left','inner_right']}})
write(A/'items/halloween_garland.json',{'model':{'type':'minecraft:model','model':'halloween:block/halloween_garland_straight'}})
for lang,name in [('ru_ru','Гирлянда из тыкв и призраков'),('en_us','Pumpkin and Ghost Garland')]:
    p=A/f'lang/{lang}.json';v=json.loads(p.read_text(encoding='utf8'));v['block.halloween.halloween_garland']=name;write(p,v)
write(RES/'data/halloween/recipe/halloween_garland.json',{'type':'minecraft:crafting_shapeless','category':'building',
    'ingredients':['minecraft:string','minecraft:string','minecraft:pumpkin','minecraft:white_wool','minecraft:torch'],
    'result':{'id':'halloween:halloween_garland','count':4}})
write(RES/'data/halloween/loot_table/blocks/halloween_garland.json',{'type':'minecraft:block','pools':[{'rolls':1,
    'entries':[{'type':'minecraft:item','name':'halloween:halloween_garland'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
print('Generated garland: five paths, twenty facing/shape variants.')

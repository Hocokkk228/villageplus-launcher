"""Fifteen Halloween furniture models, patterned upholstery and Blockbench sources."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,random,copy,base64,uuid
ROOT=Path(__file__).resolve().parents[1];R=ROOT/'src/main/resources';A=R/'assets/halloween'
def write(p,v):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
palettes={'haunted_wood':[(32,23,30),(48,31,37),(65,40,42),(83,48,45)],
 'velvet_orange':[(149,48,13),(181,62,19),(209,80,25),(230,101,35)],
 'velvet_purple':[(45,24,62),(65,33,87),(88,44,112),(110,60,133)],
 'velvet_black':[(22,24,33),(31,33,43),(42,43,54),(53,53,65)],
 'statue_stone':[(63,72,67),(79,87,79),(96,103,91),(110,116,100)],
 'rusted_iron':[(46,40,39),(64,51,43),(91,65,43),(122,78,44)],
 'scarecrow_straw':[(141,102,39),(175,133,52),(201,158,67),(221,179,86)],
 'scarecrow_coat':[(73,37,30),(104,50,35),(134,67,42),(154,83,49)],
 'witch_bottle':[(36,83,69),(46,112,83),(66,145,97),(96,171,120)]}
for name,colors in palettes.items():
    rng=random.Random(name);im=Image.new('RGB',(16,16))
    for y in range(16):
        for x in range(16):im.putpixel((x,y),colors[((x//2)%3+int(rng.random()<.12))%4])
    if 'velvet' in name:
        d=ImageDraw.Draw(im);d.rectangle((1,1,14,14),outline=colors[1])
        for x in range(3,14,3):d.point((x,1),fill=colors[3]);d.point((x,14),fill=colors[3])
    im.save(A/f'textures/block/{name}.png')
for pattern,base in [('pumpkin','orange'),('bat','purple'),('ghost','black'),('spider','purple')]:
    im=Image.open(A/f'textures/block/velvet_{base}.png');d=ImageDraw.Draw(im)
    if pattern=='pumpkin':
        d.polygon([(4,6),(6,4),(11,4),(13,7),(12,11),(10,13),(5,12),(3,9)],fill=(245,129,28));d.rectangle((8,2,9,4),fill=(87,107,40))
        for r in [(5,6,6,7),(10,6,11,7),(6,10,10,10)]:d.rectangle(r,fill=(58,30,30))
    elif pattern=='bat':
        d.polygon([(2,5),(5,7),(7,6),(7,4),(8,5),(9,4),(9,6),(11,7),(14,5),(13,10),(11,9),(9,12),(7,12),(5,9),(3,10)],fill=(19,20,28))
        d.point((7,7),fill=(235,136,43));d.point((9,7),fill=(235,136,43))
    elif pattern=='ghost':
        d.polygon([(5,5),(7,3),(10,3),(12,6),(12,12),(10,11),(8,13),(6,11),(4,12),(4,7)],fill=(230,232,211))
        for r in [(6,6,7,7),(9,6,10,7),(8,9,8,10)]:d.rectangle(r,fill=(39,35,45))
    else:
        for end in [(2,2),(13,2),(2,13),(13,13),(8,1),(1,8),(14,8),(8,14)]:d.line((8,8,*end),fill=(166,146,178))
        d.rectangle((4,4,12,12),outline=(166,146,178));d.rectangle((6,6,10,10),outline=(166,146,178))
        d.rectangle((7,7,9,10),fill=(26,20,32));d.point((8,7),fill=(219,80,29))
    im.save(A/f'textures/block/cushion_{pattern}.png')

DIRS=['north','south','west','east','up','down']
def cube(es,n,lo,hi,t,front=None,top=None):
    es.append({'name':n,'from':lo,'to':hi,'faces':{d:{'uv':[0,0,16,16],'texture':'#'+(front if d=='north' and front else top if d=='up' and top else t)} for d in DIRS}})
def chair(style):
    e=[];fabric='velvet_orange' if style=='pumpkin' else 'velvet_purple' if style=='gothic' else 'velvet_black'
    for x in [2,11]:
        for z in [2,11]:cube(e,'Carved chair leg',[x,0,z],[x+2,7,z+2],'haunted_wood')
    cube(e,'Seat frame',[1.5,6,1.5],[14.5,7.5,14.5],'haunted_wood')
    cube(e,'Stitched upholstered seat',[2,7.5,2],[14,9,12.5],fabric,top='cushion_'+('pumpkin' if style=='pumpkin' else 'bat' if style=='gothic' else 'ghost'))
    for x in [1.5,12.5]:cube(e,'Back post',[x,7,12.5],[x+2,16,14.5],'haunted_wood')
    if style=='coffin':
        cube(e,'Coffin shoulder',[3,9,12.8],[13,14,14.2],fabric,front='cushion_ghost')
        cube(e,'Coffin tapered crest',[5,14,12.8],[11,16,14.2],'haunted_wood')
    else:
        cube(e,'Upholstered back',[3.5,10,12.8],[12.5,15,14.2],fabric,front='cushion_'+('pumpkin' if style=='pumpkin' else 'bat'))
        cube(e,'Decorative crest',[5,15,12.5],[11,16,14.5],'rusted_iron')
    for x in [1,13]:cube(e,'Arm support',[x,8,3],[x+1,11,5],'haunted_wood');cube(e,'Armrest',[x-.3,11,2],[x+1.3,12,12.5],fabric)
    return e
def cushion(pattern):
    e=[];fabric='velvet_orange' if pattern=='pumpkin' else 'velvet_black' if pattern=='ghost' else 'velvet_purple'
    cube(e,'Rounded cushion middle',[1,1,2],[15,3,14],fabric)
    cube(e,'Cushion side bulge',[2,1,1],[14,3,15],fabric)
    cube(e,'Stitched patterned top',[2,3,2],[14,4,14],fabric,top='cushion_'+pattern)
    cube(e,'Cushion underside',[2,0,2],[14,1,14],fabric)
    for x,z in [(1,1),(14,1),(1,14),(14,14)]:cube(e,'Corner tassel',[x,0,z],[x+1,1,z+1],'rusted_iron')
    return e
def cabinet(coffin=False):
    e=[]
    for x in [2,11]:
        for z in [3,11]:cube(e,'Cabinet foot',[x,0,z],[x+3,3,z+2],'haunted_wood')
    cube(e,'Bottom plinth',[1.5,2,2],[14.5,4,14],'haunted_wood')
    cube(e,'Back',[2,4,12],[14,28,14],'haunted_wood')
    for x in [2,12]:cube(e,'Side frame',[x,4,2],[x+2,28,12],'haunted_wood')
    cube(e,'Crown',[1,28,1.5],[15,30,14.5],'haunted_wood')
    if coffin:
        cube(e,'Coffin door',[4.1,4,2.1],[11.9,26,3.5],'velvet_black')
        cube(e,'Iron cross upright',[7.5,13,1.8],[8.5,23,2.05],'rusted_iron')
        cube(e,'Iron cross arms',[5.5,19,1.8],[10.5,20,2.05],'rusted_iron')
    else:
        for x in [4.1,8.1]:
            cube(e,'Crooked inset door',[x,4,2.1],[x+3.8,26,3.5],'velvet_purple')
            cube(e,'Ghost carving',[x+.4,16,2.02],[x+3.4,21,2.08],'velvet_purple',front='cushion_ghost')
        for x in [7,8.5]:cube(e,'Brass pull',[x,12,1.7],[x+.5,14,2.1],'bucket_brass')
    for y in [6,24]:cube(e,'Iron door strap',[4,y,1.85],[12,y+.6,2.1],'rusted_iron')
    return e
def scarecrow():
    e=[];cube(e,'Wooden stake',[7,0,7],[9,22,9],'haunted_wood')
    cube(e,'Straw shoes',[3,0,5],[13,2,11],'scarecrow_straw')
    for x in [4.5,9]:cube(e,'Stuffed trouser leg',[x,2,6],[x+2.5,10,10],'velvet_black')
    cube(e,'Patchwork coat',[4,9,5],[12,21,11],'scarecrow_coat')
    cube(e,'Horizontal crossbar',[.5,17,7],[15.5,19,9],'haunted_wood')
    for x in [.5,12]:cube(e,'Stuffed sleeve',[x,16,5.5],[x+3.5,20,10.5],'scarecrow_coat')
    for x in [.2,14]:cube(e,'Straw hand',[x,16.5,6],[x+1.8,19,10],'scarecrow_straw')
    cube(e,'Orange coat patch',[4.5,11,4.85],[7.5,15,5],'velvet_orange')
    for y in [11,15,19]:cube(e,'Coat button',[8.5,y,4.7],[9,y+.7,5],'bucket_brass')
    pumpkin=json.loads((A/'models/block/pumpkin_lantern.json').read_text(encoding='utf8'))
    for element in pumpkin['elements']:
        v=copy.deepcopy(element)
        for k in ['from','to']:v[k]=[round(8+(v[k][0]-8)*.55,4),round(21+v[k][1]*.55,4),round(8+(v[k][2]-8)*.55,4)]
        for f in v['faces'].values():f['texture']='#p_'+f['texture'][1:]
        e.append(v)
    return e
def gargoyle():
    e=[];t='statue_stone'
    cube(e,'Stone pedestal',[1,0,1],[15,2,15],t);cube(e,'Crouching body',[5,4,6],[11,11,12],t)
    for x in [3,10]:cube(e,'Clawed foot',[x,2,3],[x+3,4,10],t)
    cube(e,'Beast head',[4,10,3],[12,15,10],t);cube(e,'Snout',[5,10,1.5],[11,12,4],t)
    for x in [5,10]:cube(e,'Eye socket',[x,12,2.9],[x+1,13,3.02],'socket')
    for x in [4,11]:cube(e,'Horn',[x,14,5],[x+1,16,6],t)
    for x in [1,12]:
        cube(e,'Folded wing',[x,6,10],[x+3,14,12],t)
        cube(e,'Wing tip',[x,12,9],[x+1,16,11],t)
    return e
def reaper():
    e=[];cube(e,'Stone pedestal',[2,0,2],[14,2,14],'statue_stone')
    cube(e,'Dark robe',[4,2,4],[12,18,12],'velvet_black')
    cube(e,'Robe shoulders',[2.5,16,4.5],[13.5,21,11.5],'velvet_black')
    cube(e,'Hood',[4,21,4],[12,29,12],'velvet_black')
    cube(e,'Recessed skull face',[5,22,3.9],[11,27,4.1],'bone',front='ghost_face')
    cube(e,'Scythe haft',[13,2,6],[14,28,7],'haunted_wood')
    cube(e,'Scythe blade',[6,28,6],[14,29,7],'rusted_iron')
    cube(e,'Curved blade tip',[5,25,6],[6,29,7],'rusted_iron')
    return e
def table():
    e=[]
    for x in [2,12]:
        for z in [2,12]:cube(e,'Carved table leg',[x,0,z],[x+2,10,z+2],'haunted_wood')
    cube(e,'Table slab',[1,10,1],[15,12,15],'haunted_wood')
    cube(e,'Purple altar runner',[5,12.01,1.5],[11,12.15,14.5],'velvet_purple',top='cushion_bat')
    for x,z in [(3,4),(11,10)]:
        cube(e,'Potion bottle',[x,12.1,z],[x+2,14,z+2],'witch_bottle');cube(e,'Bottle neck',[x+.5,14,z+.5],[x+1.5,15,z+1.5],'witch_bottle');cube(e,'Cork',[x+.5,15,z+.5],[x+1.5,15.5,z+1.5],'haunted_wood')
    return e
def candles():
    e=[];cube(e,'Iron foot',[4,0,4],[12,1.5,12],'rusted_iron');cube(e,'Twisted upright',[7,1.5,7],[9,10,9],'rusted_iron')
    cube(e,'Candelabra arms',[2,7,7.5],[14,8.5,8.5],'rusted_iron')
    for x,y in [(2,8.5),(7,10),(12,8.5)]:
        cube(e,'Wax candle',[x,y,7],[x+2,y+3.5,9],'bone')
        cube(e,'Wax drip',[x+1.8,y+1.5,7.5],[x+2.2,y+3,8.3],'bone')
        cube(e,'Candle flame',[x+.5,y+3.5,7.5],[x+1.5,y+5,8.5],'corn_orange')
        e[-1].update(shade=False,light_emission=15)
    return e
stool=[]
for x,z in [(3,3),(11,3),(3,11),(11,11)]:cube(stool,'Stool leg',[x,0,z],[x+2,6,z+2],'haunted_wood')
cube(stool,'Round-ish stool rim',[2,6,3],[14,7,13],'haunted_wood');cube(stool,'Witch velvet seat',[3,7,3],[13,8.5,13],'velvet_purple',top='cushion_spider')
models={**{s+'_chair':chair(s) for s in ['pumpkin','gothic','coffin']},'witch_stool':stool,
 **{s+'_cushion':cushion(s) for s in ['pumpkin','bat','ghost','spider']},
 'haunted_cabinet':cabinet(),'coffin_cabinet':cabinet(True),'pumpkin_scarecrow':scarecrow(),
 'gargoyle_statue':gargoyle(),'reaper_statue':reaper(),'witch_table':table(),'candle_candelabra':candles()}
names={'pumpkin_chair':'Тыквенное кресло','gothic_chair':'Готическое кресло','coffin_chair':'Кресло-гроб','witch_stool':'Ведьмин табурет',
 'pumpkin_cushion':'Подушка с тыквой','bat_cushion':'Подушка с летучей мышью','ghost_cushion':'Подушка с призраком','spider_cushion':'Подушка с паутиной',
 'haunted_cabinet':'Шкаф с привидениями','coffin_cabinet':'Шкаф-гроб','pumpkin_scarecrow':'Тыквенное пугало','gargoyle_statue':'Статуя горгульи',
 'reaper_statue':'Статуя жнеца','witch_table':'Ведьмин столик','candle_candelabra':'Мрачный канделябр'}
display={'gui':{'rotation':[25,155,0],'scale':[.7]*3},'ground':{'translation':[0,3,0],'scale':[.3]*3},
 'firstperson_righthand':{'rotation':[0,135,0],'scale':[.4]*3},'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.3]*3}}
for name,es in models.items():
    tall=name.endswith('cabinet') or name in ['pumpkin_scarecrow','reaper_statue']
    tex={f['texture'][1:] for e in es for f in e['faces'].values()}
    ptex=json.loads((A/'models/block/pumpkin_lantern.json').read_text(encoding='utf8'))['textures']
    textures={t:(ptex[t[2:]] if t.startswith('p_') else 'halloween:block/'+t) for t in tex}
    model={'ambientocclusion':True,'textures':{**textures,'particle':'halloween:block/haunted_wood'},'display':display,'elements':es}
    if tall:model['display']=copy.deepcopy(display);model['display']['gui']['scale']=[.45]*3;model['display']['gui']['translation']=[0,-4,0]
    write(A/f'models/block/{name}.json',model)
    if tall:
        for half,low,high in [('lower',0,16),('upper',16,32)]:
            part=[]
            for e in es:
                if e['from'][1]>=high or e['to'][1]<=low:continue
                v=copy.deepcopy(e);a,b=e['from'][1],e['to'][1];y,Y=max(low,a),min(high,b)
                v['from'][1]=round(y-low,4);v['to'][1]=round(Y-low,4)
                for direction in list(v['faces']):
                    if (direction=='up' and b>high) or (direction=='down' and a<low):del v['faces'][direction]
                    elif direction not in ['up','down'] and b>a:
                        u,v0,U,V=v['faces'][direction]['uv'];v['faces'][direction]['uv']=[u,v0+(V-v0)*(b-Y)/(b-a),U,v0+(V-v0)*(b-y)/(b-a)]
                part.append(v)
            write(A/f'models/block/{name}_{half}.json',{**model,'elements':part})
    write(A/f'blockstates/{name}.json',{'variants':{f'facing={d},half={h}':{'model':f'halloween:block/{name}'+('_'+h if tall else ''),'y':angle}
        for d,angle in [('north',0),('east',90),('south',180),('west',270)] for h in ['lower','upper']}})
    write(A/f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'halloween:block/{name}'}})
    condition=[{'condition':'minecraft:survives_explosion'}]
    if tall:condition.append({'condition':'minecraft:block_state_property','block':'halloween:'+name,'properties':{'half':'lower'}})
    write(R/f'data/halloween/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'halloween:'+name}],'conditions':condition}]})
    ingredients=['minecraft:dark_oak_planks','minecraft:orange_dye','minecraft:purple_dye']
    if name.endswith('cushion'):ingredients=['minecraft:white_wool','minecraft:string','minecraft:orange_dye' if name.startswith('pumpkin') else 'minecraft:purple_dye','minecraft:black_dye']
    elif name.endswith('statue'):ingredients=['minecraft:stone_bricks','minecraft:stone_bricks','minecraft:black_dye','minecraft:bone' if name.startswith('reaper') else 'minecraft:flint']
    elif name=='pumpkin_scarecrow':ingredients=['minecraft:hay_block','minecraft:stick','minecraft:carved_pumpkin','minecraft:leather']
    elif name=='candle_candelabra':ingredients=['minecraft:iron_nugget','minecraft:iron_nugget','minecraft:candle','minecraft:candle','minecraft:candle']
    # Give each design a distinct recipe instead of conflicting shapeless alternatives.
    extras={'pumpkin_chair':'minecraft:pumpkin','gothic_chair':'minecraft:amethyst_shard','coffin_chair':'minecraft:bone','witch_stool':'minecraft:spider_eye',
            'bat_cushion':'minecraft:phantom_membrane','ghost_cushion':'minecraft:white_dye','spider_cushion':'minecraft:spider_eye',
            'haunted_cabinet':'minecraft:glass','coffin_cabinet':'minecraft:iron_ingot','witch_table':'minecraft:glass_bottle'}
    if name in extras:ingredients.append(extras[name])
    write(R/f'data/halloween/recipe/{name}.json',{'type':'minecraft:crafting_shapeless','category':'building','ingredients':ingredients,'result':{'id':'halloween:'+name,'count':1}})
    textures_list=sorted(textures);bb=[]
    for i,e in enumerate(es):bb.append({**e,'type':'cube','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name+str(i))),'origin':[8,8,8],'autouv':0,
        'faces':{d:({**e['faces'][d],'texture':textures_list.index(e['faces'][d]['texture'][1:])} if d in e['faces'] else {'texture':None,'uv':[0,0,0,0]}) for d in DIRS}})
    write(ROOT/f'art/{name}.bbmodel',{'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':names[name],
        'resolution':{'width':16,'height':16},'elements':bb,'outliner':[e['uuid'] for e in bb],'display':model['display'],
        'textures':[{'id':t,'name':t+'.png','width':16,'height':16,'uv_width':16,'uv_height':16,
            'source':'data:image/png;base64,'+base64.b64encode((A/('textures/'+textures[t].split(':')[1]+'.png')).read_bytes()).decode()} for t in textures_list]})
for lang in ['ru_ru','en_us']:
    p=A/f'lang/{lang}.json';v=json.loads(p.read_text(encoding='utf8'));v.update({'block.halloween.'+n:label if lang=='ru_ru' else n.replace('_',' ').title() for n,label in names.items()});write(p,v)
print('Generated 15 furniture models, recipes, double-block halves and editable Blockbench files.')

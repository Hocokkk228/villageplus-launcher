"""0.7: animated 2x3 cabinets, three-block scarecrow, reaper and ten decorations.
Run after generate_furniture.py and generate_tables.py.
"""
from pathlib import Path
from PIL import Image,ImageDraw
import json,copy,math,base64,uuid
ROOT=Path(__file__).resolve().parents[1];R=ROOT/'src/main/resources';A=R/'assets/halloween'
DIRS=['north','south','west','east','up','down']
def write(p,v):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
def cube(es,n,a,b,t,front=None,top=None):
    es.append({'name':n,'from':a,'to':b,'faces':{d:{'uv':[0,0,16,16],'texture':'#'+(front if d=='north' and front else top if d=='up' and top else t)} for d in DIRS}})
def texture(name,bg,draw):
    im=Image.new('RGB',(32,32),bg);draw(ImageDraw.Draw(im));im.save(A/f'textures/block/{name}.png')
texture('scream_mask',(28,25,35),lambda d:(d.polygon([(11,1),(21,1),(26,7),(24,22),(19,30),(13,30),(8,22),(6,7)],fill=(235,231,212)),
    d.polygon([(9,9),(14,8),(14,12),(12,16),(10,13)],fill=(22,22,28)),d.polygon([(18,8),(23,9),(22,13),(20,16),(18,12)],fill=(22,22,28)),
    d.ellipse((13,17,20,28),fill=(24,21,28)),d.line((16,13,15,17,17,17),fill=(110,106,105),width=1)))
texture('scythe_steel',(97,110,115),lambda d:([d.line((0,y,31,y),fill=(163,179,179),width=1) for y in [2,6,15,25]],d.line((0,30,31,30),fill=(213,224,210))))
texture('spectral_glass',(52,133,115),lambda d:(d.rectangle((2,2,29,29),outline=(94,193,156)),d.line((5,5,5,24),fill=(169,234,187),width=2),d.line((9,4,20,4),fill=(133,218,165))))
texture('old_clock_face',(221,205,165),lambda d:(d.ellipse((1,1,30,30),outline=(77,46,40),width=2),
    [d.rectangle(r,fill=(57,36,40)) for r in [(15,3,16,6),(15,25,16,28),(3,15,6,16),(25,15,28,16)]],
    d.line((16,7,16,16,24,20),fill=(57,36,40),width=2)))
texture('book_pages',(224,211,168),lambda d:([d.line((3,y,13,y),fill=(100,73,57)) for y in [5,9,13,17,21,25]],
    [d.line((18,y,28,y),fill=(100,73,57)) for y in [5,9,13,17,21,25]],d.line((16,1,16,30),fill=(159,132,91),width=2)))
texture('grave_soil',(75,51,34),lambda d:([d.rectangle((x,y,x+2,y+1),fill=(100,70,43)) for x,y in [(2,4),(12,9),(23,2),(19,22),(6,26),(27,15)]],d.line((1,1,12,1),fill=(94,105,51))))
texture('cauldron_iron',(40,40,54),lambda d:(d.line((0,2,31,2),fill=(72,71,85)),d.line((0,28,31,28),fill=(29,29,39)),
    [d.rectangle((x,y,x+1,y+1),fill=(58,57,72)) for x,y in [(3,6),(18,11),(24,21),(8,24)]]))
for name,base,pattern in [('pumpkin_rug','velvet_orange','cushion_pumpkin'),('bat_rug','velvet_purple','cushion_bat'),('spiderweb_rug','velvet_black','cushion_spider')]:
    im=Image.open(A/f'textures/block/{base}.png').resize((32,32),Image.Resampling.NEAREST)
    motif=Image.open(A/f'textures/block/{pattern}.png').resize((24,24),Image.Resampling.NEAREST);im.paste(motif,(4,4))
    d=ImageDraw.Draw(im);d.rectangle((1,1,30,30),outline=(196,138,60));d.rectangle((3,3,28,28),outline=(79,49,49))
    im.save(A/f'textures/block/{name}.png')

display={'gui':{'rotation':[25,155,0],'scale':[.7]*3},'ground':{'translation':[0,3,0],'scale':[.3]*3},
 'firstperson_righthand':{'rotation':[0,135,0],'scale':[.4]*3},'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.3]*3}}
def textures_for(es):
    keys={f['texture'][1:] for e in es for f in e['faces'].values()}
    return {k:('halloween:block/pumpkin_'+k[2:] if k.startswith('p_') else 'halloween:block/'+k) for k in keys}
def model(es,disp=None):return {'ambientocclusion':True,'textures':{**textures_for(es),'particle':'halloween:block/haunted_wood'},'display':disp or display,'elements':es}
def bb(name,es,disp=None):
    tex=textures_for(es);keys=sorted(tex);elements=[]
    for i,e in enumerate(es):elements.append({**e,'type':'cube','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name+str(i))),'origin':[8,8,8],'autouv':0,
        'faces':{d:({**e['faces'][d],'texture':keys.index(e['faces'][d]['texture'][1:])} if d in e['faces'] else {'texture':None,'uv':[0,0,0,0]}) for d in DIRS}})
    write(ROOT/f'art/{name}.bbmodel',{'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},'name':name,
        'resolution':{'width':16,'height':16},'elements':elements,'outliner':[e['uuid'] for e in elements],'display':disp or display,
        'textures':[{'id':t,'name':t+'.png','width':16,'height':16,'uv_width':16,'uv_height':16,
            'source':'data:image/png;base64,'+base64.b64encode((A/('textures/'+tex[t].split(':')[1]+'.png')).read_bytes()).decode()} for t in keys]})
def clip(e,lo,hi,offset):
    a,b=e['from'],e['to'];L=[max(a[i],lo[i]) for i in range(3)];H=[min(b[i],hi[i]) for i in range(3)]
    if any(L[i]>H[i] or (L[i]==H[i] and a[i]!=b[i]) for i in range(3)):return None
    v=copy.deepcopy(e);v['from']=[round(L[i]-offset[i],4) for i in range(3)];v['to']=[round(H[i]-offset[i],4) for i in range(3)]
    for d in list(v['faces']):
        axis=0 if d in ['west','east'] else 1 if d in ['up','down'] else 2
        low=d in ['west','down','north']
        if (low and L[axis]>a[axis]) or (not low and H[axis]<b[axis]):v['faces'].pop(d)
        elif d in ['north','south','west','east'] and b[1]>a[1]:
            u,t,U,T=v['faces'][d]['uv'];v['faces'][d]['uv']=[u,t+(T-t)*(b[1]-H[1])/(b[1]-a[1]),U,t+(T-t)*(b[1]-L[1])/(b[1]-a[1])]
    if 'rotation' in v:v['rotation']['origin']=[v['rotation']['origin'][i]-offset[i] for i in range(3)]
    return v
def swing(e,hinge,angle):
    v=copy.deepcopy(e);pre=90 if angle>45 else -90 if angle<-45 else 0;rest=angle-pre
    if pre:
        c,s=math.cos(math.radians(pre)),math.sin(math.radians(pre));hx,hz=hinge
        pts=[(hx+c*(x-hx)+s*(z-hz),hz-s*(x-hx)+c*(z-hz)) for x in [e['from'][0],e['to'][0]] for z in [e['from'][2],e['to'][2]]]
        v['from'][0],v['to'][0]=min(p[0] for p in pts),max(p[0] for p in pts)
        v['from'][2],v['to'][2]=min(p[1] for p in pts),max(p[1] for p in pts)
        turn={'north':'west','west':'south','south':'east','east':'north'} if pre>0 else {'north':'east','east':'south','south':'west','west':'north'}
        v['faces']={turn.get(d,d):f for d,f in v['faces'].items()}
    if rest:v['rotation']={'origin':[hinge[0],0,hinge[1]],'axis':'y','angle':rest}
    return v

# Three-block scarecrow: central stake and a visible crossbar projecting from both sleeves.
sc=[];cube(sc,'Ground stake',[7,0,7],[9,39,9],'haunted_wood')
for x in [4,9]:cube(sc,'Dangling trouser leg',[x,9,6],[x+3,18,10],'velvet_black');cube(sc,'Straw trouser hem',[x,7,6.3],[x+3,9,9.7],'scarecrow_straw')
cube(sc,'Patchwork coat',[3,17,5],[13,35,11],'scarecrow_coat')
cube(sc,'Left and right wooden crossbar',[-5,30,7],[21,32,9],'haunted_wood')
for x in [-2,12]:cube(sc,'Outstretched sleeve',[x,28,5.5],[x+6,33,10.5],'scarecrow_coat')
cube(sc,'Coat patch',[3.8,20,4.85],[7,26,5],'velvet_orange')
for y in [20,26,32]:cube(sc,'Coat button',[8.5,y,4.7],[9.2,y+1,5],'bucket_brass')
pumpkin=json.loads((A/'models/block/pumpkin_lantern.json').read_text(encoding='utf8'))
for e in pumpkin['elements']:
    v=copy.deepcopy(e)
    for k in ['from','to']:v[k]=[round(8+(v[k][0]-8)*.68,4),round(36+v[k][1]*.68,4),round(8+(v[k][2]-8)*.68,4)]
    for f in v['faces'].values():f['texture']='#p_'+f['texture'][1:]
    sc.append(v)

def cabinet(coffin):
    body=[];doors=[[],[]]
    for x in [1,27]:
        for z in [3,11]:cube(body,'Carved foot',[x,0,z],[x+4,3,z+2],'haunted_wood')
    cube(body,'Base plinth',[0,2,1],[32,4,15],'haunted_wood');cube(body,'Cabinet back',[1,4,13],[31,46,15],'haunted_wood')
    for x in [0,30]:cube(body,'Side frame',[x,4,2],[x+2,46,13],'haunted_wood')
    cube(body,'Gothic crown',[0,46,1],[32,48,15],'haunted_wood')
    for y in [4,17,30,43]:cube(body,'Interior shelf',[2,y,3.5],[30,y+1,13],'haunted_wood')
    # Visible contents are decorative; inventory contents remain in the 54-slot container.
    for x,y in [(5,19),(20,32)]:
        cube(body,'Potion bottle',[x,y,8],[x+3,y+6,11],'witch_bottle');cube(body,'Bottle cork',[x+.7,y+6,8.7],[x+2.3,y+7,10.3],'haunted_wood')
    for side,(x,X) in enumerate([(2,15.8),(16.2,30)]):
        ds=doors[side];fabric='velvet_black' if coffin else 'velvet_purple'
        cube(ds,'Heavy door',[x,4,1.5],[X,45,2.7],'haunted_wood')
        cube(ds,'Inset panel',[x+1,6,1.25],[X-1,43,1.49],fabric)
        if coffin:
            mid=(x+X)/2;cube(ds,'Silver cross stem',[mid-.6,23,.95],[mid+.6,38,1.24],'scythe_steel');cube(ds,'Silver cross bar',[mid-4,32,.95],[mid+4,33.2,1.24],'scythe_steel')
        else:cube(ds,'Ghost door carving',[x+3,28,1.0],[X-3,37,1.24],fabric,front='cushion_ghost')
        hx=X-1.8 if side==0 else x+1.1
        cube(ds,'Brass door pull',[hx,19,.55],[hx+.7,24,1.25],'bucket_brass')
        for y in [8,39]:cube(ds,'Door iron strap',[x+.2,y,1.05],[X-.2,y+.8,1.24],'rusted_iron')
    return body,doors

for name in ['pumpkin_scarecrow','haunted_cabinet','coffin_cabinet']:
    width=1 if name=='pumpkin_scarecrow' else 2
    if width==1:body,doors=sc,[[],[]]
    else:body,doors=cabinet(name=='coffin_cabinet')
    full=body+doors[0]+doors[1];item=copy.deepcopy(full)
    for e in item:
        for k in ['from','to']:e[k][0]-=(width*16/2-8);e[k][1]-=16
    disp=copy.deepcopy(display);disp['gui']['scale']=[.35]*3
    write(A/f'models/block/{name}.json',model(item,disp));bb(name,full,disp)
    variants={}
    for part in range(6):
        col,row=part%width,part//width
        for frame in range(5):
            es=[]
            if row<3:
                for e in body:
                    # Scarecrow crossbar protrudes outside its single occupied column.
                    lo=[-16 if width==1 else col*16,row*16,-16];hi=[32 if width==1 else (col+1)*16,row*16+16,32]
                    v=clip(e,lo,hi,[col*16,row*16,0])
                    if v:es.append(v)
                if width==2:
                    for e in doors[col]:
                        v=clip(e,[-16,row*16,-16],[48,row*16+16,32],[col*16,row*16,0])
                        if v:es.append(swing(v,(2 if col==0 else 14,1.5),(1 if col==0 else -1)*frame*22.5))
            write(A/f'models/block/{name}_part{part}_door{frame}.json',model(es))
            for d,angle in [('north',0),('east',90),('south',180),('west',270)]:variants[f'facing={d},part={part},door={frame}']={'model':f'halloween:block/{name}_part{part}_door{frame}','y':angle}
    write(A/f'blockstates/{name}.json',{'variants':variants})
    write(R/f'data/halloween/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'halloween:'+name}],
        'conditions':[{'condition':'minecraft:survives_explosion'},{'condition':'minecraft:block_state_property','block':'halloween:'+name,'properties':{'part':'0'}}]}]})

# Reaper: elongated screaming mask, hands holding a large, curved steel scythe.
p=A/'models/block/reaper_statue.json';reaper=json.loads(p.read_text(encoding='utf8'))
es=[e for e in reaper['elements'] if e['name'] not in ['Recessed skull face','Scythe haft','Scythe blade','Curved blade tip',
    'Screaming mask','Long scythe haft','Scythe steel blade','Curving blade','Scythe cutting tip','Right arm','Left reaching arm','Bone hand gripping haft']]
cube(es,'Screaming mask',[4.7,20.5,3.7],[11.3,28,4.01],'bone',front='scream_mask')
cube(es,'Long scythe haft',[12.4,1,1.5],[13.5,28.5,2.6],'haunted_wood')
cube(es,'Scythe steel blade',[1,28.2,1.5],[13.5,30,2.5],'scythe_steel')
cube(es,'Curving blade',[0,26,1.5],[3,28.2,2.5],'scythe_steel');cube(es,'Scythe cutting tip',[0,23.5,1.8],[1,26,2.3],'scythe_steel')
cube(es,'Right arm',[11,15,2],[14,19,5],'velvet_black');cube(es,'Left reaching arm',[3,16,2.5],[11,19,5],'velvet_black')
for y in [13,19]:cube(es,'Bone hand gripping haft',[11.8,y,1.1],[14,y+1.8,3],'bone')
reaper=model(es);write(p,reaper);bb('reaper_statue',es)
for half,lo,hi in [('lower',0,16),('upper',16,32)]:write(A/f'models/block/reaper_statue_{half}.json',model([v for e in es if (v:=clip(e,[-16,lo,-16],[32,hi,32],[0,lo,0]))]))

extras={}
for name in ['pumpkin_rug','bat_rug','spiderweb_rug']:
    es=[];cube(es,'Tufted rug',[.5,0,.5],[15.5,.35,15.5],'velvet_black',top=name)
    for x in range(1,16,2):
        for z in [0,15.5]:cube(es,'Woven fringe',[x,0,z],[x+.7,.2,z+.5],'bucket_brass')
    extras[name]=es
es=[]
for x in [3,11]:
    for z in [3,11]:cube(es,'Cauldron foot',[x,0,z],[x+2,3,z+2],'rusted_iron')
cube(es,'Pot base',[4,2,4],[12,3,12],'rusted_iron')
for a,b in [([2,3,5],[3.5,12,11]),([12.5,3,5],[14,12,11]),([5,3,2],[11,12,3.5]),([5,3,12.5],[11,12,14])]:cube(es,'Rounded cauldron side',a,b,'cauldron_iron')
for x in [3.5,11]:
    for z in [3.5,11]:cube(es,'Octagonal pot corner',[x,3,z],[x+1.5,12,z+1.5],'cauldron_iron')
for e in es:
    for f in e['faces'].values():f['texture']='#cauldron_iron'
cube(es,'Green bubbling brew',[4,10,4],[12,10.4,12],'spectral_glass');es[-1].update(shade=False,light_emission=6)
for x,y,z in [(5,11,6),(9,11.5,9),(8,12,5)]:cube(es,'Floating potion bubble',[x,y,z],[x+.7,y+.7,z+.7],'spectral_glass')
extras['witch_cauldron']=es
es=[];cube(es,'Shelf backing',[1,0,12],[15,16,13],'haunted_wood')
for y in [0,7,14]:cube(es,'Apothecary shelf',[1,y,4],[15,y+1,13],'haunted_wood')
for x in [1,14]:cube(es,'Side rail',[x,0,4],[x+1,16,13],'haunted_wood')
for x,y,t in [(3,1,'witch_bottle'),(8,1,'velvet_purple'),(11,8,'witch_bottle'),(4,8,'corn_orange')]:
    cube(es,'Potion flask',[x,y,7],[x+2,y+4,10],t);cube(es,'Flask neck',[x+.5,y+4,8],[x+1.5,y+5,9],t);cube(es,'Cork',[x+.5,y+5,8],[x+1.5,y+5.5,9],'haunted_wood')
extras['potion_shelf']=es
es=[];cube(es,'Pedestal plinth',[3,0,3],[13,2,13],'haunted_wood');cube(es,'Carved lectern stem',[6,2,6],[10,11,10],'haunted_wood')
cube(es,'Lectern top',[2,10.5,2],[14,12,14],'haunted_wood');cube(es,'Purple book cover',[2.5,12,3],[13.5,12.5,13],'velvet_purple')
cube(es,'Open spellbook',[3,12.5,3.5],[13,13.2,12.5],'bone',top='book_pages');cube(es,'Book spine',[7.8,13.2,3],[8.2,13.5,13],'bucket_brass')
extras['spellbook_pedestal']=es
es=[];cube(es,'Clock foot',[3,0,4],[13,2,12],'haunted_wood');cube(es,'Clock body',[4,2,5],[12,13,12],'haunted_wood')
cube(es,'Clock face',[4.5,5,4.8],[11.5,12,5],'bucket_brass',front='old_clock_face');cube(es,'Clock crown',[5,13,5],[11,15,12],'haunted_wood')
cube(es,'Little pumpkin finial',[6.5,15,7],[9.5,16,10],'velvet_orange');extras['haunted_clock']=es
es=[];cube(es,'Mossy stone plinth',[3,0,4],[13,2,12],'statue_stone');cube(es,'Grave cross upright',[6,2,6],[10,16,10],'statue_stone')
cube(es,'Stone cross arms',[2,10,6],[14,13,10],'statue_stone');cube(es,'Old iron plate',[6.6,4,5.85],[9.4,8,6],'rusted_iron');extras['gravestone_cross']=es
es=[];cube(es,'Grave soil',[1,0,1],[15,2,15],'grave_soil');cube(es,'Raised earth',[3,2,3],[13,3.5,13],'grave_soil')
cube(es,'Emerging bone wrist',[7,3.5,8],[9,4.5,10],'bone')
for x in [5.5,7,8.5,10]:cube(es,'Skeleton finger',[x,3.5,6],[x+.6,5,9],'bone')
extras['grave_mound']=es
es=[];cube(es,'Lantern base',[4,0,4],[12,2,12],'rusted_iron');cube(es,'Amber lantern glass',[5,2,5],[11,10,11],'corn_orange');es[-1].update(shade=False,light_emission=15)
for x in [4,11]:
    for z in [4,11]:cube(es,'Lantern iron upright',[x,2,z],[x+1,11,z+1],'rusted_iron')
cube(es,'Lantern cap',[3.5,10,3.5],[12.5,12,12.5],'rusted_iron')
for x in [1,12]:cube(es,'Bat wing',[x,6,6],[x+3,11,9],'velvet_black');cube(es,'Wing tip',[x,10,6],[x+1,13,9],'velvet_black')
cube(es,'Hanging loop',[7,12,7],[9,16,9],'rusted_iron');extras['bat_lantern']=es
names={'pumpkin_rug':'Ковёр с тыквой','bat_rug':'Ковёр с летучей мышью','spiderweb_rug':'Ковёр с паутиной',
 'witch_cauldron':'Ведьмин котёл','potion_shelf':'Полка с зельями','spellbook_pedestal':'Пюпитр с гримуаром','haunted_clock':'Жуткие часы',
 'gravestone_cross':'Могильный крест','grave_mound':'Могилка с костяной рукой','bat_lantern':'Фонарь-летучая мышь'}
ingredients={'pumpkin_rug':['minecraft:orange_wool','minecraft:black_dye','minecraft:pumpkin'],
 'bat_rug':['minecraft:purple_wool','minecraft:black_dye','minecraft:flint'],
 'spiderweb_rug':['minecraft:black_wool','minecraft:string','minecraft:string'],
 'witch_cauldron':['minecraft:cauldron','minecraft:slime_ball'],
 'potion_shelf':['minecraft:dark_oak_planks','minecraft:glass_bottle','minecraft:nether_wart'],
 'spellbook_pedestal':['minecraft:dark_oak_planks','minecraft:book','minecraft:purple_dye'],
 'haunted_clock':['minecraft:dark_oak_planks','minecraft:clock','minecraft:pumpkin'],
 'gravestone_cross':['minecraft:stone_bricks','minecraft:stone_bricks','minecraft:iron_nugget'],
 'grave_mound':['minecraft:dirt','minecraft:bone','minecraft:bone'],
 'bat_lantern':['minecraft:lantern','minecraft:black_wool','minecraft:flint']}
for name,es in extras.items():
    write(A/f'models/block/{name}.json',model(es));bb(name,es)
    write(A/f'items/{name}.json',{'model':{'type':'minecraft:model','model':'halloween:block/'+name}})
    write(A/f'blockstates/{name}.json',{'variants':{f'facing={d}':{'model':'halloween:block/'+name,'y':a} for d,a in [('north',0),('east',90),('south',180),('west',270)]}})
    write(R/f'data/halloween/recipe/{name}.json',{'type':'minecraft:crafting_shapeless','category':'building','ingredients':ingredients[name],'result':{'id':'halloween:'+name,'count':1}})
    write(R/f'data/halloween/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'halloween:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for lang in ['ru_ru','en_us']:
    p=A/f'lang/{lang}.json';v=json.loads(p.read_text(encoding='utf8'));v.update({'block.halloween.'+k:label if lang=='ru_ru' else k.replace('_',' ').title() for k,label in names.items()});write(p,v)
print('Generated 10 new decorations, 2 animated 2x3 cabinets, 3-block scarecrow and revised reaper.')

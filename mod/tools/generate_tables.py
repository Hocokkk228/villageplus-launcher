"""Large multiblock tables and a candelabra built with Minecraft's candle model/UVs."""
from pathlib import Path
from zipfile import ZipFile
import json,copy
ROOT=Path(__file__).resolve().parents[1];R=ROOT/'src/main/resources';A=R/'assets/halloween'
def write(p,v):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
dirs=['north','south','west','east','up','down']
def cube(es,n,a,b,t):es.append({'name':n,'from':a,'to':b,'faces':{d:{'uv':[0,0,16,16],'texture':'#'+t} for d in dirs}})
display={'gui':{'rotation':[25,155,0],'scale':[.4]*3},'ground':{'translation':[0,3,0],'scale':[.2]*3},
 'firstperson_righthand':{'rotation':[0,135,0],'scale':[.25]*3},'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.25]*3}}
for name,width in [('ritual_table',2),('banquet_table',3)]:
    es=[];W=width*16
    cube(es,'Thick joined tabletop',[0,12,0],[W,14,32],'wood')
    for x in [2,W-5]:
        for z in [2,27]:
            cube(es,'Carved corner leg',[x,0,z],[x+3,12,z+3],'wood')
            cube(es,'Iron leg collar',[x-.15,2,z-.15],[x+3.15,3,z+3.15],'iron')
    for z in [1,29.5]:cube(es,'Long carved apron',[1,9,z],[W-1,12,z+1.5],'wood')
    for x in [1,W-2.5]:cube(es,'End apron',[x,9,1],[x+1.5,12,31],'wood')
    cube(es,'Purple cloth runner',[W/2-4,14.02,0],[W/2+4,14.12,32],'velvet')
    for z in [0,31.5]:cube(es,'Hanging runner ends',[W/2-4,8,z],[W/2+4,14,z+.5],'velvet')
    if width==2:
        cube(es,'Embroidered pumpkin crest',[W/2-3,14.13,13],[W/2+3,14.15,19],'pattern')
    else:
        for z in [7,21]:cube(es,'Embroidered bat crest',[W/2-3,14.13,z],[W/2+3,14.15,z+5],'pattern')
    tex={'wood':'halloween:block/haunted_wood','iron':'halloween:block/rusted_iron','velvet':'halloween:block/velvet_purple',
         'pattern':'halloween:block/cushion_'+('pumpkin' if width==2 else 'bat'),'particle':'halloween:block/haunted_wood'}
    # Each occupied cell owns only its local geometry; no oversized model bounds in the world.
    for part in range(6):
        elements=[];px,pz=part%width,part//width
        if part<width*2:
            for e in es:
                a,b=e['from'],e['to'];lo=[max(a[0],px*16),a[1],max(a[2],pz*16)];hi=[min(b[0],px*16+16),b[1],min(b[2],pz*16+16)]
                if any(lo[k]>=hi[k] for k in range(3)):continue
                v=copy.deepcopy(e);v['from']=[round(lo[0]-px*16,4),lo[1],round(lo[2]-pz*16,4)];v['to']=[round(hi[0]-px*16,4),hi[1],round(hi[2]-pz*16,4)]
                # Hide artificial slice faces between neighboring sections.
                for d,k,side in [('west',0,0),('east',0,1),('north',2,0),('south',2,1)]:
                    if (side==0 and lo[k]>a[k]) or (side==1 and hi[k]<b[k]):v['faces'].pop(d,None)
                elements.append(v)
        write(A/f'models/block/{name}_{part}.json',{'ambientocclusion':True,'textures':tex,'elements':elements})
    item=copy.deepcopy(es)
    for e in item:
        for k in ['from','to']:e[k][0]-=W/2-8;e[k][2]-=8
    write(A/f'models/block/{name}.json',{'ambientocclusion':True,'textures':tex,'display':display,'elements':item})
    write(A/f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'halloween:block/{name}'}})
    write(A/f'blockstates/{name}.json',{'variants':{f'facing={d},part={p}':{'model':f'halloween:block/{name}_{p}','y':ang}
        for d,ang in [('north',0),('east',90),('south',180),('west',270)] for p in range(6)}})
    write(R/f'data/halloween/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'building',
        'pattern':['PPP',' W ','S S'],'key':{'P':'minecraft:dark_oak_planks','W':'minecraft:purple_wool' if width==2 else 'minecraft:orange_wool','S':'minecraft:dark_oak_fence'},'result':{'id':'halloween:'+name,'count':1}})
    write(R/f'data/halloween/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'halloween:'+name}],
        'conditions':[{'condition':'minecraft:survives_explosion'},{'condition':'minecraft:block_state_property','block':'halloween:'+name,'properties':{'part':'0'}}]}]})

with ZipFile(Path.home()/'.gradle/caches/fabric-loom/1.21.11/minecraft-client.jar') as z:
    template=json.loads(z.read('assets/minecraft/models/block/template_candle.json'))
old=json.loads((A/'models/block/candle_candelabra.json').read_text(encoding='utf8'))
# Retain just the iron stand; replace the previous custom wax and orange cuboid flames.
stand=[e for e in old['elements'] if e['name'] in ['Iron foot','Twisted upright','Candelabra arms']]
for lit in [False,True]:
    elements=copy.deepcopy(stand)
    for x,y in [(3,8.5),(8,10),(13,8.5)]:
        for original in template['elements']:
            e=copy.deepcopy(original);e['name']='Vanilla candle'
            for key in ['from','to']:e[key]=[e[key][0]+x-8,e[key][1]+y,e[key][2]]
            if 'rotation' in e:
                o=e['rotation']['origin'];e['rotation']['origin']=[o[0]+x-8,o[1]+y,o[2]]
            for face in e['faces'].values():face.pop('cullface',None)
            elements.append(e)
    model={'ambientocclusion':True,'textures':{'rusted_iron':'halloween:block/rusted_iron',
        'all':'minecraft:block/candle'+('_lit' if lit else ''),'particle':'minecraft:block/candle'},
        'display':old['display'],'elements':elements}
    write(A/f'models/block/candle_candelabra_{"lit" if lit else "unlit"}.json',model)
write(A/'blockstates/candle_candelabra.json',{'variants':{'lit=false':{'model':'halloween:block/candle_candelabra_unlit'},'lit=true':{'model':'halloween:block/candle_candelabra_lit'}}})
write(A/'items/candle_candelabra.json',{'model':{'type':'minecraft:model','model':'halloween:block/candle_candelabra_unlit'}})
for lang,labels in [('ru_ru',{'ritual_table':'Большой ритуальный стол 2×2','banquet_table':'Хеллоуинский банкетный стол 3×2'}),
                    ('en_us',{'ritual_table':'Large Ritual Table 2x2','banquet_table':'Halloween Banquet Table 3x2'})]:
    p=A/f'lang/{lang}.json';v=json.loads(p.read_text(encoding='utf8'));v.update({'block.halloween.'+n:s for n,s in labels.items()});write(p,v)
print('Generated real 2x2/3x2 tables and vanilla candle geometry/UVs for lit/unlit candelabra.')

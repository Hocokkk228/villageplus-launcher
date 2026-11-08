"""Generate a reusable Minecraft era-template and honest block-model previews."""
import json
import os
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageColor

ROOT = Path(__file__).resolve().parents[1]
SIZE = (29, 22, 21)
blocks = {}
def put(x,y,z,name,**props):
    assert 0 <= x < SIZE[0] and 0 <= y < SIZE[1] and 0 <= z < SIZE[2]
    blocks[x,y,z] = (name, tuple(sorted((k,str(v).lower()) for k,v in props.items())))
def box(x0,y0,z0,x1,y1,z1,name):
    for x in range(x0,x1+1):
        for y in range(y0,y1+1):
            for z in range(z0,z1+1): put(x,y,z,name)

# Full interior air volume makes the footprint explicit to future placement code.
box(0,0,0,28,21,20,'air')
box(0,0,0,28,0,20,'stone_bricks')
box(1,0,1,27,0,19,'polished_andesite')
# Main hall: brickwork, structural pilasters, large iron-framed windows.
for x in range(1,28):
    for z in range(1,20):
        if x in (1,27) or z in (1,19):
            for y in range(1,10):
                material = 'bricks' if (x*11+y*7+z*3)%17 else 'terracotta'
                if y in (1,8,9): material = 'stone_bricks'
                if (x in (1,27) and z%5==1) or (z in (1,19) and x%5==1): material='polished_andesite'
                put(x,y,z,material)
for z in (1,19):
    for x in (4,9,19,24):
        for xx in (x,x+1):
            for y in (4,5,6,7): put(xx,y,z,'glass_pane')
for x in (1,27):
    for z in (4,9,14):
        for zz in (z,z+1):
            for y in (4,5,6,7): put(x,y,zz,'glass_pane')
# Broad loading entrance with two-block clearance.
for x in range(12,17):
    for y in range(1,5): put(x,y,1,'air')
for x in range(11,18): put(x,5,1,'polished_andesite')
for x in range(12,17): put(x,1,0,'stone_brick_slab',type='bottom')
# Three roof bays and glass skylights; stepped silhouette.
for x in range(0,29):
    for z in range(0,21):
        ridge=min(x%9,8-x%9)
        y=10+ridge//2
        put(x,y,z,'glass' if x%9 in (4,) and 4<=z<=16 else 'deepslate_tiles')
        if z in (0,20):
            for yy in range(10,y):put(x,yy,z,'bricks')
# Chimney is outside the hall wall with a dark stone cap.
box(23,1,15,26,18,18,'bricks')
for y in range(3,19):
    for x in (24,25):
        for z in (16,17):put(x,y,z,'air')
for x in range(22,28):
    for z in range(14,20):
        if x in (22,27) or z in (14,19): put(x,19,z,'polished_blackstone_bricks')
# Partition walls: two stores flank the central production hall. Doorways stay open.
for x in (8,20):
    for z in range(2,19):
        for y in range(1,5):
            if z in (9,10,11) and y<=3: continue
            put(x,y,z,'stone_bricks' if y==1 else 'bricks')
# Warehouse stock racks, leaving a clear central passage.
for x in (3,6,22,25):
    for z in (4,6,13,15):
        put(x,1,z,'barrel',facing='up',open='false')
        put(x,2,z,'spruce_slab',type='bottom')
for x in (3,6,22,25):
    for z in (3,7,12,16): put(x,1,z,'chest',facing='south',type='single',waterlogged='false')
# The workshop has furnaces, equipment and a central processing line.
for z in (5,8,12,15):
    put(10,1,z,'blast_furnace',facing='east',lit='false')
    put(11,1,z,'hopper',facing='down',enabled='true')
    put(17,1,z,'smithing_table')
    put(18,1,z,'barrel',facing='up',open='false')
for z in range(4,17):
    put(14,1,z,'polished_deepslate')
    if z in (5,8,12,15):put(14,2,z,'light_weighted_pressure_plate',power='0')
for x in (5,14,23):
    for z in (4,10,16):
        roof_y=10+min(x%9,8-x%9)//2
        for y in range(8,roof_y):put(x,y,z,'chain',axis='y',waterlogged='false')
        put(x,7,z,'lantern',hanging='true',waterlogged='false')
# Ceramic bands and lintels emphasize the industrial style.
for x in range(2,27):
    if x%4 in (0,1):put(x,3,1,'orange_terracotta')
# Deep masonry piers, lintels, loading awning and a carved ceramic facade.
for x in (1,8,20,27):
    for y in range(1,10):put(x,y,0,'stone_bricks' if y%3 else 'chiseled_stone_bricks')
for x in range(9,20):
    put(x,6,0,'stone_brick_slab',type='bottom')
    if x in (10,18):
        for y in range(1,6):put(x,y,0,'stone_brick_wall')
for x in range(11,18):put(x,8,0,'orange_terracotta' if x%2 else 'yellow_terracotta')
for z in (1,6,11,16,19):
    for y in range(1,9):put(0,y,z,'stone_bricks' if y%4 else 'chiseled_stone_bricks')
for z in range(2,19):
    if z%3==0:put(0,3,z,'orange_terracotta')
for x in range(23,27):
    for z in range(15,19):
        if x in (23,26) or z in (15,18):
            for y in (12,16,18):put(x,y,z,'stone_bricks')

palette=[]; indices={}; out=[]
for (x,y,z),state in sorted(blocks.items(),key=lambda e:(e[0][1],e[0][0],e[0][2])):
    if state not in indices:
        indices[state]=len(palette);palette.append({'name':'minecraft:'+state[0], 'properties':dict(state[1])})
    out.append([x,y,z,indices[state]])
path=ROOT/'src/main/resources/data/halloween/era_templates/steam_factory.json'
path.write_text(json.dumps({'size':list(SIZE),'palette':palette,'blocks':out},separators=(',',':')),encoding='utf-8')

colors={'air':None,'stone_bricks':'#a7a39a','chiseled_stone_bricks':'#999a94','stone_brick_wall':'#aaa9a3','yellow_terracotta':'#c39a58','polished_andesite':'#a9aaa6','bricks':'#aa5544','terracotta':'#b66c51','orange_terracotta':'#cd8357','glass_pane':'#a6cfd4','glass':'#add6d9','deepslate_tiles':'#424b53','polished_blackstone_bricks':'#34333b','barrel':'#93673f','spruce_slab':'#97724b','chest':'#b3844c','blast_furnace':'#676d6c','hopper':'#4f5558','smithing_table':'#79563f','polished_deepslate':'#596168','light_weighted_pressure_plate':'#d5b852','chain':'#777f80','lantern':'#f7bb55','stone_brick_slab':'#aaa9a3'}
texture_names={'stone_brick_wall':'stone_bricks','stone_brick_slab':'stone_bricks','glass_pane':'glass','spruce_slab':'spruce_planks','barrel':'barrel_side','chest':'oak_planks','blast_furnace':'blast_furnace_front','hopper':'hopper_outside','smithing_table':'smithing_table_side','chain':'iron_block','lantern':'gold_block','light_weighted_pressure_plate':'gold_block'}
jar=Path(os.environ['USERPROFILE'])/'.gradle/caches/fabric-loom/1.21.11/minecraft-client.jar'
textures={}
if jar.exists():
    from io import BytesIO
    with zipfile.ZipFile(jar) as archive:
        for name in colors:
            tex=texture_names.get(name,name)
            try: textures[name]=Image.open(BytesIO(archive.read('assets/minecraft/textures/block/'+tex+'.png'))).convert('RGBA').crop((0,0,16,16))
            except KeyError: pass
def face_tile(name,face,scale):
    """Project a texture onto one isometric face. Thin blocks use a full cube silhouette."""
    import numpy as np
    texture=textures.get(name)
    if texture is None:return None
    # Pixel centres inside a parallelogram, converted back to texture coordinates.
    result=Image.new('RGBA',(scale*2+1,scale*2+1)); pix=result.load(); src=texture.load()
    for yy in range(scale*2+1):
        for xx in range(scale*2+1):
            X=xx-scale;Y=yy
            if face=='top': u=X/(2*scale)+Y/scale;v=Y/scale-X/(2*scale)
            elif face=='right': u=1-X/scale;v=(Y-(1-u)*scale/2)/scale
            else: u=1+X/scale;v=(Y-u*scale/2)/scale
            if 0<=u<1 and 0<=v<1:
                c=src[min(15,int(u*16)),min(15,int(v*16))]; shade={'top':1,'right':.72,'left':.85}[face]
                if c[3]:pix[xx,yy]=(int(c[0]*shade),int(c[1]*shade),int(c[2]*shade),255)
    return result
def render(filename,cut=False):
    # Isometric block diagram, not a Minecraft screenshot. Remove roof and front wall for the cutaway.
    cells={(x,y,z):s[0] for (x,y,z),s in blocks.items() if s[0]!='air' and not (cut and (y>=9 or z==1 or z==0))}
    scale=15; ox=580; oy=210; im=Image.new('RGB',(1150,850),'#d9e1df'); d=ImageDraw.Draw(im)
    tiles={(v,f):face_tile(v,f,scale) for v in set(cells.values()) for f in ('top','right','left')}
    def pt(x,y,z):return (ox+(x-z)*scale,oy+(x+z)*scale*.5-y*scale)
    def color(v,k):return tuple(int(t*k) for t in ImageColor.getrgb(colors[v]))
    for (x,y,z),v in sorted(cells.items(),key=lambda e:(e[0][0]+e[0][2],e[0][1])):
        a,c,e=pt(x+1,y,z),pt(x+1,y,z+1),pt(x,y,z+1)
        q,r,t,u=pt(x,y+1,z),pt(x+1,y+1,z),pt(x+1,y+1,z+1),pt(x,y+1,z+1)
        for face,visible,poly,shade in (('right',(x+1,y,z) not in cells,(a,c,t,r),.72),('left',(x,y,z+1) not in cells,(e,c,t,u),.85),('top',(x,y+1,z) not in cells,(q,r,t,u),1)):
            if visible:
                tile=tiles[v,face]
                d.polygon(poly,fill=color(v,shade),outline=color(v,shade*.9))
                if tile: im.paste(tile,(int(q[0]-scale),int(q[1])),tile)
    im.save(ROOT/'preview'/filename)
render('steam_factory_exterior.png')
render('steam_factory_cutaway.png',True)
print(f'{path}: {len(out)} blocks; footprint {SIZE[0]}x{SIZE[2]}, height {SIZE[1]}')

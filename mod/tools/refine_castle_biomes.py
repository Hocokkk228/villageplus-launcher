from pathlib import Path
import nbtlib as n, json
R=Path('src/main/resources'); p=R/'data/halloween/structure/village/plains/royal_castle.nbt';f=n.load(p);S=n.String;I=n.Int;C=n.Compound;L=n.List
pal=f['palette']; blocks={tuple(map(int,b['pos'])):b for b in f['blocks']}
def put(x,y,z,name,props=None):
 st=C({'Name':S('minecraft:'+name)})
 if props:st['Properties']=C({k:S(v) for k,v in props.items()})
 try:i=pal.index(st)
 except ValueError:i=len(pal);pal.append(st)
 blocks[x,y,z]=C({'pos':L[I]([x,y,z]),'state':I(i)})
# Entrances through both rings and into every tower.
for x,z in [(21,2),(21,40),(2,21),(40,21),(21,11),(21,31),(11,21),(31,21)]:
 for d in (-1,0,1):
  for y in range(1,4):
   if x in (2,11,31,40):put(x,y,z+d,'air')
   else:put(x+d,y,z,'air')
for tx,tz in ((2,2),(36,2),(2,36),(36,36),(11,11),(27,11),(11,27),(27,27)):
 x=tx+2;z=tz+4 if tz<20 else tz
 for y in (1,2,3):put(x,y,z,'air')
# Sleeping gallery above the hall with a ladder and actual bed.
for x in range(17,26):
 for z in range(17,27):put(x,7,z,'spruce_planks')
for y in range(2,9):put(17,y,20,'ladder',{'facing':'east','waterlogged':'false'})
put(17,7,20,'air');put(17,8,20,'air')
for z,part in ((24,'foot'),(25,'head')):put(22,8,z,'purple_bed',{'facing':'south','occupied':'false','part':part})
for x,z in ((19,18),(24,18),(19,25),(24,25)):put(x,8,z,'lantern',{'hanging':'false','waterlogged':'false'})
f['blocks']=L[C](list(blocks.values()));f.save(p,gzipped=True)
# Biome-specific material palettes while retaining coherent original block properties.
maps={
'desert':{'stone_bricks':'cut_sandstone','cracked_stone_bricks':'sandstone','mossy_stone_bricks':'smooth_sandstone','tuff_bricks':'sandstone','polished_tuff':'smooth_sandstone','chiseled_stone_bricks':'chiseled_sandstone','polished_andesite':'smooth_sandstone','cobblestone':'sandstone','stone_brick_wall':'sandstone_wall','cobblestone_wall':'sandstone_wall','stone_brick_stairs':'sandstone_stairs','stone_brick_slab':'sandstone_slab','spruce_planks':'birch_planks','dark_oak_planks':'jungle_planks','dark_oak_stairs':'jungle_stairs','dark_oak_slab':'jungle_slab','purple_stained_glass':'orange_stained_glass'},
'taiga':{'stone_bricks':'cobblestone','cracked_stone_bricks':'mossy_cobblestone','mossy_stone_bricks':'mossy_cobblestone','tuff_bricks':'cobblestone','polished_tuff':'stone_bricks','polished_andesite':'stone','chiseled_stone_bricks':'stone_bricks'},
'snowy':{'stone_bricks':'stone_bricks','mossy_stone_bricks':'andesite','tuff_bricks':'polished_andesite','polished_tuff':'polished_andesite','purple_stained_glass':'light_blue_stained_glass'},
'savanna':{'stone_bricks':'cut_sandstone','mossy_stone_bricks':'terracotta','tuff_bricks':'orange_terracotta','polished_tuff':'smooth_sandstone','stone_brick_stairs':'sandstone_stairs','stone_brick_slab':'sandstone_slab','stone_brick_wall':'sandstone_wall','purple_stained_glass':'orange_stained_glass'}}
for biome,m in maps.items():
 f=n.load(p)
 for st in f['palette']:
  name=str(st['Name']).split(':')[-1]
  if name in m:st['Name']=S('minecraft:'+m[name])
 for b in f['blocks']:
  if 'nbt' in b and 'pool' in b['nbt']:
   b['nbt']['pool']=S(str(b['nbt']['pool']).replace('village/plains/','village/'+biome+'/'))
 for e in f['entities']:
  if 'VillagerData' in e['nbt']:e['nbt']['VillagerData']['type']=S('minecraft:'+biome)
 q=R/f'data/halloween/structure/village/{biome}/royal_castle.nbt';q.parent.mkdir(parents=True,exist_ok=True);f.save(q,gzipped=True)
 pool={'elements':[{'element':{'element_type':'minecraft:single_pool_element','location':f'halloween:village/{biome}/royal_castle','processors':'minecraft:empty','projection':'rigid'},'weight':1}],'fallback':'minecraft:empty'}
 q=R/f'data/minecraft/worldgen/template_pool/village/{biome}/town_centers.json';q.parent.mkdir(parents=True,exist_ok=True);q.write_text(json.dumps(pool,indent=2)+'\n')
# Larger separation reduces overlapping villages and duplicate castles.
q=R/'data/minecraft/worldgen/structure_set/villages.json';q.parent.mkdir(parents=True,exist_ok=True)
q.write_text(json.dumps({'placement':{'type':'minecraft:random_spread','salt':10387312,'separation':24,'spacing':48},'structures':[{'structure':'minecraft:village_'+b,'weight':1} for b in ('plains','desert','savanna','snowy','taiga')]},indent=2)+'\n')

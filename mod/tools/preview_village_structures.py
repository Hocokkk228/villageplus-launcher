from pathlib import Path
import nbtlib as n
from PIL import Image,ImageDraw,ImageColor
R=Path('src/main/resources/data/halloween/structure/village');out=Path('preview/new_structures');out.mkdir(parents=True,exist_ok=True)
colors={'deepslate_bricks':'#4d4e51','deepslate_tiles':'#53545a','spruce_fence':'#795639','lectern':'#a67a4b','moss_block':'#6d9049','oak_leaves':'#6e9455','air':None,'stone_bricks':'#90908a','cracked_stone_bricks':'#777774','mossy_stone_bricks':'#7d8474','tuff_bricks':'#73796a','polished_tuff':'#929989','polished_andesite':'#aaa9a2','chiseled_stone_bricks':'#7d7e7b','cobblestone':'#858581','mossy_cobblestone':'#77816c','stone':'#91918b','stone_brick_stairs':'#9d9d97','stone_brick_slab':'#9d9d97','stone_brick_wall':'#9d9d97','cut_sandstone':'#d1ba7f','sandstone':'#dec990','smooth_sandstone':'#e6d6a5','chiseled_sandstone':'#ddc88e','sand':'#ddca95','terracotta':'#a96952','orange_terracotta':'#b56a42','dark_oak_planks':'#543a2d','dark_oak_stairs':'#604634','dark_oak_slab':'#604634','spruce_planks':'#8a6542','spruce_stairs':'#80603e','spruce_slab':'#80603e','stripped_spruce_log':'#916749','spruce_fence':'#795639','birch_planks':'#c5b27e','jungle_planks':'#ad805d','jungle_stairs':'#a47a55','jungle_slab':'#a47a55','glass':'#b9d9dd','orange_stained_glass':'#e3a858','purple_stained_glass':'#a86db4','light_blue_stained_glass':'#a3cde0','iron_bars':'#9b9d9b','purple_carpet':'#792e89','purple_bed':'#8a3b98','red_carpet':'#b12737','gold_block':'#eec641','lantern':'#ffc552','bookshelf':'#af8860','jigsaw':None,'ladder':'#bd9867','bell':'#f3c747'}
colors.update({'deepslate_bricks':'#51545a','cracked_deepslate_bricks':'#43464d','polished_deepslate':'#686b70','deepslate_tiles':'#464951','cracked_deepslate_tiles':'#383b43','cobbled_deepslate':'#585a60','polished_blackstone_bricks':'#343038','cracked_polished_blackstone_bricks':'#26242b','polished_blackstone':'#4a424c','polished_blackstone_brick_stairs':'#37323b','deepslate_brick_stairs':'#565961','polished_deepslate_stairs':'#676a70','mossy_stone_brick_stairs':'#78846e','chiseled_tuff_bricks':'#858d7a','bricks':'#ad6250'})
def draw(path,name):
 f=n.load(path);pal=[str(x['Name']).split(':')[-1] for x in f['palette']];cells={tuple(map(int,b['pos'])):pal[int(b['state'])] for b in f['blocks'] if colors.get(pal[int(b['state'])],'#8b8b84') is not None};sx,sy,sz=map(int,f['size']);scale=12 if max(sx,sz)>30 else 19;ox=700;oy=260+scale*max(0,sy-15);im=Image.new('RGB',(1400,1000),'#c5dce1');d=ImageDraw.Draw(im)
 def pt(x,y,z):return (ox+(x-z)*scale,oy+(x+z)*scale*.5-y*scale)
 def col(n,k):return tuple(int(v*k) for v in ImageColor.getrgb(colors.get(n,'#858681')))
 for (x,y,z),v in sorted(cells.items(),key=lambda t:(t[0][0]+t[0][2],t[0][1])):
  a=pt(x+1,y,z);c=pt(x+1,y,z+1);e=pt(x,y,z+1);q=pt(x,y+1,z);r=pt(x+1,y+1,z);t=pt(x+1,y+1,z+1);u=pt(x,y+1,z+1)
  if (x+1,y,z) not in cells:d.polygon([a,c,t,r],fill=col(v,.72),outline=col(v,.64))
  if (x,y,z+1) not in cells:d.polygon([e,c,t,u],fill=col(v,.86),outline=col(v,.76))
  if (x,y+1,z) not in cells:d.polygon([q,r,t,u],fill=col(v,1),outline=col(v,.85))
 im.save(out/name)
for biome in ('plains','desert','savanna','snowy','taiga'):draw(R/biome/'royal_castle.nbt',f'castle-{biome}.png')
for source,name in [('houses/fighting_arena.nbt','arena.png'),('houses/mansion.nbt','mansion.png'),('dungeon.nbt','dungeon.png')]:draw(R/'plains'/source,name)

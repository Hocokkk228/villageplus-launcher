from pathlib import Path
import glob,zipfile,io,random,json
from PIL import Image,ImageDraw
R=Path('src/main/resources/assets/halloween');z=zipfile.ZipFile(glob.glob('.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged*/*/*.jar')[0]);random.seed(712)
def vanilla(name):return Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/'+name+'.png'))).convert('RGBA')
def save(im,name):im.save(R/f'textures/block/builder_workbench_{name}.png')
# The vanilla table supplies a pixel-level wood grain and lighting consistent with the game.
top=vanilla('crafting_table_top');d=ImageDraw.Draw(top)
# Tucked blueprint with folded corner and a small architectural plan.
d.rectangle((2,2,8,8),fill='#d4c39d');d.line((2,8,8,8),fill='#8e7659');d.line((8,2,8,7),fill='#a38e6c');d.point((3,3),fill='#ede0bb')
d.line((4,4,7,4),fill='#6c8790');d.line((4,5,4,7),fill='#6c8790');d.line((4,7,7,7),fill='#6c8790');d.point((6,6),fill='#6c8790')
# Ruler, small hammer and metal head, with highlight and shadow.
d.line((2,12,9,12),fill='#d5aa61');d.line((3,11,9,11),fill='#8b6338');d.point((4,12),fill='#695034');d.point((7,12),fill='#695034')
d.line((11,10,13,13),fill='#493626',width=2);d.line((10,8,13,8),fill='#575a56');d.line((10,7,13,7),fill='#a6aaa0');d.point((14,8),fill='#303b38');save(top,'top')
front=vanilla('crafting_table_front');d=ImageDraw.Draw(front)
# Small set square and hammer on the front panel, keeping surrounding vanilla grain.
d.line((3,5,3,10),fill='#d8aa68');d.line((3,10,7,10),fill='#d8aa68');d.line((4,6,4,9),fill='#6e5034');d.point((5,9),fill='#ead19d')
d.line((10,7,12,11),fill='#503728');d.line((9,6,12,6),fill='#b7b9aa');d.line((9,7,12,7),fill='#737972');save(front,'front')
side=vanilla('crafting_table_side');d=ImageDraw.Draw(side)
# A few iron nails and a folded tool; leave the oak board texture legible.
d.point((2,2),fill='#c5c6b6');d.point((13,2),fill='#c5c6b6');d.line((9,6,12,9),fill='#565c57');d.point((12,9),fill='#d4d4c4');save(side,'side')
model={'parent':'minecraft:block/cube','textures':{'particle':'halloween:block/builder_workbench_side','down':'minecraft:block/oak_planks','up':'halloween:block/builder_workbench_top','north':'halloween:block/builder_workbench_front','south':'halloween:block/builder_workbench_front','west':'halloween:block/builder_workbench_side','east':'halloween:block/builder_workbench_side'}}
(R/'models/block/builder_workbench.json').write_text(json.dumps(model,indent=2)+'\n')
# Face previews enlarged with nearest-neighbour sampling: still the actual 16px textures.
out=Image.new('RGB',(3*512,560),'#d6e2e6');d=ImageDraw.Draw(out)
for i,(name,im) in enumerate((('TOP',top),('FRONT',front),('SIDE',side))):
 out.paste(im.convert('RGB').resize((512,512),Image.Resampling.NEAREST),(i*512,48));d.text((i*512+12,13),name,fill='#1d272b')
Path('preview/new_structures').mkdir(exist_ok=True);out.save('preview/new_structures/builder-workbench.png')

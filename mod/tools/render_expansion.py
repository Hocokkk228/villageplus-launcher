from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,copy
from render_pumpkin import render
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/halloween'
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',18)
extras=[('pumpkin_rug','Ковёр с тыквой'),('bat_rug','Ковёр с мышью'),('spiderweb_rug','Ковёр с паутиной'),('witch_cauldron','Ведьмин котёл'),('potion_shelf','Полка с зельями'),
 ('spellbook_pedestal','Пюпитр с гримуаром'),('haunted_clock','Жуткие часы'),('gravestone_cross','Могильный крест'),('grave_mound','Могилка с рукой'),('bat_lantern','Фонарь-летучая мышь')]
sheet=Image.new('RGB',(1500,640),(22,25,30))
for i,(name,label) in enumerate(extras):
    render(24,32 if 'rug' in name else 22,name+'-preview.png',model_name=name)
    im=Image.open(ROOT/'art'/f'{name}-preview.png').resize((300,282),Image.Resampling.LANCZOS);x,y=i%5*300,i//5*320
    sheet.paste(im,(x,y));ImageDraw.Draw(sheet).text((x+150,y+288),label,font=font,fill=(235,223,201),anchor='mt')
sheet.save(ROOT/'art/expansion-decor-preview.png')
def cabinet(name,frame):
    result={'textures':{},'elements':[]}
    for part in range(6):
        m=json.loads((A/f'models/block/{name}_part{part}_door{frame}.json').read_text(encoding='utf8'));result['textures'].update(m['textures'])
        offset=[part%2*16-8,part//2*16-16,0]
        for e in m['elements']:
            v=copy.deepcopy(e)
            for k in ['from','to']:v[k]=[v[k][i]+offset[i] for i in range(3)]
            if 'rotation' in v:v['rotation']['origin']=[v['rotation']['origin'][i]+offset[i] for i in range(3)]
            result['elements'].append(v)
    return result
sheet=Image.new('RGB',(1500,500),(22,25,30))
heroes=[('haunted_cabinet',0,'Шкаф 2×3'),('haunted_cabinet',4,'Открытый шкаф'),('coffin_cabinet',4,'Открытый шкаф-гроб'),('pumpkin_scarecrow',None,'Пугало: 3 блока'),('reaper_statue',None,'Жнец с косой')]
for i,(name,frame,label) in enumerate(heroes):
    output=name+('-open' if frame==4 else '-new')+'-preview.png'
    render(22,14,output,model_name=name,scale=17 if name.endswith('cabinet') else 18 if name=='pumpkin_scarecrow' else 26,center_y=8 if name!='reaper_statue' else 15,model_data=cabinet(name,frame) if frame is not None else None)
    im=Image.open(ROOT/'art'/output).crop((147,0,853,940)).resize((300,400),Image.Resampling.LANCZOS);sheet.paste(im,(i*300,0))
    ImageDraw.Draw(sheet).text((i*300+150,410),label,font=font,fill=(235,223,201),anchor='mt')
sheet.save(ROOT/'art/updated-characters-preview.png')
print('Rendered ten new decorations and five cabinet/character views.')

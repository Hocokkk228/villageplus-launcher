from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from render_pumpkin import render
ROOT=Path(__file__).resolve().parents[1]
names=[('pumpkin_chair','Тыквенное кресло'),('gothic_chair','Готическое кресло'),('coffin_chair','Кресло-гроб'),('witch_stool','Ведьмин табурет'),('witch_table','Ведьмин столик'),
 ('pumpkin_cushion','Тыквенная подушка'),('bat_cushion','Подушка с мышью'),('ghost_cushion','Подушка с призраком'),('spider_cushion','Подушка с паутиной'),('candle_candelabra','Канделябр'),
 ('haunted_cabinet','Шкаф с привидениями'),('coffin_cabinet','Шкаф-гроб'),('pumpkin_scarecrow','Тыквенное пугало'),('gargoyle_statue','Горгулья'),('reaper_statue','Жнец')]
sheet=Image.new('RGB',(1500,960),(22,25,30));font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',18)
for i,(name,label) in enumerate(names):
    tall=name.endswith('cabinet') or name in ['pumpkin_scarecrow','reaper_statue']
    render(24,22,name+'-preview.png',model_name=name,scale=24 if tall else 43,center_y=15 if tall else 7.5)
    im=Image.open(ROOT/'art'/f'{name}-preview.png').resize((300,282),Image.Resampling.LANCZOS)
    x,y=i%5*300,i//5*320;sheet.paste(im,(x,y))
    ImageDraw.Draw(sheet).text((x+150,y+288),label,font=font,fill=(235,223,201),anchor='mt')
sheet.save(ROOT/'art/furniture-preview.png')
print('Rendered 15 game models.')

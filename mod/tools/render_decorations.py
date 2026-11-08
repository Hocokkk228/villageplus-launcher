from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from render_pumpkin import render

ROOT = Path(__file__).resolve().parents[1]
names = [('sheet_ghost','Призрак в простыне'), ('little_ghost','Маленький призрак'),
         ('weathered_skull','Старый череп'), ('scattered_bones','Разбросанные кости'),
         ('crossed_bones','Скрещённые кости'), ('skull_pile','Груда черепов')]
sheet = Image.new('RGB',(1500,1040),(22,25,30))
font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf',24)
for i,(name,label) in enumerate(names):
    render(24,25 if 'ghost' in name else 35,name+'-preview.png',model_name=name)
    im = Image.open(ROOT/'art'/f'{name}-preview.png').resize((500,470),Image.Resampling.LANCZOS)
    x,y = (i%3)*500,(i//3)*520
    sheet.paste(im,(x,y))
    d = ImageDraw.Draw(sheet)
    d.text((x+250,y+475),label,font=font,fill=(232,225,206),anchor='mt')
sheet.save(ROOT/'art/decorations-preview.png')
print('Rendered six decorations from their actual game models.')

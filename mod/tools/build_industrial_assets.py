"""Original appliance models, animated pixel broadcasts, component models and synthesized radio audio."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,math,wave,struct,random,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/halloween'
def write(path,value):
 p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def cube(a,b,tex,uv=None):return {'from':a,'to':b,'faces':{face:{'texture':'#'+tex,**({'uv':uv} if uv else {})} for face in ['north','south','east','west','up','down']}}
def model(name,textures,els,block=True):
 textures={'particle':next(iter(textures.values())),**textures}
 data={'textures':textures,'elements':els}
 if not block:data['display']={'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.75,.75,.75]},'firstperson_righthand':{'rotation':[0,-45,0],'translation':[0,1,0],'scale':[.6,.6,.6]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2,0],'scale':[.5,.5,.5]},'ground':{'scale':[.4,.4,.4]}}
 write(Path('models')/('block' if block else 'item')/(name+'.json'),data)

def tv(screen):
 tex={'wood':'minecraft:block/dark_oak_planks','trim':'minecraft:block/stripped_oak_log','metal':'minecraft:block/iron_block','black':'minecraft:block/black_concrete','screen':'halloween:block/'+screen}
 els=[cube([0,2,4],[16,14,14],'wood'),cube([.5,1,5],[3,2,12],'black'),cube([13,1,5],[15.5,2,12],'black')]
 for a,b in [([.5,2.5,2.5],[2,13.5,4]),([12,2.5,2.5],[15.5,13.5,4]),([2,12,2.5],[12,13.5,4]),([2,2.5,2.5],[12,4,4])]:els.append(cube(a,b,'trim'))
 els.append(cube([2,4,2.95],[12,12,3.15],'screen',[0,0,16,16]))
 for y in [9,11]:els.append(cube([13, y,2.1],[14.5,y+1.1,2.5],'metal'))
 for y in [4,5,6,7]:els.append(cube([12.8,y,2.35],[15,y+.22,2.5],'black'))
 return tex,els
for channel in ['off','news','test','nature','weather']:
 t,e=tv('crt_'+channel);model('television_'+channel,t,e)
variants={}
for f,r in [('north',0),('east',90),('south',180),('west',270)]:
 for powered in [False,True]:
  for c in range(4):variants[f'channel={c},facing={f},powered={str(powered).lower()}']={'model':'halloween:block/television_'+(['news','test','nature','weather'][c] if powered else 'off'),'y':r}
write(Path('blockstates/television.json'),{'variants':variants})
write(Path('items/television.json'),{'model':{'type':'minecraft:model','model':'halloween:block/television_off'}})
tex={'wood':'minecraft:block/spruce_planks','metal':'minecraft:block/iron_block','black':'minecraft:block/black_concrete','grille':'halloween:block/radio_grille','dial':'halloween:block/radio_dial'}
els=[cube([2,1,4],[14,9,13],'wood'),cube([3,0,5],[5,1,12],'black'),cube([11,0,5],[13,1,12],'black'),cube([3,2,3.85],[8.5,8,4.05],'grille',[0,0,16,16]),cube([9,5,3.85],[13,7,4.05],'dial',[0,0,16,16]),cube([9,2,3.2],[10.2,3.2,4],'metal'),cube([11.5,2,3.2],[12.7,3.2,4],'metal'),cube([3.8,9,7],[4.3,11,8],'black'),cube([11.7,9,7],[12.2,11,8],'black'),cube([4,10.5,7],[12,11.1,8],'black')]
model('table_radio_off',tex,els);model('table_radio_on',tex,els)
variants={}
for f,r in [('north',0),('east',90),('south',180),('west',270)]:
 for power in [False,True]:
  for c in range(4):variants[f'channel={c},facing={f},powered={str(power).lower()}']={'model':'halloween:block/table_radio_'+('on' if power else 'off'),'y':r}
write(Path('blockstates/table_radio.json'),{'variants':variants});write(Path('items/table_radio.json'),{'model':{'type':'minecraft:model','model':'halloween:block/table_radio_off'}})
# The workshop control pedestal has separate feet, steel rails and a recessed instrument panel.
model('machine_assembly',{'metal':'minecraft:block/iron_block','dark':'minecraft:block/polished_deepslate','top':'minecraft:block/smithing_table_top'},[cube([1,0,2],[4,12,14],'dark'),cube([12,0,2],[15,12,14],'dark'),cube([0,12,1],[16,15,15],'metal'),cube([1,15,2],[15,16,14],'top')])
write(Path('blockstates/machine_assembly.json'),{'variants':{'facing='+f:{'model':'halloween:block/machine_assembly','y':r} for f,r in [('north',0),('east',90),('south',180),('west',270)]}});write(Path('items/machine_assembly.json'),{'model':{'type':'minecraft:model','model':'halloween:block/machine_assembly'}})
texture_dir=ROOT/'textures/block';texture_dir.mkdir(parents=True,exist_ok=True)
for name in ['off','news','test','nature','weather']:
 frames=[]
 for frame in range(8 if name!='off' else 1):
  im=Image.new('RGB',(32,24),(13,19,16));d=ImageDraw.Draw(im)
  if name=='off':d.rounded_rectangle((1,1,30,22),radius=4,fill=(19,27,24));d.line((4,3,11,3),fill=(49,55,49))
  elif name=='news':
   d.rectangle((1,1,30,21),fill=(40,77,84));d.rectangle((2,3,13,12),fill=(65,118,111));d.line((3,9,6,6,9,8,12,4),fill=(200,225,171),width=1)
   d.rectangle((19,5,25,12),fill=(184,139,99));d.rectangle((20,8,24,8),fill=(58,79,41));d.rectangle((22,9,23,12),fill=(150,98,73));d.rectangle((18,12,27,18),fill=(57,54,67));d.rectangle((2,17,30,20),fill=(95,59,42));d.rectangle((2,21,30,22),fill=(209,194,144));d.rectangle((2+(frame*3)%22,21,7+(frame*3)%22,21),fill=(38,64,59))
  elif name=='test':
   colors=[(218,215,181),(193,185,71),(53,173,165),(59,147,66),(179,73,151),(172,58,44),(56,75,149)]
   for i,c in enumerate(colors):d.rectangle((2+i*4,2,5+i*4,17),fill=c)
   d.rectangle((2,18,29,21),fill=(23,24,23));d.rectangle((3,19,3+frame*3,20),fill=(205,205,195))
  elif name=='nature':
   d.rectangle((1,1,30,22),fill=(113,168,176));d.ellipse((24,3,28,7),fill=(237,222,153));d.polygon([(1,14),(9,7),(19,16),(30,8),(30,22),(1,22)],fill=(45,86,68));d.polygon([(13,14),(18,14),(24,22),(9,22)],fill=(63,132+frame,159));
   for x,y in [(3,17),(7,19),(25,16),(28,20)]:d.rectangle((x,y-4,x,y),fill=(67,53,35));d.polygon([(x-2,y-1),(x,y-7),(x+2,y-1)],fill=(27,64,43))
  else:
   d.rectangle((1,1,30,22),fill=(42,72,100));d.ellipse((4,4,12,12),fill=(229,198,113));d.ellipse((12,5,22,12),fill=(164,187,185));d.ellipse((18,7,28,13),fill=(187,203,192));
   for x in range(15,27,4):d.line((x,15+(frame%3),x-1,18+(frame%3)),fill=(90,163,185))
   d.text((3,14),'18',fill=(227,226,192));d.rectangle((3,22,28,22),fill=(187,197,181))
  if name!='off':
   for y in range(frame%3,24,3):d.line((1,y,30,y),fill=(0,0,0),width=1)
  frames.append(im)
 out=Image.new('RGB',(32,32*len(frames)))
 for i,im in enumerate(frames):out.paste(im.resize((32,32),Image.Resampling.NEAREST),(0,32*i))
 out.save(texture_dir/('crt_'+name+'.png'))
 if len(frames)>1:(texture_dir/('crt_'+name+'.png.mcmeta')).write_text(json.dumps({'animation':{'frametime':4,'width':32,'height':32,'interpolate':False}}))
for name in ['radio_grille','radio_dial']:
 im=Image.new('RGB',(16,16),(60,46,32));d=ImageDraw.Draw(im)
 if name=='radio_grille':
  for x in range(1,16,2):
   for y in range(x%3,16,3):d.point((x,y),fill=(170,151,113))
 else:
  d.rectangle((0,3,15,12),fill=(203,187,135));d.line((2,8,13,8),fill=(61,57,42));
  for x in range(2,15,2):d.line((x,5,x,7),fill=(61,57,42))
  d.line((9,4,9,11),fill=(135,39,31))
 im.save(texture_dir/(name+'.png'))
parts=['machine_frame','combustion_engine','gearbox','road_wheel','vehicle_axle','vehicle_door','armor_plate','turret_assembly','track_link','wing_panel','jet_engine','cockpit_glass','circuit_board','picture_tube','loudspeaker','appliance_cabinet']
for part in parts:
 metal='minecraft:block/iron_block';dark='minecraft:block/polished_deepslate';extra='minecraft:block/copper_block'
 els=[cube([3,3,3],[13,11,13],'metal'),cube([2,1,2],[14,3,14],'dark')]
 if part in ['combustion_engine','jet_engine','gearbox']:els += [cube([4,11,4],[7,14,12],'dark'),cube([9,11,4],[12,14,12],'extra'),cube([0,5,7],[3,8,9],'dark')]
 elif part=='road_wheel':els=[cube([3,2,2],[12,13,13],'dark'),cube([2,5,5],[13,10,10],'metal')]
 elif part in ['wing_panel','armor_plate','vehicle_door']:els=[cube([2,2,6],[14,14,9],'metal'),cube([3,3,5],[13,13,6],'dark')]
 elif part=='vehicle_axle':els=[cube([1,6,6],[15,9,9],'metal'),cube([0,4,4],[3,11,11],'dark'),cube([13,4,4],[16,11,11],'dark')]
 elif part=='circuit_board':metal='minecraft:block/green_terracotta';els=[cube([2,2,7],[14,14,8],'metal'),cube([5,5,6],[10,10,7],'dark'),cube([3,11,6],[13,12,7],'extra')]
 elif part in ['picture_tube','cockpit_glass']:metal='minecraft:block/light_blue_stained_glass'
 elif part=='appliance_cabinet':metal='minecraft:block/dark_oak_planks'
 model(part,{'metal':metal,'dark':dark,'extra':extra},els,False);write(Path('items')/(part+'.json'),{'model':{'type':'minecraft:model','model':'halloween:item/'+part}})
# Each station is a newly synthesized eight-second loop; no recordings or third-party music.
sounds=ROOT/'sounds.json';sound_data=json.loads(sounds.read_text()) if sounds.exists() else {}
rng=random.Random(520);rate=22050
for station,notes in enumerate([[196,246.94,293.66,246.94,174.61,220,261.63,220],[164.81,196,246.94,220,146.83,174.61,220,196],[220,293.66,329.63,293.66,196,261.63,293.66,261.63]]):
 out=ROOT/'sounds/radio'/('station_'+str(station)+'.ogg');out.parent.mkdir(parents=True,exist_ok=True)
 with tempfile.TemporaryDirectory() as temp:
  wav=Path(temp)/'radio.wav'
  with wave.open(str(wav),'wb') as w:
   w.setparams((1,2,rate,0,'NONE','not compressed'));buffer=bytearray()
   for i in range(rate*8):
    t=i/rate;n=int(t*2)%len(notes);phase=t%.5;env=min(1,phase/.025)*max(0,1-phase/.5)**1.5;f=notes[n];tone=(math.sin(2*math.pi*f*t)+.22*math.sin(2*math.pi*f*2*t))*.16*env;bass=math.sin(2*math.pi*notes[(int(t)//2*4)%8]/2*t)*.09;beat=(math.sin(2*math.pi*55*t)*math.exp(-(t%.5)*35))*.11;noise=(rng.random()-.5)*.008;sample=math.tanh(tone+bass+beat+noise);buffer.extend(struct.pack('<h',int(sample*16000)))
   w.writeframes(buffer)
  subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','3',str(out)],check=True)
 sound_data['radio.station_'+str(station)]={'sounds':[{'name':'halloween:radio/station_'+str(station),'stream':False}]}
sounds.write_text(json.dumps(sound_data,ensure_ascii=False,indent=2)+'\n')
lang=ROOT/'lang/ru_ru.json';d=json.loads(lang.read_text());labels=['Рама машины','Двигатель внутреннего сгорания','Коробка передач','Колесо','Мост автомобиля','Автомобильная дверь','Бронеплита','Башенный узел','Звено гусеницы','Панель крыла','Реактивный двигатель','Остекление кабины','Печатная плата','Кинескоп','Динамик','Деревянный корпус прибора']
for p,label in zip(parts,labels):d['item.halloween.'+p]=label
d.update({'block.halloween.television':'Ламповый телевизор','block.halloween.table_radio':'Радиоприёмник','block.halloween.machine_assembly':'Сборочный стапель','entity.halloween.industrial_vehicle':'Техника','entity.halloween.station_resident':'Бродяга'})
for p,label in [('sedan','Легковой автомобиль'),('city_bus','Городской автобус'),('battle_tank','Средний танк'),('armored_carrier','Бронетранспортёр'),('jet_fighter','Реактивный истребитель')]:d['item.halloween.'+p]=label
lang.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
print('Industrial components, CRT channels, radio models and original sound loops generated.')
# Miniature item models are exported from the exact runtime vehicle geometry, not independently redrawn.
import sys
source=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT.parents[3]/'build/industrial-machines-0520/industrial-vehicles.json'
if source.exists():
 data=json.loads(source.read_text())
 for name,parts in data.items():
  lo=[min(p['box'][i] for p in parts) for i in range(3)];hi=[max(p['box'][i+3] for p in parts) for i in range(3)];scale=14/max(hi[i]-lo[i] for i in range(3));center=[(lo[i]+hi[i])/2 for i in range(3)];textures={};els=[]
  for p in parts:
   block=p['block'].split(':')[-1];tex='m'+str(len(textures));textures[tex]='minecraft:block/'+({'smooth_sandstone':'sandstone_top'}.get(block,block))
   a=[8+(p['box'][i]-center[i])*scale for i in range(3)];b=[8+(p['box'][i+3]-center[i])*scale for i in range(3)];els.append(cube(a,b,tex))
  model(name,textures,els,False);write(Path('items')/(name+'.json'),{'model':{'type':'minecraft:model','model':'halloween:item/'+name}})
 print('Exact runtime miniatures generated for',len(data),'vehicles.')

for kind,name in enumerate(['sedan','city_bus','battle_tank','armored_carrier','jet_fighter']):
 write(Path('items')/(name+'.json'),{'model':{'type':'minecraft:special','base':'halloween:item/'+name,'model':{'type':'halloween:industrial_vehicle_item','kind':kind}}})

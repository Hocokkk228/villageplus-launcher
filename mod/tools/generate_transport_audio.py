"""Original procedural transport Foley; no third-party samples."""
import math,random,wave,struct,subprocess,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/halloween';out=root/'sounds/transport';out.mkdir(parents=True,exist_ok=True)
rate=22050
for name,seconds in [('steam',3),('rolling',4),('whistle',2.8),('brake',2.8),('sea',8),('creak',2.3)]:
 rng=random.Random(1701);buf=bytearray();low=slow=0
 for i in range(int(rate*seconds)):
  t=i/rate;n=rng.uniform(-1,1);low=.90*low+.10*n;slow=.996*slow+.004*n
  if name=='steam':
   phase=(t%0.375)/.375;env=(math.sin(math.pi*min(1,phase/.65))**1.4 if phase<.65 else 0);value=(low*1.7+n*.12)*env*.65
  elif name=='rolling':
   value=slow*2+low*.20
   for start in [0.35,0.52,1.35,1.52,2.35,2.52,3.35,3.52]:
    d=t-start
    if 0<d<.15:value+=math.exp(-d*35)*(math.sin(2*math.pi*95*d)*.2+low*.7)
  elif name=='whistle':
   env=min(1,t/.18)*min(1,(seconds-t)/.55);breath=.9+.1*math.sin(t*7)
   value=env*breath*(sum(math.sin(2*math.pi*f*t+.025*math.sin(t*20)) for f in [220,277.18,329.63])*.15+low*.23)
  elif name=='brake':
   env=math.sin(math.pi*t/seconds)**.7;value=env*(low*.6+n*.09+math.sin(2*math.pi*(790*t-80*t*t))*.055)
  elif name=='sea':
   swell=.45+.25*math.sin(2*math.pi*t/8)+.15*math.sin(2*math.pi*t/4+.3);value=(low*1.8+slow*4)*swell*.6
  else:
   env=math.sin(math.pi*t/seconds)**1.3;freq=125+24*math.sin(t*3);value=env*(math.sin(2*math.pi*freq*t)*.16+math.sin(2*math.pi*freq*2.01*t)*.055+low*.13)
  if name in ('steam','rolling','sea'):value*=min(1,t/.035,(seconds-t)/.035)
  buf+=struct.pack('<h',int(max(-.98,min(.98,value))*32767))
 wav=out/(name+'.wav')
 with wave.open(str(wav),'wb') as f:f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate);f.writeframes(buf)
 subprocess.run(['ffmpeg','-loglevel','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','5',str(out/(name+'.ogg'))],check=True);wav.unlink()
p=root/'sounds.json';sounds=json.loads(p.read_text()) if p.exists() else {}
for name in ('steam','rolling','whistle','brake','sea','creak'):
 sounds['transport.'+name]={'subtitle':'subtitles.halloween.transport.'+name,'sounds':[{'name':'halloween:transport/'+name,'attenuation_distance':96 if name=='whistle' else 32}]}
p.write_text(json.dumps(sounds,indent=2))
for lang,labels in [('ru_ru',['Работает паровоз','Стучат колёса','Гудит паровоз','Шипят тормоза','Плещется море','Скрипит корпус']),('en_us',['Steam engine chugs','Wheels clatter','Train whistles','Brakes hiss','Sea washes against hull','Timbers creak'])]:
 p=root/'lang'/f'{lang}.json';data=json.loads(p.read_text());data.update({'subtitles.halloween.transport.'+k:v for k,v in zip(('steam','rolling','whistle','brake','sea','creak'),labels)});p.write_text(json.dumps(data,ensure_ascii=False,indent=2))
print('Created six original mono Ogg transport sounds')

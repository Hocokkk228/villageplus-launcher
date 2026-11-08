"""Export the prepared holiday map without development debris, into launcher saves."""
from pathlib import Path
import shutil,struct,zlib,io,json,zipfile,uuid,copy,math
import nbtlib as n
ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'run-taiga-ready/saves/Northern Evening 050'
world=Path.home()/'.config/villageplus-launcher/worlds/Northern Express 050'
if world.exists():raise SystemExit('Destination already exists; refusing to overwrite a player world')
shutil.copytree(source,world,ignore=shutil.ignore_patterns('session.lock','*.dat_old','playerdata','advancements','stats','icon.png'))
def read_region(p):
 raw=p.read_bytes();chunks={}
 for i in range(1024):
  sec=int.from_bytes(raw[i*4:i*4+3],'big')
  if not sec:continue
  pos=sec*4096;size=int.from_bytes(raw[pos:pos+4],'big');blob=raw[pos+5:pos+4+size];kind=raw[pos+4]
  if kind==2:blob=zlib.decompress(blob)
  elif kind==1:
   import gzip
   blob=gzip.decompress(blob)
  elif kind!=3:raise ValueError(kind)
  chunks[i]=n.File.parse(io.BytesIO(blob))
 return chunks
def write_region(p,chunks):
 header=bytearray(8192);body=bytearray();offset=2
 for i,data in chunks.items():
  tmp=io.BytesIO();data.write(tmp);compressed=zlib.compress(tmp.getvalue(),3);record=struct.pack('>I',len(compressed)+1)+b'\x02'+compressed;sectors=(len(record)+4095)//4096
  header[i*4:i*4+4]=offset.to_bytes(3,'big')+bytes([sectors]);body.extend(record);body.extend(b'\0'*(sectors*4096-len(record)));offset+=sectors
 p.write_bytes(header+body)
def uid(e):return str(uuid.UUID(bytes=struct.pack('>IIII',*(int(v)&0xffffffff for v in e['UUID']))))
regions={p:read_region(p) for p in (world/'entities').glob('*.mca')}
all_entities=[e for chunks in regions.values() for c in chunks.values() for e in c.get('Entities',[])]
engines=sorted([e for e in all_entities if str(e.get('id'))=='halloween:rail_vehicle' and int(e.get('kind',-1))==0],key=lambda e:int(e.get('dwell',0)),reverse=True)[:2]
roots={uid(e) for e in engines};kept={uid(e) for e in all_entities if str(e.get('id'))=='halloween:rail_vehicle' and (uid(e) in roots or str(e.get('leader','')) in roots)}
driver_seats=[e for e in all_entities if str(e.get('id'))=='halloween:rail_seat' and any('halloween_train_driver' in [str(t) for t in p.get('Tags',[])] for p in e.get('Passengers',[]))]
for engine in engines:
 if any(str(e.get('owner',''))==uid(engine) for e in driver_seats):continue
 seat=copy.deepcopy(driver_seats[0]);rider=seat['Passengers'][0]
 def fresh(e):e['UUID']=n.IntArray([v if v<2**31 else v-2**32 for v in struct.unpack('>IIII',uuid.uuid4().bytes)])
 fresh(seat);fresh(rider);seat['owner']=n.String(uid(engine));seat['local']=n.List[n.Double]([0,2.05,-4.4]);seat['control']=n.Byte(1)
 yaw=math.radians(float(engine['Rotation'][0]));x,y,z=map(float,engine['Pos']);point=[x+4.4*math.sin(yaw),y+2.05,z-4.4*math.cos(yaw)];seat['Pos']=n.List[n.Double](point);rider['Pos']=n.List[n.Double]([point[0],point[1]-.8,point[2]]);rider['Tags']=n.List[n.String]([n.String('halloween_train_driver'),n.String('halloween_train_driver_'+uid(engine))]);rider['NoAI']=n.Byte(1);rider['Health']=n.Float(20)
 cx,cz=math.floor(point[0])>>4,math.floor(point[2])>>4;file=world/'entities'/f'r.{cx>>5}.{cz>>5}.mca';chunks=regions.setdefault(file,{});index=(cx&31)+(cz&31)*32
 if index not in chunks:chunks[index]=n.File({'DataVersion':n.Int(4671),'Position':n.IntArray([cx,cz]),'Entities':n.List[n.Compound]()})
 chunks[index]['Entities'].append(seat)
 engine['driver_created']=n.Byte(1)
removed=0;vehicles=[];drivers=0
for p,chunks in regions.items():
 for data in chunks.values():
  keep=[]
  for e in data.get('Entities',[]):
   kind=str(e.get('id',''))
   if kind in ('minecraft:item','minecraft:falling_block','minecraft:marker') or kind=='halloween:rail_vehicle' and uid(e) not in kept or kind=='halloween:rail_seat' and str(e.get('owner','')) not in kept or kind=='minecraft:villager' and 'halloween_train_driver' in [str(t) for t in e.get('Tags',[])]:
    removed+=1;continue
   if kind=='halloween:rail_vehicle':
    vehicles.append(e)
    if int(e.get('kind',-1))==0:e['dwell']=n.Int(1200);e['speed']=n.Double(0)
   if kind=='halloween:rail_seat':
    for rider in e.get('Passengers',[]):
     if 'halloween_train_driver' in [str(t) for t in rider.get('Tags',[])]:drivers+=1
   keep.append(e)
  data['Entities']=n.List[n.Compound](keep)
 write_region(p,chunks)
assert len(vehicles)==6 and sum(int(e.get('sleeper',0)) for e in vehicles)==2
assert drivers==2,drivers
m=n.load(world/'data/halloween_village_builders.dat');m['data']['houses']=n.List[n.Compound]([h for h in m['data']['houses'] if str(h.get('kind',''))!='structure']);m.save()
for key in ['halloween_projects','halloween_village_projects','halloween_era_projects','halloween_public_works','halloween_rail_deferred','chunks']:
 p=world/'data'/f'{key}.dat'
 if p.exists():p.unlink()
rail=n.load(world/'data/halloween_railway.dat')['data'];assert len(rail['stations'])==6
st=rail['stations'][0];x,y,z=map(int,st['origin']);f=[(0,1),(-1,0),(0,-1),(1,0)][int(st['facing'])];side=(f[1],-f[0]);spawn=[x+side[0]*12+f[0]*30+.5,y+3,z+side[1]*12+f[1]*30+.5]
level=n.load(world/'level.dat');d=level['Data'];d['LevelName']=n.String('Северный экспресс · Вечерняя тайга');d['DayTime']=n.Long(11500);d['GameType']=n.Int(1);d['allowCommands']=n.Byte(1);d['raining']=n.Byte(0);d['thundering']=n.Byte(0)
p=d['Player'];p['Pos']=n.List[n.Double](spawn);p['Rotation']=n.List[n.Float]([int(st['facing'])*90,0]);p['Motion']=n.List[n.Double]([0,0,0]);p.pop('RootVehicle',None);p['Health']=n.Float(20);p['playerGameType']=n.Int(1);p['abilities']['flying']=n.Byte(0);d['SpawnX']=n.Int(int(spawn[0]));d['SpawnY']=n.Int(int(spawn[1]));d['SpawnZ']=n.Int(int(spawn[2]));level.save()
manifest={'world':str(world),'spawn':spawn,'stations':6,'trains':2,'sleeperCoaches':2,'drivers':drivers,'removedDebrisEntities':removed,'requires':'VillagePlus 0.50.0','time':'evening','routeMetres':9629}
out=ROOT/'dist/0.50.0';out.mkdir(exist_ok=True,parents=True);(out/'taiga-world-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
archive=out/'Northern-Express-Taiga-050.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,3) as z:
 for p in world.rglob('*'):
  if p.is_file():z.write(p,str(Path(world.name)/p.relative_to(world)))
print(json.dumps(manifest,ensure_ascii=False));print('ZIP',str(archive),archive.stat().st_size)

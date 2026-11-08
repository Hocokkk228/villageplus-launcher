"""Export the reviewed ring world, removing abandoned test rolling stock and developer-only work queues."""
from pathlib import Path
import shutil,io,zlib,struct,json,uuid,zipfile,hashlib
import nbtlib as n
ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'build/rail-ring-demo/Six Villages Ring v3'
out=ROOT/'dist/0.47.0';out.mkdir(exist_ok=True)
world=out/'NorthernTaigaRing'
if world.exists():shutil.rmtree(world)
shutil.copytree(source,world,ignore=shutil.ignore_patterns('session.lock','*.dat_old','*.log','playerdata','stats','advancements'))
def read_region(p):
 chunks={}
 with p.open('rb') as f:
  header=f.read(4096)
  for i in range(1024):
   offset=int.from_bytes(header[i*4:i*4+3],'big')
   if not offset:continue
   f.seek(offset*4096);length=int.from_bytes(f.read(4),'big');kind=f.read(1)[0];raw=f.read(length-1)
   if kind==2:raw=zlib.decompress(raw)
   elif kind==1:import gzip;raw=gzip.decompress(raw)
   elif kind!=3:raise ValueError((p,kind))
   chunks[i]=n.File.parse(io.BytesIO(raw))
 return chunks

def write_region(p,chunks):
 header=bytearray(8192);body=bytearray();offset=2
 for i,data in chunks.items():
  raw=io.BytesIO();data.write(raw);compressed=zlib.compress(raw.getvalue(),3);record=struct.pack('>I',len(compressed)+1)+b'\x02'+compressed;sectors=(len(record)+4095)//4096
  header[i*4:i*4+4]=offset.to_bytes(3,'big')+bytes([sectors]);body.extend(record);body.extend(b'\x00'*(sectors*4096-len(record)));offset+=sectors
 p.write_bytes(header+body)

def uid(e):return str(uuid.UUID(bytes=struct.pack('>IIII',*(int(v)&0xffffffff for v in e['UUID']))))
regions={p:read_region(p) for p in (world/'entities').glob('*.mca')}
entities=[e for chunks in regions.values() for data in chunks.values() for e in data.get('Entities',[])]
engines=[e for e in entities if str(e.get('id'))=='halloween:rail_vehicle' and int(e.get('kind',-1))==0]
engines=sorted(engines,key=lambda e:int(e.get('visits',0)),reverse=True)[:2]
assert len(engines)==2 and all(int(e.get('visits',0))>=6 for e in engines),'full lap not saved'
roots={uid(e) for e in engines};vehicles=[e for e in entities if str(e.get('id'))=='halloween:rail_vehicle' and (uid(e) in roots or str(e.get('leader','')) in roots)];keep={uid(e) for e in vehicles};assert len(keep)==6
seats=0;passengers=0;drivers=0
for p,chunks in regions.items():
 for data in chunks.values():
  filtered=[]
  for e in data.get('Entities',[]):
   kind=str(e.get('id',''))
   if kind=='halloween:rail_vehicle' and uid(e) not in keep:continue
   if kind=='halloween:rail_seat':
    if str(e.get('owner','')) not in keep:continue
    seats+=1
    for person in e.get('Passengers',[]):
     assert float(person.get('Health',20))>0
     if 'halloween_train_driver' in [str(t) for t in person.get('Tags',[])]:drivers+=1
     else:passengers+=1
   if kind=='minecraft:villager' and 'halloween_train_driver' in [str(t) for t in e.get('Tags',[])]:continue
   filtered.append(e)
  data['Entities']=n.List[n.Compound](filtered)
 write_region(p,chunks)
assert drivers==2,drivers
# Plans accumulated by invisible development observers are not part of the demonstration.
for name in ['halloween_projects','halloween_village_projects','halloween_village_eras','halloween_era_projects','halloween_public_works']:
 p=world/'data'/f'{name}.dat'
 if p.exists():p.unlink()
rail=n.load(world/'data/halloween_railway.dat')['data'];stations=rail['stations'];first=stations[0];origin=[int(v) for v in first['origin']];f=[(0,1),(-1,0),(0,-1),(1,0)][int(first['facing'])];side=(f[1],-f[0]);spawn=[origin[0]+side[0]*12+f[0]*30+.5,origin[1]+3,origin[2]+side[1]*12+f[1]*30+.5]
level=n.load(world/'level.dat');d=level['Data'];d['LevelName']=n.String('Северная тайга · Кольцо шести деревень');d['GameType']=n.Int(1);d['allowCommands']=n.Byte(1);d['Time']=n.Long(max(24000,int(d.get('Time',24000))));d['DayTime']=n.Long(6000);d['raining']=n.Byte(0);d['thundering']=n.Byte(0);d['rainTime']=n.Int(120000);d['thunderTime']=n.Int(120000)
p=d.get('Player',n.Compound());p['Pos']=n.List[n.Double](spawn);p['Motion']=n.List[n.Double]([0,0,0]);p['Rotation']=n.List[n.Float]([float(int(first['facing'])*90),0]);p['Health']=n.Float(20);p['DeathTime']=n.Short(0);p['HurtTime']=n.Short(0);p['playerGameType']=n.Int(1);p['Dimension']=n.String('minecraft:overworld');p['abilities']=n.Compound(flying=n.Byte(0),instabuild=n.Byte(1),invulnerable=n.Byte(1),mayBuild=n.Byte(1),mayfly=n.Byte(1),flySpeed=n.Float(.05),walkSpeed=n.Float(.1));p['Inventory']=n.List[n.Compound]([n.Compound(Slot=n.Byte(0),id=n.String('halloween:world_statistics'),count=n.Int(1))]);p.pop('RootVehicle',None);d['Player']=p;d['SpawnX']=n.Int(int(spawn[0]));d['SpawnY']=n.Int(int(spawn[1]));d['SpawnZ']=n.Int(int(spawn[2]));level.save()
manifest={'world':world.name,'spawn':spawn,'stations':[{'origin':[int(v) for v in s['origin']],'facing':int(s['facing']),'village':int(s['village'])} for s in stations],'vehicles':len(vehicles),'drivers':drivers,'passengers':passengers,'engineVisits':[int(e['visits']) for e in engines]}
(out/'world-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
archive=out/'Northern-Taiga-Ring.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,3) as z:
 for p in world.rglob('*'):
  if p.is_file():z.write(p,str(p.relative_to(out)))
print(json.dumps(manifest,ensure_ascii=False));print('ZIP',archive.stat().st_size)

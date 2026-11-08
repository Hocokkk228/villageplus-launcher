from pathlib import Path
import json,zipfile,base64
root=Path(__file__).resolve().parents[1];data={'models':{}};models={k:json.loads((root/f'src/main/resources/data/halloween/rail_models/ship_{k}.json').read_text()) for k in ['brig','cutter']};names=sorted({p['block'].split(':')[1] for ps in models.values() for p in ps});data['names']=names;data['textures']=[]
with zipfile.ZipFile(Path.home()/'.gradle/caches/fabric-loom/1.21.11/minecraft-client.jar') as z:
 for name in names:
  texture=name.replace('_wood','_log') if name.endswith('_wood') else 'barrel_side' if name=='barrel' else name
  data['textures'].append('data:image/png;base64,'+base64.b64encode(z.read(f'assets/minecraft/textures/block/{texture}.png')).decode())
for name,parts in models.items():
 out=[]
 for p in parts:
  row={'b':names.index(p['block'].split(':')[1]),'a':p['box'],'g':p['group']}
  if 'rotation' in p:row.update(r=p['rotation'],p=p['pivot'])
  out.append(row)
 data['models'][name]=out
p=root/'preview/fleet/fleet-models.html';s=p.read_text();assert '__FLEET_DATA__' in s;s=s.replace('__FLEET_DATA__',json.dumps(data,separators=(',',':')));p.write_text(s)
q=Path('/home/ottobyyy/.codex/visualizations/2026/10/29/01a188f4-46e6-7353-8694-e1976dd1b9be/fleet-models.html');q.write_text(s);assert q.stat().st_size<1000000
print(q, q.stat().st_size)

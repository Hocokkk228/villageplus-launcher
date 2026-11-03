"""Isolated renderer regression: running game, hide/show, navigate to room."""
import asyncio, threading, http.server, functools, json, os
from pathlib import Path
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[2]
class Quiet(http.server.SimpleHTTPRequestHandler):
 def log_message(self,*args):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),functools.partial(Quiet,directory=str(ROOT/'app')))
threading.Thread(target=server.serve_forever,daemon=True).start()
async def main():
 async with async_playwright() as p:
  browser=await p.chromium.launch(executable_path='/home/ottobyyy/.cache/ms-playwright/chromium_headless_shell-1248/chrome-headless-shell-linux64/chrome-headless-shell',headless=True,args=['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
  page=await browser.new_page(viewport={'width':1420,'height':920})
  await page.goto(f'http://127.0.0.1:{server.server_port}')
  private_ice=os.environ.get('VP_TEST_ICE_FILE')
  if private_ice:
   await page.evaluate("ice=>{window.testIce=ice;const RTC=window.RTCPeerConnection;window.RTCPeerConnection=class extends RTC{constructor(config){super({...config,iceTransportPolicy:'relay'})}}}",json.loads(Path(private_ice).read_text()))
  result=await page.evaluate("""async()=>{
    const {Rooms}=await import('./rooms.js'); const notices=[], peers={}, counts={finish:0,listen:0,servers:0,bytes:0,joined:0};
    const bytes=new Uint8Array(24013).fill(91), digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))).map(x=>x.toString(16).padStart(2,'0')).join('');
    const pack={minecraft:'1.21.11',aio:true,files:[{name:'test.jar',size:bytes.length,sha256:digest}]};
    const api=name=>({on:()=>()=>{},ice:async()=>window.testIce||[],sendSlon:async(to,msg)=>peers[to].receive({...msg,from:name}),packManifest:async()=>pack,
      packBegin:async()=>({id:'transfer',missing:['test.jar']}),packRead:async(file,offset)=>{const b=bytes.slice(offset,offset+9000);return{data:btoa(String.fromCharCode(...b)),size:b.length,end:offset+b.length===bytes.length}},
      packWrite:async(id,file,offset,data,end)=>{if(offset!==counts.bytes)throw Error('out of order');const b=atob(data);if([...b].some(x=>x.charCodeAt(0)!==91))throw Error('corrupt');counts.bytes+=b.length},
      packFinish:async()=>{counts.finish++;if(counts.bytes!==bytes.length)throw Error('incomplete')},tcpListen:async()=>{counts.listen++;return{id:1,port:25579}},addRoomServer:async()=>counts.servers++,joinRoom:async(peer,port)=>{if(peer!=='host'||port!==25579)throw Error('bad join');counts.joined++},tcpClose:async()=>{}});
    peers.host=new Rooms(api('host'),m=>notices.push(m),()=>{},()=>{});
    peers.guest=new Rooms(api('guest'),m=>notices.push(m),m=>peers.guest.accept(m),()=>{});
    await peers.host.invite('guest',25565);
    const until=Date.now()+15000;while(!counts.servers&&Date.now()<until)await new Promise(r=>setTimeout(r,50));
    const stats=await peers.guest.pc.getStats();const transport=[...stats.values()].find(s=>s.type==='transport'&&s.selectedCandidatePairId);const pair=transport&&stats.get(transport.selectedCandidatePairId);const candidate=pair&&stats.get(pair.localCandidateId);const candidateType=candidate?.candidateType;peers.host.close();peers.guest.close();return{counts,notices,candidateType};
  }""")
  assert result['counts']=={'finish':1,'listen':1,'servers':1,'bytes':24013,'joined':1},result
  if private_ice:assert result['candidateType']=='relay',result
  print('PASS: '+('forced external TURN relay; ' if private_ice else '')+'two real WebRTC peers → offer/answer + ICE → 24013 mod bytes → one installed pack + server entry + automatic game join')
  await browser.close()
try: asyncio.run(main())
finally:server.shutdown()

"""Isolated renderer regression: running game, hide/show, navigate to room."""
import asyncio, threading, http.server, functools
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
  await page.add_init_script("""window.handlers={};window.village={state:async()=>({settings:{nickname:'test',aio:true,ram:6,selection:{}},game:{installed:true},os:'linux'}),on:(e,f)=>{handlers[e]=f;return()=>{}},chatList:async()=>({active:'friend',contacts:[{peer:'friend',preview:'сохранено'}],messages:[{text:'сохранено',mine:false}]}),chatOpen:async()=>({active:'friend',contacts:[{peer:'friend',preview:'сохранено'}],messages:[{text:'сохранено',mine:false}]}),worlds:async()=>[],window:async()=>{},openRoom:async()=>{throw Error('Открой одиночный мир в Minecraft.')}}""")
  await page.goto(f'http://127.0.0.1:{server.server_port}')
  await page.wait_for_function("!!window.handlers['game-status']")
  await page.evaluate("handlers['game-status']({state:'running',message:'Игра запущена'});handlers['launcher-visibility']({visible:false});handlers['launcher-visibility']({visible:true})")
  for _ in range(3):
   for tab in ['friends','settings','home']:
    await page.locator(f'.nav-item[data-page="{tab}"]').click()
    await page.wait_for_timeout(350)
    opacity=await page.locator('#page-'+tab).evaluate('e=>Number(getComputedStyle(e).opacity)')
    assert opacity==1, (tab,opacity)
  await page.locator('.nav-item[data-page="friends"]').click()
  await page.locator('#open-room').click()
  assert 'Открой одиночный' in await page.locator('#room-status').inner_text()
  assert await page.locator('#chat-list .saved-chat').count()==1
  assert 'сохранено' in await page.locator('#messages').inner_text()
  await page.reload()
  await page.wait_for_function("document.querySelectorAll('#chat-list .saved-chat').length===1")
  assert 'сохранено' in await page.locator('#messages').inner_text()
  print('PASS: restored saved chat; running game → hide/show → 9 visible tab switches; room failure stays visible')
  await browser.close()
try: asyncio.run(main())
finally:server.shutdown()

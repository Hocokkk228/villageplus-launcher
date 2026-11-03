const{app,BrowserWindow}=require('electron'),path=require('node:path');
app.commandLine.appendSwitch('disable-features','Vulkan');
if(process.env.VP_TEST_HOME)app.setPath('userData',process.env.VP_TEST_HOME);
if(!app.requestSingleInstanceLock())app.quit();else{
 app.on('second-instance',()=>app.emit('vp-reopen'));
 (async()=>{let loading;await app.whenReady();app.on('browser-window-created',(_,win)=>{if(loading&&win!==loading)win.webContents.once('did-finish-load',()=>loading?.close());});try{const archive=await require('../core/launcher-update.cjs').resolve(app.getPath('userData'),require('../package.json').version,message=>{if(!app.isReady())return;if(!loading){loading=new BrowserWindow({width:480,height:190,show:!process.env.VP_HEADLESS,resizable:false,title:'VillagePlus',backgroundColor:'#182019',webPreferences:{sandbox:true,contextIsolation:true,nodeIntegration:false}});loading.loadURL('data:text/html;charset=utf-8,'+encodeURIComponent('<body style="background:#182019;color:#dce6d3;font:16px system-ui;padding:35px"><b>VillagePlus</b><p>Скачиваем обновление лаунчера…</p></body>'));}});if(archive)require(path.join(archive,'electron/main.cjs'));else require('./main.cjs');}catch(error){console.error('Launcher update fallback:',error.message);require('./main.cjs')}})();
}

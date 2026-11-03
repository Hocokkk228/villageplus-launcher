const net=require('node:net');
class TcpBridge{
 constructor(event){this.event=event;this.sockets=new Map();this.servers=new Map();this.serial=1}
 bind(socket,id){this.sockets.set(id,socket);socket.pause();socket.setNoDelay(true);socket.on('data',data=>{socket.pause();this.event({type:'data',id,data:data.toString('base64')})});socket.on('end',()=>this.event({type:'end',id}));socket.on('error',error=>this.event({type:'error',id,message:error.message}));socket.on('close',()=>{this.sockets.delete(id);this.event({type:'close',id})})}
 async listen(){const id=this.serial++,server=net.createServer(socket=>{const sid=this.serial++;this.bind(socket,sid);this.event({type:'open',listener:id,id:sid})});this.servers.set(id,server);try{await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve)});return{id,port:server.address().port}}catch(error){this.servers.delete(id);server.close();throw error}}
 async connect(port){port=Number(port);if(!Number.isInteger(port)||port<1024||port>65535)throw Error('Неверный порт');const id=this.serial++,socket=net.connect(port,'127.0.0.1');this.bind(socket,id);try{await new Promise((resolve,reject)=>{socket.once('connect',resolve);socket.once('error',reject)});return id}catch(error){socket.destroy();throw Error('Мир хоста недоступен на порту '+port+': '+error.message)}}
 async send(id,base64,resume){const socket=this.sockets.get(id);if(!socket)return false;if(base64){if(typeof base64!=='string'||base64.length>131072)throw Error('Слишком большой пакет');await new Promise((resolve,reject)=>socket.write(Buffer.from(base64,'base64'),error=>error?reject(error):resolve()))}if(resume)socket.resume();return true}
 close(id){this.sockets.get(id)?.destroy();this.servers.get(id)?.close();this.servers.delete(id);return true}
 hasListenerPort(port){return[...this.servers.values()].some(server=>server.address()?.port===port)}
 destroy(){for(const socket of this.sockets.values())socket.destroy();for(const server of this.servers.values())server.close();this.servers.clear()}
}
module.exports={TcpBridge};

const{Store}=require('./store.cjs'),crypto=require('node:crypto');
const valid=name=>typeof name==='string'&&/^[a-z0-9_]{2,32}$/.test(name);
class Chats{
 constructor(file){this.store=new Store(file,{accounts:{}})}
 account(name){if(!valid(name))throw Error('Сначала войди в SLON.');return this.store.data.accounts[name]??={active:null,contacts:{}};}
 save(){this.store.save({accounts:this.store.data.accounts})}
 open(account,peer){if(!valid(peer))throw Error('Проверь ник друга.');if(peer===account)throw Error('Укажи ник друга, а не свой.');const a=this.account(account);if(!a.contacts[peer]&&Object.keys(a.contacts).length>=100)throw Error('В списке уже 100 чатов.');a.contacts[peer]??={updated:Date.now(),messages:[]};a.active=peer;this.save();return this.list(account)}
 append(account,peer,text,mine,id,invite){if(!valid(peer)||typeof text!=='string'||!text.trim())return;const a=this.account(account);if(!a.contacts[peer]&&Object.keys(a.contacts).length>=100)return;const c=a.contacts[peer]??={messages:[]};if(id&&c.messages.some(m=>m.id===id))return;c.messages.push({id:id||crypto.randomUUID(),text:text.slice(0,4000),mine:!!mine,time:Date.now(),...(invite?{invite:{room:invite.room,expires:invite.expires}}:{})});c.messages=c.messages.slice(-500);c.updated=Date.now();this.save()}
 list(account){if(!valid(account))return{contacts:[],active:null,messages:[]};const a=this.account(account);return{contacts:Object.entries(a.contacts).map(([peer,c])=>({peer,updated:c.updated,preview:c.messages.at(-1)?.text||''})).sort((a,b)=>b.updated-a.updated),active:a.active,messages:a.contacts[a.active]?.messages||[]}}
}
module.exports={Chats};

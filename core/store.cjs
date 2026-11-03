const fs=require('node:fs');const path=require('node:path');
class Store{constructor(file,defaults={}){this.file=file;this.defaults=defaults;this.data={...defaults};try{this.data={...defaults,...JSON.parse(fs.readFileSync(file,'utf8'))}}catch{}}save(patch){this.data={...this.data,...patch};fs.mkdirSync(path.dirname(this.file),{recursive:true});const tmp=this.file+'.tmp';fs.writeFileSync(tmp,JSON.stringify(this.data,null,2),{mode:0o600});fs.renameSync(tmp,this.file);return this.data;}}
module.exports={Store};

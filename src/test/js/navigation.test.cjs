const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const code=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/app.js'),'utf8');
function fixture(mobile){let closed=0;const events={},attrs={};const close={addEventListener(_,fn){this.click=fn;}},link={addEventListener(_,fn){this.click=fn;}},toggle={setAttribute(k,v){attrs[k]=v;}};
 const sidebar={querySelectorAll(q){return q==='nav a'?[link]:[close];},addEventListener(e,fn){events[e]=fn;}};
 vm.runInNewContext(code,{document:{getElementById(id){return id==='appSidebar'?sidebar:null;},querySelectorAll(){return[toggle];}},window:{matchMedia(){return{matches:mobile};},bootstrap:{Offcanvas:{getOrCreateInstance(){return{hide(){closed++;}};}}}}});return{close,link,events,attrs,closed:()=>closed};}
test('móvil cierra al seleccionar opción y al pulsar X',()=>{const f=fixture(true);f.link.click();assert.equal(f.closed(),1);f.close.click();assert.equal(f.closed(),2);});
test('escritorio no cierra por seleccionar una opción',()=>{const f=fixture(false);f.link.click();assert.equal(f.closed(),0);});
test('el estado accesible sigue al offcanvas',()=>{const f=fixture(true);f.events['shown.bs.offcanvas']();assert.equal(f.attrs['aria-expanded'],'true');f.events['hidden.bs.offcanvas']();assert.equal(f.attrs['aria-expanded'],'false');});

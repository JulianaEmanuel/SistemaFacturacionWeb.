const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/factura-contacto-pago.js'),'utf8');
test('contactos se presentan como texto e importe sigue al total sin enviar cantidades',()=>{
 let change,refresh;
 const cliente={selectedOptions:[{dataset:{direccion:'<img src=x>',correo:'cliente@example.invalid'}}],addEventListener(_,f){change=f;}};
 const elements={idCliente:cliente,'cliente-contacto-direccion':{},'cliente-contacto-correo':{},'cliente-contacto-telefono':{},'factura-total':{textContent:'4.60'},'factura-pago-importe':{}};
 class Observer{constructor(f){refresh=f;}observe(){}}
 vm.runInNewContext(source,{document:{getElementById:id=>elements[id]},MutationObserver:Observer});
 assert.equal(elements['cliente-contacto-direccion'].textContent,'Dirección: <img src=x>');assert.equal(elements['cliente-contacto-telefono'].textContent,'Teléfono: No registrado');assert.equal(elements['factura-pago-importe'].textContent,'$ 4.60');
 elements['factura-total'].textContent='12.25';refresh();assert.equal(elements['factura-pago-importe'].textContent,'$ 12.25');cliente.selectedOptions=[];change();assert.equal(elements['cliente-contacto-correo'].textContent,'Correo: No registrado');
});
test('puede cargarse sin formulario',()=>{assert.doesNotThrow(()=>vm.runInNewContext(source,{document:{getElementById(){return null;}}}));});
test('muestra solo los campos de la forma de pago elegida y calcula el cambio',()=>{
 let alCambiarForma,alEscribir,refrescar;
 const campo=()=>({disabled:false,closest(){return this.grupo.hidden?this.grupo:(this.padre&&this.padre.hidden?this.padre:null);}});
 const grupo=(codigos,campos)=>{const g={hidden:true,dataset:{pagoGrupo:codigos},campos,querySelectorAll(){return campos;}};campos.forEach(c=>{c.grupo=g;});return g;};
 const monto=campo(),banco=campo(),ultimos=campo(),cuotas=campo();
 const efectivo=grupo('EFECTIVO',[monto]),transferencia=grupo('TRANSFERENCIA',[banco]),tarjeta=grupo('DEBITO CREDITO',[ultimos,cuotas]),credito=grupo('CREDITO',[cuotas]);
 cuotas.grupo=credito;cuotas.padre=tarjeta;tarjeta.campos=[ultimos,cuotas];
 const forma={selectedOptions:[{dataset:{codigo:'EFECTIVO'}}],addEventListener(_,f){alCambiarForma=f;}};
 const recibido={value:'',addEventListener(_,f){alEscribir=f;}};
 const elements={idCliente:{selectedOptions:[],addEventListener(){}},idFormaPago:forma,montoRecibido:recibido,'factura-pago-cambio':{},'factura-total':{textContent:'11.64'},'factura-pago-importe':{}};
 class Observer{constructor(f){refrescar=f;}observe(){}}
 vm.runInNewContext(source,{document:{getElementById:id=>elements[id],querySelectorAll:()=>[efectivo,transferencia,tarjeta,credito]},MutationObserver:Observer});
 assert.equal(efectivo.hidden,false);assert.equal(transferencia.hidden,true);assert.equal(tarjeta.hidden,true);
 assert.equal(monto.disabled,false);assert.equal(banco.disabled,true);assert.equal(ultimos.disabled,true);
 assert.equal(elements['factura-pago-cambio'].textContent,'$ 0.00');
 recibido.value='20';alEscribir();assert.equal(elements['factura-pago-cambio'].textContent,'$ 8.36');
 recibido.value='10,5';alEscribir();assert.equal(elements['factura-pago-cambio'].textContent,'Faltan $ 1.14');
 recibido.value='abc';alEscribir();assert.equal(elements['factura-pago-cambio'].textContent,'Monto no válido');
 forma.selectedOptions=[{dataset:{codigo:'DEBITO'}}];alCambiarForma();
 assert.equal(efectivo.hidden,true);assert.equal(tarjeta.hidden,false);assert.equal(credito.hidden,true);
 assert.equal(monto.disabled,true);assert.equal(ultimos.disabled,false);assert.equal(cuotas.disabled,true);
 forma.selectedOptions=[{dataset:{codigo:'CREDITO'}}];alCambiarForma();
 assert.equal(credito.hidden,false);assert.equal(cuotas.disabled,false);
 recibido.value='';elements['factura-total'].textContent='—';refrescar();assert.equal(elements['factura-pago-cambio'].textContent,'—');
});

const test = require('node:test'), assert = require('node:assert/strict'), vm = require('node:vm'), fs = require('node:fs'), path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/reportes-panel.js'),'utf8');
function screen({values=['4.6000'],type='bar',unit='importe',available=true,reduced=false,theme='light'}={}) {
    const charts=[], events={}, canvas={}, status={textContent:''}, rows=values.map(value=>({dataset:{value,label:'Cliente <seguro>'}}));
    const card={dataset:{summaryChart:type,unit,horizontal:'true'},querySelector(s){return s==='canvas'?canvas:status;},querySelectorAll(){return rows;}};
    const root={getAttribute(){return theme;}};let changed;
    class Chart {constructor(_,config){this.config=config;this.options=config.options;charts.push(this);} update(mode){this.mode=mode;}}
    class Observer {constructor(fn){changed=fn;} observe(){}}
    vm.runInNewContext(source,{document:{documentElement:root,querySelectorAll(){return[card];}},window:{Chart:available?Chart:undefined,matchMedia(){return{matches:reduced};},addEventListener(k,v){events[k]=v;}},MutationObserver:Observer,Intl});
    return {charts,canvas,status,events,changeTheme(value){theme=value;changed();}};
}
test('usa cantidades reales para productos y conserva nombres como texto',()=>{
    const ui=screen({unit:'cantidad',values:['1.02','3.50']});assert.equal(ui.charts[0].config.data.datasets[0].data[0],1.02);assert.equal(ui.charts[0].config.data.labels[0],'Cliente <seguro>');
    assert.equal(ui.charts[0].options.indexAxis,'y');
});
test('composicion representa subtotal e IVA recibidos',()=>{
    const ui=screen({type:'doughnut',values:['4.0000','0.6000']});assert.equal(ui.charts[0].config.type,'doughnut');assert.equal(ui.charts[0].config.data.datasets[0].data[1],.6);
});
test('no inventa datos al faltar movimientos o haber valores invalidos',()=>{
    for(const values of [[],[''],['NaN'],['Infinity']]){const ui=screen({values});assert.equal(ui.charts.length,0);assert.ok(ui.status.textContent);assert.equal(ui.canvas.hidden,true);}
    assert.equal(screen({type:'doughnut',values:['0','0']}).charts.length,0);
});
test('sin Chart conserva el aviso de datos detallados',()=>{const ui=screen({available:false});assert.equal(ui.charts.length,0);assert.match(ui.status.textContent,/datos detallados/);});
test('respeta movimiento reducido y adapta colores al tema y la impresion',()=>{
    const ui=screen({reduced:true});assert.equal(ui.charts[0].options.animation,false);
    ui.changeTheme('dark');assert.equal(ui.charts[0].options.plugins.legend.labels.color,'#c6cfe4');assert.equal(ui.charts[0].mode,'none');
    ui.events.beforeprint();assert.equal(ui.charts[0].options.plugins.legend.labels.color,'#596680');
    ui.events.afterprint();assert.equal(ui.charts[0].options.plugins.legend.labels.color,'#c6cfe4');
});

const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/login.js'), 'utf8');
test('el ojo muestra y oculta sin modificar ni almacenar la contraseña', () => {
    const input = { type: 'password', value: 'valor-ficticio' }, attrs = {}; let click;
    const button = { addEventListener(_,fn) { click=fn; }, setAttribute(k,v) {attrs[k]=v;} };
    vm.runInNewContext(source, { document: { getElementById(id) {return id==='password'?input:button;} } });
    click();assert.equal(input.type,'text');assert.equal(attrs['aria-pressed'],'true');assert.equal(attrs['aria-label'],'Ocultar contraseña');
    click();assert.equal(input.type,'password');assert.equal(attrs['aria-pressed'],'false');assert.equal(input.value,'valor-ficticio');
});
test('el script se puede cargar sin el formulario de login', () => {
    assert.doesNotThrow(()=>vm.runInNewContext(source,{document:{getElementById(){return null;}}}));
});

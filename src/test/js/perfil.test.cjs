const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/perfil.js'), 'utf8');
function formulario() {
    const input = {files: [], setCustomValidity(value) {this.validationMessage = value;}, addEventListener(_, fn) {this.change = fn;}};
    const preview = {src: '/perfil/foto'}, error = {}, revoked = [], events = {};
    let next = 0;
    vm.runInNewContext(source, {document: {getElementById(id) {return {'profile-photo': input, 'profile-preview': preview, 'profile-photo-error': error}[id];}},
        URL: {createObjectURL() {return `blob:foto-${++next}`;}, revokeObjectURL(url) {revoked.push(url);}},
        window: {addEventListener(name, fn) {events[name] = fn;}}});
    return {input, preview, error, revoked, events};
}
test('muestra vista previa JPG y PNG y libera las URL anteriores', () => {
    const f = formulario();
    for (const type of ['image/jpeg', 'image/png']) {f.input.files = [{type, size: 1024}]; f.input.change(); assert.match(f.preview.src, /^blob:/); assert.equal(f.error.hidden, true);}
    assert.equal(f.revoked[0], 'blob:foto-1');f.events.pagehide();assert.equal(f.revoked[1], 'blob:foto-2');
});
test('rechaza formato no admitido y exceso de 2 MB', () => {
    for (const file of [{type: 'image/svg+xml', size: 10}, {type: 'image/png', size: 2097153}]) {
        const f = formulario();f.input.files = [file];f.input.change();
        assert.equal(f.error.hidden, false);assert.ok(f.input.validationMessage);assert.equal(f.preview.src, '/perfil/foto');
    }
});
test('cancelar la seleccion restaura la foto actual y elimina el error', () => {
    const f = formulario();f.input.files = [{type: 'image/png', size: 100}];f.input.change();
    f.input.files = [];f.input.change();assert.equal(f.preview.src, '/perfil/foto');assert.equal(f.input.validationMessage, '');assert.equal(f.error.hidden, true);
});
test('puede cargarse sin formulario', () => {
    assert.doesNotThrow(() => vm.runInNewContext(source, {document: {getElementById() {return null;}}}));
});

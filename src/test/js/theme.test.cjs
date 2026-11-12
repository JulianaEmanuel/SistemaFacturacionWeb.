const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/theme.js'), 'utf8');
function screen({saved = null, dark = false, blocked = false, loading = false} = {}) {
    const attrs = {}, events = {}, dom = {}, systemEvents = {}, writes = [], icon = {};
    const root = {setAttribute(k,v) {attrs[k] = v;}, getAttribute(k) {return attrs[k];}};
    const button = {hidden: true, attrs: {}, setAttribute(k,v) {this.attrs[k] = v;}, querySelector() {return icon;}, addEventListener(_,fn) {this.click = fn;}};
    const system = {matches: dark, addEventListener(k,v) {systemEvents[k] = v;}};
    let ready = !loading;
    vm.runInNewContext(source, {document: {documentElement: root, readyState: loading ? 'loading' : 'complete',
        querySelectorAll() {return ready ? [button] : [];}, addEventListener(k,v) {dom[k] = v;}},
        window: {matchMedia() {return system;}, addEventListener(k,v) {events[k] = v;}},
        localStorage: {getItem() {if (blocked) throw Error('No disponible'); return saved;}, setItem(k,v) {if (blocked) throw Error('No disponible'); writes.push([k,v]);}}});
    return {attrs, button, icon, events, systemEvents, system, writes, ready() {ready = true; dom.DOMContentLoaded();}};
}
test('adopta el tema del sistema cuando no existe preferencia', () => {
    assert.equal(screen({dark:true}).attrs['data-bs-theme'], 'dark');
    assert.equal(screen().attrs['data-bs-theme'], 'light');
});
test('la preferencia guardada prevalece y los valores invalidos se ignoran', () => {
    assert.equal(screen({saved:'light',dark:true}).attrs['data-bs-theme'], 'light');
    assert.equal(screen({saved:'invalido',dark:true}).attrs['data-bs-theme'], 'dark');
});
test('el boton alterna, guarda y actualiza su estado accesible', () => {
    const s = screen();s.button.click();
    assert.equal(s.attrs['data-bs-theme'], 'dark');assert.equal(s.button.attrs['aria-pressed'], 'true');
    assert.equal(s.button.attrs['aria-label'], 'Activar modo claro');assert.equal(s.icon.className, 'bi bi-sun');
    assert.deepEqual(s.writes[0], ['sistemaweb.theme','dark']);
    s.button.click();assert.equal(s.attrs['data-bs-theme'], 'light');assert.equal(s.button.attrs['aria-pressed'], 'false');
});
test('funciona aunque localStorage este bloqueado', () => {
    const s = screen({blocked:true});assert.doesNotThrow(() => s.button.click());assert.equal(s.attrs['data-bs-theme'], 'dark');
});
test('sigue los cambios del sistema hasta elegir un modo explicitamente', () => {
    const s = screen();s.systemEvents.change({matches:true});assert.equal(s.attrs['data-bs-theme'], 'dark');
    s.button.click();s.systemEvents.change({matches:true});assert.equal(s.attrs['data-bs-theme'], 'light');
});
test('sincroniza la preferencia entre pestanas', () => {
    const s = screen();s.events.storage({key:'sistemaweb.theme',newValue:'dark'});assert.equal(s.attrs['data-bs-theme'], 'dark');
    s.events.storage({key:'otraClave',newValue:'light'});assert.equal(s.attrs['data-bs-theme'], 'dark');
    s.events.storage({key:null,newValue:null});assert.equal(s.attrs['data-bs-theme'], 'light');
});
test('aplica el tema en head y enlaza el boton cuando el DOM esta listo', () => {
    const s = screen({saved:'dark',loading:true});assert.equal(s.attrs['data-bs-theme'], 'dark');assert.equal(s.button.hidden, true);
    s.ready();assert.equal(s.button.hidden, false);s.button.click();assert.equal(s.attrs['data-bs-theme'], 'light');
});

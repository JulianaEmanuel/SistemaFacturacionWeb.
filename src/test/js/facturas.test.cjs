const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

// DOM mínimo para ejecutar el archivo real sin dependencias de navegador.
function screen(catalog) {
    const rows = [], elements = new Map();
    const element = () => ({ textContent: '', disabled: false, events: {}, addEventListener(type, fn) { this.events[type] = fn; } });
    const body = element();body.querySelectorAll = () => rows;body.append = row => rows.push(row);
    const form = element();form.reportValidity = () => true;
    function row() {
        const values = Object.fromEntries(['precio', 'porcentaje', 'subtotal', 'iva', 'total'].map(key => [key, element()]));
        const r = { values, remove() { rows.splice(rows.indexOf(this), 1); } };
        const select = { value: '', matches: key => key === 'select', closest: () => r,
            get selectedOptions() { return [{ dataset: catalog[this.value] || {} }]; } };
        const input = { value: '1' };
        r.select = select;r.input = input;
        r.querySelector = key => key === 'select' ? select : key === 'input' ? input : values[/data-valor="([^"]+)"/.exec(key)[1]];
        r.querySelectorAll = () => Object.values(values);
        return r;
    }
    elements.set('factura-form', form);elements.set('factura-lineas', body);
    elements.set('factura-linea-template', { content: { cloneNode: row } });
    for (const id of ['factura-aviso', 'factura-subtotal', 'factura-iva', 'factura-total', 'factura-agregar', 'factura-registrar']) elements.set(id, element());
    const document = { getElementById: id => elements.get(id), querySelector: () => null };
    vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/facturas.js'), 'utf8'),
        { document, window: { addEventListener() {}, print() {} } });
    return { rows, elements, change: r => body.events.change({ target: r.select }), input: () => body.events.input(), add: () => elements.get('factura-agregar').events.click() };
}
test('previsualiza importes con HALF_UP y cuatro decimales internos', () => {
    const ui = screen({ '3': { precio: '12.3456', iva: '15.00' } });
    ui.rows[0].select.value = '3';ui.rows[0].input.value = '2.50';ui.change(ui.rows[0]);
    assert.equal(ui.elements.get('factura-subtotal').textContent, '30.86');
    assert.equal(ui.elements.get('factura-iva').textContent, '4.63');assert.equal(ui.elements.get('factura-total').textContent, '35.49');
});
test('acumula productos duplicados y mantiene los nombres de campos', () => {
    const ui = screen({ '3': { precio: '10.0000', iva: '15.00' } });
    ui.rows[0].select.value = '3';ui.rows[0].input.value = '1.25';ui.change(ui.rows[0]);ui.add();
    ui.rows[1].select.value = '3';ui.rows[1].input.value = '0.75';ui.change(ui.rows[1]);
    assert.equal(ui.rows.length, 1);assert.equal(ui.rows[0].input.value, '2.00');assert.equal(ui.rows[0].input.name, 'lineas[0].cantidad');
    assert.equal(ui.elements.get('factura-total').textContent, '23.00');
});
test('rechaza una cantidad con más de dos decimales en la previsualización', () => {
    const ui = screen({ '3': { precio: '1.0000', iva: '0.00' } });
    ui.rows[0].select.value = '3';ui.rows[0].input.value = '0.001';ui.input();
    assert.match(ui.elements.get('factura-aviso').textContent, /Cantidad/);
});
for (const quantity of ['1', '2', '3', '1.02', '1,02']) {
    test(`cantidad ${quantity}: recalcula sin interpretar separadores de miles`, () => {
        const ui = screen({ '3': { precio: '100.0000', iva: '15.00' } });
        ui.rows[0].select.value = '3';ui.rows[0].input.value = quantity;ui.input();
        const amount = quantity.includes('02') ? '102.00' : `${quantity}00.00`;
        assert.equal(ui.elements.get('factura-subtotal').textContent, amount);
        assert.equal(ui.rows[0].values.porcentaje.textContent, '15 %');
        ui.rows[0].input.value = '2';ui.input();
        assert.equal(ui.elements.get('factura-iva').textContent, '30.00');
        assert.equal(ui.elements.get('factura-total').textContent, '230.00');
    });
}
for (const [rate, text, total] of [['0.00', '0 %', '100.00'], ['5.00', '5 %', '105.00'], ['12.50', '12,5 %', '112.50']]) {
    test(`respeta tarifa configurada ${rate}`, () => {
        const ui = screen({ '3': { precio: '100.0000', iva: rate } });
        ui.rows[0].select.value = '3';ui.change(ui.rows[0]);
        assert.equal(ui.rows[0].values.porcentaje.textContent, text);
        assert.equal(ui.elements.get('factura-total').textContent, total);
    });
}
for (const quantity of ['-1', '0', '1000000', '1,000', '1.000,02', '1,000.02', '1e2']) {
    test(`bloquea envío con cantidad inválida ${quantity}`, () => {
        const ui = screen({ '3': { precio: '1.0000', iva: '15.00' } });
        ui.rows[0].select.value = '3';ui.rows[0].input.value = quantity;ui.input();
        let blocked = false;
        ui.elements.get('factura-form').events.submit({ preventDefault() { blocked = true; } });
        assert.equal(blocked, true);assert.equal(ui.elements.get('factura-total').textContent, '—');
        assert.equal(ui.rows[0].values.porcentaje.textContent, '15 %');
    });
}
test('acumula cantidades con coma y punto sin pérdida de precisión', () => {
    const ui = screen({ '3': { precio: '100.0000', iva: '15.00' } });
    ui.rows[0].select.value = '3';ui.rows[0].input.value = '1,02';ui.change(ui.rows[0]);ui.add();
    ui.rows[1].select.value = '3';ui.rows[1].input.value = '0.98';ui.change(ui.rows[1]);
    assert.equal(ui.rows.length, 1);assert.equal(ui.rows[0].input.value, '2.00');
    assert.equal(ui.elements.get('factura-total').textContent, '230.00');
});

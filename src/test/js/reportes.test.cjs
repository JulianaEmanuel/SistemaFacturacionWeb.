const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function screen(points, { horizontal = false, type = 'line', available = true, reduced = false } = {}) {
    let config = null, printed = false;
    const canvas = { dataset: { tipo: type, horizontal: String(horizontal) }, parentElement: { style: {} } };
    const aviso = { textContent: '' }, printButton = { addEventListener(_, fn) { this.click = fn; } };
    const elements = { 'reporte-chart': canvas, 'reporte-chart-aviso': aviso, 'reporte-imprimir': printButton };
    const document = { getElementById: id => elements[id], querySelectorAll: () => points.map(p => ({ dataset: { etiqueta: p[0], total: p[1] } })) };
    function Chart(_, value) { config = value; }
    vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/reportes.js'), 'utf8'),
        { document, window: { Chart: available ? Chart : undefined, matchMedia() { return {matches:reduced}; }, print() { printed = true; } } });
    return { config, aviso, canvas, printButton, wasPrinted: () => printed };
}
test('respeta movimiento reducido también dentro del gráfico canvas', () => { assert.equal(screen([['Dato','1.0000']],{reduced:true}).config.options.animation,false); });
test('usa exclusivamente datos recibidos del servidor', () => {
    const ui = screen([['2026-10-01', '4.6000'], ['2026-10-02', '0.0000']]);
    assert.equal(ui.config.type, 'line');assert.equal(ui.config.data.labels.length, 2);
    assert.equal(ui.config.data.datasets[0].data[0], 4.6);assert.equal(ui.config.data.datasets[0].data[1], 0);
});
test('ranking horizontal responsive sin modificar los importes', () => {
    const ui = screen([['Producto [3]', '11.5000']], { horizontal: true, type: 'bar' });
    assert.equal(ui.config.options.indexAxis, 'y');assert.equal(ui.config.options.responsive, true);assert.equal(ui.canvas.parentElement.style.height, '360px');
});
test('no inventa puntos cuando faltan datos', () => { assert.equal(screen([]).config, null); });
test('rechaza valores no numéricos y vacíos en vez de inventar ceros', () => {
    for (const value of ['', 'no-numero', 'Infinity']) assert.equal(screen([['Dato', value]]).config, null);
});
test('la tabla sigue disponible cuando no se carga Chart.js', () => {
    const ui = screen([['Dato', '1.0000']], { available: false });assert.equal(ui.config, null);assert.match(ui.aviso.textContent, /tabla/);
});
test('conserva etiquetas como texto y permite impresión', () => {
    const ui = screen([['<script>alert(1)</script>', '1.0000']]);assert.equal(ui.config.data.labels[0], '<script>alert(1)</script>');
    ui.printButton.click();assert.equal(ui.wasPrinted(), true);
});

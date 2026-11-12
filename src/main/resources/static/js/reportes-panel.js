(() => {
    'use strict';
    const cards = [...document.querySelectorAll('[data-summary-chart]')];
    if (!cards.length) return;
    const money = new Intl.NumberFormat('es-EC', {style:'currency', currency:'USD'});
    const quantity = new Intl.NumberFormat('es-EC', {maximumFractionDigits:2});
    const charts = [];
    const colors = ['#8574ed','#5d92ed','#3bbab7','#e47db5','#e9ad60','#a5aaec','#6cb9de','#ba87df','#77bf9b','#d88e7b'];
    const dark = () => document.documentElement.getAttribute('data-bs-theme') === 'dark';
    cards.forEach(card => {
        const canvas = card.querySelector('canvas'), status = card.querySelector('.summary-chart-status');
        const rows = [...card.querySelectorAll('.summary-chart-data li')];
        const values = rows.map(row => /^-?\d+(\.\d+)?$/.test(row.dataset.value || '') ? Number(row.dataset.value) : NaN);
        const empty = text => {canvas.hidden = true; status.textContent = text;};
        if (!rows.length) return empty('Sin movimientos para los filtros seleccionados.');
        if (values.some(value => !Number.isFinite(value))) return empty('No se puede representar esta serie. Consulta los datos detallados.');
        const donut = card.dataset.summaryChart === 'doughnut';
        if (donut && (values.every(value => value === 0) || values.some(value => value < 0))) return empty('Sin importes representables en esta composición.');
        if (typeof window.Chart !== 'function') return empty('La gráfica no está disponible. Puedes consultar los datos detallados.');
        const horizontal = card.dataset.horizontal === 'true', units = card.dataset.unit === 'cantidad';
        const format = value => units ? quantity.format(value) : money.format(value);
        const ink = dark() ? '#c6cfe4' : '#596680', grid = dark() ? '#35415a' : '#e9edf5';
        const valueAxis = {beginAtZero:true, ticks:{color:ink, callback:format}, grid:{color:grid}};
        const categoryAxis = {ticks:{color:ink, maxTicksLimit:horizontal?10:7, callback(value) {
            const label = String(this.getLabelForValue(value)); return label.length > 28 ? label.slice(0,27)+'…' : label;
        }}, grid:{display:false}};
        const chart = new window.Chart(canvas, {
            type:card.dataset.summaryChart,
            data:{labels:rows.map(row => row.dataset.label), datasets:[{
                label:units?'Cantidad vendida':'Total facturado', data:values,
                backgroundColor:donut || horizontal ? colors : 'rgba(133,116,237,.18)',
                borderColor:donut?'transparent':'#8574ed', borderWidth:donut?0:2,
                fill:!horizontal && !donut, tension:.25, borderRadius:donut?0:5, maxBarThickness:34,
                pointRadius:rows.length>60?0:3, pointHoverRadius:5
            }]},
            options:{responsive:true, maintainAspectRatio:false, indexAxis:horizontal?'y':'x',
                animation:window.matchMedia('(prefers-reduced-motion: reduce)').matches?false:{duration:300},
                ...(donut ? {cutout:'70%'} : {scales:horizontal?{x:valueAxis,y:categoryAxis}:{x:categoryAxis,y:valueAxis}}),
                plugins:{legend:{display:donut, position:'bottom', labels:{color:ink, usePointStyle:true, padding:20}},
                    tooltip:{callbacks:{label:context => `${context.label}: ${format(context.parsed && typeof context.parsed==='object' ? context.parsed[horizontal?'x':'y'] : context.parsed)}`}}}
            }
        });
        charts.push(chart);
    });
    const updateColors = (printing = false) => {
        const ink = !printing && dark() ? '#c6cfe4' : '#596680', grid = !printing && dark() ? '#35415a' : '#e9edf5';
        charts.forEach(chart => {
            chart.options.plugins.legend.labels.color = ink;
            Object.values(chart.options.scales || {}).forEach(axis => {
                if(axis.ticks) axis.ticks.color = ink;
                if(axis.grid && axis.grid.display !== false) axis.grid.color = grid;
            });
            chart.update('none');
        });
    };
    new MutationObserver(() => updateColors()).observe(document.documentElement, {attributes:true, attributeFilter:['data-bs-theme']});
    window.addEventListener('beforeprint', () => updateColors(true));
    window.addEventListener('afterprint', () => updateColors());
})();

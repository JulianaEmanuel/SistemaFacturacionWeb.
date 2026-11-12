(() => {
    "use strict";
    document.getElementById("factura-imprimir")?.addEventListener("click", () => window.print());
    const form = document.getElementById("factura-form");
    if (!form) return;
    const body = document.getElementById("factura-lineas");
    const aviso = document.getElementById("factura-aviso");
    // Enteros escalados: evita errores de coma flotante en la previsualización.
    function scaled(text, scale) {
        const match = /^(\d+)(?:\.(\d+))?$/.exec(text);
        if (!match || (match[2] || "").length > scale) throw new Error("Revisa las cantidades y los productos.");
        return BigInt(match[1]) * 10n ** BigInt(scale) + BigInt((match[2] || "").padEnd(scale, "0") || "0");
    }
    const rounded = (value, divisor) => (value + divisor / 2n) / divisor;
    function money(value) {
        const cents = rounded(value, 100n);
        return `${cents / 100n}.${String(cents % 100n).padStart(2, "0")}`;
    }
    function rows() { return [...body.querySelectorAll(".factura-linea")]; }
    function quantity(input) {
        const text = input.value.trim();
        if (!/^[0-9]{1,6}([.,][0-9]{1,2})?$/.test(text)) throw new Error("Cantidad: usa hasta 6 enteros y 2 decimales, con coma o punto y sin separadores de miles.");
        const value = scaled(text.replace(",", "."), 2);
        if (value <= 0n || value > 99999999n) throw new Error("La cantidad debe estar entre 0.01 y 999999.99.");
        return value;
    }
    function percentage(rate) {
        const decimal = String(rate % 100n).padStart(2, "0").replace(/0+$/, "");
        return `${rate / 100n}${decimal ? "," + decimal : ""} %`;
    }
    function reindex() {
        rows().forEach((row, i) => {
            row.querySelector("select").name = `lineas[${i}].idProducto`;
            row.querySelector("input").name = `lineas[${i}].cantidad`;
        });
    }
    function update() {
        reindex(); let subtotal = 0n, iva = 0n, total = 0n;
        aviso.textContent = "";
        try {
            rows().forEach(row => {
                const select = row.querySelector("select"), option = select.selectedOptions[0];
                if (!select.value) { row.querySelectorAll("[data-valor]").forEach(cell => cell.textContent = "—"); return; }
                const price = scaled(option.dataset.precio, 4), rate = scaled(option.dataset.iva, 2);
                row.querySelector('[data-valor="precio"]').textContent = money(price);
                row.querySelector('[data-valor="porcentaje"]').textContent = percentage(rate);
                const units = quantity(row.querySelector("input"));
                const base = rounded(price * units, 100n), tax = rounded(base * rate, 10000n), sum = base + tax;
                const values = { precio: money(price), porcentaje: percentage(rate), subtotal: money(base), iva: money(tax), total: money(sum) };
                Object.entries(values).forEach(([key, value]) => row.querySelector(`[data-valor="${key}"]`).textContent = value);
                subtotal += base; iva += tax; total += sum;
            });
            document.getElementById("factura-subtotal").textContent = money(subtotal);
            document.getElementById("factura-iva").textContent = money(iva);
            document.getElementById("factura-total").textContent = money(total);
            return true;
        } catch (error) {
            aviso.textContent = error.message;
            rows().forEach(row => ["subtotal", "iva", "total"].forEach(key => row.querySelector(`[data-valor="${key}"]`).textContent = "—"));
            ["factura-subtotal", "factura-iva", "factura-total"].forEach(id => document.getElementById(id).textContent = "—");
            return false;
        }
    }
    function add() {
        if (rows().length >= 200) { aviso.textContent = "La factura admite hasta 200 líneas."; return; }
        body.append(document.getElementById("factura-linea-template").content.cloneNode(true)); update();
    }
    function combine(changed) {
        const select = changed.querySelector("select");
        if (!select.value) return;
        const existing = rows().find(row => row !== changed && row.querySelector("select").value === select.value);
        if (!existing) return;
        try {
            const sum = quantity(existing.querySelector("input")) + quantity(changed.querySelector("input"));
            if (sum > 99999999n) throw new Error("La cantidad acumulada excede el máximo permitido.");
            existing.querySelector("input").value = `${sum / 100n}.${String(sum % 100n).padStart(2, "0")}`;
            changed.remove();
        } catch (error) { select.value = ""; aviso.textContent = error.message; }
    }
    document.getElementById("factura-agregar").addEventListener("click", add);
    body.addEventListener("input", update);
    body.addEventListener("change", event => { if (event.target.matches("select")) combine(event.target.closest("tr")); update(); });
    body.addEventListener("click", event => { if (event.target.closest(".factura-quitar")) { event.target.closest("tr").remove(); update(); } });
    form.addEventListener("submit", event => {
        if (!update()) { event.preventDefault(); return; }
        if (!form.reportValidity() || !rows().length) { event.preventDefault(); aviso.textContent = "Selecciona un cliente y al menos un producto con cantidad válida."; return; }
        document.getElementById("factura-registrar").disabled = true;
    });
    window.addEventListener("pageshow", () => { if (!document.querySelector(".alert-warning")) document.getElementById("factura-registrar").disabled = false; });
    if (!rows().length) add(); else { rows().forEach(combine); update(); }
})();

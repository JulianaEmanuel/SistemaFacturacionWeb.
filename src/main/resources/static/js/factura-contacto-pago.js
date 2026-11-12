(() => {
    const cliente=document.getElementById('idCliente');
    if(!cliente)return;
    const pintar=()=>{
        const option=cliente.selectedOptions[0];
        for(const [campo,titulo] of [['direccion','Dirección'],['correo','Correo'],['telefono','Teléfono']]) {
            const destino=document.getElementById(`cliente-contacto-${campo}`);
            if(destino) destino.textContent=`${titulo}: ${option?.dataset[campo] || 'No registrado'}`;
        }
    };
    cliente.addEventListener('change',pintar);pintar();

    // Centavos enteros: evita errores de coma flotante. null si el texto no es un monto válido.
    const centavos=texto=>{
        const m=/^(\d{1,8})(?:[.,](\d{1,2}))?$/.exec((texto||'').trim());
        return m?Number(m[1])*100+Number((m[2]||'').padEnd(2,'0')||0):null;
    };
    const dinero=c=>`$ ${Math.floor(c/100)}.${String(c%100).padStart(2,'0')}`;

    const total=document.getElementById('factura-total'),pago=document.getElementById('factura-pago-importe');
    const forma=document.getElementById('idFormaPago'),recibido=document.getElementById('montoRecibido'),cambio=document.getElementById('factura-pago-cambio');
    const grupos=[...(document.querySelectorAll?.('[data-pago-grupo]')||[])];

    const calcularCambio=()=>{
        if(!cambio)return;
        const aPagar=centavos(total?.textContent),dado=centavos(recibido?.value);
        if(aPagar===null){cambio.textContent='—';return;}
        if(!recibido?.value?.trim()){cambio.textContent=dinero(0);return;}
        if(dado===null){cambio.textContent='Monto no válido';return;}
        cambio.textContent=dado<aPagar?`Faltan ${dinero(aPagar-dado)}`:dinero(dado-aPagar);
    };
    // Solo se muestran y envían los campos de la forma de pago elegida.
    const mostrarGrupos=()=>{
        const codigo=forma?.selectedOptions?.[0]?.dataset?.codigo||'';
        for(const g of grupos) g.hidden=!g.dataset.pagoGrupo.split(' ').includes(codigo);
        for(const g of grupos) for(const campo of g.querySelectorAll('input,select'))
            campo.disabled=campo.closest('[data-pago-grupo][hidden]')!==null;
    };
    if(forma){forma.addEventListener('change',mostrarGrupos);mostrarGrupos();}
    if(recibido) recibido.addEventListener('input',calcularCambio);

    if(total&&pago){const actualizar=()=>{pago.textContent=`$ ${total.textContent}`;calcularCambio();};new MutationObserver(actualizar).observe(total,{childList:true,characterData:true,subtree:true});actualizar();}
})();

package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import com.facturacion.sistemaweb.model.FacturaForm;
import com.facturacion.sistemaweb.service.*;

class FacturaRegistroServiceTest {
    @Test void soloElPrimerEnvioDelTokenEsUnaFacturaNueva() {
        var facturas=mock(FacturaService.class);var envios=new FacturaEnvios();var session=new MockHttpSession();
        when(facturas.usuarioId()).thenReturn(7L);
        var form=new FacturaForm();form.setToken(envios.nuevo(session,7L));
        when(facturas.registrar(form)).thenReturn(99L);
        var registro=new FacturaRegistroService(facturas,envios);
        var primero=registro.registrar(session,form);
        var repetido=registro.registrar(session,form);
        assertEquals(new FacturaRegistroService.Registro(99L,true),primero);
        assertEquals(new FacturaRegistroService.Registro(99L,false),repetido);
        verify(facturas,times(1)).registrar(form);
    }
    @Test void siElGuardadoFallaNoSeMarcaComoNueva() {
        var facturas=mock(FacturaService.class);var envios=new FacturaEnvios();var session=new MockHttpSession();
        when(facturas.usuarioId()).thenReturn(7L);
        var form=new FacturaForm();form.setToken(envios.nuevo(session,7L));
        when(facturas.registrar(form)).thenThrow(new FacturaException("sin stock"));
        assertThrows(FacturaException.class,()->new FacturaRegistroService(facturas,envios).registrar(session,form));
    }
}

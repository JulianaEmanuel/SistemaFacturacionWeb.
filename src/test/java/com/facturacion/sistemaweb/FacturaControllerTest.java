package com.facturacion.sistemaweb;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.facturacion.sistemaweb.controller.FacturaController;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.security.SecurityConfig;
import com.facturacion.sistemaweb.service.*;

@WebMvcTest(FacturaController.class) @ActiveProfiles("sqlserver")
@Import({SecurityConfig.class,FacturaEnvios.class,CapturaVistasConfig.class}) @AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class FacturaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean FacturaService service;
    @MockitoBean FacturaRegistroService registro;
    @MockitoBean FacturaCorreoService correo;
    @MockitoBean UserDetailsService users;
    FacturaVista.Formulario datos(String impedimento) {
        return new FacturaVista.Formulario(7L,"Ana Paz",impedimento,
            List.of(new FacturaVista.ClienteOpcion(2L,"0912345678","Cliente Ejemplo","Av. de prueba",null,"cliente@example.invalid")),
            List.of(new FacturaVista.ProductoOpcion(3L,"Arroz",new BigDecimal("10.1234"),new BigDecimal("15.00"))),
            List.of(new FacturaVista.FormaPagoOpcion(1,"Efectivo")),true);
    }
    FacturaVista vista() {
        var d=new FacturaVista.Linea(3L,"Arroz",BigDecimal.ONE,new BigDecimal("10.1234"),new BigDecimal("15.00"),new BigDecimal("10.1234"),new BigDecimal("1.5185"),new BigDecimal("11.6419"));
        return new FacturaVista(99L,LocalDateTime.of(2026,10,30,12,0),"0912345678","Cliente Ejemplo","Ana Paz",d.subtotal(),d.iva(),d.total(),List.of(d));
    }
    @Test void anonimoNoAccedeYCsrfEsObligatorio() throws Exception {
        mvc.perform(get("/modulos/facturas")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/modulos/facturas").with(user("ana"))).andExpect(status().isForbidden());verifyNoInteractions(registro,service);
    }
    @Test void listadoBusquedaFechasYPaginacion() throws Exception {
        when(service.listar("Ana",LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),0)).thenReturn(new PageImpl<>(List.of(vista()),PageRequest.of(0,20),21));
        mvc.perform(get("/modulos/facturas").with(user("ana")).param("q","Ana").param("desde","2026-01-01").param("hasta","2026-01-31"))
            .andExpect(status().isOk()).andExpect(view().name("facturas/lista"))
            .andExpect(content().string(containsString("Cliente Ejemplo"))).andExpect(content().string(containsString("Siguiente")));
    }
    @Test void formularioTokenCatalogoYEmpleadoSinSelectorArbitrario() throws Exception {
        when(service.formulario()).thenReturn(datos(null));
        mvc.perform(get("/modulos/facturas/nuevo").with(user("ana"))).andExpect(status().isOk()).andExpect(view().name("facturas/formulario"))
            .andExpect(content().string(containsString("Ana Paz"))).andExpect(content().string(containsString("name=\"token\"")))
            .andExpect(content().string(containsString("name=\"_csrf\""))).andExpect(content().string(containsString("name=\"idFormaPago\"")))
            .andExpect(content().string(containsString("Efectivo"))).andExpect(content().string(containsString("cliente@example.invalid")))
            .andExpect(content().string(not(containsString("name=\"idEmpleado\""))));
    }
    @Test void sinEmpleadoSeExplicaYDeshabilitaRegistro() throws Exception {
        when(service.formulario()).thenReturn(datos("Tu cuenta no tiene un empleado asociado."));
        mvc.perform(get("/modulos/facturas/nuevo").with(user("ana"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("no tiene un empleado asociado"))).andExpect(content().string(containsString("disabled")));
    }
    @Test void validaLineasYClienteAntesDeRegistrar() throws Exception {
        when(service.formulario()).thenReturn(datos(null));
        mvc.perform(post("/modulos/facturas").with(user("ana")).with(csrf()).param("token","simulado"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("facturaForm","idCliente","idFormaPago","lineas"));verifyNoInteractions(registro);
    }
    @Test void registraConMocksIgnorandoPreciosEmpleadoYTotalesInyectados() throws Exception {
        when(registro.registrar(any(),any())).thenReturn(new FacturaRegistroService.Registro(99L,true));
        mvc.perform(post("/modulos/facturas").with(user("ana")).with(csrf()).param("token","simulado").param("idFormaPago","1").param("idCliente","2")
                .param("lineas[0].idProducto","3").param("lineas[0].cantidad","1.25").param("idEmpleado","999").param("total","0.01").param("lineas[0].precio","0.01"))
            .andExpect(redirectedUrl("/modulos/facturas/99")).andExpect(flash().attribute("successMessage","Factura registrada correctamente."));
        verify(registro).registrar(any(),argThat(f -> f.getIdCliente()==2L && f.getLineas().size()==1 && f.getLineas().getFirst().getCantidad().equals(new BigDecimal("1.25"))));
    }
    @Test void cantidadesInvalidasNoLleganAlRegistro() throws Exception {
        when(service.formulario()).thenReturn(datos(null));
        for(String cantidad:List.of("0","-1","0.001","1000000","1,000","1.000,02","1,000.02","1e2")) mvc.perform(post("/modulos/facturas").with(user("ana")).with(csrf()).param("token","simulado").param("idFormaPago","1").param("idCliente","2")
                .param("lineas[0].idProducto","3").param("lineas[0].cantidad",cantidad))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("facturaForm","lineas[0].cantidad"));
        verifyNoInteractions(registro);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"1","2","3","1.02","1,02","999999,99"})
    void cantidadesConComaOPuntoSeInterpretanComoDecimales(String cantidad) throws Exception {
        when(registro.registrar(any(),any())).thenReturn(new FacturaRegistroService.Registro(99L,true));
        mvc.perform(post("/modulos/facturas").with(user("ana")).with(csrf()).param("token","simulado").param("idFormaPago","1").param("idCliente","2")
                .param("lineas[0].idProducto","3").param("lineas[0].cantidad",cantidad))
            .andExpect(redirectedUrl("/modulos/facturas/99"));
        verify(registro).registrar(any(),argThat(f -> f.getLineas().getFirst().getCantidad().equals(new BigDecimal(cantidad.replace(',','.')))));
    }
    @Test void tokenInvalidoMuestraErrorSinRegistrar() throws Exception {
        when(service.formulario()).thenReturn(datos(null));when(registro.registrar(any(),any())).thenThrow(new FacturaException("El formulario no es válido."));
        mvc.perform(post("/modulos/facturas").with(user("ana")).with(csrf()).param("token","forjado").param("idFormaPago","1").param("idCliente","2").param("lineas[0].idProducto","3").param("lineas[0].cantidad","1"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("El formulario no es válido.")));
    }
    @Test void detalleHistoricoConImpresionYSinEdicion() throws Exception {
        when(service.consultar(99L)).thenReturn(vista());
        mvc.perform(get("/modulos/facturas/99").with(user("ana"))).andExpect(status().isOk()).andExpect(view().name("facturas/detalle"))
            .andExpect(content().string(containsString("Arroz"))).andExpect(content().string(containsString("Imprimir")))
            .andExpect(content().string(containsString("No registrada en esta factura.")))
            .andExpect(content().string(not(containsString("Eliminar"))));
    }
    @Test void facturaAjenaDenegadaEInexistente404() throws Exception {
        when(service.consultar(99L)).thenThrow(new org.springframework.security.access.AccessDeniedException("Ajena"));
        when(service.consultar(100L)).thenThrow(new NoSuchElementException());
        mvc.perform(get("/modulos/facturas/99").with(user("ana"))).andExpect(status().isForbidden());
        mvc.perform(get("/modulos/facturas/100").with(user("ana"))).andExpect(status().isNotFound());
    }
    private org.springframework.test.web.servlet.ResultActions enviarFactura() throws Exception {
        return mvc.perform(post("/modulos/facturas").with(user("ana")).with(csrf()).param("token","simulado").param("idFormaPago","1").param("idCliente","2")
            .param("lineas[0].idProducto","3").param("lineas[0].cantidad","1"));
    }
    @Test void facturaNuevaSeEnviaUnaVezAlCorreoDelCliente() throws Exception {
        when(registro.registrar(any(),any())).thenReturn(new FacturaRegistroService.Registro(99L,true));
        when(correo.enviar(99L)).thenReturn(new FacturaCorreoService.Resultado(FacturaCorreoService.Estado.ENVIADO,"cliente@example.invalid"));
        enviarFactura().andExpect(redirectedUrl("/modulos/facturas/99"))
            .andExpect(flash().attribute("successMessage","Factura registrada correctamente."))
            .andExpect(flash().attribute("correoMessage",containsString("cliente@example.invalid")));
        verify(correo,times(1)).enviar(99L);
    }
    @Test void dobleEnvioDelFormularioNoReenviaElCorreo() throws Exception {
        when(registro.registrar(any(),any())).thenReturn(new FacturaRegistroService.Registro(99L,false));
        enviarFactura().andExpect(redirectedUrl("/modulos/facturas/99"));
        verifyNoInteractions(correo);
    }
    @Test void falloDeCorreoNoImpideMostrarLaFacturaRegistrada() throws Exception {
        when(registro.registrar(any(),any())).thenReturn(new FacturaRegistroService.Registro(99L,true));
        when(correo.enviar(99L)).thenThrow(new IllegalStateException("smtp caído"));
        enviarFactura().andExpect(redirectedUrl("/modulos/facturas/99"))
            .andExpect(flash().attribute("successMessage","Factura registrada correctamente."))
            .andExpect(flash().attribute("correoWarning",containsString("No se pudo enviar")));
    }
    @Test void clienteSinCorreoSeInforma() throws Exception {
        when(registro.registrar(any(),any())).thenReturn(new FacturaRegistroService.Registro(99L,true));
        when(correo.enviar(99L)).thenReturn(new FacturaCorreoService.Resultado(FacturaCorreoService.Estado.SIN_CORREO,null));
        enviarFactura().andExpect(redirectedUrl("/modulos/facturas/99"))
            .andExpect(flash().attribute("correoWarning",containsString("no tiene un correo")));
    }
    @Test void reenvioDesdeDetalleRequiereCsrfYPermisoSobreLaFactura() throws Exception {
        mvc.perform(post("/modulos/facturas/99/enviar-correo").with(user("ana"))).andExpect(status().isForbidden());
        verifyNoInteractions(correo);
        when(service.consultar(98L)).thenThrow(new org.springframework.security.access.AccessDeniedException("Ajena"));
        mvc.perform(post("/modulos/facturas/98/enviar-correo").with(user("ana")).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(correo);
        when(service.consultar(99L)).thenReturn(vista());
        when(correo.enviar(99L)).thenReturn(new FacturaCorreoService.Resultado(FacturaCorreoService.Estado.ENVIADO,"cliente@example.invalid"));
        mvc.perform(post("/modulos/facturas/99/enviar-correo").with(user("ana")).with(csrf()))
            .andExpect(redirectedUrl("/modulos/facturas/99")).andExpect(flash().attribute("correoMessage",containsString("cliente@example.invalid")));
    }
    @Test void formularioMuestraCamposDePagoSinPedirNumeroCompletoDeTarjeta() throws Exception {
        var conCodigos=new FacturaVista.Formulario(7L,"Ana Paz",null,datos(null).clientes(),datos(null).productos(),
            List.of(new FacturaVista.FormaPagoOpcion(1,"Efectivo","EFECTIVO"),new FacturaVista.FormaPagoOpcion(4,"Tarjeta de crédito","CREDITO")),true);
        when(service.formulario()).thenReturn(conCodigos);
        mvc.perform(get("/modulos/facturas/nuevo").with(user("ana"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("data-codigo=\"CREDITO\"")))
            .andExpect(content().string(containsString("data-pago-grupo=\"EFECTIVO\"")))
            .andExpect(content().string(containsString("data-pago-grupo=\"TRANSFERENCIA\"")))
            .andExpect(content().string(containsString("name=\"ultimosDigitos\"")))
            .andExpect(content().string(containsString("Mastercard")))
            .andExpect(content().string(containsString("nunca</strong> el número completo")))
            .andExpect(content().string(not(containsString("name=\"numeroTarjeta\""))))
            .andExpect(content().string(not(containsString("name=\"cvv\""))));
    }
    @Test void registraLosDatosDelPagoEnElFormulario() throws Exception {
        when(registro.registrar(any(),any())).thenReturn(new FacturaRegistroService.Registro(99L,false));
        mvc.perform(post("/modulos/facturas").with(user("ana")).with(csrf()).param("token","simulado").param("idFormaPago","4").param("idCliente","2")
                .param("lineas[0].idProducto","3").param("lineas[0].cantidad","1")
                .param("montoRecibido","20,50").param("banco","Produbanco").param("referencia","ABC123")
                .param("marcaTarjeta","Visa").param("ultimosDigitos","4321").param("cuotas","6")
                .param("cambio","999").param("numeroTarjeta","4111111111111111"))
            .andExpect(redirectedUrl("/modulos/facturas/99"));
        verify(registro).registrar(any(),argThat(f -> f.getMontoRecibido().equals(new BigDecimal("20.50")) && "Produbanco".equals(f.getBanco())
            && "ABC123".equals(f.getReferencia()) && "Visa".equals(f.getMarcaTarjeta()) && "4321".equals(f.getUltimosDigitos()) && f.getCuotas()==6));
    }
    @Test void montoRecibidoInvalidoSeRechazaAntesDeRegistrar() throws Exception {
        when(service.formulario()).thenReturn(datos(null));
        mvc.perform(post("/modulos/facturas").with(user("ana")).with(csrf()).param("token","simulado").param("idFormaPago","1").param("idCliente","2")
                .param("lineas[0].idProducto","3").param("lineas[0].cantidad","1").param("montoRecibido","1.000,50"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("facturaForm","montoRecibido"))
            .andExpect(content().string(containsString("Ingresa el monto recibido con hasta 2 decimales")))
            .andExpect(content().string(not(containsString("Failed to convert"))));
        verifyNoInteractions(registro);
    }
    @Test void montoRecibidoUsaSuPropioEditorYNoElDeCantidades() throws Exception {
        // 1234567.50 supera las 6 cifras enteras de una cantidad, pero es un monto válido.
        when(registro.registrar(any(),any())).thenReturn(new FacturaRegistroService.Registro(99L,false));
        mvc.perform(post("/modulos/facturas").with(user("ana")).with(csrf()).param("token","simulado").param("idFormaPago","1").param("idCliente","2")
                .param("lineas[0].idProducto","3").param("lineas[0].cantidad","1").param("montoRecibido","1234567.50"))
            .andExpect(redirectedUrl("/modulos/facturas/99"));
        verify(registro).registrar(any(),argThat(f -> f.getMontoRecibido().equals(new BigDecimal("1234567.50"))));
    }
    @Test void detalleMuestraElPagoEnmascarado() throws Exception {
        var base=vista();
        var conTarjeta=new FacturaVista(base.id(),base.fecha(),base.cedula(),base.cliente(),base.empleado(),base.subtotal(),base.iva(),base.total(),base.lineas(),
            new FacturaVista.Contacto("Av. de prueba",null,null,true),
            new FacturaVista.Pago("Tarjeta de crédito",base.total(),null,null,null,"ABC123","Visa","4321",3));
        when(service.consultar(99L)).thenReturn(conTarjeta);
        mvc.perform(get("/modulos/facturas/99").with(user("ana"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Visa •••• 4321 · Autorización ABC123 · 3 cuotas")));
    }
    @Test void detalleConCorreoMuestraBotonDeEnvio() throws Exception {
        var base=vista();
        var conCorreo=new FacturaVista(base.id(),base.fecha(),base.cedula(),base.cliente(),base.empleado(),base.subtotal(),base.iva(),base.total(),base.lineas(),
            new FacturaVista.Contacto("Av. de prueba",null,"cliente@example.invalid",true),new FacturaVista.Pago("Efectivo",base.total()));
        when(service.consultar(99L)).thenReturn(conCorreo);
        mvc.perform(get("/modulos/facturas/99").with(user("ana"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Enviar por correo"))).andExpect(content().string(containsString("/modulos/facturas/99/enviar-correo")));
        when(service.consultar(99L)).thenReturn(base);
        mvc.perform(get("/modulos/facturas/99").with(user("ana"))).andExpect(content().string(not(containsString("Enviar por correo"))));
    }
}

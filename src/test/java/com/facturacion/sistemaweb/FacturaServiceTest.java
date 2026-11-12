package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.service.*;

@ExtendWith(MockitoExtension.class)
class FacturaServiceTest {
    @Mock FacturaRepository facturas;
    @Mock DetalleFacturaRepository detalles;
    @Mock PersonaRepository personas;
    @Mock ProductoRepository productos;
    @Mock EntityManager em;
    @Mock EmpleadoSesionService sesion;
    @Mock FormaPagoRepository formasPago;@Mock FacturaPagoRepository pagos;@Mock FacturaClienteRepository historicos;@Mock PersonaContactosRepository contactos;@Mock FacturacionSchema schema;
    FacturaService service;
    Persona persona;
    @BeforeEach void setup() {
        service=new FacturaService(facturas,detalles,personas,productos,em,sesion,new FacturaCalculoService(),formasPago,pagos,historicos,contactos,schema);
        persona=new Persona();persona.setIdPersona(2L);persona.setNombresPersona("Ana   ");persona.setApellidosPersona("Paz  ");persona.setCedulaPersona("0912345678   ");
    }
    Empleado empleado() { var e=mock(Empleado.class);lenient().when(e.getIdEmpleado()).thenReturn(23L);lenient().when(e.getPersona()).thenReturn(persona);return e; }
    FacturaForm form() { var f=new FacturaForm();f.setIdCliente(2L);f.setIdFormaPago(1);f.setLineas(List.of(FacturaCalculoTest.linea(3,"1.25"),FacturaCalculoTest.linea(3,"0.75")));return f; }
    Producto producto() { var p=new Producto();p.setIdProducto(3L);p.setNombreProducto("Arroz   ");p.setPrecioUnitario(new BigDecimal("10.1234"));p.setIvaProducto(new BigDecimal("15.00"));return p; }
    void contexto() {
        var forma=mock(FormaPago.class);lenient().when(forma.isActiva()).thenReturn(true);lenient().when(forma.getId()).thenReturn(1);lenient().when(forma.getNombre()).thenReturn("Efectivo");lenient().when(em.find(FormaPago.class,1,LockModeType.PESSIMISTIC_READ)).thenReturn(forma);
        when(sesion.actual(true)).thenReturn(new EmpleadoSesionService.Cuenta(7L,false));doReturn(empleado()).when(sesion).empleado(7L,true);
    }
    void datos() { contexto();when(em.find(Persona.class,2L,LockModeType.PESSIMISTIC_READ)).thenReturn(persona);when(em.find(Producto.class,3L,LockModeType.PESSIMISTIC_READ)).thenReturn(producto()); }
    void guardarCabecera() {
        when(facturas.saveAndFlush(any())).thenAnswer(i -> { Factura f=i.getArgument(0);ReflectionTestUtils.setField(f,"idFactura",99L);return f; });
    }
    @Test void registroCalculaDesdeBaseAcumulaYGuardaAmbosSinCascadas() {
        datos();guardarCabecera();assertEquals(99L,service.registrar(form()));
        var header=ArgumentCaptor.forClass(Factura.class);verify(facturas).saveAndFlush(header.capture());
        assertEquals(new BigDecimal("20.2468"),header.getValue().getSubtotal());assertEquals(new BigDecimal("23.2838"),header.getValue().getTotal());assertNotNull(header.getValue().getFecha());
        verify(detalles).saveAllAndFlush(argThat(items -> {
            var list=new ArrayList<DetalleFactura>();items.forEach(list::add);
            return list.size()==1 && list.getFirst().getCantidad().equals(new BigDecimal("2.00")) && list.getFirst().getFactura()==header.getValue()
                && list.getFirst().getPrecio().equals(new BigDecimal("10.1234"));
        }));
        verify(em).find(Producto.class,3L,LockModeType.PESSIMISTIC_READ);verifyNoInteractions(personas,productos);
    }
    @Test void clienteInexistenteNoGuardaCabecera() {
        contexto();assertThrows(FacturaException.class,()->service.registrar(form()));verifyNoInteractions(facturas,detalles);
    }
    @Test void nuevasFacturasUsanLaTarifaActualDelProducto() {
        datos();guardarCabecera();var actualizado=producto();actualizado.setIvaProducto(new BigDecimal("5.00"));
        when(em.find(Producto.class,3L,LockModeType.PESSIMISTIC_READ)).thenReturn(actualizado);
        service.registrar(form());
        verify(detalles).saveAllAndFlush(argThat(items -> {
            var list=new ArrayList<DetalleFactura>();items.forEach(list::add);
            return list.getFirst().getIva().equals(new BigDecimal("5.00")) && list.getFirst().getTotal().equals(new BigDecimal("21.2591"));
        }));
    }
    @Test void productoInexistenteNoGuardaCabecera() {
        contexto();when(em.find(Persona.class,2L,LockModeType.PESSIMISTIC_READ)).thenReturn(persona);
        assertThrows(FacturaException.class,()->service.registrar(form()));verifyNoInteractions(facturas,detalles);
    }
    @Test void sinEmpleadoOAsociacionAmbiguaNoRegistra() {
        when(sesion.actual(true)).thenReturn(new EmpleadoSesionService.Cuenta(7L,false));
        when(sesion.empleado(7L,true)).thenThrow(new FacturaException("Sin asociación"));
        assertThrows(FacturaException.class,()->service.registrar(form()));verifyNoInteractions(em,facturas,detalles);
    }
    @Test void sinDetallesNoPersiste() {
        contexto();when(em.find(Persona.class,2L,LockModeType.PESSIMISTIC_READ)).thenReturn(persona);
        var f=form();f.setLineas(List.of());assertThrows(FacturaException.class,()->service.registrar(f));verifyNoInteractions(facturas,detalles);
    }
    @Test void empleadoSoloListaSusFacturasConFechasInclusivas() {
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,false));doReturn(empleado()).when(sesion).empleado(7L,false);
        when(facturas.buscar(any(),anyString(),any(),any(),any())).thenReturn(Page.empty());
        service.listar(" Ana ",LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),0);
        verify(facturas).buscar(eq(23L),eq("Ana"),eq(LocalDateTime.of(2026,1,1,0,0)),eq(LocalDateTime.of(2026,2,1,0,0)),eq(PageRequest.of(0,20)));
    }
    @Test void pagoCoincideConTotalYCopiaDeClienteNoCambiaAlEditarPersona() {
        datos();guardarCabecera();persona.setDireccionPersona("Calle de prueba");persona.setCorreoPersona("cliente@example.invalid");
        service.registrar(form());
        var pago=ArgumentCaptor.forClass(FacturaPago.class);verify(pagos).saveAndFlush(pago.capture());
        assertEquals(99L,pago.getValue().getIdFactura());assertEquals(new BigDecimal("23.2838"),pago.getValue().getImporte());assertEquals("Efectivo",pago.getValue().getNombre());
        var copia=ArgumentCaptor.forClass(FacturaCliente.class);verify(historicos).saveAndFlush(copia.capture());
        persona.setNombresPersona("Nombre cambiado");persona.setDireccionPersona("Otra dirección");
        assertEquals("Ana",copia.getValue().getNombres());assertEquals("Calle de prueba",copia.getValue().getDireccion());
    }
    @Test void pagoInactivoONoExistenteNoPersisteLaFactura() {
        contexto();when(em.find(Persona.class,2L,LockModeType.PESSIMISTIC_READ)).thenReturn(persona);
        var inactiva=mock(FormaPago.class);when(em.find(FormaPago.class,1,LockModeType.PESSIMISTIC_READ)).thenReturn(inactiva);
        assertThrows(FacturaException.class,()->service.registrar(form()));
        when(em.find(FormaPago.class,1,LockModeType.PESSIMISTIC_READ)).thenReturn(null);
        assertThrows(FacturaException.class,()->service.registrar(form()));verifyNoInteractions(facturas,detalles,pagos,historicos);
    }
    @Test void sinMigracionNoIntentaGuardarUnaFacturaParcial() {
        when(sesion.actual(true)).thenReturn(new EmpleadoSesionService.Cuenta(7L,false));
        doThrow(new FacturaException("Migración pendiente")).when(schema).exigirDisponible();
        assertThrows(FacturaException.class,()->service.registrar(form()));verifyNoInteractions(facturas,detalles,pagos,historicos);
    }
    @Test void consultaPriorizaLaCopiaHistoricaSobreLaPersonaActual() {
        persona.setDireccionPersona("Dirección original");var copia=new FacturaCliente(99L,persona);persona.setNombresPersona("Otro nombre");
        when(schema.disponible()).thenReturn(true);when(historicos.findById(99L)).thenReturn(Optional.of(copia));
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,true));
        Empleado responsable=empleado();
        when(facturas.completo(99L)).thenReturn(Optional.of(cabecera(responsable)));
        var vista=service.consultar(99L);assertEquals("Ana Paz",vista.cliente());assertTrue(vista.contacto().historico());assertEquals("Dirección original",vista.contacto().direccion());
        assertNull(vista.pago());verifyNoInteractions(personas);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"pago","cliente"})
    void falloEnPagoOCopiaRevierteTodaLaTransaccion(String destino) {
        datos();guardarCabecera();
        if(destino.equals("pago"))doThrow(new IllegalStateException("Fallo simulado")).when(pagos).saveAndFlush(any());
        else doThrow(new IllegalStateException("Fallo simulado")).when(historicos).saveAndFlush(any());
        var commits=new java.util.concurrent.atomic.AtomicInteger();var rollbacks=new java.util.concurrent.atomic.AtomicInteger();
        var tx=new AbstractPlatformTransactionManager(){protected Object doGetTransaction(){return new Object();}protected void doBegin(Object t,TransactionDefinition d){}protected void doCommit(DefaultTransactionStatus s){commits.incrementAndGet();}protected void doRollback(DefaultTransactionStatus s){rollbacks.incrementAndGet();}};
        var factory=new ProxyFactory(service);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        assertThrows(IllegalStateException.class,()->((FacturaService)factory.getProxy()).registrar(form()));assertEquals(0,commits.get());assertEquals(1,rollbacks.get());
    }
    @Test void adminPuedeListarTodasSinSerEmpleado() {
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,true));when(facturas.buscar(any(),anyString(),any(),any(),any())).thenReturn(Page.empty());
        service.listar("",null,null,0);verify(facturas).buscar(isNull(),eq(""),isNull(),isNull(),any());verify(sesion,never()).empleado(any(),anyBoolean());
    }
    Factura cabecera(Empleado empleado) {
        var f=new Factura();ReflectionTestUtils.setField(f,"idFactura",99L);f.setEmpleado(empleado);f.setIdPersona(2L);f.setFecha(LocalDateTime.of(2026,1,1,12,0));f.setSubtotal(new BigDecimal("10.0000"));f.setTotal(new BigDecimal("11.5000"));return f;
    }
    @Test void empleadoNoConsultaFacturaAjena() {
        var otro=mock(Empleado.class);when(otro.getIdEmpleado()).thenReturn(44L);
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,false));doReturn(empleado()).when(sesion).empleado(7L,false);
        when(facturas.completo(99L)).thenReturn(Optional.of(cabecera(otro)));
        assertThrows(AccessDeniedException.class,()->service.consultar(99L));verifyNoInteractions(detalles,personas);
    }
    @Test void consultaUsaPrecioIvaYTotalesHistoricos() {
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,true));doReturn(Optional.of(cabecera(empleado()))).when(facturas).completo(99L);
        var p=producto();p.setPrecioUnitario(new BigDecimal("999"));p.setIvaProducto(new BigDecimal("50"));
        var d=new DetalleFactura();d.setProducto(p);d.setCantidad(BigDecimal.ONE);d.setPrecio(new BigDecimal("10.0000"));d.setIva(new BigDecimal("15.00"));d.setSubtotal(new BigDecimal("10.0000"));d.setTotal(new BigDecimal("11.5000"));
        when(detalles.deFactura(99L)).thenReturn(List.of(d));when(personas.findById(2L)).thenReturn(Optional.of(persona));
        var vista=service.consultar(99L);assertEquals(new BigDecimal("10.0000"),vista.lineas().getFirst().precio());assertEquals(new BigDecimal("15.00"),vista.lineas().getFirst().porcentaje());assertEquals(new BigDecimal("1.5000"),vista.iva());
    }
    @Test void falloDeDetalleHaceRollbackCompletoEnLaFronteraTransaccional() {
        datos();guardarCabecera();doThrow(new IllegalStateException("Fallo simulado")).when(detalles).saveAllAndFlush(any());
        var commits=new java.util.concurrent.atomic.AtomicInteger();var rollbacks=new java.util.concurrent.atomic.AtomicInteger();
        var tx=new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object transaction,TransactionDefinition definition) { assertEquals(TransactionDefinition.ISOLATION_SERIALIZABLE,definition.getIsolationLevel()); }
            protected void doCommit(DefaultTransactionStatus s) { commits.incrementAndGet(); }
            protected void doRollback(DefaultTransactionStatus s) { rollbacks.incrementAndGet(); }
        };
        var factory=new ProxyFactory(service);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        assertThrows(IllegalStateException.class,()->((FacturaService)factory.getProxy()).registrar(form()));
        assertEquals(0,commits.get());assertEquals(1,rollbacks.get());
    }
}

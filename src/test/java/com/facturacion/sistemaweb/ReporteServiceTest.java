package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.model.ReporteResultado.*;
import com.facturacion.sistemaweb.repository.ReporteRepository;
import com.facturacion.sistemaweb.service.*;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {
    @Mock ReporteRepository repository;
    @Mock EmpleadoSesionService sesion;
    ReporteService service;
    @BeforeEach void setup() {
        service=new ReporteService(repository,sesion,Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"),ZoneId.of("America/Guayaquil")));
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,true));
    }
    static Resumen resumen(long n,String sub,String iva,String total) { return new Resumen(n,new BigDecimal(sub),new BigDecimal(iva),new BigDecimal(total)); }
    static Fila fila(String id,String total) { return new Fila(id,"Nombre "+id,"",1,new BigDecimal("1.00"),new BigDecimal(total),BigDecimal.ZERO,new BigDecimal(total)); }
    void vacio() { when(repository.resumen(any(),any())).thenReturn(resumen(0,"0","0","0")); }
    @Test void limiteIncluyeDiezMilSinTruncar() {
        assertDoesNotThrow(()->ReporteService.exigirLimite(10000));
        assertThrows(ReporteException.class,()->ReporteService.exigirLimite(10001));
        vacio();service.consultar(ReporteTipo.RESUMEN,new ReporteFiltro());
    }
    @Test void mesActualYFechasExclusivas() {
        vacio();var r=service.consultar(ReporteTipo.RESUMEN,new ReporteFiltro());
        assertEquals(LocalDate.of(2026,10,1),r.filtro().desde());assertEquals(LocalDateTime.of(2026,10,31,0,0),r.filtro().finExclusivo());
        assertTrue(r.sinVentas());assertEquals(new BigDecimal("0.0000"),r.resumen().ticket());
    }
    @Test void calculaResumenConPrecisionYPresentacionHalfUp() {
        when(repository.resumen(any(),any())).thenReturn(resumen(3,"3.0000","0.0150","3.0150"));
        var r=service.consultar(ReporteTipo.RESUMEN,new ReporteFiltro());
        assertEquals(new BigDecimal("1.0050"),r.resumen().ticket());assertEquals("1.01",r.resumen().ticketVisible());assertEquals("3.02",r.resumen().totalVisible());
    }
    @Test void rellenaDiasSinInventarVentas() {
        when(repository.resumen(any(),any())).thenReturn(resumen(1,"4","0.6","4.6"));
        when(repository.filas(any(),any())).thenReturn(List.of(fila("2026-10-02","4.6")));
        var f=new ReporteFiltro();f.setDesde(LocalDate.of(2026,10,1));f.setHasta(LocalDate.of(2026,10,3));
        var r=service.consultar(ReporteTipo.VENTAS,f);assertEquals(3,r.filas().size());assertEquals(0,r.filas().getFirst().facturas());
        assertEquals(new BigDecimal("4.6000"),r.filas().get(1).total());assertEquals("line",r.tipoGrafico());
    }
    @Test void mesesSeAgrupanConAnioYSeCompletan() {
        vacio();var f=new ReporteFiltro();f.setAgrupacion("mes");f.setDesde(LocalDate.of(2025,12,1));f.setHasta(LocalDate.of(2026,2,2));
        var r=service.consultar(ReporteTipo.VENTAS,f);assertEquals(List.of("2025-12","2026-01","2026-02"),r.filas().stream().map(Fila::id).toList());assertEquals("bar",r.tipoGrafico());
    }
    @Test void empleadoSiempreSeResuelveDesdeSesion() {
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,false));var e=mock(Empleado.class);when(e.getIdEmpleado()).thenReturn(55L);when(sesion.empleado(7L,false)).thenReturn(e);vacio();
        for(var tipo:ReporteTipo.values()) {
            var r=service.consultar(tipo,new ReporteFiltro());assertEquals(55L,r.filtro().empleado());assertFalse(r.administrador());
        }
        verify(repository,times(5)).resumen(any(),argThat(f -> f.empleado().equals(55L)));
    }
    @Test void empleadoNoPuedeAmpliarAlcancePorParametro() {
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,false));var e=mock(Empleado.class);when(e.getIdEmpleado()).thenReturn(55L);when(sesion.empleado(7L,false)).thenReturn(e);
        var f=new ReporteFiltro();f.setEmpleado(99L);assertThrows(AccessDeniedException.class,()->service.consultar(ReporteTipo.VENTAS,f));verifyNoInteractions(repository);
    }
    @Test void administradorPuedeFiltrarOtroEmpleadoSinAsociacionPropia() {
        vacio();var f=new ReporteFiltro();f.setEmpleado(99L);assertEquals(99L,service.consultar(ReporteTipo.EMPLEADOS,f).filtro().empleado());verify(sesion,never()).empleado(any(),anyBoolean());
    }
    @Test void empleadoSinAsociacionNoObtieneResultadosGlobales() {
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,false));when(sesion.empleado(7L,false)).thenThrow(new FacturaException("Sin empleado"));
        assertThrows(ReporteException.class,()->service.consultar(ReporteTipo.CLIENTES,new ReporteFiltro()));verifyNoInteractions(repository);
    }
    @Test void conservaFiltrosYRankingAutorizado() {
        vacio();var f=new ReporteFiltro();f.setCliente(2L);f.setProducto(3L);f.setOrden("importe");f.setLimite(10);f.setQ(" Ana ");
        var r=service.consultar(ReporteTipo.PRODUCTOS,f);assertEquals("Ana",r.filtro().q());assertEquals(3L,r.filtro().producto());assertTrue(r.horizontal());
        verify(repository).filas(eq(ReporteTipo.PRODUCTOS),argThat(x -> x.orden().equals("importe") && x.cliente()==2L && x.limite()==10));
    }
    @Test void rechazaRangoMayorAUnAnioYFechasInvertidas() {
        var f=new ReporteFiltro();f.setDesde(LocalDate.of(2025,1,1));f.setHasta(LocalDate.of(2026,1,1));assertThrows(ReporteException.class,()->service.consultar(ReporteTipo.VENTAS,f));
        f.setDesde(LocalDate.of(2026,10,30));f.setHasta(LocalDate.of(2026,10,1));assertThrows(ReporteException.class,()->service.consultar(ReporteTipo.VENTAS,f));verifyNoInteractions(repository);
    }
    @Test void aceptaAnioBisiestoCompleto() {
        vacio();var f=new ReporteFiltro();f.setDesde(LocalDate.of(2024,1,1));f.setHasta(LocalDate.of(2024,12,31));assertEquals(366,service.consultar(ReporteTipo.VENTAS,f).filas().size());
    }
    @Test void rechazaFiltrosFueraDeListaYLimites() {
        var f=new ReporteFiltro();f.setOrden("total; DELETE");assertThrows(ReporteException.class,()->service.consultar(ReporteTipo.PRODUCTOS,f));
        var agrup=new ReporteFiltro();agrup.setAgrupacion("otro");assertThrows(ReporteException.class,()->service.consultar(ReporteTipo.VENTAS,agrup));
    }
    @Test void rechazaLimiteDeRankingEIdsInvalidos() {
        var f=new ReporteFiltro();f.setLimite(101);assertThrows(ReporteException.class,()->service.consultar(ReporteTipo.PRODUCTOS,f));
        f.setLimite(10);f.setCliente(-1L);assertThrows(ReporteException.class,()->service.consultar(ReporteTipo.CLIENTES,f));
    }
    @Test void rechazaMasDeDiezMilFilasSinTruncamientoSilencioso() {
        vacio();when(repository.filas(any(),any())).thenReturn(Collections.nCopies(10001,fila("1","1")));
        assertThrows(ReporteException.class,()->service.consultar(ReporteTipo.CLIENTES,new ReporteFiltro()));
    }
    @Test void graficoLimitadoNoTruncaLaTabla() {
        vacio();List<Fila> filas=new ArrayList<>();for(int i=0;i<30;i++) filas.add(fila(""+i,""+i));when(repository.filas(any(),any())).thenReturn(filas);
        var r=service.consultar(ReporteTipo.CLIENTES,new ReporteFiltro());assertEquals(30,r.filas().size());assertEquals(20,r.grafico().size());assertEquals(new BigDecimal("29.0000"),r.grafico().getFirst().total());
    }
}

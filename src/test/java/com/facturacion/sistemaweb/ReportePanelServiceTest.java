package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.model.ReporteResultado.*;
import com.facturacion.sistemaweb.repository.ReporteRepository;
import com.facturacion.sistemaweb.service.*;

@ExtendWith(MockitoExtension.class)
class ReportePanelServiceTest {
    @Mock ReporteRepository repository;
    @Mock EmpleadoSesionService sesion;
    ReporteService service;
    ReporteFiltro filtro;
    @BeforeEach void preparar() {
        service=new ReporteService(repository,sesion);
        filtro=new ReporteFiltro();filtro.setDesde(LocalDate.of(2026,11,1));filtro.setHasta(LocalDate.of(2026,11,3));
    }
    Fila fila(String id,String total) {return new Fila(id,"Nombre "+id,"",1,BigDecimal.ONE,new BigDecimal(total),BigDecimal.ZERO,new BigDecimal(total));}
    void adminConDatos() {
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,true));
        when(repository.filas(any(),any())).thenReturn(List.of());
        when(repository.resumen(eq(ReporteTipo.RESUMEN),any())).thenReturn(new Resumen(20,new BigDecimal("210"),BigDecimal.ZERO,new BigDecimal("210")));
    }
    @Test void combinaVentasClientesYProductosConPeriodoCompletoYRankingLimitado() {
        adminConDatos();
        when(repository.filas(eq(ReporteTipo.VENTAS),any())).thenReturn(List.of(fila("2026-11-01","110"),fila("2026-11-02","100")));
        when(repository.filas(eq(ReporteTipo.CLIENTES),any())).thenReturn(java.util.stream.IntStream.rangeClosed(1,20).mapToObj(i->fila(""+i,""+i)).toList());
        when(repository.filas(eq(ReporteTipo.PRODUCTOS),any())).thenReturn(List.of(fila("producto","10")));
        var panel=service.consultarPanel(filtro);
        assertEquals(3,panel.ventas().size());assertEquals(new BigDecimal("0.0000"),panel.ventas().getLast().total());
        assertEquals(20,panel.clientesFacturados());assertEquals(10,panel.clientes().size());assertEquals("20",panel.clientes().getFirst().id());
        assertEquals("producto",panel.productos().getFirst().id());
        assertEquals(new BigDecimal("210.0000"),panel.reporte().resumen().total());
        verify(repository).filas(eq(ReporteTipo.PRODUCTOS),argThat(f->f.orden().equals("cantidad")&&f.limite()==10));
    }
    @Test void todasLasSeriesDeUnEmpleadoSeRestringenASuPropioId() {
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,false));
        var empleado=mock(Empleado.class);when(empleado.getIdEmpleado()).thenReturn(55L);
        when(sesion.empleado(7L,false)).thenReturn(empleado);
        when(repository.resumen(eq(ReporteTipo.RESUMEN),any())).thenReturn(new Resumen(1,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ONE));
        filtro.setCliente(9L);filtro.setProducto(11L);filtro.setQ("Ana");service.consultarPanel(filtro);
        ArgumentCaptor<ReporteFiltro.Validado> captor=ArgumentCaptor.forClass(ReporteFiltro.Validado.class);
        verify(repository,times(4)).filas(any(),captor.capture());
        for(var f:captor.getAllValues()) {
            assertEquals(55L,f.empleado());assertEquals(9L,f.cliente());assertEquals(11L,f.producto());assertEquals("Ana",f.q());
        }
    }
    @Test void noPermiteConsultarElPanelDeOtroEmpleado() {
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,false));
        var empleado=mock(Empleado.class);when(empleado.getIdEmpleado()).thenReturn(55L);when(sesion.empleado(7L,false)).thenReturn(empleado);
        filtro.setEmpleado(99L);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.consultarPanel(filtro));verifyNoInteractions(repository);
    }
    @Test void sinVentasNoInventaSeriesNiClientes() {
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,true));
        when(repository.resumen(eq(ReporteTipo.RESUMEN),any())).thenReturn(new Resumen(0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var panel=service.consultarPanel(filtro);assertTrue(panel.ventas().isEmpty());assertTrue(panel.productos().isEmpty());assertEquals(0,panel.clientesFacturados());
        verify(repository,never()).filas(eq(ReporteTipo.CLIENTES),any());
    }
    @Test void noTruncaUnConjuntoDeClientesQueExcedeElLimite() {
        adminConDatos();when(repository.filas(eq(ReporteTipo.CLIENTES),any())).thenReturn(Collections.nCopies(10001,fila("1","1")));
        assertThrows(ReporteException.class,()->service.consultarPanel(filtro));
    }
}

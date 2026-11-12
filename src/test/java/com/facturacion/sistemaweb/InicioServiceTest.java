package com.facturacion.sistemaweb;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.facturacion.sistemaweb.model.Empleado;
import com.facturacion.sistemaweb.repository.InicioRepository;
import com.facturacion.sistemaweb.service.*;

class InicioServiceTest {
    @Test void administradorConsultaTotalesRealesGlobales() {
        var repo=mock(InicioRepository.class);var sesion=mock(EmpleadoSesionService.class);
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(824L,true));when(repo.clientes()).thenReturn(7L);when(repo.productos()).thenReturn(9L);
        when(repo.totales(null)).thenReturn(new InicioService.Totales(3,new BigDecimal("15.1250")));when(repo.recientes(null)).thenReturn(List.of());
        var p=new InicioService(repo,sesion).consultar();assertEquals(7,p.clientes());assertEquals(3L,p.facturas());assertEquals("15.13",p.ventasVisible());verify(sesion,never()).empleado(anyLong(),anyBoolean());
    }
    @Test void empleadoSoloConsultaSusFacturas() {
        var repo=mock(InicioRepository.class);var sesion=mock(EmpleadoSesionService.class);var e=mock(Empleado.class);when(e.getIdEmpleado()).thenReturn(55L);
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(824L,false));when(sesion.empleado(824L,false)).thenReturn(e);
        when(repo.totales(55L)).thenReturn(new InicioService.Totales(0,new BigDecimal("0.0000")));when(repo.recientes(55L)).thenReturn(List.of());
        var p=new InicioService(repo,sesion).consultar();assertEquals("0.00",p.ventasVisible());verify(repo).totales(55L);verify(repo).recientes(55L);verify(repo,never()).totales(null);
    }
    @Test void sinEmpleadoNoInventaVentasCeroNiAmpliaElAlcance() {
        var repo=mock(InicioRepository.class);var sesion=mock(EmpleadoSesionService.class);when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(824L,false));
        when(sesion.empleado(824L,false)).thenThrow(new FacturaException("Asocia un empleado"));
        var p=new InicioService(repo,sesion).consultar();assertNull(p.facturas());assertNull(p.ventas());assertEquals("—",p.ventasVisible());verify(repo,never()).totales(any());verify(repo,never()).recientes(any());
    }
}

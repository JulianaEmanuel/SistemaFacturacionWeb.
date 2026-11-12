package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.repository.ReporteRepository;
import com.facturacion.sistemaweb.service.*;

@SpringJUnitConfig(ReporteAccessTest.Config.class)
class ReporteAccessTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean ReporteRepository repository() { return mock(ReporteRepository.class); }
        @Bean EmpleadoSesionService sesion() { return mock(EmpleadoSesionService.class); }
        @Bean ReporteService servicio(ReporteRepository repo,EmpleadoSesionService sesion) { return new ReporteService(repo,sesion,Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"),ZoneOffset.UTC)); }
        @Bean ReportePdfService pdf(ReporteService service) { return new ReportePdfService(service); }
        @Bean ReporteExcelService excel(ReporteService service) { return new ReporteExcelService(service); }
    }
    @Autowired ReporteRepository repository;
    @Autowired EmpleadoSesionService sesion;
    @Autowired ReporteService service;
    @Autowired ReportePdfService pdf;
    @Autowired ReporteExcelService excel;
    @BeforeEach void setup() {
        reset(repository,sesion);when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(7L,false));
        var e=mock(Empleado.class);when(e.getIdEmpleado()).thenReturn(55L);when(sesion.empleado(7L,false)).thenReturn(e);
        when(repository.resumen(any(),any())).thenReturn(ReporteServiceTest.resumen(1,"4","0.6","4.6"));
        when(repository.filas(any(),any())).thenReturn(List.of(ReporteServiceTest.fila("55","4.6")));
    }
    @Test @WithAnonymousUser void anonimoNoPuedeInvocarServiciosNiExportadores() {
        assertThrows(AccessDeniedException.class,()->service.consultar(ReporteTipo.RESUMEN,new ReporteFiltro()));
        assertThrows(AccessDeniedException.class,()->pdf.generar(ReporteTipo.CLIENTES,new ReporteFiltro()));
        assertThrows(AccessDeniedException.class,()->excel.generar(ReporteTipo.CLIENTES,new ReporteFiltro()));verifyNoInteractions(repository);
    }
    @Test @WithMockUser void descargasDirectasRechazanAlcanceAjeno() {
        var f=new ReporteFiltro();f.setEmpleado(99L);
        assertThrows(AccessDeniedException.class,()->pdf.generar(ReporteTipo.CLIENTES,f));
        assertThrows(AccessDeniedException.class,()->excel.generar(ReporteTipo.CLIENTES,f));verifyNoInteractions(repository);
    }
    @Test @WithMockUser void todasLasExportacionesDelEmpleadoConsultanSoloSuId() {
        assertTrue(pdf.generar(ReporteTipo.CLIENTES,new ReporteFiltro()).length>0);assertTrue(excel.generar(ReporteTipo.CLIENTES,new ReporteFiltro()).length>0);
        verify(repository,times(2)).resumen(eq(ReporteTipo.CLIENTES),argThat(f -> f.empleado().equals(55L)));
        verify(repository,times(2)).filas(eq(ReporteTipo.CLIENTES),argThat(f -> f.empleado().equals(55L)));
    }
    @Test @WithMockUser void administradorPuedeExportarGlobalSinDependerDeIdFijo() {
        when(sesion.actual(false)).thenReturn(new EmpleadoSesionService.Cuenta(824L,true));
        pdf.generar(ReporteTipo.EMPLEADOS,new ReporteFiltro());excel.generar(ReporteTipo.EMPLEADOS,new ReporteFiltro());
        verify(repository,times(2)).resumen(eq(ReporteTipo.EMPLEADOS),argThat(f -> f.empleado()==null));
    }
}

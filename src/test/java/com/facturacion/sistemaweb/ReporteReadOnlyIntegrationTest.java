package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.*;
import com.facturacion.sistemaweb.model.*;
import com.facturacion.sistemaweb.repository.ReporteRepository;

/** Solo SELECT reales: no crea facturas, usuarios ni datos de preparación. */
@SpringBootTest @ActiveProfiles("sqlserver") @Transactional(readOnly=true,isolation=Isolation.SERIALIZABLE)
class ReporteReadOnlyIntegrationTest {
    @Autowired ReporteRepository repository;
    @Autowired JdbcTemplate jdbc;
    ReporteFiltro.Validado filtro(String agrupacion,String orden,Long empleado) {
        return new ReporteFiltro.Validado(LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),null,null,empleado,agrupacion,orden,"",10);
    }
    @Test void agrupacionesYRankingsEjecutanContraSqlServer() {
        for(var tipo:ReporteTipo.values()) { repository.resumen(tipo,filtro("dia","cantidad",null));repository.filas(tipo,filtro("dia","cantidad",null)); }
        repository.filas(ReporteTipo.VENTAS,filtro("mes","cantidad",null));repository.filas(ReporteTipo.VENTAS,filtro("factura","cantidad",null));
        repository.filas(ReporteTipo.PRODUCTOS,filtro("dia","importe",null));
    }
    @Test void alcanceSinEmpleadoValidoNoDevuelveDatos() {
        for(var tipo:ReporteTipo.values()) {
            var f=filtro("dia","cantidad",-1L);assertEquals(0,repository.resumen(tipo,f).facturas());assertTrue(repository.filas(tipo,f).isEmpty());
        }
    }
    @Test void cabecerasNoSeMultiplicanPorDetalle() {
        var f=filtro("dia","cantidad",null);var r=repository.resumen(ReporteTipo.RESUMEN,f);
        var esperado=jdbc.queryForObject("SELECT COALESCE(SUM(TOTAL_FACTURA),0) FROM dbo.Factura WHERE FECHA_FACTURA>=? AND FECHA_FACTURA<?",BigDecimal.class,f.inicio(),f.finExclusivo());
        assertEquals(0,esperado.compareTo(r.total()));
        var count=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Factura WHERE FECHA_FACTURA>=? AND FECHA_FACTURA<?",Long.class,f.inicio(),f.finExclusivo());assertEquals(count,r.facturas());
    }
    @Test void filtroProductoSoloIncluyeSusLineasHistoricas() {
        var f=new ReporteFiltro.Validado(LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),null,-1L,null,"dia","cantidad","",10);
        assertEquals(0,repository.resumen(ReporteTipo.PRODUCTOS,f).facturas());assertTrue(repository.filas(ReporteTipo.PRODUCTOS,f).isEmpty());
    }
}

package com.facturacion.sistemaweb;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.facturacion.sistemaweb.repository.InicioRepository;

@SpringBootTest @ActiveProfiles("sqlserver") @Transactional(readOnly=true)
class InicioReadOnlyIntegrationTest {
    @Autowired InicioRepository inicio;
    @Autowired JdbcTemplate jdbc;
    @Test void indicadoresCoincidenConLasTablasSinEscribir() {
        assertEquals(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Persona",Long.class).longValue(),inicio.clientes());
        assertEquals(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Producto",Long.class).longValue(),inicio.productos());
        assertEquals(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Factura",Long.class).longValue(),inicio.totales(null).facturas());
        var total=jdbc.queryForObject("SELECT COALESCE(SUM(TOTAL_FACTURA),0) FROM dbo.Factura",java.math.BigDecimal.class);
        assertEquals(0,total.compareTo(inicio.totales(null).ventas()));assertTrue(inicio.recientes(null).size()<=5);
    }
    @Test void alcanceAjenoInexistenteNoDevuelveDatos() {
        assertEquals(0,inicio.totales(-1L).facturas());assertEquals(0,inicio.totales(-1L).ventas().signum());assertTrue(inicio.recientes(-1L).isEmpty());
    }
}

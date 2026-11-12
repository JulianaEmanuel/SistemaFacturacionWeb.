package com.facturacion.sistemaweb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.facturacion.sistemaweb.repository.*;

/** Únicamente SELECT reales: no emite facturas ni actualiza registros. */
@SpringBootTest @ActiveProfiles("sqlserver") @Transactional(readOnly=true)
class FacturaReadOnlyIntegrationTest {
    @Autowired FacturaRepository facturas;
    @Autowired DetalleFacturaRepository detalles;
    @Autowired FacturaClienteRepository clientesFactura;
    @Autowired FacturaPagoRepository pagos;
    @Autowired FormaPagoRepository formasPago;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.facturacion.sistemaweb.service.EmpleadoSesionService sesion;
    @Test @Transactional(readOnly=false)
    void consultasDeBloqueoNoInsertanRegistros() {
        org.junit.jupiter.api.Assertions.assertNull(em.find(com.facturacion.sistemaweb.model.Persona.class,-1L,jakarta.persistence.LockModeType.PESSIMISTIC_READ));
        org.junit.jupiter.api.Assertions.assertNull(em.find(com.facturacion.sistemaweb.model.Producto.class,-1L,jakarta.persistence.LockModeType.PESSIMISTIC_READ));
        org.junit.jupiter.api.Assertions.assertNull(em.find(com.facturacion.sistemaweb.model.Usuario.class,-1L,jakarta.persistence.LockModeType.PESSIMISTIC_READ));
        org.junit.jupiter.api.Assertions.assertThrows(com.facturacion.sistemaweb.service.FacturaException.class,()->sesion.empleado(-1L,true));
    }
    @Test void consultasPaginadasMapeosYDetalleCompilanContraSqlServer() {
        facturas.buscar(null,"",null,null,PageRequest.of(0,20));
        facturas.buscar(-1L,"consulta-de-lectura",java.time.LocalDateTime.of(2026,1,1,0,0),java.time.LocalDateTime.of(2027,1,1,0,0),PageRequest.of(0,20));
        facturas.completo(-1L);detalles.deFactura(-1L);
    }
    @Test void tablasDeFacturaUsanLosNombresFisicosDelEsquemaExistente() {
        org.junit.jupiter.api.Assertions.assertEquals("SistemaFacturacionWeb",
            jdbc.queryForObject("SELECT DB_NAME()",String.class));
        clientesFactura.findById(-1L);
        pagos.findByIdFactura(-1L);
        formasPago.findByActivaTrueOrderByNombreAsc();
    }
}

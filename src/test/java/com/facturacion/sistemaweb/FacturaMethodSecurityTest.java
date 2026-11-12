package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import com.facturacion.sistemaweb.repository.*;
import com.facturacion.sistemaweb.service.*;

@SpringJUnitConfig(FacturaMethodSecurityTest.Config.class)
class FacturaMethodSecurityTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean EmpleadoSesionService sesion() { return mock(EmpleadoSesionService.class); }
        @Bean FacturaService facturas(EmpleadoSesionService sesion) { return new FacturaService(mock(FacturaRepository.class),mock(DetalleFacturaRepository.class),mock(PersonaRepository.class),mock(ProductoRepository.class),mock(EntityManager.class),sesion,new FacturaCalculoService(),mock(FormaPagoRepository.class),mock(FacturaPagoRepository.class),mock(FacturaClienteRepository.class),mock(PersonaContactosRepository.class),mock(FacturacionSchema.class)); }
        @Bean FacturaRegistroService registro(FacturaService facturas) { return new FacturaRegistroService(facturas,new FacturaEnvios()); }
    }
    @Autowired FacturaService facturas;
    @Autowired FacturaRegistroService registro;
    @Test @WithAnonymousUser void anonimoNoConsultaNiRegistraDesdeServicio() {
        assertThrows(AccessDeniedException.class,()->facturas.listar("",null,null,0));
        assertThrows(AccessDeniedException.class,()->facturas.registrar(new com.facturacion.sistemaweb.model.FacturaForm()));
        assertThrows(AccessDeniedException.class,()->registro.registrar(new org.springframework.mock.web.MockHttpSession(),new com.facturacion.sistemaweb.model.FacturaForm()));
    }
}

package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.facturacion.sistemaweb.repository.*;

@SpringBootTest @ActiveProfiles("sqlserver") @Transactional(readOnly=true)
class AdministracionReadOnlyIntegrationTest {
    @Autowired AdministracionRepository usuarios;
    @Autowired TipoUsuarioRepository tipos;
    @Autowired EmpleadoRepository empleados;
    @Test void consultasRealesSinEscriturasNiCredenciales() {
        usuarios.buscar("",null,null);usuarios.buscar("consulta-de-lectura",-1,false);usuarios.buscarId(-1L);
        usuarios.buscarNombre("consulta-de-lectura");usuarios.esAdmin("consulta-de-lectura","ADMINISTRADOR");
        usuarios.tipoReservado(-1,"ADMINISTRADOR");usuarios.administradoresActivos("ADMINISTRADOR");
        assertFalse(usuarios.nombreDuplicado("consulta-de-lectura",null));assertFalse(usuarios.tipoEnUso(-1));assertTrue(usuarios.totalUsuarios()>=0);
        tipos.buscar("");tipos.duplicados("consulta-de-lectura",null);
        empleados.usuariosDisponibles(null);empleados.usuariosDisponibles(-1L);
    }
}

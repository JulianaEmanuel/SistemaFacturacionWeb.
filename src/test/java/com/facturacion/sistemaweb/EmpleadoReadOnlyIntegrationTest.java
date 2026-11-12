package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.facturacion.sistemaweb.repository.EmpleadoRepository;

/** Solo GET y SELECT contra SQL Server. Ninguna operación de escritura real. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sqlserver")
@Transactional(readOnly = true)
@org.springframework.security.test.context.support.WithMockUser(authorities={"USER","ACCOUNT_ID_1"})
class EmpleadoReadOnlyIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.facturacion.sistemaweb.repository.AdministracionRepository sesiones;
    @org.junit.jupiter.api.BeforeEach void sesionAutenticada() {
        org.mockito.Mockito.when(sesiones.buscarNombre(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.Optional.of(new com.facturacion.sistemaweb.model.UsuarioResumen(1L, 1, "user", "OPERADOR", true)));
    }
    @Autowired MockMvc mvc;
    @Autowired EmpleadoRepository repository;
    @Test void rutasYConsultasRealesDeLectura() throws Exception {
        mvc.perform(get("/modulos/empleados")).andExpect(status().isOk()).andExpect(view().name("empleados/lista"));
        mvc.perform(get("/modulos/empleados").param("q", "consulta-sin-escrituras"))
            .andExpect(status().isOk()).andExpect(view().name("empleados/lista"));
        mvc.perform(get("/modulos/empleados/nuevo")).andExpect(status().isOk()).andExpect(view().name("empleados/formulario"));
        repository.personasDisponibles(-1L); repository.usuariosDisponibles(-1L);
        assertFalse(repository.personaAsignada(-1L, null));
        assertFalse(repository.usuarioAsignado(-1L, null));
        assertFalse(repository.tieneFacturas(-1L));
        var empleados = repository.buscar("");
        if (!empleados.isEmpty()) {
            Long id = empleados.getFirst().getIdEmpleado();
            mvc.perform(get("/modulos/empleados/" + id + "/editar"))
                .andExpect(status().isOk()).andExpect(view().name("empleados/formulario"));
        }
        mvc.perform(get("/modulos/empleados/0/editar")).andExpect(status().isNotFound());
    }
}

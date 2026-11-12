package com.facturacion.sistemaweb;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.facturacion.sistemaweb.repository.PersonaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sqlserver")
@org.springframework.security.test.context.support.WithMockUser(authorities={"USER","ACCOUNT_ID_1"})
@org.springframework.context.annotation.Import(CapturaVistasConfig.class)
class ClienteControllerIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.facturacion.sistemaweb.repository.AdministracionRepository sesiones;
    @org.junit.jupiter.api.BeforeEach void sesionAutenticada() {
        org.mockito.Mockito.when(sesiones.buscarNombre(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.Optional.of(new com.facturacion.sistemaweb.model.UsuarioResumen(1L, 1, "user", "OPERADOR", true)));
    }

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PersonaRepository personaRepository;

	@Test
	void listaClientesLeePersonasDesdeSqlServer() throws Exception {
		mockMvc.perform(get("/modulos/clientes"))
				.andExpect(status().isOk())
				.andExpect(view().name("clientes/lista"))
				.andExpect(content().string(containsString("Gestión de clientes")));
	}

	@Test
	void formularioNuevoSeMuestraSinEscrituras() throws Exception {
		mockMvc.perform(get("/modulos/clientes/nuevo"))
				.andExpect(status().isOk())
				.andExpect(view().name("clientes/formulario"))
				.andExpect(content().string(containsString("Cédula")));
	}

	@Test
	void formularioInvalidoSeRechazaAntesDePersistir() throws Exception {
		mockMvc.perform(post("/modulos/clientes").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
					.param("cedulaPersona", "")
					.param("nombresPersona", "")
					.param("apellidosPersona", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("clientes/formulario"))
				.andExpect(content().string(containsString("La cédula es obligatoria.")));
	}

	@Test
	void duplicateQueriesExecuteAgainstCharPaddedColumnWithoutWriting() {
		personaRepository.existeCedula("0000000000");
		personaRepository.existeCedulaEnOtraPersona("0000000000", -1L);
	}
}
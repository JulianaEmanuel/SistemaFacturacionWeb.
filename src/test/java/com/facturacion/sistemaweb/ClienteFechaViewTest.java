package com.facturacion.sistemaweb;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import com.facturacion.sistemaweb.controller.ClienteController;
import com.facturacion.sistemaweb.model.Persona;
import com.facturacion.sistemaweb.service.PersonaService;

@WebMvcTest(controllers = ClienteController.class)
@ActiveProfiles("sqlserver")
@org.springframework.security.test.context.support.WithMockUser
@org.springframework.context.annotation.Import(com.facturacion.sistemaweb.security.SecurityConfig.class)
class ClienteFechaViewTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.security.core.userdetails.UserDetailsService autenticacionSimulada;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PersonaService personaService;

	@Test
	void listaFormateaFechaYRepresentaNullConGuion() throws Exception {
		Persona conFecha = persona(1L, LocalDateTime.of(2026, 10, 9, 17, 45));
		Persona sinFecha = persona(2L, null);
		when(personaService.listar(null)).thenReturn(List.of(conFecha, sinFecha));

		mockMvc.perform(get("/modulos/clientes"))
				.andExpect(status().isOk())
				.andExpect(view().name("clientes/lista"))
				.andExpect(content().string(containsString("data-label=\"Fecha de nacimiento\">09/10/2026</td>")))
				.andExpect(content().string(containsString("data-label=\"Fecha de nacimiento\">-</td>")));
	}

	private Persona persona(Long id, LocalDateTime fechaNacimiento) {
		Persona persona = new Persona();
		persona.setIdPersona(id);
		persona.setCedulaPersona("0912345678");
		persona.setNombresPersona("Persona");
		persona.setApellidosPersona("Prueba");
		persona.setFechaNacimientoPersona(fechaNacimiento);
		return persona;
	}
}
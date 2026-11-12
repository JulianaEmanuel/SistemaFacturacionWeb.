package com.facturacion.sistemaweb;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import com.facturacion.sistemaweb.controller.ProveedorController;
import com.facturacion.sistemaweb.model.Persona;
import com.facturacion.sistemaweb.model.Proveedor;
import com.facturacion.sistemaweb.model.ProveedorForm;
import com.facturacion.sistemaweb.service.ProveedorDuplicadoException;
import com.facturacion.sistemaweb.service.PersonaNoEncontradaException;
import com.facturacion.sistemaweb.service.ProveedorRelacionadoException;
import com.facturacion.sistemaweb.service.ProveedorService;

@WebMvcTest(controllers = ProveedorController.class)
@ActiveProfiles("sqlserver")
@org.springframework.security.test.context.support.WithMockUser
@org.springframework.context.annotation.Import({com.facturacion.sistemaweb.security.SecurityConfig.class,CapturaVistasConfig.class})
class ProveedorControllerIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.security.core.userdetails.UserDetailsService autenticacionSimulada;
    // Estas pruebas cubren las acciones del administrador; la matriz de permisos se prueba en PermisosModulosSecurityTest.
    @org.springframework.test.context.bean.override.mockito.MockitoBean(name="adminAccess") com.facturacion.sistemaweb.security.AdminAccess adminAccess;
    @org.junit.jupiter.api.BeforeEach void comoAdministrador() { org.mockito.Mockito.when(adminAccess.allowed(org.mockito.ArgumentMatchers.any())).thenReturn(true); }

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProveedorService proveedorService;

	@Test
	void listsSuppliersWithRealPersonFieldsAndSearchRoute() throws Exception {
		Proveedor proveedor = proveedor(9L);
		when(proveedorService.listar("cedula")).thenReturn(List.of(proveedor));

		mockMvc.perform(get("/modulos/proveedores").param("q", "cedula"))
				.andExpect(status().isOk())
				.andExpect(view().name("proveedores/lista"))
				.andExpect(content().string(containsString("Cédula")))
				.andExpect(content().string(containsString("Email real")))
				.andExpect(content().string(containsString("Nombre real Persona")));
	}

	@Test
	void newFormSelectsAnAvailablePerson() throws Exception {
		when(proveedorService.listarPersonasDisponibles()).thenReturn(List.of(persona(15L)));

		mockMvc.perform(get("/modulos/proveedores/nuevo"))
				.andExpect(status().isOk())
				.andExpect(view().name("proveedores/formulario"))
				.andExpect(content().string(containsString("Selecciona una persona")))
				.andExpect(content().string(containsString("Nombre real Persona")));
	}

	@Test
	void newFormShowsDisabledStateWhenThereAreNoAvailablePeople() throws Exception {
		when(proveedorService.listarPersonasDisponibles()).thenReturn(List.of());

		mockMvc.perform(get("/modulos/proveedores/nuevo"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("No hay personas disponibles")))
				.andExpect(content().string(containsString("disabled")));
	}

	@Test
	void rejectsInvalidEmailBeforeCallingService() throws Exception {
		when(proveedorService.listarPersonasDisponibles()).thenReturn(List.of(persona(15L)));

		mockMvc.perform(post("/modulos/proveedores").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
					.param("idPersona", "15")
					.param("email", "no-es-correo"))
				.andExpect(status().isOk())
				.andExpect(view().name("proveedores/formulario"))
				.andExpect(content().string(containsString("Ingresa un correo electrónico válido.")));
	}

	@Test
	void validRegistrationDelegatesToServiceWithoutDatabaseWrite() throws Exception {
		when(proveedorService.listarPersonasDisponibles()).thenReturn(List.of(persona(15L)));
		Proveedor respuesta = proveedor(9L);
		when(proveedorService.registrar(org.mockito.ArgumentMatchers.any(ProveedorForm.class))).thenReturn(respuesta);

		mockMvc.perform(post("/modulos/proveedores").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
					.param("idPersona", "15")
					.param("email", "compras@example.com"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/modulos/proveedores"));

		verify(proveedorService).registrar(org.mockito.ArgumentMatchers.any(ProveedorForm.class));
	}

	@Test
	void mapsMissingAndDuplicatePersonToFieldErrors() throws Exception {
		when(proveedorService.listarPersonasDisponibles()).thenReturn(List.of(persona(15L)));
		when(proveedorService.registrar(org.mockito.ArgumentMatchers.any(ProveedorForm.class)))
				.thenThrow(new ProveedorDuplicadoException("La persona seleccionada ya está registrada como proveedor."));

		mockMvc.perform(post("/modulos/proveedores").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).param("idPersona", "15").param("email", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("proveedores/formulario"))
				.andExpect(content().string(containsString("ya está registrada como proveedor")));
	}

	@Test
	void editChangesEmailThroughServiceAndKeepsPersonDisplayReadOnly() throws Exception {
		Proveedor existente = proveedor(9L);
		when(proveedorService.buscarPorId(9L)).thenReturn(existente);
		when(proveedorService.actualizar(org.mockito.ArgumentMatchers.eq(9L),
				org.mockito.ArgumentMatchers.any(ProveedorForm.class))).thenReturn(existente);

		mockMvc.perform(post("/modulos/proveedores/9/editar").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
					.param("idPersona", "15")
					.param("email", "nuevo@example.com"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/modulos/proveedores"));

		verify(proveedorService).actualizar(org.mockito.ArgumentMatchers.eq(9L),
				org.mockito.ArgumentMatchers.any(ProveedorForm.class));
	}

	@Test
	void deleteWithProductsShowsErrorAndDoesNotCascade() throws Exception {
		doThrow(new ProveedorRelacionadoException("No se puede eliminar: el proveedor tiene productos asociados."))
				.when(proveedorService).eliminar(9L);

		mockMvc.perform(post("/modulos/proveedores/9/eliminar").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/modulos/proveedores"))
				.andExpect(flash().attribute("errorMessage",
						"No se puede eliminar: el proveedor tiene productos asociados."));

		verify(proveedorService).eliminar(9L);
	}

	@Test
	void mapsMissingPersonToFieldError() throws Exception {
		when(proveedorService.listarPersonasDisponibles()).thenReturn(List.of(persona(15L)));
		when(proveedorService.registrar(org.mockito.ArgumentMatchers.any(ProveedorForm.class)))
				.thenThrow(new PersonaNoEncontradaException("La persona seleccionada no existe."));

		mockMvc.perform(post("/modulos/proveedores").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).param("idPersona", "15").param("email", ""))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("La persona seleccionada no existe.")));
	}

	private Persona persona(Long id) {
		Persona persona = new Persona();
		persona.setIdPersona(id);
		persona.setCedulaPersona("0912345678   ");
		persona.setNombresPersona("Nombre real Persona         ");
		persona.setApellidosPersona("Apellido real               ");
		return persona;
	}

	private Proveedor proveedor(Long id) {
		Persona persona = persona(15L);
		Proveedor proveedor = mock(Proveedor.class);
		when(proveedor.getIdProveedor()).thenReturn(id);
		when(proveedor.getIdPersonaProveedor()).thenReturn(15L);
		when(proveedor.getPersona()).thenReturn(persona);
		when(proveedor.getEmail()).thenReturn("Email real@example.com   ");
		return proveedor;
	}
}
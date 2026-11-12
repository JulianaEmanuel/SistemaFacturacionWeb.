package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facturacion.sistemaweb.model.Persona;
import com.facturacion.sistemaweb.model.Proveedor;
import com.facturacion.sistemaweb.model.ProveedorForm;
import com.facturacion.sistemaweb.repository.PersonaRepository;
import com.facturacion.sistemaweb.repository.ProveedorRepository;
import com.facturacion.sistemaweb.service.PersonaNoEncontradaException;
import com.facturacion.sistemaweb.service.ProveedorDuplicadoException;
import com.facturacion.sistemaweb.service.ProveedorRelacionadoException;
import com.facturacion.sistemaweb.service.ProveedorService;

@ExtendWith(MockitoExtension.class)
class ProveedorServiceTest {

	@Mock
	private ProveedorRepository proveedorRepository;

	@Mock
	private PersonaRepository personaRepository;

	private ProveedorService proveedorService;

	@BeforeEach
	void setUp() {
		proveedorService = new ProveedorService(proveedorRepository, personaRepository);
	}

	@Test
	void registersAnExistingPersonWithOptionalEmail() {
		Persona persona = new Persona();
		persona.setIdPersona(12L);
		when(personaRepository.findById(12L)).thenReturn(Optional.of(persona));
		when(proveedorRepository.existePorPersona(12L)).thenReturn(false);
		when(proveedorRepository.save(any(Proveedor.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ProveedorForm form = new ProveedorForm();
		form.setIdPersona(12L);
		form.setEmail(" compras@example.com ");
		Proveedor saved = proveedorService.registrar(form);

		assertSame(persona, saved.getPersona());
		assertEquals("compras@example.com", saved.getEmail());
	}

	@Test
	void rejectsMissingPersonWithoutSaving() {
		when(personaRepository.findById(99L)).thenReturn(Optional.empty());
		ProveedorForm form = new ProveedorForm();
		form.setIdPersona(99L);

		assertThrows(PersonaNoEncontradaException.class, () -> proveedorService.registrar(form));
		verify(proveedorRepository, never()).save(any(Proveedor.class));
	}

	@Test
	void preventsRegisteringTheSamePersonTwice() {
		Persona persona = new Persona();
		persona.setIdPersona(12L);
		when(personaRepository.findById(12L)).thenReturn(Optional.of(persona));
		when(proveedorRepository.existePorPersona(12L)).thenReturn(true);
		ProveedorForm form = new ProveedorForm();
		form.setIdPersona(12L);

		assertThrows(ProveedorDuplicadoException.class, () -> proveedorService.registrar(form));
		verify(proveedorRepository, never()).save(any(Proveedor.class));
	}

	@Test
	void updatesOnlyEmailAndKeepsPersonAssociation() {
		Persona persona = new Persona();
		persona.setIdPersona(12L);
		Proveedor proveedor = new Proveedor();
		proveedor.setPersona(persona);
		proveedor.setEmail("viejo@example.com");
		when(proveedorRepository.buscarPorIdConPersona(4L)).thenReturn(Optional.of(proveedor));
		when(proveedorRepository.save(proveedor)).thenReturn(proveedor);
		ProveedorForm form = new ProveedorForm();
		form.setIdPersona(33L);
		form.setEmail("nuevo@example.com");

		Proveedor updated = proveedorService.actualizar(4L, form);

		assertSame(persona, updated.getPersona());
		assertEquals("nuevo@example.com", updated.getEmail());
	}

	@Test
	void preventsDeletingProviderWithProductsWithoutDeleting() {
		Proveedor proveedor = new Proveedor();
		when(proveedorRepository.buscarPorIdConPersona(4L)).thenReturn(Optional.of(proveedor));
		when(proveedorRepository.tieneProductos(4L)).thenReturn(true);

		assertThrows(ProveedorRelacionadoException.class, () -> proveedorService.eliminar(4L));
		verify(proveedorRepository, never()).delete(any(Proveedor.class));
	}
}
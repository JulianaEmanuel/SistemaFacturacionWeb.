package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facturacion.sistemaweb.model.Persona;
import com.facturacion.sistemaweb.model.PersonaForm;
import com.facturacion.sistemaweb.repository.PersonaRepository;
import com.facturacion.sistemaweb.service.PersonaService;

@ExtendWith(MockitoExtension.class)
class PersonaDateConversionTest {

	@Mock
	private PersonaRepository personaRepository;

	private PersonaService personaService;

	@BeforeEach
	void setUp() {
		var contactos=org.mockito.Mockito.mock(com.facturacion.sistemaweb.repository.PersonaContactosRepository.class);org.mockito.Mockito.lenient().when(contactos.disponible()).thenReturn(true);personaService = new PersonaService(personaRepository,contactos);
	}

	@Test
	void editingDatePreservesExistingTimeAndNullDateStaysNull() {
		Persona persona = new Persona();
		persona.setIdPersona(10L);
		persona.setFechaNacimientoPersona(LocalDateTime.of(1990, 4, 12, 16, 27, 38, 123_000_000));
		when(personaRepository.findById(10L)).thenReturn(Optional.of(persona));
		when(personaRepository.existeCedulaEnOtraPersona("0912345678", 10L)).thenReturn(false);
		when(personaRepository.save(any(Persona.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PersonaForm form = new PersonaForm();
		form.setCedulaPersona("0912345678");
		form.setNombresPersona("Ana");
		form.setApellidosPersona("Pérez");form.setDireccionPersona("Calle de prueba");form.setCorreoPersona("cliente@example.invalid");
		form.setFechaNacimientoPersona(LocalDate.of(2026, 10, 9));

		Persona updated = personaService.actualizar(10L, form);

		assertEquals(LocalDateTime.of(2026, 10, 9, 16, 27, 38, 123_000_000), updated.getFechaNacimientoPersona());
	}

	@Test
	void registeringWithoutBirthDateKeepsSqlDateNullable() {
		when(personaRepository.existeCedula("0912345678")).thenReturn(false);
		when(personaRepository.saveAndFlush(any(Persona.class))).thenAnswer(invocation -> invocation.getArgument(0));

		PersonaForm form = new PersonaForm();
		form.setCedulaPersona("0912345678");
		form.setNombresPersona("Ana");
		form.setApellidosPersona("Pérez");form.setDireccionPersona("Calle de prueba");form.setCorreoPersona("cliente@example.invalid");

		Persona created = personaService.registrar(form);

		assertEquals(null, created.getFechaNacimientoPersona());
	}
}
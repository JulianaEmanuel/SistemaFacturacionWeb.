package com.facturacion.sistemaweb;

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
import com.facturacion.sistemaweb.model.PersonaForm;
import com.facturacion.sistemaweb.repository.PersonaRepository;
import com.facturacion.sistemaweb.service.CedulaDuplicadaException;
import com.facturacion.sistemaweb.service.PersonaService;

@ExtendWith(MockitoExtension.class)
class PersonaServiceDuplicateCedulaTest {

	@Mock
	private PersonaRepository personaRepository;

	private PersonaService personaService;

	@BeforeEach
	void setUp() {
		personaService = new PersonaService(personaRepository,org.mockito.Mockito.mock(com.facturacion.sistemaweb.repository.PersonaContactosRepository.class));
	}

	@Test
	void rejectsDuplicateCedulaOnRegistrationWithoutSaving() {
		when(personaRepository.existeCedula("0912345678")).thenReturn(true);

		assertThrows(CedulaDuplicadaException.class, () -> personaService.registrar(validForm("0912345678")));
		verify(personaRepository, never()).save(any(Persona.class));
	}

	@Test
	void rejectsCedulaUsedByAnotherPersonaOnUpdateWithoutSaving() {
		Persona existing = new Persona();
		existing.setIdPersona(20L);
		existing.setCedulaPersona("1234567890 ");
		when(personaRepository.findById(20L)).thenReturn(Optional.of(existing));
		when(personaRepository.existeCedulaEnOtraPersona("0912345678", 20L)).thenReturn(true);

		assertThrows(CedulaDuplicadaException.class,
				() -> personaService.actualizar(20L, validForm("0912345678")));
		verify(personaRepository, never()).save(any(Persona.class));
	}

	private PersonaForm validForm(String cedula) {
		PersonaForm form = new PersonaForm();
		form.setCedulaPersona(cedula);
		form.setNombresPersona("Ana");
		form.setApellidosPersona("Pérez");
		return form;
	}
}
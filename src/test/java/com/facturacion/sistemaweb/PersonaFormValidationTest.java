package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.facturacion.sistemaweb.model.PersonaForm;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class PersonaFormValidationTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	@BeforeAll
	static void createValidator() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		validatorFactory.close();
	}

	@Test
	void acceptsTenAsciiDigits() {
		PersonaForm form = validForm("0912345678");

		assertFalse(validator.validate(form).stream()
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("cedulaPersona")));
	}

	@Test
	void bindsBirthDateAsDateWithoutTime() {
		PersonaForm form = validForm("0912345678");
		form.setFechaNacimientoPersona(LocalDate.of(2026, 10, 9));

		assertFalse(validator.validate(form).stream()
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("fechaNacimientoPersona")));
	}

	@ParameterizedTest
	@ValueSource(strings = {"123456789", "12345678901"})
	void rejectsIncorrectLengths(String cedula) {
		assertCedulaInvalid(cedula);
	}

	@ParameterizedTest
	@ValueSource(strings = {"12345A7890", "123 567890", "123-567890", "１２３４５６７８９０"})
	void rejectsNonAsciiDigitsAndOtherCharacters(String cedula) {
		assertCedulaInvalid(cedula);
	}

	private void assertCedulaInvalid(String cedula) {
		Set<ConstraintViolation<PersonaForm>> violations = validator.validate(validForm(cedula));
		assertTrue(violations.stream()
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("cedulaPersona")
						&& violation.getMessage().contains("10 dígitos numéricos")));
	}

	private PersonaForm validForm(String cedula) {
		PersonaForm form = new PersonaForm();
		form.setCedulaPersona(cedula);
		form.setNombresPersona("Ana");
		form.setApellidosPersona("Pérez");
		return form;
	}
}
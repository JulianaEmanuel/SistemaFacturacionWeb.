package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.facturacion.sistemaweb.model.ProveedorForm;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ProveedorFormValidationTest {

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
	void acceptsExistingPersonAndOptionalEmptyEmail() {
		ProveedorForm form = validForm();
		form.setEmail("");

		assertTrue(validator.validate(form).isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"correo-invalido", "persona@", "@dominio.com"})
	void rejectsInvalidEmail(String email) {
		ProveedorForm form = validForm();
		form.setEmail(email);

		assertTrue(validator.validate(form).stream()
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("email")));
	}

	@Test
	void rejectsEmailLongerThanNchar100() {
		ProveedorForm form = validForm();
		form.setEmail("a".repeat(90) + "@example.com");

		Set<ConstraintViolation<ProveedorForm>> violations = validator.validate(form);
		assertFalse(violations.stream()
				.filter(violation -> violation.getPropertyPath().toString().equals("email")).toList().isEmpty());
	}

	@Test
	void requiresPersonaOnRegistrationForm() {
		ProveedorForm form = validForm();
		form.setIdPersona(null);

		assertTrue(validator.validate(form).stream()
				.anyMatch(violation -> violation.getPropertyPath().toString().equals("idPersona")));
	}

	private ProveedorForm validForm() {
		ProveedorForm form = new ProveedorForm();
		form.setIdPersona(1L);
		return form;
	}
}
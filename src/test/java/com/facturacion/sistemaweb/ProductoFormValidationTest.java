package com.facturacion.sistemaweb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.facturacion.sistemaweb.model.ProductoForm;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ProductoFormValidationTest {

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
	void acceptsValuesWithinSqlDecimalPrecisionAndScale() {
		ProductoForm form = validForm();
		form.setPrecioUnitario(new BigDecimal("9999999999.9999"));
		form.setIvaProducto(new BigDecimal("999.99"));

		assertTrue(validator.validate(form).isEmpty());
	}

	@Test
	void rejectsNameLongerThanCharThirty() {
		ProductoForm form = validForm();
		form.setNombreProducto("1234567890123456789012345678901");

		assertInvalidProperty(form, "nombreProducto");
	}

	@ParameterizedTest
	@ValueSource(strings = {"-0.0001", "10000000000.0000", "1.12345"})
	void rejectsInvalidPriceRangeOrScale(String value) {
		ProductoForm form = validForm();
		form.setPrecioUnitario(new BigDecimal(value));

		assertInvalidProperty(form, "precioUnitario");
	}

	@ParameterizedTest
	@ValueSource(strings = {"-0.01", "1000.00", "1.001"})
	void rejectsInvalidIvaRangeOrScale(String value) {
		ProductoForm form = validForm();
		form.setIvaProducto(new BigDecimal(value));

		assertInvalidProperty(form, "ivaProducto");
	}

	@Test
	void requiresProveedor() {
		ProductoForm form = validForm();
		form.setIdProveedor(null);

		assertInvalidProperty(form, "idProveedor");
	}

	private void assertInvalidProperty(ProductoForm form, String property) {
		Set<ConstraintViolation<ProductoForm>> violations = validator.validate(form);
		assertFalse(violations.stream().filter(violation -> violation.getPropertyPath().toString().equals(property)).toList().isEmpty());
	}

	private ProductoForm validForm() {
		ProductoForm form = new ProductoForm();
		form.setIdProveedor(1L);
		form.setNombreProducto("Producto de prueba");
		form.setPrecioUnitario(new BigDecimal("12.3456"));
		form.setIvaProducto(new BigDecimal("12.34"));
		return form;
	}
}
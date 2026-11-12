package com.facturacion.sistemaweb.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ProveedorForm {

	@NotNull(message = "Selecciona una persona.")
	private Long idPersona;

	@Email(message = "Ingresa un correo electrónico válido.")
	@Size(max = 100, message = "El correo admite hasta 100 caracteres.")
	private String email;

	public Long getIdPersona() {
		return idPersona;
	}

	public void setIdPersona(Long idPersona) {
		this.idPersona = idPersona;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
}
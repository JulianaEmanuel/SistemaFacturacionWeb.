package com.facturacion.sistemaweb.service;

public class PersonaRelacionadaException extends RuntimeException {

	public PersonaRelacionadaException(String message) {
		super(message);
	}

	public PersonaRelacionadaException(String message, Throwable cause) {
		super(message, cause);
	}
}
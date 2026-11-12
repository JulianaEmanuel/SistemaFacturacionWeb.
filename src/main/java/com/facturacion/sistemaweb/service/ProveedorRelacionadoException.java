package com.facturacion.sistemaweb.service;

public class ProveedorRelacionadoException extends RuntimeException {

	public ProveedorRelacionadoException(String message) {
		super(message);
	}

	public ProveedorRelacionadoException(String message, Throwable cause) {
		super(message, cause);
	}
}
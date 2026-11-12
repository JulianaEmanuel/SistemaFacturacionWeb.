package com.facturacion.sistemaweb.service;

public class ProductoRelacionadoException extends RuntimeException {

	public ProductoRelacionadoException(String message) {
		super(message);
	}

	public ProductoRelacionadoException(String message, Throwable cause) {
		super(message, cause);
	}
}
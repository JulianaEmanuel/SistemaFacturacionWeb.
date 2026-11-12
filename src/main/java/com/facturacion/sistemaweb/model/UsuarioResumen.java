package com.facturacion.sistemaweb.model;

/** No contiene credenciales; apto para formularios y listados. */
public record UsuarioResumen(Long id, Integer idTipo, String nombre, String tipo, boolean activo) { }

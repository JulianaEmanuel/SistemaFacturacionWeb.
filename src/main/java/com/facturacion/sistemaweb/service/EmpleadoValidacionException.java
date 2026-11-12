package com.facturacion.sistemaweb.service;

public class EmpleadoValidacionException extends RuntimeException {
    private final String campo;
    public EmpleadoValidacionException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }
    public String getCampo() { return campo; }
}

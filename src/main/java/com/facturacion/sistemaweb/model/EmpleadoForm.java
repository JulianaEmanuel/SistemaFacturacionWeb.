package com.facturacion.sistemaweb.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class EmpleadoForm {
    @NotNull(message = "Selecciona una persona.")
    @Positive(message = "Selecciona una persona válida.")
    private Long idPersona;
    @NotNull(message = "Selecciona un usuario.")
    @Positive(message = "Selecciona un usuario válido.")
    private Long idUsuario;
    public Long getIdPersona() { return idPersona; }
    public void setIdPersona(Long idPersona) { this.idPersona = idPersona; }
    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }
}

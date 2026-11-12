package com.facturacion.sistemaweb.model;

import jakarta.validation.constraints.*;

public class UsuarioForm {
    @NotBlank(message="El nombre de usuario es obligatorio.") @Size(max=100, message="El nombre admite hasta 100 caracteres.")
    private String nombre;
    @NotNull(message="Selecciona un tipo de usuario.") @Positive
    private Integer idTipo;
    private boolean activo = true;
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Integer getIdTipo() { return idTipo; }
    public void setIdTipo(Integer idTipo) { this.idTipo = idTipo; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}

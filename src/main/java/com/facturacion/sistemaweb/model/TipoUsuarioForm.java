package com.facturacion.sistemaweb.model;

import jakarta.validation.constraints.*;

public class TipoUsuarioForm {
    @NotBlank(message="El nombre es obligatorio.") @Size(max=50, message="El nombre admite hasta 50 caracteres.")
    private String nombre;
    @NotBlank(message="La descripción es obligatoria.") @Size(max=300, message="La descripción admite hasta 300 caracteres.")
    private String descripcion;
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}

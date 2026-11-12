package com.facturacion.sistemaweb.model;

import jakarta.persistence.*;

@Entity
@Table(name = "\"TipoUsuario\"", schema = "dbo")
public class TipoUsuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_TIPO_USUARIO", nullable = false)
    private Integer id;
    @Column(name = "TIPO_USUARIO", nullable = false, length = 50, columnDefinition = "char(50)")
    private String nombre;
    @Column(name = "DESCRIPCION", nullable = false, length = 300, columnDefinition = "char(300)")
    private String descripcion;
    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}

package com.facturacion.sistemaweb.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** Vista JPA de solo lectura: deliberadamente no mapea CONTRASENIA. */
@Entity
@Immutable
@Table(name = "Usuario", schema = "dbo")
public class Usuario {
    @Id
    @Column(name = "ID_USUARIO", nullable = false)
    private Long idUsuario;
    @Column(name = "ID_TIPO_USUARIO", nullable = false)
    private Integer idTipoUsuario;
    @Column(name = "NOMBRE_USUARIO", nullable = false, length = 100, columnDefinition = "char(100)")
    private String nombreUsuario;
    @Column(name = "ACTIVO", nullable = false)
    private boolean activo;

    public Long getIdUsuario() { return idUsuario; }
    public Integer getIdTipoUsuario() { return idTipoUsuario; }
    public String getNombreUsuario() { return nombreUsuario; }
    public boolean isActivo() { return activo; }
}

package com.facturacion.sistemaweb.model;
import jakarta.persistence.*;
@Entity @Table(name="\"FormaPago\"",schema="dbo")
public class FormaPago {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="ID_FORMA_PAGO") private Integer id;
    @Column(name="CODIGO",nullable=false,length=30) private String codigo;
    @Column(name="NOMBRE",nullable=false,length=80) private String nombre;
    @Column(name="ACTIVA",nullable=false) private boolean activa;
    public Integer getId(){return id;}public String getCodigo(){return codigo;}public String getNombre(){return nombre;}public boolean isActiva(){return activa;}
}

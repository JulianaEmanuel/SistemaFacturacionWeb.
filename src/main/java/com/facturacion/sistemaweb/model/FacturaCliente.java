package com.facturacion.sistemaweb.model;
import jakarta.persistence.*;
@Entity @Table(name="\"FacturaCliente\"",schema="dbo")
public class FacturaCliente {
    @Id @Column(name="ID_FACTURA") private Long idFactura;
    @Column(name="IDENTIFICACION",nullable=false,length=13) private String identificacion;
    @Column(name="NOMBRES",nullable=false,length=100) private String nombres;
    @Column(name="APELLIDOS",nullable=false,length=100) private String apellidos;
    @Column(name="DIRECCION",length=250) private String direccion;
    @Column(name="TELEFONO",length=25) private String telefono;
    @Column(name="CORREO",length=254) private String correo;
    protected FacturaCliente(){}
    public FacturaCliente(Long factura,Persona p){idFactura=factura;identificacion=p.getCedulaPersona().stripTrailing();nombres=p.getNombresPersona().stripTrailing();apellidos=p.getApellidosPersona().stripTrailing();direccion=p.getDireccionPersona();telefono=p.getTelefonoPersona();correo=p.getCorreoPersona();}
    public Long getIdFactura(){return idFactura;}public String getIdentificacion(){return identificacion;}public String getNombres(){return nombres;}public String getApellidos(){return apellidos;}public String getDireccion(){return direccion;}public String getTelefono(){return telefono;}public String getCorreo(){return correo;}
}

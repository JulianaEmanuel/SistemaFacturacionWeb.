package com.facturacion.sistemaweb.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity @Table(name="Factura",schema="dbo")
public class Factura {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="ID_FACTURA",nullable=false) private Long idFactura;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="ID_EMPLEADO",nullable=false) private Empleado empleado;
    // La tabla no tiene FK física a Persona. No se inventa una restricción JPA.
    @Column(name="ID_PERSONA",nullable=false) private Long idPersona;
    @Column(name="FECHA_FACTURA",nullable=false) private LocalDateTime fecha;
    @Column(name="SUBTOTAL_FACTURA",nullable=false,precision=18,scale=4) private BigDecimal subtotal;
    @Column(name="TOTAL_FACTURA",nullable=false,precision=18,scale=4) private BigDecimal total;
    public Long getIdFactura() { return idFactura; }
    public Empleado getEmpleado() { return empleado; }
    public void setEmpleado(Empleado empleado) { this.empleado=empleado; }
    public Long getIdPersona() { return idPersona; }
    public void setIdPersona(Long idPersona) { this.idPersona=idPersona; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha=fecha; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal=subtotal; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total=total; }
}

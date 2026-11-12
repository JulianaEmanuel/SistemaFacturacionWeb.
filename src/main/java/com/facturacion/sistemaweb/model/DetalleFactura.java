package com.facturacion.sistemaweb.model;

import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity @Table(name="Detallefactura",schema="dbo")
public class DetalleFactura {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="ID_DETALLE_FACTURA",nullable=false) private Long idDetalle;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ID_FACTURA",nullable=false) private Factura factura;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ID_PRODUCTO",nullable=false) private Producto producto;
    @Column(name="PRECIO_UNITARIO_DETALLE",nullable=false,precision=14,scale=4) private BigDecimal precio;
    @Column(name="IVA_DETALLE",nullable=false,precision=5,scale=2) private BigDecimal iva;
    @Column(name="CANTIDAD",nullable=false,precision=8,scale=2) private BigDecimal cantidad;
    @Column(name="SUBTOTAL_DETALLE",nullable=false,precision=18,scale=4) private BigDecimal subtotal;
    @Column(name="TOTAL_DETALLE",nullable=false,precision=18,scale=4) private BigDecimal total;
    public Long getIdDetalle() { return idDetalle; }
    public Factura getFactura() { return factura; }
    public void setFactura(Factura factura) { this.factura=factura; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto=producto; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio=precio; }
    public BigDecimal getIva() { return iva; }
    public void setIva(BigDecimal iva) { this.iva=iva; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad=cantidad; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal=subtotal; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total=total; }
}
